/* 장소 상세 API 연결 */
document.addEventListener("DOMContentLoaded", function () {
  const state = document.querySelector("[data-detail-state]");
  const content = document.querySelector("[data-detail-content]");
  const favoriteButton = document.querySelector("[data-favorite-toggle]");
  const addToTripButton = document.querySelector("[data-add-to-trip]");
  const modalRoot = document.querySelector("#modal-root");
  let currentPlaceId = null;
  let currentPlaceName = null;
  let favorite = false;
  let favoriteAuthenticated = true;
  const categoryLabels = {
    ATTRACTION: "관광지",
    RESTAURANT: "맛집",
    CAFE: "카페",
    ACCOMMODATION: "숙소",
    FESTIVAL: "축제",
    ACTIVITY: "체험",
    TRANSPORT: "교통",
  };

  function setText(selector, value, fallback) {
    const element = document.querySelector(selector);
    if (element) element.textContent = value || fallback || "";
  }

  function renderOptionalRow(rowSelector, valueSelector, value) {
    const row = document.querySelector(rowSelector);
    if (!value) {
      row.hidden = true;
      return;
    }
    row.hidden = false;
    setText(valueSelector, value);
  }

  function renderFavoriteButton() {
    favoriteButton.classList.toggle("selected", favorite);
    favoriteButton.setAttribute("aria-pressed", String(favorite));
    favoriteButton.querySelector("span").textContent = favorite ? "♥" : "♡";
    favoriteButton.querySelector("strong").textContent =
      favorite ? "찜 해제" : "찜하기";
  }

  async function loadFavoriteState(placeId) {
    favoriteButton.disabled = true;
    try {
      const response = await fetch("/api/v1/favorites/" + placeId, {
        headers: { Accept: "application/json" },
        credentials: "same-origin",
        allMyTripsLoading: false,
      });
      if (response.status === 401) {
        favoriteAuthenticated = false;
        favorite = false;
        renderFavoriteButton();
        favoriteButton.disabled = false;
        favoriteButton.title = "로그인 후 찜하기를 사용할 수 있습니다.";
        return;
      }
      const payload = await response.json().catch(function () { return null; });
      if (!response.ok || !payload?.success) throw new Error("찜 상태 조회에 실패했습니다.");
      favoriteAuthenticated = true;
      favorite = Boolean(payload.data?.favorite);
      renderFavoriteButton();
      favoriteButton.disabled = false;
      favoriteButton.removeAttribute("title");
    } catch (error) {
      favoriteButton.disabled = true;
      favoriteButton.title = "찜 상태를 불러오지 못했습니다.";
    }
  }

  async function toggleFavorite() {
    if (!currentPlaceId || favoriteButton.disabled) return;
    if (!favoriteAuthenticated) {
      window.AllMyTripsModal.showToast("로그인이 필요한 기능입니다.");
      return;
    }
    favoriteButton.disabled = true;
    const method = favorite ? "DELETE" : "POST";
    const url = "/api/v1/favorites?placeId=" + currentPlaceId;

    try {
      const response = await fetch(url, {
        method: method,
        headers: { Accept: "application/json" },
        credentials: "same-origin",
        allMyTripsLoading: false,
      });
      if (response.status === 401) {
        favoriteAuthenticated = false;
        favoriteButton.title = "로그인 후 찜하기를 사용할 수 있습니다.";
        window.AllMyTripsModal.showToast("로그인이 필요한 기능입니다.");
        return;
      }
      if (!response.ok) throw new Error("찜 상태 변경에 실패했습니다.");
      favorite = !favorite;
      renderFavoriteButton();
      window.AllMyTripsModal.showToast(
        favorite ? "찜한 장소에 추가했습니다." : "찜을 해제했습니다."
      );
    } catch (error) {
      window.AllMyTripsModal.showToast("찜 상태를 변경하지 못했습니다.");
    } finally {
      favoriteButton.disabled = false;
    }
  }

  function closeTripModal() {
    modalRoot.replaceChildren();
  }

  function handleTripUnauthorized(response) {
    if (response.status !== 401) return false;
    closeTripModal();
    window.AllMyTripsModal.showToast("로그인 후 일정에 장소를 추가할 수 있습니다.");
    return true;
  }

  function renderModalMessage(container, message, isError) {
    container.replaceChildren();
    const text = document.createElement("p");
    text.className = "trip-picker-message" + (isError ? " error" : "");
    text.textContent = message;
    container.appendChild(text);
  }

  async function addPlaceToDay(tripDayId, dayLabel, button) {
    button.disabled = true;
    try {
      const itemResponse = await fetch("/api/v1/trip-days/" + tripDayId + "/items", {
        headers: { Accept: "application/json" },
        credentials: "same-origin",
        allMyTripsLoading: false,
      });
      if (handleTripUnauthorized(itemResponse)) return;
      const itemPayload = await itemResponse.json().catch(function () { return null; });
      if (!itemResponse.ok || !itemPayload?.success) throw new Error("일정 항목 조회에 실패했습니다.");
      const items = itemPayload.data;
      const duplicate = items.some(function (item) {
        return item.placeId === currentPlaceId;
      });
      if (duplicate) {
        closeTripModal();
        window.AllMyTripsModal.showToast(dayLabel + "에 이미 추가된 장소입니다.");
        return;
      }
      const nextOrder = items.reduce(function (maximum, item) {
        return Math.max(maximum, item.sortOrder || 0);
      }, 0) + 1;
      const saveResponse = await fetch("/api/v1/trip-days/" + tripDayId + "/items", {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        credentials: "same-origin",
        body: JSON.stringify({
          placeId: currentPlaceId,
          itemType: "PLACE",
          title: currentPlaceName,
          sortOrder: nextOrder,
          currencyCode: "KRW",
          source: "MANUAL",
        }),
        allMyTripsLoading: false,
      });
      if (handleTripUnauthorized(saveResponse)) return;
      if (!saveResponse.ok) throw new Error("일정 저장에 실패했습니다.");
      closeTripModal();
      window.AllMyTripsModal.showToast(dayLabel + " 일정에 추가했습니다.");
    } catch (error) {
      button.disabled = false;
      window.AllMyTripsModal.showToast("일정에 장소를 추가하지 못했습니다.");
    }
  }

  async function showTripDays(trip, container) {
    renderModalMessage(container, "여행 일정을 불러오는 중입니다.");
    try {
      const response = await fetch("/api/v1/trips/" + trip.tripId + "/days", {
        headers: { Accept: "application/json" },
        credentials: "same-origin",
        allMyTripsLoading: false,
      });
      if (handleTripUnauthorized(response)) return;
      const payload = await response.json().catch(function () { return null; });
      if (!response.ok || !payload?.success) throw new Error("여행 일자 조회에 실패했습니다.");
      const days = payload.data;
      container.replaceChildren();
      if (days.length === 0) {
        renderModalMessage(container, "이 여행에는 아직 일정 날짜가 없습니다.");
        return;
      }
      days.forEach(function (day) {
        const button = document.createElement("button");
        const title = document.createElement("strong");
        const date = document.createElement("span");
        const dayLabel = "DAY " + day.dayNumber;
        button.type = "button";
        button.className = "trip-day-option";
        title.textContent = dayLabel + (day.title ? " · " + day.title : "");
        date.textContent = day.tripDate;
        button.append(title, date);
        button.addEventListener("click", function () {
          addPlaceToDay(day.tripDayId, dayLabel, button);
        });
        container.appendChild(button);
      });
    } catch (error) {
      renderModalMessage(container, "여행 일정을 불러오지 못했습니다.", true);
    }
  }

  async function openTripModal() {
    modalRoot.innerHTML = `
      <div class="modal-backdrop">
        <section class="modal-card trip-picker-modal" role="dialog" aria-modal="true"
                 aria-labelledby="trip-picker-title">
          <button class="modal-close" type="button" data-trip-close aria-label="닫기">×</button>
          <span class="trip-picker-kicker">ADD TO TRIP</span>
          <h2 id="trip-picker-title">어느 일정에 추가할까요?</h2>
          <p class="trip-picker-place"></p>
          <div class="trip-picker-list" data-trip-picker-list></div>
          <div class="trip-day-list" data-trip-day-list hidden></div>
        </section>
      </div>`;
    modalRoot.querySelector(".trip-picker-place").textContent = currentPlaceName;
    modalRoot.querySelector("[data-trip-close]").addEventListener("click", closeTripModal);
    const tripList = modalRoot.querySelector("[data-trip-picker-list]");
    const dayList = modalRoot.querySelector("[data-trip-day-list]");
    renderModalMessage(tripList, "내 여행을 불러오는 중입니다.");

    try {
      const response = await fetch("/api/v1/trips", {
        headers: { Accept: "application/json" },
        credentials: "same-origin",
        allMyTripsLoading: false,
      });
      if (handleTripUnauthorized(response)) return;
      const payload = await response.json().catch(function () { return null; });
      if (!response.ok || !payload?.success) throw new Error("여행 목록 조회에 실패했습니다.");
      const trips = payload.data;
      tripList.replaceChildren();
      if (trips.length === 0) {
        renderModalMessage(tripList, "먼저 여행 계획을 만들어주세요.");
        return;
      }
      trips.forEach(function (trip) {
        const button = document.createElement("button");
        const title = document.createElement("strong");
        const period = document.createElement("span");
        button.type = "button";
        button.className = "trip-option";
        title.textContent = trip.title;
        period.textContent = trip.startDate + " ~ " + trip.endDate;
        button.append(title, period);
        button.addEventListener("click", function () {
          tripList.querySelectorAll("button").forEach(function (item) {
            item.classList.toggle("selected", item === button);
          });
          dayList.hidden = false;
          showTripDays(trip, dayList);
        });
        tripList.appendChild(button);
      });
    } catch (error) {
      renderModalMessage(tripList, "내 여행을 불러오지 못했습니다.", true);
    }
  }

  function renderDetail(detail) {
    const place = detail.place;
    currentPlaceId = place.placeId;
    currentPlaceName = place.name;
    const category = categoryLabels[place.category] || place.category || "장소";
    const location = [place.region, place.city].filter(Boolean).join(" · ");
    const primaryImage = detail.images.find(function (image) {
      return image.primaryImage;
    }) || detail.images[0];

    document.title = place.name + " — All My Trips";
    setText("[data-place-name]", place.name);
    setText("[data-place-summary]", place.description, location + "의 " + category);
    setText("[data-place-location]", location, "대한민국");
    setText("[data-place-category]", category);
    setText("[data-place-rating]",
      place.averageRating == null ? "정보 없음" : Number(place.averageRating).toFixed(1) + " / 5");
    setText("[data-place-region]", place.region || place.city, "정보 없음");
    setText("[data-place-category-fact]", category);
    setText("[data-place-description]", place.description, "등록된 장소 소개가 없습니다.");
    renderOptionalRow("[data-address-row]", "[data-place-address]", place.address);
    renderOptionalRow("[data-phone-row]", "[data-place-phone]", place.phone);

    const image = document.querySelector("[data-place-image]");
    if (primaryImage && primaryImage.imageUrl) {
      image.src = primaryImage.imageUrl;
      image.alt = primaryImage.altText || place.name;
    } else {
      image.hidden = true;
      image.parentElement.classList.add("without-image");
    }

    const styles = document.querySelector("[data-place-styles]");
    styles.replaceChildren();
    detail.styles.forEach(function (style) {
      const chip = document.createElement("span");
      chip.textContent = style.name;
      styles.appendChild(chip);
    });
    styles.hidden = detail.styles.length === 0;

    const website = document.querySelector("[data-place-website]");
    if (place.websiteUrl) {
      website.href = place.websiteUrl;
      website.hidden = false;
    } else {
      website.hidden = true;
    }

    state.hidden = true;
    content.hidden = false;
    loadFavoriteState(place.placeId);
  }

  async function loadDetail() {
    const match = window.location.pathname.match(/\/guide\/places\/(\d+)$/);
    if (!match) {
      state.textContent = "잘못된 장소 주소입니다. 여행 가이드에서 장소를 다시 선택해주세요.";
      state.classList.add("error");
      return;
    }

    try {
      const response = await fetch("/api/places/" + match[1], {
        headers: { Accept: "application/json" },
        allMyTripsLoading: false,
      });
      if (!response.ok) throw new Error("장소 상세 요청에 실패했습니다.");
      renderDetail(await response.json());
    } catch (error) {
      state.textContent = "장소 정보를 불러오지 못했습니다. 여행 가이드에서 다시 시도해주세요.";
      state.classList.add("error");
    }
  }

  document.body.dataset.pageReady = "true";
  favoriteButton.addEventListener("click", toggleFavorite);
  addToTripButton.addEventListener("click", openTripModal);
  loadDetail();
});
