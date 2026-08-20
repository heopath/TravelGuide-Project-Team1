/* QR 결제 승인 화면 수용 기준 (#281)
 *
 * PC에 뜬 결제 QR을 폰으로 찍으면 열리는 화면이다. 여기서 누르는 순간이 실제 결제라,
 * 무엇을 얼마에 결제하는지 보여주고 나서 눌리게 해야 한다.
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/payment/qr-approve.html");
const JS = path.join(ROOT, "src/main/resources/static/js/pages/payment/qr-approve.js");

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

const summary = (overrides) => Object.assign({
  reservationId: 5,
  reservationNumber: "AMT-TKT-ABC123",
  productName: "제주 아쿠아리움 입장권",
  optionName: "성인",
  quantity: 2,
  amount: 40000,
  currency: "KRW",
  alreadyPaid: false,
  expiresAt: new Date(Date.now() + 5 * 60 * 1000).toISOString(),
  serverTime: new Date().toISOString(),
}, overrides || {});

function ok(data) {
  return { ok: true, status: 200, json: async () => ({ success: true, data }) };
}
function fail(status, message) {
  return { ok: false, status, json: async () => ({ success: false, message }) };
}

/**
 * 화면을 띄운다. 토큰은 주소에서 읽으므로 query로 넘긴다.
 *
 * 스크립트가 평가되는 즉시 조회를 시작하므로 fetch 대역을 먼저 깔아 둔다.
 */
async function boot(options = {}) {
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/pay/qr" + (options.query ?? "?token=tok-1"),
    runScripts: "outside-only",
  });
  const w = dom.window;
  const calls = [];

  w.fetch = async (url, request = {}) => {
    calls.push({ url: String(url), method: (request.method || "GET").toUpperCase(), body: request.body });
    return options.responder(String(url), request || {});
  };

  w.eval(fs.readFileSync(JS, "utf8"));
  return { w, d: w.document, calls };
}

const text = (d, selector) => d.querySelector(selector)?.textContent?.trim() || "";

async function run() {
  /* ── 승인 전 확인 ── */
  {
    const { d, calls } = await boot({ responder: () => ok(summary()) });
    await until(() => !d.querySelector("[data-pay-detail]").hidden);

    test("토큰으로 결제 내용을 불러온다",
      calls.some((c) => c.method === "GET" && c.url.includes("/api/v1/payments/qr?token=tok-1")),
      calls.map((c) => `${c.method} ${c.url}`).join(" | "));
    test("무엇을 결제하는지 보여준다", text(d, "[data-pay-product]").includes("아쿠아리움"));
    test("수량을 보여준다", text(d, "[data-pay-quantity]") === "2매");
    test("예약번호를 보여준다", text(d, "[data-pay-number]") === "AMT-TKT-ABC123");
    /* 금액을 확인하지 않고 누르게 만드는 결제 화면은 없다. */
    test("금액을 보여준다", text(d, "[data-pay-amount]") === "40,000원");
    test("모의 결제라는 사실을 밝힌다",
      !d.querySelector("[data-pay-notice]").hidden
      && text(d, "[data-pay-notice]").includes("실제 돈이 빠져나가지 않"));
    test("남은 시간을 밝힌다",
      /\d+분 \d+초 안에 승인/.test(text(d, "[data-pay-remain]")),
      text(d, "[data-pay-remain]"));
    test("승인 버튼을 보여준다", d.querySelector("[data-pay-approve-button]").hidden === false);
  }

  /* ── 승인 ── */
  {
    let approved = null;
    const { d, w } = await boot({
      responder: (url, request) => {
        if (url.includes("/approve")) {
          approved = JSON.parse(request.body);
          return ok({ tickets: [{ ticketNumber: "AMT-TKN-A" }, { ticketNumber: "AMT-TKN-B" }] });
        }
        return ok(summary());
      },
    });
    await until(() => !d.querySelector("[data-pay-approve-button]").hidden);

    d.querySelector("[data-pay-approve-button]").click();
    await until(() => d.querySelector("[data-pay-approve]").dataset.payDone === "1");

    test("승인하면 토큰을 그대로 보낸다", approved?.token === "tok-1");
    test("발급된 티켓 수를 알려준다", text(d, "[data-pay-state]").includes("2장"));
    /* 폰에서 승인했으니 티켓은 마이페이지에 있다. 갈 곳을 알려줘야 한다. */
    test("티켓을 볼 곳을 알려준다", d.querySelector("[data-pay-mypage]").hidden === false);
    test("승인이 끝나면 버튼을 감춘다", d.querySelector("[data-pay-approve-button]").hidden === true);
    test("승인 뒤에는 남은 시간을 세지 않는다", d.querySelector("[data-pay-remain]").hidden === true);
    void w;
  }

  /* ── 잘못된 접근 ── */
  {
    const { d, calls } = await boot({ query: "", responder: () => ok(summary()) });
    await until(() => text(d, "[data-pay-state]").includes("결제 QR 정보가 없습니다"));

    test("토큰 없이 열면 안내만 한다", text(d, "[data-pay-state]").includes("결제 QR 정보가 없습니다"));
    /* 토큰이 없는데 조회를 부르면 서버에 의미 없는 요청만 남는다. */
    test("토큰 없이 조회하지 않는다", calls.length === 0);
    test("토큰 없이 승인 버튼을 두지 않는다",
      d.querySelector("[data-pay-approve-button]").hidden === true);
  }
  {
    /* 만료·위조는 서버가 갈라서 알려준다. 화면은 그 말을 그대로 보여주면 된다. */
    const { d } = await boot({
      responder: () => fail(410, "결제 QR의 유효 시간이 지났습니다. 다시 띄워 주세요."),
    });
    await until(() => text(d, "[data-pay-state]").includes("유효 시간이 지났습니다"));

    test("만료된 QR은 서버 안내를 그대로 보여준다",
      text(d, "[data-pay-state]").includes("유효 시간이 지났습니다"));
    test("만료된 QR에는 승인 버튼을 두지 않는다",
      d.querySelector("[data-pay-approve-button]").hidden === true);
  }
  {
    /* 스캔이 늦어 이미 결제가 끝난 경우다. 눌러 봐야 거절당한다. */
    const { d } = await boot({ responder: () => ok(summary({ alreadyPaid: true })) });
    await until(() => text(d, "[data-pay-state]").includes("이미 결제가 끝난"));

    test("이미 결제된 예약이면 그 사실을 알린다", text(d, "[data-pay-state]").includes("이미 결제가 끝난"));
    test("이미 결제됐으면 승인 버튼을 두지 않는다",
      d.querySelector("[data-pay-approve-button]").hidden === true);
    test("이미 결제됐어도 티켓 볼 곳은 알려준다",
      d.querySelector("[data-pay-mypage]").hidden === false);
  }
  {
    /*
     * 로그인이 풀린 폰으로 찍은 경우다. 화면 로드 시점에 로그인 여부를 미리 판정하지 않고,
     * 401을 받고 나서 로그인으로 보낸다(auth-state.js와의 레이스).
     */
    let moved = "";
    const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
      url: "http://localhost/pay/qr?token=tok-1", runScripts: "outside-only",
    });
    const w = dom.window;
    w.fetch = async () => fail(401, "로그인이 필요합니다.");

    /*
     * jsdom의 window.location은 바꿔치기할 수 없어서(Unforgeable) 진짜로 이동해 버린다.
     * 스크립트를 함수로 감싸 window만 대역으로 넘긴다. document·fetch는 그대로 쓴다.
     */
    const load = w.eval(`(function (window) {\n${fs.readFileSync(JS, "utf8")}\n})`);
    load({
      location: { pathname: "/pay/qr", search: "?token=tok-1",
        set href(value) { moved = value; }, get href() { return moved; } },
      setInterval: w.setInterval.bind(w),
      clearInterval: w.clearInterval.bind(w),
    });

    await until(() => moved !== "");
    test("401이면 로그인으로 보낸다", moved.startsWith("/auth/login?redirect="), moved);
    /* 돌아올 주소에 토큰이 남아 있어야 로그인 뒤 그 결제를 이어서 승인할 수 있다. */
    test("로그인 뒤 돌아올 주소에 토큰을 남긴다", moved.includes("token%3Dtok-1"), moved);
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  /*
   * 화면이 남은 시간을 1초마다 세고 있어 타이머가 살아 있다. 그냥 끝내면 프로세스가
   * 안 죽고 npm test가 여기서 멈춘다. 실패 여부를 종료 코드로 넘기고 바로 끝낸다.
   */
  process.exit(failed > 0 ? 1 : 0);
}

run().catch((error) => { console.error(error); process.exit(1); });
