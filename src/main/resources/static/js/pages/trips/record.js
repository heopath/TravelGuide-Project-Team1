/* 완료 여행의 사진만 고르면 일정·예약을 자동으로 엮는 여행 사진첩 */
document.addEventListener("DOMContentLoaded", function () {
  const tripId = Number(document.body.dataset.tripId);

  const loadingEl = document.querySelector("[data-record-loading]");
  const errorEl = document.querySelector("[data-record-error]");
  const errorMessageEl = document.querySelector("[data-record-error-message]");
  const blockedEl = document.querySelector("[data-record-blocked]");
  const appEl = document.querySelector("[data-record-app]");
  const titleEl = document.querySelector("[data-record-trip-title]");
  const periodEl = document.querySelector("[data-record-trip-period]");
  const backButton = document.querySelector("[data-record-back]");

  const imageForm = document.querySelector("[data-record-image-form]");
  const fileInput = imageForm?.querySelector("input[type='file']");
  const createAlbumButton = document.querySelector("[data-record-create-album]");
  const fileSelection = document.querySelector("[data-record-file-selection]");
  const fileCount = document.querySelector("[data-record-file-count]");
  const fileNames = document.querySelector("[data-record-file-names]");
  const imageError = document.querySelector("[data-record-image-error]");
  const photoManager = document.querySelector("[data-record-photo-manager]");
  const imageListEl = document.querySelector("[data-record-image-list]");
  const imageCountEl = document.querySelector("[data-record-image-count]");
  const stepPanels = Array.from(document.querySelectorAll("[data-record-step-panel]"));
  const stepJumps = Array.from(document.querySelectorAll("[data-record-step-jump]"));
  const stepPrev = document.querySelector("[data-record-step-prev]");
  const stepNext = document.querySelector("[data-record-step-next]");
  const stepLabel = document.querySelector("[data-record-step-label]");

  const visibilitySelect = document.querySelector("[data-record-visibility]");
  const deleteSection = document.querySelector("[data-record-delete-section]");
  const deleteOpenButton = document.querySelector("[data-record-delete-open]");
  const deleteConfirm = document.querySelector("[data-record-delete-confirm]");
  const deleteCancelButton = document.querySelector("[data-record-delete-cancel]");
  const deleteSubmitButton = document.querySelector("[data-record-delete-submit]");

  const bookSection = document.querySelector("[data-record-book]");
  const bookCanvas = document.querySelector("[data-record-book-canvas]");
  const bookNote = document.querySelector("[data-record-book-note]");
  const pageControls = document.querySelector("[data-record-page-controls]");
  const pagePrev = document.querySelector("[data-record-page-prev]");
  const pageNext = document.querySelector("[data-record-page-next]");
  const pageIndicator = document.querySelector("[data-record-page-indicator]");
  const bookSaveButton = document.querySelector("[data-record-book-save]");
  const bookGifButton = document.querySelector("[data-record-book-gif]");
  const bookShareButton = document.querySelector("[data-record-book-share]");

  let currentTrip = null;
  let currentRecord = null;
  let images = [];
  let tripDays = [];
  let bookingSummary = { items: [], errors: [] };
  let routePoints = [];
  let selectedFiles = [];
  let currentPage = 0;
  let pageCount = 0;
  let drawing = false;
  let gifBlobCache = null;
  let wizardStep = 1;

  backButton?.addEventListener("click", () => {
    if (window.history.length > 1 && document.referrer.startsWith(window.location.origin)) {
      window.history.back();
      return;
    }
    window.location.href = "/mypage?view=trips";
  });

  function toast(message) {
    if (window.AllMyTripsModal?.showToast) {
      window.AllMyTripsModal.showToast(message);
      return;
    }
    window.alert(message);
  }

  async function request(url, options = {}) {
    const method = String(options.method || "GET").toUpperCase();
    const headers = {
      Accept: "application/json",
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...(options.headers || {}),
    };
    const response = await fetch(url, {
      ...options,
      method,
      credentials: "same-origin",
      headers,
    });
    const result = await response.json().catch(() => null);
    if (!response.ok || !result?.success) {
      const error = new Error(result?.message || "요청을 처리하지 못했습니다.");
      error.code = result?.code || "";
      error.status = response.status;
      throw error;
    }
    return result.data;
  }

  function showOnly(target) {
    [loadingEl, errorEl, blockedEl, appEl].forEach((element) => {
      if (element) element.hidden = element !== target;
    });
  }

  function showError(message) {
    showOnly(errorEl);
    if (errorMessageEl) errorMessageEl.textContent = message;
  }

  function canOpenStep(step) {
    return step === 1 || images.length > 0;
  }

  function updateWizardControls() {
    stepJumps.forEach((button) => {
      const step = Number(button.dataset.recordStepJump);
      button.disabled = !canOpenStep(step);
      button.classList.toggle("is-active", step === wizardStep);
      button.classList.toggle("is-complete", step < wizardStep && canOpenStep(step));
      if (step === wizardStep) button.setAttribute("aria-current", "step");
      else button.removeAttribute("aria-current");
    });
    if (stepPrev) stepPrev.disabled = wizardStep <= 1;
    if (stepNext) {
      stepNext.disabled = wizardStep >= 3 || !canOpenStep(wizardStep + 1);
      stepNext.textContent = wizardStep === 1 ? "사진 정리 →" : wizardStep === 2 ? "앨범 보기 →" : "완료";
    }
    if (stepLabel) stepLabel.textContent = `${wizardStep} / 3`;
  }

  function showWizardStep(nextStep, direction) {
    const requested = Math.max(1, Math.min(3, Number(nextStep) || 1));
    if (!canOpenStep(requested)) return;
    const previous = wizardStep;
    wizardStep = requested;
    stepPanels.forEach((panel) => {
      const active = Number(panel.dataset.recordStepPanel) === wizardStep;
      panel.classList.remove("is-active", "is-enter-next", "is-enter-prev");
      panel.setAttribute("aria-hidden", active ? "false" : "true");
      if (active) {
        panel.classList.add("is-active");
        if (previous !== wizardStep) {
          panel.classList.add(direction || (wizardStep > previous ? "is-enter-next" : "is-enter-prev"));
        }
      }
    });
    updateWizardControls();
    if (wizardStep === 3 && images.length) {
      window.requestAnimationFrame(() => void renderAlbumPage(currentPage));
    }
  }

  function updateWizardState() {
    if (!images.length && wizardStep > 1) wizardStep = 1;
    showWizardStep(wizardStep);
  }

  stepPrev?.addEventListener("click", () => showWizardStep(wizardStep - 1, "is-enter-prev"));
  stepNext?.addEventListener("click", () => showWizardStep(wizardStep + 1, "is-enter-next"));
  stepJumps.forEach((button) => {
    button.addEventListener("click", () => {
      const step = Number(button.dataset.recordStepJump);
      showWizardStep(step, step > wizardStep ? "is-enter-next" : "is-enter-prev");
    });
  });

  function formatPeriod(startDate, endDate) {
    if (!startDate) return "";
    const start = String(startDate).replaceAll("-", ".");
    const end = String(endDate || startDate).replaceAll("-", ".");
    return start === end ? start : `${start} – ${end}`;
  }

  function automaticTitle() {
    return `${currentTrip?.title || currentTrip?.destinationName || "여행"} 사진첩`;
  }

  function automaticContent() {
    const period = formatPeriod(currentTrip?.startDate, currentTrip?.endDate);
    return [
      period,
      currentTrip?.destinationName,
      "여행 일정과 예약, 사진으로 자동 구성한 사진첩입니다."
    ].filter(Boolean).join(" · ");
  }

  function normalizeImages(list) {
    return (list || [])
      .slice()
      .sort((left, right) => Number(left.sortOrder || 0) - Number(right.sortOrder || 0))
      .map((image) => ({
        imageUrl: image.imageUrl,
        altText: image.altText || "",
        cover: Boolean(image.cover),
      }));
  }

  function resetAlbumRendering() {
    gifBlobCache = null;
    currentPage = 0;
  }

  function renderImages() {
    if (!imageListEl) return;
    imageListEl.replaceChildren();
    if (imageCountEl) imageCountEl.textContent = `사진 ${images.length}장`;

    images.forEach((image, index) => {
      const tile = document.createElement("div");
      tile.className = "record-image-tile" + (image.cover ? " featured" : "");
      tile.style.backgroundImage = `url("${image.imageUrl}")`;
      tile.title = image.altText || `${index + 1}번째 여행 사진`;

      const removeButton = document.createElement("button");
      removeButton.type = "button";
      removeButton.className = "record-image-remove";
      removeButton.setAttribute("aria-label", `${index + 1}번째 사진 삭제`);
      removeButton.textContent = "×";
      removeButton.addEventListener("click", () => void removeImage(index));
      tile.appendChild(removeButton);

      if (!image.cover) {
        const coverButton = document.createElement("button");
        coverButton.type = "button";
        coverButton.className = "record-image-cover-button";
        coverButton.textContent = "대표로 지정";
        coverButton.addEventListener("click", () => void setCoverImage(index));
        tile.appendChild(coverButton);
      }
      imageListEl.appendChild(tile);
    });

    updateWizardState();
  }

  async function persistImages(nextImages) {
    if (!currentRecord) return;
    const response = await request(`/api/v1/travel-records/${currentRecord.travelRecordId}/images`, {
      method: "PUT",
      body: JSON.stringify({
        images: nextImages.map((image) => ({
          imageUrl: image.imageUrl,
          altText: image.altText || null,
          cover: Boolean(image.cover),
        })),
      }),
    });
    currentRecord = response;
    images = normalizeImages(response.images);
    resetAlbumRendering();
    renderImages();
  }

  async function removeImage(index) {
    if (!currentRecord) return;
    const next = images.filter((_, imageIndex) => imageIndex !== index);
    if (next.length && !next.some((image) => image.cover)) next[0].cover = true;
    try {
      await persistImages(next);
      if (images.length) await renderAlbumPage(0);
      toast("사진을 앨범에서 뺐습니다.");
    } catch (error) {
      if (imageError) imageError.textContent = error.message;
    }
  }

  async function setCoverImage(index) {
    try {
      await persistImages(images.map((image, imageIndex) => ({
        ...image,
        cover: imageIndex === index,
      })));
      await renderAlbumPage(0);
      toast("대표 사진을 바꿨습니다.");
    } catch (error) {
      if (imageError) imageError.textContent = error.message;
    }
  }

  function acceptedFiles(files) {
    const acceptedTypes = new Set(["image/jpeg", "image/png", "image/webp", "image/gif"]);
    return Array.from(files || []).filter((file) => acceptedTypes.has(file.type) && file.size <= 10 * 1024 * 1024);
  }

  function renderFileSelection() {
    const raw = Array.from(fileInput?.files || []);
    selectedFiles = acceptedFiles(raw);
    if (fileSelection) fileSelection.hidden = selectedFiles.length === 0;
    if (fileCount) fileCount.textContent = `선택한 사진 ${selectedFiles.length}장`;
    if (fileNames) fileNames.textContent = selectedFiles.map((file) => file.name).join(", ");
    if (createAlbumButton) createAlbumButton.disabled = selectedFiles.length === 0;

    if (imageError) {
      if (raw.length !== selectedFiles.length) {
        imageError.textContent = "10MB 이하의 JPG, PNG, WEBP, GIF 파일만 사용할 수 있습니다.";
      } else if (images.length + selectedFiles.length > 20) {
        imageError.textContent = "사진첩에는 최대 20장까지 담을 수 있습니다.";
        createAlbumButton.disabled = true;
      } else {
        imageError.textContent = "";
      }
    }
  }

  fileInput?.addEventListener("change", renderFileSelection);

  async function ensureRecord() {
    if (currentRecord) return currentRecord;
    const record = await request("/api/v1/travel-records", {
      method: "POST",
      body: JSON.stringify({
        tripId,
        title: automaticTitle(),
        content: automaticContent(),
        rating: null,
        visibility: visibilitySelect?.value || "PRIVATE",
      }),
    });
    currentRecord = record;
    images = normalizeImages(record.images);
    if (deleteSection) deleteSection.hidden = false;
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
      throw new Error(result?.message || `${photo.name} 업로드에 실패했습니다.`);
    }
    return result.data;
  }

  imageForm?.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!selectedFiles.length || images.length + selectedFiles.length > 20) return;
    if (imageError) imageError.textContent = "";
    if (createAlbumButton) createAlbumButton.disabled = true;

    const uploaded = [];
    const creatingRecord = !currentRecord;
    let failed = 0;
    try {
      const record = await ensureRecord();
      for (let index = 0; index < selectedFiles.length; index += 1) {
        if (createAlbumButton) {
          createAlbumButton.textContent = `사진 올리는 중 ${index + 1} / ${selectedFiles.length}`;
        }
        try {
          const storageReference = await uploadPhoto(record.travelRecordId, selectedFiles[index]);
          uploaded.push({
            imageUrl: storageReference,
            altText: `${currentTrip?.destinationName || "여행"} 사진 ${images.length + uploaded.length + 1}`,
            cover: images.length === 0 && uploaded.length === 0,
          });
        } catch (error) {
          failed += 1;
        }
      }

      if (!uploaded.length) {
        if (creatingRecord && currentRecord) {
          await request(`/api/v1/travel-records/${currentRecord.travelRecordId}`, { method: "DELETE" });
          currentRecord = null;
          if (deleteSection) deleteSection.hidden = true;
        }
        throw new Error("사진을 업로드하지 못했습니다. 잠시 후 다시 시도해주세요.");
      }
      await persistImages(images.concat(uploaded));
      imageForm.reset();
      selectedFiles = [];
      renderFileSelection();
      await renderAlbumPage(0);
      showWizardStep(3, "is-enter-next");
      const message = failed
        ? `${uploaded.length}장은 담았고 ${failed}장은 올리지 못했습니다.`
        : `사진 ${uploaded.length}장과 여행 정보를 자동으로 엮었습니다.`;
      if (bookNote) bookNote.textContent = message;
      toast(message);
    } catch (error) {
      if (imageError) imageError.textContent = error.message || "사진첩을 만들지 못했습니다.";
    } finally {
      if (createAlbumButton) {
        createAlbumButton.textContent = "사진으로 앨범 만들기";
        createAlbumButton.disabled = selectedFiles.length === 0;
      }
    }
  });

  function collectAlbumData() {
    return {
      tripTitle: currentTrip?.title || currentTrip?.destinationName || "여행 사진첩",
      destination: currentTrip?.destinationName || "",
      startDate: currentTrip?.startDate || "",
      endDate: currentTrip?.endDate || "",
      images: images.slice(),
      days: tripDays.map((day) => ({ ...day, items: (day.items || []).slice() })),
      bookings: bookingSummary || { items: [] },
      route: routePoints.slice(),
    };
  }

  async function renderAlbumPage(index) {
    if (drawing || !bookCanvas || !window.AllMyTripsRecordBook || !images.length) return;
    drawing = true;
    try {
      const result = await window.AllMyTripsRecordBook.renderAlbum(bookCanvas, collectAlbumData(), index);
      currentPage = result.index;
      pageCount = result.pageCount;
      if (pageControls) pageControls.hidden = pageCount <= 1;
      if (pageIndicator) pageIndicator.textContent = `${currentPage + 1} / ${pageCount}`;
      if (pagePrev) pagePrev.disabled = currentPage <= 0;
      if (pageNext) pageNext.disabled = currentPage >= pageCount - 1;
      if (bookNote && !bookNote.textContent) {
        bookNote.textContent = `사진 ${images.length}장, 일정 ${tripDays.length}일, 예약 ${bookingSummary?.items?.length || 0}건으로 자동 구성했습니다.`;
      }
    } catch (error) {
      if (bookNote) bookNote.textContent = error.message || "사진첩 페이지를 그리지 못했습니다.";
    } finally {
      drawing = false;
    }
  }

  async function turnAlbumPage(index, direction) {
    if (drawing || !bookCanvas || index < 0 || index >= pageCount || index === currentPage) return;
    const animationClass = direction === "prev" ? "is-turning-prev" : "is-turning-next";
    bookCanvas.classList.remove("is-turning-prev", "is-turning-next");
    void bookCanvas.offsetWidth;
    bookCanvas.classList.add(animationClass);
    window.setTimeout(() => void renderAlbumPage(index), 155);
    window.setTimeout(() => bookCanvas.classList.remove(animationClass), 360);
  }

  pagePrev?.addEventListener("click", () => void turnAlbumPage(currentPage - 1, "prev"));
  pageNext?.addEventListener("click", () => void turnAlbumPage(currentPage + 1, "next"));

  function safeFileName(extension) {
    const title = currentTrip?.title || "여행사진첩";
    return `${title.replace(/[\\/:*?"<>|]/g, "")}.${extension}`;
  }

  function downloadBlob(blob, filename) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 0);
  }

  bookSaveButton?.addEventListener("click", async () => {
    if (!bookCanvas || !window.AllMyTripsRecordBook) return;
    bookSaveButton.disabled = true;
    try {
      downloadBlob(await window.AllMyTripsRecordBook.toBlob(bookCanvas), safeFileName("png"));
      if (bookNote) bookNote.textContent = `${currentPage + 1}번째 앨범 페이지를 PNG로 저장했습니다.`;
    } catch (error) {
      if (bookNote) bookNote.textContent = "현재 페이지를 저장하지 못했습니다.";
    } finally {
      bookSaveButton.disabled = false;
    }
  });

  async function createGifBlob() {
    if (gifBlobCache) return gifBlobCache;
    if (!window.GIF || !window.AllMyTripsRecordBook) {
      throw new Error("GIF 저장 도구를 불러오지 못했습니다.");
    }
    const frames = await window.AllMyTripsRecordBook.renderAll(collectAlbumData());
    if (!frames.length) throw new Error("GIF로 만들 앨범 페이지가 없습니다.");

    gifBlobCache = await new Promise((resolve, reject) => {
      const gif = new window.GIF({
        workers: 2,
        quality: 10,
        repeat: 0,
        width: frames[0].width,
        height: frames[0].height,
        workerScript: "https://cdn.jsdelivr.net/npm/gif.js.optimized@1.0.1/dist/gif.worker.js",
      });
      frames.forEach((frame, index) => {
        gif.addFrame(frame, { copy: true, delay: index === 0 ? 2100 : 1600 });
      });
      gif.on("finished", resolve);
      gif.on("abort", () => reject(new Error("GIF 생성을 중단했습니다.")));
      gif.on("error", () => reject(new Error("GIF 저장 중 오류가 발생했습니다.")));
      gif.render();
    });
    return gifBlobCache;
  }

  function setGifBusy(busy) {
    if (bookGifButton) bookGifButton.disabled = busy;
    if (bookShareButton) bookShareButton.disabled = busy;
  }

  bookGifButton?.addEventListener("click", async () => {
    setGifBusy(true);
    if (bookNote) bookNote.textContent = `표지와 ${Math.max(pageCount - 1, 1)}개의 여행 페이지로 GIF를 만드는 중입니다…`;
    try {
      downloadBlob(await createGifBlob(), safeFileName("gif"));
      if (bookNote) bookNote.textContent = "페이지가 순서대로 넘어가는 GIF 사진첩을 저장했습니다.";
    } catch (error) {
      if (bookNote) bookNote.textContent = error.message || "GIF 사진첩을 만들지 못했습니다.";
    } finally {
      setGifBusy(false);
    }
  });

  bookShareButton?.addEventListener("click", async () => {
    setGifBusy(true);
    if (bookNote) bookNote.textContent = "공유할 GIF 사진첩을 만드는 중입니다…";
    try {
      const blob = await createGifBlob();
      const file = new File([blob], safeFileName("gif"), { type: "image/gif" });
      if (navigator.share && (!navigator.canShare || navigator.canShare({ files: [file] }))) {
        await navigator.share({
          title: automaticTitle(),
          text: `${currentTrip?.destinationName || "여행"}에서의 추억을 공유합니다.`,
          files: [file],
        });
        if (bookNote) bookNote.textContent = "GIF 사진첩을 공유했습니다.";
      } else {
        downloadBlob(blob, file.name);
        if (bookNote) bookNote.textContent = "이 브라우저는 파일 공유를 지원하지 않아 GIF를 다운로드했습니다.";
      }
    } catch (error) {
      if (error?.name !== "AbortError" && bookNote) {
        bookNote.textContent = error.message || "GIF 사진첩을 공유하지 못했습니다.";
      }
    } finally {
      setGifBusy(false);
    }
  });

  visibilitySelect?.addEventListener("change", async () => {
    if (!currentRecord) return;
    visibilitySelect.disabled = true;
    try {
      currentRecord = await request(`/api/v1/travel-records/${currentRecord.travelRecordId}`, {
        method: "PUT",
        body: JSON.stringify({
          title: currentRecord.title || automaticTitle(),
          content: currentRecord.content || automaticContent(),
          rating: currentRecord.rating || null,
          visibility: visibilitySelect.value,
        }),
      });
      toast(visibilitySelect.value === "PUBLIC" ? "전체 공개로 바꿨습니다." : "나만 보기로 바꿨습니다.");
    } catch (error) {
      visibilitySelect.value = currentRecord.visibility || "PRIVATE";
      toast(error.message || "공개 범위를 바꾸지 못했습니다.");
    } finally {
      visibilitySelect.disabled = false;
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
      currentRecord = null;
      images = [];
      resetAlbumRendering();
      renderImages();
      if (deleteSection) deleteSection.hidden = true;
      if (deleteConfirm) deleteConfirm.hidden = true;
      toast("사진첩을 삭제했습니다.");
    } catch (error) {
      toast(error.message || "사진첩을 삭제하지 못했습니다.");
    } finally {
      deleteSubmitButton.disabled = false;
    }
  });

  async function loadAlbumContext() {
    let days = [];
    try {
      days = await request(`/api/v1/trips/${tripId}/days`);
      days.sort((left, right) => Number(left.dayNumber || 0) - Number(right.dayNumber || 0));
      await Promise.all(days.map(async (day) => {
        try {
          day.items = await request(`/api/v1/trip-days/${day.tripDayId}/items`);
          day.items.sort((left, right) => Number(left.sortOrder || 0) - Number(right.sortOrder || 0));
        } catch (error) {
          day.items = [];
        }
      }));
    } catch (error) {
      days = [];
    }
    tripDays = days;

    try {
      bookingSummary = await request(`/api/v1/trips/${tripId}/booking-summary`);
    } catch (error) {
      bookingSummary = { items: [], errors: [{ section: "ALL", message: error.message }] };
    }

    const uniquePlaceIds = [...new Set(days.flatMap((day) => day.items || [])
      .map((item) => item.placeId)
      .filter(Boolean))];
    const places = await Promise.all(uniquePlaceIds.map(async (placeId) => {
      try {
        const response = await request(`/api/v1/places/${placeId}`);
        return response?.place || null;
      } catch (error) {
        return null;
      }
    }));
    routePoints = places.filter((place) => place?.latitude != null && place?.longitude != null)
      .map((place) => ({
        label: place.name || "여행지",
        lat: Number(place.latitude),
        lng: Number(place.longitude),
      }));
  }

  async function loadTripAndRecord() {
    showOnly(loadingEl);
    try {
      currentTrip = await request(`/api/v1/trips/${tripId}`);
      if (titleEl) titleEl.textContent = currentTrip.title || currentTrip.destinationName || "여행 사진첩";
      if (periodEl) {
        periodEl.textContent = [
          currentTrip.destinationName,
          formatPeriod(currentTrip.startDate, currentTrip.endDate),
        ].filter(Boolean).join(" · ");
      }

      if (!window.AllMyTripsTripStatus.isTripFinished(currentTrip)) {
        showOnly(blockedEl);
        return;
      }

      const [records] = await Promise.all([
        request("/api/v1/travel-records/me"),
        loadAlbumContext(),
      ]);
      currentRecord = (records || []).find((record) => Number(record.tripId) === tripId) || null;
      images = normalizeImages(currentRecord?.images);
      if (visibilitySelect) visibilitySelect.value = currentRecord?.visibility || "PRIVATE";
      if (deleteSection) deleteSection.hidden = !currentRecord;
      renderImages();
      showOnly(appEl);
      if (images.length) {
        showWizardStep(3);
        await renderAlbumPage(0);
      } else {
        showWizardStep(1);
      }
    } catch (error) {
      showError(error.message || "여행 사진첩을 불러오지 못했습니다.");
    }
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
