/*
 * 메인 화면 — 영상 재생 방식이 두 가지다.
 * 1) 스크롤: 스크롤 위치로 영상 시간을 제어하는 scroll-scrub
 * 2) 여행 계획하기 클릭: 스크롤 제어를 멈추고 자동 재생한 뒤 계획 화면으로 이동
 */
document.addEventListener("DOMContentLoaded", function () {
  const NEXT_URL = "/trips/new/plan";
  /* 자동 재생 구간별 속도 — 도입부와 마무리는 원속도로 두고 중반만 빠르게 넘긴다. */
  const MIDDLE_START_RATIO = .25;
  const MIDDLE_END_RATIO = .75;
  const MIDDLE_SPEED = 2;

  const world = document.querySelector("[data-scroll-world]");
  const video = document.querySelector("[data-world-video]");
  const intro = document.querySelector("[data-world-intro]");
  const finale = document.querySelector("[data-world-finale]");
  const startButton = document.querySelector("[data-world-start]");
  const skipButton = document.querySelector("[data-world-skip]");
  const progress = document.querySelector("[data-world-progress]");
  const hint = document.querySelector("[data-world-hint]");

  document.body.dataset.pageReady = "true";
  if (!world || !video || !startButton) return;

  let targetTime = 0;
  let currentTime = 0;
  let ticking = false;
  let videoReady = false;
  let lastSeekAt = 0;
  let playing = false;
  let leaving = false;

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
  }

  function smoothstep(value) {
    const x = clamp(value, 0, 1);
    return x * x * (3 - 2 * x);
  }

  function readScroll() {
    ticking = false;
    /* 자동 재생 중에는 스크롤이 영상 시간과 오버레이를 건드리지 않는다. */
    if (playing) return;

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
    if (!playing && videoReady && !video.seeking && timestamp - lastSeekAt >= 1000 / 24) {
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

  function goToPlan() {
    if (leaving) return;
    leaving = true;
    if (window.AllMyTripsLoading) {
      window.AllMyTripsLoading.show();
      window.setTimeout(function () { window.location.href = NEXT_URL; }, 350);
      return;
    }
    window.location.href = NEXT_URL;
  }

  function applyPlaybackSpeed() {
    if (!playing || !Number.isFinite(video.duration) || video.duration <= 0) return;
    const ratio = video.currentTime / video.duration;
    const rate = ratio >= MIDDLE_START_RATIO && ratio < MIDDLE_END_RATIO ? MIDDLE_SPEED : 1;
    if (video.playbackRate !== rate) video.playbackRate = rate;
  }

  function playIntro() {
    /*
     * 사용자가 직접 누른 재생이므로 prefers-reduced-motion으로 건너뛰지 않는다.
     * 모션을 줄여야 하는 사용자는 건너뛰기 버튼과 ESC로 즉시 빠져나갈 수 있다.
     */
    playing = true;
    world.classList.add("is-playing");
    document.body.classList.add("home-intro-playing");
    /* 스크롤이 만들어 둔 인라인 스타일을 덮어써야 오버레이가 확실히 사라진다. */
    [intro, finale, hint].forEach(function (layer) {
      layer.style.opacity = 0;
      layer.style.pointerEvents = "none";
    });
    skipButton.hidden = false;
    skipButton.focus();

    const playback = video.play();
    /* 자동재생 차단이나 코덱 문제로 재생이 시작되지 않으면 사용자를 붙잡아 두지 않는다. */
    if (playback && typeof playback.catch === "function") playback.catch(goToPlan);
  }

  startButton.addEventListener("click", playIntro);
  skipButton.addEventListener("click", goToPlan);
  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape" && playing) goToPlan();
  });

  function markVideoReady() {
    videoReady = Number.isFinite(video.duration) && video.duration > 0;
    currentTime = 0;
    readScroll();
  }

  video.addEventListener("loadedmetadata", markVideoReady);
  /* 캐시된 영상은 리스너를 붙이기 전에 loadedmetadata가 끝나 있어 스크럽이 동작하지 않는다. */
  if (video.readyState >= HTMLMediaElement.HAVE_METADATA) markVideoReady();
  video.addEventListener("timeupdate", function () {
    if (!playing || !Number.isFinite(video.duration) || video.duration <= 0) return;
    applyPlaybackSpeed();
    progress.style.transform = "scaleX(" + (video.currentTime / video.duration) + ")";
  });
  video.addEventListener("ended", goToPlan);
  video.addEventListener("error", function () {
    world.classList.add("video-unavailable");
    if (playing) goToPlan();
  });

  window.addEventListener("scroll", requestRead, { passive: true });
  window.addEventListener("resize", requestRead);
  readScroll();
  window.requestAnimationFrame(renderVideo);
});
