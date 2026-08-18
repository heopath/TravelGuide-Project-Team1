/* 마이페이지 · 내 티켓 수용 기준 (#253) */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/mypage/mypage.html");
const JS = path.join(ROOT, "src/main/resources/static/js/pages/mypage/mypage-tickets.js");

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
    .replace(/^export function/m, "function");
  w.eval(`${source}
    window.__initTickets = initTickets;`);
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

    d.defaultView.confirm = () => true;
    d.querySelector("[data-ticket-pay]").click();
    await until(() => paid !== null);
    await until(() => listCalls > 1);

    test("결제는 POST로 보낸다",
      calls.some((c) => c.method === "POST" && c.url.includes("/ticket-reservations/5/payment")));
    test("멱등키를 담는다", typeof paid.idempotencyKey === "string" && paid.idempotencyKey.length > 0);
    test("결제수단을 담는다", paid.method === "CARD");
    test("결제 후 목록을 다시 받는다", listCalls > 1);
    test("결제 결과를 알린다", toasts.some((m) => m.includes("결제가 완료")));
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

  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed > 0) process.exit(1);
}

run().catch((error) => { console.error(error); process.exit(1); });
