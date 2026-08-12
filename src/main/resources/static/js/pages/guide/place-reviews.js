/* 장소 상세 방문자 후기 */
document.addEventListener("DOMContentLoaded", function () {
  const reviewState = document.querySelector("[data-review-state]");
  const reviewList = document.querySelector("[data-review-list]");
  const moreButton = document.querySelector("[data-review-more]");
  const openButton = document.querySelector("[data-review-open]");
  const modal = document.querySelector("[data-review-modal]");
  const form = document.querySelector("[data-review-form]");
  const contentInput = document.querySelector("[data-review-content]");
  const lengthOutput = document.querySelector("[data-review-length]");
  const formError = document.querySelector("[data-review-error]");
  const submitButton = document.querySelector("[data-review-submit]");
  const modalTitle = document.querySelector("[data-review-modal-title]");
  const modalPlaceName = document.querySelector("[data-review-place-name]");
  const ratingButtons = Array.from(document.querySelectorAll("[data-review-rating] button"));
  const closeButtons = Array.from(document.querySelectorAll("[data-review-close]"));
  const PAGE_SIZE = 5;
  let place = null;
  let reviews = [];
  let currentPage = 0;
  let hasNext = false;
  let authenticated = false;
  let myReview = null;
  let editingReview = null;
  let selectedRating = 0;

  function toast(message) {
    if (window.AllMyTripsModal && typeof window.AllMyTripsModal.showToast === "function") {
      window.AllMyTripsModal.showToast(message);
      return;
    }
    window.alert(message);
  }

  async function api(url, options) {
    const response = await fetch(url, Object.assign({
      headers: { Accept: "application/json" },
      credentials: "same-origin",
      allMyTripsLoading: false,
    }, options || {}));
    const payload = await response.json().catch(function () { return null; });
    if (!response.ok || !payload || payload.success === false) {
      const error = new Error((payload && payload.message) || "후기 요청을 처리하지 못했습니다.");
      error.status = response.status;
      throw error;
    }
    return payload.data;
  }

  function stars(rating) {
    const value = Math.max(0, Math.min(5, Number(rating) || 0));
    return "★".repeat(value) + "☆".repeat(5 - value);
  }

  function formatDate(value) {
    if (!value) return "";
    return new Intl.DateTimeFormat("ko-KR", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(new Date(value));
  }

  function updateSummary(summary) {
    const average = Number(summary && summary.averageRating || 0);
    const count = Number(summary && summary.reviewCount || 0);
    document.querySelector("[data-review-average]").textContent = average.toFixed(1);
    document.querySelector("[data-review-count]").textContent = count.toLocaleString("ko-KR");
    const averageStars = document.querySelector("[data-review-average-stars]");
    averageStars.textContent = stars(Math.round(average));
    averageStars.setAttribute("aria-label", "평균 평점 " + average.toFixed(1) + "점");

    const distribution = summary && summary.ratingDistribution || {};
    for (let rating = 5; rating >= 1; rating--) {
      const row = document.querySelector('[data-rating-row="' + rating + '"]');
      const ratingCount = Number(distribution[rating] || 0);
      row.querySelector("em").textContent = ratingCount.toLocaleString("ko-KR");
      row.querySelector("i b").style.width = count ? (ratingCount / count * 100) + "%" : "0%";
    }

    if (place) {
      const placeRating = document.querySelector("[data-place-rating]");
      if (placeRating) {
        placeRating.textContent = count > 0 ? average.toFixed(1) : "평점 정보 없음";
      }
    }
  }

  function createReviewItem(review) {
    const article = document.createElement("article");
    article.className = "review-item";

    const head = document.createElement("div");
    head.className = "review-item-head";
    const author = document.createElement("div");
    author.className = "review-author";
    const nickname = document.createElement("strong");
    nickname.textContent = review.nickname || "여행자";
    author.appendChild(nickname);
    if (review.verifiedVisit) {
      const verified = document.createElement("span");
      verified.className = "verified-badge";
      verified.textContent = "방문 인증";
      author.appendChild(verified);
    }
    const date = document.createElement("span");
    date.className = "review-date";
    date.textContent = formatDate(review.updatedAt || review.createdAt);
    author.appendChild(date);
    head.appendChild(author);

    if (review.ownedByRequester) {
      const actions = document.createElement("div");
      actions.className = "review-owner-actions";
      const edit = document.createElement("button");
      edit.type = "button";
      edit.textContent = "수정";
      edit.addEventListener("click", function () { openModal(review); });
      const remove = document.createElement("button");
      remove.type = "button";
      remove.textContent = "삭제";
      remove.addEventListener("click", function () { deleteReview(review); });
      actions.append(edit, remove);
      head.appendChild(actions);
    }

    const rating = document.createElement("div");
    rating.className = "review-item-rating";
    rating.textContent = stars(review.rating);
    rating.setAttribute("aria-label", review.rating + "점");
    const reviewContent = document.createElement("p");
    reviewContent.className = "review-item-content";
    reviewContent.textContent = review.content;
    article.append(head, rating, reviewContent);
    return article;
  }

  function renderReviews() {
    reviewList.replaceChildren();
    reviews.forEach(function (review) {
      reviewList.appendChild(createReviewItem(review));
    });
    const empty = reviews.length === 0;
    reviewState.hidden = !empty;
    reviewState.classList.remove("error");
    reviewState.textContent = empty
      ? "아직 작성된 후기가 없습니다. 이 장소의 첫 후기를 남겨보세요."
      : "";
    reviewList.hidden = empty;
    moreButton.hidden = !hasNext;
    openButton.textContent = myReview ? "내 후기 수정하기" : "후기 작성하기";
  }

  async function loadReviews(reset) {
    if (!place) return;
    if (reset) {
      currentPage = 0;
      reviews = [];
      reviewState.hidden = false;
      reviewState.classList.remove("error");
      reviewState.textContent = "후기를 불러오는 중입니다.";
      reviewList.hidden = true;
      moreButton.hidden = true;
    }
    try {
      const data = await api(
        "/api/v1/places/" + place.placeId + "/reviews?page=" + currentPage + "&size=" + PAGE_SIZE
      );
      authenticated = Boolean(data.authenticated);
      myReview = data.myReview || null;
      reviews = reset ? data.reviews : reviews.concat(data.reviews);
      hasNext = Boolean(data.hasNext);
      updateSummary(data.summary);
      renderReviews();
    } catch (error) {
      reviewState.hidden = false;
      reviewState.classList.add("error");
      reviewState.textContent = "후기를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
      reviewList.hidden = true;
    }
  }

  function setRating(value) {
    selectedRating = Number(value) || 0;
    ratingButtons.forEach(function (button) {
      const selected = Number(button.dataset.rating) <= selectedRating;
      button.classList.toggle("selected", selected);
      button.setAttribute("aria-checked", String(Number(button.dataset.rating) === selectedRating));
    });
  }

  function openModal(review) {
    if (!authenticated) {
      toast("로그인 후 장소 후기를 작성할 수 있습니다.");
      return;
    }
    editingReview = review || myReview || null;
    modalTitle.textContent = editingReview ? "내 후기 수정하기" : "후기 작성하기";
    modalPlaceName.textContent = place.name;
    contentInput.value = editingReview ? editingReview.content : "";
    lengthOutput.textContent = contentInput.value.length;
    setRating(editingReview ? editingReview.rating : 0);
    formError.textContent = "";
    submitButton.textContent = editingReview ? "수정하기" : "등록하기";
    modal.hidden = false;
    modal.setAttribute("aria-hidden", "false");
    document.body.style.overflow = "hidden";
    ratingButtons[0].focus();
  }

  function closeModal() {
    modal.hidden = true;
    modal.setAttribute("aria-hidden", "true");
    document.body.style.overflow = "";
    editingReview = null;
    formError.textContent = "";
  }

  async function submitReview(event) {
    event.preventDefault();
    const content = contentInput.value.trim();
    if (!selectedRating) {
      formError.textContent = "별점을 선택해 주세요.";
      return;
    }
    if (!content) {
      formError.textContent = "후기 내용을 입력해 주세요.";
      contentInput.focus();
      return;
    }
    formError.textContent = "";
    submitButton.disabled = true;
    try {
      const editing = Boolean(editingReview);
      const url = editing
        ? "/api/v1/place-reviews/" + editingReview.placeReviewId
        : "/api/v1/places/" + place.placeId + "/reviews";
      await api(url, {
        method: editing ? "PATCH" : "POST",
        headers: { Accept: "application/json", "Content-Type": "application/json" },
        body: JSON.stringify({ rating: selectedRating, content: content }),
      });
      closeModal();
      toast(editing ? "후기를 수정했습니다." : "후기를 등록했습니다.");
      await loadReviews(true);
    } catch (error) {
      if (error.status === 401) authenticated = false;
      formError.textContent = error.message;
    } finally {
      submitButton.disabled = false;
    }
  }

  async function deleteReview(review) {
    if (!window.confirm("작성한 후기를 삭제할까요?")) return;
    try {
      await api("/api/v1/place-reviews/" + review.placeReviewId, { method: "DELETE" });
      toast("후기를 삭제했습니다.");
      await loadReviews(true);
    } catch (error) {
      toast(error.message);
    }
  }

  document.addEventListener("placeDetailLoaded", function (event) {
    place = event.detail.place;
    loadReviews(true);
  });
  openButton.addEventListener("click", function () { openModal(myReview); });
  moreButton.addEventListener("click", function () {
    currentPage += 1;
    loadReviews(false);
  });
  ratingButtons.forEach(function (button) {
    button.addEventListener("click", function () { setRating(button.dataset.rating); });
  });
  closeButtons.forEach(function (button) { button.addEventListener("click", closeModal); });
  contentInput.addEventListener("input", function () {
    lengthOutput.textContent = contentInput.value.length;
  });
  form.addEventListener("submit", submitReview);
  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape" && !modal.hidden) closeModal();
  });
});
