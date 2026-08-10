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

  const state = {
    offers: [],
    selectedId: null,
    /* 서버에 저장된 행의 id. 선택 해제할 때 이 값으로 지운다. */
    bookingId: null,
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
        <div class="hotel-price-sub">Sandbox Key가 있으면 실습 요금을 조회해요</div>`;
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
      </div>
    </article>`;
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
        : mockListing ? "Mock 개발용 샘플 가격" : "TourAPI 정보 · 가격 미제공";
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
      showError("숙소 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
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

    state.selectedId = deselected ? null : offerId;
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
      render();
      window.dispatchEvent(new CustomEvent("allmytrips:accommodation-selected", {
        detail: { offer: previousId ? { ...state.offers.find((item) => item.offerId === previousId) } : null }
      }));
      showError(error.message || "숙소를 담지 못했어요. 잠시 후 다시 시도해주세요.");
    }
  }

  async function saveStay(offer) {
    const payload = await request("POST", `/api/v1/trips/${state.tripId}/accommodations`, {
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
    return saved ? saved.accommodationBookingId : null;
  }

  async function removeStay(bookingId) {
    await request("DELETE", `/api/v1/trips/${state.tripId}/accommodations/${bookingId}`);
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

    state.bookingId = stay.accommodationBookingId;
    state.selectedId = stay.offerId;
    render();

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
      const button = event.target.closest("[data-hotel-pick]");
      if (button) select(button.dataset.hotelPick);
    });

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
    state, searchHotels, select, sortedOffers, selectedOffer
  };
})();
