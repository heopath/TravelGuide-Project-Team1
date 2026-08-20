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

const mockHotel = () => ({
  ...hotel("mock:1", "개발용 샘플 호텔", "호텔"),
  provider: "mock", nightlyPrice: 42000, totalPrice: 84000,
  priceSource: "MOCK", priceSourceLabel: "샘플"
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
  let mockMode = false;
  let emptyMode = false;
  let providerFailureMode = false;
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
      if (providerFailureMode) {
        return {
          ok: false,
          status: 503,
          json: async () => ({
            success: false,
            code: "ACCOMMODATION_PROVIDER_UNAVAILABLE",
            message: "숙소 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."
          })
        };
      }
      return json({ success: true, data: {
        offers: emptyMode ? [] : mockMode ? [mockHotel()]
          : sandboxMode ? [hotel("tour:1", "가나다 리조트", "콘도미니엄"), sandboxHotel()]
            : [hotel("tour:2", "제주 바다 호텔", "호텔"), hotel("tour:1", "가나다 리조트", "콘도미니엄")],
        meta: {
          listingProvider: emptyMode ? "tourapi" : mockMode ? "mock" : "tourapi",
          priceProvider: sandboxMode && !mockMode ? "liteapi-sandbox" : null,
          matchedPriceCount: sandboxMode && !mockMode ? 1 : 0,
          totalCount: emptyMode ? 0 : mockMode ? 1 : 2,
          nights: 2, priceSource: mockMode ? "MOCK" : sandboxMode ? "SANDBOX" : "UNAVAILABLE",
          priceSourceNotice: emptyMode ? null : mockMode ? "개발용 샘플 데이터입니다." : sandboxMode
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
  T("TourAPI 목록과 가격 미제공 상태를 실제 출처대로 표시한다",
    $("hotelListingSource").textContent.includes("한국관광공사 TourAPI")
      && $("hotelPriceMode").textContent.includes("TourAPI 정보 · 가격 미제공"));

  d.querySelector('[data-hotel-sort="name"]').click();
  T("이름순 정렬이 동작한다", d.querySelector(".hotel-card h3").textContent === "가나다 리조트");

  d.querySelector('[data-hotel-pick="tour:1"]').click();
  T("선택한 숙소 버튼이 `선택 취소`로 바뀐다",
    d.querySelector('[data-hotel-pick="tour:1"]').textContent.includes("선택 취소")
      && d.querySelector('[data-hotel-pick="tour:1"]').getAttribute("aria-pressed") === "true");
  T("오른쪽 예약 현황에 선택한 숙소가 표시된다",
    $("rows").textContent.includes("선택 완료 · 가나다 리조트") && $("rows").textContent.includes("요금 미정"));
  /* 진행 현황은 네 칸이다 — 가는 편·오는 편·숙소·티켓. 숙소 하나면 25%다. (#281) */
  T("숙소 선택 완료가 진행 현황에 반영된다", $("dn").textContent === "1" && $("fill").style.width === "25%");
  T("가격 없는 숙소는 예상 총액에 더하지 않고 안내한다",
    $("cTot").textContent === "200,000원" && $("costNote").textContent.includes("숙소 요금 제외"));
  T("선택은 브라우저 상태에만 있고 DB 저장 API를 호출하지 않는다",
    !urls.some((url) => /\/trips\/\d+\/.*accommodation/.test(url)));

  d.querySelector('[data-hotel-pick="tour:1"]').click();
  T("선택한 숙소 버튼을 다시 누르면 선택이 해제된다",
    w.__accommodationBooking.state.selectedId === null
      && !d.querySelector('[data-hotel-offer="tour:1"]').classList.contains("selected")
      && d.querySelector('[data-hotel-pick="tour:1"]').textContent.includes("이 숙소 선택")
      && d.querySelector('[data-hotel-pick="tour:1"]').getAttribute("aria-pressed") === "false");
  T("숙소 선택 해제가 예약 현황과 진행률에 반영된다",
    $("rows").textContent.includes("선택 전") && $("dn").textContent === "0" && $("fill").style.width === "0%");
  T("숙소 선택 해제 후 예상 총액에서 숙소 금액이 빠진다",
    $("cTot").textContent === "200,000원" && $("costNote").textContent.includes("숙소 요금 제외"));

  emptyMode = true;
  $("hotelSearchForm").dispatchEvent(new w.Event("submit", { bubbles: true, cancelable: true }));
  await until(() => w.__accommodationBooking.state.searched
    && !w.__accommodationBooking.state.loading && w.__accommodationBooking.state.offers.length === 0);
  T("TourAPI 정상 0건은 검색 조건 안내로 표시한다",
    $("hotelStatus").textContent.includes("조건에 맞는 숙소를 찾지 못했어요")
      && $("hotelCount").textContent === "0곳");

  emptyMode = false;
  providerFailureMode = true;
  $("hotelSearchForm").dispatchEvent(new w.Event("submit", { bubbles: true, cancelable: true }));
  await until(() => $("hotelStatus").classList.contains("error")
    && !w.__accommodationBooking.state.loading);
  T("TourAPI 장애는 정상 0건과 다른 재시도 안내로 표시한다",
    $("hotelStatus").textContent === "숙소 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."
      && $("hotelCount").textContent === "검색 실패");

  providerFailureMode = false;
  mockMode = true;
  $("hotelSearchForm").dispatchEvent(new w.Event("submit", { bubbles: true, cancelable: true }));
  await until(() => w.__accommodationBooking.state.meta?.listingProvider === "mock"
    && !w.__accommodationBooking.state.loading);
  T("Mock 폴백이면 상단 목록 출처와 가격 출처를 Mock으로 표시한다",
    $("hotelListingSource").textContent.includes("개발용 Mock 데이터")
      && $("hotelPriceMode").textContent.includes("Mock 개발용 샘플 가격")
      && $("hotelSourceNote").textContent.includes("개발용 샘플 데이터"));

  mockMode = false;
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

  d.querySelector('[data-hotel-sort="price"]').click();
  T("최저가순은 가격이 있는 숙소를 가격 없는 숙소보다 먼저 표시한다",
    d.querySelector(".hotel-card").dataset.hotelOffer === "tour:2");

  d.querySelector('[data-hotel-pick="tour:2"]').click();
  T("선택한 Sandbox KRW 요금은 예상 총액에 실습가로 반영한다",
    $("cTot").textContent === "775,240원"
      && $("costNote").textContent.includes("숙소 Sandbox 실습가")
      && $("rows").textContent.includes("575,240원"));

  d.querySelector('[data-hotel-pick="tour:2"]').click();
  T("가격이 있는 숙소도 선택 취소하면 예상 총액에서 제거된다",
    $("cTot").textContent === "200,000원"
      && $("rows").textContent.includes("선택 전")
      && $("dn").textContent === "0");

}

/* ────────── 여행에 담은 뒤의 예약 플로우 ──────────
 * 위 run()은 tripId가 없어 저장하지 않는 비교 전용 경로다. 여기서는 tripId를 붙여
 * 딥링크 이탈 → 복귀 확인 → 자가 신고 → 예약번호까지 서버와 주고받는 경로를 확인한다.
 */

const bookableHotel = () => ({
  ...hotel("tour:1", "가나다 리조트", "콘도미니엄"),
  deeplinkUrl: "https://www.google.com/search?q=%EC%A0%9C%EC%A3%BC+%EA%B0%80%EB%82%98%EB%8B%A4"
});

const stayOf = (status, bookingRef) => ({
  accommodationBookingId: 31, checkIn: "2026-08-17", checkOut: "2026-08-19", nights: 2,
  status, offerId: "tour:1", provider: "tourapi", name: "가나다 리조트",
  accommodationType: "HOTEL", areaLabel: "제주", address: "제주특별자치도 가나다 리조트길 1",
  rating: null, nightlyPrice: null, totalPrice: null, currency: "KRW",
  priceSource: "UNAVAILABLE", countedInTotal: false, bookingRef: bookingRef || null
});

const bookingsPayload = (stays, unresolvedClicks) => ({
  stays, selectedTotal: 0, isEstimate: true, done: false, priceSource: null,
  unresolvedClicks: unresolvedClicks || []
});

/** 서버 상태를 흉내내는 JSDOM 한 벌. 담긴 숙소와 미해결 이탈 건을 미리 넣을 수 있다. */
async function bootWithTrip(initialStays, unresolvedClicks) {
  const calls = [];
  const opened = [];
  let stays = initialStays || [];
  let unresolved = unresolvedClicks || [];

  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/booking/flights?tab=hotel&tripId=7&destination=CJU"
      + "&date=2026-08-17&returnDate=2026-08-19&adults=2",
    runScripts: "outside-only"
  });
  const w = dom.window;
  const d = w.document;
  Object.defineProperty(d, "visibilityState", { value: "visible", configurable: true });
  w.open = (url) => { opened.push(url); return null; };

  w.fetch = async (url, options) => {
    const method = (options && options.method) || "GET";
    calls.push(`${method} ${url}`);

    if (url.startsWith("/api/v1/accommodations/search")) {
      return json({ success: true, data: {
        offers: [bookableHotel()],
        meta: { listingProvider: "tourapi", priceProvider: null, matchedPriceCount: 0,
          totalCount: 1, nights: 2, priceSource: "UNAVAILABLE", priceSourceNotice: "" }
      } });
    }
    if (url === "/api/v1/trips/7") {
      return json({ success: true, data: {
        startDate: "2026-08-17", endDate: "2026-08-19", destinationName: "제주"
      } });
    }
    if (url === "/api/v1/trips/7/accommodations" && method === "POST") {
      stays = [stayOf("SELECTED", null)];
      return json({ success: true, data: bookingsPayload(stays, unresolved) });
    }
    if (url === "/api/v1/trips/7/accommodations" && method === "GET") {
      return json({ success: true, data: bookingsPayload(stays, unresolved) });
    }
    if (url.endsWith("/outbound-click")) {
      return json({ success: true, data: { clickId: 55 } });
    }
    if (url.endsWith("/report")) {
      const body = JSON.parse(options.body);
      stays = [stayOf(body.userReportedBooked ? "USER_REPORTED" : "SELECTED", null)];
      unresolved = [];
      return json({ success: true, data: bookingsPayload(stays, unresolved) });
    }
    if (url.endsWith("/booking-ref")) {
      stays = [stayOf("CONFIRMED", JSON.parse(options.body).bookingRef)];
      return json({ success: true, data: bookingsPayload(stays, unresolved) });
    }
    if (/\/accommodations\/\d+$/.test(url) && method === "DELETE") {
      stays = [];
      unresolved = [];
      return json({ success: true, data: bookingsPayload(stays, unresolved) });
    }
    return json({ success: true, data: null });
  };

  w.eval(fs.readFileSync(FLIGHTS, "utf8"));
  w.eval(fs.readFileSync(HOTELS, "utf8"));
  if (d.readyState !== "loading") d.dispatchEvent(new w.Event("DOMContentLoaded"));

  await until(() => w.__accommodationBooking.state.searched
    && !w.__accommodationBooking.state.loading);

  return { w, d, calls, opened, api: w.__accommodationBooking };
}

/** 외부 사이트에 다녀온 것처럼 복귀 가드를 넘겨 복귀 이벤트를 발생시킨다. */
async function simulateReturn(w, d) {
  const realNow = Date.now();
  const OriginalDate = w.Date;
  w.Date = class extends OriginalDate {
    static now() { return realNow + 10000; }
  };
  d.dispatchEvent(new w.Event("visibilitychange"));
  await until(() => d.getElementById("hv2").classList.contains("show"));
  w.Date = OriginalDate;
}

async function runBookingFlow() {
  /* ── 담기 → 이탈 → 자가 신고 → 예약번호 ── */
  {
    const { w, d, calls, opened, api } = await bootWithTrip();
    const $ = (id) => d.getElementById(id);

    d.querySelector('[data-hotel-pick="tour:1"]').click();
    await until(() => api.state.bookingId === 31);
    T("여행에 담으면 저장 API를 호출한다",
      calls.includes("POST /api/v1/trips/7/accommodations"));
    T("담은 숙소에만 예약 사이트 이동 버튼이 나온다",
      d.querySelectorAll("[data-hotel-book]").length === 1);

    api.openBooking("tour:1");
    T("이동 버튼 → 외부 이동 안내 모달이 열린다",
      $("hv1").classList.contains("show") && $("h1nm").textContent === "가나다 리조트");
    T("모달에 결제가 외부에서 일어난다는 고지가 있다",
      $("hv1").textContent.includes("숙소를 판매하지 않아요"));

    await api.goOut();
    T("이동 시 이탈 이력을 남긴다",
      calls.includes("POST /api/v1/trips/7/accommodations/31/outbound-click"));
    T("딥링크를 새 탭으로 연다", opened.length === 1 && opened[0].startsWith("https://www.google.com/search"));

    await simulateReturn(w, d);
    T("복귀를 감지하면 예약 여부를 묻는다", $("hv2").classList.contains("show"));

    await api.reportBooked();
    T("`네, 예약했어요` → 자가 신고 API를 호출한다",
      calls.includes("PATCH /api/v1/trips/7/accommodations/31/report"));
    T("카드에 `예약함 · 직접 표시`가 표시된다",
      $("hotelList").textContent.includes("예약함 · 직접 표시"));
    T("예약번호 입력 모달이 열린다", $("hv3").classList.contains("show"));

    $("hotelRefInput").value = "abc123";
    await api.saveRefAndClose();
    T("예약번호를 넣으면 확정으로 승격한다",
      calls.includes("PATCH /api/v1/trips/7/accommodations/31/booking-ref")
        && api.state.status === "CONFIRMED");
    T("확정 상태와 예약번호를 카드에 표시한다",
      $("hotelList").textContent.includes("확정 · ABC123"));
  }

  /* ── `아니요, 다시 볼게요`는 담은 것까지 되돌린다 ── */
  {
    const { w, d, calls, api } = await bootWithTrip();

    d.querySelector('[data-hotel-pick="tour:1"]').click();
    await until(() => api.state.bookingId === 31);
    api.openBooking("tour:1");
    await api.goOut();
    await simulateReturn(w, d);
    await api.reportNo();

    T("`아니요, 다시 볼게요` → 담아둔 숙소를 지운다",
      calls.includes("DELETE /api/v1/trips/7/accommodations/31"));
    T("선택 표시와 상태가 함께 사라진다",
      api.state.selectedId === null && api.state.bookingId === null && api.state.status === null);
  }

  /* ── 복귀를 놓친 건은 다음 방문에 다시 묻는다 ── */
  {
    const { d, calls, api } = await bootWithTrip(
      [stayOf("SELECTED", null)],
      [{ clickId: 55, accommodationBookingId: 31, offerId: "tour:1", name: "가나다 리조트" }]
    );
    const $ = (id) => d.getElementById(id);

    await until(() => !$("hotelRecall").hidden);
    T("답을 못 받은 이탈 건을 재질문 배너로 다시 묻는다",
      $("hotelRecallText").textContent === "가나다 리조트, 예약하셨나요?");

    $("hotelRecallYes").click();
    await until(() => api.state.status === "USER_REPORTED");
    T("배너에서 `네`를 누르면 자가 신고로 이어진다",
      calls.includes("PATCH /api/v1/trips/7/accommodations/31/report") && $("hotelRecall").hidden);
  }
}

async function main() {
  await run();
  await runBookingFlow();
  console.log(`\n${passed} passed, ${failed} failed`);
  process.exit(failed === 0 ? 0 : 1);
}

main().catch((error) => { console.error(error); process.exit(1); });
