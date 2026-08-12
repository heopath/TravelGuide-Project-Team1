/* 여행 기록: 완료된 여행 1건에 대한 기록 조회/작성/수정/이미지/삭제 */
document.addEventListener("DOMContentLoaded", function () {
  const tripId = Number(document.body.dataset.tripId);

  const loadingEl = document.querySelector("[data-record-loading]");
  const errorEl = document.querySelector("[data-record-error]");
  const errorMessageEl = document.querySelector("[data-record-error-message]");
  const blockedEl = document.querySelector("[data-record-blocked]");
  const appEl = document.querySelector("[data-record-app]");
  const titleEl = document.querySelector("[data-record-trip-title]");
  const periodEl = document.querySelector("[data-record-trip-period]");

  const modeLabelEl = document.querySelector("[data-record-mode-label]");
  const titleInput = document.querySelector("[data-record-title]");
  const titleError = document.querySelector("[data-record-title-error]");
  const contentInput = document.querySelector("[data-record-content]");
  const contentError = document.querySelector("[data-record-content-error]");
  const ratingGroup = document.querySelector("[data-record-rating]");
  const ratingButtons = ratingGroup ? Array.from(ratingGroup.querySelectorAll("button")) : [];
  const visibilitySelect = document.querySelector("[data-record-visibility]");
  const submitButton = document.querySelector("[data-record-submit]");

  const imageListEl = document.querySelector("[data-record-image-list]");
  const imageForm = document.querySelector("[data-record-image-form]");
  const imageError = document.querySelector("[data-record-image-error]");

  const deleteSection = document.querySelector("[data-record-delete-section]");
  const deleteOpenButton = document.querySelector("[data-record-delete-open]");
  const deleteConfirm = document.querySelector("[data-record-delete-confirm]");
  const deleteCancelButton = document.querySelector("[data-record-delete-cancel]");
  const deleteSubmitButton = document.querySelector("[data-record-delete-submit]");

  let currentRecord = null;
  let currentRating = 0;
  let images = [];
  let saving = false;

  function toast(message) {
    if (window.AllMyTripsModal?.showToast) {
      window.AllMyTripsModal.showToast(message);
      return;
    }
    alert(message);
  }

  async function request(url, options = {}) {
    const method = String(options.method || "GET").toUpperCase();
    const headers = {
      Accept: "application/json",
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...(options.headers || {}),
    };

    // CSRF 헤더는 app.js의 전역 fetch 래퍼(installCsrfAwareFetch)가 붙여준다.
    // record.html이 appScripts를 먼저 로드하므로 이 request()가 부르는 fetch는
    // 항상 그 래퍼를 거친다 — 여기서 직접 토큰을 읽어 붙이지 않는다.

    const response = await fetch(url, {
      ...options,
      method,
      credentials: "same-origin",
      headers,
    });

    const result = await response.json().catch(() => null);

    if (response.status === 401) {
      const error = new Error(result?.message || "로그인이 필요합니다.");
      error.code = result?.code || "UNAUTHORIZED";
      throw error;
    }

    // docs/api/error-responses.md 기준: 미인증(401)과 달리 403은 CSRF 토큰
    // 실패·만료를 뜻한다. app.js의 CSRF 래퍼가 아직 토큰을 재발급·재시도하지
    // 않으므로(별도 이슈), 여기서는 원인을 알 수 있는 메시지만 구분해 보여준다.
    if (response.status === 403) {
      const error = new Error(result?.message || "요청이 만료되었어요. 새로고침 후 다시 시도해주세요.");
      error.code = result?.code || "FORBIDDEN";
      throw error;
    }

    if (!response.ok || !result?.success) {
      const error = new Error(result?.message || "요청을 처리하지 못했습니다.");
      error.code = result?.code || "";
      error.status = response.status;
      throw error;
    }

    return result.data;
  }

  function showLoading() {
    if (loadingEl) loadingEl.hidden = false;
    if (errorEl) errorEl.hidden = true;
    if (blockedEl) blockedEl.hidden = true;
    if (appEl) appEl.hidden = true;
  }

  function showError(message) {
    if (loadingEl) loadingEl.hidden = true;
    if (errorEl) errorEl.hidden = false;
    if (blockedEl) blockedEl.hidden = true;
    if (appEl) appEl.hidden = true;
    if (errorMessageEl) errorMessageEl.textContent = message;
  }

  function showBlocked() {
    if (loadingEl) loadingEl.hidden = true;
    if (errorEl) errorEl.hidden = true;
    if (blockedEl) blockedEl.hidden = false;
    if (appEl) appEl.hidden = true;
  }

  function showApp() {
    if (loadingEl) loadingEl.hidden = true;
    if (errorEl) errorEl.hidden = true;
    if (blockedEl) blockedEl.hidden = true;
    if (appEl) appEl.hidden = false;
  }

  function formatPeriod(startDate, endDate) {
    if (!startDate || !endDate) return "";
    return `${startDate.replaceAll("-", ".")} – ${endDate.replaceAll("-", ".")}`;
  }

  function setRating(value) {
    currentRating = value;
    ratingButtons.forEach((button) => {
      button.classList.toggle("active", Number(button.dataset.value) <= value);
    });
  }

  ratingButtons.forEach((button) => {
    button.addEventListener("click", () => setRating(Number(button.dataset.value)));
  });

  function normalizeImages(list) {
    return (list || [])
      .slice()
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map((image) => ({ imageUrl: image.imageUrl, altText: image.altText, cover: image.cover }));
  }

  function renderImages() {
    if (!imageListEl) return;
    imageListEl.replaceChildren();

    if (images.length === 0) {
      const empty = document.createElement("p");
      empty.className = "record-image-empty";
      empty.textContent = "아직 등록된 사진이 없습니다.";
      imageListEl.appendChild(empty);
      return;
    }

    images.forEach((image, index) => {
      const tile = document.createElement("div");
      tile.className = "record-image-tile" + (image.cover ? " featured" : "");
      tile.style.backgroundImage = `url("${image.imageUrl}")`;
      if (image.altText) tile.title = image.altText;

      const removeButton = document.createElement("button");
      removeButton.type = "button";
      removeButton.className = "record-image-remove";
      removeButton.setAttribute("aria-label", "이미지 삭제");
      removeButton.textContent = "×";
      removeButton.addEventListener("click", () => removeImage(index));
      tile.appendChild(removeButton);

      if (!image.cover) {
        const coverButton = document.createElement("button");
        coverButton.type = "button";
        coverButton.className = "record-image-cover-button";
        coverButton.textContent = "대표로 지정";
        coverButton.addEventListener("click", () => setCoverImage(index));
        tile.appendChild(coverButton);
      }

      imageListEl.appendChild(tile);
    });
  }

  function mutateImages(nextImages) {
    images = nextImages;
    renderImages();
    if (currentRecord) {
      persistImagesToServer();
    }
  }

  function removeImage(index) {
    mutateImages(images.filter((_, i) => i !== index));
  }

  function setCoverImage(index) {
    mutateImages(images.map((image, i) => ({ ...image, cover: i === index })));
  }

  async function persistImagesToServer() {
    if (imageError) imageError.textContent = "";
    try {
      const response = await request(`/api/v1/travel-records/${currentRecord.travelRecordId}/images`, {
        method: "PUT",
        body: JSON.stringify({
          images: images.map((image) => ({
            imageUrl: image.imageUrl,
            altText: image.altText || null,
            cover: image.cover,
          })),
        }),
      });
      currentRecord = response;
      images = normalizeImages(response.images);
      renderImages();
      toast("여행 기록 이미지가 수정되었습니다.");
    } catch (error) {
      if (imageError) imageError.textContent = error.message;
    }
  }

  imageForm?.addEventListener("submit", (event) => {
    event.preventDefault();
    if (imageError) imageError.textContent = "";

    const formData = new FormData(imageForm);
    const imageUrl = String(formData.get("imageUrl") || "").trim();
    const altText = String(formData.get("altText") || "").trim();
    const cover = formData.get("cover") === "on";

    if (!imageUrl) {
      if (imageError) imageError.textContent = "이미지 URL을 입력해주세요.";
      return;
    }

    if (images.length >= 20) {
      if (imageError) imageError.textContent = "이미지는 최대 20개까지 등록할 수 있습니다.";
      return;
    }

    const nextImages = cover ? images.map((image) => ({ ...image, cover: false })) : images.slice();
    nextImages.push({ imageUrl, altText, cover });

    imageForm.reset();
    mutateImages(nextImages);
  });

  function applyRecord(record) {
    currentRecord = record;

    if (titleInput) titleInput.value = record?.title || "";
    if (contentInput) contentInput.value = record?.content || "";
    if (visibilitySelect) visibilitySelect.value = record?.visibility || "PRIVATE";
    setRating(record?.rating || 0);
    images = normalizeImages(record?.images);
    renderImages();

    if (modeLabelEl) modeLabelEl.textContent = record ? "기록 수정" : "새 기록 작성";
    if (submitButton) submitButton.textContent = record ? "기록 수정" : "기록 저장";
    if (deleteSection) deleteSection.hidden = !record;
    if (deleteConfirm) deleteConfirm.hidden = true;
  }

  submitButton?.addEventListener("click", async () => {
    if (saving) return;
    if (titleError) titleError.textContent = "";
    if (contentError) contentError.textContent = "";

    const title = (titleInput?.value || "").trim();
    const content = (contentInput?.value || "").trim();
    const visibility = visibilitySelect?.value || "PRIVATE";
    const rating = currentRating > 0 ? currentRating : null;

    if (!title) {
      if (titleError) titleError.textContent = "제목을 입력해주세요.";
      return;
    }
    if (!content) {
      if (contentError) contentError.textContent = "여행 메모를 입력해주세요.";
      return;
    }

    saving = true;
    submitButton.disabled = true;

    // 생성 응답의 images는 항상 빈 배열이라(이미지는 별도 API), applyRecord가 로컬 배열을
    // 덮어쓰기 전에 저장이 안 된 이미지가 있었는지 먼저 기억해둔다.
    const pendingImages = currentRecord ? null : images.slice();

    try {
      let response;
      if (currentRecord) {
        response = await request(`/api/v1/travel-records/${currentRecord.travelRecordId}`, {
          method: "PUT",
          body: JSON.stringify({ title, content, rating, visibility }),
        });
        toast("여행 기록이 수정되었습니다.");
        applyRecord(response);
      } else {
        response = await request("/api/v1/travel-records", {
          method: "POST",
          body: JSON.stringify({ tripId, title, content, rating, visibility }),
        });
        toast("여행 기록이 작성되었습니다.");
        applyRecord(response);

        if (pendingImages && pendingImages.length > 0) {
          images = pendingImages;
          await persistImagesToServer();
        }
      }
    } catch (error) {
      toast(error.message || "기록을 저장하지 못했습니다.");
    } finally {
      saving = false;
      submitButton.disabled = false;
    }
  });

  deleteOpenButton?.addEventListener("click", () => {
    if (deleteConfirm) deleteConfirm.hidden = false;
  });

  deleteCancelButton?.addEventListener("click", () => {
    if (deleteConfirm) deleteConfirm.hidden = true;
  });

  deleteSubmitButton?.addEventListener("click", async () => {
    if (!currentRecord) return;
    deleteSubmitButton.disabled = true;
    try {
      await request(`/api/v1/travel-records/${currentRecord.travelRecordId}`, { method: "DELETE" });
      toast("여행 기록이 삭제되었습니다.");
      applyRecord(null);
    } catch (error) {
      toast(error.message || "기록을 삭제하지 못했습니다.");
    } finally {
      deleteSubmitButton.disabled = false;
    }
  });

  async function loadTripAndRecord() {
    showLoading();

    let trip;
    try {
      trip = await request(`/api/v1/trips/${tripId}`);
    } catch (error) {
      if (error.code === "UNAUTHORIZED") {
        showError("로그인이 필요합니다. 로그인 후 다시 시도해주세요.");
      } else {
        showError(error.message || "여행 정보를 불러오지 못했습니다.");
      }
      return;
    }

    if (titleEl) titleEl.textContent = trip.title || "여행 기록";
    if (periodEl) periodEl.textContent = formatPeriod(trip.startDate, trip.endDate);

    if (trip.status !== "COMPLETED") {
      showBlocked();
      return;
    }

    let myRecords;
    try {
      myRecords = await request("/api/v1/travel-records/me");
    } catch (error) {
      showError(error.message || "여행 기록을 불러오지 못했습니다.");
      return;
    }

    const existing = myRecords.find((record) => record.tripId === tripId) || null;
    applyRecord(existing);
    showApp();
  }

  if (!Number.isInteger(tripId) || tripId <= 0) {
    showError("잘못된 여행 정보입니다.");
    document.body.dataset.pageReady = "true";
    return;
  }

  loadTripAndRecord().finally(() => {
    document.body.dataset.pageReady = "true";
  });
});
