/* 마이페이지 · 내 티켓 수용 기준 (#253) */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/mypage/mypage.html");
const JS = path.join(ROOT, "src/main/resources/static/js/pages/mypage/mypage-tickets.js");
const PAYMENT_METHODS = path.join(ROOT, "src/main/resources/static/js/core/payment-methods.js");
const DIALOG = path.join(ROOT, "src/main/resources/static/js/core/dialog.js");

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

const ticket = (overrides) => Object.assign({
  reservationId: 5, reservationNumber: "AMT-TKT-ABC123DEF456",
  tripId: null, status: "CONFIRMED",
  productName: "제주 아쿠아리움 입장권", optionName: "성인 입장권",
  usageDate: "2026-09-15", usageStartTime: "10:00:00",
  quantity: 2, totalAmount: 40000, currency: "KRW",
}, overrides || {});

const trip = (overrides) => Object.assign({
  tripId: 12, title: "제주 가을 여행",
  startDate: "2026-09-14", endDate: "2026-09-16",
}, overrides || {});

/*
 * ES 모듈이라 import를 그대로 실행할 수 없다. mypage-common의 두 export만 상수로
 * 바꿔치기해 함수 본문을 평가한다. 이 테스트가 보는 것은 모듈 배선이 아니라 화면 동작이고,
 * 배선은 mypage-module-wiring.test.js가 따로 본다.
 */
function loadModule(w, handlers) {
  /* import를 전부 걷어낸다. 하나라도 남으면 eval이 SyntaxError로 죽는다. */
  const source = fs.readFileSync(JS, "utf8")
    .replace(/^import[\s\S]*?from\s*"[^"]+";\s*$/gm, "")
    /* export가 여러 개다(initTickets·initTicketHistory). 하나만 걷어내면 eval이 죽는다. */
    .replace(/^export function/gm, "function");
  w.eval(`${source}
    window.__initTickets = initTickets;
    window.__initTicketHistory = initTicketHistory;`);
  w.request = handlers.request;
  w.showToast = handlers.showToast;
  /* QR 그리기는 별도 시험(qr-encoder.test.js)이 맡는다. 여기서는 호출 여부만 본다. */
  w.createQrSvg = handlers.createQrSvg || ((text) => {
    const svg = w.document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.dataset.stubToken = text;
    return svg;
  });
  return w.__initTickets;
}

async function boot(responder, toasts) {
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/mypage", runScripts: "outside-only",
  });
  const w = dom.window;
  /* 결제수단 선택 창. 화면에서도 모듈보다 먼저 올라간다. (#281) */
  w.eval(fs.readFileSync(DIALOG, "utf8"));
  w.eval(fs.readFileSync(PAYMENT_METHODS, "utf8"));
  const calls = [];
  const init = loadModule(w, {
    request: async (url, options = {}) => {
      calls.push({ url: String(url), method: (options.method || "GET").toUpperCase(), body: options.body });
      return responder(String(url), options || {});
    },
    showToast: (message) => { toasts.push(message); },
  });
  await init();
  return { w, d: w.document, calls };
}

/**
 * 예매한 티켓 화면. (#281)
 *
 * 대시보드 미리보기와 같은 모듈을 쓰지만 그리는 자리와 모양이 다르다. 결제·취소·입장 QR은
 * 두 화면이 함께 쓰는 코드라, 여기서는 이 화면에만 있는 것(탭·목록·티켓 상세)을 본다.
 */
async function bootHistory(responder, toasts) {
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/mypage?view=tickets", runScripts: "outside-only",
  });
  const w = dom.window;
  w.eval(fs.readFileSync(DIALOG, "utf8"));
  w.eval(fs.readFileSync(PAYMENT_METHODS, "utf8"));
  const calls = [];
  loadModule(w, {
    request: async (url, options = {}) => {
      calls.push({ url: String(url), method: (options.method || "GET").toUpperCase(), body: options.body });
      return responder(String(url), options || {});
    },
    showToast: (message) => { toasts.push(message); },
  });
  await w.__initTicketHistory();
  return { w, d: w.document, calls };
}

const tabOf = (d, id) => d.querySelector(`[data-ticket-tab="${id}"]`);
const picks = (d) => [...d.querySelectorAll("[data-ticket-pick]")];
const detailText = (d) => d.querySelector("[data-ticket-detail]")?.textContent || "";

const rows = (d) => [...d.querySelectorAll("[data-ticket-row]")];

async function run() {
  /* ── 마크업 ── */
  {
    const markup = fs.readFileSync(HTML, "utf8");
    test("`준비되면 표시됩니다` 플레이스홀더가 사라졌다",
      !markup.includes("관광 티켓 예약 기능이 준비되면"));
    test("티켓 목록 자리가 있다", markup.includes("data-ticket-list"));
  }

  /* ── 목록 ── */
  {
    const toasts = [];
    const { d, calls } = await boot((url) => {
      if (url.startsWith("/api/v1/ticket-reservations")) return [ticket()];
      if (url.startsWith("/api/v1/trips")) return { items: [trip()] };
      return null;
    }, toasts);

    test("여행별이 아니라 사용자 기준으로 부른다",
      calls.some((c) => c.url === "/api/v1/ticket-reservations"),
      calls.map((c) => c.url).join(" | "));
    test("tripId를 붙이지 않는다",
      !calls.some((c) => c.url.includes("ticket-reservations?tripId")));
    test("티켓 한 건을 보여준다", rows(d).length === 1);
    test("상품명·옵션·이용일·수량·금액을 보여준다",
      rows(d)[0].textContent.includes("제주 아쿠아리움 입장권")
      && rows(d)[0].textContent.includes("성인 입장권")
      && rows(d)[0].textContent.includes("2026.09.15 10:00")
      && rows(d)[0].textContent.includes("2매")
      && rows(d)[0].textContent.includes("40,000원"));
    test("예약번호를 보여준다",
      rows(d)[0].textContent.includes("AMT-TKT-ABC123DEF456"));
    test("상태를 사람 말로 보여준다",
      rows(d)[0].querySelector("[data-ticket-status]").textContent === "결제 완료");
    test("입장 코드는 보여주지 않는다",
      !rows(d)[0].textContent.includes("입장 코드"));
  }

  /* ── 여행 연결 ── */
  {
    const toasts = [];
    let patched = null;
    const { d, calls } = await boot((url, options) => {
      if (url.includes("/trip") && options.method === "PATCH") {
        patched = JSON.parse(options.body);
        return {};
      }
      if (url.startsWith("/api/v1/ticket-reservations")) return [ticket()];
      if (url.startsWith("/api/v1/trips")) return { items: [trip()] };
      return null;
    }, toasts);

    const select = d.querySelector("[data-ticket-row] select");
    test("여행에 안 붙은 티켓은 연결 선택지를 준다", select !== null);
    test("이용일이 겹치는 여행만 선택지에 넣는다",
      select && [...select.options].filter((o) => o.value).length === 1
      && [...select.options].some((o) => o.textContent === "제주 가을 여행"));

    select.value = "12";
    select.dispatchEvent(new d.defaultView.Event("change"));
    /* patched는 요청 처리 중에 채워져 안내보다 먼저다. 안내까지 기다려야 한다. */
    await until(() => toasts.length > 0);

    test("연결은 PATCH로 보낸다",
      calls.some((c) => c.method === "PATCH" && c.url.includes("/ticket-reservations/5/trip")));
    test("고른 여행 번호를 담는다", patched.tripId === 12);
    test("연결 결과를 알린다", toasts.some((m) => m.includes("연결했어요")));
  }

  /* ── 이용일에 맞는 여행이 없을 때 ── */
  {
    const toasts = [];
    const { d } = await boot((url) => {
      if (url.startsWith("/api/v1/ticket-reservations")) return [ticket()];
      /* 8월 여행뿐이라 9월 티켓을 붙일 수 없다. */
      if (url.startsWith("/api/v1/trips")) {
        return { items: [trip({ tripId: 7, startDate: "2026-08-25", endDate: "2026-08-27" })] };
      }
      return null;
    }, toasts);

    test("기간이 안 맞으면 선택지를 주지 않는다",
      d.querySelector("[data-ticket-row] select") === null);
    test("왜 못 붙이는지 밝힌다",
      d.querySelector("[data-ticket-unlinked]").textContent.includes("이용일에 맞는 여행이 없어요"));
  }

  /* ── 끝난 티켓 ── */
  {
    const toasts = [];
    const { d } = await boot((url) => {
      if (url.startsWith("/api/v1/ticket-reservations")) return [ticket({ status: "CANCELLED" })];
      if (url.startsWith("/api/v1/trips")) return { items: [trip()] };
      return null;
    }, toasts);

    test("취소된 티켓은 여행에 붙일 수 없다",
      d.querySelector("[data-ticket-row] select") === null);
    test("취소 상태를 표시한다",
      d.querySelector("[data-ticket-status]").textContent === "취소됨");
  }

  /* ── 입장 QR (#265) ── */
  {
    const toasts = [];
    const issued = [
      { issuedTicketId: 11, ticketNumber: "AMT-TKN-000000000011", status: "ISSUED" },
      { issuedTicketId: 12, ticketNumber: "AMT-TKN-000000000012", status: "ISSUED" },
      /* 취소된 티켓은 섞여 있어도 QR을 만들면 안 된다. */
      { issuedTicketId: 13, ticketNumber: "AMT-TKN-000000000013", status: "CANCELLED" },
    ];
    const { d, calls } = await boot((url, options) => {
      if (url.includes("/qr") && options.method === "POST") {
        const id = Number(url.match(/tickets\/(\d+)\/qr/)[1]);
        return {
          issuedTicketId: id, ticketNumber: "AMT-TKN-" + id,
          token: "token-" + id,
          expiresAt: "2026-08-18T14:35:19+09:00",
          serverTime: "2026-08-18T14:30:19+09:00",
        };
      }
      if (url.endsWith("/tickets")) return issued;
      if (url.startsWith("/api/v1/ticket-reservations")) return [ticket({ status: "CONFIRMED" })];
      if (url.startsWith("/api/v1/trips")) return { items: [] };
      return null;
    }, toasts);

    const open = d.querySelector("[data-ticket-qr-open]");
    test("결제 완료된 예약에 입장 QR 버튼이 있다", open !== null);

    open.click();
    await until(() => d.querySelectorAll("[data-qr-card]").length > 0);

    test("발급 API를 티켓마다 POST로 부른다",
      calls.filter((c) => c.method === "POST" && c.url.includes("/qr")).length === 2,
      calls.filter((c) => c.url.includes("/qr")).map((c) => c.url).join(" | "));
    test("쓸 수 있는 티켓 수만큼 QR을 보여준다",
      d.querySelectorAll("[data-qr-card]").length === 2);
    test("취소된 티켓의 QR은 만들지 않는다",
      !calls.some((c) => c.url.includes("/tickets/13/qr")));
    test("티켓 번호를 함께 보여준다",
      d.querySelector("[data-qr-card] small").textContent.includes("AMT-TKN-"));
    test("남은 시간을 서버가 준 값으로 센다",
      /분 \d\d초 뒤 만료/.test(d.querySelector("[data-qr-remain]").textContent),
      d.querySelector("[data-qr-remain]")?.textContent);

    open.click();
    test("닫으면 QR과 타이머를 함께 정리한다",
      d.querySelectorAll("[data-qr-card]").length === 0
      && !d.querySelector("[data-ticket-qr-panel]").dataset.qrTimer);
  }
  {
    const toasts = [];
    const { d } = await boot((url) => {
      if (url.startsWith("/api/v1/ticket-reservations")) return [ticket({ status: "PENDING" })];
      if (url.startsWith("/api/v1/trips")) return { items: [] };
      return null;
    }, toasts);

    /* 결제 전에는 티켓이 아직 없다. 버튼을 두면 눌러야만 없다는 것을 알게 된다. */
    test("결제 전 예약에는 QR 버튼을 두지 않는다",
      d.querySelector("[data-ticket-qr-open]") === null);
  }

  /* ── 결제 전 예약 (#276) ── */
  {
    const toasts = [];
    let paid = null;
    let listCalls = 0;
    const pending = ticket({
      status: "PENDING",
      expiresAt: new Date(Date.now() + 9 * 60 * 1000).toISOString(),
    });
    const { d, calls } = await boot((url, options) => {
      if (url.includes("/payment") && options.method === "POST") {
        paid = JSON.parse(options.body);
        return {};
      }
      if (url.startsWith("/api/v1/ticket-reservations")) {
        listCalls += 1;
        /* 결제 뒤에는 확정된 목록을 준다. 화면이 다시 받아 그리는지 본다. */
        return [paid ? ticket({ status: "CONFIRMED" }) : pending];
      }
      if (url.startsWith("/api/v1/trips")) return { items: [] };
      return null;
    }, toasts);

    test("결제 전 예약에 결제 버튼이 있다", d.querySelector("[data-ticket-pay]") !== null);
    test("예약 취소 버튼도 함께 둔다", d.querySelector("[data-ticket-cancel]") !== null);
    test("결제 전에는 입장 QR 버튼을 두지 않는다",
      d.querySelector("[data-ticket-qr-open]") === null);
    test("결제까지 남은 시간을 밝힌다",
      /\d+분 안에 결제해야/.test(d.querySelector("[data-pay-remain]").textContent),
      d.querySelector("[data-pay-remain]")?.textContent);

    d.querySelector("[data-ticket-pay]").click();

    /* 결제수단을 고르기 전에는 결제가 나가면 안 된다. (#281) */
    await until(() => d.querySelector(".pay-method-overlay") !== null);
    test("결제 전에 결제수단을 고르게 한다", d.querySelector(".pay-method-overlay") !== null);
    test("고르기 전에는 결제하지 않는다", paid === null);
    test("모의 결제라는 사실을 고르는 자리에서 밝힌다",
      d.querySelector(".pay-method-notice")?.textContent.includes("실제 돈이 빠져나가지 않"));

    d.querySelector('.pay-method-overlay input[value="EASY_PAY:TOSS_PAY"]').click();
    d.querySelector(".pay-method-overlay .primary-button").click();

    await until(() => paid !== null);
    await until(() => listCalls > 1);

    test("결제는 POST로 보낸다",
      calls.some((c) => c.method === "POST" && c.url.includes("/ticket-reservations/5/payment")));
    test("멱등키를 담는다", typeof paid.idempotencyKey === "string" && paid.idempotencyKey.length > 0);
    test("고른 결제수단을 담는다", paid.method === "EASY_PAY");
    /* 카카오페이·토스는 method가 같아서 사업자가 없으면 어디로 결제됐는지 남지 않는다. */
    test("간편결제는 사업자도 담는다", paid.easyPayProvider === "TOSS_PAY");
    test("결제하면 선택 창이 닫힌다", d.querySelector(".pay-method-overlay") === null);
    test("결제 후 목록을 다시 받는다", listCalls > 1);
    test("결제 결과를 알린다", toasts.some((m) => m.includes("결제가 완료")));
  }

  /* ── QR 결제 (#281) ── */
  {
    /*
     * QR 결제는 다른 수단과 달리 이 화면에서 끝나지 않는다. QR을 띄우고, 손님이 폰으로
     * 스캔해 승인해야 결제된다. 그래서 고른 즉시 결제가 나가면 안 되고, 승인이 끝났는지
     * 물어보며 기다려야 한다.
     */
    const toasts = [];
    let paidDirectly = false;
    let qrIssued = 0;
    let approved = false;
    const { d } = await boot((url, options) => {
      if (url.includes("/payment/qr") && options.method === "POST") {
        qrIssued += 1;
        const now = new Date();
        return {
          reservationId: 5,
          token: "dGVzdA.c2lnbmF0dXJl",
          expiresAt: new Date(now.getTime() + 5 * 60 * 1000).toISOString(),
          serverTime: now.toISOString(),
        };
      }
      if (url.includes("/payment") && options.method === "POST") {
        paidDirectly = true;
        return {};
      }
      if (url.endsWith("/tickets")) {
        /* 폴링. 승인 전에는 비어 있고, 승인하면 발급된 티켓이 생긴다. */
        return approved ? [{ issuedTicketId: 1, ticketNumber: "AMT-TKN-AAA", status: "ISSUED" }] : [];
      }
      if (url.startsWith("/api/v1/ticket-reservations")) {
        return [ticket({ status: approved ? "CONFIRMED" : "PENDING" })];
      }
      if (url.startsWith("/api/v1/trips")) return { items: [] };
      return null;
    }, toasts);

    d.querySelector("[data-ticket-pay]").click();
    await until(() => d.querySelector(".pay-method-overlay") !== null);

    test("마이페이지에서는 QR 결제도 고를 수 있다",
      d.querySelector('.pay-method-overlay input[value="QR"]') !== null);

    d.querySelector('.pay-method-overlay input[value="QR"]').click();
    d.querySelector(".pay-method-overlay .primary-button").click();

    await until(() => d.querySelector("[data-pay-qr]") !== null);

    test("QR 결제를 고르면 결제 QR을 띄운다", qrIssued === 1);
    /* 고른 순간 결제되면 폰으로 승인하는 흐름 자체가 의미가 없다. */
    test("QR을 띄우는 것만으로는 결제되지 않는다", paidDirectly === false);

    const drawn = d.querySelector("[data-pay-qr-code] svg");
    test("QR에 승인 화면 주소를 담는다",
      Boolean(drawn) && drawn.dataset.stubToken.includes("/pay/qr?token="),
      drawn && drawn.dataset.stubToken);
    /* 토큰만 담으면 찍어도 아무 데도 가지 않는다. */
    test("QR 주소에 토큰이 붙는다",
      Boolean(drawn) && drawn.dataset.stubToken.includes("dGVzdA"));
    test("남은 시간을 밝힌다",
      /\d+분 \d+초 뒤 만료/.test(d.querySelector("[data-pay-qr-remain]").textContent),
      d.querySelector("[data-pay-qr-remain]")?.textContent);
    test("승인을 기다리는 중이라고 알린다",
      d.querySelector("[data-pay-qr-state]").textContent.includes("승인을 기다리는"));

    /* 폰에서 승인된 상황. 화면은 물어보다가 티켓이 생긴 것을 보고 끝낸다. */
    approved = true;
    await until(() => d.querySelector("[data-pay-qr]") === null, 8000);

    test("승인되면 QR 창을 닫는다", d.querySelector("[data-pay-qr]") === null);
    test("승인 결과를 알린다", toasts.some((m) => m.includes("결제가 완료")));
  }
  {
    /* 창을 닫으면 더 묻지 않는다. 남겨 두면 화면을 떠난 뒤에도 계속 요청이 나간다. */
    const toasts = [];
    let ticketCalls = 0;
    const { d } = await boot((url, options) => {
      if (url.includes("/payment/qr") && options.method === "POST") {
        const now = new Date();
        return {
          reservationId: 5,
          token: "dGVzdA.c2lnbmF0dXJl",
          expiresAt: new Date(now.getTime() + 5 * 60 * 1000).toISOString(),
          serverTime: now.toISOString(),
        };
      }
      if (url.endsWith("/tickets")) { ticketCalls += 1; return []; }
      if (url.startsWith("/api/v1/ticket-reservations")) return [ticket({ status: "PENDING" })];
      if (url.startsWith("/api/v1/trips")) return { items: [] };
      return null;
    }, toasts);

    d.querySelector("[data-ticket-pay]").click();
    await until(() => d.querySelector(".pay-method-overlay") !== null);
    d.querySelector('.pay-method-overlay input[value="QR"]').click();
    d.querySelector(".pay-method-overlay .primary-button").click();
    await until(() => d.querySelector("[data-pay-qr]") !== null);

    d.querySelector("[data-pay-qr] .text-button").click();
    const asked = ticketCalls;
    await new Promise((resolve) => setTimeout(resolve, 3000));

    test("QR 창을 닫으면 승인 확인을 멈춘다", ticketCalls === asked, `${asked} → ${ticketCalls}`);
    test("닫으면 결제 버튼을 다시 누를 수 있다",
      d.querySelector("[data-ticket-pay]")?.disabled === false);
  }
  {
    /* 만료 시각이 지난 예약은 자리가 이미 반납됐을 수 있다. 그 사실을 밝힌다. */
    const toasts = [];
    const { d } = await boot((url) => {
      if (url.startsWith("/api/v1/ticket-reservations")) {
        return [ticket({ status: "PENDING", expiresAt: new Date(Date.now() - 1000).toISOString() })];
      }
      if (url.startsWith("/api/v1/trips")) return { items: [] };
      return null;
    }, toasts);

    test("만료 시각이 지나면 그 사실을 알린다",
      d.querySelector("[data-pay-remain]").textContent.includes("자리가 반납"));
  }

  /* ── 빈 목록과 실패 ── */
  {
    const toasts = [];
    const { d } = await boot((url) => {
      if (url.startsWith("/api/v1/ticket-reservations")) return [];
      if (url.startsWith("/api/v1/trips")) return { items: [] };
      return null;
    }, toasts);

    test("티켓이 없으면 안내를 띄운다",
      d.querySelector("[data-ticket-empty]").hidden === false
      && d.querySelector("[data-ticket-empty-title]").textContent.includes("아직 예약 내역이 없어요"));
  }
  {
    const toasts = [];
    const { d } = await boot((url) => {
      if (url.startsWith("/api/v1/ticket-reservations")) throw new Error("불러오지 못했습니다");
      return null;
    }, toasts);

    /* "없음"과 "실패"가 같은 화면으로 보이면 안 된다. */
    test("불러오기 실패는 없음과 다르게 알린다",
      d.querySelector("[data-ticket-empty-title]").textContent.includes("불러오지 못했어요"));
  }

  /* ── 여행 목록 조회가 실패해도 티켓은 보인다 ── */
  {
    const toasts = [];
    const { d } = await boot((url) => {
      if (url.startsWith("/api/v1/ticket-reservations")) return [ticket()];
      if (url.startsWith("/api/v1/trips")) throw new Error("여행 목록 실패");
      return null;
    }, toasts);

    test("여행 목록이 실패해도 티켓은 보여준다", rows(d).length === 1);
  }

  /* ── 예매한 티켓 화면 (#281) ── */
  {
    const toasts = [];
    const paid = ticket({
      reservationId: 5, status: "CONFIRMED", productName: "아쿠아플라넷 제주",
      optionName: "성인", usageDate: "2026-09-15", usageStartTime: "10:00:00",
      usageEndTime: "18:00:00", placeName: "제주 서귀포시",
      paymentMethod: "EASY_PAY", paymentProvider: "MOCK_KAKAO_PAY",
      paidAt: "2026-08-10T14:02:00+09:00", quantity: 2, totalAmount: 78000,
    });
    const pending = ticket({
      reservationId: 6, status: "PENDING", productName: "제주 스카이 워터쇼",
      usageDate: "2026-09-16", expiresAt: new Date(Date.now() + 9 * 60 * 1000).toISOString(),
    });
    const used = ticket({ reservationId: 7, status: "USED", productName: "성산일출봉 입장권" });

    const { d } = await bootHistory((url) => {
      if (url.startsWith("/api/v1/ticket-reservations")) return [paid, pending, used];
      if (url.startsWith("/api/v1/trips")) return { items: [] };
      return null;
    }, toasts);

    test("상태 탭을 개수와 함께 보여준다",
      tabOf(d, "ALL")?.textContent.includes("3")
      && tabOf(d, "UPCOMING")?.textContent.includes("2")
      && tabOf(d, "USED")?.textContent.includes("1")
      && tabOf(d, "CLOSED")?.textContent.includes("0"),
      [...d.querySelectorAll("[data-ticket-tab]")].map((t) => t.textContent).join(" | "));
    test("목록에 티켓을 모두 보여준다", picks(d).length === 3);
    /* 오른쪽이 비어 있으면 화면이 덜 그려진 것처럼 보인다. 첫 티켓을 펴 둔다. */
    test("첫 티켓을 자동으로 펴 둔다", picks(d)[0].classList.contains("is-active"));
    test("상세에 예약번호를 보여준다", detailText(d).includes("AMT-TKT-ABC123DEF456"));
    test("상세에 장소를 보여준다", detailText(d).includes("제주 서귀포시"));
    test("상세에 이용 시간을 범위로 보여준다", detailText(d).includes("10:00–18:00"));
    test("상세에 인원을 보여준다", detailText(d).includes("2명"));
    /* 결제수단이 CARD로 박혀 있던 것을 고른 값으로 바꾼 게 여기까지 이어진다. */
    test("결제 수단을 사람이 읽는 이름으로 보여준다", detailText(d).includes("카카오페이"));
    test("결제일을 보여준다", detailText(d).includes("2026. 08. 10"));
    test("결제 완료 티켓에는 입장 QR 자리를 둔다",
      d.querySelector("[data-ticket-detail] [data-ticket-qr-open]") !== null);

    /* 결제 전 티켓 */
    picks(d)[1].click();
    test("고른 티켓으로 상세가 바뀐다", detailText(d).includes("제주 스카이 워터쇼"));
    test("결제 전 티켓에는 결제 버튼을 둔다",
      d.querySelector("[data-ticket-detail] [data-ticket-pay]") !== null);
    /* 아직 티켓이 없으니 입장 QR을 둘 수 없다. 눌러야만 없다는 걸 알게 하지 않는다. */
    test("결제 전 티켓에는 입장 QR 자리를 두지 않는다",
      d.querySelector("[data-ticket-detail] [data-ticket-qr-open]") === null);
    test("결제 전 티켓에는 결제 정보를 채우지 않는다",
      !detailText(d).includes("카카오페이") && detailText(d).includes("결제 전"));

    /* 탭으로 거르기 */
    tabOf(d, "USED").click();
    test("탭을 누르면 그 상태만 거른다",
      picks(d).length === 1 && picks(d)[0].textContent.includes("성산일출봉"));
    test("탭을 바꾸면 상세도 그 탭의 티켓으로 바뀐다", detailText(d).includes("성산일출봉"));

    tabOf(d, "CLOSED").click();
    test("빈 탭에서는 없다고 알린다",
      d.querySelector("[data-ticket-picker]")?.textContent.includes("취소·환불 티켓이 없어요"));
  }
  {
    /* 예약이 하나도 없을 때. 두 칸 모두 채워야 앞의 티켓이 남지 않는다. */
    const toasts = [];
    const { d } = await bootHistory((url) => {
      if (url.startsWith("/api/v1/ticket-reservations")) return [];
      if (url.startsWith("/api/v1/trips")) return { items: [] };
      return null;
    }, toasts);

    test("예약이 없으면 목록 자리에 안내를 둔다",
      d.querySelector("[data-ticket-picker]")?.textContent.includes("아직 예약 내역이 없어요"));
    test("예약이 없으면 상세 자리에도 안내를 둔다",
      d.querySelector("[data-ticket-detail]")?.textContent.includes("표시할 티켓이 없어요"));
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed > 0) process.exit(1);
}

run().catch((error) => { console.error(error); process.exit(1); });
