(function () {
  const root = document.createElement("div");
  root.className = "travel-loader";
  root.setAttribute("role", "status");
  root.setAttribute("aria-live", "polite");
  root.setAttribute("aria-label", "페이지를 불러오는 중");
  root.setAttribute("aria-hidden", "true");
  root.innerHTML = `
    <div class="travel-loader__content">
      <div class="travel-loader__map" aria-hidden="true">
        <span class="travel-loader__pin">●</span>
        <span class="travel-loader__route"></span>
        <span class="travel-loader__plane">✈</span>
      </div>
      <strong>여행을 준비하고 있어요</strong>
      <span>마이티가 가장 좋은 경로를 찾는 중...</span>
      <div class="travel-loader__dots" aria-hidden="true"><i></i><i></i><i></i></div>
    </div>`;
  document.body.appendChild(root);

  const minimumDuration = 1400;
  let activeRequests = 0;
  let shownAt = 0;
  let hideTimer = null;

  function show() {
    if (!root.classList.contains("is-active")) shownAt = Date.now();
    if (hideTimer) window.clearTimeout(hideTimer);
    root.classList.add("is-active");
    root.setAttribute("aria-hidden", "false");
  }

  function hide() {
    activeRequests = 0;
    const remaining = minimumDuration - (Date.now() - shownAt);
    if (remaining > 0 && root.classList.contains("is-active")) {
      hideTimer = window.setTimeout(hide, remaining);
      return;
    }
    root.classList.remove("is-active");
    root.setAttribute("aria-hidden", "true");
    hideTimer = null;
  }

  function hideImmediately() {
    if (hideTimer) window.clearTimeout(hideTimer);
    activeRequests = 0;
    hideTimer = null;
    root.classList.remove("is-active");
    root.setAttribute("aria-hidden", "true");
  }

  document.addEventListener("click", function (event) {
    const link = event.target.closest("a[href]");
    const route = event.target.closest("[data-route]:not(body)");
    if (route || (link && link.target !== "_blank" && !link.href.startsWith("javascript:"))) show();
  }, true);

  document.addEventListener("submit", function (event) {
    if (!event.target.matches("[data-no-global-loading]")) show();
  }, true);

  const originalFetch = window.fetch;
  if (originalFetch) {
    window.fetch = function () {
      const requestOptions = arguments[1];
      if (requestOptions && requestOptions.allMyTripsLoading === false) {
        return originalFetch.apply(this, arguments);
      }
      activeRequests += 1;
      show();
      return originalFetch.apply(this, arguments).finally(function () {
        activeRequests -= 1;
        if (activeRequests <= 0) hide();
      });
    };
  }

  window.addEventListener("pageshow", hideImmediately);
  window.AllMyTripsLoading = { show, hide };
})();
