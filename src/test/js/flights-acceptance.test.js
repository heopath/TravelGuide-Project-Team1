/* 항공 예약 화면 수용 기준 (스펙 10장)
 *
 * 프로토타입에서 jsdom으로 통과시킨 항목을 실제 화면 코드로 옮긴 것이다.
 * 실행: src/test/js 에서 `npm install` 후 `npm test`
 *
 * Gradle 빌드에는 붙이지 않았다. Java 빌드에 npm 의존성을 끌어들이지 않기 위해서다.
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/booking/flights.html");
const SCRIPT = path.join(ROOT, "src/main/resources/static/js/pages/booking/flights.js");

let passed = 0;
let failed = 0;
const T = (name, condition) => {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name); }
};

/* 배지는 전부 서버가 계산한다. 수하물·운임규정 배지는 스펙에서 삭제됐다. */
const B = {
  late: { code: "LATE_FOR_FIRST_PLAN", label: "첫 일정 늦음", tone: "w" },
  missesLast: { code: "MISSES_LAST_PLAN", label: "마지막 일정 못 함", tone: "w" },
  early: { code: "EARLY_DEPARTURE", label: "이른 출발", tone: "" }
};

const META = {
  scheduleProvider: "mock", priceProvider: null, matchedPriceCount: 0, totalCount: 3,
  priceSource: "PUBLISHED",
  priceSourceNotice: "공시운임 기준입니다. 항공사 특가에 따라 실제 판매가는 더 낮을 수 있어요."
};

/* 프로토타입 DATA와 같은 운임. 서버가 추천순으로 이미 정렬해 내려준 순서다. */
const OFFERS = {
  outbound: [
    offer("mock:ke121", "KE", "대한항공", "KE121", "08:10", "09:20", 89000, ["AI 추천"], []),
    offer("mock:7c101", "7C", "제주항공", "7C101", "10:30", "11:40", 76000, ["최저가"], [B.late]),
    offer("mock:lj301", "LJ", "진에어", "LJ301", "14:00", "15:10", 82000, [], [B.late, B.early])
  ],
  inbound: [
    offer("mock:ke1284", "KE", "대한항공", "KE1284", "18:40", "19:55", 94000, ["AI 추천"], []),
    offer("mock:7c122", "7C", "제주항공", "7C122", "20:15", "21:30", 71000, ["최저가"], []),
    offer("mock:tw716", "TW", "티웨이항공", "TW716", "15:20", "16:35", 68000, [], [B.missesLast])
  ]
};

function offer(offerId, carrierCode, carrierName, flightNumber, dep, arr, perAdult, ribbons, badges) {
  return {
    offerId, provider: "mock", carrierCode, carrierName, flightNumber,
    origin: "GMP", destination: "CJU",
    departureAt: "2026-08-15T" + dep + ":00", arrivalAt: "2026-08-15T" + arr + ":00",
    departureTime: dep, arrivalTime: arr, durationLabel: "1시간 10분",
    pricePerAdult: perAdult, totalPrice: perAdult * 2, currency: "KRW",
    priceSource: "PUBLISHED", priceSourceLabel: "공시운임",
    ribbons, badges, deeplinkUrl: "https://example.test/book"
  };
}

const PAX = 2;
const won = (n) => Math.round(n).toLocaleString("ko-KR") + "원";

/**
 * @param options.query 주소 뒤에 붙일 쿼리스트링. `?tripId=`를 붙이면 저장 경로가 켜진다.
 * @param options.trip  { days, items } 여행 일정 응답. 없으면 일정 API는 빈 응답을 준다.
 */
async function boot(options = {}) {
  const trip = options.trip || null;
  const urls = [];
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/booking/flights" + (options.query || ""),
    runScripts: "outside-only"
  });
  const w = dom.window;
  const d = w.document;

  // jsdom 기본값은 prerender다. 복귀 감지가 visible을 요구하므로 맞춰준다.
  Object.defineProperty(d, "visibilityState", { value: "visible", configurable: true });

  w.open = () => null;
  w.fetch = async (url) => {
    urls.push(url);
    if (url.startsWith("/api/v1/csrf")) {
      return json({ headerName: "X-CSRF-TOKEN", token: "test-token" });
    }
    if (url.startsWith("/api/v1/flights/search")) {
      // 오는 편은 출발지와 도착지가 뒤집혀 조회된다.
      const inbound = url.includes("destination=GMP");
      return json({ success: true, data: { offers: inbound ? OFFERS.inbound : OFFERS.outbound, meta: META } });
    }
    if (trip) {
      if (/^\/api\/v1\/trips\/\d+\/days$/.test(url)) {
        return json({ success: true, data: trip.days });
      }
      const items = /^\/api\/v1\/trip-days\/(\d+)\/items$/.exec(url);
      if (items) return json({ success: true, data: trip.items[items[1]] || [] });
    }
    return json({ success: true, data: null });
  };

  // 정적 마크업이 금액처럼 보이는 값을 미리 박아두지 않았는지 먼저 확인한다.
  const staticTotal = d.getElementById("cTot").textContent.trim();

  w.eval(fs.readFileSync(SCRIPT, "utf8"));

  // 파싱이 아직 끝나지 않았으면 jsdom이 DOMContentLoaded를 알아서 쏜다.
  // 그때 수동으로 한 번 더 쏘면 init()이 두 번 돌아 토글류 핸들러가 두 번 걸리고,
  // `조건 변경`처럼 상태를 뒤집는 동작이 제자리로 돌아온다.
  if (d.readyState !== "loading") {
    d.dispatchEvent(new w.Event("DOMContentLoaded"));
  }
  await until(() => d.body.dataset.pageReady === "true");

  return { w, d, staticTotal, api: w.__flightBooking, urls };
}

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

/** 외부 사이트에 다녀온 것처럼 8초 가드를 넘겨 복귀 이벤트를 발생시킨다. */
async function simulateReturn(w, d) {
  const realNow = Date.now();
  const OriginalDate = w.Date;
  w.Date = class extends OriginalDate {
    static now() { return realNow + 10000; }
  };
  d.dispatchEvent(new w.Event("visibilitychange"));
  await until(() => d.getElementById("ov2").classList.contains("show"));
  w.Date = OriginalDate;
}

async function run() {
  /* ────────── 레이아웃 ────────── */
  {
    const { w, d, staticTotal } = await boot();
    const $ = (id) => d.getElementById(id);

    T("검색 폼이 초기 렌더에서 닫혀 있다", !$("formwrap").classList.contains("open"));
    $("chg").click();
    T("조건 변경 클릭 시 열리고 캐럿이 회전한다",
      $("formwrap").classList.contains("open") && $("chg").classList.contains("open"));
    T("정렬 버튼이 정확히 3개다", d.querySelectorAll(".sort .sc").length === 3);
    T("우측 패널이 1개다", d.querySelectorAll(".side .sc2").length === 1);
    T("카드 어디에도 `직항` 텍스트가 없다", !$("list").innerHTML.includes("직항"));
    T("모든 카드의 배지가 2개 이하다",
      [...d.querySelectorAll(".fl .bg")].every((b) => b.children.length <= 2));
    T("정적 마크업이 틀린 금액을 미리 보여주지 않는다 (숫자 깜빡임 없음)",
      staticTotal === "—" && $("cTot").textContent !== "—");
    T("제휴 고지가 상시 노출된다", d.querySelector(".disc").textContent.includes("수수료"));
    T("모달2 본문에 결제 확인 불가 문구가 있다",
      $("ov2").textContent.includes("외부 사이트의 결제 결과는 저희가 확인할 수 없어요"));
    T("카드 어디에도 기종 텍스트가 없다", !$("list").innerHTML.includes("B737"));
    T("모든 가격에 출처 라벨이 붙는다",
      [...d.querySelectorAll(".fl .per")].every((el) => el.textContent.includes("공시운임")));
    T("목록 하단에 가격 출처 문구가 노출된다",
      !$("srcnote").hidden && $("srcnote").textContent.includes("공시운임 기준"));

    /* ────────── 계산 ────────── */
    const initial = 89000 * PAX + 94000 * PAX;
    T("초기 예상 총액 = 구간0 추천가 + 구간1 추천가 (숙소·티켓 미선택)",
      $("cTot").textContent === won(initial));
    T("1인 금액 = 총액 / 인원 (반올림)",
      $("cPer").textContent === "1인 " + won(Math.round(initial / PAX)));
    T("초기 진행 카운트 0", $("dn").textContent === "0" && $("tabCount").textContent === "0");
    T("항공 행이 `예상`으로 표시된다", $("rows").innerHTML.includes("<small>예상</small>"));

    // 다른 항공편 선택 시 총액 즉시 반영 (7C101 76,000원)
    w.__flightBooking.openOut("mock:7c101");
    T("다른 항공편 선택 시 총액이 즉시 반영된다",
      $("cTot").textContent === won(76000 * PAX + 94000 * PAX));

    // 정렬을 바꿔도 선택이 유지되어야 한다 (offerId 기준)
    d.querySelectorAll(".sc")[1].click();
    T("최저가순 첫 카드가 가장 싼 편이다",
      d.querySelector(".fl .tot").textContent.startsWith((76000 * PAX).toLocaleString("ko-KR")));
    T("정렬을 바꿔도 선택된 카드가 유지된다 (id 기준)",
      d.querySelector(".fl.sel")?.dataset.offer === "mock:7c101");
    d.querySelectorAll(".sc")[0].click();

    w.dispatchEvent(new w.CustomEvent("allmytrips:ticket-reserved", { detail: { reservation: {
      productName: "제주 아쿠아리움 입장권", totalAmount: 40000, status: "PENDING"
    } } }));
    T("티켓 모의 예약 금액이 예상 총액에 반영된다",
      $("cTot").textContent === won(76000 * PAX + 94000 * PAX + 40000));
    T("티켓 모의 예약이 진행률과 출처 안내에 반영된다",
      $("dn").textContent === "1" && $("fill").style.width === "33%"
        && $("costNote").textContent.includes("티켓 모의 예약가")
        && $("rows").textContent.includes("실제 결제 아님"));
  }

  /* ────────── 플로우: 예약함 → 확정 → 왕복 완료 ────────── */
  {
    const { w, d } = await boot();
    const $ = (id) => d.getElementById(id);
    const api = w.__flightBooking;

    api.openOut("mock:ke121");
    T("카드 클릭 → 모달1 표시, picked 즉시 반영",
      $("ov1").classList.contains("show") && api.state.picked[0] === "mock:ke121");

    await api.goOut();
    await simulateReturn(w, d);
    T("이동 → 복귀 감지 후 모달2 표시", $("ov2").classList.contains("show"));

    await api.reportBooked();
    T("`네, 예약했어요` → 버튼이 `✓ 예약함 (직접 표시)`로 바뀐다",
      $("list").innerHTML.includes("✓ 예약함 (직접 표시)"));
    T("모달3 표시", $("ov3").classList.contains("show"));
    T("구간 라벨에 `직접 표시`", $("lp0").textContent.includes("직접 표시"));

    $("refInput").value = "abc123";
    await api.saveRefAndNext();
    T("예약번호 입력 후 진행 → 구간 라벨에 `확정` 표시", $("lp0").textContent.includes("확정"));
    T("오는 편으로 이동한다", d.querySelectorAll(".leg")[1].classList.contains("on"));

    // 오는 편: 예약번호 없이 `나중에`로 닫아도 자가 신고는 남는다
    api.openOut("mock:ke1284");
    await api.goOut();
    await simulateReturn(w, d);
    await api.reportBooked();
    $("refInput").value = "";
    await api.closeModal3();

    const total = 89000 * PAX + 94000 * PAX;
    T("왕복 모두 표시 완료 시 총액이 유지된다", $("cTot").textContent === won(total));
    T("왕복 완료 시에만 진행 카운트 1", $("dn").textContent === "1");
    T("진행바 33%", $("fill").style.width === "33%");
    T("탭 카운트 1", $("tabCount").textContent === "1");
    // 숙소·티켓 행은 계속 `예상`이므로 항공 행(첫 행)만 본다.
    T("우측 항공 행에서 `예상` 라벨이 사라진다",
      !d.querySelector("#rows .sr").innerHTML.includes("<small>예상</small>"));
    T("우측 항공 행이 성인 2명 총액으로 바뀐다", $("rows").innerHTML.includes("성인 2명 총액"));
  }

  /* ────────── 플로우: `나중에` 경로에서도 예약번호가 저장된다 ────────── */
  {
    const { w, d } = await boot();
    const $ = (id) => d.getElementById(id);
    const api = w.__flightBooking;

    api.openOut("mock:ke121");
    await api.goOut();
    await simulateReturn(w, d);
    await api.reportBooked();
    $("refInput").value = "xyz789";
    await api.closeModal3();

    T("`나중에` 버튼으로 닫아도 입력한 예약번호가 저장된다", api.state.bookingRef[0] === "XYZ789");
    T("예약번호가 저장되면 확정으로 승격한다", api.status(0) === "CONFIRMED");
  }

  /* ────────── 플로우: 아니요 / 나중에 확인할게요 ────────── */
  {
    const { w, d } = await boot();
    const $ = (id) => d.getElementById(id);
    const api = w.__flightBooking;
    const initial = won(89000 * PAX + 94000 * PAX);

    api.openOut("mock:7c101");
    await api.goOut();
    await simulateReturn(w, d);
    await api.reportNo();
    T("`아니요` → 선택이 완전히 해제된다", !d.querySelector(".fl.sel") && api.state.picked[0] === null);
    T("`아니요` → 금액이 추천가로 원복된다", $("cTot").textContent === initial);

    api.openOut("mock:7c101");
    await api.goOut();
    await simulateReturn(w, d);
    await api.reportLater();
    T("`나중에 확인할게요` → 선택은 유지된다", !!d.querySelector(".fl.sel"));
    T("`나중에 확인할게요` → 예약 표시는 되지 않는다", !$("list").innerHTML.includes("직접 표시"));
    T("`나중에 확인할게요` → 카운트 0", $("tabCount").textContent === "0");
  }

  /* ────────── 일정 연동 (#133) ────────── */
  {
    /* 1일차 활동은 일부러 시간순이 아니게 넣는다. sortOrder가 아니라 값으로 골라야 한다.
       마지막날에는 종료 시각이 없는 항목을 섞어 그 항목이 최대값을 가리지 않는지 본다. */
    const trip = {
      days: [
        { tripDayId: 41, dayNumber: 1, tripDate: "2026-08-15" },
        { tripDayId: 42, dayNumber: 2, tripDate: "2026-08-16" },
        { tripDayId: 43, dayNumber: 3, tripDate: "2026-08-17" }
      ],
      items: {
        41: [
          { startTime: "13:00:00", endTime: "14:30:00" },
          { startTime: "09:30:00", endTime: "11:00:00" }
        ],
        43: [
          { startTime: "10:00:00", endTime: "11:00:00" },
          { startTime: "19:00:00", endTime: "20:45:00" },
          { startTime: "21:00:00", endTime: null }
        ]
      }
    };

    const { urls } = await boot({ query: "?tripId=7", trip });
    const searches = urls.filter((u) => u.startsWith("/api/v1/flights/search"));
    const outbound = searches.find((u) => !u.includes("destination=GMP")) || "";
    const inbound = searches.find((u) => u.includes("destination=GMP")) || "";

    T("가는 편에 1일차 첫 활동 시작 시각이 실린다",
      outbound.includes("firstPlanStartAt=2026-08-15T09%3A30%3A00"));
    T("오는 편에 마지막날 마지막 활동 종료 시각이 실린다",
      inbound.includes("lastPlanEndAt=2026-08-17T20%3A45%3A00"));
    T("가는 편에는 마지막 일정 기준을 넘기지 않는다", !outbound.includes("lastPlanEndAt"));
    T("오는 편에는 첫 일정 기준을 넘기지 않는다", !inbound.includes("firstPlanStartAt"));
    T("중간 날짜의 일정은 읽지 않는다", !urls.includes("/api/v1/trip-days/42/items"));
  }

  /* ────────── 일정이 없을 때 ────────── */
  {
    // 1일차에 활동이 없으면 기준 삼을 것이 없다. 임의의 시각을 만들면 없는 충돌을 만든다.
    const trip = { days: [{ tripDayId: 51, dayNumber: 1, tripDate: "2026-08-15" }], items: { 51: [] } };
    const { urls } = await boot({ query: "?tripId=8", trip });
    const searches = urls.filter((u) => u.startsWith("/api/v1/flights/search"));

    T("활동이 없으면 기준 시각을 넘기지 않는다",
      searches.length > 0 && searches.every((u) => !u.includes("PlanStartAt") && !u.includes("PlanEndAt")));
  }

  {
    // tripId 없이 들어온 비교 전용 화면. 일정 API를 부르면 안 된다.
    const { urls } = await boot();
    T("tripId가 없으면 일정을 조회하지 않는다", !urls.some((u) => u.includes("/days") || u.includes("/items")));
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  process.exit(failed === 0 ? 0 : 1);
}

run().catch((e) => { console.error(e); process.exit(1); });
