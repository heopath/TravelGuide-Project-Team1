/* 장소 상세 API 연결 */
document.addEventListener("DOMContentLoaded", function () {
  const state = document.querySelector("[data-detail-state]");
  const content = document.querySelector("[data-detail-content]");
  const favoriteButton = document.querySelector("[data-favorite-toggle]");
  let currentPlaceId = null;
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

  function renderDetail(detail) {
    const place = detail.place;
    currentPlaceId = place.placeId;
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
      state.textContent = "잘못된 장소 주소입니다. 추천 장소에서 장소를 다시 선택해주세요.";
      state.classList.add("error");
      return;
    }

    try {
      const response = await fetch("/api/v1/places/" + match[1], {
        headers: { Accept: "application/json" },
        allMyTripsLoading: false,
      });
      if (!response.ok) throw new Error("장소 상세 요청에 실패했습니다.");
      const payload = await response.json();
      if (!payload.data) throw new Error("장소 상세 응답이 올바르지 않습니다.");
      renderDetail(payload.data);
    } catch (error) {
      state.textContent = "장소 정보를 불러오지 못했습니다. 추천 장소에서 다시 시도해주세요.";
      state.classList.add("error");
    }
  }

  document.body.dataset.pageReady = "true";
  favoriteButton.addEventListener("click", toggleFavorite);
  loadDetail();
});
