/* 여행 예약 · 항공 탭
 *
 * 우리는 항공권을 팔지 않는다. 결제는 항공사·여행사 페이지에서 일어나므로
 * "예약 완료"라는 상태가 이 화면에 존재할 수 없다. 존재하는 것은 두 가지뿐이다.
 *   userReportedBooked — 사용자가 직접 "예약했다"고 표시한 것 (검증 안 됨)
 *   bookingRef         — 예약번호가 입력된 것 (사실상 확정)
 */
(function () {
  "use strict";

  const OUTBOUND = 0;
  const INBOUND = 1;

  /* 일정 충돌 배지와 추천 스코어의 기준.
     가는 편은 1일차 첫 활동 시작 시각, 오는 편은 마지막날 마지막 활동 종료 시각을 본다.
     loadItinerary()가 여행 일정에서 채운다. 비어 있으면 페널티가 0이라 가격만으로 순위가 갈린다. */
  const itinerary = { firstPlanStartAt: null, lastPlanEndAt: null };

  const EXT_NOTICE_KEY = "allmytrips.flightExtNoticeSeen";

  /*
   * 국내선 공항 이름. 코드만 보고 어디인지 아는 사람은 많지 않다.
   *
   * 모르는 코드는 이름 없이 코드만 보여준다. 여기 없는 공항을 억지로 채우면 화면이
   * 틀린 지명을 말하게 된다.
   */
  const AIRPORT_NAMES = {
    GMP: "김포", ICN: "인천", CJU: "제주", PUS: "부산", TAE: "대구",
    KWJ: "광주", CJJ: "청주", USN: "울산", RSU: "여수", HIN: "사천",
    KUV: "군산", WJU: "원주", YNY: "양양", MWX: "무안", KPO: "포항"
  };

  const airportName = (code) => AIRPORT_NAMES[String(code || "").toUpperCase()] || "";
  const airportLabel = (code) => {
    const name = airportName(code);
    return name ? `${name} ${code}` : String(code || "");
  };

  /* `2026-08-27` → `8월 27일`. 연도는 조건에 이미 있고, 줄이 길어지면 노선이 밀린다. */
  const dayLabel = (iso) => {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(iso || ""));
    return match ? `${Number(match[2])}월 ${Number(match[3])}일` : String(iso || "");
  };

  /* 외부 사이트에 이만큼도 머무르지 않았으면 복귀로 보지 않는다.
     오탐을 줄일 뿐 없애지는 못한다. 알림 확인·앱 전환에도 visibilitychange는 발생한다. */
  const MIN_AWAY_MS = 8000;

  const $ = (id) => document.getElementById(id);
  const won = (n) => Math.round(Number(n) || 0).toLocaleString("ko-KR") + "원";
  const text = (id, value) => { const el = $(id); if (el) el.textContent = value; };
  const show = (id) => $(id).classList.add("show");
  const hide = (id) => $(id).classList.remove("show");
  const esc = (s) => String(s ?? "").replace(/[&<>"']/g,
    (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  /* ────────── 상태 ──────────
     이 4개가 전부다. 나머지는 전부 파생값이며 별도 상태로 두지 않는다. */
  const state = {
    leg: OUTBOUND,
    picked: { 0: null, 1: null },              // offerId. 인덱스로 들면 정렬 시 선택이 깨진다
    userReportedBooked: { 0: false, 1: false },
    bookingRef: { 0: "", 1: "" }
  };

  /* 화면 데이터와 서버 연결용 식별자. 상태가 아니라 캐시다. */
  const offers = { 0: [], 1: [] };
  const priceMeta = { 0: null, 1: null };
  const clickId = { 0: null, 1: null };
  let sortKey = "rec";
  let pendingOfferId = null;
  let tripId = null;
  let initialTab = "flight";
  let search = { origin: "GMP", destination: "CJU", departureDate: null, returnDate: null, adults: 2 };
  let hotelSelection = null;
  let ticketReservation = null;
  let bookingSummary = null;
  let bookingSummaryError = null;
  /*
   * 티켓은 여행에 묶이지 않는다(#255). 그래서 여행 기준 요약(booking-summary)이 아니라
   * 사용자 기준으로 따로 받는다. 여행을 안 고르고 담은 티켓도 이 탭에서 결제할 수 있어야
   * 한다 — 예전에는 여행이 없으면 목록 자체가 비어 결제할 길이 없었다. (#276)
   */
  let ticketReservations = null;
  let ticketReservationsError = null;
  /*
   * 예약별 발급 티켓. 결제 응답에 담겨 온 것을 그대로 들고 있는다.
   *
   * 입장 코드(verificationToken)는 결제 응답에만 들어 있고 목록 조회로는 다시 받을 수 없다.
   * 서버가 해시만 저장하기 때문이다. 새로고침하면 코드가 사라지는 것은 그래서이고, 의도한
   * 동작이다. 실제 서비스라면 이 자리에서 QR을 그린다.
   */
  const issuedTickets = {};

  /* ────────── 파생값 ────────── */
  const offerOf = (leg, id) => offers[leg].find((o) => o.offerId === id) || null;
  const recommended = (leg) => offers[leg][0] || null;

  const status = (leg) =>
    state.bookingRef[leg] ? "CONFIRMED" : state.userReportedBooked[leg] ? "USER_REPORTED" : "NONE";

  /* 운임을 모르는 항공편은 0원이 아니라 '값이 없는 것'이다. 총액 계산에서 빠진다. */
  const hasPrice = (offer) => !!offer && offer.totalPrice !== null && offer.totalPrice !== undefined;

  const legPrice = (leg) => {
    const offer = state.picked[leg] ? offerOf(leg, state.picked[leg]) : recommended(leg);
    return hasPrice(offer) ? Number(offer.totalPrice) : 0;
  };

  const airDone = () => state.userReportedBooked[OUTBOUND] && state.userReportedBooked[INBOUND];
  const airTotal = () => legPrice(OUTBOUND) + legPrice(INBOUND);

  /*
   * 예상 총액은 두 가지 모드로 움직인다. (#281 시안 2차)
   *
   *   아무것도 안 골랐을 때 — 추천 항공편 기준의 어림값. 비교를 시작하기 전에도
   *                          이 여행이 대략 얼마인지는 보여야 한다.
   *   하나라도 골랐을 때   — 고른 것만 더한다. 화면에 `✓ 가는 편 항공`이라고 적어 두고
   *                          총액에는 안 고른 편이 섞여 있으면, 그 숫자는 아무것도
   *                          설명하지 못한다.
   *
   * 그래서 숙소만 고른 상태에서는 항공이 빠진 금액이 나온다. 대신 그 사실을 기준
   * 문구에 적는다 — 숫자가 왜 낮은지 화면이 스스로 말해야 한다.
   */
  const chosenLegCount = () =>
    (state.picked[OUTBOUND] ? 1 : 0) + (state.picked[INBOUND] ? 1 : 0);
  const anythingChosen = () => chosenLegCount() > 0 || hotelDone() || ticketDone();
  const chosenAirTotal = () =>
    (state.picked[OUTBOUND] ? legPrice(OUTBOUND) : 0)
    + (state.picked[INBOUND] ? legPrice(INBOUND) : 0);
  const airIsEstimate = () => !airDone();
  const hasOffers = () => offers[OUTBOUND].length > 0 || offers[INBOUND].length > 0;
  const hotelDone = () => !!hotelSelection;
  /*
   * 요금이 있으면 출처와 상관없이 보여주고 합계에 넣는다. 항공이 샘플 운임을 다루는 방식과 같다.
   * 숙소만 샘플을 빼면 카드에는 291,200원이 보이는데 예약 현황은 "요금 미정"이 되어,
   * 요금을 못 가져온 것인지 화면이 안 세는 것인지 구분할 수 없다.
   *
   * 샘플·실습 요금이 운영에 나갈 걱정은 없다. Mock provider는 @Profile("!prod")이고
   * Sandbox provider는 prod에서 호출되지 않으며, 그래도 새어 나오면 검색 단계에서 막는다.
   * 대신 어떤 출처인지는 sub 라벨과 하단 문구에 그대로 드러낸다.
   */
  const hotelHasDisplayPrice = () => hotelDone()
    && hotelSelection.totalPrice !== null
    && hotelSelection.totalPrice !== undefined
    && hotelSelection.priceSource !== "UNAVAILABLE";
  const hotelPriceNote = () => hotelSelection?.priceSource === "SANDBOX" ? " · 실습"
    : hotelSelection?.priceSource === "MOCK" ? " · 샘플" : "";
  const hotelCanAddToTotal = () => hotelHasDisplayPrice()
    && String(hotelSelection.currency || "KRW").toUpperCase() === "KRW";
  const hotelTotal = () => hotelCanAddToTotal() ? Number(hotelSelection.totalPrice) : 0;
  const hotelPriceLabel = () => {
    if (!hotelHasDisplayPrice()) return "요금 미정";
    const currency = String(hotelSelection.currency || "KRW").toUpperCase();
    return currency === "KRW"
      ? won(hotelSelection.totalPrice)
      : `${currency} ${Number(hotelSelection.totalPrice).toLocaleString("ko-KR", {
        minimumFractionDigits: 2, maximumFractionDigits: 2
      })}`;
  };
  const ticketDone = () => !!ticketReservation;
  const ticketTotal = () => ticketDone() ? Number(ticketReservation.totalAmount || 0) : 0;

  /* 목록에 출처가 섞이면 카드마다 개별 표시되고, 하단 문구가 그 사실을 알린다. */
  const sourceLabel = () => offers[state.leg][0]?.priceSourceLabel || "공시운임";

  /* ────────── 정렬 ──────────
     표시 순서만 바꾼다. picked는 offerId로 들고 있으므로 선택은 풀리지 않는다. */
  const SORTS = {
    rec: {
      why: "<b>추천순</b> = 가격 60% + 일정 적합도 40%",
      sort: (list) => list          // 서버가 준 순서를 그대로 쓴다
    },
    price: {
      why: "<b>최저가순</b> = {SOURCE} 기준",
      sort: (list) => list.slice().sort((a, b) => {
        // 운임을 모르는 편은 싸지도 비싸지도 않다. 비교 대상에서 빼고 뒤로 보낸다.
        if (hasPrice(a) !== hasPrice(b)) return hasPrice(a) ? -1 : 1;
        if (!hasPrice(a)) return 0;
        return a.totalPrice - b.totalPrice;
      })
    },
    dep: {
      why: "<b>출발 시간순</b> = 이른 출발 먼저",
      sort: (list) => list.slice().sort((a, b) => a.departureAt.localeCompare(b.departureAt))
    }
  };

  /* ────────── 통신 ──────────
     화면 로드 시점의 인증 상태로 미리 판정하지 않는다. 요청을 보내고 401을 받아 분기한다. */
  let csrf = null;

  async function csrfHeaders() {
    if (!csrf) {
      const res = await fetch("/api/v1/csrf", {
        headers: { Accept: "application/json" }, credentials: "same-origin"
      });
      const payload = await res.json().catch(() => null);
      if (!res.ok || !payload?.headerName || !payload?.token) throw new Error("CSRF_TOKEN_REQUEST_FAILED");
      csrf = payload;
    }
    return { [csrf.headerName]: csrf.token };
  }

  async function request(method, url, body, retried) {
    const headers = { Accept: "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (method !== "GET") Object.assign(headers, await csrfHeaders());

    const res = await fetch(url, {
      method, headers, credentials: "same-origin",
      body: body === undefined ? undefined : JSON.stringify(body)
    });

    if (res.status === 401) {
      const back = encodeURIComponent(location.pathname + location.search);
      location.href = "/auth/login?redirect=" + back;
      throw new Error("UNAUTHORIZED");
    }
    /* 403은 CSRF 토큰 문제일 수 있다. 토큰을 새로 받아 한 번만 다시 시도한다. */
    if (res.status === 403 && !retried) {
      csrf = null;
      return request(method, url, body, true);
    }

    const payload = await res.json().catch(() => null);
    if (!res.ok || payload?.success === false) throw new Error(payload?.message || "REQUEST_FAILED");
    return payload;
  }

  /* tripId가 없으면 비교까지만 하고 저장은 하지 않는다. 화면은 그대로 동작한다. */
  const canPersist = () => tripId !== null;
  const legUrl = (leg, suffix) => `/api/v1/trips/${tripId}/flights/${leg}${suffix || ""}`;

  /* ────────── 검색 ────────── */
  async function loadOffers(leg) {
    const outbound = leg === OUTBOUND;
    const date = outbound ? search.departureDate : search.returnDate;
    if (!date) { offers[leg] = []; return; }

    const params = new URLSearchParams({
      origin: outbound ? search.origin : search.destination,
      destination: outbound ? search.destination : search.origin,
      date,
      adults: String(search.adults),
      nonStop: "true"
    });
    // 가는 편에는 첫 일정만, 오는 편에는 마지막 일정만 기준으로 넘긴다.
    if (outbound && itinerary.firstPlanStartAt) params.set("firstPlanStartAt", itinerary.firstPlanStartAt);
    if (!outbound && itinerary.lastPlanEndAt) params.set("lastPlanEndAt", itinerary.lastPlanEndAt);

    try {
      const payload = await request("GET", "/api/v1/flights/search?" + params.toString());
      offers[leg] = payload.data?.offers || [];
      priceMeta[leg] = payload.data?.meta || null;
    } catch (e) {
      offers[leg] = [];
      priceMeta[leg] = null;
    }
  }

  async function runSearch() {
    $("list").innerHTML = `<p class="empty">항공편을 불러오는 중이에요…</p>`;
    await Promise.all([loadOffers(OUTBOUND), loadOffers(INBOUND)]);
    renderConditionBar();
    render();
    sync();
  }

  /* ────────── 결과 카드 ────────── */
  function render() {
    const list = $("list");
    const leg = state.leg;

    if (!offers[leg].length) {
      list.innerHTML = `<p class="empty">이 조건에 맞는 항공편을 찾지 못했어요. 날짜나 공항을 바꿔보세요.</p>`;
      return;
    }

    renderSourceNote();
    list.innerHTML = SORTS[sortKey].sort(offers[leg]).map((offer) => {
      const on = state.picked[leg] === offer.offerId;
      const st = on ? status(leg) : "NONE";
      /*
       * 고르는 것과 예약하는 것을 나눈다. (#281 시안)
       *
       * 예전에는 카드를 누르면 곧바로 외부 예약 사이트 안내 창이 떴다. 비교하다 눌러
       * 본 사람도 매번 그 창을 닫아야 했다. 이제 누르면 이 편으로 정해지고, 정해진
       * 카드 안에서 예약 사이트로 넘어간다 — 우리는 항공권을 팔지 않으므로 그 단계는
       * 사라질 수 없다.
       */
      const label = st === "CONFIRMED" ? "✓ 예약 확정"
        : st === "USER_REPORTED" ? "✓ 예약함 (직접 표시)"
        : "예약 사이트로 이동 ↗";
      const done = st === "NONE" ? "" : " done";

      const tips = (offer.ribbons || []).slice(0, 2)
        .map((t) => `<span>${esc(t)}</span>`).join("");
      const badges = (offer.badges || []).slice(0, 2)
        .map((b) => `<span class="${esc(b.tone)}">${esc(b.label)}</span>`).join("");

      return `<div class="fl${on ? " sel" : ""}" data-offer="${esc(offer.offerId)}">
        ${on ? `<span class="fcheck" aria-hidden="true">✓</span>` : ""}
        ${tips ? `<div class="tip">${tips}</div>` : ""}
        <div class="fi">
          <div class="lg" data-carrier="${esc(offer.carrierCode)}">${esc(offer.carrierCode)}</div>
          <div class="cx">
            <div class="car">${esc(offer.carrierName)}<small>${esc(offer.flightNumber)}</small></div>
            <div class="tm">
              <div class="tp"><div class="h">${esc(offer.departureTime)}</div><div class="a">${esc(offer.origin)}</div></div>
              <div class="du"><div class="m">${esc(offer.durationLabel)}</div><div class="l"></div></div>
              <div class="tp"><div class="h">${esc(offer.arrivalTime)}</div><div class="a">${esc(offer.destination)}</div></div>
            </div>
            <div class="bg">${badges}</div>
          </div>
        </div>
        <div class="fp">
          <div class="tot${hasPrice(offer) ? "" : " none"}">${hasPrice(offer)
            ? Number(offer.totalPrice).toLocaleString("ko-KR") + "<small>원</small>"
            : esc(offer.priceSourceLabel)}</div>
          <div class="per">${hasPrice(offer)
            ? `성인 ${search.adults}명 · 1인 ${won(offer.pricePerAdult)} · ${esc(offer.priceSourceLabel)}`
            : "예약 사이트에서 확인해 주세요"}</div>
          ${on
            ? `<span class="picked">${st === "NONE" ? "선택됨" : label.replace("✓ ", "")}</span>
               <button type="button" class="pick${done}" data-go="${esc(offer.offerId)}">${label}</button>
               ${st === "NONE"
                 ? `<button type="button" class="unchoose" data-unchoose="1">선택 취소</button>`
                 : ""}`
            : `<button type="button" class="choose" data-choose="${esc(offer.offerId)}">선택하기</button>`}
        </div>
      </div>`;
    }).join("");
  }

  function renderSourceNote() {
    const note = priceMeta[state.leg]?.priceSourceNotice;
    $("srcnote").textContent = note || "";
    $("srcnote").hidden = !note;
  }

  /* ────────── 우측 패널 ────────── */
  /**
   * 항공 한 편의 진행 현황 줄. (#281 시안)
   *
   * <p>가는 편과 오는 편을 따로 센다. 예전에는 `왕복 항공` 한 줄이라 가는 편만 표시한
   * 상태가 진행률에 전혀 반영되지 않았다 — 절반을 끝냈는데 0/3이었다.
   */
  /*
   * 진행 현황 줄 아이콘. 탭과 같은 그림을 쓴다 — 마크업 맨 위의 스프라이트를 가져다 쓰므로
   * 그림이 한 곳에만 있다. 복사해 두면 한쪽만 고쳐져 탭과 목록의 아이콘이 갈린다. (#281)
   */
  const icon = (name) =>
    `<svg class="rowic" aria-hidden="true"><use href="#ic-${name}" /></svg>`;

  function legRow(leg) {
    const st = status(leg);
    const reported = state.userReportedBooked[leg];
    /*
     * 단계가 끝났다는 기준은 `골랐는가`다. 예약 확정은 외부 사이트에서 일어나고 우리가
     * 확인할 수 없어, 그것을 기준으로 삼으면 화면이 영원히 미완으로 남는다. 확정 여부는
     * 같은 줄의 상태 문구로 따로 밝힌다. (#281 시안)
     */
    const chosen = Boolean(state.picked[leg]);
    const label = st === "CONFIRMED" ? "예약 확정"
      : reported ? "예약함"
      : chosen ? "선택됨"
      : leg === INBOUND && !state.picked[OUTBOUND] ? "가는 편 먼저"
      : "미선택";
    const price = legPrice(leg);

    return {
      tab: "flight", leg, done: chosen,
      ic: icon("plane"),
      nm: leg === OUTBOUND ? "가는 편 선택" : "오는 편 선택",
      ds: label,
      pv: chosen && price > 0 ? won(price) : "",
      dim: !(hasOffers() && price > 0)
    };
  }

  function sideRows() {
    const rows = [
      legRow(OUTBOUND),
      legRow(INBOUND),
      { tab: "hotel", done: hotelDone(), ic: icon("hotel"), nm: "숙소 선택",
        ds: hotelDone() ? "선택됨" : "미선택",
        pv: hotelDone() ? hotelPriceLabel() : "", dim: !hotelHasDisplayPrice() },
      { tab: "ticket", done: ticketDone(), ic: icon("ticket"), nm: "티켓·액티비티 선택",
        ds: ticketDone() ? "선택됨" : "미선택",
        pv: ticketDone() ? won(ticketTotal()) : "", dim: !ticketDone() }
    ];

    /*
     * 행을 누르면 그 탭으로 간다. 진행 현황을 보다가 "숙소 선택 전"을 발견했을 때 위쪽
     * 탭을 다시 찾아 누르게 두지 않는다. 눌러도 되는 자리라는 표시로 오른쪽에 꺾쇠를 둔다.
     */
    return rows.map((r, i) => `<button type="button" class="sr${r.done ? " done" : ""}" data-side-tab="${r.tab}"${
      r.leg === undefined ? "" : ` data-side-leg="${r.leg}"`}>
      <span class="num" aria-hidden="true">${i + 1}</span>
      <span class="ic" aria-hidden="true">${r.ic}</span>
      <span class="nm">${r.nm}</span>
      <span class="ds${r.done ? " o" : ""}">${r.ds}</span>
      ${r.done ? `<span class="mk" aria-hidden="true">✓</span>` : ""}
    </button>`).join("");
  }

  function sync() {
    [OUTBOUND, INBOUND].forEach((leg) => {
      const el = $("lp" + leg);
      const st = status(leg);
      const offer = state.picked[leg] ? offerOf(leg, state.picked[leg]) : null;

      if (st !== "NONE" && offer) {
        el.textContent = `✓ ${offer.carrierName} ${offer.flightNumber} · `
          + (hasPrice(offer) ? won(offer.totalPrice) : offer.priceSourceLabel)
          + (st === "CONFIRMED" ? " · 확정" : " · 직접 표시");
        el.className = "lp sel";
      } else if (leg === INBOUND && !state.userReportedBooked[OUTBOUND]) {
        el.textContent = "가는 편 선택 후 진행";
        el.className = "lp";
      } else {
        el.textContent = "항공편을 선택하세요";
        el.className = "lp";
      }
    });

    $("rows").innerHTML = sideRows();

    text("cBase", `성인 ${search.adults}명 기준`);
    const chosenMode = anythingChosen();
    const total = (chosenMode ? chosenAirTotal() : airTotal()) + hotelTotal() + ticketTotal();

    if (!hasOffers()) {
      text("cTot", "—");
      text("cPer", "항공편을 먼저 검색해 주세요");
    } else if (chosenMode && total === 0) {
      /* 고른 것은 있는데 값을 아는 게 하나도 없다. 0원이라고 쓰면 공짜처럼 읽힌다. */
      text("cTot", "—");
      text("cPer", "고른 항목의 요금 정보가 없어요");
    } else {
      text("cTot", won(total));
      text("cPer", `1인 ${won(Math.round(total / search.adults))}`);
    }

    const legs = chosenLegCount();
    const airLabel = !chosenMode ? `항공 추천가 · ${sourceLabel()}`
      : legs === 2 ? (airIsEstimate() ? "항공 선택가" : "항공 확정")
      : legs === 1 ? "항공 1편만 반영"
      : "항공 미선택";
    const hotelLabel = hotelHasDisplayPrice() && !hotelCanAddToTotal()
      ? "숙소 통화 달라 합계 제외"
      : hotelSelection?.priceSource === "SANDBOX"
        ? "숙소 Sandbox 실습가"
        : hotelSelection?.priceSource === "MOCK"
          ? "숙소 샘플가"
          : hotelCanAddToTotal() ? "숙소 선택가" : "숙소 요금 제외";
    const ticketLabel = ticketDone() ? "티켓 모의 예약가" : "티켓 미선택";
    text("costNote", `${airLabel} · ${hotelLabel} · ${ticketLabel}`);
    renderCostLines();
    renderNextStep();

    /*
     * 네 단계로 센다. 가는 편·오는 편·숙소·티켓. (#281 시안)
     *
     * 예전에는 왕복 항공을 한 칸으로 묶어 셋이었는데, 가는 편만 표시한 사람에게 0/3이
     * 나왔다. 절반을 끝냈는데 아무것도 안 한 것처럼 보였다.
     */
    const steps = [
      Boolean(state.picked[OUTBOUND]),
      Boolean(state.picked[INBOUND]),
      hotelDone(),
      ticketDone()
    ];
    const done = steps.filter(Boolean).length;
    text("dn", done);
    text("tabCount", done);
    $("fill").style.width = Math.round((done / steps.length) * 100) + "%";

    renderMine();
  }

  /**
   * 예상 총액이 어디서 나온 값인지 줄로 남긴다. (#281 시안)
   *
   * <p>고른 것만 적는다. 총액만 크게 띄우면 무엇이 들어갔는지 알 수 없고, 숙소 요금이
   * 빠졌다는 사실 같은 것이 숫자 뒤에 숨는다.
   */
  function renderCostLines() {
    const lines = [];

    [OUTBOUND, INBOUND].forEach((leg) => {
      if (!state.picked[leg]) return;
      const price = legPrice(leg);
      lines.push({
        nm: leg === OUTBOUND ? "가는 편 항공" : "오는 편 항공",
        pv: price > 0 ? won(price) : "요금 미제공",
        note: status(leg) === "NONE" ? "예상" : `성인 ${search.adults}명 총액`
      });
    });

    if (hotelDone()) {
      lines.push({
        nm: `숙소 · ${hotelSelection.name}`,
        pv: hotelHasDisplayPrice() ? hotelPriceLabel() : "요금 미정",
        note: hotelHasDisplayPrice()
          ? `${hotelSelection.nightsLabel}${hotelPriceNote()}${hotelCanAddToTotal() ? "" : " · 합계 제외"}`
          : "요금 미제공"
      });
    }

    if (ticketDone()) {
      lines.push({
        nm: `티켓 · ${ticketReservation.productName}`,
        pv: won(ticketTotal()),
        note: "실제 결제 아님"
      });
    }

    $("costLines").innerHTML = lines.length
      ? lines.map((l) => `<div class="costline">
          <span class="ck" aria-hidden="true">✓</span>
          <span class="nm">${esc(l.nm)}<small>${esc(l.note)}</small></span>
          <span class="pv">${esc(l.pv)}</span>
        </div>`).join("")
      : `<p class="costempty">아직 고른 항목이 없어요. 위 금액은 추천 항공편 기준입니다.</p>`;
  }

  /**
   * 다음에 할 일 하나만 띄운다.
   *
   * <p>네 단계를 늘어놓고 무엇부터 할지는 안 알려주는 화면이 되지 않게 한다. 다 골랐으면
   * 버튼을 감춘다 — 누를 것이 없는데 버튼이 남아 있으면 아직 할 일이 있는 줄 안다.
   */
  function renderNextStep() {
    const next = !state.picked[OUTBOUND] ? { label: "다음 단계: 가는 편 선택", tab: "flight", leg: OUTBOUND }
      : !state.picked[INBOUND] ? { label: "다음 단계: 오는 편 선택", tab: "flight", leg: INBOUND }
      : !hotelDone() ? { label: "다음 단계: 숙소 선택", tab: "hotel" }
      : !ticketDone() ? { label: "다음 단계: 티켓·액티비티 선택", tab: "ticket" }
      : null;

    const button = $("nextStep");
    button.hidden = !next;
    if (!next) return;

    button.textContent = next.label;
    button.dataset.nextTab = next.tab;
    if (next.leg === undefined) delete button.dataset.nextLeg;
    else button.dataset.nextLeg = String(next.leg);
  }

  function renderConditionBar() {
    const from = airportLabel(search.origin);
    const to = airportLabel(search.destination);

    text("cond-pax", `성인 ${search.adults}명`);
    text("cond-route", `${from} → ${to}`);
    if (search.departureDate && search.returnDate) {
      text("cond-date", `${dayLabel(search.departureDate)} – ${dayLabel(search.returnDate)}`);
    }
    text("lv0", `${from} → ${to}`);
    text("lv1", `${to} → ${from}`);
    /* 날짜는 위 조건 바가 이미 말한다. 편 헤더에 또 적으면 같은 값이 두 번 나온다. */

    /* 검색 칸의 코드 옆 이름. 모르는 코드면 비운다. */
    text("f-origin-name", airportName($("f-origin").value || search.origin));
    text("f-destination-name", airportName($("f-destination").value || search.destination));
  }

  /* ────────── `내 예약` 탭 ──────────
     복귀 감지는 반드시 실패한다는 전제로, 언제든 수동으로 되돌릴 수 있는 경로를 둔다. */
  function summaryAmount(item) {
    if (item.amount === null || item.amount === undefined) return "요금 미제공";
    const currency = String(item.currency || "KRW").toUpperCase();
    return currency === "KRW" ? won(item.amount)
      : `${currency} ${Number(item.amount).toLocaleString("ko-KR")}`;
  }

  function summaryError(section) {
    return (bookingSummary?.errors || []).find((error) => error.section === section)?.message || "";
  }

  const ticketStatusLabels = {
    PENDING: "결제 대기",
    CONFIRMED: "결제 완료",
    CANCELLED: "취소됨",
    EXPIRED: "만료됨",
    USED: "사용 완료",
  };

  /**
   * 사용자 기준으로 받은 예약을 요약 항목과 같은 모양으로 바꾼다.
   *
   * <p>상태 이름을 화면에서 직접 붙인다. 서버 요약을 거치면 알 수 없는 값이 왔을 때
   * `모의 예약`으로 뭉뚱그려지고, 그러면 결제·취소 버튼이 통째로 사라진다.
   */
  function ticketItems(fallback) {
    const fromServer = (ticketReservations || [])
      .filter((item) => !["CANCELLED", "EXPIRED"].includes(item.status));

    /*
     * 방금 담은 티켓은 화면 상태에만 있고 서버 목록에는 아직 없다. 목록을 다시 받기 전에도
     * 바로 보여야 하므로, 서버에 없으면 화면이 들고 있는 것을 함께 쓴다.
     */
    const known = new Set(fromServer.map((item) => String(item.reservationId)));
    const local = ticketReservation
      && !known.has(String(ticketReservation.reservationId))
      && !["CANCELLED", "EXPIRED"].includes(ticketReservation.status)
      ? [ticketReservation]
      : [];

    /*
     * 사용자 기준 조회가 실패했거나(로그인 전 등) 아직 안 왔으면 여행 요약이 아는 티켓을
     * 그대로 쓴다. 한쪽이 비었다고 화면까지 비면 안 된다.
     */
    if (!fromServer.length && !local.length) {
      return fallback || [];
    }

    return fromServer.concat(local)
      .map((item) => ({
        type: "TICKET",
        referenceId: String(item.reservationId),
        title: item.productName,
        detail: item.optionName,
        status: item.status,
        statusLabel: ticketStatusLabels[item.status] || item.status,
        amount: item.totalAmount,
        currency: item.currency,
        usageDate: item.usageDate,
        quantity: item.quantity,
      }));
  }

  /**
   * 티켓 줄을 그린다. 여행을 고른 경우와 안 고른 경우가 같은 화면을 써야 한다.
   *
   * <p>예전에는 여행이 없으면 상태와 무관하게 `모의 예약`이라고만 적힌 카드가 나오고
   * 결제 버튼이 아예 없었다. 여행 없이 산 티켓을 결제할 길이 화면에 없던 이유다. (#276)
   *
   * <p>PENDING은 아직 결제하지 않은 상태다. 자리를 잡아 두고 있을 뿐이라 시간이 지나면
   * 반납된다(15분). 그래서 결제 버튼이 이 자리에 있어야 한다.
   *
   * <p>취소는 결제 전후 모두 둔다. 손님에게는 둘 다 "취소" 하나이고, 결제했으면 환불까지
   * 함께 일어난다. 다만 확인 문구는 갈린다 — 결제한 건을 취소하면 발급된 티켓이 무효가
   * 되므로 그 사실을 누르기 전에 알려야 한다.
   */
  function ticketRowsHtml(tickets) {
    if (!tickets.length) {
      return `<div class="mn"><div class="mn-h">티켓·액티비티</div><p class="mn-e">아직 담은 티켓이 없어요.</p>
        <div class="mn-a"><button type="button" class="mn-b" data-mine-tab="ticket">티켓 선택하기</button></div></div>`;
    }
    return tickets.map((item) => `<div class="mn${item.status === "CANCELLED" ? " cancelled" : ""}">
      <div class="mn-h">티켓·액티비티 <span class="mn-s">${esc(item.statusLabel)}</span></div>
      <p class="mn-f">${esc(item.title)} · ${esc(item.detail || "")}</p>
      <p class="mn-meta">${esc(item.usageDate || "")} · ${item.quantity || 1}매 · ${esc(summaryAmount(item))} · 실제 결제 아님</p>
      ${item.status === "CONFIRMED"
        ? `<div class="mn-tickets" data-mine-tickets="${esc(item.referenceId)}"></div>`
        : ""}
      <div class="mn-a">${item.status === "PENDING"
        ? `<button type="button" class="mn-b primary" data-mine-ticket-pay="${esc(item.referenceId)}" data-mine-ticket-summary="${esc(`${item.title} · ${summaryAmount(item)}`)}">모의 결제하기</button>`
        : ""}${item.status === "CONFIRMED"
        ? `<button type="button" class="mn-b" data-mine-ticket-show="${esc(item.referenceId)}">발급된 티켓 보기</button>`
        : ""}${item.status === "PENDING" || item.status === "CONFIRMED"
        ? `<button type="button" class="mn-b danger" data-mine-ticket-cancel="${esc(item.referenceId)}"`
          + ` data-mine-ticket-paid="${item.status === "CONFIRMED" ? "1" : ""}">`
          + `${item.status === "CONFIRMED" ? "결제 취소" : "모의 예약 취소"}</button>`
        : ""}${item.status === "CONFIRMED"
        /* 결제까지 끝난 티켓에서 상품 목록으로 돌아가는 버튼은 의미가 없다. 입장 QR로 보낸다. */
        ? `<a class="mn-b" href="/mypage">마이페이지에서 입장 QR</a>`
        : `<button type="button" class="mn-b" data-mine-tab="ticket">다른 티켓 보기</button>`}</div>
    </div>`).join("");
  }

  function renderSummaryMine(container) {
    const items = bookingSummary.items || [];
    const flights = items.filter((item) => item.type === "FLIGHT");
    const stays = items.filter((item) => item.type === "ACCOMMODATION");
    /* 티켓만 사용자 기준으로 갈아끼운다. 항공·숙소는 여행에 묶여 있어 그대로 둔다. */
    const tickets = ticketItems(items.filter((item) => item.type === "TICKET"));

    const section = (type, title, rows, empty) => {
      const error = summaryError(type);
      return `<section class="mine-group"><h3>${title}</h3>${error
        ? `<p class="mn-e mine-error">${esc(error)}</p>`
        : rows || `<p class="mn-e">${empty}</p>`}</section>`;
    };

    const flightRows = flights.map((item) => `<div class="mn">
      <div class="mn-h">${item.leg === OUTBOUND ? "가는 편" : "오는 편"} <span class="mn-s">${esc(item.statusLabel)}</span></div>
      <p class="mn-f">${esc(item.title)} · ${esc(summaryAmount(item))}</p>
      <p class="mn-meta">${esc(item.detail || "")} · ${esc(item.amountSource || "출처 미제공")}</p>
      <div class="mn-a">
        <button type="button" class="mn-b" data-mine-report="${item.leg}"
          ${item.status === "NONE" ? "" : "disabled"}>예약함으로 표시</button>
        <input class="mn-i" data-mine-ref="${item.leg}" maxlength="12" placeholder="예약번호"
          value="${esc(item.bookingRef || "")}" />
        <button type="button" class="mn-b" data-mine-save="${item.leg}">예약번호 저장</button>
      </div>
    </div>`).join("");

    const stayRows = stays.map((item) => `<div class="mn">
      <div class="mn-h">숙소 <span class="mn-s">${esc(item.statusLabel)}</span></div>
      <p class="mn-f">${esc(item.title)} · ${esc(summaryAmount(item))}</p>
      <p class="mn-meta">${esc(item.detail || "")} · ${esc(item.amountSource || "요금 미제공")}</p>
      <div class="mn-a"><button type="button" class="mn-b" data-mine-tab="hotel">숙소에서 확인·변경</button></div>
    </div>`).join("");

    /*
     * PENDING은 아직 결제하지 않은 상태다. 자리를 잡아 두고 있을 뿐이라 시간이 지나면
     * 반납된다(15분). 그래서 결제 버튼이 이 자리에 있어야 한다.
     *
     * 취소는 결제 전후 모두 둔다. 손님에게는 둘 다 "취소" 하나이고, 결제했으면 환불까지
     * 함께 일어난다. 다만 확인 문구는 갈린다 — 결제한 건을 취소하면 발급된 티켓이 무효가
     * 되므로 그 사실을 누르기 전에 알려야 한다.
     */
    const ticketRows = ticketRowsHtml(tickets);

    const globalError = bookingSummaryError
      ? `<p class="mn-e mine-error">${esc(bookingSummaryError)}</p>` : "";
    container.innerHTML = globalError
      + section("FLIGHT", "항공", flightRows, "아직 선택한 항공편이 없어요.")
      + section("ACCOMMODATION", "숙소", stayRows, "아직 선택한 숙소가 없어요.")
      + section("TICKET", "티켓·액티비티", ticketRows, "아직 담은 티켓이 없어요.");

    /* innerHTML로 다시 그리면 티켓 자리도 비므로, 들고 있던 것을 다시 채운다. */
    Object.keys(issuedTickets).forEach(renderIssuedTickets);
  }

  /**
   * 발급된 티켓을 예약 카드 안에 그린다.
   *
   * 입장 코드는 결제 직후에만 있다. 목록으로 다시 불러오면 번호와 유효기간만 온다.
   * 그 차이를 화면이 숨기지 않고 밝힌다 — 코드가 없어진 것을 오류로 오해하지 않도록.
   */
  function renderIssuedTickets(reservationId) {
    const slot = document.querySelector(`[data-mine-tickets="${reservationId}"]`);
    if (!slot) return;
    const tickets = issuedTickets[reservationId];
    if (!tickets || !tickets.length) {
      slot.innerHTML = "";
      return;
    }
    slot.innerHTML = tickets.map((ticket) => `<div class="mn-ticket">
      <b>${esc(ticket.ticketNumber)}</b>
      <span class="mn-meta">${esc(String(ticket.validFrom || "").slice(0, 16).replace("T", " "))}
        ~ ${esc(String(ticket.validUntil || "").slice(0, 16).replace("T", " "))}</span>
      ${ticket.verificationToken
        ? `<code class="mn-ticket-code">${esc(ticket.verificationToken)}</code>`
        /*
         * 예전에는 "결제 직후에만 표시됩니다"라고 안내했는데 이제 사실이 아니다. #265로
         * 마이페이지에서 QR을 언제든 다시 발급받을 수 있다. 그대로 두면 손님이 코드를
         * 놓친 줄 알고 포기한다.
         */
        : `<span class="mn-meta">입장 코드는 마이페이지 &gt; 최근 예약 내역에서 QR로 다시 볼 수 있어요.</span>`}
    </div>`).join("");
  }

  function renderMine() {
    const container = $("mineList");
    if (!container) return;

    if (bookingSummary?.items) {
      renderSummaryMine(container);
      return;
    }

    const flightCards = [OUTBOUND, INBOUND].map((leg) => {
      const offer = state.picked[leg] ? offerOf(leg, state.picked[leg]) : null;
      const name = leg === OUTBOUND ? "가는 편" : "오는 편";
      if (!offer) {
        return `<div class="mn"><div class="mn-h">${name}</div>
          <p class="mn-e">아직 선택한 항공편이 없어요.</p></div>`;
      }
      const st = status(leg);
      const stLabel = st === "CONFIRMED" ? "확정"
        : st === "USER_REPORTED" ? "예약함 (직접 표시)" : "선택만 함";
      return `<div class="mn">
        <div class="mn-h">${name} <span class="mn-s">${stLabel}</span></div>
        <p class="mn-f">${esc(offer.carrierName)} ${esc(offer.flightNumber)} · ${hasPrice(offer) ? won(offer.totalPrice) : esc(offer.priceSourceLabel)}</p>
        <div class="mn-a">
          <button type="button" class="mn-b" data-mine-report="${leg}"
            ${st === "NONE" ? "" : "disabled"}>예약함으로 표시</button>
          <input class="mn-i" data-mine-ref="${leg}" maxlength="12" placeholder="예약번호"
            value="${esc(state.bookingRef[leg])}" />
          <button type="button" class="mn-b" data-mine-save="${leg}">예약번호 저장</button>
        </div>
      </div>`;
    }).join("");

    const hotelState = window.__accommodationBooking?.state || {};
    const hotelStatus = hotelState.status === "CONFIRMED" ? "확정"
      : hotelState.status === "USER_REPORTED" ? "예약함 (직접 표시)"
        : hotelDone() ? "선택 완료" : "선택 전";
    const hotelCard = hotelDone()
      ? `<div class="mn">
          <div class="mn-h">숙소 <span class="mn-s">${hotelStatus}</span></div>
          <p class="mn-f">${esc(hotelSelection.name)} · ${hotelPriceLabel()}</p>
          <p class="mn-meta">${esc(hotelSelection.nightsLabel || "")} · ${esc(hotelSelection.priceSource === "UNAVAILABLE" ? "요금 미제공" : hotelSelection.priceSource)}</p>
          <div class="mn-a"><button type="button" class="mn-b" data-mine-tab="hotel">숙소에서 확인·변경</button></div>
        </div>`
      : `<div class="mn"><div class="mn-h">숙소</div><p class="mn-e">아직 선택한 숙소가 없어요.</p>
          <div class="mn-a"><button type="button" class="mn-b" data-mine-tab="hotel">숙소 선택하기</button></div></div>`;

    /*
     * 여행을 안 고른 경우다. 예전에는 여기서 상태와 무관하게 `모의 예약`이라고만 적힌
     * 카드를 그리고 결제 버튼을 두지 않아, 여행 없이 담은 티켓을 결제할 길이 없었다. (#276)
     * 여행이 있을 때와 같은 줄을 쓴다.
     */
    const ticketCard = ticketRowsHtml(ticketItems());

    container.innerHTML = `<section class="mine-group"><h3>항공</h3>${flightCards}</section>
      <section class="mine-group"><h3>숙소</h3>${hotelCard}</section>
      <section class="mine-group"><h3>티켓·액티비티</h3>${ticketCard}</section>`;
  }

  /**
   * 티켓 예약을 사용자 기준으로 받는다.
   *
   * <p>여행 요약과 달리 `tripId`가 필요 없다. 티켓은 여행에 묶이지 않으므로(#255)
   * 여행을 안 골랐어도 담아둔 티켓이 이 탭에 보여야 하고, 거기서 결제할 수 있어야 한다.
   */
  async function loadTicketReservations() {
    try {
      const payload = await request("GET", "/api/v1/ticket-reservations");
      ticketReservations = Array.isArray(payload?.data) ? payload.data : [];
      ticketReservationsError = null;
    } catch (error) {
      /* 로그인 전이면 401이다. 티켓 목록만 비우고 나머지 화면은 그대로 둔다. */
      ticketReservations = [];
      ticketReservationsError = error.message || "";
    }
  }

  async function loadBookingSummary() {
    await loadTicketReservations();
    if (!canPersist()) {
      /* 여행이 없어도 티켓은 받았으므로 그 부분만 다시 그린다. */
      renderMine();
      return;
    }
    try {
      const payload = await request("GET", `/api/v1/trips/${tripId}/booking-summary`);
      if (!Array.isArray(payload.data?.items)) throw new Error("BOOKING_SUMMARY_INVALID");
      bookingSummary = payload.data;
      bookingSummaryError = null;
    } catch (error) {
      /* 기존 개별 복원 결과는 유지하고, 통합 조회 오류만 해당 탭에서 알린다. */
      bookingSummaryError = "예약 정보를 한 번에 불러오지 못했습니다. 각 탭의 정보는 그대로 확인할 수 있습니다.";
    }
    renderMine();
  }

  /* ────────── 플로우 ────────── */
  function openOut(offerId) {
    const leg = state.leg;
    const offer = offerOf(leg, offerId);
    if (!offer) return;

    /* 이미 표시를 끝낸 구간의 버튼은 다시 이동시키지 않는다. */
    if (state.picked[leg] === offerId && status(leg) !== "NONE") return;

    pendingOfferId = offerId;
    state.picked[leg] = offerId;
    render();
    sync();

    text("m1site", offer.carrierName);
    text("m1nm", `${offer.carrierName} ${offer.flightNumber}`);
    text("m1tm", `${offer.departureTime} → ${offer.arrivalTime}`);
    text("m1pr", hasPrice(offer) ? won(offer.totalPrice) : offer.priceSourceLabel);
    text("m1px", `성인 ${search.adults}명 총액`);
    text("m1lg", offer.carrierCode);
    show("ov1");
  }

  /**
   * 복귀 감지. 정확히 못 한다는 전제로 만든다.
   * visibilitychange는 알림 확인·앱 전환·화면 잠금에도 발생하고 모바일에서 특히 부정확하다.
   * 놓쳤을 때의 복구 경로는 `내 예약` 탭이다.
   */
  function detectReturn(onReturn) {
    const leftAt = Date.now();
    const handler = () => {
      if (document.visibilityState !== "visible") return;
      if (Date.now() - leftAt < MIN_AWAY_MS) return;
      document.removeEventListener("visibilitychange", handler);
      onReturn();
    };
    document.addEventListener("visibilitychange", handler);
  }

  async function goOut() {
    hide("ov1");
    const leg = state.leg;
    const offer = offerOf(leg, pendingOfferId);
    if (!offer) return;

    /* 나가기 전에 선택을 저장한다. 복귀 감지를 놓쳐도 데이터가 사라지지 않아야 한다. */
    if (canPersist()) {
      try {
        const payload = await request("POST", legUrl(leg, "/outbound-click"), {
          offerId: offer.offerId,
          provider: offer.provider,
          carrierCode: offer.carrierCode,
          carrierName: offer.carrierName,
          flightNumber: offer.flightNumber,
          departureAt: offer.departureAt,
          arrivalAt: offer.arrivalAt,
          totalPrice: hasPrice(offer) ? offer.totalPrice : null,
          currency: offer.currency,
          priceSource: offer.priceSource,
          deeplinkUrl: offer.deeplinkUrl
        });
        clickId[leg] = payload.data?.clickId ?? null;
      } catch (e) {
        /* 기록에 실패해도 이동은 막지 않는다. 사용자의 목적은 예약이다. */
      }
    }

    if (offer.deeplinkUrl) window.open(offer.deeplinkUrl, "_blank", "noopener,noreferrer");

    text("m2nm", `${offer.carrierName} ${offer.flightNumber}`);
    text("m2tm", `${offer.departureTime} → ${offer.arrivalTime}`);
    text("m2pr", hasPrice(offer) ? won(offer.totalPrice) : offer.priceSourceLabel);
    text("m2px", `성인 ${search.adults}명 총액`);
    text("m2lg", offer.carrierCode);

    detectReturn(() => show("ov2"));
  }

  /** "아니요, 다시 볼게요" — 선택이 완전히 해제되고 금액이 추천가로 돌아간다. */
  async function reportNo() {
    hide("ov2");
    const leg = state.leg;
    if (canPersist()) {
      const query = clickId[leg] ? `?clickId=${clickId[leg]}` : "";
      try { await request("DELETE", legUrl(leg) + query); } catch (e) { /* 로컬 상태는 되돌린다 */ }
    }
    state.picked[leg] = null;
    state.userReportedBooked[leg] = false;
    state.bookingRef[leg] = "";
    clickId[leg] = null;
    render();
    sync();
  }

  /** "나중에 확인할게요" — 선택은 유지하고 예약 표시만 하지 않는다. */
  async function reportLater() {
    hide("ov2");
    const leg = state.leg;
    if (canPersist()) {
      try {
        await request("PATCH", legUrl(leg, "/report"),
          { userReportedBooked: false, clickId: clickId[leg] });
      } catch (e) { /* 표시하지 않는 것이 기본값이라 실패해도 상태는 같다 */ }
    }
    state.userReportedBooked[leg] = false;
    render();
    sync();
  }

  /** "네, 예약했어요" — 자가 신고. 결제 확인이 아니다. */
  async function reportBooked() {
    hide("ov2");
    const leg = state.leg;
    const offer = offerOf(leg, pendingOfferId);
    if (!offer) return;

    if (canPersist()) {
      try {
        await request("PATCH", legUrl(leg, "/report"),
          { userReportedBooked: true, clickId: clickId[leg] });
      } catch (e) { return; }
    }
    state.userReportedBooked[leg] = true;
    render();
    sync();

    text("m3nm", `${offer.carrierName} ${offer.flightNumber}`);
    text("m3pr", hasPrice(offer) ? won(offer.totalPrice) : offer.priceSourceLabel);
    text("m3px", `성인 ${search.adults}명 총액`);
    text("m3st", "예약함 · 직접 표시");
    text("m3lg", offer.carrierCode);
    $("refInput").value = state.bookingRef[leg] || "";
    $("m3p").innerHTML = leg === OUTBOUND
      ? "가는 편을 <b>예약함 (직접 표시)</b>으로 기록했어요."
      : "왕복 항공을 모두 <b>예약함 (직접 표시)</b>으로 기록했어요.";
    $("m3next").textContent = leg === OUTBOUND ? "오는 편 선택하기 →" : "숙소 예약하러 가기 →";
    show("ov3");
  }

  /** 예약번호가 들어오면 확정으로 승격한다. `나중에`로 닫는 경로에서도 입력값은 저장한다. */
  async function saveRef(leg) {
    const value = $("refInput").value.trim().toUpperCase();
    if (!value) return;
    state.bookingRef[leg] = value;
    if (canPersist()) {
      try { await request("PATCH", legUrl(leg, "/booking-ref"), { bookingRef: value }); }
      catch (e) { /* 로컬 표시는 유지하고 다음 조회에서 정정된다 */ }
    }
  }

  async function saveRefAndNext() {
    const leg = state.leg;
    await saveRef(leg);
    hide("ov3");
    if (leg === OUTBOUND) setLeg(INBOUND); else { render(); sync(); }
  }

  async function closeModal3() {
    await saveRef(state.leg);
    hide("ov3");
    render();
    sync();
  }

  function setLeg(next) {
    state.leg = next;
    document.querySelectorAll(".leg").forEach((el, i) => {
      const on = i === next;
      el.classList.toggle("on", on);
      el.setAttribute("aria-selected", String(on));
    });
    /*
     * 편 이름·노선·상태는 편마다 한 벌씩 있고 고르는 편의 것만 보인다. 두 벌을 동시에
     * 보여주면 어느 편을 고르는 중인지가 흐려진다. (#281 시안)
     */
    [0, 1].forEach((leg) => {
      ["lt", "lv", "lp"].forEach((key) => {
        const el = document.getElementById(key + leg);
        if (el) el.hidden = leg !== next;
      });
    });
    render();
    sync();
  }

  /* ────────── 탭 ────────── */
  function setTab(name) {
    const availableTabs = ["flight", "hotel", "ticket", "mine"];
    const nextTab = availableTabs.includes(name) ? name : "flight";

    document.querySelectorAll(".tab").forEach((t) => {
      const on = t.dataset.tab === nextTab;
      t.classList.toggle("on", on);
      t.setAttribute("aria-selected", String(on));
    });
    availableTabs.forEach((key) => {
      $("panel-" + key).hidden = key !== nextTab;
    });
    if (nextTab === "mine") {
      renderMine();
      void loadBookingSummary();
    }

    // 탭을 주소에 남겨 새로고침하거나 링크를 공유해도 같은 탭이 다시 열린다.
    const url = new URL(location.href);
    if (nextTab === "flight") url.searchParams.delete("tab");
    else url.searchParams.set("tab", nextTab);
    history.replaceState(null, "", url.pathname + url.search + url.hash);
    window.dispatchEvent(new CustomEvent("allmytrips:booking-tab-changed", {
      detail: { tab: nextTab }
    }));
  }

  /* ────────── 저장된 상태 복원 ────────── */
  async function restore() {
    if (!canPersist()) return;
    let payload;
    try { payload = await request("GET", `/api/v1/trips/${tripId}/bookings`); }
    catch (e) { return; }

    (payload.data?.legs || []).forEach((row) => {
      if (!row.offerId) return;
      state.picked[row.leg] = row.offerId;
      state.userReportedBooked[row.leg] = row.status !== "NONE";
      state.bookingRef[row.leg] = row.bookingRef || "";
    });

    /* 복귀 감지를 놓친 건은 다음 방문에 다시 물어본다. */
    const unresolved = (payload.data?.unresolvedClicks || [])[0];
    if (unresolved) showRecall(unresolved);

    sync();
  }

  function showRecall(click) {
    const offer = offerOf(click.leg, click.offerId);
    const name = click.leg === OUTBOUND ? "가는 편" : "오는 편";
    text("recallText", offer
      ? `${name} ${offer.carrierName} ${offer.flightNumber}, 예약하셨나요?`
      : `${name} 항공편, 예약하셨나요?`);
    $("recall").hidden = false;
    $("recallYes").onclick = async () => {
      state.leg = click.leg;
      pendingOfferId = click.offerId;
      clickId[click.leg] = click.clickId;
      $("recall").hidden = true;
      await reportBooked();
    };
    $("recallNo").onclick = async () => {
      state.leg = click.leg;
      clickId[click.leg] = click.clickId;
      $("recall").hidden = true;
      await reportNo();
    };
  }

  /* ────────── 초기화 ────────── */
  function readParams() {
    const params = new URLSearchParams(location.search);
    const id = params.get("tripId");
    tripId = id && /^\d+$/.test(id) ? id : null;
    initialTab = ["flight", "hotel", "ticket", "mine"].includes(params.get("tab"))
      ? params.get("tab")
      : "flight";

    const today = new Date();
    const depart = new Date(today.getTime() + 7 * 86400000);
    const back = new Date(today.getTime() + 9 * 86400000);
    const iso = (d) => d.toISOString().slice(0, 10);

    search.departureDate = params.get("date") || iso(depart);
    search.returnDate = params.get("returnDate") || iso(back);
    search.origin = (params.get("origin") || "GMP").toUpperCase();
    search.destination = (params.get("destination") || "CJU").toUpperCase();
    search.adults = Number(params.get("adults")) || 2;

    $("f-origin").value = search.origin;
    $("f-destination").value = search.destination;
    $("f-depart").value = search.departureDate;
    $("f-return").value = search.returnDate;
    $("f-adults").value = String(search.adults);
  }

  async function loadTripSummary() {
    if (!canPersist()) return;
    try {
      const payload = await request("GET", `/api/v1/trips/${tripId}`);
      const trip = payload.data;
      if (trip?.title) {
        text("cond-trip", trip.title);
        /* 제목에도 얹는다. 여행을 여러 개 굴릴 때 어느 여행인지가 화면 맨 위에 보여야 한다. */
        text("hdTitle", `${trip.title} 예약`);
        document.title = `${trip.title} 예약 · 항공 — All My Trips`;
      }
      if (trip?.startDate) { search.departureDate = trip.startDate; $("f-depart").value = trip.startDate; }
      if (trip?.endDate) { search.returnDate = trip.endDate; $("f-return").value = trip.endDate; }
      if (trip?.companionCount) { search.adults = trip.companionCount; $("f-adults").value = String(trip.companionCount); }
    } catch (e) { /* 여행 정보를 못 읽어도 기본 조건으로 비교는 할 수 있다 */ }
  }

  /* 일정 항목의 시각은 LocalTime이라 "09:00" 또는 "09:00:30"으로 온다.
     초를 채워 길이를 맞춰야 문자열 정렬로 최소·최대를 고를 수 있다. */
  function normalizeTime(value) {
    const m = /^(\d{2}):(\d{2})(?::(\d{2}))?/.exec(String(value ?? ""));
    return m ? `${m[1]}:${m[2]}:${m[3] || "00"}` : null;
  }

  /* sortOrder는 사용자가 정한 표시 순서일 뿐 시간순이 아니다. 값을 직접 비교한다. */
  function sortedTimes(items, field) {
    return (items || []).map((item) => normalizeTime(item?.[field])).filter(Boolean).sort();
  }

  const earliestTime = (items, field) => sortedTimes(items, field)[0] || null;

  const latestTime = (items, field) => {
    const times = sortedTimes(items, field);
    return times.length ? times[times.length - 1] : null;
  };

  /* 일정 항목은 날짜 없이 시각만 갖는다. 그 날의 tripDate와 합쳐야 기준 시각이 된다. */
  const planAt = (date, time) => (date && time ? `${date}T${time}` : null);

  async function itemsOf(tripDayId) {
    const payload = await request("GET", `/api/v1/trip-days/${tripDayId}/items`);
    return payload?.data || [];
  }

  /* 추천순 스코어의 일정 적합도 40%와 일정 충돌 배지가 이 값에서 나온다.
     서버는 두 파라미터를 이미 받고 있고, 여기서 채우지 않으면 모든 후보의
     일정 점수가 1.0으로 평평해져 `추천순`이 `최저가순`과 같아진다.

     1일차나 마지막날에 활동이 하나도 없으면 비워 둔다. 기준으로 삼을 활동이
     없다는 뜻이라, 임의의 시각을 만들어 넣으면 없는 충돌을 있다고 하게 된다. */
  async function loadItinerary() {
    if (!canPersist()) return;
    try {
      const payload = await request("GET", `/api/v1/trips/${tripId}/days`);
      const days = (payload?.data || [])
        .filter((day) => day?.tripDate && day.tripDayId != null)
        .sort((a, b) => String(a.tripDate).localeCompare(String(b.tripDate))
          || (a.dayNumber ?? 0) - (b.dayNumber ?? 0));
      if (!days.length) return;

      const first = days[0];
      const last = days[days.length - 1];
      const firstItems = await itemsOf(first.tripDayId);
      // 당일치기면 같은 날이다. 한 번 읽은 것을 다시 읽지 않는다.
      const lastItems = last.tripDayId === first.tripDayId ? firstItems : await itemsOf(last.tripDayId);

      itinerary.firstPlanStartAt = planAt(first.tripDate, earliestTime(firstItems, "startTime"));
      itinerary.lastPlanEndAt = planAt(last.tripDate, latestTime(lastItems, "endTime"));
    } catch (e) {
      /* 일정을 못 읽어도 가격 비교는 그대로 된다. 일정 점수와 충돌 배지만 빠진다. */
    }
  }

  function bind() {
    /*
     * 출발지와 도착지를 맞바꾼다. 값만 바꾸고 검색하지는 않는다 — 날짜까지 함께 고치려던
     * 사람이 있으면 뒤집자마자 결과가 갈리는 편이 오히려 방해된다. (#281 시안)
     */
    $("swap").addEventListener("click", () => {
      const origin = $("f-origin").value;
      $("f-origin").value = $("f-destination").value;
      $("f-destination").value = origin;
    });

    $("searchForm").addEventListener("submit", (e) => {
      e.preventDefault();
      search.origin = $("f-origin").value.trim().toUpperCase();
      search.destination = $("f-destination").value.trim().toUpperCase();
      search.departureDate = $("f-depart").value;
      search.returnDate = $("f-return").value;
      search.adults = Math.max(1, Number($("f-adults").value) || 1);
      runSearch();
    });

    document.querySelectorAll(".leg").forEach((el) =>
      el.addEventListener("click", () => setLeg(Number(el.dataset.leg))));

    document.querySelectorAll(".sc").forEach((el) =>
      el.addEventListener("click", () => {
        document.querySelectorAll(".sc").forEach((x) => x.classList.remove("on"));
        el.classList.add("on");
        sortKey = el.dataset.sort;
        $("why").innerHTML = SORTS[sortKey].why.replace("{SOURCE}", sourceLabel());
        render();
      }));

    document.querySelectorAll(".tab").forEach((el) =>
      el.addEventListener("click", () => setTab(el.dataset.tab)));

    $("nextStep").addEventListener("click", () => {
      const button = $("nextStep");
      setTab(button.dataset.nextTab);
      if (button.dataset.nextLeg !== undefined) setLeg(Number(button.dataset.nextLeg));
    });

    /* 조건을 고치는 자리는 검색 패널이다. 여기서는 그 자리로 데려다 준다. */
    $("condEdit").addEventListener("click", () => {
      $("formwrap").scrollIntoView({ behavior: "smooth", block: "center" });
      $("f-origin").focus();
    });

    ["f-origin", "f-destination"].forEach((id) => {
      $(id).addEventListener("input", () => {
        text(id + "-name", airportName($(id).value));
      });
    });

    /* 진행 현황의 행은 다시 그려지므로 개별 요소가 아니라 담는 칸에 한 번만 건다. */
    $("rows").addEventListener("click", (e) => {
      const row = e.target.closest("[data-side-tab]");
      if (!row) return;
      setTab(row.dataset.sideTab);
      /* 항공은 어느 편 줄을 눌렀는지까지 따라간다. 탭만 바꾸면 다시 편을 골라야 한다. */
      if (row.dataset.sideLeg !== undefined) setLeg(Number(row.dataset.sideLeg));
    });

    /* 카드는 다시 그려지므로 개별 버튼이 아니라 컨테이너에 한 번만 건다. */
    $("list").addEventListener("click", (e) => {
      /* 고르기: 이 편으로 정한다. 아직 아무 데도 가지 않는다. */
      const choose = e.target.closest("[data-choose]");
      if (choose) {
        state.picked[state.leg] = choose.dataset.choose;
        render();
        sync();
        return;
      }
      /*
       * 선택 취소. 예약 표시를 하기 전까지만 둔다 — 이미 `예약함`으로 표시한 편을 여기서
       * 지우면 밖에서 한 예약 기록이 조용히 사라진다. 그건 `내 예약` 탭에서 되돌린다.
       */
      const unchoose = e.target.closest("[data-unchoose]");
      if (unchoose) {
        state.picked[state.leg] = null;
        render();
        sync();
        return;
      }
      /* 예약: 여기서 외부 사이트로 넘어간다. */
      const go = e.target.closest("[data-go]");
      if (go) openOut(go.dataset.go);
    });

    $("m1go").addEventListener("click", goOut);
    $("m1back").addEventListener("click", () => hide("ov1"));
    $("m2yes").addEventListener("click", reportBooked);
    $("m2no").addEventListener("click", reportNo);
    $("m2later").addEventListener("click", reportLater);
    $("m3next").addEventListener("click", saveRefAndNext);
    $("m3later").addEventListener("click", closeModal3);

    document.querySelectorAll(".ov").forEach((ov) =>
      ov.addEventListener("click", (e) => { if (e.target === ov) ov.classList.remove("show"); }));

    $("extx").addEventListener("click", () => {
      $("extbar").hidden = true;
      try { localStorage.setItem(EXT_NOTICE_KEY, "1"); } catch (e) { /* 저장 못 해도 닫히기는 한다 */ }
    });

    $("mineList").addEventListener("click", async (e) => {
      const tab = e.target.closest("[data-mine-tab]");
      if (tab) {
        setTab(tab.dataset.mineTab);
        return;
      }
      const payTicket = e.target.closest("[data-mine-ticket-pay]");
      if (payTicket) {
        /* 결제수단을 고르게 한다. (#281) 마이페이지 `내 티켓`과 같은 창을 쓴다. */
        const picked = await window.AllMyTripsPayment.choose({
          summary: payTicket.dataset.mineTicketSummary || "",
          confirmLabel: "모의 결제하기",
        });
        if (!picked) return;
        payTicket.disabled = true;
        try {
          const reservationId = payTicket.dataset.mineTicketPay;
          /*
           * 멱등키를 화면에서 만든다. 응답이 유실되어 다시 눌러도 같은 키로 들어가면 서버가
           * 앞의 결과를 그대로 돌려주고 두 번 결제되지 않는다.
           */
          const idempotencyKey = crypto.randomUUID
            ? crypto.randomUUID()
            : `pay-${reservationId}-${Date.now()}`;
          const result = await request("POST", `/api/v1/ticket-reservations/${reservationId}/payment`,
            { method: picked.method, idempotencyKey, easyPayProvider: picked.easyPayProvider });
          /* request()는 data가 아니라 응답 전체를 돌려준다. */
          issuedTickets[reservationId] = result?.data?.tickets || [];
          bookingSummary = null;
          await loadBookingSummary();
          sync();
        } catch (error) {
          bookingSummaryError = error.message || "모의 결제를 완료하지 못했습니다.";
          renderMine();
        } finally {
          payTicket.disabled = false;
        }
        return;
      }
      const showTickets = e.target.closest("[data-mine-ticket-show]");
      if (showTickets) {
        const reservationId = showTickets.dataset.mineTicketShow;
        showTickets.disabled = true;
        try {
          const loaded = await request("GET", `/api/v1/ticket-reservations/${reservationId}/tickets`);
          issuedTickets[reservationId] = loaded?.data || [];
          renderIssuedTickets(reservationId);
        } catch (error) {
          bookingSummaryError = error.message || "발급된 티켓을 불러오지 못했습니다.";
          renderMine();
        } finally {
          showTickets.disabled = false;
        }
        return;
      }
      const cancelTicket = e.target.closest("[data-mine-ticket-cancel]");
      if (cancelTicket) {
        /*
         * 결제한 건은 취소하면 발급된 티켓이 무효가 된다. 누르기 전에 그 사실을 알려야 한다.
         * 결제 전 취소와 같은 문구를 쓰면 티켓이 사라지는 줄 모르고 누른다.
         */
        const paid = cancelTicket.dataset.mineTicketPaid === "1";
        const question = paid
          ? "결제를 취소할까요? 발급된 티켓은 더 이상 사용할 수 없게 됩니다."
          : "이 모의 예약을 취소할까요? 취소한 수량은 다시 예약할 수 있게 됩니다.";
        if (!window.confirm(question)) return;
        cancelTicket.disabled = true;
        try {
          const reservationId = cancelTicket.dataset.mineTicketCancel;
          await request("DELETE", `/api/v1/ticket-reservations/${reservationId}`);
          /* 무효가 된 티켓을 화면에 남겨두지 않는다. */
          delete issuedTickets[reservationId];
          if (String(ticketReservation?.reservationId) === reservationId) ticketReservation = null;
          window.dispatchEvent(new CustomEvent("allmytrips:ticket-cancelled", {
            detail: { reservationId: Number(reservationId) }
          }));
          bookingSummary = null;
          await loadBookingSummary();
          sync();
        } catch (error) {
          bookingSummaryError = error.message || "모의 예약을 취소하지 못했습니다.";
          renderMine();
        } finally {
          cancelTicket.disabled = false;
        }
        return;
      }
      const report = e.target.closest("[data-mine-report]");
      if (report) {
        const leg = Number(report.dataset.mineReport);
        state.leg = leg;
        pendingOfferId = state.picked[leg];
        await reportBooked();
        return;
      }
      const save = e.target.closest("[data-mine-save]");
      if (save) {
        const leg = Number(save.dataset.mineSave);
        const input = document.querySelector(`[data-mine-ref="${leg}"]`);
        const value = input.value.trim().toUpperCase();
        state.bookingRef[leg] = value;
        if (canPersist()) {
          try { await request("PATCH", legUrl(leg, "/booking-ref"), { bookingRef: value }); }
          catch (err) { /* 다음 조회에서 정정된다 */ }
        }
        render();
        sync();
      }
    });

    window.addEventListener("allmytrips:accommodation-selected", (event) => {
      hotelSelection = event.detail?.offer || null;
      bookingSummary = null;
      sync();
    });
    window.addEventListener("allmytrips:ticket-reserved", (event) => {
      ticketReservation = event.detail?.reservation || null;
      bookingSummary = null;
      sync();
    });
  }

  async function init() {
    readParams();
    bind();
    setTab(initialTab);

    let seen = false;
    try { seen = localStorage.getItem(EXT_NOTICE_KEY) === "1"; } catch (e) { /* 비공개 모드 */ }
    $("extbar").hidden = seen;

    // 검색 전에 끝나야 한다. 기준 시각 없이 조회하면 일정 점수가 평평한 결과가 나온다.
    await Promise.all([loadTripSummary(), loadItinerary()]);
    renderConditionBar();
    await runSearch();
    await restore();

    document.body.dataset.pageReady = "true";
  }

  document.addEventListener("DOMContentLoaded", init);

  /* 수용 기준 테스트가 플로우를 직접 태울 수 있도록 노출한다. 화면 코드는 쓰지 않는다. */
  window.__flightBooking = {
    state, offers, setLeg, openOut, goOut,
    reportBooked, reportNo, reportLater, saveRefAndNext, closeModal3,
    status, legPrice, airTotal, airDone, airIsEstimate, render, sync,
    getSearch: () => ({ ...search }),
    getHotelSelection: () => hotelSelection,
    getTicketReservation: () => ticketReservation
  };
})();
