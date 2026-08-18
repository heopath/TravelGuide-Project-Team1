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
      const label = st === "CONFIRMED" ? "✓ 예약 확정"
        : st === "USER_REPORTED" ? "✓ 예약함 (직접 표시)"
        : "예약 사이트로 이동 ↗";
      const done = st === "NONE" ? "" : " done";

      const tips = (offer.ribbons || []).slice(0, 2)
        .map((t) => `<span>${esc(t)}</span>`).join("");
      const badges = (offer.badges || []).slice(0, 2)
        .map((b) => `<span class="${esc(b.tone)}">${esc(b.label)}</span>`).join("");

      return `<div class="fl${on ? " sel" : ""}" data-offer="${esc(offer.offerId)}">
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
          <button type="button" class="pick${done}" data-pick="${esc(offer.offerId)}">${label}</button>
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
  function sideRows() {
    const bothConfirmed = status(OUTBOUND) === "CONFIRMED" && status(INBOUND) === "CONFIRMED";
    const air = bothConfirmed ? ["확정", "o", "g"]
      : airDone() ? ["예약함 (직접 표시)", "o", "g"]
      : state.userReportedBooked[OUTBOUND] ? ["오는 편 남음", "w", "b"]
      : ["선택 중", "w", "b"];

    const rows = [
      { ic: "✈", icc: air[2], nm: "왕복 항공", ds: air[0], dsc: air[1],
        pv: hasOffers() ? (airTotal() > 0 ? won(airTotal()) : "미정") : "—", sub: airIsEstimate() ? "예상" : `성인 ${search.adults}명 총액`, dim: false },
      { ic: "▤", icc: hotelDone() ? "g" : "n", nm: "숙소",
        ds: hotelDone() ? `선택 완료 · ${hotelSelection.name}` : "선택 전", dsc: hotelDone() ? "o" : "",
        pv: hotelDone() ? hotelPriceLabel() : "—",
        sub: hotelHasDisplayPrice()
          ? `${hotelSelection.nightsLabel}${hotelPriceNote()}${hotelCanAddToTotal() ? "" : " · 합계 제외"}`
          : hotelDone() ? "요금 미제공" : "숙소에서 선택", dim: !hotelHasDisplayPrice() },
      { ic: "◈", icc: ticketDone() ? "g" : "n", nm: "티켓·액티비티",
        ds: ticketDone() ? `모의 예약 · ${ticketReservation.productName}` : "선택 전",
        dsc: ticketDone() ? "o" : "", pv: ticketDone() ? won(ticketTotal()) : "—",
        sub: ticketDone() ? "실습용 · 실제 결제 아님" : "티켓에서 선택", dim: !ticketDone() }
    ];

    return rows.map((r) => `<div class="sr">
      <div class="ic ${r.icc}" aria-hidden="true">${r.ic}</div>
      <div class="tx"><div class="nm">${r.nm}</div><div class="ds ${r.dsc}">${r.ds}</div></div>
      <div class="pv ${r.dim ? "n" : ""}">${r.pv}<small>${r.sub}</small></div>
    </div>`).join("");
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

    if (hasOffers()) {
      const total = airTotal() + hotelTotal() + ticketTotal();
      text("cTot", won(total));
      text("cPer", `1인 ${won(Math.round(total / search.adults))}`);
    } else {
      text("cTot", "—");
      text("cPer", "항공편을 먼저 검색해 주세요");
    }
    const airLabel = airIsEstimate() ? `항공 추천가 · ${sourceLabel()}` : "항공 확정";
    const hotelLabel = hotelHasDisplayPrice() && !hotelCanAddToTotal()
      ? "숙소 통화 달라 합계 제외"
      : hotelSelection?.priceSource === "SANDBOX"
        ? "숙소 Sandbox 실습가"
        : hotelSelection?.priceSource === "MOCK"
          ? "숙소 샘플가"
          : hotelCanAddToTotal() ? "숙소 선택가" : "숙소 요금 제외";
    const ticketLabel = ticketDone() ? "티켓 모의 예약가" : "티켓 미선택";
    text("costNote", `${airLabel} · ${hotelLabel} · ${ticketLabel}`);

    const done = (airDone() ? 1 : 0) + (hotelDone() ? 1 : 0) + (ticketDone() ? 1 : 0);
    text("dn", done);
    text("tabCount", done);
    $("fill").style.width = Math.round((done / 3) * 100) + "%";

    renderMine();
  }

  function renderConditionBar() {
    text("cond-pax", `성인 ${search.adults}명`);
    text("cond-route", `${search.origin} → ${search.destination}`);
    if (search.departureDate && search.returnDate) {
      text("cond-date", `${search.departureDate} ~ ${search.returnDate}`);
    }
    text("lv0", `${search.origin} → ${search.destination}`);
    text("lv1", `${search.destination} → ${search.origin}`);
    if (search.departureDate) text("lt0", `① 가는 편 · ${search.departureDate}`);
    if (search.returnDate) text("lt1", `② 오는 편 · ${search.returnDate}`);
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

  function renderSummaryMine(container) {
    const items = bookingSummary.items || [];
    const flights = items.filter((item) => item.type === "FLIGHT");
    const stays = items.filter((item) => item.type === "ACCOMMODATION");
    const tickets = items.filter((item) => item.type === "TICKET");

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
    const ticketRows = tickets.map((item) => `<div class="mn${item.status === "CANCELLED" ? " cancelled" : ""}">
      <div class="mn-h">티켓·액티비티 <span class="mn-s">${esc(item.statusLabel)}</span></div>
      <p class="mn-f">${esc(item.title)} · ${esc(item.detail || "")}</p>
      <p class="mn-meta">${esc(item.usageDate || "")} · ${item.quantity || 1}매 · ${esc(summaryAmount(item))} · 실제 결제 아님</p>
      ${item.status === "CONFIRMED"
        ? `<div class="mn-tickets" data-mine-tickets="${esc(item.referenceId)}"></div>`
        : ""}
      <div class="mn-a">${item.status === "PENDING"
        ? `<button type="button" class="mn-b primary" data-mine-ticket-pay="${esc(item.referenceId)}">모의 결제하기</button>`
        : ""}${item.status === "CONFIRMED"
        ? `<button type="button" class="mn-b" data-mine-ticket-show="${esc(item.referenceId)}">발급된 티켓 보기</button>`
        : ""}${item.status === "PENDING" || item.status === "CONFIRMED"
        ? `<button type="button" class="mn-b danger" data-mine-ticket-cancel="${esc(item.referenceId)}"`
          + ` data-mine-ticket-paid="${item.status === "CONFIRMED" ? "1" : ""}">`
          + `${item.status === "CONFIRMED" ? "결제 취소" : "모의 예약 취소"}</button>`
        : ""}${item.status === "CONFIRMED"
        /*
         * 결제까지 끝난 티켓에서 상품 목록으로 돌아가는 버튼(`티켓에서 확인`)은 의미가 없었다.
         * 그 자리에 입장 QR로 가는 길을 둔다. QR은 마이페이지가 담당한다. (#276)
         */
        ? `<a class="mn-b" href="/mypage">마이페이지에서 입장 QR</a>`
        : `<button type="button" class="mn-b" data-mine-tab="ticket">다른 티켓 보기</button>`}</div>
    </div>`).join("");

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

    const ticketCard = ticketDone()
      ? `<div class="mn">
          <div class="mn-h">티켓·액티비티 <span class="mn-s">모의 예약</span></div>
          <p class="mn-f">${esc(ticketReservation.productName)} · ${esc(ticketReservation.optionName || "")}</p>
          <p class="mn-meta">${esc(ticketReservation.usageDate || "")} · ${ticketReservation.quantity || 1}매 · ${won(ticketTotal())} · 실제 결제 아님</p>
          <div class="mn-a"><button type="button" class="mn-b" data-mine-tab="ticket">티켓에서 확인</button></div>
        </div>`
      : `<div class="mn"><div class="mn-h">티켓·액티비티</div><p class="mn-e">아직 담은 티켓이 없어요.</p>
          <div class="mn-a"><button type="button" class="mn-b" data-mine-tab="ticket">티켓 선택하기</button></div></div>`;

    container.innerHTML = `<section class="mine-group"><h3>항공</h3>${flightCards}</section>
      <section class="mine-group"><h3>숙소</h3>${hotelCard}</section>
      <section class="mine-group"><h3>티켓·액티비티</h3>${ticketCard}</section>`;
  }

  async function loadBookingSummary() {
    if (!canPersist()) return;
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
    document.querySelectorAll(".leg").forEach((el, i) => el.classList.toggle("on", i === next));
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
      if (trip?.title) text("cond-trip", trip.title);
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
    $("chg").addEventListener("click", () => {
      const open = $("formwrap").classList.toggle("open");
      $("chg").classList.toggle("open", open);
      $("chg").setAttribute("aria-expanded", String(open));
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

    /* 카드는 다시 그려지므로 개별 버튼이 아니라 컨테이너에 한 번만 건다. */
    $("list").addEventListener("click", (e) => {
      const button = e.target.closest("[data-pick]");
      if (button) openOut(button.dataset.pick);
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
        if (!window.confirm("모의 결제를 진행할까요? 실제 결제는 이루어지지 않고, 결제하면 티켓이 발급됩니다.")) return;
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
            { method: "CARD", idempotencyKey });
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
