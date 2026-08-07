/* 추천 장소 검색 */
document.addEventListener("DOMContentLoaded", function () {
  const form = document.querySelector("[data-place-search]");
  const keywordInput = document.querySelector("#guide-keyword");
  const categoryButtons = Array.from(document.querySelectorAll("[data-category]"));
  const showAllButton = document.querySelector("[data-show-all]");
  const results = document.querySelector("[data-place-results]");
  const state = document.querySelector("[data-place-state]");
  const resultCount = document.querySelector("[data-result-count]");
  const resultTitle = document.querySelector("[data-result-title]");
  const resultDescription = document.querySelector("[data-result-description]");
  let selectedCategory = "";
  let requestSequence = 0;

  const categoryLabels = {
    ATTRACTION: "관광지",
    RESTAURANT: "맛집",
    CAFE: "카페",
    ACCOMMODATION: "숙소",
    FESTIVAL: "축제",
    ACTIVITY: "체험",
    TRANSPORT: "교통",
  };
  function showState(message, isError) {
    state.textContent = message;
    state.classList.toggle("error", Boolean(isError));
    state.hidden = false;
    results.hidden = true;
    resultCount.textContent = "";
  }

  function sortPlaces(places) {
    return places.sort(function (left, right) {
      const favoriteDifference = Number(Boolean(right.favorite)) - Number(Boolean(left.favorite));
      const ratingDifference = Number(right.averageRating || 0) - Number(left.averageRating || 0);
      return favoriteDifference || ratingDifference || right.placeId - left.placeId;
    });
  }

  function showToast(message) {
    if (window.AllMyTripsModal?.showToast) {
      window.AllMyTripsModal.showToast(message);
    }
  }

  function renderCardFavorite(place, card, button) {
    const favorite = Boolean(place.favorite);
    card.classList.toggle("favorite", favorite);
    button.classList.toggle("selected", favorite);
    button.setAttribute("aria-pressed", String(favorite));
    button.setAttribute("aria-label", place.name + (favorite ? " 찜 취소" : " 찜하기"));
    button.title = favorite ? "찜 취소" : "찜하기";
    button.textContent = "";
  }

  async function toggleCardFavorite(place, card, button) {
    if (button.disabled) return;
    button.disabled = true;
    const adding = !place.favorite;

    try {
      const response = await fetch("/api/v1/favorites?placeId=" + place.placeId, {
        method: adding ? "POST" : "DELETE",
        headers: { Accept: "application/json" },
        credentials: "same-origin",
        allMyTripsLoading: false,
      });
      if (response.status === 401) {
        showToast("로그인 후 찜하기를 사용할 수 있습니다.");
        return;
      }
      if (!response.ok) throw new Error("찜 상태 변경에 실패했습니다.");
      place.favorite = adding;
      renderCardFavorite(place, card, button);
      showToast(adding ? "찜한 장소에 추가했습니다." : "찜을 해제했습니다.");
    } catch (error) {
      showToast("찜 상태를 변경하지 못했습니다.");
    } finally {
      button.disabled = false;
    }
  }

  function createPlaceCard(place) {
    const card = document.createElement("article");
    const routeButton = document.createElement("button");
    const favoriteButton = document.createElement("button");
    const visual = document.createElement("span");
    const category = document.createElement("span");
    const body = document.createElement("span");
    const meta = document.createElement("span");
    const name = document.createElement("strong");
    const location = document.createElement("span");
    const footer = document.createElement("span");
    const rating = document.createElement("span");
    const detailLink = document.createElement("span");

    card.className = "place-card category-" + String(place.category || "default").toLowerCase();
    routeButton.type = "button";
    routeButton.className = "place-card-link";
    routeButton.dataset.route = "/guide/places/" + place.placeId;
    routeButton.setAttribute("aria-label", place.name + " 상세 보기");

    favoriteButton.type = "button";
    favoriteButton.className = "place-card-favorite";
    favoriteButton.addEventListener("click", function () {
      toggleCardFavorite(place, card, favoriteButton);
    });
    renderCardFavorite(place, card, favoriteButton);

    visual.className = "place-card-visual";
    if (place.primaryImageUrl) {
      visual.classList.add("with-image");
      visual.style.backgroundImage =
        'linear-gradient(180deg, rgba(9, 17, 38, 0.02), rgba(9, 17, 38, 0.38)), url("' +
        String(place.primaryImageUrl).replace(/"/g, "%22") + '")';
    }
    category.className = "place-category";
    category.textContent = categoryLabels[place.category] || place.category || "장소";
    visual.appendChild(category);

    body.className = "place-card-body";
    meta.className = "place-card-meta";
    location.className = "place-location";
    location.textContent = [place.region, place.city].filter(Boolean).join(" · ") || "지역 정보 없음";
    meta.appendChild(location);
    name.textContent = place.name;

    footer.className = "place-card-footer";
    rating.className = "place-rating";
    rating.textContent = place.averageRating == null
      ? "평점 정보 없음"
      : "★ " + Number(place.averageRating).toFixed(1);
    detailLink.className = "place-detail-link";
    detailLink.textContent = "자세히 보기  →";
    footer.append(rating, detailLink);
    body.append(meta, name, footer);

    routeButton.append(visual, body);
    card.append(routeButton, favoriteButton);
    return card;
  }

  function renderPlaces(places) {
    results.replaceChildren();
    const keyword = keywordInput.value.trim();
    const categoryLabel = categoryLabels[selectedCategory];
    resultTitle.textContent = categoryLabel
      ? categoryLabel + " 추천 장소"
      : keyword ? "검색 결과" : "전체 추천 장소";
    resultDescription.textContent = categoryLabel
      ? categoryLabel + " 카테고리에 해당하는 장소를 모았어요."
      : keyword ? "검색어에 맞는 장소를 확인해 보세요." : "다양한 카테고리의 장소를 둘러보세요.";
    if (places.length === 0) {
      showState("조건에 맞는 장소가 없습니다. 다른 검색어를 입력해보세요.");
      return;
    }

    const fragment = document.createDocumentFragment();
    places.forEach(function (place) {
      fragment.appendChild(createPlaceCard(place));
    });
    results.appendChild(fragment);
    state.hidden = true;
    results.hidden = false;
    resultCount.textContent = places.length + "개";
  }

  async function loadPlaces() {
    const currentRequest = ++requestSequence;
    const keyword = keywordInput.value.trim();

    showState("장소를 불러오는 중입니다.");
    try {
      const params = new URLSearchParams({
        page: "0",
        size: selectedCategory ? "100" : "20",
      });
      if (keyword) params.set("keyword", keyword);
      if (selectedCategory) params.set("category", selectedCategory);
      const response = await fetch("/api/v1/places?" + params.toString(), {
        headers: { Accept: "application/json" },
        credentials: "same-origin",
        allMyTripsLoading: false,
      });
      if (!response.ok) throw new Error("장소 검색 API 요청에 실패했습니다.");
      const payload = await response.json();
      const places = sortPlaces(Array.isArray(payload.data) ? payload.data : []);
      if (currentRequest === requestSequence) renderPlaces(places);
    } catch (error) {
      if (currentRequest === requestSequence) {
        showState("장소를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.", true);
      }
    }
  }

  function selectCategory(category) {
    selectedCategory = category;
    categoryButtons.forEach(function (button) {
      const selected = button.dataset.category === selectedCategory;
      button.classList.toggle("selected", selected);
      button.setAttribute("aria-pressed", String(selected));
    });
    showAllButton.classList.toggle("selected", !selectedCategory);
    loadPlaces();
    document.querySelector(".place-results-panel").scrollIntoView({ behavior: "smooth", block: "start" });
  }

  form.addEventListener("submit", function (event) {
    event.preventDefault();
    loadPlaces();
  });

  categoryButtons.forEach(function (button) {
    button.addEventListener("click", function () {
      selectCategory(button.dataset.category);
    });
  });

  showAllButton.addEventListener("click", function () {
    selectCategory("");
  });

  document.body.dataset.pageReady = "true";
  loadPlaces();
});
