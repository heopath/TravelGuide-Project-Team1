/* 카카오페이 결제 수용 기준 (#281)
 *
 * 토스와 달리 결제창을 우리 화면 안에 띄우지 못한다. 카카오 화면으로 손님을 아예 보냈다가
 * 돌려받는다. 그래서 확인할 것이 셋이다.
 *  1. 시크릿 키가 브라우저로 새지 않는가 — 새면 누구나 우리 이름으로 결제를 부른다.
 *  2. 돌아온 화면이 승인을 서버에 맡기고, 어느 예약인지를 화면이 정하지 않는가.
 *  3. 되돌아 나왔을 때 승인 대신 정리를 부르는가 — 안 그러면 다음 결제가 헌 거래번호를 문다.
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");
const { readMarkup } = require("./markup");

const ROOT = path.resolve(__dirname, "../../..");
const RETURN_HTML = path.join(ROOT, "src/main/resources/templates/payment/kakao-return.html");
const RETURN_JS = path.join(ROOT, "src/main/resources/static/js/pages/payment/kakao-return.js");
const METHODS_JS = path.join(ROOT, "src/main/resources/static/js/core/payment-methods.js");
const CHECKOUT_JS = path.join(ROOT, "src/main/resources/static/js/core/payment-checkout.js");
const SERVICE_JAVA = path.join(ROOT,
  "src/main/java/org/example/all_my_trip_project/domain/payment/service/KakaoPayService.java");
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

/** 카카오에서 돌아온 화면을 띄운다. 결과는 주소에 실려 온다. */
async function boot(query, options = {}) {
  const dom = new JSDOM(fs.readFileSync(RETURN_HTML, "utf8"), {
    url: "http://localhost/pay/kakao" + query,
    runScripts: "outside-only",
  });
  const w = dom.window;
  const calls = [];

  if (options.returnTo !== undefined) {
    w.sessionStorage.setItem("allmytrips.payReturnTo", options.returnTo);
  }

  w.fetch = async (url, request = {}) => {
    calls.push({ url: String(url), method: (request.method || "GET").toUpperCase(), body: request.body });
    return (options.responder || (() => ok({ replayed: false })))(String(url), request || {});
  };

  w.eval(fs.readFileSync(RETURN_JS, "utf8"));
  return { w, d: w.document, calls };
}

/** 결제수단 선택 창을 띄운다. 카카오 노출은 meta 태그의 켜짐 여부로 갈린다. */
function bootMethods(enabled) {
  const meta = enabled === null
    ? ""
    : '<meta name="kakao-pay-enabled" content="' + enabled + '">';
  const dom = new JSDOM("<!doctype html><html><head>" + meta + "</head><body></body></html>",
    { runScripts: "outside-only" });
  dom.window.eval(fs.readFileSync(METHODS_JS, "utf8"));
  return dom.window;
}

/** 결제창 모듈을 올린다. 카카오 결제창은 meta 태그가 켜져 있어야 뜬다. */
function bootCheckout(enabled) {
  const meta = enabled === null
    ? ""
    : '<meta name="kakao-pay-enabled" content="' + enabled + '">';
  const dom = new JSDOM("<!doctype html><html><head>" + meta + "</head><body></body></html>",
    { url: "http://localhost/mypage", runScripts: "outside-only" });
  dom.window.eval(fs.readFileSync(CHECKOUT_JS, "utf8"));
  return dom.window;
}

async function run() {
  /* ── 성공으로 돌아온 경우 ── */
  {
    const { d, calls } = await boot("?pg_token=tok-abc", { returnTo: "/mypage?tab=ticket" });
    await until(() => calls.length > 0);
    await until(() => !d.querySelector("[data-kakao-back]").hidden);

    const approve = calls.find((c) => c.url.includes("/api/v1/payments/kakao/approve"));
    test("승인을 서버에 맡긴다", Boolean(approve) && approve.method === "POST",
      calls.map((c) => c.method + " " + c.url).join(" | "));

    const body = approve ? JSON.parse(approve.body) : {};
    test("pg_token을 그대로 보낸다", body.pgToken === "tok-abc");
    /*
     * 어느 예약인지도, 거래번호도 화면이 정하지 않는다. 화면이 들고 있다가 되돌려주는
     * 값이면 다른 결제의 거래번호를 끼워 넣어 승인시킬 수 있다.
     */
    test("예약 번호를 화면이 정하지 않는다", body.reservationId === undefined);
    test("거래번호를 화면이 들고 있지 않는다", body.tid === undefined);

    test("결제가 끝났음을 알린다", text(d, "[data-kakao-state]").includes("결제가 완료"));
    test("테스트 결제라는 사실을 밝힌다",
      !d.querySelector("[data-kakao-notice]").hidden
      && text(d, "[data-kakao-notice]").includes("실제 돈이 빠져나가지 않"));
    test("결제를 시작한 화면으로 돌려보낸다",
      d.querySelector("[data-kakao-back]").getAttribute("href") === "/mypage?tab=ticket");
  }

  /* ── 이미 결제된 예약 ── */
  {
    const { d } = await boot("?pg_token=tok-b", { responder: () => ok({ replayed: true }) });
    await until(() => text(d, "[data-kakao-state]").includes("이미"));
    /* 새로고침으로 같은 승인이 두 번 들어와도 두 번 결제되지 않았음을 말해 준다. */
    test("이미 결제된 건은 그렇다고 말한다", text(d, "[data-kakao-state]").includes("이미 결제"));
  }

  /* ── 손님이 되돌아 나온 경우 ── */
  {
    const { d, calls } = await boot("?result=cancel");
    await until(() => calls.length > 0);

    /* 결제되지 않았는데 승인을 부르면 카카오 쪽 실패만 쌓인다. */
    test("취소하고 돌아오면 승인하지 않는다",
      !calls.some((c) => c.url.includes("/approve")),
      calls.map((c) => c.url).join(" | "));
    /* 남은 기록을 지워야 다음 결제가 헌 거래번호를 물지 않는다. */
    test("취소하고 돌아오면 서버에 정리를 부른다",
      calls.some((c) => c.url.includes("/api/v1/payments/kakao/cancel") && c.method === "POST"));
    test("취소했다고 알린다", text(d, "[data-kakao-state]").includes("취소"));
    test("다시 시도할 길을 준다", text(d, "[data-kakao-back]").includes("다시"));
  }
  {
    const { d, calls } = await boot("?result=fail");
    await until(() => calls.length > 0);
    /* 취소와 실패는 손님에게 해 줄 말이 다르다. */
    test("실패로 돌아오면 완료되지 않았다고 말한다",
      text(d, "[data-kakao-state]").includes("완료되지 않았"),
      text(d, "[data-kakao-state]"));
    test("실패로 돌아와도 정리는 부른다",
      calls.some((c) => c.url.includes("/cancel")));
  }

  /* ── 서버가 승인을 거절한 경우 ── */
  {
    const { d } = await boot("?pg_token=tok-c",
      { responder: () => fail(410, "결제 시간이 지났습니다. 결제를 다시 시작해 주세요.") });
    await until(() => text(d, "[data-kakao-state]").includes("다시 시작"));
    test("거절 사유를 그대로 보여준다",
      text(d, "[data-kakao-state]").includes("결제 시간이 지났습니다"));
  }

  /* ── 돌아갈 주소는 우리 사이트 안이어야 한다 ── */
  {
    const { d } = await boot("?pg_token=tok-d", { returnTo: "https://evil.example/steal" });
    await until(() => !d.querySelector("[data-kakao-back]").hidden);
    /* 저장소는 다른 스크립트도 건드린다. 검사 없이 쓰면 바깥으로 보내는 발판이 된다. */
    test("바깥 주소로는 보내지 않는다",
      d.querySelector("[data-kakao-back]").getAttribute("href") === "/mypage",
      d.querySelector("[data-kakao-back]").getAttribute("href"));
  }

  /* ── 결제수단 목록 ── */
  {
    const w = bootMethods(null);
    const kakao = w.AllMyTripsPayment.METHODS.find((m) => m.id === "KAKAO");
    test("결제수단 목록에 카카오페이 실결제가 있다", Boolean(kakao));
    /* 바로 결제되지 않는다. 부르는 쪽이 이 값으로 갈라 카카오로 보낸다. */
    test("카카오는 별도 흐름으로 표시한다", kakao.flow === "KAKAO");
    /* 서버 CHECK 제약에 있는 값이어야 한다. */
    test("서버가 아는 결제수단·사업자를 쓴다",
      kakao.method === "EASY_PAY" && kakao.provider === "KAKAO_PAY");
  }
  {
    /* 키가 없으면 결제창이 뜨지 않는다. 고를 수 있게 두면 눌러도 아무 일이 없다. */
    const w = bootMethods(null);
    const promise = w.AllMyTripsPayment.choose({ summary: "티켓 · 10,000원" });
    const shown = [...w.document.querySelectorAll(".pay-method-item strong")].map((n) => n.textContent);
    test("설정되지 않으면 카카오 실결제를 내주지 않는다", !shown.includes("카카오페이로 결제"),
      shown.join(" | "));
    /* 모의 카카오페이는 그대로 있어야 한다. 둘을 한꺼번에 감추면 결제수단이 사라진다. */
    test("모의 카카오페이는 그대로 남는다", shown.includes("카카오페이"));
    w.document.querySelector(".pay-method-actions .text-button").click();
    await promise;
  }
  {
    const w = bootMethods("true");
    const promise = w.AllMyTripsPayment.choose({ summary: "티켓 · 10,000원" });
    const shown = [...w.document.querySelectorAll(".pay-method-item strong")].map((n) => n.textContent);
    test("설정되면 카카오 실결제를 내준다", shown.includes("카카오페이로 결제"), shown.join(" | "));
    /* 켜진 것만 이름을 부른다. 꺼진 결제사를 테스트 결제창이라고 적으면 목록에 남은
     * 모의 결제를 가리키는 말이 되어 사실과 어긋난다. */
    test("실제 결제창이라는 사실을 밝힌다",
      w.document.querySelector(".pay-method-notice").textContent.includes("카카오페이는 테스트 결제창"),
      w.document.querySelector(".pay-method-notice").textContent);
    w.document.querySelector(".pay-method-actions .text-button").click();
    await promise;
  }

  /* ── 기록을 사람이 읽는 이름으로 되돌린다 ── */
  {
    const w = bootMethods("true");
    const { labelOf } = w.AllMyTripsPayment;
    /* 서버는 `결제사_사업자`로 적는다. 손님에게는 무엇으로 냈는지만 보이면 된다. */
    test("모의 카카오페이 기록을 읽어 준다", labelOf("EASY_PAY", "MOCK_KAKAO_PAY") === "카카오페이");
    test("실제 카카오페이 기록도 같은 이름으로 읽어 준다",
      labelOf("EASY_PAY", "KAKAO_KAKAO_PAY") === "카카오페이",
      labelOf("EASY_PAY", "KAKAO_KAKAO_PAY"));
    test("토스를 거친 카드 기록도 카드로 읽어 준다",
      labelOf("CARD", "TOSS") === "신용·체크카드", labelOf("CARD", "TOSS"));
  }

  /* ── 결제창 모듈 ── */
  {
    const w = bootCheckout(null);
    const checkout = w.AllMyTripsCheckout;
    test("결제창 모듈이 카카오 결제를 내놓는다", typeof checkout.kakaoCheckout === "function");
    const result = await checkout.kakaoCheckout({ summary: "티켓", amountText: "10,000원" });
    test("설정되지 않으면 결제창을 띄우지 않는다", result === null);
  }
  {
    const w = bootCheckout("true");
    const checkout = w.AllMyTripsCheckout;
    let asked = 0;

    const promise = checkout.kakaoCheckout({
      summary: "제주 아쿠아리움 입장권 · 40,000원",
      amountText: "40,000원",
      ready: async () => { asked += 1; return { redirectUrl: "https://online-pay.kakao.com/x" }; },
    });
    await until(() => Boolean(w.document.querySelector("[data-pay-checkout]")));

    test("무엇을 얼마에 결제하는지 보여준다",
      w.document.querySelector(".pay-checkout-amount strong").textContent === "40,000원");
    /* 고르자마자 카카오로 보내지 않는다. 잘못 눌렀을 때 되돌릴 자리를 준다. */
    test("이동하기 전에 한 번 더 묻는다", asked === 0);
    test("테스트 결제라는 사실을 밝힌다",
      w.document.querySelector(".pay-checkout-notice").textContent.includes("실제 돈이 빠져나가지 않"));

    w.document.querySelector(".pay-checkout-cancel").click();
    test("취소하면 아무 일도 일어나지 않는다", (await promise) === null && asked === 0);
    test("취소하면 창을 닫는다", w.document.querySelector("[data-pay-checkout]") === null);
  }
  {
    /* 결제 주소를 못 받으면 손님을 아무 데도 보내지 않고 그대로 알린다. */
    const w = bootCheckout("true");
    w.AllMyTripsCheckout.kakaoCheckout({
      summary: "티켓 · 10,000원",
      amountText: "10,000원",
      ready: async () => ({}),
    });
    await until(() => Boolean(w.document.querySelector("[data-pay-checkout]")));
    w.document.querySelector(".pay-checkout-confirm").click();
    await until(() => !w.document.querySelector("[data-pay-error]").hidden);

    test("결제 주소를 못 받으면 이유를 알린다",
      w.document.querySelector("[data-pay-error]").textContent.includes("결제 주소"));
    test("실패해도 다시 누를 수 있다",
      w.document.querySelector(".pay-checkout-confirm").disabled === false);
  }

  /* ── 키가 새지 않는가 ── */
  {
    const flights = readMarkup(FLIGHTS_HTML);
    const mypage = readMarkup(MYPAGE_HTML);
    const markup = readMarkup(RETURN_HTML) + flights + mypage;
    /*
     * 카카오는 화면이 쓸 공개 키조차 없다. 결제 시작부터 서버가 부르므로, 화면에는
     * 켜졌는지만 실린다. 키 비슷한 것이 화면에 있으면 그 자체가 사고다.
     */
    test("화면에 카카오 키가 실리지 않는다",
      !/secret-key|secretKey|SECRET_KEY|DEV_SECRET/.test(markup));

    const front = fs.readFileSync(CHECKOUT_JS, "utf8")
      + fs.readFileSync(RETURN_JS, "utf8")
      + fs.readFileSync(METHODS_JS, "utf8");
    test("화면 스크립트가 시크릿 키를 다루지 않는다",
      !/secret/i.test(front.replace(/시크릿 키/g, "")));

    /* 결제가 일어나는 두 화면 모두에 있어야 한다. 한쪽만 있으면 그 화면에서만 뜬다. */
    test("예약 화면이 켜짐 여부를 싣는다", flights.includes('name="kakao-pay-enabled"'));
    test("마이페이지가 켜짐 여부를 싣는다", mypage.includes('name="kakao-pay-enabled"'));
  }

  /* ── 서버가 화면 말을 믿지 않는가 ── */
  {
    const service = fs.readFileSync(SERVICE_JAVA, "utf8");
    /* 거래번호가 화면을 거쳐 돌아오면 다른 결제에 승인을 붙일 수 있다. */
    test("거래번호를 서버가 보관한다",
      service.includes("redisTemplate.opsForValue().set") && service.includes("SESSION_TTL"));
    /* 금액을 화면에서 받으면 1원짜리 결제창을 띄우고 4만원 티켓을 받아 갈 수 있다. */
    test("금액을 예약에서 읽는다", service.includes("amountOf(reservation)"));
    test("승인 금액을 예약 금액과 대조한다", service.includes("requireSameAmount"));
    test("남의 예약인지 확인한다", service.includes("requireOwnReservation"));
    /* 모의 결제와 섞이면 정산도 문의도 갈 곳이 없다. */
    test("결제사를 기록에 남긴다", service.includes('ACQUIRER = "KAKAO"'));
  }

  console.log("\n" + passed + " passed, " + failed + " failed");
  if (failed > 0) process.exitCode = 1;
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
