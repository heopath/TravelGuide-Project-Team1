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
        fetch("/api/v1/favorites?page=0&size=100", {
          headers: { Accept: "application/json" },
          credentials: "same-origin",
          allMyTripsLoading: false,
        }),
        fetch("/api/v1/favorites/count", {
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
      if (results.some(function (result) { return !result?.success; })) {
        throw new Error("찜한 여행지 응답 형식이 올바르지 않습니다.");
      }
      const favorites = results[0].data;
      const totalCount = results[1].data;
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

/* 마이 페이지 전용 JavaScript */
(function () {
  function showToast(message) {
    if (window.AllMyTripsModal?.showToast) {
      window.AllMyTripsModal.showToast(message);
      return;
    }
    alert(message);
  }

  async function request(url, options) {
    const response = await fetch(url, {
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
        ...(options?.body ? { "Content-Type": "application/json" } : {}),
        ...(options?.headers || {}),
      },
      ...options,
    });

    const result = await response.json().catch(function () {
      return null;
    });

    if (response.status === 401) {
      window.location.href = "/auth/login";
      throw new Error("로그인이 필요합니다.");
    }

    if (!response.ok || !result?.success) {
      throw new Error(result?.message || "요청을 처리하지 못했습니다.");
    }

    return result.data;
  }

  document.addEventListener("DOMContentLoaded", function () {
    const profileCard = document.querySelector("[data-profile-card]");
    const avatar = document.querySelector("[data-profile-avatar]");
    const nicknameText = document.querySelector("[data-profile-nickname]");
    const emailText = document.querySelector("[data-profile-email]");
    const editButton = document.querySelector("[data-profile-edit]");
    const editMenuButton = document.querySelector("[data-profile-edit-menu]");
    const editForm = document.querySelector("[data-profile-form]");
    const cancelButton = document.querySelector("[data-profile-cancel]");
    const nicknameInput = editForm?.elements.namedItem("nickname");
    const formError = document.querySelector("[data-profile-error]");
    const tripList = document.querySelector("[data-trip-list]");
    const tripCount = document.querySelector("[data-trip-count]");
    const tripMoreButton = document.querySelector("[data-trip-more]");

    let currentMember = null;

    function renderMember(member) {
      currentMember = member;
      const nickname = member.nickname || member.email || "사용자";
      const initial = nickname.trim().charAt(0) || "사";

      if (avatar) avatar.textContent = initial;
      if (nicknameText) nicknameText.textContent = nickname;
      if (emailText) emailText.textContent = member.email;
      if (nicknameInput) nicknameInput.value = nickname;
      if (profileCard) profileCard.setAttribute("aria-busy", "false");

      const headerAvatar = document.querySelector("[data-auth-avatar]");
      if (headerAvatar) {
        headerAvatar.textContent = initial;
        headerAvatar.title = nickname + "님의 마이페이지";
        headerAvatar.setAttribute("aria-label", nickname + "님의 마이페이지");
      }
    }

    const TRIP_PREVIEW_COUNT = 3;
    const tripStatusLabels = {
      DRAFT: "작성 중",
      PLANNED: "진행 예정",
      ONGOING: "진행 중",
      COMPLETED: "완료",
      CANCELLED: "취소",
    };

    function formatTripPeriod(startDate, endDate) {
      const toDotted = function (value) {
        return String(value || "").replaceAll("-", ".");
      };
      const start = toDotted(startDate);
      const end = toDotted(endDate);
      if (!start) return "";
      return end && end !== start ? start + " ~ " + end : start;
    }

    function createTripCard(trip) {
      const item = document.createElement("button");
      item.type = "button";
      item.className = "trip-summary-card";
      item.dataset.route = "/trips/" + trip.tripId + "/schedule";
      item.setAttribute("aria-label", (trip.title || "여행") + " 일정 열기");

      const copy = document.createElement("span");
      copy.className = "trip-summary-copy";

      const title = document.createElement("strong");
      title.textContent = trip.title || trip.destinationName || "이름 없는 여행";

      const meta = document.createElement("span");
      meta.textContent = [trip.destinationName, formatTripPeriod(trip.startDate, trip.endDate)]
        .filter(Boolean)
        .join(" · ");

      copy.append(title, meta);

      const status = document.createElement("em");
      status.className = "trip-summary-status status-" + String(trip.status || "").toLowerCase();
      status.textContent = tripStatusLabels[trip.status] || trip.status || "";

      item.append(copy, status);
      return item;
    }

    function renderTrips(trips) {
      if (!tripList) return;

      const list = Array.isArray(trips) ? trips : [];
      // 지표는 응답 길이로 계산한다. 화면에는 최근 몇 건만 보여주므로 렌더링 개수와 다르다.
      if (tripCount) tripCount.textContent = list.length + "개";

      tripList.replaceChildren();

      if (list.length === 0) {
        const empty = document.createElement("p");
        empty.className = "mypage-state";
        empty.textContent = "아직 만든 여행이 없습니다.";

        const start = document.createElement("button");
        start.type = "button";
        start.className = "outline-button wide";
        start.dataset.route = "/trips/new/plan";
        start.textContent = "새 여행 만들기 →";

        tripList.append(empty, start);
        if (tripMoreButton) tripMoreButton.hidden = true;
        return;
      }

      // 최근 생성 순으로 보여준다. 서버 정렬에 의존하지 않고 화면에서 한 번 더 정렬한다.
      const sorted = list.slice().sort(function (a, b) {
        return String(b.createdAt || "").localeCompare(String(a.createdAt || ""));
      });

      sorted.slice(0, TRIP_PREVIEW_COUNT).forEach(function (trip) {
        tripList.appendChild(createTripCard(trip));
      });

      if (tripMoreButton) tripMoreButton.hidden = list.length <= TRIP_PREVIEW_COUNT;
    }

    function showTripState(message) {
      if (!tripList) return;
      tripList.replaceChildren();
      const state = document.createElement("p");
      state.className = "mypage-state error";
      state.textContent = message;
      tripList.appendChild(state);
      if (tripCount) tripCount.textContent = "—";
      if (tripMoreButton) tripMoreButton.hidden = true;
    }

    function showEditForm() {
      if (!editForm || !editButton || !nicknameInput || !currentMember) return;
      editForm.hidden = false;
      editButton.hidden = true;
      nicknameInput.value = currentMember.nickname;
      nicknameInput.focus();
      profileCard?.scrollIntoView({ behavior: "smooth", block: "center" });
      if (formError) formError.textContent = "";
    }

    function hideEditForm() {
      if (!editForm || !editButton) return;
      editForm.hidden = true;
      editButton.hidden = false;
      if (formError) formError.textContent = "";
    }

    editButton?.addEventListener("click", showEditForm);
    editMenuButton?.addEventListener("click", showEditForm);
    cancelButton?.addEventListener("click", hideEditForm);

    editForm?.addEventListener("submit", async function (event) {
      event.preventDefault();

      const nickname = nicknameInput.value.trim();
      const submitButton = editForm.querySelector("button[type='submit']");

      if (nickname.length < 2 || nickname.length > 20) {
        formError.textContent = "닉네임은 2자 이상 20자 이하여야 합니다.";
        return;
      }

      submitButton.disabled = true;
      formError.textContent = "";

      try {
        const updatedMember = await request("/api/v1/members/me", {
          method: "PATCH",
          body: JSON.stringify({ nickname: nickname }),
        });

        renderMember(updatedMember);
        hideEditForm();
        showToast("회원정보가 수정되었습니다.");
      } catch (error) {
        formError.textContent = error.message;
      } finally {
        submitButton.disabled = false;
      }
    });

    const memberRequest = request("/api/v1/members/me")
      .then(renderMember)
      .catch(function (error) {
        if (profileCard) profileCard.setAttribute("aria-busy", "false");
        if (nicknameText) nicknameText.textContent = "회원정보를 불러오지 못했습니다";
        if (emailText) emailText.textContent = error.message;
      });

    const tripRequest = request("/api/v1/trips")
      .then(renderTrips)
      .catch(function (error) {
        showTripState(error.message || "여행 일정을 불러오지 못했습니다.");
      });

    // 한 요청이 실패해도 나머지 영역은 정상 렌더링되도록 allSettled를 유지한다.
    Promise.allSettled([memberRequest, tripRequest]).then(function () {
      document.body.dataset.pageReady = "true";
    });
  });
 })();
