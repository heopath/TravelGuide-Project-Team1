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

/** 마이티를 띄운다. 프래그먼트만 담은 문서에 올린다 — 화면 전체는 필요 없다. */
async function boot(responder) {
  const fragment = fs.readFileSync(FRAGMENT, "utf8")
    /* th: 속성은 브라우저에서 의미가 없다. 서버가 렌더한 뒤의 모습으로 본다. */
    .replace(/\sth:fragment="[^"]*"/g, "");

  const dom = new JSDOM(`<!doctype html><html><body>${fragment}</body></html>`,
    { url: "http://localhost/home", runScripts: "outside-only" });
  const w = dom.window;
  const calls = [];

  w.fetch = async (url, request = {}) => {
    calls.push({
      url: String(url),
      method: (request.method || "GET").toUpperCase(),
      body: request.body,
      allMyTripsLoading: request.allMyTripsLoading,
    });
    return responder(String(url), request || {});
  };

  w.eval(fs.readFileSync(JS, "utf8"));
  return { w, d: w.document, calls };
}

const el = (d, s) => d.querySelector(s);

async function run() {
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
  }

  /* ── 열기 ── */
  {
    const { d, calls } = await boot(() => ok({
      room: room("BOT"),
      messages: [said("BOT", "안녕하세요! 무엇을 도와드릴까요?"), said("USER", "티켓 QR 어디서 봐요?")],
    }));
    el(d, "[data-mighty-open]").click();
    await until(() => el(d, "[data-mighty-log]").children.length === 2);

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
      await until(() => el(d, "[data-mighty-log]").children.length === 1);
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
    await until(() => el(d, "[data-mighty-actions]").hidden === false);

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
    await until(() => el(d, "[data-mighty-actions]").hidden === false);

    test("상담원 응대 중에는 봇으로 되돌리는 길이 없다",
      el(d, "[data-mighty-return]").hidden === true);
    test("대신 새 상담은 시작할 수 있다",
      el(d, "[data-mighty-restart]").hidden === false);
  }
  {
    /* 봇이 응대 중일 때 탈출구가 떠 있으면 무엇을 하라는 건지 알 수 없다. */
    const { d } = await boot(() => ok({
      room: room("BOT"),
      messages: [said("BOT", "안녕하세요!")],
    }));
    el(d, "[data-mighty-open]").click();
    await until(() => el(d, "[data-mighty-log]").children.length === 1);

    test("봇 응대 중에는 탈출구를 보여주지 않는다",
      el(d, "[data-mighty-actions]").hidden === true);
  }

  console.log("\n" + passed + " passed, " + failed + " failed");

  /*
   * 마이티는 열려 있는 동안 3초마다 새 말을 확인한다. 확인용으로 띄운 jsdom 창이 여럿
   * 남아 그 타이머가 살아 있으면 node가 끝나지 않는다. 볼 것은 다 봤으니 여기서 닫는다.
   */
  process.exit(failed > 0 ? 1 : 0);
}

run().catch((error) => { console.error(error); process.exitCode = 1; });
