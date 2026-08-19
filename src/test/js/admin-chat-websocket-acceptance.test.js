/* 관리자 상담 채팅 — 실시간 대기열/오류 큐/경쟁 조건/폴백 폴링 수용 기준
 * 실행: src/test/js 에서 `npm test`
 *
 * PR #282 리뷰(heopath) 1차·2차에서 지적된 항목을 고정한다.
 *   1. 관리자가 어떤 방도 열지 않은 상태에서도 대기열 토픽을 구독해, 새 상담·다른 방의
 *      상태 전환이 새로고침 없이 목록에 반영된다.
 *   2. 방을 열 때 REST로 대화를 읽기 전에 그 방 토픽부터 구독해, 그 틈에 저장된 메시지를
 *      놓치지 않는다.
 *   3. (2차 리뷰) 구독 선행만으로는 부족하다 — 느리게 온 옛 응답이 빠르게 도착한 새 응답을
 *      뒤늦게 덮어써서는 안 된다("요청 세대 번호"로 방지).
 *   4. (2차 리뷰) WebSocket이 계속 연결되지 않으면(스크립트 없음, 핸드셰이크·nginx 실패)
 *      REST 폴백 폴링으로 대기열·열어 둔 방이 계속 갱신돼야 한다.
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

const message = (id, senderType, content) => ({
  supportChatMessageId: id, supportChatRoomId: 5,
  senderType, senderUserId: senderType === "USER" ? 7 : null,
  content, createdAt: "2026-08-19T02:00:00Z",
});

/**
 * SockJS/Stomp를 흉내낸다. subscribe 순서를 fetch 호출과 같은 로그에 남겨 순서를 비교한다.
 *
 * <p>{@code autoConnect: false}면 STOMP CONNECT가 영영 응답하지 않는 상황(핸드셰이크·nginx
 * Upgrade 설정 실패)을 흉내낸다 — `client.connected`가 계속 false로 남는다.
 */
function fakeSocket(log, { autoConnect = true } = {}) {
  const subs = {};
  function FakeSockJS(url) { this.url = url; }
  const Stomp = {
    over() {
      const client = {
        connected: false,
        debug: null,
        connect(headers, onConnect) {
          if (!autoConnect) return;
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

/**
 * jsdom의 setInterval을 가짜로 바꿔 실제 시간을 기다리지 않고 결정적으로 통제한다.
 *
 * <p>admin-chat.js는 연결이 안 되는 동안 REST 폴백 폴링을 setInterval로 돈다(PR #282
 * 2차 리뷰 반영). jsdom의 진짜 setInterval은 실제 Node 타이머로 뒷받침되면서도 unref
 * 가능한 핸들을 내주지 않아, 정리하지 않으면 테스트 프로세스가 끝나지 않는다 — 그래서
 * 실제 타이머를 아예 쓰지 않고 콜백만 붙잡아 뒀다가 테스트가 원할 때 수동으로 발화시킨다.
 */
function fakeTimers() {
  const intervals = new Map();
  let nextId = 1;
  return {
    setInterval(fn) {
      const id = nextId++;
      intervals.set(id, fn);
      return id;
    },
    clearInterval(id) { intervals.delete(id); },
    ids() { return [...intervals.keys()]; },
    fire(id) {
      const fn = intervals.get(id);
      if (fn) fn();
    },
  };
}

const openedWindows = [];

async function boot(responder, { autoConnect = true } = {}) {
  const log = [];
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/admin", runScripts: "outside-only"
  });
  const w = dom.window;
  openedWindows.push(w);
  const d = w.document;
  w.confirm = () => true;
  w.fetch = async (url, options) => {
    log.push({ type: "fetch", url: String(url) });
    return responder(String(url), options || {});
  };
  const socket = fakeSocket(log, { autoConnect });
  w.SockJS = socket.FakeSockJS;
  w.Stomp = socket.Stomp;
  const timers = fakeTimers();
  w.setInterval = timers.setInterval;
  w.clearInterval = timers.clearInterval;

  w.eval(fs.readFileSync(JS, "utf8"));
  if (d.readyState !== "loading") d.dispatchEvent(new w.Event("DOMContentLoaded"));
  await until(() => log.some((e) => e.type === "fetch"));
  return { w, d, log, socket, timers };
}

function closeAllWindows() {
  openedWindows.forEach(function (w) {
    try { w.close(); } catch (error) { /* 이미 닫혔거나 정리할 것이 없다. */ }
  });
  openedWindows.length = 0;
}

const rows = (d) => [...d.querySelectorAll("#chatRoomList .admin-chat-room")];
const bubbles = (d) => [...d.querySelectorAll("#chatMessages .admin-chat-message")];

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

  /*
   * ── 느리게 도착한 옛 응답이 빠르게 도착한 새 응답을 덮어쓰지 않는다 (2차 리뷰) ──
   *
   * openRoom()이 부른 첫 조회(A)가 아직 응답을 못 받은 사이, 그 방 토픽에 이벤트가 와서
   * 두 번째 조회(B)가 실행되고 B가 먼저 응답을 받아 화면에 그려진다. 그 뒤에야 A의 응답이
   * (더 낡은 내용으로) 도착한다 — A가 화면을 다시 옛 상태로 덮어쓰면 안 된다.
   */
  {
    let roomCallCount = 0;
    let resolveFirst = null;
    const staleView = { room: room(5), messages: [message(1, "USER", "예약이 안 돼요")] };
    const freshView = { room: room(5), messages: [message(1, "USER", "예약이 안 돼요"), message(2, "ADMIN", "확인해 드릴게요")] };

    const { d, w, log, socket } = await boot((url) => {
      if (/support-chats\/\d+$/.test(url)) {
        roomCallCount += 1;
        if (roomCallCount === 1) {
          return new Promise((resolve) => { resolveFirst = () => resolve(ok(staleView)); });
        }
        return ok(freshView);
      }
      return ok([room(5)]);
    });
    await openFirstRoom(d, w, log); /* 첫 조회(A)가 시작됐고, 아직 응답 대기 중이다. */

    /* 방 토픽에 이벤트가 와서 두 번째 조회(B)가 실행되고, 곧바로(동기 응답) 화면에 반영된다. */
    socket.subs["/topic/support-chat/rooms/5"]({ body: "{}" });
    await until(() => roomCallCount === 2);
    await until(() => bubbles(d).length === 2);

    T("빠르게 도착한 새 응답이 먼저 화면에 반영된다", bubbles(d).length === 2);

    /* 이제야 첫 조회(A)의 낡은 응답이 도착한다. */
    resolveFirst();
    await new Promise((resolve) => setTimeout(resolve, 30)); /* A의 .then이 처리될 시간을 준다. */

    T("느리게 도착한 옛 응답이 화면을 다시 덮어쓰지 않는다", bubbles(d).length === 2);
  }

  /* ── 연결이 안 되는 동안 REST 폴백 폴링이 대기열을 갱신한다 (2차 리뷰) ── */
  {
    const { log, timers } = await boot(() => ok([room(5)]), { autoConnect: false });
    const fetchesBefore = log.filter((e) => e.type === "fetch").length;
    const [intervalId] = timers.ids();

    T("연결이 안 되면 폴백 폴링을 예약해 둔다", intervalId !== undefined);

    timers.fire(intervalId);
    await until(() => log.filter((e) => e.type === "fetch").length > fetchesBefore);

    T("연결이 안 된 동안 폴백 폴링이 대기열을 다시 불러온다",
      log.filter((e) => e.type === "fetch").length > fetchesBefore);
  }

  /* ── 연결이 안 되는 동안 REST 폴백 폴링이 열어 둔 방도 함께 갱신한다 ── */
  {
    const { d, w, log, timers } = await boot((url) => {
      if (/support-chats\/\d+$/.test(url)) return ok({ room: room(5), messages: [] });
      return ok([room(5)]);
    }, { autoConnect: false });
    await openFirstRoom(d, w, log);
    const roomFetchesBefore = log.filter((e) => e.type === "fetch" && /support-chats\/\d+$/.test(e.url)).length;

    const [intervalId] = timers.ids();
    timers.fire(intervalId);
    await until(() =>
      log.filter((e) => e.type === "fetch" && /support-chats\/\d+$/.test(e.url)).length > roomFetchesBefore);

    T("폴백 폴링이 열어 둔 방의 대화도 함께 갱신한다",
      log.filter((e) => e.type === "fetch" && /support-chats\/\d+$/.test(e.url)).length > roomFetchesBefore);
  }

  /* ── 연결돼 있으면 폴백 폴링이 불필요한 조회를 하지 않는다 ── */
  {
    const { log, timers } = await boot(() => ok([room(5)])); /* autoConnect: true(기본) */
    await until(() => log.some((e) => e.type === "subscribe" && e.destination === "/topic/support-chat/admin/rooms"));
    const fetchesBefore = log.filter((e) => e.type === "fetch").length;

    const [intervalId] = timers.ids();
    timers.fire(intervalId);
    await new Promise((resolve) => setTimeout(resolve, 20));

    T("연결돼 있으면 폴백 폴링이 조회를 건너뛴다",
      log.filter((e) => e.type === "fetch").length === fetchesBefore);
  }

  closeAllWindows();
  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed > 0) process.exit(1);
}

run().catch((error) => { closeAllWindows(); console.error(error); process.exit(1); });
