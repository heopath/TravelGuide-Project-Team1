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
    if (event.target.closest("[data-no-global-loading]")) return;
    const link = event.target.closest("a[href]");
    const route = event.target.closest("[data-route]:not(body)");
    /*
     * 화면이 실제로 넘어가는 링크에만 로딩을 띄운다.
     *
     * 예전에는 `javascript:`로 시작하는지만 봤는데, 대소문자를 섞거나(`JavaScript:`)
     * `data:`·`vbscript:` 같은 다른 스킴이면 그냥 통과했다. 브라우저가 정규화해 주는
     * protocol로 http·https만 받는다. (CodeQL js/incomplete-url-scheme-check)
     */
    const navigates = link && link.target !== "_blank"
      && (link.protocol === "http:" || link.protocol === "https:");
    if (route || navigates) show();
  }, true);

  document.addEventListener("submit", function (event) {
    if (event.target.matches("[data-no-global-loading]")) return;
    show();

    /*
     * 화면이 실제로 넘어가는 제출에만 로딩을 남긴다.
     *
     * JS가 처리하는 폼은 preventDefault를 부르는데, 그 폼이 allMyTripsLoading:false로
     * 요청하면 아래 fetch 감싸기를 건너뛰므로 로더를 끌 사람이 아무도 없었다. 관리자
     * 상품·옵션·시간대 등록에서 로딩 화면이 뜬 채로 멈춘 것이 이 때문이다.
     *
     * 이 리스너는 capture라 페이지 핸들러보다 먼저 돈다. 그래서 여기서는 아직
     * preventDefault 여부를 알 수 없다. 이벤트가 다 돈 뒤에 판정한다.
     *
     * 추적 중인 요청이 있으면 손대지 않는다. 그쪽은 끝날 때 알아서 끈다.
     */
    window.setTimeout(function () {
      if (event.defaultPrevented && activeRequests <= 0) hideImmediately();
    }, 0);
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
