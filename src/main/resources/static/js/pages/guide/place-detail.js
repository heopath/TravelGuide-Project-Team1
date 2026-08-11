/* 추천 장소 상세 */
document.addEventListener("DOMContentLoaded", function () {
  const state = document.querySelector("[data-detail-state]");
  const content = document.querySelector("[data-detail-content]");
  const favoriteButton = document.querySelector("[data-favorite-toggle]");
  const favoriteStatus = document.querySelector("[data-favorite-status]");
  const mapContainer = document.querySelector("[data-place-map]");
  let currentPlace = null;
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

  function toast(message) {
    if (window.AllMyTripsModal && typeof window.AllMyTripsModal.showToast === "function") {
      window.AllMyTripsModal.showToast(message);
      return;
    }
    window.alert(message);
  }

  function setText(selector, value, fallback) {
    const element = document.querySelector(selector);
    if (element) element.textContent = value || fallback || "";
  }

  function renderFavoriteButton() {
    favoriteButton.classList.toggle("selected", favorite);
    favoriteButton.setAttribute("aria-pressed", String(favorite));
    favoriteButton.querySelector("strong").textContent = favorite ? "찜 해제" : "찜하기";

    if (!favoriteAuthenticated) {
      favoriteStatus.textContent = "로그인하면 장소를 찜하고 여행 일정에 불러올 수 있습니다.";
    } else if (favorite) {
      favoriteStatus.textContent = "찜 완료 · 여행 일정을 만들 때 이 장소를 불러올 수 있습니다.";
    } else {
      favoriteStatus.textContent = "찜한 장소는 여행 일정을 만들 때 불러올 수 있습니다.";
    }
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
        favoriteButton.title = "로그인 후 찜하기를 사용할 수 있습니다.";
        return;
      }
      const payload = await response.json().catch(function () { return null; });
      if (!response.ok || !payload || !payload.success) {
        throw new Error("찜 상태 조회에 실패했습니다.");
      }
      favoriteAuthenticated = true;
      favorite = Boolean(payload.data && payload.data.favorite);
      favoriteButton.removeAttribute("title");
    } catch (error) {
      favoriteButton.title = "찜 상태를 불러오지 못했습니다.";
    } finally {
      renderFavoriteButton();
      favoriteButton.disabled = false;
    }
  }

  async function toggleFavorite() {
    if (!currentPlace || favoriteButton.disabled) return;
    if (!favoriteAuthenticated) {
      toast("로그인이 필요한 기능입니다.");
      return;
    }

    favoriteButton.disabled = true;
    try {
      const response = await fetch("/api/v1/favorites?placeId=" + currentPlace.placeId, {
        method: favorite ? "DELETE" : "POST",
        headers: { Accept: "application/json" },
        credentials: "same-origin",
        allMyTripsLoading: false,
      });
      if (response.status === 401) {
        favoriteAuthenticated = false;
        toast("로그인이 필요한 기능입니다.");
        return;
      }
      if (!response.ok) throw new Error("찜 상태 변경에 실패했습니다.");
      favorite = !favorite;
      toast(favorite ? "찜한 장소에 추가했습니다." : "찜을 해제했습니다.");
    } catch (error) {
      toast("찜 상태를 변경하지 못했습니다.");
    } finally {
      renderFavoriteButton();
      favoriteButton.disabled = false;
    }
  }

  function mapLink(place, type) {
    const prefix = type === "route" ? "/link/to/" : "/link/map/";
    const latitude = Number(place.latitude);
    const longitude = Number(place.longitude);
    if (Number.isFinite(latitude) && Number.isFinite(longitude)) {
      return "https://map.kakao.com" + prefix
        + encodeURIComponent(place.name) + "," + latitude + "," + longitude;
    }
    if (place.externalProvider === "KAKAO" && place.externalPlaceId) {
      return "https://map.kakao.com" + prefix + encodeURIComponent(place.externalPlaceId);
    }
    return "";
  }

  function renderMap(place) {
    const latitude = Number(place.latitude);
    const longitude = Number(place.longitude);
    const mapButton = document.querySelector("[data-kakao-map]");
    const routeButton = document.querySelector("[data-kakao-route]");
    const mapUrl = mapLink(place, "map");
    const routeUrl = mapLink(place, "route");

    mapButton.href = mapUrl;
    mapButton.hidden = !mapUrl;
    routeButton.href = routeUrl;
    routeButton.hidden = !routeUrl;

    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
      setText("[data-map-message]", "지도 좌표 정보가 없습니다.");
      return;
    }
    if (!window.kakao || !window.kakao.maps || typeof window.kakao.maps.load !== "function") {
      setText("[data-map-message]", "지도를 표시할 수 없습니다. 카카오맵 버튼을 이용해 주세요.");
      return;
    }

    window.kakao.maps.load(function () {
      const position = new window.kakao.maps.LatLng(latitude, longitude);
      const map = new window.kakao.maps.Map(mapContainer, {
        center: position,
        level: 4,
      });
      new window.kakao.maps.Marker({ map: map, position: position });
      map.addControl(
        new window.kakao.maps.ZoomControl(),
        window.kakao.maps.ControlPosition.RIGHT
      );
      mapContainer.classList.add("ready");
    });
  }

  async function copyText(value, successMessage) {
    if (!value) {
      toast("복사할 장소 주소가 없습니다.");
      return;
    }
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(value);
      } else {
        const textarea = document.createElement("textarea");
        textarea.value = value;
        textarea.style.position = "fixed";
        textarea.style.opacity = "0";
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand("copy");
        textarea.remove();
      }
      toast(successMessage);
    } catch (error) {
      toast("복사하지 못했습니다.");
    }
  }

  async function sharePlace() {
    if (!currentPlace) return;
    const shareData = {
      title: currentPlace.name + " — All My Trips",
      text: currentPlace.description || currentPlace.address || currentPlace.name,
      url: window.location.href,
    };
    try {
      if (navigator.share) {
        await navigator.share(shareData);
      } else {
        await copyText(window.location.href, "장소 링크를 복사했습니다.");
      }
    } catch (error) {
      if (error && error.name !== "AbortError") toast("공유하지 못했습니다.");
    }
  }

  function renderDetail(detail) {
    const place = detail.place;
    currentPlace = place;
    const category = categoryLabels[place.category] || place.category || "장소";
    const location = [place.region, place.city].filter(Boolean).join(" · ");
    const images = Array.isArray(detail.images) ? detail.images : [];
    const primaryImage = images.find(function (image) {
      return image.primaryImage;
    }) || images[0];

    document.title = place.name + " — All My Trips";
    setText("[data-place-name]", place.name, "장소 상세");
    setText("[data-place-category]", category);
    setText(
      "[data-place-rating]",
      place.averageRating == null ? "평점 정보 없음" : Number(place.averageRating).toFixed(1)
    );
    setText("[data-place-location]", location, "대한민국");
    setText("[data-place-description]", place.description, "등록된 장소 소개가 없습니다.");
    setText("[data-place-address]", place.address, "주소 정보가 없습니다.");

    const image = document.querySelector("[data-place-image]");
    const imageWrap = document.querySelector("[data-place-image-wrap]");
    if (primaryImage && primaryImage.imageUrl) {
      image.src = primaryImage.imageUrl;
      image.alt = primaryImage.altText || place.name + " 대표 이미지";
    } else {
      image.hidden = true;
      imageWrap.classList.add("without-image");
    }

    state.hidden = true;
    content.hidden = false;
    renderMap(place);
    loadFavoriteState(place.placeId);
  }

  async function loadDetail() {
    const match = window.location.pathname.match(/\/guide\/places\/(\d+)$/);
    if (!match) {
      state.textContent = "잘못된 장소 주소입니다. 추천 장소에서 장소를 다시 선택해 주세요.";
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
      state.textContent = "장소 정보를 불러오지 못했습니다. 추천 장소에서 다시 시도해 주세요.";
      state.classList.add("error");
    }
  }

  document.querySelectorAll("[data-address-copy]").forEach(function (button) {
    button.addEventListener("click", function () {
      copyText(currentPlace && currentPlace.address, "장소 주소를 복사했습니다.");
    });
  });
  document.querySelector("[data-share-place]").addEventListener("click", sharePlace);
  favoriteButton.addEventListener("click", toggleFavorite);
  document.body.dataset.pageReady = "true";
  loadDetail();
});
