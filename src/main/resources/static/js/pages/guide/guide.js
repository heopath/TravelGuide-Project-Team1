/* 여행 가이드 장소 검색 */
document.addEventListener("DOMContentLoaded", function () {
  const form = document.querySelector("[data-place-search]");
  const keywordInput = document.querySelector("#guide-keyword");
  const categoryButtons = Array.from(document.querySelectorAll("[data-category]"));
  const results = document.querySelector("[data-place-results]");
  const state = document.querySelector("[data-place-state]");
  const resultCount = document.querySelector("[data-result-count]");
  const resultTitle = document.querySelector("[data-result-title]");
  const selectedCategories = new Set();
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

  function createPlaceCard(place) {
    const card = document.createElement("button");
    const favoriteMarker = document.createElement("span");
    const category = document.createElement("span");
    const name = document.createElement("strong");
    const location = document.createElement("span");
    const rating = document.createElement("small");

    card.type = "button";
    card.className = "place-card category-" + String(place.category || "default").toLowerCase();
    card.classList.toggle("favorite", Boolean(place.favorite));
    card.dataset.route = "/guide/places/" + place.placeId;
    card.setAttribute("aria-label", (place.favorite ? "찜한 장소, " : "") + place.name + " 상세 보기");

    if (place.favorite) {
      favoriteMarker.className = "place-favorite-marker";
      favoriteMarker.textContent = "♥ 찜한 장소";
      card.appendChild(favoriteMarker);
    }

    category.className = "place-category";
    category.textContent = categoryLabels[place.category] || place.category || "장소";
    name.textContent = place.name;
    location.className = "place-location";
    location.textContent = [place.region, place.city].filter(Boolean).join(" · ") || "지역 정보 없음";
    rating.className = "place-rating";
    rating.textContent = place.averageRating == null
      ? "평점 정보 없음"
      : "★ " + Number(place.averageRating).toFixed(1);

    card.append(category, name, location, rating);
    return card;
  }

  function renderPlaces(places) {
    results.replaceChildren();
    const hasCondition = keywordInput.value.trim() || selectedCategories.size > 0;
    resultTitle.textContent = hasCondition ? "검색 결과" : "추천 장소";
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
      const categories = Array.from(selectedCategories);
      const targets = categories.length > 0 ? categories : [""];
      const responses = await Promise.all(targets.map(async function (category) {
        const params = new URLSearchParams({
          page: "0",
          size: categories.length > 0 ? "100" : "20",
        });
        if (keyword) params.set("keyword", keyword);
        if (category) params.set("category", category);
        const response = await fetch("/api/v1/places?" + params.toString(), {
          headers: { Accept: "application/json" },
          credentials: "same-origin",
          allMyTripsLoading: false,
        });
        if (!response.ok) throw new Error("장소 검색 API 요청에 실패했습니다.");
        const payload = await response.json();
        return Array.isArray(payload.data) ? payload.data : [];
      }));
      const placesById = new Map();
      responses.flat().forEach(function (place) {
        placesById.set(place.placeId, place);
      });
      const places = Array.from(placesById.values()).sort(function (left, right) {
        const favoriteDifference = Number(Boolean(right.favorite)) - Number(Boolean(left.favorite));
        const ratingDifference = Number(right.averageRating || 0) - Number(left.averageRating || 0);
        return favoriteDifference || ratingDifference || right.placeId - left.placeId;
      });
      if (currentRequest === requestSequence) renderPlaces(places);
    } catch (error) {
      if (currentRequest === requestSequence) {
        showState("장소를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.", true);
      }
    }
  }

  form.addEventListener("submit", function (event) {
    event.preventDefault();
    loadPlaces();
  });

  categoryButtons.forEach(function (button) {
    button.addEventListener("click", function () {
      const category = button.dataset.category;
      if (!category) {
        selectedCategories.clear();
      } else if (selectedCategories.has(category)) {
        selectedCategories.delete(category);
      } else {
        selectedCategories.add(category);
      }
      categoryButtons.forEach(function (item) {
        const itemCategory = item.dataset.category;
        const selected = itemCategory
          ? selectedCategories.has(itemCategory)
          : selectedCategories.size === 0;
        item.classList.toggle("selected", selected);
        item.setAttribute("aria-pressed", String(selected));
      });
      loadPlaces();
    });
  });

  document.body.dataset.pageReady = "true";
  loadPlaces();
});
