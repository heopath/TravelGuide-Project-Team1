/* 마이티 수용 기준 (#191)
 *
 * 화면 구석에 늘 서 있는 도우미다. 그래서 두 가지가 중요하다.
 *  1. 손님이 하려던 일을 방해하지 않는가 — 안 눌렀는데 창이 떠 있거나, 읽을 것이 없는데
 *     빨간 점이 붙어 있으면 안 된다.
 *  2. 기존 상담과 같은 대화인가 — 별도 방을 만들면 마이페이지에서 하던 이야기가 끊긴다.
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");
const { readMarkup } = require("./markup");

const ROOT = path.resolve(__dirname, "../../..");
const FRAGMENT = path.join(ROOT, "src/main/resources/templates/fragments/mighty.html");
const JS = path.join(ROOT, "src/main/resources/static/js/core/mighty.js");
const LIVE_JS = path.join(ROOT, "src/main/resources/static/js/core/support-chat-live.js");
const TEMPLATES = path.join(ROOT, "src/main/resources/templates");

let passed = 0;
let failed = 0;
function test(name, condition, detail) {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name + (detail ? " — " + detail : "")); }
}
function until(predicate, timeoutMs = 3000) {
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

const ok = (data) => ({ ok: true, status: 200, json: async () => ({ success: true, data }) });
const fail = (status, message) =>
  ({ ok: false, status, json: async () => ({ success: false, message }) });

const room = (status) => ({ supportChatRoomId: 1, status: status || "BOT" });
const said = (senderType, content) => ({ senderType, content });

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

function fakeTimers() {
  const intervals = new Map();
  let nextId = 1;
  return {
    setInterval(fn) { const id = nextId++; intervals.set(id, fn); return id; },
    clearInterval(id) { intervals.delete(id); },
    ids() { return [...intervals.keys()]; },
    fire(id) { intervals.get(id)?.(); },
  };
}

/** jsdom에는 PointerEvent가 없다. 드래그 이동 테스트에 필요한 최소 속성만 흉내 낸다. */
function definePointerEvent(w) {
  w.PointerEvent = class PointerEvent extends w.Event {
    constructor(type, init = {}) {
      super(type, init);
      this.clientX = init.clientX || 0;
      this.clientY = init.clientY || 0;
      this.pointerId = init.pointerId || 1;
      this.button = init.button || 0;
    }
  };
}

/** 마이티를 띄운다. 프래그먼트만 담은 문서에 올린다 — 화면 전체는 필요 없다. */
async function boot(responder, { withSocket = false, withFakeTimers = false, withPointerEvents = false } = {}) {
  const fragment = fs.readFileSync(FRAGMENT, "utf8")
    /* th: 속성은 브라우저에서 의미가 없다. 서버가 렌더한 뒤의 모습으로 본다. */
    .replace(/\sth:fragment="[^"]*"/g, "");

  const dom = new JSDOM(`<!doctype html><html><body>${fragment}</body></html>`,
    { url: "http://localhost/home", runScripts: "outside-only" });
  const w = dom.window;
  const calls = [];
  const socketLog = [];

  w.fetch = async (url, request = {}) => {
    calls.push({
      url: String(url),
      method: (request.method || "GET").toUpperCase(),
      body: request.body,
      allMyTripsLoading: request.allMyTripsLoading,
    });
    return responder(String(url), request || {});
  };

  const socket = withSocket ? fakeSocket(socketLog) : null;
  if (socket) {
    w.SockJS = socket.FakeSockJS;
    w.Stomp = socket.Stomp;
  }
  const timers = withFakeTimers ? fakeTimers() : null;
  if (timers) {
    w.setInterval = timers.setInterval;
    w.clearInterval = timers.clearInterval;
  }
  if (withPointerEvents) definePointerEvent(w);

  w.eval(fs.readFileSync(LIVE_JS, "utf8"));
  w.eval(fs.readFileSync(JS, "utf8"));
  return { w, d: w.document, calls, socket, socketLog, timers };
}

const el = (d, s) => d.querySelector(s);

async function run() {
  /* ── 공통 WebSocket 우선, 장애 시에만 폴백 ── */
  {
    const { d, calls, socket, socketLog, timers } = await boot(() => ok({
      room: room("BOT"), messages: [said("BOT", "안녕하세요")],
    }), { withSocket: true, withFakeTimers: true });
    el(d, "[data-mighty-open]").click();
    await until(() => socketLog.some((e) => e.destination === "/topic/support-chat/rooms/1"));
    test("마이티도 방 WebSocket 토픽을 구독한다",
      socketLog.some((e) => e.destination === "/topic/support-chat/rooms/1"));
    test("마이티도 사용자 오류 큐를 구독한다",
      socketLog.some((e) => e.destination === "/user/queue/support-chat/errors"));

    const beforeEvent = calls.length;
    socket.subs["/topic/support-chat/rooms/1"]({ body: JSON.stringify({ type: "MESSAGE" }) });
    await until(() => calls.length > beforeEvent);
    test("WebSocket 이벤트가 오면 대화를 즉시 동기화한다", calls.length > beforeEvent);

    const beforePoll = calls.length;
    timers.fire(timers.ids()[0]);
    await new Promise((resolve) => setTimeout(resolve, 10));
    test("WebSocket 연결 중에는 3초 폴백 조회를 건너뛴다", calls.length === beforePoll);
  }

  {
    const { d, calls, timers } = await boot(() => ok({ room: room("BOT"), messages: [] }),
      { withFakeTimers: true });
    el(d, "[data-mighty-open]").click();
    await until(() => calls.length > 0);
    await new Promise((resolve) => setTimeout(resolve, 20));
    const beforePoll = calls.length;
    timers.fire(timers.ids()[0]);
    await until(() => calls.length > beforePoll);
    test("WebSocket을 연결하지 못하면 3초 폴백으로 계속 조회한다", calls.length > beforePoll);
  }

  /* ── 3초 폴링 재렌더링의 스크롤 위치 ── */
  {
    /* 마지막 말이 BOT이어야 대기 말풍선이 안 붙어 메시지 개수(2)를 그대로 기대할 수 있다. */
    const { w, d } = await boot(() => ok({
      room: room("BOT"),
      messages: [said("USER", "추가 질문"), said("BOT", "첫 답변")],
    }));
    el(d, "[data-mighty-open]").click();
    await until(() => el(d, "[data-mighty-log]").children.length === 2);
    const log = el(d, "[data-mighty-log]");
    Object.defineProperty(log, "scrollHeight", { configurable: true, get: () => 1000 });
    Object.defineProperty(log, "clientHeight", { configurable: true, get: () => 200 });

    log.scrollTop = 200;
    await w.AllMyTripsMighty.refresh();
    test("마이티에서 위의 대화를 읽는 동안 3초 폴링이 위치를 유지한다", log.scrollTop === 200);

    log.scrollTop = 800;
    await w.AllMyTripsMighty.refresh();
    test("마이티가 맨 아래에 있으면 새 내용도 계속 따라간다", log.scrollTop === 1000);
  }

  /* ── AI와 별개인 직접 상담원 연결 ── */
  {
    let status = "BOT";
    const { w, d, calls } = await boot((url) => {
      if (url === "/api/v1/support/chat/request-agent") status = "WAITING";
      return ok({ room: room(status), messages: [] });
    });
    w.confirm = () => true;
    el(d, "[data-mighty-open]").click();
    await until(() => el(d, "[data-mighty-tools]").hidden === false);
    test("상담 옵션은 처음에는 대화 영역을 차지하지 않는다",
      el(d, "[data-mighty-actions]").hidden === true);
    el(d, "[data-mighty-tools]").click();
    test("+ 버튼을 누르면 상담원 연결 선택지를 표시한다",
      el(d, "[data-mighty-actions]").hidden === false
      && el(d, "[data-mighty-agent]").hidden === false);
    el(d, "[data-mighty-agent]").click();
    await until(() => calls.some((c) => c.url === "/api/v1/support/chat/request-agent"));
    test("마이티 버튼은 직접 연결 API를 POST로 호출한다",
      calls.some((c) => c.url === "/api/v1/support/chat/request-agent" && c.method === "POST"));
  }

  /* ── 답변에서 다음 화면으로 이어 주기 ── */
  {
    const { d } = await boot(() => ok({
      room: room("BOT"),
      messages: [{ senderType: "BOT", content: "원하는 방법을 선택해 주세요.",
        actionKey: "NEW_TRIP", actionKey2: "MY_TRIPS", actionKey3: "TRIP_SCHEDULE" }],
    }));
    el(d, "[data-mighty-open]").click();
    await until(() => el(d, ".mighty-link") !== null);
    test("마이티 답변에 여행 만들기 버튼을 붙인다", el(d, ".mighty-link").textContent === "여행 만들기 →");
    test("마이티 버튼은 허용된 내부 경로를 쓴다", el(d, ".mighty-link").dataset.route === "/trips/new/plan");
    test("마이티도 복수 선택지를 표시한다",
      [...d.querySelectorAll(".mighty-link")].map((item) => item.textContent).join("|")
      === "여행 만들기 →|내 여행 보기 →|여행 일정 열기 →");
  }
  {
    const { d } = await boot(() => ok({
      room: room("BOT"), messages: [{ senderType: "BOT", content: "추천 장소예요.", blocks: [{
        blockType: "PLACE_CARDS", schemaVersion: 1, payload: { items: [{
          placeId: 10, name: "남산서울타워", category: "명소", address: "서울 용산구",
          reason: "서울 야경을 한눈에 볼 수 있어요",
          description: "서울을 대표하는 전망 명소로 야간 조명이 특히 유명합니다.",
          imageUrl: "https://cdn.example.com/places/10.jpg", rating: 4.6,
        }] },
      }] }],
    }));
    el(d, "[data-mighty-open]").click();
    await until(() => el(d, ".mighty-place-card") !== null);
    test("마이티도 장소 추천을 상세 카드로 표시한다",
      el(d, ".mighty-place-card").textContent.includes("남산서울타워"));
    test("마이티 장소 카드는 DB 장소 상세 화면으로 연결한다",
      el(d, ".mighty-place-card").dataset.route === "/guide/places/10");
    test("마이티 장소 카드는 대표 이미지를 보여준다",
      el(d, ".mighty-place-card-image").src === "https://cdn.example.com/places/10.jpg");
    test("마이티 장소 카드는 평점을 보여준다",
      el(d, ".mighty-place-card-rating").textContent === "★ 4.6");
    test("마이티 장소 카드는 추천 이유와 별도로 설명을 보여준다",
      el(d, ".mighty-place-card-description").textContent
      === "서울을 대표하는 전망 명소로 야간 조명이 특히 유명합니다.");
  }
  {
    const { d } = await boot(() => ok({
      room: room("BOT"), messages: [{ senderType: "BOT", content: "장소 하나 알려드릴게요.", blocks: [{
        blockType: "PLACE_CARDS", schemaVersion: 1, payload: { items: [{
          placeId: 11, name: "경복궁", category: "명소", address: "서울 종로구",
        }] },
      }] }],
    }));
    el(d, "[data-mighty-open]").click();
    await until(() => el(d, ".mighty-place-card") !== null);
    test("이미지·평점·설명이 없는 마이티 카드도 정상 렌더링한다",
      el(d, ".mighty-place-card").textContent.includes("경복궁"));
    test("이미지가 없으면 마이티 카드에 이미지 요소를 만들지 않는다",
      el(d, ".mighty-place-card-image") === null);
    test("평점이 없으면 마이티 카드에 평점 요소를 만들지 않는다",
      el(d, ".mighty-place-card-rating") === null);
  }

  /* ── 대기 말풍선. 상태줄만으로는 놓칠 수 있어(마이페이지 상담 채팅에서 먼저 반영) 마이티
     대화창 안에도 확실히 보여야 한다 ── */
  {
    const { d } = await boot(() => ok({
      room: room("BOT"), messages: [said("USER", "환불 문의드립니다")],
    }));
    el(d, "[data-mighty-open]").click();
    await until(() => el(d, "[data-mighty-state]").textContent !== "마이티를 연결하는 중이에요");
    test("봇 답변 대기 중엔 마이티 대화창 안에 타이핑 말풍선이 보인다",
      el(d, ".mighty-typing-bot") !== null);
    test("상담원 연결 말풍선은 아직 보이지 않는다",
      el(d, ".mighty-typing-handoff") === null);
    test("봇 답변 대기 중엔 상태줄도 대기 문구를 보여준다",
      el(d, "[data-mighty-state]").textContent === "답변을 기다리는 중입니다...");
  }
  {
    const { d } = await boot(() => ok({
      room: room("BOT"),
      messages: [said("USER", "환불 문의드립니다"), said("BOT", "환불 절차를 안내해 드릴게요.")],
    }));
    el(d, "[data-mighty-open]").click();
    await until(() => el(d, "[data-mighty-state]").textContent !== "마이티를 연결하는 중이에요");
    test("봇이 이미 답했으면 마이티에 타이핑 말풍선이 없다",
      el(d, ".mighty-typing-bot") === null);
  }
  {
    const { d } = await boot(() => ok({
      room: room("WAITING"),
      messages: [said("USER", "환불 문의드립니다"), said("BOT", "상담원에게 연결해 드릴게요.")],
    }));
    el(d, "[data-mighty-open]").click();
    await until(() => el(d, "[data-mighty-state]").textContent !== "마이티를 연결하는 중이에요");
    test("상담원 연결 대기 중엔 마이티 대화창 안에 연결 중 말풍선이 보인다",
      el(d, ".mighty-typing-handoff") !== null);
  }

  /* ── 끌어서 위치 옮기기(여행 가이드 등 다른 UI와 겹칠 때 손님이 직접 비켜 준다) ── */
  {
    const { w, d } = await boot(() => ok({ room: room("BOT"), messages: [] }),
      { withPointerEvents: true });
    const root = el(d, "[data-mighty-root]");
    const button = el(d, "[data-mighty-open]");
    const panel = el(d, "[data-mighty-panel]");

    button.dispatchEvent(new w.PointerEvent("pointerdown",
      { clientX: 100, clientY: 100, pointerId: 1, bubbles: true }));
    d.dispatchEvent(new w.PointerEvent("pointermove",
      { clientX: 140, clientY: 130, pointerId: 1, bubbles: true }));
    d.dispatchEvent(new w.PointerEvent("pointerup",
      { clientX: 140, clientY: 130, pointerId: 1, bubbles: true }));

    test("버튼을 끌면 옮긴 만큼 위치가 이동한다(아이콘이 pivot, top·right 기준)",
      root.style.top === "30px" && parseFloat(root.style.right) === w.innerWidth - 40,
      root.style.top + "," + root.style.right);
    test("위치를 옮기면 왼쪽·아래 고정을 푼다",
      root.style.left === "auto" && root.style.bottom === "auto");
    test("옮긴 위치를 저장해 다른 화면에서도 남는다",
      w.localStorage.getItem("allMyTripsMightyPosition") !== null);

    button.dispatchEvent(new w.Event("click", { bubbles: true }));
    test("드래그 직후 따라오는 click은 열기로 처리하지 않는다", panel.hidden === true);

    button.dispatchEvent(new w.Event("click", { bubbles: true }));
    test("끌지 않은 다음 클릭은 평소대로 연다", panel.hidden === false);
  }
  {
    /* 대화창이 열려 있을 때는 머리글을 손잡이로 쓴다. 손님 말 입력창은 끌리지 않는다. */
    const { w, d } = await boot(() => ok({ room: room("BOT"), messages: [] }),
      { withPointerEvents: true });
    const root = el(d, "[data-mighty-root]");
    el(d, "[data-mighty-open]").click();
    const head = d.querySelector(".mighty-head");

    head.dispatchEvent(new w.PointerEvent("pointerdown",
      { clientX: 50, clientY: 50, pointerId: 2, bubbles: true }));
    /* 오른쪽으로 너무 끌어도(오른쪽 여백이 뷰포트 너비를 넘어도) 클램프돼야 한다. */
    d.dispatchEvent(new w.PointerEvent("pointermove",
      { clientX: 20, clientY: 90, pointerId: 2, bubbles: true }));
    d.dispatchEvent(new w.PointerEvent("pointerup",
      { clientX: 20, clientY: 90, pointerId: 2, bubbles: true }));

    test("열린 대화창은 머리글을 끌어서 옮긴다",
      root.style.top === "40px" && parseFloat(root.style.right) === w.innerWidth,
      root.style.top + "," + root.style.right);
  }
  {
    /* 대화창이 열리며 커진 root가 화면 아래로 넘치면 pivot을 위로 당겨 보정한다. */
    const { w, d } = await boot(() => ok({ room: room("BOT"), messages: [] }),
      { withPointerEvents: true });
    const root = el(d, "[data-mighty-root]");
    const button = el(d, "[data-mighty-open]");

    button.dispatchEvent(new w.PointerEvent("pointerdown",
      { clientX: 100, clientY: 100, pointerId: 3, bubbles: true }));
    d.dispatchEvent(new w.PointerEvent("pointermove",
      { clientX: 100, clientY: 900, pointerId: 3, bubbles: true }));
    d.dispatchEvent(new w.PointerEvent("pointerup",
      { clientX: 100, clientY: 900, pointerId: 3, bubbles: true }));
    const draggedTop = root.style.top;

    Object.defineProperty(root, "getBoundingClientRect", {
      configurable: true,
      value: () => ({ top: parseFloat(draggedTop), bottom: parseFloat(draggedTop) + 520, left: 0, right: 300, width: 300, height: 520 }),
    });
    /* 드래그 직후 따라오는 click은 억제되므로 한 번 더 눌러야 실제로 연다. */
    button.dispatchEvent(new w.Event("click", { bubbles: true }));
    button.dispatchEvent(new w.Event("click", { bubbles: true }));

    test("대화창이 열려 화면 아래로 넘치면 자동으로 위로 당겨 보정한다",
      parseFloat(root.style.top) < parseFloat(draggedTop),
      draggedTop + " -> " + root.style.top);
  }

  /* ── 화면에 실렸는가 ── */
  {
    /* 손님이 쉬며 둘러보는 화면에는 다 있어야 한다. 한 곳만 빠지면 거기서만 없다. */
    const shouldHave = ["home/home.html", "booking/flights.html", "booking/ticket.html",
      "guide/guide.html", "trips/schedule.html", "mypage/mypage.html"];
    const missing = shouldHave.filter((p) =>
      !readMarkup(path.join(TEMPLATES, p)).includes("fragments/mighty"));
    test("손님 화면에 마이티가 실려 있다", missing.length === 0, missing.join(", "));

    /* 일하는 화면과 결제 중에 마스코트가 떠 있으면 방해가 된다. */
    const shouldNot = ["admin/admin.html", "auth/login.html",
      "payment/toss-return.html", "payment/kakao-return.html"];
    const wrong = shouldNot.filter((p) => {
      const f = path.join(TEMPLATES, p);
      return fs.existsSync(f) && readMarkup(f).includes("fragments/mighty");
    });
    test("관리자·로그인·결제 결과 화면에는 없다", wrong.length === 0, wrong.join(", "));
  }

  /* ── 처음 모습 ── */
  {
    const { d } = await boot(() => ok({ room: room(), messages: [] }));
    /* 안 눌렀는데 창이 떠 있으면 화면을 가린다. */
    test("대화창은 처음에 닫혀 있다", el(d, "[data-mighty-panel]").hidden === true);
    /* 읽을 것이 없는데 빨간 점이 붙어 있으면 눌러 보게 된다. */
    test("안 읽은 표시도 처음에는 없다", el(d, "[data-mighty-dot]").hidden === true);
    test("버튼이 눌리는 상태다", el(d, "[data-mighty-open]").disabled !== true);
    test("눌리기 전에는 서버를 부르지 않는다",
      el(d, "[data-mighty-log]").children.length === 0);
    test("입력 폼은 공통 전체 화면 로더를 사용하지 않는다",
      el(d, "[data-mighty-form]").hasAttribute("data-no-global-loading"));
    test("보낼 말 설명은 보조기기용 라벨로만 남긴다",
      el(d, "[data-mighty-form] label").classList.contains("sr-only"));
  }

  /* ── 열기 ── */
  {
    /* 마지막 말이 BOT이어야 대기 말풍선이 안 붙어 메시지 개수(3)를 그대로 기대할 수 있다. */
    const { d, calls } = await boot(() => ok({
      room: room("BOT"),
      messages: [said("BOT", "안녕하세요! 무엇을 도와드릴까요?"), said("USER", "티켓 QR 어디서 봐요?"),
        said("BOT", "QR은 예매한 티켓 상세에서 확인할 수 있어요.")],
    }));
    el(d, "[data-mighty-open]").click();
    await until(() => el(d, "[data-mighty-log]").children.length === 3);

    /* 별도 방을 만들면 마이페이지에서 하던 이야기가 끊긴다. */
    test("기존 상담 채팅을 그대로 부른다",
      calls.some((c) => c.url === "/api/v1/support/chat" && c.method === "POST"),
      calls.map((c) => c.method + " " + c.url).join(" | "));
    test("마이티 조회는 전체 화면 로더를 띄우지 않는다",
      calls.find((c) => c.url === "/api/v1/support/chat")?.allMyTripsLoading === false);
    test("대화창이 열린다", el(d, "[data-mighty-panel]").hidden === false);
    test("보조기기에도 열렸다고 알린다",
      el(d, "[data-mighty-open]").getAttribute("aria-expanded") === "true");

    const lines = [...d.querySelectorAll("[data-mighty-log] > li")];
    test("손님 말과 상대 말을 갈라 놓는다",
      lines[0].className.includes("them") && lines[1].className.includes("me"));
    test("상대가 누구인지 밝힌다", lines[0].querySelector("small").textContent === "마이티");
    /* 상담원이 받으면 마이티가 아니라 사람이 답하는 것이다. 그걸 숨기면 안 된다. */
    test("손님 말에는 보낸이를 붙이지 않는다", lines[1].querySelector("small") === null);
  }

  /* ── 응답이 느릴 때 ── */
  {
    const pending = new Promise(() => {});
    const { w, d, calls } = await boot(() => pending);
    el(d, "[data-mighty-open]").click();
    await until(() => calls.length === 1);

    test("느린 동안에는 봇 창 안에서만 불러오는 중이라고 알린다",
      el(d, "[data-mighty-empty]").textContent.includes("불러오는 중"));

    /* 수동 새로고침이나 3초 확인이 겹쳐도 진행 중인 요청 하나만 남아야 한다. */
    w.AllMyTripsMighty.refresh();
    w.AllMyTripsMighty.refresh();
    await new Promise((resolve) => setTimeout(resolve, 20));
    test("응답이 늦어도 조회 요청을 겹쳐 보내지 않는다", calls.length === 1, String(calls.length));
  }

  /* ── 상태 문구 ── */
  {
    for (const [status, expected] of [
      ["WAITING", "상담원을 기다리"], ["ASSIGNED", "상담원과 이야기"], ["CLOSED", "지난 상담"],
    ]) {
      const { d } = await boot(() => ok({ room: room(status), messages: [said("BOT", "안녕하세요")] }));
      el(d, "[data-mighty-open]").click();
      /*
       * WAITING은 연결 중 말풍선이 함께 붙어 로그 칸 수가 늘어나므로 상태 문구로 완료를 본다.
       * open()이 먼저 "연결하는 중" 문구를 넣고 refresh()가 비동기로 실제 상태를 반영하므로,
       * 그 초기 문구와 달라질 때까지 기다려야 실제 draw() 완료 시점을 잡을 수 있다.
       */
      await until(() => el(d, "[data-mighty-state]").textContent !== "마이티를 연결하는 중이에요");
      /* 답을 기다려도 되는지가 여기서 갈린다. */
      test(`${status}이면 그렇다고 적는다`,
        el(d, "[data-mighty-state]").textContent.includes(expected),
        el(d, "[data-mighty-state]").textContent);
    }
  }

  /* ── 로그인하지 않았을 때 ── */
  {
    const { d } = await boot(() => fail(401, "로그인이 필요합니다."));
    el(d, "[data-mighty-open]").click();
    await until(() => el(d, "[data-mighty-form]").hidden === true);

    /* 눌러볼 수는 있어야 무엇인지 안다. 다만 칠 수 없다는 것을 밝힌다. */
    test("로그인이 필요하다고 알린다",
      el(d, "[data-mighty-empty]").textContent.includes("로그인하면"));
    test("칠 수 없을 때는 입력창을 감춘다", el(d, "[data-mighty-form]").hidden === true);
  }

  /* ── 보내기 ── */
  {
    const sent = [];
    const { d, calls } = await boot((url, request) => {
      if (url.includes("/messages")) { sent.push(JSON.parse(request.body)); return ok(null); }
      return ok({ room: room("BOT"), messages: [said("BOT", "안녕하세요")] });
    });
    el(d, "[data-mighty-open]").click();
    await until(() => el(d, "[data-mighty-log]").children.length === 1);

    el(d, "[data-mighty-input]").value = "티켓 취소하고 싶어요";
    el(d, "[data-mighty-form]").dispatchEvent(
      new d.defaultView.Event("submit", { bubbles: true, cancelable: true }));

    /*
     * 답이 올 때까지 화면이 그대로면 눌렸는지 알 수 없어 다시 누르게 된다.
     * 보낸 말을 먼저 그려야 한다.
     */
    test("보내자마자 화면에 그린다",
      [...d.querySelectorAll("[data-mighty-log] > li")].some(
        (li) => li.className.includes("me") && li.textContent.includes("티켓 취소")));
    test("입력창을 비운다", el(d, "[data-mighty-input]").value === "");

    await until(() => sent.length === 1);
    test("보낸 말을 그대로 서버에 넘긴다", sent[0].content === "티켓 취소하고 싶어요");
    test("보내는 주소가 맞다", calls.some((c) => c.url === "/api/v1/support/chat/messages"));
  }
  {
    /* 못 보낸 말을 다시 치게 하면 화가 난다. */
    const { d } = await boot((url) => {
      if (url.includes("/messages")) return fail(500, "지금은 보낼 수 없어요.");
      return ok({ room: room("BOT"), messages: [] });
    });
    el(d, "[data-mighty-open]").click();
    await until(() => el(d, "[data-mighty-form]").hidden === false);

    el(d, "[data-mighty-input]").value = "안 갈 말";
    el(d, "[data-mighty-form]").dispatchEvent(
      new d.defaultView.Event("submit", { bubbles: true, cancelable: true }));
    await until(() => el(d, "[data-mighty-input]").value === "안 갈 말");

    test("못 보내면 쓴 말을 돌려준다", el(d, "[data-mighty-input]").value === "안 갈 말");
    test("못 보낸 이유를 알린다",
      el(d, "[data-mighty-empty]").textContent.includes("보낼 수 없"),
      el(d, "[data-mighty-empty]").textContent);
    test("다시 누를 수 있다", el(d, "[data-mighty-send]").disabled === false);
  }

  /* ── 닫기 ── */
  {
    const { d } = await boot(() => ok({ room: room(), messages: [said("BOT", "안녕하세요")] }));
    const panel = el(d, "[data-mighty-panel]");
    el(d, "[data-mighty-open]").click();
    await until(() => panel.hidden === false);

    el(d, "[data-mighty-close]").click();
    test("닫기 버튼으로 닫힌다", panel.hidden === true);
    test("닫으면 보조기기에도 알린다",
      el(d, "[data-mighty-open]").getAttribute("aria-expanded") === "false");

    el(d, "[data-mighty-open]").click();
    await until(() => panel.hidden === false);
    d.dispatchEvent(new d.defaultView.KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    test("Esc로도 닫힌다", panel.hidden === true);
  }

  /* ── 상담원에게 넘어간 뒤의 탈출구 ──
   *
   * 상태 전환에 → BOT 경로가 없고 방을 닫는 것도 관리자만 할 수 있어서, WAITING이 되면
   * 손님은 봇을 다시 쓸 수도 새 대화를 시작할 수도 없었다.
   */
  {
    const { d, calls } = await boot(() => ok({
      room: room("WAITING"),
      messages: [said("USER", "환불 문의드립니다"), said("BOT", "상담원에게 연결해 드릴게요.")],
    }));
    el(d, "[data-mighty-open]").click();
    await until(() => el(d, "[data-mighty-tools]").hidden === false);
    el(d, "[data-mighty-tools]").click();

    test("상담원 대기 중이면 봇으로 돌아가는 길이 보인다",
      el(d, "[data-mighty-return]").hidden === false);
    test("새 상담 시작도 함께 보인다",
      el(d, "[data-mighty-restart]").hidden === false);

    el(d, "[data-mighty-return]").click();
    await until(() => calls.some((c) => c.url === "/api/v1/support/chat/return-to-bot"));
    test("봇 복귀를 서버에 요청한다",
      calls.some((c) => c.url === "/api/v1/support/chat/return-to-bot" && c.method === "POST"));
  }
  {
    /* 사람이 응대 중인 대화는 뺏지 않는다. 별개 문의는 새 방에서. */
    const { d } = await boot(() => ok({
      room: room("ASSIGNED"),
      messages: [said("ADMIN", "안녕하세요, 담당자입니다.")],
    }));
    el(d, "[data-mighty-open]").click();
    await until(() => el(d, "[data-mighty-tools]").hidden === false);
    el(d, "[data-mighty-tools]").click();

    test("상담원 응대 중에는 봇으로 되돌리는 길이 없다",
      el(d, "[data-mighty-return]").hidden === true);
    test("대신 새 상담은 시작할 수 있다",
      el(d, "[data-mighty-restart]").hidden === false);
  }
  {
    /* BOT 상태의 보조 기능은 + 버튼 안에 접어 둔다. */
    const { d } = await boot(() => ok({
      room: room("BOT"),
      messages: [said("BOT", "안녕하세요!")],
    }));
    el(d, "[data-mighty-open]").click();
    await until(() => el(d, "[data-mighty-log]").children.length === 1);

    test("봇 응대 중에는 + 버튼만 보여준다",
      el(d, "[data-mighty-tools]").hidden === false
      && el(d, "[data-mighty-actions]").hidden === true);
    el(d, "[data-mighty-tools]").click();
    test("+ 버튼을 열면 직접 상담원 연결 선택지를 보여준다",
      el(d, "[data-mighty-actions]").hidden === false
      && el(d, "[data-mighty-agent]").hidden === false);
  }

  console.log("\n" + passed + " passed, " + failed + " failed");

  /*
   * 마이티는 열려 있는 동안 3초마다 새 말을 확인한다. 확인용으로 띄운 jsdom 창이 여럿
   * 남아 그 타이머가 살아 있으면 node가 끝나지 않는다. 볼 것은 다 봤으니 여기서 닫는다.
   */
  process.exit(failed > 0 ? 1 : 0);
}

run().catch((error) => { console.error(error); process.exitCode = 1; });
