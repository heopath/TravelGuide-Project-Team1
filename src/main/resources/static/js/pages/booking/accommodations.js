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
    sort: "recommended",
    searched: false,
    loading: false,
    meta: null
  };

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

  function sortedOffers() {
    const list = state.offers.slice();
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
        <button type="button" class="hotel-pick${selected ? " done" : ""}" data-hotel-pick="${esc(offer.offerId)}">
          ${selected ? "✓ 선택 완료" : "이 숙소 선택"}
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
    $("hotelPriceMode").textContent = state.meta?.priceProvider === "liteapi-sandbox"
      ? `LiteAPI Sandbox · 실습 요금 ${state.meta.matchedPriceCount}곳`
      : "TourAPI 정보 · 가격 미제공";
    $("hotelPriceMode").classList.toggle("sandbox", state.meta?.priceProvider === "liteapi-sandbox");
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
      if (state.selectedId && !nextOffers.some((offer) => offer.offerId === state.selectedId)) {
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

  function select(offerId) {
    const offer = state.offers.find((item) => item.offerId === offerId);
    if (!offer) return;
    state.selectedId = offerId;
    render();
    window.dispatchEvent(new CustomEvent("allmytrips:accommodation-selected", {
      detail: { offer: { ...offer } }
    }));
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
      if (!state.searched) searchHotels();
    });
  }

  function init() {
    syncDefaultsFromFlight();
    bind();
    if (!$("panel-hotel").hidden) searchHotels();
  }

  document.addEventListener("DOMContentLoaded", init);

  window.__accommodationBooking = {
    state, searchHotels, select, sortedOffers, selectedOffer
  };
})();
