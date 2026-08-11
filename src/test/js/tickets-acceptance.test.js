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

async function main() {
  const calls = [];
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/booking/flights?tab=ticket&tripId=10", runScripts: "outside-only"
  });
  const w = dom.window;
  const d = w.document;
  w.__flightBooking = { getSearch: () => ({ destination: "CJU", departureDate: "2026-08-17", returnDate: "2026-08-19" }) };
  w.fetch = async (url, options = {}) => {
    calls.push(`${options.method || "GET"} ${url}`);
    if (url.startsWith("/api/v1/tickets?")) return json({ success: true, data: [{
      productId: 1, productName: "제주 아쿠아리움 입장권", placeName: "아쿠아플라넷 제주",
      region: "제주", optionName: "성인 입장권", slotId: 31, usageDate: "2026-08-18",
      startTime: "10:00:00", endTime: "12:00:00", unitPrice: 20000, currency: "KRW",
      maxQuantityPerUser: 4, remainingQuantity: 30
    }] });
    if (url.startsWith("/api/v1/ticket-reservations?")) return json({ success: true, data: [] });
    if (url === "/api/v1/ticket-reservations") return json({ success: true, data: {
      reservationId: 9, tripId: 10, status: "PENDING", productName: "제주 아쿠아리움 입장권",
      optionName: "성인 입장권", quantity: 2, totalAmount: 40000, currency: "KRW"
    } });
    return json({ success: true, data: null });
  };
  let selected = null;
  w.addEventListener("allmytrips:ticket-reserved", (event) => { selected = event.detail.reservation; });
  w.eval(fs.readFileSync(SCRIPT, "utf8"));
  d.dispatchEvent(new w.Event("DOMContentLoaded"));
  await until(() => d.querySelectorAll(".ticket-card").length === 1);

  T("티켓 탭에서 여행지와 여행 기간으로 상품을 자동 조회한다",
    calls.some((call) => call.includes("destination=%EC%A0%9C%EC%A3%BC") && call.includes("from=2026-08-17")));
  T("상품명·옵션·시간·남은 수량·실습가를 표시한다",
    d.getElementById("ticketList").textContent.includes("제주 아쿠아리움 입장권")
      && d.getElementById("ticketList").textContent.includes("남은 수량 30개")
      && d.getElementById("ticketList").textContent.includes("20,000원"));
  T("실제 결제가 아닌 모의 예약임을 상시 표시한다",
    d.querySelector(".ticket-practice").textContent.includes("실제 결제 아님"));

  d.querySelector("[data-ticket-quantity]").value = "2";
  d.querySelector("[data-ticket-reserve]").click();
  await until(() => selected !== null);
  T("선택 수량으로 모의 예약 API를 호출한다", calls.includes("POST /api/v1/ticket-reservations"));
  T("예약 결과를 오른쪽 현황이 받을 수 있도록 이벤트로 전달한다",
    selected.productName === "제주 아쿠아리움 입장권" && selected.totalAmount === 40000);
  T("예약 후에도 실제 결제가 아니라는 안내를 유지한다",
    d.getElementById("ticketStatus").textContent.includes("실제 결제는 이루어지지 않았습니다"));

  w.dispatchEvent(new w.CustomEvent("allmytrips:ticket-cancelled", {
    detail: { reservationId: 9 }
  }));
  T("내 예약에서 취소하면 티켓 탭의 선택 상태도 함께 해제된다",
    w.__ticketBooking.state.reservation === null
      && d.getElementById("ticketStatus").textContent.includes("다시 예약할 수 있습니다"));

  console.log(`\n${passed} passed, ${failed} failed`);
  process.exit(failed ? 1 : 0);
}
main().catch((error) => { console.error(error); process.exit(1); });
