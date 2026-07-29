/* 메인 화면 — 스크롤 위치로 영상 시간을 제어하는 scroll-scrub */
document.addEventListener("DOMContentLoaded", function () {
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
