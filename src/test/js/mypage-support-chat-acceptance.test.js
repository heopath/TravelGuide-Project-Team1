/* 마이페이지 상담 채팅(WebSocket) 수용 기준
 * 실행: src/test/js 에서 `npm test`
 *
 * PR #282 리뷰(heopath)에서 지적된 세 가지를 고정한다.
 *   1. REST 동기화 전에 구독부터 걸어, 그 틈에 도착한 봇 답변을 놓치지 않는다.
 *   2. 새로고침·다른 탭에서 열어도(마지막 메시지가 USER면) 봇 대기 상태가 복원된다.
 *   3. 서버가 `/user/queue/support-chat/errors`로 보내는 오류를 실제로 받아 화면에 반영한다.
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/mypage/mypage.html");
const JS = path.join(ROOT, "src/main/resources/static/js/pages/mypage/mypage-support-chat.js");

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
      if (Date.now() - started > timeoutMs) return reject(new Error("timeout: " + predicate));
      setTimeout(tick, 10);
    };
    tick();
  });
}

const ok = (data) => ({
  ok: true, status: 200, json: async () => ({ success: true, code: "SUCCESS", data })
});

const room = (overrides) => Object.assign({
  supportChatRoomId: 5,
  status: "BOT",
}, overrides || {});

const message = (id, senderType, content) => ({
  supportChatMessageId: id, supportChatRoomId: 5,
  senderType, senderUserId: senderType === "USER" ? 7 : null,
  content, createdAt: "2026-08-19T02:00:00Z",
});

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

async function boot(responder, { withSocket = true } = {}) {
  const log = [];
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/mypage?view=support", runScripts: "outside-only"
  });
  const w = dom.window;
  const d = w.document;
  w.fetch = async (url, options) => {
    log.push({ type: "fetch", url: String(url) });
    return responder(String(url), options || {});
  };

  let socket = null;
  if (withSocket) {
    socket = fakeSocket(log);
    w.SockJS = socket.FakeSockJS;
    w.Stomp = socket.Stomp;
  }

  const source = fs.readFileSync(JS, "utf8")
    .replace("export function initSupportChat()", "window.initSupportChat = function initSupportChat()");
  w.eval(source);
  const chat = w.initSupportChat();
  return { w, d, log, chat, socket };
}

const panel = (d) => d.querySelector('[data-support-panel="chat"]');
const statusText = (d) => panel(d).querySelector("[data-support-chat-status]");
const chatInput = (d) => panel(d).querySelector("[data-support-chat-input]");
const send = (d) => panel(d).querySelector("[data-support-chat-send]");

async function run() {
  /* ── 구독을 먼저 걸고, 그다음 REST로 동기화한다 ── */
  {
    const { d, log, chat } = await boot(() => ok({ room: room(), messages: [message(1, "BOT", "무엇을 도와드릴까요?")] }));
    await chat.load();
    await until(() => log.some((e) => e.type === "subscribe" && e.destination === "/topic/support-chat/rooms/5"));
    /* resubscribe()가 REST를 다시 부르므로 fetch가 최소 두 번(첫 load + 동기화) 있어야 한다. */
    await until(() => log.filter((e) => e.type === "fetch").length >= 2);

    const roomSubIndex = log.findIndex((e) => e.type === "subscribe" && e.destination === "/topic/support-chat/rooms/5");
    const secondFetchIndex = log.findIndex((e, i) => e.type === "fetch" && log.slice(0, i).some((p) => p.type === "fetch"));

    T("방 토픽을 REST 재동기화보다 먼저 구독한다", roomSubIndex >= 0 && roomSubIndex < secondFetchIndex);
    T("본인 오류 큐도 함께 구독한다",
      log.some((e) => e.type === "subscribe" && e.destination === "/user/queue/support-chat/errors"));
    T("입력창은 대기 상태가 아니면 열려 있다", chatInput(d).disabled === false);
  }

  /* ── 새로고침·다른 탭에서 열어도 봇 대기 상태가 복원된다 ── */
  {
    /* 손님 메시지까지만 있고 봇 답이 아직 없는 상태 — 방금 물어본 뒤 새로고침한 상황. */
    const { d, chat } = await boot(
      () => ok({ room: room({ status: "BOT" }), messages: [message(1, "USER", "환불 문의드립니다") ] }),
      { withSocket: false }
    );
    await chat.load();
    await until(() => statusText(d).textContent.length > 0);

    T("마지막 메시지가 USER면 대기 상태로 복원된다", chatInput(d).disabled === true);
    T("보내기 버튼도 함께 잠긴다", send(d).disabled === true);
    T("대기 안내 문구를 보여준다", statusText(d).textContent.includes("준비"));
  }
  {
    /* 봇이 이미 답한 뒤라면 대기 상태가 아니어야 한다. */
    const { d, chat } = await boot(
      () => ok({ room: room({ status: "BOT" }), messages: [message(1, "USER", "환불 문의드립니다"), message(2, "BOT", "환불 절차를 안내해 드릴게요.")] }),
      { withSocket: false }
    );
    await chat.load();
    await until(() => statusText(d).textContent.length > 0);

    T("봇이 이미 답했으면 입력창이 열려 있다", chatInput(d).disabled === false);
  }

  /* ── 서버가 보내는 복구 가능한 오류를 실제로 받는다 ── */
  {
    const { d, log, chat, socket } = await boot(
      () => ok({ room: room({ status: "BOT" }), messages: [message(1, "USER", "환불 문의드립니다")] })
    );
    await chat.load();
    await until(() => statusText(d).textContent.includes("준비"));
    await until(() => log.some((e) => e.type === "subscribe" && e.destination === "/user/queue/support-chat/errors"));

    socket.subs["/user/queue/support-chat/errors"]({
      body: JSON.stringify({ type: "VALIDATION_ERROR", code: "INVALID_SUPPORT_CHAT_REQUEST", message: "메시지는 2000자 이하여야 합니다.", retryable: false }),
    });

    T("오류 큐 메시지를 화면에 보여준다", statusText(d).textContent.includes("2000자"));
    T("재시도 불가능한 오류면 잠긴 입력창을 풀어 준다", chatInput(d).disabled === false);
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed > 0) process.exit(1);
}

run().catch((error) => { console.error(error); process.exit(1); });
