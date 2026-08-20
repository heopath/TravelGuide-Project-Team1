const fs = require("fs");
const path = require("path");
const { JSDOM } = require("jsdom");

const ROOT = path.resolve(__dirname, "../..");
const HTML = path.join(ROOT, "main/resources/templates/booking/flights.html");
const SCRIPT = path.join(ROOT, "main/resources/static/js/pages/booking/tickets.js");
let passed = 0;
let failed = 0;
function T(name, ok) { if (ok) { passed++; console.log(`PASS ${name}`); } else { failed++; console.error(`FAIL ${name}`); } }
function json(data, ok = true, status = 200) { return { ok, status, json: async () => data }; }
function until(predicate, timeout = 3000) { return new Promise((resolve, reject) => {
  const started = Date.now();
  const tick = () => predicate() ? resolve() : Date.now() - started > timeout ? reject(new Error("timeout")) : setTimeout(tick, 10);
  tick();
}); }

const PRODUCT = {
  productId: 1, productName: "제주 아쿠아리움 입장권", placeName: "아쿠아플라넷 제주",
  region: "제주", minUnitPrice: 20000, currency: "KRW",
  firstUsageDate: "2026-09-15", lastUsageDate: "2026-12-31",
  availableSlotCount: 3, remainingQuantity: 30
};
const SLOT = {
  productId: 1, productName: "제주 아쿠아리움 입장권", placeName: "아쿠아플라넷 제주",
  region: "제주", optionName: "성인 입장권", slotId: 31, usageDate: "2026-09-15",
  startTime: "10:00:00", endTime: "12:00:00", unitPrice: 20000, currency: "KRW",
  maxQuantityPerUser: 4, remainingQuantity: 30
};
const RESERVATION = {
  reservationId: 9, tripId: null, status: "PENDING", productName: "제주 아쿠아리움 입장권",
  optionName: "성인 입장권", quantity: 2, totalAmount: 40000, currency: "KRW"
};
const QUEUE_TOKEN = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

/* 여행 유무만 다르게 같은 화면을 띄운다. 티켓은 여행과 무관하게 팔려야 한다. (#255) */
function boot(query) {
  const calls = [];
  const bodies = [];
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: `http://localhost/booking/flights?${query}`, runScripts: "outside-only"
  });
  const w = dom.window;
  w.__flightBooking = { getSearch: () => ({ destination: "CJU", departureDate: "2026-08-17", returnDate: "2026-08-19" }) };
  w.fetch = async (url, options = {}) => {
    calls.push(`${options.method || "GET"} ${url}`);
    if (options.body) bodies.push(JSON.parse(options.body));
    if (url.startsWith("/api/v1/tickets/products/")) {
      return json({ success: true, data: { product: PRODUCT, slots: [SLOT] } });
    }
    if (url.startsWith("/api/v1/tickets/products")) {
      return json({ success: true, data: { items: [PRODUCT], page: 0, size: 20, total: 1, totalPages: 1 } });
    }
    if (url.startsWith("/api/v1/ticket-reservations")) return json({ success: true, data: [] });
    if (url === "/api/v1/booking-queue/entries") {
      return json({ success: true, data: { token: QUEUE_TOKEN, status: "READY", slotId: 31 } });
    }
    if (url === `/api/v1/booking-queue/entries/${QUEUE_TOKEN}/reservation`) {
      return json({ success: true, data: RESERVATION });
    }
    return json({ success: true, data: null });
  };
  const seen = { reservation: null };
  w.addEventListener("allmytrips:ticket-reserved", (event) => { seen.reservation = event.detail.reservation; });
  w.eval(fs.readFileSync(SCRIPT, "utf8"));
  w.document.dispatchEvent(new w.Event("DOMContentLoaded"));
  return { w, d: w.document, calls, bodies, seen };
}

async function main() {
  /* ── 여행을 고른 상태 ── */
  const { w, d, calls, bodies, seen } = boot("tab=ticket&tripId=10");
  await until(() => d.querySelectorAll(".ticket-card").length === 1);

  T("티켓 탭에서 판매 중인 상품을 날짜 조건 없이 조회한다",
    calls.some((call) => call.includes("/api/v1/tickets/products"))
      && !calls.some((call) => call.includes("from=") || call.includes("destination=")));
  T("상품명·장소·이용 가능 기간·최저가를 표시한다",
    d.getElementById("ticketList").textContent.includes("제주 아쿠아리움 입장권")
      && d.getElementById("ticketList").textContent.includes("2026-09-15 ~ 2026-12-31")
      && d.getElementById("ticketList").textContent.includes("20,000원"));
  T("실제 결제가 아닌 모의 예약임을 상시 표시한다",
    d.querySelector(".ticket-practice").textContent.includes("실제 결제 아님"));

  /*
   * 상세는 이제 별도 화면이다. (#281) 목록은 그 화면으로 보내는 데까지 책임진다 —
   * 무엇을 사는지(어디서·몇 시에·얼마에) 보여주는 일은 상세가 맡는다.
   * 예매 흐름은 ticket-detail-acceptance.test.js가 본다.
   */
  const open = d.querySelector("[data-ticket-open]");
  T("상품을 고르면 상세 화면으로 간다", open.getAttribute("href") === "/booking/tickets/1?tripId=10");
  /* 여행을 고른 채로 들어왔으면 상세에도 이어 준다. 거기서 다시 고르게 두지 않는다. */
  T("고른 여행을 상세로 이어 준다", open.getAttribute("href").includes("tripId=10"));

  /* ── 여행 없이 ── (#255의 핵심) */
  const solo = boot("tab=ticket");
  await until(() => solo.d.querySelectorAll(".ticket-card").length === 1);
  T("여행을 고르지 않아도 상품 목록이 뜬다",
    solo.d.getElementById("ticketList").textContent.includes("제주 아쿠아리움 입장권"));
  T("여행이 없으면 상세 주소에 tripId를 붙이지 않는다",
    solo.d.querySelector("[data-ticket-open]").getAttribute("href") === "/booking/tickets/1");
  T("여행이 없으면 내 티켓 전체로 복원한다",
    solo.calls.includes("GET /api/v1/ticket-reservations"));

  console.log(`\n${passed} passed, ${failed} failed`);
  process.exit(failed ? 1 : 0);
}
main().catch((error) => { console.error(error); process.exit(1); });
