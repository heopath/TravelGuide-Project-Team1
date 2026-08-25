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

  const bookSection = document.querySelector("[data-record-book]");
  const bookStage = document.querySelector("[data-record-book-stage]");
  const bookCanvas = document.querySelector("[data-record-book-canvas]");
  const bookDrawButton = document.querySelector("[data-record-book-draw]");
  const bookSaveButton = document.querySelector("[data-record-book-save]");
  const bookGifButton = document.querySelector("[data-record-book-gif]");
  const bookNote = document.querySelector("[data-record-book-note]");

  let currentRecord = null;
  let currentTrip = null;
  let currentRating = 0;
  let images = [];
  let saving = false;
  let drawing = false;
  let routePoints = [];

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
      void persistImagesToServer().catch(() => {
        // persistImagesToServer가 화면에 실패 이유를 표시한다. 클릭 이벤트에서는 미처리 Promise를 남기지 않는다.
      });
    }
  }

  function removeImage(index) {
    mutateImages(images.filter((_, i) => i !== index));
  }

  function setCoverImage(index) {
    mutateImages(images.map((image, i) => ({ ...image, cover: i === index })));
  }

  async function persistImagesToServerWith(nextImages) {
    if (imageError) imageError.textContent = "";
    try {
      const response = await request(`/api/v1/travel-records/${currentRecord.travelRecordId}/images`, {
        method: "PUT",
        body: JSON.stringify({
          images: nextImages.map((image) => ({
            imageUrl: image.imageUrl,
            altText: image.altText || null,
            cover: image.cover,
          })),
        }),
      });
      currentRecord = response;
      images = normalizeImages(response.images);
      renderImages();
    } catch (error) {
      if (imageError) imageError.textContent = error.message;
      throw error;
    }
  }

  async function persistImagesToServer() {
    await persistImagesToServerWith(images);
    toast("여행 기록 이미지가 수정되었습니다.");
  }

  async function createRecordForPhotoUpload() {
    if (currentRecord) return currentRecord;
    const title = (titleInput?.value || `${currentTrip?.title || "여행"} 사진첩`).trim();
    const content = (contentInput?.value || "사진으로 남긴 여행의 순간들입니다.").trim();
    const visibility = visibilitySelect?.value || "PUBLIC";
    const record = await request("/api/v1/travel-records", {
      method: "POST",
      body: JSON.stringify({ tripId, title, content, rating: currentRating || null, visibility }),
    });
    applyRecord(record);
    return record;
  }

  async function uploadPhoto(recordId, photo) {
    const formData = new FormData();
    formData.append("file", photo);
    const response = await fetch(`/api/v1/travel-records/${recordId}/images/upload`, {
      method: "POST",
      credentials: "same-origin",
      body: formData,
    });
    const result = await response.json().catch(() => null);
    if (!response.ok || !result?.success) {
      throw new Error(result?.message || "사진을 S3에 업로드하지 못했습니다.");
    }
    return result.data;
  }

  imageForm?.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (imageError) imageError.textContent = "";

    const formData = new FormData(imageForm);
    const photo = formData.get("photo");
    const altText = String(formData.get("altText") || "").trim();
    const cover = formData.get("cover") === "on";

    if (!(photo instanceof File) || photo.size === 0) {
      if (imageError) imageError.textContent = "업로드할 사진을 선택해주세요.";
      return;
    }

    if (images.length >= 20) {
      if (imageError) imageError.textContent = "이미지는 최대 20개까지 등록할 수 있습니다.";
      return;
    }

    const submit = imageForm.querySelector("button[type='submit']");
    if (submit) submit.disabled = true;
    try {
      const record = await createRecordForPhotoUpload();
      const imageUrl = await uploadPhoto(record.travelRecordId, photo);
      const nextImages = cover ? images.map((image) => ({ ...image, cover: false })) : images.slice();
      nextImages.push({ imageUrl, altText, cover: cover || nextImages.length === 0 });
      imageForm.reset();
      await persistImagesToServerWith(nextImages);
      toast("사진이 사진첩에 추가되었습니다.");
    } catch (error) {
      if (imageError) imageError.textContent = error.message || "사진을 업로드하지 못했습니다.";
    } finally {
      if (submit) submit.disabled = false;
    }
  });

  function applyRecord(record) {
    currentRecord = record;

    if (titleInput) titleInput.value = record?.title || "";
    if (contentInput) contentInput.value = record?.content || "";
    if (visibilitySelect) visibilitySelect.value = record?.visibility || "PUBLIC";
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
    const visibility = visibilitySelect?.value || "PUBLIC";
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

  /*
   * 책 지면 그리기.
   *
   * 저장하지 않은 내용도 그대로 그린다. 지금 화면에 쓰고 있는 글이 어떻게 앉는지
   * 보려고 누르는 버튼이라, 저장된 것만 그리면 쓸모가 없다.
   */
  function collectBookData() {
    return {
      tripTitle: currentTrip?.title || "",
      destination: currentTrip?.destinationName || "",
      startDate: currentTrip?.startDate || "",
      endDate: currentTrip?.endDate || "",
      title: titleInput?.value.trim() || "",
      content: contentInput?.value.trim() || "",
      rating: currentRating,
      images: images.slice(),
      route: routePoints.slice()
    };
  }

  /*
   * 지면에 그릴 동선. 일정 항목에는 좌표가 없고 장소 번호만 있어서 장소를 따로 부른다.
   *
   * 한 번 부른 장소는 다시 부르지 않는다. 같은 장소를 여러 날에 담을 수 있기 때문이다.
   * 좌표가 없는 장소는 건너뛴다. 지도를 못 그려도 지면은 그려져야 하므로 실패는 삼킨다.
   */
  async function loadRoute() {
    try {
      const days = await request(`/api/v1/trips/${tripId}/days`);
      const ordered = (days || []).slice().sort((a, b) => (a.dayNumber || 0) - (b.dayNumber || 0));

      const items = [];
      for (const day of ordered) {
        const dayItems = await request(`/api/v1/trip-days/${day.tripDayId}/items`);
        (dayItems || [])
          .slice()
          .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
          .forEach((item) => { if (item.placeId) items.push(item); });
      }

      const cache = new Map();
      const points = [];
      for (const item of items) {
        if (!cache.has(item.placeId)) {
          try {
            const response = await request(`/api/v1/places/${item.placeId}`);
            cache.set(item.placeId, response?.place || null);
          } catch (error) {
            cache.set(item.placeId, null);
          }
        }
        const place = cache.get(item.placeId);
        if (!place || place.latitude == null || place.longitude == null) continue;
        points.push({ label: place.name || item.title, lat: Number(place.latitude), lng: Number(place.longitude) });
      }
      routePoints = points;
    } catch (error) {
      routePoints = [];
    }
  }

  bookDrawButton?.addEventListener("click", async () => {
    if (drawing || !bookCanvas || !window.AllMyTripsRecordBook) return;
    drawing = true;
    bookDrawButton.disabled = true;
    if (bookNote) bookNote.textContent = "지면을 그리는 중입니다…";

    try {
      const result = await window.AllMyTripsRecordBook.render(bookCanvas, collectBookData());
      if (bookStage) bookStage.hidden = false;
      if (bookSaveButton) bookSaveButton.hidden = false;
      if (bookGifButton) bookGifButton.hidden = false;

      const parts = [];
      if (result.total === 0) parts.push("사진이 없어 글로만 지면을 짰습니다.");
      else parts.push(`사진 ${result.shown}장으로 지면을 짰습니다.`);
      if (result.total > result.shown) parts.push(`${result.total - result.shown}장은 다음 지면으로 넘어갑니다.`);
      if (result.missing > 0) {
        parts.push(`${result.missing}장은 저장이 허용되지 않는 곳의 사진이라 빈 자리로 두었습니다.`);
      }
      if (bookNote) bookNote.textContent = parts.join(" ");
    } catch (error) {
      if (bookNote) bookNote.textContent = error.message || "지면을 그리지 못했습니다.";
    } finally {
      drawing = false;
      bookDrawButton.disabled = false;
    }
  });

  bookSaveButton?.addEventListener("click", async () => {
    if (!bookCanvas || !window.AllMyTripsRecordBook) return;
    bookSaveButton.disabled = true;
    try {
      const blob = await window.AllMyTripsRecordBook.toBlob(bookCanvas);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `${(currentTrip?.title || "여행기록").replace(/[\\/:*?"<>|]/g, "")}.png`;
      link.click();
      URL.revokeObjectURL(url);
      if (bookNote) bookNote.textContent = "이미지를 저장했습니다.";
    } catch (error) {
      /* 저장이 허용되지 않는 사진이 섞이면 브라우저가 내보내기를 막는다. */
      if (bookNote) bookNote.textContent = "이 지면은 저장할 수 없습니다. 사진 주소를 바꾼 뒤 다시 만들어 주세요.";
    } finally {
      bookSaveButton.disabled = false;
    }
  });

  /* 브라우저는 캔버스를 GIF로 직접 저장하지 못한다. 사진첩 표지와 지면을 교차하는
     짧은 2프레임 GIF를 만들어 메시지/커뮤니티에 바로 공유할 수 있게 한다. */
  bookGifButton?.addEventListener("click", () => {
    if (!bookCanvas || !window.GIF) {
      if (bookNote) bookNote.textContent = "GIF 저장 도구를 불러오지 못했습니다. 네트워크를 확인한 뒤 다시 시도해주세요.";
      return;
    }
    bookGifButton.disabled = true;
    if (bookNote) bookNote.textContent = "GIF 사진첩을 만드는 중입니다…";
    const gif = new window.GIF({
      workers: 2,
      quality: 10,
      width: bookCanvas.width,
      height: bookCanvas.height,
      workerScript: "https://cdn.jsdelivr.net/npm/gif.js.optimized@1.0.1/dist/gif.worker.js"
    });
    gif.addFrame(bookCanvas, { copy: true, delay: 1800 });
    gif.addFrame(bookCanvas, { copy: true, delay: 900 });
    gif.on("finished", (blob) => {
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `${(currentTrip?.title || "여행사진첩").replace(/[\\/:*?\"<>|]/g, "")}.gif`;
      link.click();
      URL.revokeObjectURL(url);
      if (bookNote) bookNote.textContent = "GIF 사진첩을 저장했습니다.";
      bookGifButton.disabled = false;
    });
    gif.on("abort", () => {
      if (bookNote) bookNote.textContent = "GIF 저장을 완료하지 못했습니다. PNG 저장을 이용해주세요.";
      bookGifButton.disabled = false;
    });
    gif.on("error", () => {
      if (bookNote) bookNote.textContent = "GIF 저장 중 오류가 발생했습니다. PNG 저장을 이용해주세요.";
      bookGifButton.disabled = false;
    });
    gif.render();
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

    if (!window.AllMyTripsTripStatus.isTripFinished(trip)) {
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
    currentTrip = trip;
    applyRecord(existing);
    showApp();
    if (bookSection) bookSection.hidden = false;
    loadRoute();
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
