/* 토스페이먼츠 결제 수용 기준 (#281)
 *
 * 우리 결제수단 중 유일하게 진짜 결제사를 거친다. 그래서 확인할 것이 둘이다.
 *  1. 시크릿 키가 브라우저로 새지 않는가 — 새면 누구나 우리 이름으로 승인을 부른다.
 *  2. 돌아온 화면이 승인을 서버에 맡기고, 실패로 돌아왔을 때 승인을 부르지 않는가.
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");
const { readMarkup } = require("./markup");

const ROOT = path.resolve(__dirname, "../../..");
const RETURN_HTML = path.join(ROOT, "src/main/resources/templates/payment/toss-return.html");
const RETURN_JS = path.join(ROOT, "src/main/resources/static/js/pages/payment/toss-return.js");
const METHODS_JS = path.join(ROOT, "src/main/resources/static/js/core/payment-methods.js");
const CHECKOUT_JS = path.join(ROOT, "src/main/resources/static/js/core/payment-checkout.js");
const FLIGHTS_HTML = path.join(ROOT, "src/main/resources/templates/booking/flights.html");
const MYPAGE_HTML = path.join(ROOT, "src/main/resources/templates/mypage/mypage.html");

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

const text = (d, selector) => d.querySelector(selector)?.textContent?.trim() || "";

function ok(data) {
  return { ok: true, status: 200, json: async () => ({ success: true, data }) };
}
function fail(status, message) {
  return { ok: false, status, json: async () => ({ success: false, message }) };
}

/** 토스에서 돌아온 화면을 띄운다. 결과는 주소에 실려 온다. */
async function boot(query, options = {}) {
  const dom = new JSDOM(fs.readFileSync(RETURN_HTML, "utf8"), {
    url: "http://localhost/pay/toss" + query,
    runScripts: "outside-only",
  });
  const w = dom.window;
  const calls = [];

  if (options.returnTo !== undefined) {
    w.sessionStorage.setItem("allmytrips.tossReturnTo", options.returnTo);
  }

  w.fetch = async (url, request = {}) => {
    calls.push({ url: String(url), method: (request.method || "GET").toUpperCase(), body: request.body });
    return (options.responder || (() => ok({ replayed: false })))(String(url), request || {});
  };

  w.eval(fs.readFileSync(RETURN_JS, "utf8"));
  return { w, d: w.document, calls };
}

/** 결제수단 선택 창을 띄운다. 토스 노출은 meta 태그의 클라이언트 키로 갈린다. */
function bootMethods(clientKey) {
  const meta = clientKey === null
    ? ""
    : '<meta name="toss-client-key" content="' + clientKey + '">';
  const dom = new JSDOM("<!doctype html><html><head>" + meta + "</head><body></body></html>",
    { runScripts: "outside-only" });
  dom.window.eval(fs.readFileSync(METHODS_JS, "utf8"));
  return dom.window;
}

async function run() {
  /* ── 성공으로 돌아온 경우 ── */
  {
    const { d, calls } = await boot(
      "?paymentKey=test_pk_1&orderId=AMT-7-abc123&amount=40000",
      { returnTo: "/mypage?tab=ticket" });
    await until(() => calls.length > 0);
    await until(() => !d.querySelector("[data-toss-back]").hidden);

    const confirm = calls.find((c) => c.url.includes("/api/v1/payments/toss/confirm"));
    test("승인을 서버에 맡긴다", Boolean(confirm) && confirm.method === "POST",
      calls.map((c) => c.method + " " + c.url).join(" | "));

    const body = confirm ? JSON.parse(confirm.body) : {};
    test("결제 키를 그대로 보낸다", body.paymentKey === "test_pk_1");
    test("주문번호를 그대로 보낸다", body.orderId === "AMT-7-abc123");
    /* 주소에는 문자열로 실려 온다. 서버는 숫자를 받는다. */
    test("금액을 숫자로 보낸다", body.amount === 40000, JSON.stringify(body));
    /* 예약 번호는 보내지 않는다. 서버가 주문번호에서 꺼내야 바꿔치기가 막힌다. */
    test("예약 번호를 화면이 정하지 않는다", body.reservationId === undefined);

    test("무엇을 얼마에 결제했는지 보여준다",
      text(d, "[data-toss-order]") === "AMT-7-abc123"
      && text(d, "[data-toss-amount]") === "40,000원");
    test("결제가 끝났음을 알린다", text(d, "[data-toss-state]").includes("결제가 완료"));
    test("테스트 결제라는 사실을 밝힌다",
      !d.querySelector("[data-toss-notice]").hidden
      && text(d, "[data-toss-notice]").includes("실제 돈이 빠져나가지 않"));
    test("결제를 시작한 화면으로 돌려보낸다",
      d.querySelector("[data-toss-back]").getAttribute("href") === "/mypage?tab=ticket");
  }

  /* ── 이미 결제된 예약 ── */
  {
    const { d } = await boot("?paymentKey=test_pk_2&orderId=AMT-7-b&amount=40000",
      { responder: () => ok({ replayed: true }) });
    await until(() => text(d, "[data-toss-state]").includes("이미"));
    /* 새로고침으로 같은 승인이 두 번 들어와도 두 번 결제되지 않았음을 말해 준다. */
    test("이미 결제된 건은 그렇다고 말한다", text(d, "[data-toss-state]").includes("이미 결제"));
  }

  /* ── 실패로 돌아온 경우 ── */
  {
    const { d, calls } = await boot(
      "?code=PAY_PROCESS_CANCELED&message=%EC%82%AC%EC%9A%A9%EC%9E%90%EA%B0%80%20%EC%B7%A8%EC%86%8C&orderId=AMT-7-c");
    await until(() => !d.querySelector("[data-toss-back]").hidden);

    /* 결제되지 않았는데 승인을 부르면 서버와 토스에 의미 없는 실패만 쌓인다. */
    test("실패로 돌아오면 승인하지 않는다", calls.length === 0,
      calls.map((c) => c.url).join(" | "));
    test("실패 사유를 보여준다", text(d, "[data-toss-state]").includes("사용자가 취소"),
      text(d, "[data-toss-state]"));
    test("다시 시도할 길을 준다", text(d, "[data-toss-back]").includes("다시"));
  }

  /* ── 서버가 승인을 거절한 경우 ── */
  {
    const { d } = await boot("?paymentKey=test_pk_3&orderId=AMT-7-d&amount=1",
      { responder: () => fail(400, "결제 요청이 올바르지 않습니다.") });
    await until(() => text(d, "[data-toss-state]").includes("올바르지"));
    test("거절 사유를 그대로 보여준다", text(d, "[data-toss-state]").includes("올바르지 않습니다"));
  }

  /* ── 돌아갈 주소는 우리 사이트 안이어야 한다 ── */
  {
    const { d } = await boot("?paymentKey=test_pk_4&orderId=AMT-7-e&amount=40000",
      { returnTo: "https://evil.example/steal" });
    await until(() => !d.querySelector("[data-toss-back]").hidden);
    /* 저장소는 다른 스크립트도 건드린다. 검사 없이 쓰면 바깥으로 보내는 발판이 된다. */
    test("바깥 주소로는 보내지 않는다",
      d.querySelector("[data-toss-back]").getAttribute("href") === "/mypage",
      d.querySelector("[data-toss-back]").getAttribute("href"));
  }
  {
    const { d } = await boot("?paymentKey=test_pk_5&orderId=AMT-7-f&amount=40000",
      { returnTo: "//evil.example/steal" });
    await until(() => !d.querySelector("[data-toss-back]").hidden);
    test("//로 시작하는 주소도 막는다",
      d.querySelector("[data-toss-back]").getAttribute("href") === "/mypage");
  }

  /* ── 결제수단 목록 ── */
  {
    const w = bootMethods(null);
    const labels = w.AllMyTripsPayment.METHODS.map((m) => m.label);
    test("결제수단 목록에 토스페이먼츠가 있다", labels.includes("토스페이먼츠로 결제"), labels.join(" | "));

    const toss = w.AllMyTripsPayment.METHODS.find((m) => m.id === "TOSS");
    /* 바로 결제되지 않는다. 부르는 쪽이 이 값으로 갈라 결제창을 띄운다. */
    test("토스는 별도 흐름으로 표시한다", toss.flow === "TOSS");
    /* 서버 CHECK 제약에 있는 값이어야 한다. */
    test("토스도 서버가 아는 결제수단이다",
      ["CARD", "TRANSFER", "VIRTUAL_ACCOUNT", "EASY_PAY"].includes(toss.method));
  }
  {
    /* 키가 없으면 결제창이 뜨지 않는다. 고를 수 있게 두면 눌러도 아무 일이 없다. */
    const w = bootMethods(null);
    const promise = w.AllMyTripsPayment.choose({ summary: "티켓 · 10,000원" });
    const shown = [...w.document.querySelectorAll(".pay-method-item strong")].map((n) => n.textContent);
    test("클라이언트 키가 없으면 토스를 내주지 않는다", !shown.includes("토스페이먼츠로 결제"),
      shown.join(" | "));
    test("키가 없으면 모의 결제라고만 안내한다",
      w.document.querySelector(".pay-method-notice").textContent.includes("모의 결제입니다"));
    w.document.querySelector(".pay-method-actions .text-button").click();
    await promise;
  }
  {
    const w = bootMethods("test_gck_docs_x");
    const promise = w.AllMyTripsPayment.choose({ summary: "티켓 · 10,000원" });
    const shown = [...w.document.querySelectorAll(".pay-method-item strong")].map((n) => n.textContent);
    test("클라이언트 키가 있으면 토스를 내준다", shown.includes("토스페이먼츠로 결제"), shown.join(" | "));
    /* 토스는 실제 결제창이 뜬다. `모의`라고만 적어 두면 창이 뜬 순간 당황한다. */
    test("키가 있으면 토스는 테스트 결제창이라고 밝힌다",
      w.document.querySelector(".pay-method-notice").textContent.includes("테스트 결제창"));
    w.document.querySelector(".pay-method-actions .text-button").click();
    await promise;
  }

  /* ── 결제창 모듈 ── */
  {
    const dom = new JSDOM("<!doctype html><html><head></head><body></body></html>",
      { runScripts: "outside-only" });
    dom.window.eval(fs.readFileSync(CHECKOUT_JS, "utf8"));
    const checkout = dom.window.AllMyTripsCheckout;

    test("결제창 모듈이 토스 결제를 내놓는다", typeof checkout.tossCheckout === "function");
    /* 키가 없으면 SDK를 부르러 나가지 않고 바로 물러난다. */
    const result = await checkout.tossCheckout({ reservationId: 7, amount: 1000 });
    test("키가 없으면 결제창을 띄우지 않는다", result === null);
    test("키가 없으면 스크립트를 불러오지 않는다",
      dom.window.document.querySelectorAll("script[src*='tosspayments']").length === 0);
  }

  /* ── 키가 새지 않는가 ── */
  {
    const flights = readMarkup(FLIGHTS_HTML);
    const mypage = readMarkup(MYPAGE_HTML);
    const markup = readMarkup(RETURN_HTML) + flights + mypage;
    /* 시크릿 키는 승인에만 쓴다. 화면에 실리면 누구나 우리 이름으로 승인을 부른다. */
    test("화면에 시크릿 키가 실리지 않는다",
      !/secret-key|secretKey|test_sk_|test_gsk_/.test(markup));

    const front = fs.readFileSync(CHECKOUT_JS, "utf8")
      + fs.readFileSync(RETURN_JS, "utf8")
      + fs.readFileSync(METHODS_JS, "utf8");
    test("화면 스크립트가 시크릿 키를 다루지 않는다",
      !/secret/i.test(front.replace(/시크릿 키/g, "")));

    /* 결제가 일어나는 두 화면 모두에 있어야 한다. 한쪽만 있으면 그 화면에서만 토스가 뜬다. */
    test("예약 화면이 클라이언트 키를 싣는다", flights.includes('name="toss-client-key"'));
    test("마이페이지가 클라이언트 키를 싣는다", mypage.includes('name="toss-client-key"'));
  }

  console.log("\n" + passed + " passed, " + failed + " failed");
  if (failed > 0) process.exitCode = 1;
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
