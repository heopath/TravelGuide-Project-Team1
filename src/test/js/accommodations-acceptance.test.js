/* 숙소 탭 수용 기준
 * 실행: src/test/js 에서 `npm test`
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/booking/flights.html");
const FLIGHTS = path.join(ROOT, "src/main/resources/static/js/pages/booking/flights.js");
const HOTELS = path.join(ROOT, "src/main/resources/static/js/pages/booking/accommodations.js");

let passed = 0;
let failed = 0;
const T = (name, condition) => {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name); }
};

const flightOffer = (leg) => ({
  offerId: `mock:${leg}`, provider: "mock", carrierCode: "KE", carrierName: "대한항공",
  flightNumber: leg === "out" ? "KE101" : "KE102", origin: leg === "out" ? "GMP" : "CJU",
  destination: leg === "out" ? "CJU" : "GMP", departureAt: "2026-08-17T08:00:00",
  arrivalAt: "2026-08-17T09:10:00", departureTime: "08:00", arrivalTime: "09:10",
  durationLabel: "1시간 10분", pricePerAdult: 50000, totalPrice: 100000, currency: "KRW",
  priceSource: "PUBLISHED", priceSourceLabel: "공시운임", ribbons: ["AI 추천"], badges: [],
  deeplinkUrl: "https://example.test/flight"
});

const hotel = (id, name, typeLabel) => ({
  offerId: id, provider: "tourapi", name, type: "HOTEL", typeLabel, areaLabel: "제주",
  address: `제주특별자치도 ${name}길 1`, rating: null, reviewCount: null,
  nightlyPrice: null, totalPrice: null, currency: "KRW", nightsLabel: "2박",
  priceSource: "UNAVAILABLE", priceSourceLabel: "요금 미제공", amenities: [],
  freeCancellation: false, breakfastIncluded: false, imageUrl: null,
  latitude: 33.4, longitude: 126.5, deeplinkUrl: null, ribbons: id === "tour:2" ? ["AI 추천"] : []
});

const sandboxHotel = () => ({
  ...hotel("tour:2", "제주 바다 호텔", "호텔"),
  nightlyPrice: 287620, totalPrice: 575240, currency: "KRW",
  priceSource: "SANDBOX", priceSourceLabel: "Sandbox 실습 요금",
  freeCancellation: true, breakfastIncluded: true
});

function json(body) {
  return { ok: true, status: 200, json: async () => body };
}

function until(predicate, timeoutMs = 4000) {
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

async function run() {
  const urls = [];
  let sandboxMode = false;
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/booking/flights?tab=hotel&destination=CJU&date=2026-08-17&returnDate=2026-08-19&adults=2",
    runScripts: "outside-only"
  });
  const w = dom.window;
  const d = w.document;
  Object.defineProperty(d, "visibilityState", { value: "visible", configurable: true });
  w.open = () => null;
  w.fetch = async (url) => {
    urls.push(url);
    if (url.startsWith("/api/v1/flights/search")) {
      return json({ success: true, data: {
        offers: [flightOffer(url.includes("destination=GMP") ? "in" : "out")],
        meta: { priceSource: "PUBLISHED", priceSourceNotice: "공시운임 기준", totalCount: 1 }
      } });
    }
    if (url.startsWith("/api/v1/accommodations/search")) {
      return json({ success: true, data: {
        offers: [sandboxMode ? sandboxHotel() : hotel("tour:2", "제주 바다 호텔", "호텔"),
          hotel("tour:1", "가나다 리조트", "콘도미니엄")],
        meta: {
          listingProvider: "tourapi", priceProvider: sandboxMode ? "liteapi-sandbox" : null,
          matchedPriceCount: sandboxMode ? 1 : 0, totalCount: 2,
          nights: 2, priceSource: sandboxMode ? "SANDBOX" : "UNAVAILABLE",
          priceSourceNotice: sandboxMode
            ? "LiteAPI Sandbox 실습용 요금입니다. 실제 예약 가능 여부나 결제 금액이 아닙니다."
            : "이 지역은 요금 정보가 제공되지 않아 예약 사이트에서 확인해야 해요."
        }
      } });
    }
    return json({ success: true, data: null });
  };

  w.eval(fs.readFileSync(FLIGHTS, "utf8"));
  w.eval(fs.readFileSync(HOTELS, "utf8"));
  if (d.readyState !== "loading") d.dispatchEvent(new w.Event("DOMContentLoaded"));

  await until(() => d.body.dataset.pageReady === "true"
    && w.__accommodationBooking.state.searched
    && !w.__accommodationBooking.state.loading);

  const $ = (id) => d.getElementById(id);
  T("숙소 주소로 들어오면 숙소 탭이 자동으로 열린다", !$("panel-hotel").hidden);
  T("항공 도착지 CJU가 숙소 검색어 제주로 바뀐다", $("h-destination").value === "제주");
  T("항공 일정과 인원이 숙소 검색 조건에 이어진다",
    $("h-checkin").value === "2026-08-17" && $("h-checkout").value === "2026-08-19" && $("h-adults").value === "2");
  T("숙소 탭 진입 시 TourAPI 검색 API를 자동 호출한다",
    urls.some((url) => url.startsWith("/api/v1/accommodations/search?") && url.includes("destination=%EC%A0%9C%EC%A3%BC")));
  T("받아온 숙소 카드가 모두 표시된다", d.querySelectorAll(".hotel-card").length === 2);
  T("TourAPI에 없는 가격을 0원으로 표시하지 않는다",
    [...d.querySelectorAll(".hotel-card")].every((card) => card.textContent.includes("요금 미제공") && !card.textContent.includes("0원")));

  d.querySelector('[data-hotel-sort="name"]').click();
  T("이름순 정렬이 동작한다", d.querySelector(".hotel-card h3").textContent === "가나다 리조트");

  d.querySelector('[data-hotel-pick="tour:1"]').click();
  T("숙소 선택 버튼이 `선택 완료`로 바뀐다",
    d.querySelector('[data-hotel-pick="tour:1"]').textContent.includes("선택 완료"));
  T("오른쪽 예약 현황에 선택한 숙소가 표시된다",
    $("rows").textContent.includes("선택 완료 · 가나다 리조트") && $("rows").textContent.includes("요금 미정"));
  T("숙소 선택 완료가 진행 현황에 반영된다", $("dn").textContent === "1" && $("fill").style.width === "33%");
  T("가격 없는 숙소는 예상 총액에 더하지 않고 안내한다",
    $("cTot").textContent === "256,000원" && $("costNote").textContent.includes("숙소 요금 제외"));
  T("선택은 브라우저 상태에만 있고 DB 저장 API를 호출하지 않는다",
    !urls.some((url) => /\/trips\/\d+\/.*accommodation/.test(url)));

  sandboxMode = true;
  $("hotelSearchForm").dispatchEvent(new w.Event("submit", { bubbles: true, cancelable: true }));
  await until(() => w.__accommodationBooking.state.meta?.priceProvider === "liteapi-sandbox"
    && !w.__accommodationBooking.state.loading);

  T("Sandbox 총액과 통화를 카드에 표시한다",
    d.querySelector('[data-hotel-offer="tour:2"]').textContent.includes("KRW 575,240"));
  T("Sandbox 가격을 실제 결제 금액으로 오해하지 않게 표시한다",
    d.querySelector('[data-hotel-offer="tour:2"]').textContent.includes("실제 결제 금액 아님")
      && $("hotelPriceMode").textContent.includes("실습 요금 1곳"));
  T("무료 취소와 조식 포함 조건을 표시한다",
    d.querySelector('[data-hotel-offer="tour:2"]').textContent.includes("무료 취소 가능")
      && d.querySelector('[data-hotel-offer="tour:2"]').textContent.includes("조식 포함"));

  d.querySelector('[data-hotel-pick="tour:2"]').click();
  T("선택한 Sandbox KRW 요금은 예상 총액에 실습가로 반영한다",
    $("cTot").textContent === "831,240원"
      && $("costNote").textContent.includes("숙소 Sandbox 실습가")
      && $("rows").textContent.includes("575,240원"));

  console.log(`\n${passed} passed, ${failed} failed`);
  process.exit(failed === 0 ? 0 : 1);
}

run().catch((error) => { console.error(error); process.exit(1); });
