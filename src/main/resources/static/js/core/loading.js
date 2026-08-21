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

  /*
   * 기다릴 일이 있을 때만 띄운다.
   *
   * 예전에는 링크를 누르거나 요청이 나가는 즉시 화면 전체를 덮고, 최소 1.4초를
   * 채운 뒤에야 사라졌다. 그래서 100ms 만에 끝나는 화면에도 로딩이 1.4초 머물렀고,
   * 어디를 눌러도 로딩이 보인다는 인상을 줬다. 빠른 화면이 오히려 느려 보였다.
   *
   * 이제 SHOW_DELAY 안에 끝나면 아예 띄우지 않는다. 사람이 기다린다고 느끼기 전에
   * 끝난 일에는 로딩이 필요 없다. 한 번 뜬 뒤에는 MINIMUM_VISIBLE만큼은 남겨,
   * 떴다가 곧바로 사라지는 깜빡임을 막는다.
   */
  const SHOW_DELAY = 300;
  const MINIMUM_VISIBLE = 400;

  let activeRequests = 0;
  let shownAt = 0;
  let showTimer = null;
  let hideTimer = null;

  function isVisible() {
    return root.classList.contains("is-active");
  }

  function paint() {
    showTimer = null;
    shownAt = Date.now();
    root.classList.add("is-active");
    root.setAttribute("aria-hidden", "false");
  }

  function show() {
    if (hideTimer) {
      window.clearTimeout(hideTimer);
      hideTimer = null;
    }
    /* 이미 떠 있거나 띄우기로 예약돼 있으면 시계를 다시 돌리지 않는다. */
    if (isVisible() || showTimer) return;
    showTimer = window.setTimeout(paint, SHOW_DELAY);
  }

  function hide() {
    activeRequests = 0;
    /* 아직 뜨기 전이면 없던 일로 한다. 이 경우가 대부분이고, 그래서 조용하다. */
    if (showTimer) {
      window.clearTimeout(showTimer);
      showTimer = null;
      return;
    }
    if (!isVisible()) return;

    const remaining = MINIMUM_VISIBLE - (Date.now() - shownAt);
    if (remaining > 0) {
      hideTimer = window.setTimeout(hide, remaining);
      return;
    }
    root.classList.remove("is-active");
    root.setAttribute("aria-hidden", "true");
    hideTimer = null;
  }

  function hideImmediately() {
    if (showTimer) window.clearTimeout(showTimer);
    if (hideTimer) window.clearTimeout(hideTimer);
    activeRequests = 0;
    showTimer = null;
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
