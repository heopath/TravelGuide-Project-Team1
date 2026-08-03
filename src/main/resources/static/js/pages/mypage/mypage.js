document.addEventListener("DOMContentLoaded", function () {
  const favoriteCount = document.querySelector("[data-favorite-count]");
  const favoriteList = document.querySelector("[data-favorite-list]");
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
    favoriteList.replaceChildren();
    const state = document.createElement("p");
    state.className = "favorite-place-state" + (isError ? " error" : "");
    state.textContent = message;
    favoriteList.appendChild(state);
  }

  function createFavoriteCard(favorite) {
    const article = document.createElement("article");
    const button = document.createElement("button");
    const imageBox = document.createElement("div");
    const copy = document.createElement("div");
    const name = document.createElement("strong");
    const meta = document.createElement("span");
    const memo = document.createElement("small");

    article.className = "favorite-place-card";
    button.type = "button";
    button.dataset.route = "/guide/places/" + favorite.placeId;
    button.setAttribute("aria-label", favorite.placeName + " 상세 보기");
    imageBox.className = "favorite-place-image";
    copy.className = "favorite-place-copy";

    if (favorite.primaryImageUrl) {
      const image = document.createElement("img");
      image.src = favorite.primaryImageUrl;
      image.alt = "";
      image.loading = "lazy";
      imageBox.appendChild(image);
    } else {
      const placeholder = document.createElement("span");
      placeholder.textContent = "♡";
      imageBox.appendChild(placeholder);
    }

    name.textContent = favorite.placeName;
    meta.textContent = [favorite.region, categoryLabels[favorite.category] || favorite.category]
      .filter(Boolean)
      .join(" · ");
    copy.append(name, meta);

    if (favorite.memo) {
      memo.textContent = favorite.memo;
      copy.appendChild(memo);
    }

    button.append(imageBox, copy);
    article.appendChild(button);
    return article;
  }

  async function loadFavorites() {
    try {
      const responses = await Promise.all([
        fetch("/api/favorites?page=0&size=100", {
          headers: { Accept: "application/json" },
          credentials: "same-origin",
          allMyTripsLoading: false,
        }),
        fetch("/api/favorites/count", {
          headers: { Accept: "application/json" },
          credentials: "same-origin",
          allMyTripsLoading: false,
        }),
      ]);

      if (responses.some(function (response) { return response.status === 401; })) {
        favoriteCount.textContent = "—";
        showState("로그인 후 찜한 여행지를 확인할 수 있습니다.", false);
        return;
      }
      if (responses.some(function (response) { return !response.ok; })) {
        throw new Error("즐겨찾기 요청에 실패했습니다.");
      }

      const results = await Promise.all(responses.map(function (response) {
        return response.json();
      }));
      const favorites = results[0];
      const totalCount = results[1];
      favoriteCount.textContent = totalCount + "곳";
      favoriteList.replaceChildren();

      if (favorites.length === 0) {
        showState("아직 찜한 여행지가 없습니다. 여행 가이드에서 관심 장소를 추가해보세요.", false);
        return;
      }

      favorites.forEach(function (favorite) {
        favoriteList.appendChild(createFavoriteCard(favorite));
      });
    } catch (error) {
      favoriteCount.textContent = "—";
      showState("찜한 여행지를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.", true);
    }
  }

  document.body.dataset.pageReady = "true";
  loadFavorites();
});
