/* 관리자 상담 채팅 — 실시간 대기열/오류 큐 수용 기준
 * 실행: src/test/js 에서 `npm test`
 *
 * PR #282 리뷰(heopath)에서 지적된 두 가지를 고정한다.
 *   1. 관리자가 어떤 방도 열지 않은 상태에서도 대기열 토픽을 구독해, 새 상담·다른 방의
 *      상태 전환이 새로고침 없이 목록에 반영된다.
 *   2. 방을 열 때 REST로 대화를 읽기 전에 그 방 토픽부터 구독해, 그 틈에 저장된 메시지를
 *      놓치지 않는다.
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/admin/admin.html");
const JS = path.join(ROOT, "src/main/resources/static/js/pages/admin/admin-chat.js");

let passed = 0;
let failed = 0;
const T = (name, condition) => {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name); }
};

function until(predicate, timeoutMs = 4000) {
  return new Promise((resolve, reject) => {
    const started = Date.now();
    const tick = () => {
      if (predicate()) return resolve();
      if (Date.now() - started > timeoutMs) return reject(new Error("timeout"));
      setTimeout(tick, 10);
    };
    tick();
  });
}

const ok = (data) => ({
  ok: true, status: 200, json: async () => ({ success: true, code: "SUCCESS", data })
});

const room = (id, overrides) => Object.assign({
  supportChatRoomId: id,
  userId: 7,
  userNickname: "민재",
  userEmail: "user@example.com",
  assignedAdminId: null,
  assignedAdminNickname: null,
  assignedToMe: false,
  status: "WAITING",
  lastMessageAt: "2026-08-19T02:00:00Z",
  createdAt: "2026-08-19T01:00:00Z",
  lastMessagePreview: "예약이 안 돼요",
  messageCount: 1,
}, overrides || {});

/** SockJS/Stomp를 흉내낸다. subscribe 순서를 fetch 호출과 같은 로그에 남겨 순서를 비교한다. */
function fakeSocket(log) {
  const subs = {};
  function FakeSockJS(url) { this.url = url; }
  const Stomp = {
    over() {
      const client = {
        connected: false,
        debug: null,
        connect(headers, onConnect) {
          setTimeout(() => { client.connected = true; onConnect(); }, 0);
        },
        subscribe(destination, callback) {
          log.push({ type: "subscribe", destination });
          subs[destination] = callback;
          return { unsubscribe() { delete subs[destination]; } };
        },
        disconnect() { client.connected = false; },
      };
      return client;
    },
  };
  return { FakeSockJS, Stomp, subs };
}

async function boot(responder) {
  const log = [];
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/admin", runScripts: "outside-only"
  });
  const w = dom.window;
  const d = w.document;
  w.confirm = () => true;
  w.fetch = async (url, options) => {
    log.push({ type: "fetch", url: String(url) });
    return responder(String(url), options || {});
  };
  const socket = fakeSocket(log);
  w.SockJS = socket.FakeSockJS;
  w.Stomp = socket.Stomp;

  w.eval(fs.readFileSync(JS, "utf8"));
  if (d.readyState !== "loading") d.dispatchEvent(new w.Event("DOMContentLoaded"));
  await until(() => log.some((e) => e.type === "fetch"));
  return { w, d, log, socket };
}

const rows = (d) => [...d.querySelectorAll("#chatRoomList .admin-chat-room")];

async function openFirstRoom(d, w, log) {
  await until(() => rows(d).length > 0);
  rows(d)[0].dispatchEvent(new w.Event("click", { bubbles: true }));
  await until(() => log.some((e) => e.type === "fetch" && /support-chats\/\d+$/.test(e.url)));
}

async function run() {
  /* ── 방을 열지 않아도 대기열에 붙는다 ── */
  {
    const { log } = await boot(() => ok([room(5)]));

    await until(() => log.some((e) => e.type === "subscribe" && e.destination === "/topic/support-chat/admin/rooms"));

    T("관리자 대기열 토픽을 구독한다",
      log.some((e) => e.type === "subscribe" && e.destination === "/topic/support-chat/admin/rooms"));
    T("본인 오류 큐도 함께 구독한다",
      log.some((e) => e.type === "subscribe" && e.destination === "/user/queue/support-chat/errors"));
  }

  /* ── 대기열 이벤트가 오면 새로고침 없이 목록을 다시 읽는다 ── */
  {
    const { log, socket } = await boot(() => ok([room(5)]));
    await until(() => log.some((e) => e.type === "subscribe" && e.destination === "/topic/support-chat/admin/rooms"));
    const fetchesBefore = log.filter((e) => e.type === "fetch").length;

    socket.subs["/topic/support-chat/admin/rooms"]({
      body: JSON.stringify({ type: "MESSAGE", message: null, room: room(6, { status: "WAITING" }) }),
    });

    await until(() => log.filter((e) => e.type === "fetch").length > fetchesBefore);
    T("대기열 이벤트를 받으면 목록을 다시 불러온다",
      log.filter((e) => e.type === "fetch").length > fetchesBefore);
  }

  /* ── 방을 열 때, REST로 대화를 읽기 전에 그 방 토픽부터 구독한다 ── */
  {
    const { d, w, log } = await boot((url) => {
      if (/support-chats\/\d+$/.test(url)) {
        return ok({ room: room(5), messages: [] });
      }
      return ok([room(5)]);
    });
    await openFirstRoom(d, w, log);

    const roomSubIndex = log.findIndex(
      (e) => e.type === "subscribe" && e.destination === "/topic/support-chat/rooms/5");
    const roomFetchIndex = log.findIndex(
      (e) => e.type === "fetch" && /support-chats\/\d+$/.test(e.url));

    T("방 토픽 구독이 그 방 REST 조회보다 먼저 일어난다",
      roomSubIndex >= 0 && roomFetchIndex >= 0 && roomSubIndex < roomFetchIndex);
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed > 0) process.exit(1);
}

run().catch((error) => { console.error(error); process.exit(1); });
