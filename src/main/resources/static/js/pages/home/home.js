/* 메인 화면 — 스크롤 위치로 영상 시간을 제어하는 scroll-scrub */
document.addEventListener("DOMContentLoaded", function () {
  const tripSearchForm = document.querySelector("[data-ai-trip-search]");
  const tripSearchError = document.querySelector("[data-ai-trip-search-error]");

  if (tripSearchForm) {
    const startDate = tripSearchForm.elements.startDate;
    const endDate = tripSearchForm.elements.endDate;
    const submitButton = tripSearchForm.querySelector(".search-submit");
    const today = new Date().toISOString().slice(0, 10);
    startDate.min = today;
    endDate.min = today;

    function travelDuration() {
      if (!startDate.value || !endDate.value) return 0;
      return Math.round((Date.parse(endDate.value + "T00:00:00") - Date.parse(startDate.value + "T00:00:00")) / 86400000) + 1;
    }

    function updateSearchSubmitState() {
      const destination = tripSearchForm.elements.destination.value.trim();
      const travelers = Number(tripSearchForm.elements.travelers.value);
      const isValid = Boolean(destination)
        && travelDuration() >= 1
        && travelDuration() <= 30
        && Number.isInteger(travelers)
        && travelers >= 1
        && travelers <= 20;
      submitButton.disabled = !isValid;
      if (isValid) tripSearchError.hidden = true;
    }

    startDate.addEventListener("change", function () {
      endDate.min = startDate.value || today;
      if (endDate.value && endDate.value < startDate.value) endDate.value = "";
      updateSearchSubmitState();
    });
    [tripSearchForm.elements.destination, endDate, tripSearchForm.elements.travelers].forEach(function (field) {
      field.addEventListener("input", updateSearchSubmitState);
      field.addEventListener("change", updateSearchSubmitState);
    });
    updateSearchSubmitState();

    tripSearchForm.addEventListener("submit", function (event) {
      event.preventDefault();
      const formData = new FormData(tripSearchForm);
      const destination = String(formData.get("destination") || "").trim();
      const selectedStartDate = String(formData.get("startDate") || "");
      const selectedEndDate = String(formData.get("endDate") || "");
      const travelers = Number(formData.get("travelers"));
      const duration = travelDuration();

      if (!destination || !selectedStartDate || !selectedEndDate || !Number.isInteger(travelers) || travelers < 1 || travelers > 20) {
        tripSearchError.textContent = "여행지, 여행 기간, 여행 인원을 모두 입력해 주세요.";
        tripSearchError.hidden = false;
        return;
      }
      if (duration < 1 || duration > 30) {
        tripSearchError.textContent = "여행 기간은 최대 30일까지 선택할 수 있어요.";
        tripSearchError.hidden = false;
        return;
      }

      tripSearchError.hidden = true;
      const query = new URLSearchParams({ destination: destination, startDate: selectedStartDate, endDate: selectedEndDate, travelers: String(travelers) });
      const nextUrl = "/ai-trip-plan?" + query.toString();
      if (window.AllMyTripsLoading) {
        window.AllMyTripsLoading.show();
        window.setTimeout(function () { window.location.href = nextUrl; }, 350);
        return;
      }
      window.location.href = nextUrl;
    });
  }

  const world = document.querySelector("[data-scroll-world]");
  const video = document.querySelector("[data-world-video]");
  const intro = document.querySelector("[data-world-intro]");
  const finale = document.querySelector("[data-world-finale]");
  const progress = document.querySelector("[data-world-progress]");
  const hint = document.querySelector("[data-world-hint]");

  document.body.dataset.pageReady = "true";
  if (!world || !video) return;

  let targetTime = 0;
  let currentTime = 0;
  let ticking = false;
  let videoReady = false;
  let lastSeekAt = 0;

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
  }

  function smoothstep(value) {
    const x = clamp(value, 0, 1);
    return x * x * (3 - 2 * x);
  }

  function readScroll() {
    const distance = Math.max(1, world.offsetHeight - window.innerHeight);
    const rect = world.getBoundingClientRect();
    const ratio = clamp(-rect.top / distance, 0, 1);

    targetTime = videoReady ? ratio * Math.max(0, video.duration - 0.04) : 0;
    progress.style.transform = "scaleX(" + ratio + ")";

    const introOpacity = 1 - smoothstep(ratio / 0.24);
    intro.style.opacity = introOpacity;
    intro.style.transform = "translateY(" + (-ratio * 36) + "px)";
    intro.style.pointerEvents = introOpacity > 0.55 ? "auto" : "none";

    const finaleOpacity = smoothstep((ratio - 0.72) / 0.2);
    finale.style.opacity = finaleOpacity;
    finale.style.setProperty("--finale-lift", ((1 - finaleOpacity) * 34) + "px");
    finale.style.pointerEvents = finaleOpacity > 0.6 ? "auto" : "none";
    hint.style.opacity = clamp(1 - ratio / 0.12, 0, 1);
    ticking = false;
  }

  function requestRead() {
    if (ticking) return;
    ticking = true;
    window.requestAnimationFrame(readScroll);
  }

  function renderVideo(timestamp) {
    /*
     * 영상은 24fps이므로 60fps마다 seek하면 같은 프레임을 여러 번 디코딩하게 된다.
     * seek를 영상 프레임 속도에 맞춰 제한하고, 목표 지점에 가까워지면 즉시 고정해
     * 불필요한 미세 seek가 계속 쌓이지 않게 한다.
     */
    if (videoReady && !video.seeking && timestamp - lastSeekAt >= 1000 / 24) {
      const difference = targetTime - currentTime;
      currentTime = Math.abs(difference) < 0.025
        ? targetTime
        : currentTime + difference * 0.28;

      if (Math.abs(video.currentTime - currentTime) > 1 / 48) {
        try {
          video.currentTime = currentTime;
          lastSeekAt = timestamp;
        } catch (error) {
          /* 브라우저가 메타데이터를 준비하는 동안의 seek 오류는 다음 프레임에서 재시도 */
        }
      }
    }
    window.requestAnimationFrame(renderVideo);
  }

  video.addEventListener("loadedmetadata", function () {
    videoReady = Number.isFinite(video.duration) && video.duration > 0;
    currentTime = 0;
    readScroll();
  });
  video.addEventListener("error", function () {
    world.classList.add("video-unavailable");
  });

  window.addEventListener("scroll", requestRead, { passive: true });
  window.addEventListener("resize", requestRead);
  readScroll();
  window.requestAnimationFrame(renderVideo);
});
