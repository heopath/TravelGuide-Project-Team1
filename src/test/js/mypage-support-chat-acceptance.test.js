/* 마이페이지 상담 채팅(WebSocket) 수용 기준
 * 실행: src/test/js 에서 `npm test`
 *
 * PR #282 리뷰(heopath) 1차·2차에서 지적된 항목을 고정한다.
 *   1. REST 동기화 전에 구독부터 걸어, 그 틈에 도착한 봇 답변을 놓치지 않는다.
 *   2. 새로고침·다른 탭에서 열어도(마지막 메시지가 USER면) 봇 대기 상태가 복원된다.
 *   3. 서버가 `/user/queue/support-chat/errors`로 보내는 오류를 실제로 받아 화면에 반영한다.
 *   4. (2차 리뷰) 구독 선행만으로는 부족하다 — 느리게 온 옛 응답이 빠르게 도착한 새 응답을
 *      뒤늦게 덮어써서는 안 된다("요청 세대 번호"로 방지).
 *   5. (2차 리뷰) WebSocket이 계속 연결되지 않으면(스크립트 없음, 핸드셰이크·nginx 실패)
 *      REST 폴백 폴링으로 방이 계속 갱신돼야 한다.
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

/**
 * jsdom의 setInterval을 가짜로 바꿔 실제 시간을 기다리지 않고 결정적으로 통제한다.
 *
 * <p>mypage-support-chat.js는 연결이 안 되는 동안 REST 폴백 폴링을 setInterval로 돈다
 * (PR #282 2차 리뷰 반영). jsdom의 진짜 setInterval은 실제 Node 타이머로 뒷받침되면서도
 * unref 가능한 핸들을 내주지 않아, 정리하지 않으면 테스트 프로세스가 끝나지 않는다 —
 * 그래서 실제 타이머를 아예 쓰지 않고 콜백만 붙잡아 뒀다가 테스트가 원할 때 수동으로
 * 발화시킨다.
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

async function boot(responder, { withSocket = true } = {}) {
  const log = [];
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/mypage?view=support", runScripts: "outside-only"
  });
  const w = dom.window;
  openedWindows.push(w);
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
  const timers = fakeTimers();
  w.setInterval = timers.setInterval;
  w.clearInterval = timers.clearInterval;

  const source = fs.readFileSync(JS, "utf8")
    .replace("export function initSupportChat()", "window.initSupportChat = function initSupportChat()");
  w.eval(source);
  const chat = w.initSupportChat();
  return { w, d, log, chat, socket, timers };
}

function closeAllWindows() {
  openedWindows.forEach(function (w) {
    try { w.close(); } catch (error) { /* 이미 닫혔거나 정리할 것이 없다. */ }
  });
  openedWindows.length = 0;
}

const panel = (d) => d.querySelector('[data-support-panel="chat"]');
const statusText = (d) => panel(d).querySelector("[data-support-chat-status]");
const chatInput = (d) => panel(d).querySelector("[data-support-chat-input]");
const send = (d) => panel(d).querySelector("[data-support-chat-send]");
const messagesEl = (d) => panel(d).querySelector("[data-support-chat-messages]");
const actionsEl = (d) => panel(d).querySelector("[data-support-chat-actions]");
const returnBtn = (d) => panel(d).querySelector("[data-support-chat-return]");
const restartBtn = (d) => panel(d).querySelector("[data-support-chat-restart]");

async function run() {
  /* ── 봇 답변에서 실제 화면으로 이어 주는 액션 ── */
  {
    const actionMessage = Object.assign(message(1, "BOT", "원하는 방법을 선택해 주세요."),
      { actionKey: "NEW_TRIP", actionKey2: "MY_TRIPS", actionKey3: "TRIP_SCHEDULE" });
    const { d, chat } = await boot(() => ok({ room: room(), messages: [actionMessage] }),
      { withSocket: false });
    await chat.load();
    const link = messagesEl(d).querySelector(".support-chat-link");
    T("여행 만들기 안내에 이동 버튼을 붙인다", link?.textContent === "여행 만들기 →");
    T("이동 주소는 허용된 내부 경로다", link?.dataset.route === "/trips/new/plan");
    T("한 답변에 복수 선택지를 순서대로 표시한다",
      [...messagesEl(d).querySelectorAll(".support-chat-link")].map((item) => item.textContent).join("|")
      === "여행 만들기 →|내 여행 보기 →|여행 일정 열기 →");
  }
  {
    const unknown = Object.assign(message(1, "BOT", "임의 안내"), { actionKey: "EXTERNAL_SITE" });
    const { d, chat } = await boot(() => ok({ room: room(), messages: [unknown] }),
      { withSocket: false });
    await chat.load();
    T("알 수 없는 액션은 버튼으로 만들지 않는다",
      messagesEl(d).querySelector(".support-chat-link") === null);
  }

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
    T("대기 안내 문구를 보여준다", statusText(d).textContent.includes("기다리는"));
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

  /* ── 대기 말풍선. 상태줄만으로는 놓칠 수 있어(QA 피드백) 대화창 안에도 확실히 보여야 한다 ── */
  {
    const { d, chat } = await boot(
      () => ok({ room: room({ status: "BOT" }), messages: [message(1, "USER", "환불 문의드립니다")] }),
      { withSocket: false }
    );
    await chat.load();
    await until(() => statusText(d).textContent.length > 0);

    T("봇 답변 대기 중엔 대화창 안에 타이핑 말풍선이 보인다",
      messagesEl(d).querySelector(".support-chat-typing-bot") !== null);
    T("상담원 연결 말풍선은 아직 보이지 않는다",
      messagesEl(d).querySelector(".support-chat-typing-handoff") === null);
  }
  {
    const { d, chat } = await boot(
      () => ok({ room: room({ status: "WAITING" }), messages: [message(1, "USER", "환불 문의드립니다"), message(2, "BOT", "상담원에게 연결해 드릴게요.")] }),
      { withSocket: false }
    );
    await chat.load();
    await until(() => statusText(d).textContent.length > 0);

    T("상담원 연결 대기 중엔 대화창 안에 연결 중 말풍선이 보인다",
      messagesEl(d).querySelector(".support-chat-typing-handoff") !== null);
  }

  /* ── 서버가 보내는 복구 가능한 오류를 실제로 받는다 ── */
  {
    const { d, log, chat, socket } = await boot(
      () => ok({ room: room({ status: "BOT" }), messages: [message(1, "USER", "환불 문의드립니다")] })
    );
    await chat.load();
    await until(() => statusText(d).textContent.includes("기다리는"));
    await until(() => log.some((e) => e.type === "subscribe" && e.destination === "/user/queue/support-chat/errors"));

    socket.subs["/user/queue/support-chat/errors"]({
      body: JSON.stringify({ type: "VALIDATION_ERROR", code: "INVALID_SUPPORT_CHAT_REQUEST", message: "메시지는 2000자 이하여야 합니다.", retryable: false }),
    });

    T("오류 큐 메시지를 화면에 보여준다", statusText(d).textContent.includes("2000자"));
    T("재시도 불가능한 오류면 잠긴 입력창을 풀어 준다", chatInput(d).disabled === false);
  }

  /*
   * ── 느리게 도착한 옛 응답이 빠르게 도착한 새 응답을 덮어쓰지 않는다 (2차 리뷰) ──
   *
   * 첫 load()(A)가 아직 응답을 못 받은 사이 두 번째 load()(B)를 부르고, B가 먼저 응답을
   * 받아 화면에 그려진다. 그 뒤에야 A의 응답이(더 낡은 내용으로) 도착한다 — A가 화면을
   * 다시 옛 상태로 덮어쓰면 안 된다.
   */
  {
    let callCount = 0;
    let resolveFirst = null;
    const staleView = { room: room({ status: "BOT" }), messages: [message(1, "USER", "환불 문의드립니다")] };
    const freshView = {
      room: room({ status: "BOT" }),
      messages: [message(1, "USER", "환불 문의드립니다"), message(2, "BOT", "환불 절차를 안내해 드릴게요.")],
    };

    const { d, chat } = await boot((url) => {
      if (url === "/api/v1/support/chat") {
        callCount += 1;
        if (callCount === 1) {
          return new Promise((resolve) => { resolveFirst = () => resolve(ok(staleView)); });
        }
        return ok(freshView);
      }
      return ok({});
    }, { withSocket: false });

    const firstLoad = chat.load(); /* A: 아직 응답 대기 중. */
    await until(() => callCount === 1);
    await chat.load(); /* B: 곧바로 응답을 받아 화면에 반영된다. */

    T("빠르게 도착한 새 응답이 먼저 화면에 반영된다", messagesEl(d).children.length === 2);

    resolveFirst(); /* 이제야 A의 낡은 응답이 도착한다. */
    await firstLoad;
    await new Promise((resolve) => setTimeout(resolve, 20));

    T("느리게 도착한 옛 응답이 화면을 다시 덮어쓰지 않는다", messagesEl(d).children.length === 2);
  }

  /* ── 연결이 안 되는 동안 REST 폴백 폴링이 방을 갱신한다 (2차 리뷰) ── */
  {
    const { d, log, chat, timers } = await boot(
      () => ok({ room: room({ status: "BOT" }), messages: [message(1, "USER", "환불 문의드립니다")] }),
      { withSocket: false } /* SockJS/Stomp가 없는 환경 — 핸드셰이크·nginx 실패와 같은 결과. */
    );
    await chat.load();
    const fetchesBefore = log.filter((e) => e.type === "fetch").length;
    const [intervalId] = timers.ids();

    T("연결할 수 없으면 폴백 폴링을 예약해 둔다", intervalId !== undefined);

    timers.fire(intervalId);
    await until(() => log.filter((e) => e.type === "fetch").length > fetchesBefore);

    T("연결이 안 된 동안 폴백 폴링이 방을 다시 불러온다",
      log.filter((e) => e.type === "fetch").length > fetchesBefore);

    d.querySelector('[data-support-tab="faq"]').click();
    T("다른 고객센터 탭으로 이동하면 폴백 폴링을 정리한다", timers.ids().length === 0);
  }

  /* ── 탭 이탈 전에 시작된 느린 REST 응답도 채팅을 다시 활성화하지 않는다 ── */
  {
    let finishRequest;
    const response = new Promise((resolve) => { finishRequest = resolve; });
    const { d, chat, timers } = await boot(() => response, { withSocket: false });
    const loading = chat.load();

    d.querySelector('[data-support-tab="faq"]').click();
    finishRequest(ok({ room: room({ status: "BOT" }), messages: [] }));
    await loading;

    T("탭 이탈 뒤 도착한 REST 응답은 폴백 폴링을 다시 만들지 않는다", timers.ids().length === 0);
  }

  /* ── 연결돼 있으면 폴백 폴링이 불필요한 조회를 하지 않는다 ── */
  {
    const { d, log, chat, timers } = await boot(
      () => ok({ room: room({ status: "WAITING" }), messages: [] })
    ); /* withSocket: true(기본), 곧 연결된다. */
    await chat.load();
    await until(() => log.some((e) => e.type === "subscribe" && e.destination === "/topic/support-chat/rooms/5"));
    const fetchesBefore = log.filter((e) => e.type === "fetch").length;

    const [intervalId] = timers.ids();
    timers.fire(intervalId);
    await new Promise((resolve) => setTimeout(resolve, 20));

    T("연결돼 있으면 폴백 폴링이 조회를 건너뛴다",
      log.filter((e) => e.type === "fetch").length === fetchesBefore);
  }

  /* ── 상담원에게 넘어간 뒤의 탈출구 ──
   *
   * 상태 전환에 → BOT 경로가 없고 방을 닫는 것도 관리자만 할 수 있어서, WAITING이 되면
   * 손님은 봇을 다시 쓸 수도 새 대화를 시작할 수도 없었다.
   */
  {
    const { d, log, chat } = await boot(
      () => ok({ room: room({ status: "WAITING" }), messages: [message(1, "USER", "환불 문의드립니다")] }),
      { withSocket: false }
    );
    await chat.load();
    await until(() => actionsEl(d).hidden === false);

    T("상담원 대기 중이면 봇으로 돌아가는 길이 보인다", returnBtn(d).hidden === false);
    T("새 상담 시작도 함께 보인다", restartBtn(d).hidden === false);

    returnBtn(d).click();
    await until(() => log.some((e) => e.url === "/api/v1/support/chat/return-to-bot"));
    T("봇 복귀를 서버에 요청한다",
      log.some((e) => e.type === "fetch" && e.url === "/api/v1/support/chat/return-to-bot"));
  }
  {
    /* 사람이 응대 중인 대화는 뺏지 않는다. 별개 문의는 새 방에서. */
    const { d, chat } = await boot(
      () => ok({ room: room({ status: "ASSIGNED" }), messages: [message(1, "ADMIN", "담당자입니다.")] }),
      { withSocket: false }
    );
    await chat.load();
    await until(() => actionsEl(d).hidden === false);

    T("상담원 응대 중에는 봇으로 되돌리는 길이 없다", returnBtn(d).hidden === true);
    T("대신 새 상담은 시작할 수 있다", restartBtn(d).hidden === false);
  }
  {
    /* 봇이 응대 중일 때 탈출구가 떠 있으면 무엇을 하라는 건지 알 수 없다. */
    const { d, chat } = await boot(
      () => ok({ room: room({ status: "BOT" }), messages: [message(1, "BOT", "무엇을 도와드릴까요?")] }),
      { withSocket: false }
    );
    await chat.load();
    await until(() => statusText(d).textContent.length > 0);

    T("봇 응대 중에는 탈출구를 보여주지 않는다", actionsEl(d).hidden === true);
  }

  closeAllWindows();
  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed > 0) process.exit(1);
}

run().catch((error) => { closeAllWindows(); console.error(error); process.exit(1); });
