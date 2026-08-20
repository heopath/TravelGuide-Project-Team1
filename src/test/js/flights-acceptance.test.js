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
/* 결제수단 선택 창. 화면에서도 flights.js보다 먼저 올라간다. (#281) */
const PAYMENT_METHODS = path.join(ROOT, "src/main/resources/static/js/core/payment-methods.js");

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
  let summary = options.summary || null;
  const urls = [];
  const calls = [];
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/booking/flights" + (options.query || ""),
    runScripts: "outside-only"
  });
  const w = dom.window;
  const d = w.document;

  // jsdom 기본값은 prerender다. 복귀 감지가 visible을 요구하므로 맞춰준다.
  Object.defineProperty(d, "visibilityState", { value: "visible", configurable: true });

  w.open = () => null;
  w.confirm = () => true;
  w.fetch = async (url, request = {}) => {
    urls.push(url);
    calls.push(`${request.method || "GET"} ${url}`);
    if (url.startsWith("/api/v1/csrf")) {
      return json({ headerName: "X-CSRF-TOKEN", token: "test-token" });
    }
    if (url.startsWith("/api/v1/flights/search")) {
      // 오는 편은 출발지와 도착지가 뒤집혀 조회된다.
      const inbound = url.includes("destination=GMP");
      return json({ success: true, data: { offers: inbound ? OFFERS.inbound : OFFERS.outbound, meta: META } });
    }
    if (/^\/api\/v1\/trips\/\d+\/booking-summary$/.test(url) && summary) {
      return json({ success: true, data: summary });
    }
    if (/^\/api\/v1\/ticket-reservations\/\d+\/payment$/.test(url) && request.method === "POST") {
      /* 멱등키가 실제로 실려 오는지 보려고 본문을 남긴다. */
      w.__lastPaymentBody = request.body;
      const id = url.split("/")[4];
      summary = {
        ...summary,
        items: summary.items.map((item) => String(item.referenceId) === id
          ? { ...item, status: "CONFIRMED", statusLabel: "결제 완료" }
          : item)
      };
      return json({ success: true, data: options.payment || { payment: {}, tickets: [], replayed: false } });
    }
    if (/^\/api\/v1\/ticket-reservations\/\d+\/tickets$/.test(url)) {
      return json({ success: true, data: options.tickets || [] });
    }
    if (/^\/api\/v1\/ticket-reservations\/\d+$/.test(url) && request.method === "DELETE") {
      const id = url.split("/").pop();
      summary = {
        ...summary,
        items: summary.items.map((item) => String(item.referenceId) === id
          ? { ...item, status: "CANCELLED", statusLabel: "취소됨", includedInEstimate: false }
          : item)
      };
      return json({ success: true, data: { reservationId: Number(id), status: "CANCELLED" } });
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

  w.eval(fs.readFileSync(PAYMENT_METHODS, "utf8"));
  w.eval(fs.readFileSync(SCRIPT, "utf8"));

  // 파싱이 아직 끝나지 않았으면 jsdom이 DOMContentLoaded를 알아서 쏜다.
  // 그때 수동으로 한 번 더 쏘면 init()이 두 번 돌아 토글류 핸들러가 두 번 걸리고,
  // `조건 변경`처럼 상태를 뒤집는 동작이 제자리로 돌아온다.
  if (d.readyState !== "loading") {
    d.dispatchEvent(new w.Event("DOMContentLoaded"));
  }
  await until(() => d.body.dataset.pageReady === "true");

  return { w, d, staticTotal, api: w.__flightBooking, urls, calls };
}

function json(body) {
  return { ok: true, status: 200, json: async () => body };
}

/**
 * 결제수단 선택 창에서 하나 고르고 결제한다. (#281)
 *
 * id를 주지 않으면 기본값(카드) 그대로 결제한다. 창이 안 뜨면 until이 시간 초과로 죽는다.
 */
async function pickPaymentMethod(d, id) {
  await until(() => d.querySelector(".pay-method-overlay"));
  const overlay = d.querySelector(".pay-method-overlay");
  if (id) overlay.querySelector(`input[value="${id}"]`).click();
  overlay.querySelector(".primary-button").click();
}

/** 티켓 한 건만 담긴 통합 조회 응답. 결제 전후를 상태만 바꿔 만든다. */
function ticketSummary(status, statusLabel) {
  return {
    tripId: 10,
    items: [
      { type: "TICKET", referenceId: "30", title: "아쿠아리움", detail: "성인",
        status, statusLabel, amount: 40000, currency: "KRW",
        amountSource: "INTERNAL_MOCK", includedInEstimate: true, practice: true,
        usageDate: "2026-08-18", quantity: 2 }
    ],
    money: { estimatedTotal: 40000, practiceTotal: 40000, currency: "KRW", actualPaymentConfirmed: false },
    progress: { done: 1, total: 3 },
    errors: []
  };
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
    const { w, d, staticTotal, calls } = await boot();
    const $ = (id) => d.getElementById(id);

    /*
     * 검색 조건은 접지 않는다. (#281) 예전에는 요약 칩과 내용이 겹친다는 이유로 접어 두고
     * `조건 변경`으로 폈는데, 조건을 고치려면 매번 한 번 더 눌러야 했다.
     */
    T("검색 조건이 처음부터 펴져 있다", $("formwrap").classList.contains("open"));
    T("조건 변경 토글은 없앴다", $("chg") === null);
    T("요약 칩은 제목 줄에 있다",
      Boolean(d.querySelector(".page-heading .cond #cond-trip")));

    /* 왕복은 방향을 자주 뒤집는다. 두 칸을 지웠다 다시 치게 두지 않는다. */
    $("f-origin").value = "GMP";
    $("f-destination").value = "CJU";
    /* 부팅 때 이미 가는 편·오는 편을 조회한다. 그 뒤로 늘었는지만 본다. */
    const searchesBeforeSwap = calls.filter((c) => c.includes("/flights/search")).length;
    $("swap").click();
    T("출발지와 도착지를 맞바꾼다",
      $("f-origin").value === "CJU" && $("f-destination").value === "GMP");
    /* 뒤집자마자 검색까지 나가면 날짜를 함께 고치려던 사람을 방해한다. */
    T("맞바꾸기만으로는 검색하지 않는다",
      calls.filter((c) => c.includes("/flights/search")).length === searchesBeforeSwap);
    $("swap").click();
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
    /* 금액 내역은 진행 현황 줄이 아니라 예상 총액 아래에 붙는다. (#281 시안) */
    T("고른 것이 없으면 금액이 추천가 기준임을 밝힌다",
      $("costLines").textContent.includes("추천 항공편 기준"));

    // 다른 항공편 선택 시 총액 즉시 반영 (7C101 76,000원)
    w.__flightBooking.openOut("mock:7c101");
    /*
     * 하나라도 고르면 총액은 고른 것만 더한다. (#281 시안 2차) 화면에 `✓ 가는 편 항공`이라
     * 적어 두고 총액에는 안 고른 편이 섞여 있으면 그 숫자는 아무것도 설명하지 못한다.
     */
    T("고른 편만 총액에 들어간다", $("cTot").textContent === won(76000 * PAX));
    T("안 고른 편은 기준 문구가 밝힌다", $("costNote").textContent.includes("항공 1편만 반영"));

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
      $("cTot").textContent === won(76000 * PAX + 40000));
    /*
     * 앞에서 가는 편을 골랐고 여기서 티켓까지 잡았으니 네 칸 중 둘이다. 단계는 `골랐는가`로
     * 센다 — 예약 확정은 외부 사이트 일이라 우리가 확인할 수 없다. (#281 시안)
     */
    T("티켓 모의 예약이 진행률과 출처 안내에 반영된다",
      $("dn").textContent === "2" && $("fill").style.width === "50%"
        && $("costNote").textContent.includes("티켓 모의 예약가")
        && $("costLines").textContent.includes("실제 결제 아님"));

    w.dispatchEvent(new w.CustomEvent("allmytrips:accommodation-selected", { detail: { offer: {
      name: "제주 테스트 호텔", totalPrice: null, currency: "KRW",
      priceSource: "UNAVAILABLE", nightsLabel: "2박"
    } } }));
    d.querySelector('[data-tab="mine"]').click();
    T("내 예약 탭에 항공·숙소·티켓 세 종류를 함께 표시한다",
      d.querySelectorAll("#mineList .mine-group").length === 3
        && $("mineList").textContent.includes("제주 테스트 호텔")
        && $("mineList").textContent.includes("제주 아쿠아리움 입장권"));
    T("숙박 요금 미제공과 티켓 모의 예약을 실제 결제액처럼 표시하지 않는다",
      $("mineList").textContent.includes("요금 미제공")
        && $("mineList").textContent.includes("실제 결제 아님"));
    d.querySelector('[data-mine-tab="hotel"]').click();
    T("내 예약에서 숙소 확인 버튼을 누르면 숙소 탭으로 이동한다",
      !$("panel-hotel").hidden && $("panel-mine").hidden);
  }

  /* ────────── 통합 내 예약 조회와 티켓 취소 ────────── */
  {
    const summary = {
      items: [
        { type: "ACCOMMODATION", referenceId: "20", title: "제주 호텔", detail: "2026-08-17 → 2026-08-19",
          status: "SELECTED", statusLabel: "선택 완료", amount: null, currency: "KRW",
          amountSource: "UNAVAILABLE", includedInEstimate: false, practice: false },
        { type: "TICKET", referenceId: "30", title: "아쿠아리움", detail: "성인",
          status: "PENDING", statusLabel: "모의 예약", amount: 40000, currency: "KRW",
          amountSource: "INTERNAL_MOCK", includedInEstimate: true, practice: true,
          usageDate: "2026-08-18", quantity: 2 }
      ],
      money: { estimatedTotal: 40000, practiceTotal: 40000, currency: "KRW", actualPaymentConfirmed: false },
      progress: { done: 1, total: 3 },
      errors: [{ section: "FLIGHT", message: "항공 예약 정보를 불러오지 못했습니다." }]
    };
    const { d, calls } = await boot({ query: "?tripId=10&tab=mine", summary });
    await until(() => d.querySelector("[data-mine-ticket-cancel]"));

    T("통합 조회 일부가 실패해도 숙소와 티켓은 표시한다",
      d.getElementById("mineList").textContent.includes("항공 예약 정보를 불러오지 못했습니다")
        && d.getElementById("mineList").textContent.includes("제주 호텔")
        && d.getElementById("mineList").textContent.includes("아쿠아리움"));
    T("통합 조회가 숙소 요금 미제공과 티켓 실습 금액을 구분한다",
      d.getElementById("mineList").textContent.includes("요금 미제공")
        && d.getElementById("mineList").textContent.includes("실제 결제 아님"));

    d.querySelector("[data-mine-ticket-cancel]").click();
    await until(() => calls.includes("DELETE /api/v1/ticket-reservations/30"));
    await until(() => d.getElementById("mineList").textContent.includes("취소됨"));
    T("내 예약에서 티켓 모의 예약 취소 API를 호출한다",
      calls.includes("DELETE /api/v1/ticket-reservations/30"));
    T("취소한 티켓은 취소됨으로 바뀌고 취소 버튼이 사라진다",
      !d.querySelector("[data-mine-ticket-cancel]")
        && d.getElementById("mineList").textContent.includes("취소됨"));
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

    /* 절반을 끝낸 상태가 진행률에 나타나야 한다. 이것이 4칸으로 쪼갠 이유다. (#281) */
    T("가는 편만 마쳐도 진행률이 오른다",
      $("dn").textContent === "1" && $("fill").style.width === "25%");
    T("진행 현황이 가는 편·오는 편을 따로 센다",
      d.querySelectorAll("#rows [data-side-leg]").length === 2
      && $("rows").textContent.includes("가는 편 선택")
      && $("rows").textContent.includes("오는 편 선택"));

    // 오는 편: 예약번호 없이 `나중에`로 닫아도 자가 신고는 남는다
    api.openOut("mock:ke1284");
    await api.goOut();
    await simulateReturn(w, d);
    await api.reportBooked();
    $("refInput").value = "";
    await api.closeModal3();

    const total = 89000 * PAX + 94000 * PAX;
    T("왕복 모두 표시 완료 시 총액이 유지된다", $("cTot").textContent === won(total));
    /*
     * 가는 편과 오는 편을 따로 센다. (#281) 예전에는 왕복을 한 칸으로 묶어, 가는 편만
     * 표시한 사람에게 0/3이 나왔다 — 절반을 끝냈는데 아무것도 안 한 것처럼 보였다.
     */
    T("왕복을 마치면 항공 두 칸이 함께 찬다", $("dn").textContent === "2");
    T("진행바 50%", $("fill").style.width === "50%");
    T("탭 카운트 2", $("tabCount").textContent === "2");
    // 숙소·티켓 행은 계속 `예상`이므로 항공 행(첫 행)만 본다.
    T("우측 항공 행에서 `예상` 라벨이 사라진다",
      !d.querySelector("#rows .sr").innerHTML.includes("<small>예상</small>"));
    T("예약 표시한 편은 금액 내역이 총액 기준으로 바뀐다",
      $("costLines").innerHTML.includes("성인 2명 총액"));
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
    /* 단계는 `골랐는가`로 센다. 예약 확정은 외부 사이트 일이라 우리가 확인할 수 없다. */
    T("`나중에 확인할게요` → 고른 상태이므로 단계는 그대로 1", $("tabCount").textContent === "1");
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

  /* ── 모의 결제와 발권 (#241) ── */
  {
    const { d } = await boot({ query: "?tripId=10&tab=mine", summary: ticketSummary("PENDING", "결제 대기") });
    await until(() => d.querySelector("[data-mine-ticket-pay]"));

    T("결제 전에는 결제 버튼이 보인다", Boolean(d.querySelector("[data-mine-ticket-pay]")));
    T("결제 전에는 취소도 함께 할 수 있다", Boolean(d.querySelector("[data-mine-ticket-cancel]")));
    T("결제 전에는 발급된 티켓 자리를 두지 않는다", !d.querySelector("[data-mine-tickets]"));
  }
  {
    const { d } = await boot({ query: "?tripId=10&tab=mine", summary: ticketSummary("CONFIRMED", "결제 완료") });
    await until(() => d.querySelector("[data-mine-ticket-show]"));

    T("결제 후에는 결제 버튼이 사라진다", !d.querySelector("[data-mine-ticket-pay]"));
    T("결제 후에도 취소할 수 있다", Boolean(d.querySelector("[data-mine-ticket-cancel]")));
    /*
     * 결제한 건은 취소하면 발급된 티켓이 무효가 된다. 결제 전 취소와 같은 문구를 쓰면
     * 티켓이 사라지는 줄 모르고 누른다. 화면이 둘을 구분할 수 있어야 한다.
     */
    T("결제한 건임을 취소 버튼이 구분해 둔다",
      d.querySelector("[data-mine-ticket-cancel]").dataset.mineTicketPaid === "1");
    T("결제 후 취소 버튼은 결제 취소로 이름이 바뀐다",
      d.querySelector("[data-mine-ticket-cancel]").textContent === "결제 취소");
    T("결제 후에는 발급된 티켓을 볼 수 있다", Boolean(d.querySelector("[data-mine-ticket-show]")));
  }
  {
    // 결제를 누르면 멱등키를 만들어 보내고, 응답의 티켓을 그 자리에 그린다.
    const { d, w, calls } = await boot({
      query: "?tripId=10&tab=mine",
      summary: ticketSummary("PENDING", "결제 대기"),
      payment: {
        payment: { paymentId: 1, status: "PAID", method: "CARD", provider: "MOCK" },
        tickets: [
          { ticketNumber: "AMT-TKN-AAA", validFrom: "2026-08-18T10:00:00+09:00",
            validUntil: "2026-08-19T00:00:00+09:00", verificationToken: "tok-aaa" },
          { ticketNumber: "AMT-TKN-BBB", validFrom: "2026-08-18T10:00:00+09:00",
            validUntil: "2026-08-19T00:00:00+09:00", verificationToken: "tok-bbb" }
        ],
        replayed: false
      }
    });
    await until(() => d.querySelector("[data-mine-ticket-pay]"));

    d.querySelector("[data-mine-ticket-pay]").click();
    /* 결제수단을 고르기 전에는 결제가 나가면 안 된다. (#281) */
    await until(() => d.querySelector(".pay-method-overlay"));
    T("결제수단을 고르기 전에는 결제하지 않는다",
      !calls.some((c) => c.includes("/payment")));

    await pickPaymentMethod(d, "EASY_PAY:KAKAO_PAY");
    await until(() => calls.some((c) => c.includes("POST /api/v1/ticket-reservations/30/payment")));
    await until(() => d.querySelectorAll(".mn-ticket").length === 2);

    const body = JSON.parse(w.__lastPaymentBody || "{}");
    T("결제는 결제 API로 보낸다",
      calls.some((c) => c === "POST /api/v1/ticket-reservations/30/payment"));
    T("결제 요청에 멱등키를 담는다", typeof body.idempotencyKey === "string" && body.idempotencyKey.length > 0);
    T("고른 결제수단을 그대로 보낸다", body.method === "EASY_PAY");
    /* 카카오페이·토스는 method가 같아서 사업자를 함께 보내지 않으면 구분되지 않는다. */
    T("간편결제는 사업자를 함께 보낸다", body.easyPayProvider === "KAKAO_PAY");
    T("고른 뒤에는 선택 창이 닫힌다", !d.querySelector(".pay-method-overlay"));
    T("발급된 티켓을 수량만큼 그린다", d.querySelectorAll(".mn-ticket").length === 2);
    T("입장 코드를 화면에 보여준다",
      d.querySelector(".mn-ticket-code")?.textContent === "tok-aaa");
  }
  {
    /*
     * 선택 창을 닫으면 결제하지 않는다. (#281) 여기서 결제가 나가면 고르다 그만둔 손님의
     * 돈이 나가는 셈이고, 결제 버튼도 눌린 채로 잠겨 다시 시도할 수 없다.
     */
    const { d, calls } = await boot({
      query: "?tripId=10&tab=mine",
      summary: ticketSummary("PENDING", "결제 대기")
    });
    await until(() => d.querySelector("[data-mine-ticket-pay]"));

    d.querySelector("[data-mine-ticket-pay]").click();
    await until(() => d.querySelector(".pay-method-overlay"));
    d.querySelector(".pay-method-overlay .text-button").click();
    await until(() => !d.querySelector(".pay-method-overlay"));

    T("결제수단 선택을 취소하면 결제하지 않는다", !calls.some((c) => c.includes("/payment")));
    T("취소해도 결제 버튼은 다시 누를 수 있다",
      d.querySelector("[data-mine-ticket-pay]").disabled === false);
  }
  {
    /*
     * 결제한 건을 취소하면 발급된 티켓이 무효가 된다. 누르기 전에 그 사실을 알려야 하고,
     * 취소 뒤에는 화면에 남은 티켓도 지워야 한다. 무효가 된 코드를 계속 보여주면
     * 손님이 그것을 들고 현장에 간다.
     */
    const asked = [];
    const { d, w, calls } = await boot({
      query: "?tripId=10&tab=mine",
      summary: ticketSummary("CONFIRMED", "결제 완료"),
      tickets: [{ ticketNumber: "AMT-TKN-AAA", validFrom: null, validUntil: null }]
    });
    await until(() => d.querySelector("[data-mine-ticket-show]"));

    d.querySelector("[data-mine-ticket-show]").click();
    await until(() => d.querySelector(".mn-ticket"));

    w.confirm = (question) => { asked.push(question); return true; };
    d.querySelector("[data-mine-ticket-cancel]").click();
    await until(() => calls.some((c) => c.startsWith("DELETE /api/v1/ticket-reservations/30")));

    T("결제 취소 전에 티켓이 무효가 된다고 알린다",
      asked.length === 1 && asked[0].includes("사용할 수 없게"));
    await until(() => !d.querySelector(".mn-ticket"));
    T("취소하면 화면에 남은 티켓도 지운다", !d.querySelector(".mn-ticket"));
  }
  {
    /*
     * 목록 조회에는 입장 코드가 없다. 서버가 해시만 저장하기 때문이다.
     * 코드가 사라진 것을 오류로 오해하지 않도록 화면이 그 사실을 밝혀야 한다.
     */
    const { d, calls } = await boot({
      query: "?tripId=10&tab=mine",
      summary: ticketSummary("CONFIRMED", "결제 완료"),
      tickets: [
        { ticketNumber: "AMT-TKN-AAA", validFrom: "2026-08-18T10:00:00+09:00",
          validUntil: "2026-08-19T00:00:00+09:00" }
      ]
    });
    await until(() => d.querySelector("[data-mine-ticket-show]"));

    d.querySelector("[data-mine-ticket-show]").click();
    await until(() => calls.some((c) => c.includes("GET /api/v1/ticket-reservations/30/tickets")));
    await until(() => d.querySelector(".mn-ticket"));

    T("발급된 티켓을 다시 불러올 수 있다", Boolean(d.querySelector(".mn-ticket")));
    T("다시 부른 티켓에는 입장 코드가 없다", !d.querySelector(".mn-ticket-code"));
    /*
     * 예전에는 "결제 직후에만 표시됩니다"라고 안내했다. #265로 마이페이지에서 QR을 언제든
     * 다시 발급받을 수 있게 되어 그 문구는 사실이 아니게 됐다. 그대로 두면 손님이 코드를
     * 놓친 줄 알고 포기한다. (#276)
     */
    T("코드를 다시 볼 수 있는 곳을 알린다",
      d.querySelector(".mn-ticket")?.textContent.includes("마이페이지"));
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  process.exit(failed === 0 ? 0 : 1);
}

run().catch((e) => { console.error(e); process.exit(1); });
