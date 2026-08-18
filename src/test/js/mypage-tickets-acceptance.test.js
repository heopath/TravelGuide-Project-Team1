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
  const source = fs.readFileSync(JS, "utf8")
    .replace(/^import[\s\S]*?from\s*"\.\/mypage-common\.js";/m, "")
    .replace(/^export function/m, "function");
  w.eval(`${source}
    window.__initTickets = initTickets;`);
  w.request = handlers.request;
  w.showToast = handlers.showToast;
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
