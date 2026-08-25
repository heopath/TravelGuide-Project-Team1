/* 여행 예약 · 숙소 탭
 *
 * TourAPI는 숙소 이름·주소·사진 같은 기본 정보만 제공한다.
 * 이 단계의 "선택 완료"는 예약이나 결제가 아니라 여행에 넣을 숙소 후보를 고른 상태다.
 * DB 저장과 실시간 요금은 각각 별도 단계에서 연결한다.
 */
(function () {
  "use strict";

  const $ = (id) => document.getElementById(id);
  const esc = (value) => String(value ?? "").replace(/[&<>"']/g,
    (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char]));
  const money = (value, currency) => {
    const code = String(currency || "KRW").toUpperCase();
    const digits = code === "KRW" ? 0 : 2;
    return `${code} ${Number(value || 0).toLocaleString("ko-KR", {
      minimumFractionDigits: digits, maximumFractionDigits: digits
    })}`;
  };

  const AIRPORT_AREAS = {
    GMP: "서울", ICN: "인천", CJU: "제주", PUS: "부산", TAE: "대구",
    KWJ: "광주", RSU: "여수", USN: "울산", CJJ: "청주", MWX: "무안",
    KPO: "포항", KUV: "군산", WJU: "원주", YNY: "양양", HIN: "진주"
  };

  const text = (id, value) => { const el = $(id); if (el) el.textContent = value; };
  const show = (id) => $(id).classList.add("show");
  const hide = (id) => $(id).classList.remove("show");

  /* 알림 확인·앱 전환에도 visibilitychange가 발생한다. 너무 빨리 돌아오면 이탈이 아니다.
     오탐을 줄일 뿐 없애지는 못한다. 항공과 같은 값을 쓴다. */
  const MIN_AWAY_MS = 3000;

  const state = {
    offers: [],
    selectedId: null,
    /* 서버에 저장된 행의 id. 선택 해제할 때 이 값으로 지운다. */
    bookingId: null,
    /* SELECTED / USER_REPORTED / CONFIRMED. 담은 것이 없으면 null이다. */
    status: null,
    bookingRef: "",
    /* 지금 답변을 기다리는 이탈 건. 자가 신고가 이 값으로 결과를 적는다. */
    clickId: null,
    /* 여행 없이 비교만 하는 경우 null이다. 그때는 저장하지 않고 브라우저 상태로만 둔다. */
    tripId: new URLSearchParams(window.location.search).get("tripId"),
    sort: "recommended",
    searched: false,
    loading: false,
    meta: null
  };

  const canPersist = () => state.tripId !== null && state.tripId !== "";

  const selectedOffer = () => state.offers.find((offer) => offer.offerId === state.selectedId) || null;
  const hasPrice = (offer) => offer?.totalPrice !== null && offer?.totalPrice !== undefined;

  function safeImageUrl(value) {
    if (!value) return "";
    try {
      const url = new URL(value, location.origin);
      return ["http:", "https:"].includes(url.protocol) ? url.href : "";
    } catch (error) {
      return "";
    }
  }

  function areaFromFlightDestination(value) {
    const normalized = String(value || "").trim().toUpperCase();
    return AIRPORT_AREAS[normalized] || String(value || "").trim();
  }

  function syncDefaultsFromFlight() {
    if (state.searched) return;
    $("h-destination").value = areaFromFlightDestination($("f-destination")?.value) || "제주";
    $("h-checkin").value = $("f-depart")?.value || "";
    $("h-checkout").value = $("f-return")?.value || "";
    $("h-adults").value = $("f-adults")?.value || "2";
  }

  /**
   * 여행에 담을 거면 여행 기간이 기준이다.
   *
   * 항공 폼의 날짜를 그대로 쓰면 여행과 어긋난 기간을 담으려다 서버 검증에 걸린다.
   * 실제로 그랬다 — 여행은 9/10~9/14인데 폼은 항공 검색값인 8/17~8/19였다.
   * tripId가 없으면(비교만 하는 경우) 항공 폼 값을 그대로 둔다.
   */
  async function syncDefaultsFromTrip() {
    if (!canPersist() || state.searched) return;

    try {
      const trip = await request("GET", `/api/v1/trips/${state.tripId}`);
      if (trip?.startDate) $("h-checkin").value = trip.startDate;
      if (trip?.endDate) $("h-checkout").value = trip.endDate;
      if (trip?.destinationName) $("h-destination").value = trip.destinationName;
    } catch (error) {
      /* 여행을 못 읽어도 비교 화면은 살아 있어야 한다. 항공 폼 기본값으로 진행한다. */
    }
  }

  function sortedOffers() {
    const list = state.offers.slice();
    if (state.sort === "price") {
      return list.sort((a, b) => {
        if (hasPrice(a) !== hasPrice(b)) return hasPrice(a) ? -1 : 1;
        if (hasPrice(a) && Number(a.totalPrice) !== Number(b.totalPrice)) {
          return Number(a.totalPrice) - Number(b.totalPrice);
        }
        return String(a.name).localeCompare(String(b.name), "ko");
      });
    }
    if (state.sort === "name") {
      return list.sort((a, b) => String(a.name).localeCompare(String(b.name), "ko"));
    }
    if (state.sort === "type") {
      return list.sort((a, b) => String(a.typeLabel).localeCompare(String(b.typeLabel), "ko")
        || String(a.name).localeCompare(String(b.name), "ko"));
    }
    return list;
  }

  function rating(offer) {
    if (offer.rating === null || offer.rating === undefined) return "";
    const reviews = offer.reviewCount ? ` · 후기 ${Number(offer.reviewCount).toLocaleString("ko-KR")}` : "";
    return `<span class="hotel-rating">★ ${esc(offer.rating)}${esc(reviews)}</span>`;
  }

  function price(offer) {
    if (!hasPrice(offer)) {
      return `<div class="hotel-price none">${esc(offer.priceSourceLabel || "요금 미제공")}</div>
        <div class="hotel-price-sub">실시간 요금은 예약 사이트에서 확인해 주세요</div>`;
    }
    if (offer.priceSource === "SANDBOX") {
      return `<div class="hotel-price">${money(offer.totalPrice, offer.currency)}</div>
        <div class="hotel-price-sub">${esc(offer.nightsLabel)} 총액</div>
        <div class="hotel-practice">LiteAPI Sandbox 실습 요금 · 실제 결제 금액 아님</div>`;
    }
    const sample = offer.priceSource === "MOCK" ? "개발용 샘플" : esc(offer.priceSourceLabel || "");
    return `<div class="hotel-price">${money(offer.totalPrice, offer.currency)}</div>
      <div class="hotel-price-sub">${esc(offer.nightsLabel)} 총액 · ${sample}</div>`;
  }

  function rateFacts(offer) {
    if (!hasPrice(offer) || offer.priceSource === "MOCK") return "";
    const cancellation = offer.freeCancellation
      ? '<span class="hotel-cancel free">무료 취소 가능</span>'
      : '<span class="hotel-cancel no">환불 불가 요금</span>';
    const breakfast = offer.breakfastIncluded ? '<span class="hotel-board">조식 포함</span>' : "";
    return cancellation + breakfast;
  }

  function card(offer) {
    const selected = offer.offerId === state.selectedId;
    const imageUrl = safeImageUrl(offer.imageUrl);
    const ribbons = (offer.ribbons || []).slice(0, 2)
      .map((label) => `<span>${esc(label)}</span>`).join("");
    const amenities = (offer.amenities || []).slice(0, 3)
      .map((item) => `<span>${esc(item)}</span>`).join("");

    return `<article class="hotel-card${selected ? " selected" : ""}" data-hotel-offer="${esc(offer.offerId)}">
      <div class="hotel-image${imageUrl ? "" : " empty"}">
        ${imageUrl ? `<img src="${esc(imageUrl)}" alt="${esc(offer.name)} 숙소 사진" loading="lazy" />` : "<span aria-hidden=\"true\">▤</span>"}
        ${ribbons ? `<div class="hotel-ribbons">${ribbons}</div>` : ""}
      </div>
      <div class="hotel-info">
        <div class="hotel-meta"><span>${esc(offer.typeLabel || "숙소")}</span><span>${esc(offer.areaLabel || "")}</span></div>
        <h3>${esc(offer.name)}</h3>
        <p class="hotel-address">${esc(offer.address || "주소 정보 없음")}</p>
        <div class="hotel-facts">${rating(offer)}${rateFacts(offer)}${amenities ? `<span class="hotel-amenities">${amenities}</span>` : ""}</div>
      </div>
      <div class="hotel-choice">
        ${price(offer)}
        <button type="button" class="hotel-pick${selected ? " done" : ""}" data-hotel-pick="${esc(offer.offerId)}"
                aria-pressed="${selected}">
          ${selected ? "선택 취소" : "이 숙소 선택"}
        </button>
        ${selected ? bookAction(offer) : ""}
      </div>
    </article>`;
  }

  /**
   * 담아둔 숙소에만 예약 사이트 이동과 상태를 붙인다.
   *
   * 고르지 않은 숙소는 보낼 곳을 정할 수 없고, 딥링크가 없는 숙소(provider가 주소를
   * 만들지 못한 경우)는 버튼을 내지 않는다. 눌러도 아무 일이 없는 버튼이 더 나쁘다.
   */
  function bookAction(offer) {
    const label = statusLabel();
    const badge = label
      ? `<span class="hotel-state${state.status === "CONFIRMED" ? " confirmed" : ""}">${esc(label)}</span>`
      : "";
    const button = offer.deeplinkUrl
      ? `<button type="button" class="hotel-book" data-hotel-book="${esc(offer.offerId)}">예약하러 가기 ↗</button>`
      : "";
    return button + badge;
  }

  function statusLabel() {
    if (state.status === "CONFIRMED") return `확정 · ${state.bookingRef}`;
    if (state.status === "USER_REPORTED") return "예약함 · 직접 표시";
    return "";
  }

  function render() {
    const list = sortedOffers();
    $("hotelList").innerHTML = list.map(card).join("");
    $("hotelCount").textContent = state.searched ? `${list.length}곳` : "검색 전";

    if (state.loading) {
      $("hotelStatus").hidden = false;
      $("hotelStatus").className = "hotel-status loading";
      $("hotelStatus").textContent = "숙소 정보를 불러오는 중이에요.";
    } else if (state.searched && !list.length) {
      $("hotelStatus").hidden = false;
      $("hotelStatus").className = "hotel-status";
      $("hotelStatus").textContent = "조건에 맞는 숙소를 찾지 못했어요. 여행지를 조금 넓게 입력해 보세요.";
    } else {
      $("hotelStatus").hidden = state.searched;
      $("hotelStatus").className = "hotel-status";
    }

    const note = state.meta?.priceSourceNotice || "";
    $("hotelSourceNote").textContent = note;
    $("hotelSourceNote").hidden = !note || state.loading;
    const listingProvider = state.meta?.listingProvider;
    const sandboxPrice = state.meta?.priceProvider === "liteapi-sandbox";
    const mockListing = listingProvider === "mock";

    $("hotelListingSource").textContent = state.loading
      ? "숙소 정보 조회 중"
      : mockListing ? "개발용 Mock 데이터"
        : listingProvider === "tourapi" ? "한국관광공사 TourAPI" : "숙소 정보 출처 확인 중";
    $("hotelListingSource").classList.toggle("mock", mockListing);

    $("hotelPriceMode").textContent = state.loading
      ? "가격 정보 조회 중"
      : sandboxPrice ? `LiteAPI Sandbox · 실습 요금 ${state.meta.matchedPriceCount}곳`
        : mockListing ? "Mock 개발용 샘플 가격" : "TourAPI 숙소 정보 · 요금 연동 불가";
    $("hotelPriceMode").classList.toggle("sandbox", sandboxPrice);
    $("hotelPriceMode").classList.toggle("mock", mockListing);
  }

  function showError(message) {
    state.loading = false;
    state.searched = true;
    state.offers = [];
    $("hotelStatus").hidden = false;
    $("hotelStatus").className = "hotel-status error";
    $("hotelStatus").textContent = message;
    $("hotelList").innerHTML = "";
    $("hotelCount").textContent = "검색 실패";
    $("hotelSourceNote").hidden = true;
  }

  function formValues() {
    return {
      destination: $("h-destination").value.trim(),
      checkIn: $("h-checkin").value,
      checkOut: $("h-checkout").value,
      adults: Math.max(1, Number($("h-adults").value) || 1),
      rooms: Math.max(1, Number($("h-rooms").value) || 1),
      currency: "KRW"
    };
  }

  function valid(values) {
    if (!values.destination) return "여행지를 입력해 주세요.";
    if (!values.checkIn || !values.checkOut) return "체크인과 체크아웃 날짜를 선택해 주세요.";
    if (values.checkOut <= values.checkIn) return "체크아웃 날짜는 체크인 다음 날부터 선택할 수 있어요.";
    const nights = (new Date(values.checkOut) - new Date(values.checkIn)) / 86400000;
    if (nights > 30) return "숙박 기간은 최대 30박까지 검색할 수 있어요.";
    return null;
  }

  async function searchHotels() {
    if (state.loading) return;
    const values = formValues();
    const error = valid(values);
    if (error) {
      showError(error);
      return;
    }

    state.loading = true;
    state.searched = true;
    state.meta = null;
    render();
    $("hotelSearchButton").disabled = true;

    const params = new URLSearchParams(values);
    try {
      const response = await fetch(`/api/v1/accommodations/search?${params}`, {
        headers: { Accept: "application/json" }, credentials: "same-origin"
      });
      const payload = await response.json().catch(() => null);
      if (!response.ok || !payload?.success) throw new Error("ACCOMMODATION_SEARCH_FAILED");

      const nextOffers = payload.data?.offers || [];
      /*
       * 선택한 숙소가 새 검색 결과에 없으면 카드로 표시할 방법이 없어 선택 표시를 지운다.
       * 다만 서버에 저장된 것(bookingId 있음)은 지우지 않는다. 다른 날짜를 검색했다고
       * 담아둔 숙소가 사라지면, DB에는 남아 있는데 화면만 비어 새로고침 때 되살아난다.
       */
      const lost = state.selectedId && !nextOffers.some((offer) => offer.offerId === state.selectedId);
      if (lost && !state.bookingId) {
        state.selectedId = null;
        window.dispatchEvent(new CustomEvent("allmytrips:accommodation-selected", { detail: { offer: null } }));
      }
      state.offers = nextOffers;
      state.meta = payload.data?.meta || null;
      state.loading = false;
      render();
    } catch (requestError) {
      showError("숙소 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      $("hotelSearchButton").disabled = false;
    }
  }

  /**
   * 선택을 화면에 먼저 반영하고 서버에 저장한다.
   *
   * 서버 응답을 기다렸다가 그리면 클릭이 굼떠 보인다. 저장이 실패하면 되돌리고 안내한다.
   * tripId가 없으면(여행 없이 비교만 하는 경우) 저장하지 않고 브라우저 상태로만 둔다.
   */
  async function select(offerId) {
    const offer = state.offers.find((item) => item.offerId === offerId);
    if (!offer) return;

    const deselected = state.selectedId === offerId;
    const previousId = state.selectedId;
    const previousBookingId = state.bookingId;
    const previousStatus = state.status;
    const previousRef = state.bookingRef;

    state.selectedId = deselected ? null : offerId;
    /* 다른 숙소를 고르면 이전 숙소의 예약 표시는 남을 수 없다. 서버 upsert도 같은 판단이다. */
    state.status = deselected ? null : "SELECTED";
    state.bookingRef = "";
    state.clickId = null;
    render();
    window.dispatchEvent(new CustomEvent("allmytrips:accommodation-selected", {
      detail: { offer: deselected ? null : { ...offer } }
    }));

    if (!canPersist()) return;

    try {
      if (deselected) {
        if (previousBookingId) await removeStay(previousBookingId);
        state.bookingId = null;
      } else {
        state.bookingId = await saveStay(offer);
      }
    } catch (error) {
      /* 저장에 실패했는데 화면만 선택된 채로 두면 새로고침 때 사라져 더 혼란스럽다. */
      state.selectedId = previousId;
      state.bookingId = previousBookingId;
      state.status = previousStatus;
      state.bookingRef = previousRef;
      render();
      window.dispatchEvent(new CustomEvent("allmytrips:accommodation-selected", {
        detail: { offer: previousId ? { ...state.offers.find((item) => item.offerId === previousId) } : null }
      }));
      showError(error.message || "숙소를 담지 못했어요. 잠시 후 다시 시도해주세요.");
    }
  }

  async function saveStay(offer, targetTripId = state.tripId) {
    const payload = await request("POST", `/api/v1/trips/${targetTripId}/accommodations`, {
      checkIn: $("h-checkin").value,
      checkOut: $("h-checkout").value,
      offerId: offer.offerId,
      provider: offer.provider,
      name: offer.name,
      accommodationType: offer.type,
      areaLabel: offer.areaLabel,
      address: offer.address,
      rating: offer.rating,
      latitude: offer.latitude,
      longitude: offer.longitude,
      nightlyPrice: offer.nightlyPrice,
      totalPrice: offer.totalPrice,
      currency: offer.currency,
      priceSource: offer.priceSource,
      rooms: Number($("h-rooms")?.value) || 1,
      adults: Number($("h-adults")?.value) || 2
    });
    const saved = (payload?.stays || []).find((stay) => stay.offerId === offer.offerId);
    if (!saved) return null;
    /* 상태는 서버가 정한다. 같은 기간을 다시 고르면 예약 표시가 초기화되는 것도 서버 규칙이다. */
    state.status = saved.status;
    state.bookingRef = saved.bookingRef || "";
    return saved.accommodationBookingId;
  }

  /**
   * 여행을 고르지 않은 상태에서 담아 둔 숙소를 나중에 선택한 여행에 연결한다.
   * 화면의 선택값은 그대로 두고 저장 대상 여행만 갱신한다.
   */
  async function attachToTrip(nextTripId) {
    const normalizedTripId = String(nextTripId || "").trim();
    if (!/^\d+$/.test(normalizedTripId)) {
      throw new Error("예약을 저장할 여행 정보가 올바르지 않습니다.");
    }

    const offer = selectedOffer();
    if (!offer) throw new Error("선택한 숙소 정보를 찾지 못했습니다.");

    const bookingId = await saveStay(offer, normalizedTripId);
    if (!bookingId) throw new Error("숙소 선택을 여행에 저장하지 못했습니다.");
    state.tripId = normalizedTripId;
    state.bookingId = bookingId;
    render();
    return state.bookingId;
  }

  async function removeStay(bookingId) {
    await request("DELETE", `/api/v1/trips/${state.tripId}/accommodations/${bookingId}`);
  }

  /* ────────── 예약 사이트 이탈과 자가 신고 ────────── */

  const stayUrl = (suffix) =>
    `/api/v1/trips/${state.tripId}/accommodations/${state.bookingId}${suffix || ""}`;

  const nightsLabelOf = (offer) => offer.nightsLabel || "";

  /** 실습 요금인지. 목록과 이동 안내가 같은 기준으로 판단해야 한 쪽만 빠지지 않는다. */
  const isPracticePrice = (offer) =>
    offer.priceSource === "SANDBOX" || offer.priceSource === "MOCK";

  function fillModal(prefix, offer) {
    text(prefix + "tp", offer.typeLabel || "숙소");
    text(prefix + "nm", offer.name);
    text(prefix + "dt", `${$("h-checkin").value} → ${$("h-checkout").value}`);
    text(prefix + "pr", hasPrice(offer) ? money(offer.totalPrice, offer.currency)
      : (offer.priceSourceLabel || "요금 미제공"));
    text(prefix + "px", `${nightsLabelOf(offer)} 총액`);

    /*
     * 나가는 순간이 제일 위험하다. 목록에는 실습 요금이라고 적혀 있어도, 마지막으로 보는
     * 이 화면에서 빠지면 그 사실을 모르고 예약 사이트로 넘어간다. 거기 가격은 다르다.
     */
    const note = $(prefix + "pn");
    if (note) note.hidden = !(hasPrice(offer) && isPracticePrice(offer));
  }

  /** 이동 안내 모달. 여기서 바로 내보내지 않는 이유는 새 탭이 열린다는 것을 먼저 알리기 위해서다. */
  function openBooking(offerId) {
    const offer = state.offers.find((item) => item.offerId === offerId);
    if (!offer || !offer.deeplinkUrl) return;
    fillModal("h1", offer);
    show("hv1");
  }

  /**
   * 복귀 감지. 정확히 못 한다는 전제로 만든다.
   * visibilitychange는 알림 확인·앱 전환·화면 잠금에도 발생하고 모바일에서 특히 부정확하다.
   * 놓쳤을 때의 복구 경로는 다음 방문의 재질문 배너다.
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
    hide("hv1");
    const offer = selectedOffer();
    if (!offer) return;

    /* 기록에 실패해도 이동은 막지 않는다. 사용자의 목적은 예약이다. */
    if (canPersist() && state.bookingId) {
      try {
        const payload = await request("POST", stayUrl("/outbound-click"),
          { deeplinkUrl: offer.deeplinkUrl });
        state.clickId = payload?.clickId ?? null;
      } catch (error) { /* 이력이 없으면 재질문을 못 할 뿐이다 */ }
    }

    window.open(offer.deeplinkUrl, "_blank", "noopener,noreferrer");

    fillModal("h2", offer);
    detectReturn(() => show("hv2"));
  }

  /** "네, 예약했어요" — 자가 신고. 결제 확인이 아니다. */
  async function reportBooked() {
    hide("hv2");
    const offer = selectedOffer();

    if (canPersist() && state.bookingId) {
      try {
        await applyBookings(await request("PATCH", stayUrl("/report"),
          { userReportedBooked: true, clickId: state.clickId }));
      } catch (error) { return; }
    } else {
      state.status = "USER_REPORTED";
    }
    state.clickId = null;
    render();

    if (offer) fillModal("h3", offer);
    $("hotelRefInput").value = state.bookingRef || "";
    show("hv3");
  }

  /** "나중에 확인할게요" — 담아둔 것은 유지하고 예약 표시만 하지 않는다. */
  async function reportLater() {
    hide("hv2");
    if (canPersist() && state.bookingId) {
      try {
        await applyBookings(await request("PATCH", stayUrl("/report"),
          { userReportedBooked: false, clickId: state.clickId }));
      } catch (error) { /* 표시하지 않는 것이 기본값이라 실패해도 상태는 같다 */ }
    }
    state.clickId = null;
    render();
  }

  /** "아니요, 다시 볼게요" — 선택이 완전히 해제된다. 이탈 이력도 함께 사라진다. */
  async function reportNo() {
    hide("hv2");
    if (canPersist() && state.bookingId) {
      try { await removeStay(state.bookingId); } catch (error) { /* 로컬 상태는 되돌린다 */ }
    }
    clearSelection();
    render();
    window.dispatchEvent(new CustomEvent("allmytrips:accommodation-selected", { detail: { offer: null } }));
  }

  /** 예약번호가 들어오면 확정으로 승격한다. 비어 있으면 호출하지 않는다. */
  async function saveRef() {
    const value = $("hotelRefInput").value.trim().toUpperCase();
    if (!value) return;
    state.bookingRef = value;
    state.status = "CONFIRMED";
    if (canPersist() && state.bookingId) {
      try { await applyBookings(await request("PATCH", stayUrl("/booking-ref"), { bookingRef: value })); }
      catch (error) { /* 로컬 표시는 유지하고 다음 조회에서 정정된다 */ }
    }
    render();
  }

  async function saveRefAndClose() {
    await saveRef();
    hide("hv3");
  }

  function clearSelection() {
    state.selectedId = null;
    state.bookingId = null;
    state.status = null;
    state.bookingRef = "";
    state.clickId = null;
  }

  /** 서버 응답 하나로 화면 상태를 맞춘다. 화면이 따로 계산하면 두 곳이 갈린다. */
  function applyBookings(payload) {
    const stay = (payload?.stays || [])[0];
    if (!stay) {
      clearSelection();
      return payload;
    }
    state.bookingId = stay.accommodationBookingId;
    state.selectedId = stay.offerId;
    state.status = stay.status;
    state.bookingRef = stay.bookingRef || "";
    return payload;
  }

  /**
   * 나갔는데 답을 못 받은 건을 다시 물어본다.
   *
   * 검색 결과가 바뀌어 카드가 없어도 물어볼 수 있어야 하므로 서버가 내려준 숙소명을 쓴다.
   */
  function showRecall(click) {
    text("hotelRecallText", `${click.name || "이 숙소"}, 예약하셨나요?`);
    $("hotelRecall").hidden = false;
    $("hotelRecallYes").onclick = async () => {
      state.clickId = click.clickId;
      $("hotelRecall").hidden = true;
      await reportBooked();
    };
    $("hotelRecallNo").onclick = async () => {
      state.clickId = click.clickId;
      $("hotelRecall").hidden = true;
      await reportNo();
    };
  }

  /**
   * 저장해 둔 숙소를 화면에 되살린다.
   *
   * 검색 결과에 같은 offerId가 있으면 그 카드를 선택 상태로 표시한다. 없으면
   * (다른 날짜를 검색한 경우 등) 우측 예약 현황에만 반영한다. 담아둔 것을 지우지는 않는다.
   */
  async function restoreSaved() {
    if (!canPersist()) return;

    let payload;
    try { payload = await request("GET", `/api/v1/trips/${state.tripId}/accommodations`); }
    catch (error) { return; }

    const stay = (payload?.stays || [])[0];
    if (!stay) return;

    applyBookings(payload);
    render();

    /* 복귀 감지를 놓친 건은 다음 방문에 다시 물어본다. */
    const unresolved = (payload?.unresolvedClicks || [])[0];
    if (unresolved) showRecall(unresolved);

    window.dispatchEvent(new CustomEvent("allmytrips:accommodation-selected", {
      detail: {
        offer: {
          offerId: stay.offerId, provider: stay.provider, name: stay.name,
          type: stay.accommodationType, areaLabel: stay.areaLabel, address: stay.address,
          rating: stay.rating, nightlyPrice: stay.nightlyPrice, totalPrice: stay.totalPrice,
          currency: stay.currency, priceSource: stay.priceSource,
          nightsLabel: `${stay.nights}박`
        }
      }
    }));
  }

  async function request(method, url, body) {
    const options = { method, headers: { Accept: "application/json" } };
    if (body) {
      options.headers["Content-Type"] = "application/json";
      options.body = JSON.stringify(body);
    }
    const response = await fetch(url, options);
    const result = await response.json().catch(() => null);

    if (!response.ok || !result?.success) {
      const error = new Error(result?.message || "요청을 처리하지 못했습니다.");
      error.code = result?.code || "";
      throw error;
    }
    return result.data;
  }

  function bind() {
    $("hotelSearchForm").addEventListener("submit", (event) => {
      event.preventDefault();
      searchHotels();
    });

    document.querySelectorAll("[data-hotel-sort]").forEach((button) => {
      button.addEventListener("click", () => {
        document.querySelectorAll("[data-hotel-sort]").forEach((item) => item.classList.remove("on"));
        button.classList.add("on");
        state.sort = button.dataset.hotelSort;
        render();
      });
    });

    $("hotelList").addEventListener("click", (event) => {
      const pick = event.target.closest("[data-hotel-pick]");
      if (pick) {
        select(pick.dataset.hotelPick);
        return;
      }
      const book = event.target.closest("[data-hotel-book]");
      if (book) openBooking(book.dataset.hotelBook);
    });

    $("h1go").addEventListener("click", goOut);
    $("h1back").addEventListener("click", () => hide("hv1"));
    $("h2yes").addEventListener("click", reportBooked);
    $("h2no").addEventListener("click", reportNo);
    $("h2later").addEventListener("click", reportLater);
    $("h3save").addEventListener("click", saveRefAndClose);
    /* `나중에`로 닫아도 입력값은 저장한다. 적어놓고 닫았는데 사라지면 다시 찾아야 한다. */
    $("h3later").addEventListener("click", saveRefAndClose);

    $("hotelList").addEventListener("error", (event) => {
      if (event.target.tagName !== "IMG") return;
      const image = event.target.closest(".hotel-image");
      image.classList.add("empty");
      image.innerHTML = "<span aria-hidden=\"true\">▤</span>";
    }, true);

    window.addEventListener("allmytrips:booking-tab-changed", (event) => {
      if (event.detail?.tab !== "hotel") return;
      syncDefaultsFromFlight();
      /* 여행이 있으면 여행 기간이 항공 폼 값을 덮는다. init과 같은 순서다. */
      syncDefaultsFromTrip().finally(() => {
        if (!state.searched) searchHotels();
      });
    });
  }

  function init() {
    syncDefaultsFromFlight();
    bind();
    /*
     * 담아둔 숙소를 먼저 되살리고 검색한다. 순서를 바꾸면 검색 결과가 렌더된 뒤에
     * 선택 표시가 뒤늦게 붙어 화면이 한 번 깜빡인다.
     * restoreSaved는 실패해도 조용히 넘어간다 — 비교 화면 자체는 살아 있어야 한다.
     */
    syncDefaultsFromTrip()
      .then(restoreSaved)
      .finally(() => {
        if (!$("panel-hotel").hidden) searchHotels();
      });
  }

  document.addEventListener("DOMContentLoaded", init);

  window.__accommodationBooking = {
    state, searchHotels, select, sortedOffers, selectedOffer,
    openBooking, goOut, reportBooked, reportLater, reportNo, saveRefAndClose, restoreSaved,
    attachToTrip
  };
})();
