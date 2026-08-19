/* 여행 일정 편집: DAY 일정, 카카오 지도, 장소 검색 */
document.addEventListener("DOMContentLoaded", function () {
  const requestedTripId = Number(document.body.dataset.tripId);
  const pathTripIdMatch = window.location.pathname.match(/\/trips\/(\d+)(?:\/|$)/);
  const pathTripId = Number(pathTripIdMatch?.[1]);
  let activeTripId = Number.isInteger(requestedTripId) && requestedTripId > 0
    ? requestedTripId
    : Number.isInteger(pathTripId) && pathTripId > 0 ? pathTripId : null;
  let activeDay = null;
  let activeItems = [];
  let map = null;
  let expandedMap = null;
  let placesService = null;
  let routeVisible = true;
  let routeLine = null;
  let segmentRouteLines = [];
  let mapOverlays = [];
  let expandedMapOverlays = [];
  let expandedRouteLine = null;
  let expandedSegmentRouteLines = [];
  let searchPreviewOverlay = null;
  let lastSearchResults = [];
  let favoritePlaces = null;
  let favoritePlacesRequest = null;
  let recommendedPlaces = null;
  let recommendedPlacesRequest = null;
  let activePlaceSource = "search";
  let activeTrip = null;
  let savingTrip = false;
  let scheduleDays = [];
  let lastRouteResult = null;
  let lastOptimizationCriterion = "TIME";
  let optimizationPreviewKey = "";
  let allScheduleVisible = false;
  let activeScheduleCalculation = new Map();
  const maxItineraryItemsPerDay = 5;
  const aiRecommendationDurationMinutes = 120;
  const dayMinutes = 24 * 60;
  const itineraryItemLimitMessage = "하루 일정은 최대 5개까지 추가할 수 있습니다.";
  const placeCategoryNames = new Map();
  const placeCategoryStorageKey = "allMyTrips.kakaoPlaceCategoryNames";
  const segmentModeStorageKey = "allMyTrips.segmentTravelModes";
  const dayTransportModeStorageKey = "allMyTrips.dayTransportMode";
  const segmentModes = loadSegmentModes();
  const segmentRouteResults = new Map();
  const segmentRouteRequests = new Map();
  const placeRoutePreviewCache = new Map();
  const placeRoutePreviewTargets = new WeakMap();
  let globalTransportMode = "";
  const travelModeOptions = {
    walk: {label: "도보", icon: "🚶", color: "#3b9cff", endpoint: "/api/v1/routes/walk"},
    transit: {label: "대중교통", icon: "🚌", color: "#57a542", endpoint: "/api/v1/routes/transit"},
    car: {label: "자동차", icon: "🚗", color: "#e94b8b", endpoint: "/api/v1/routes/car"},
  };
  const scheduleTimeStorageKey = "tripScheduleTimeOverrides";
  const scheduleDayStartStorageKey = "tripScheduleDayStartTimes";
  let activeTimeEditor = null;
  let activeTimeItem = null;
  let durationGuideBubble = null;
  let durationGuideTarget = null;
  let dismissedDurationGuideKey = "";
  let draggedScheduleItem = null;
  let dropPlaceholder = null;
  let dropTargetScheduleItem = null;
  let dropBeforeTarget = true;
  const pendingReorders = new Map();
  const pendingOptimizationResults = new Map();
  let floatingAddressPopover = null;
  let floatingAddressButton = null;
  let floatingAddressParent = null;

  function loadPlaceCategoryNames() {
    try {
      const saved = JSON.parse(sessionStorage.getItem(placeCategoryStorageKey) || "{}");
      Object.entries(saved).forEach(function ([placeId, categoryName]) {
        if (placeId && categoryName) placeCategoryNames.set(String(placeId), String(categoryName));
      });
    } catch (error) {
      try {
        sessionStorage.removeItem(placeCategoryStorageKey);
      } catch (storageError) {
        // sessionStorage 접근이 차단된 환경에서는 메모리 Map만 사용한다.
      }
    }
  }

  function rememberPlaceCategory(placeId, categoryName) {
    if (!placeId || !categoryName) return;
    placeCategoryNames.set(String(placeId), String(categoryName));
    try {
      const saved = Object.fromEntries(placeCategoryNames.entries());
      sessionStorage.setItem(placeCategoryStorageKey, JSON.stringify(saved));
    } catch (error) {
      // sessionStorage 사용이 제한된 환경에서도 화면 표시 기능은 유지한다.
    }
  }

  function ensureItineraryCapacity(items) {
    if ((items || []).length >= maxItineraryItemsPerDay) {
      throw new Error(itineraryItemLimitMessage);
    }
  }

  loadPlaceCategoryNames();

  const tripList = document.querySelector("[data-trip-list]");
  const title = document.querySelector("[data-schedule-title]");
  const dayTabs = document.querySelector("[data-day-tabs]");
  const dayTabsShell = document.querySelector("[data-day-tabs-shell]");
  const dayPrev = document.querySelector("[data-day-prev]");
  const dayNext = document.querySelector("[data-day-next]");
  const timeline = document.querySelector("[data-timeline]");
  const scheduleWorkspace = document.querySelector(".schedule-workspace");
  const scheduleWorkspaceBody = document.querySelector(".schedule-workspace-body");
  const scheduleMapPanel = document.querySelector(".schedule-workspace-body > .map-panel");
  const period = document.querySelector("[data-schedule-period]");
  const destination = document.querySelector("[data-schedule-destination]");
  const mapContainer = document.querySelector("[data-schedule-map]");
  const mapExpandButton = document.querySelector(".map-expand-button");
  const mapRouteToggle = document.querySelector("[data-toggle-route-map]");
  const optimizeRouteTrigger = document.querySelector("[data-optimize-trigger]");
  const mapModal = document.querySelector("[data-map-modal]");
  const expandedMapContainer = document.querySelector("[data-schedule-map-expanded]");
  const mapStatus = document.querySelector("[data-map-status]");
  const searchForm = document.querySelector("[data-place-search-form]");
  const keywordInput = document.querySelector("[data-place-keyword]");
  const searchResults = document.querySelector("[data-place-results]");
  const placeSourceTabs = Array.from(document.querySelectorAll("[data-place-source]"));
  const placeRoutePreviewObserver = typeof window.IntersectionObserver === "function"
    ? new window.IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        placeRoutePreviewObserver.unobserve(entry.target);
        const place = placeRoutePreviewTargets.get(entry.target);
        if (place) requestPlaceRoutePreview(place, entry.target);
      });
    }, {root: searchResults, rootMargin: "80px 0px"})
    : null;
  const routeToggle = document.querySelector("[data-toggle-route]");
  const backButton = document.querySelector("[data-schedule-back]");
  const aiEmptyCta = document.querySelector("[data-schedule-ai-empty-cta]");
  const placeAddTrigger = document.querySelector("[data-place-add-trigger]");
  const placeAddPopover = document.querySelector("[data-place-add-popover]");
  const placeAddClose = document.querySelector("[data-place-add-close]");
  const placeAddTitle = document.querySelector("[data-place-add-title]");
  const optimizationSummarySlot = document.querySelector("[data-optimization-summary-slot]");
  const moreMenu = document.querySelector("[data-schedule-more]");
  const moreRouteMapButton = document.querySelector("[data-more-route-map]");
  const moreRouteState = document.querySelector("[data-more-route-state]");
  const transportTrigger = document.querySelector("[data-transport-trigger]");
  const transportTriggerIcon = document.querySelector("[data-transport-trigger-icon]");
  const transportTriggerLabel = document.querySelector("[data-transport-trigger-label]");
  const transportSettingsPanel = document.querySelector("[data-transport-settings-panel]");
  const transportModeOptions = Array.from(document.querySelectorAll("[data-transport-mode]"));
  const transportSettingsCancel = document.querySelector("[data-transport-settings-cancel]");
  const transportSettingsApply = document.querySelector("[data-transport-settings-apply]");
  const overviewCarousel = document.querySelector("[data-overview-carousel]");
  const overviewPrev = document.querySelector("[data-overview-prev]");
  const overviewNext = document.querySelector("[data-overview-next]");
  const overviewPager = document.querySelector("[data-overview-pager]");
  let suppressMoreToggleClose = false;
  let overviewGroups = [];
  let overviewPage = 0;
  const overviewPageSize = 3;
  let scheduleTransportInline = null;

  function setOverviewLayout(enabled) {
    scheduleWorkspace?.classList.toggle("is-overview-mode", enabled);
    scheduleWorkspaceBody?.classList.toggle("is-overview-mode", enabled);
    if (enabled) {
      scheduleMapPanel?.style.removeProperty("--schedule-map-top-offset");
      hideDurationGuide();
    }
  }

  function mountTransportControlAboveTimeline() {
    if (!transportTrigger || !transportSettingsPanel || !timeline?.parentElement) return;
    const carousel = timeline.parentElement;
    if (carousel.contains(transportTrigger)) return;
    scheduleTransportInline = document.createElement("div");
    scheduleTransportInline.className = "schedule-transport-inline";
    scheduleTransportInline.append(transportTrigger, transportSettingsPanel);
    carousel.insertBefore(scheduleTransportInline, timeline);
  }

  function alignScheduleMapToFirstItem() {
    if (!scheduleMapPanel || !timeline) return;
    if (window.innerWidth <= 760 || allScheduleVisible) {
      scheduleMapPanel.style.removeProperty("--schedule-map-top-offset");
      return;
    }
    const firstItem = timeline.querySelector(".schedule-item");
    const workspaceBody = scheduleMapPanel.parentElement;
    if (!firstItem || !workspaceBody) {
      scheduleMapPanel.style.removeProperty("--schedule-map-top-offset");
      return;
    }
    const bodyTop = workspaceBody.getBoundingClientRect().top;
    const itemTop = firstItem.getBoundingClientRect().top;
    const offset = Math.max(0, Math.round(itemTop - bodyTop));
    scheduleMapPanel.style.setProperty("--schedule-map-top-offset", offset + "px");
  }

  function insertScheduleSummary(summary) {
    if (!timeline?.parentElement) return;
    if (summary.classList.contains("route-optimization-summary")) {
      const summarySlot = document.querySelector("[data-optimization-summary-slot]");
      if (summarySlot) {
        summarySlot.replaceChildren(summary);
        return;
      }
    }
    const anchor = scheduleTransportInline?.nextSibling || timeline;
    timeline.parentElement.insertBefore(summary, anchor);
  }

  mountTransportControlAboveTimeline();
  window.addEventListener("resize", function () {
    window.requestAnimationFrame(alignScheduleMapToFirstItem);
    window.requestAnimationFrame(positionDurationGuide);
  });

  function updateMoreRouteState() {
    if (!moreRouteState) return;
    moreRouteState.textContent = routeVisible ? "ON" : "OFF";
    moreRouteState.classList.toggle("is-off", !routeVisible);
    moreRouteMapButton?.setAttribute("aria-pressed", String(routeVisible));
  }

  function syncTransportSettings(mode) {
    transportModeOptions.forEach(function (option) {
      const selected = option.dataset.transportMode === mode;
      option.classList.toggle("is-selected", selected);
      option.setAttribute("aria-checked", String(selected));
    });
    if (transportTriggerIcon && mode) {
      transportTriggerIcon.textContent = travelModeOptions[mode]?.icon || "🚌";
    }
    if (transportTriggerLabel) {
      const label = mode && travelModeOptions[mode]
        ? travelModeOptions[mode].label
        : "교통수단 선택";
      transportTriggerLabel.textContent = label;
      transportTrigger?.setAttribute("aria-label", label);
    }
  }

  function closePlaceSearchPanel() {
    if (!placeAddPopover) return;
    placeAddPopover.hidden = true;
    placeAddTrigger?.setAttribute("aria-expanded", "false");
    if (optimizationSummarySlot) optimizationSummarySlot.hidden = false;
  }

  function positionPlaceSearchPanel() {
    if (!placeAddPopover || placeAddPopover.hidden || !placeAddTrigger) return;
    if (window.innerWidth <= 760) {
      placeAddPopover.style.removeProperty("top");
      placeAddPopover.style.removeProperty("right");
      placeAddPopover.style.removeProperty("left");
      return;
    }
    const parent = placeAddPopover.offsetParent || placeAddPopover.parentElement;
    if (!parent) return;
    const triggerRect = placeAddTrigger.getBoundingClientRect();
    const parentRect = parent.getBoundingClientRect();
    const panelWidth = placeAddPopover.offsetWidth;
    const panelLeft = Math.max(0, Math.round(triggerRect.right - parentRect.left - panelWidth));
    placeAddPopover.style.top = Math.round(triggerRect.bottom - parentRect.top + 8) + "px";
    placeAddPopover.style.left = panelLeft + "px";
    placeAddPopover.style.right = "auto";
  }

  function closeMorePanels() {
    if (transportSettingsPanel) transportSettingsPanel.hidden = true;
    transportTrigger?.setAttribute("aria-expanded", "false");
    if (moreMenu) moreMenu.open = false;
  }

  function openTransportSettings() {
    closePlaceSearchPanel();
    transportTrigger?.setAttribute("aria-expanded", "true");
    if (moreMenu?.open) {
      suppressMoreToggleClose = true;
      moreMenu.open = false;
    }
    syncTransportSettings(globalTransportMode);
    if (transportSettingsPanel) transportSettingsPanel.hidden = false;
    positionTransportSettingsPanel();
  }

  function closeTransportSettings() {
    if (transportSettingsPanel) transportSettingsPanel.hidden = true;
    transportTrigger?.setAttribute("aria-expanded", "false");
  }

  function positionTransportSettingsPanel() {
    if (!transportSettingsPanel || transportSettingsPanel.hidden || !transportTrigger) return;
    const parent = transportSettingsPanel.offsetParent || transportSettingsPanel.parentElement;
    if (!parent) return;
    const triggerRect = transportTrigger.getBoundingClientRect();
    const parentRect = parent.getBoundingClientRect();
    transportSettingsPanel.style.left = Math.round(triggerRect.right - parentRect.left + 8) + "px";
    transportSettingsPanel.style.top = Math.round(triggerRect.top - parentRect.top) + "px";
    transportSettingsPanel.style.right = "auto";
  }

  function updateDayNavigation() {
    if (!dayTabs || !dayTabsShell) return;
    const isScrollable = scheduleDays.length > 3;
    dayTabsShell.classList.toggle("is-scrollable", isScrollable);
    if (dayPrev) dayPrev.hidden = !isScrollable;
    if (dayNext) dayNext.hidden = !isScrollable;
    if (!isScrollable) return;
    const maxScroll = Math.max(0, dayTabs.scrollWidth - dayTabs.clientWidth);
    if (dayPrev) dayPrev.disabled = dayTabs.scrollLeft <= 1;
    if (dayNext) dayNext.disabled = dayTabs.scrollLeft >= maxScroll - 1;
  }

  function revealDayButton(button) {
    if (!button || !dayTabs) return;
    button.scrollIntoView({behavior: "smooth", block: "nearest", inline: "center"});
    window.setTimeout(updateDayNavigation, 250);
  }

  function updateFloatingAddressPopover() {
    if (!floatingAddressPopover || !floatingAddressButton || floatingAddressPopover.hidden) return;
    const buttonRect = floatingAddressButton.getBoundingClientRect();
    floatingAddressPopover.style.left = (buttonRect.left + buttonRect.width / 2) + "px";
    floatingAddressPopover.style.top = (buttonRect.bottom + 9) + "px";
  }

  function closeFloatingAddressPopover() {
    if (!floatingAddressPopover) return;
    floatingAddressPopover.hidden = true;
    floatingAddressPopover.classList.remove("is-floating");
    floatingAddressPopover.removeAttribute("style");
    if (floatingAddressParent) floatingAddressParent.appendChild(floatingAddressPopover);
    if (floatingAddressButton) floatingAddressButton.setAttribute("aria-expanded", "false");
    floatingAddressPopover = null;
    floatingAddressButton = null;
    floatingAddressParent = null;
  }

  window.addEventListener("scroll", updateFloatingAddressPopover, true);
  window.addEventListener("resize", updateFloatingAddressPopover);

  function toggleAiEmptyCta(visible) {
    if (aiEmptyCta) aiEmptyCta.hidden = !visible;
  }

  function setRouteToggleLabel(label) {
    if (!routeToggle) return;
    const labelElement = routeToggle.querySelector("strong");
    if (labelElement) {
      labelElement.textContent = label;
    } else {
      routeToggle.textContent = label;
    }
  }

  function updatePlaceAddTitle(day) {
    if (!placeAddTitle) return;
    placeAddTitle.textContent = day?.dayNumber
      ? "DAY " + day.dayNumber + "에 장소 추가"
      : "일정에 장소 추가";
  }

  function showEmpty(container, message) {
    container.replaceChildren();
    const text = document.createElement("p");
    text.className = "schedule-empty";
    text.textContent = message;
    container.appendChild(text);
  }

  function toast(message) {
    if (window.AllMyTripsModal?.showToast) window.AllMyTripsModal.showToast(message);
  }

  function formatRouteDistance(meters) {
    const value = Number(meters) || 0;
    if (value < 1000) return value + "m";
    return (value / 1000).toFixed(1).replace(".0", "") + "km";
  }

  function formatRouteDuration(seconds) {
    const minutes = Math.max(0, Math.round((Number(seconds) || 0) / 60));
    if (minutes < 60) return minutes + "분";
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    return rest ? hours + "시간 " + rest + "분" : hours + "시간";
  }

  function clearRouteDisplay() {
    lastRouteResult = null;
    document.querySelectorAll(".route-optimization-summary, .segment-route-total-summary")
      .forEach(function (element) { element.remove(); });
  }

  function loadSegmentModes() {
    try {
      return new Map(Object.entries(JSON.parse(sessionStorage.getItem(segmentModeStorageKey) || "{}")));
    } catch (error) {
      try { sessionStorage.removeItem(segmentModeStorageKey); } catch (storageError) { /* 메모리 상태만 사용 */ }
      return new Map();
    }
  }

  function dayTransportModeKey(day) {
    return [
      dayTransportModeStorageKey,
      String(activeTripId || "draft"),
      segmentDayKey(day),
    ].join(":");
  }

  function loadDayTransportMode(day) {
    try {
      const mode = sessionStorage.getItem(dayTransportModeKey(day)) || "";
      return travelModeOptions[mode] ? mode : "";
    } catch (error) {
      return "";
    }
  }

  function saveDayTransportMode(day) {
    try {
      const storageKey = dayTransportModeKey(day);
      if (globalTransportMode) sessionStorage.setItem(storageKey, globalTransportMode);
      else sessionStorage.removeItem(storageKey);
    } catch (error) {
      // sessionStorage가 차단되어도 현재 화면의 선택 상태는 유지한다.
    }
  }

  function saveSegmentModes() {
    try {
      sessionStorage.setItem(segmentModeStorageKey, JSON.stringify(Object.fromEntries(segmentModes.entries())));
    } catch (error) {
      // sessionStorage가 차단되어도 현재 화면의 선택 상태는 유지한다.
    }
  }

  function segmentItemKey(item) {
    return String(item?.itineraryItemId || item?.place?.externalPlaceId || item?.title || "");
  }

  function segmentDayKey(day) {
    return String(day?.tripDayId || day?.dayNumber || "draft");
  }

  function segmentRouteKey(fromItem, toItem, day) {
    return [String(activeTripId || "draft"), segmentDayKey(day), segmentItemKey(fromItem), segmentItemKey(toItem)].join(":");
  }

  function dayIsActive(day) {
    if (!day || !activeDay) return false;
    if (day.tripDayId != null || activeDay.tripDayId != null) {
      return String(day.tripDayId || "") === String(activeDay.tripDayId || "");
    }
    return String(day.dayNumber || "") === String(activeDay.dayNumber || "");
  }

  function currentSegmentPairs() {
    return activeItems.slice(0, -1).map(function (fromItem, index) {
      const toItem = activeItems[index + 1];
      if (fromItem.place?.latitude == null || fromItem.place?.longitude == null
        || toItem.place?.latitude == null || toItem.place?.longitude == null) return null;
      return {fromItem, toItem, key: segmentRouteKey(fromItem, toItem, activeDay)};
    }).filter(Boolean);
  }

  function pruneSegmentRouteState() {
    if (!activeDay) return;
    const currentPrefix = [String(activeTripId || "draft"), segmentDayKey(activeDay), ""].join(":");
    const validKeys = new Set(currentSegmentPairs().map(function (pair) { return pair.key; }));
    let modesChanged = false;
    Array.from(segmentRouteResults.keys()).forEach(function (key) {
      if (key.startsWith(currentPrefix) && !validKeys.has(key)) segmentRouteResults.delete(key);
    });
    Array.from(segmentModes.keys()).forEach(function (key) {
      if (key.startsWith(currentPrefix) && !validKeys.has(key)) {
        segmentModes.delete(key);
        modesChanged = true;
      }
    });
    if (modesChanged) saveSegmentModes();
  }

  function findSegmentControl(key) {
    return Array.from(timeline.querySelectorAll("[data-route-segment-key]"))
      .find(function (element) { return element.dataset.routeSegmentKey === key; });
  }

  function createRouteModeIcon(mode) {
    const icon = document.createElement("span");
    icon.className = "schedule-route-segment-icon";
    icon.setAttribute("aria-hidden", "true");
    icon.textContent = travelModeOptions[mode]?.icon || travelModeOptions.car.icon;
    return icon;
  }

  function createSegmentRouteSummary(fromItem, toItem) {
    const key = segmentRouteKey(fromItem, toItem, activeDay);
    const selectedMode = globalTransportMode || "";
    const state = segmentRouteResults.get(key);
    const selectedMeta = travelModeOptions[selectedMode];
    const wrapper = document.createElement("section");
    const summaryText = document.createElement("span");

    wrapper.className = "schedule-route-segment";
    wrapper.dataset.routeSegmentKey = key;
    if (selectedMode) wrapper.dataset.routeMode = selectedMode;
    if (state?.status) wrapper.dataset.routeStatus = state.status;
    const summary = document.createElement("div");
    summary.className = "schedule-route-segment-summary";
    summaryText.className = "schedule-route-segment-text";

    if (selectedMeta) summary.appendChild(createRouteModeIcon(selectedMode));
    if (state?.status === "success" && selectedMeta) {
      summaryText.textContent = "이동 " + formatRouteDuration(state.data.totalDurationSeconds)
        + " · " + formatRouteDistance(state.data.totalDistanceMeters);
    } else if (state?.status === "loading" && selectedMeta) {
      summaryText.textContent = "이동시간 계산 중...";
    } else if (state?.status === "error" && selectedMeta) {
      summaryText.textContent = state.notFound
        ? "이동 경로를 찾을 수 없습니다."
        : "이동시간을 계산하지 못했습니다.";
    } else if (selectedMeta) {
      summaryText.textContent = "이동 경로를 계산 중입니다.";
    } else {
      summaryText.textContent = "더보기에서 교통수단을 설정해주세요.";
    }
    summary.appendChild(summaryText);
    wrapper.appendChild(summary);
    return wrapper;
  }

  function replaceSegmentRouteSummary(fromItem, toItem, updateTotal = true) {
    const key = segmentRouteKey(fromItem, toItem, activeDay);
    const current = findSegmentControl(key);
    if (current) current.replaceWith(createSegmentRouteSummary(fromItem, toItem));
    if (updateTotal) renderSegmentRouteTotal();
  }

  function renderSegmentRouteTotal() {
    document.querySelectorAll(".segment-route-total-summary").forEach(function (element) { element.remove(); });
    if (!timeline?.parentElement || !globalTransportMode) return;
    const pairs = currentSegmentPairs();
    const completed = pairs.map(function (pair) { return segmentRouteResults.get(pair.key); })
      .filter(function (state) { return state?.status === "success" && state.mode === globalTransportMode; });
    if (!completed.length) return;
    const totalDuration = completed.reduce(function (sum, state) {
      return sum + Number(state.data.totalDurationSeconds || 0);
    }, 0);
    const totalDistance = completed.reduce(function (sum, state) {
      return sum + Number(state.data.totalDistanceMeters || 0);
    }, 0);
    const summary = document.createElement("div");
    summary.className = "segment-route-total-summary";
    summary.textContent = "선택 경로 " + completed.length + "/" + pairs.length + "구간 · 총 "
      + formatRouteDuration(totalDuration) + " · " + formatRouteDistance(totalDistance);
    insertScheduleSummary(summary);
  }

  async function requestSegmentRoute(fromItem, toItem, mode, day = activeDay, options = {}) {
    const key = segmentRouteKey(fromItem, toItem, day);
    const requestKey = key + ":" + mode;
    if (segmentRouteRequests.has(requestKey)) return segmentRouteRequests.get(requestKey);
    const meta = travelModeOptions[mode];
    if (!meta) return;

    segmentRouteResults.set(key, {status: "loading", mode});
    if (dayIsActive(day)) replaceSegmentRouteSummary(fromItem, toItem);
    const requestPromise = api(meta.endpoint, {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({
        startX: Number(fromItem.place.longitude),
        startY: Number(fromItem.place.latitude),
        endX: Number(toItem.place.longitude),
        endY: Number(toItem.place.latitude),
      }),
    }).then(function (data) {
      if (segmentModes.get(key) !== mode) return;
      segmentRouteResults.set(key, {status: "success", mode, data});
    }).catch(function (error) {
      if (segmentModes.get(key) !== mode) return;
      segmentRouteResults.set(key, {
        status: "error",
        mode,
        notFound: error.status === 404,
        message: error.message || "해당 구간의 경로를 찾지 못했습니다.",
      });
    }).finally(function () {
      segmentRouteRequests.delete(requestKey);
      if (dayIsActive(day) && !options.deferUiRefresh) {
        replaceSegmentRouteSummary(fromItem, toItem);
        refreshScheduleTimeDisplay({refreshMap: true});
      }
    });
    segmentRouteRequests.set(requestKey, requestPromise);
    return requestPromise;
  }

  function restoreSavedSegmentRoutes() {
    currentSegmentPairs().forEach(function (pair) {
      const mode = globalTransportMode;
      if (mode && !segmentRouteResults.has(pair.key)) {
        segmentModes.set(pair.key, mode);
        requestSegmentRoute(pair.fromItem, pair.toItem, mode);
      }
    });
    saveSegmentModes();
  }

  async function applyDayTransportMode(mode) {
    if (!activeDay) throw new Error("적용할 DAY를 선택해주세요.");
    const targetDay = activeDay;
    const items = activeItems.slice();

    globalTransportMode = mode;
    saveDayTransportMode(targetDay);
    syncTransportSettings(mode);
    if (items.length) {
      const currentDayStart = getScheduleDayStartTime(targetDay, items);
      if (currentDayStart) saveScheduleDayStartTime(targetDay, currentDayStart);
    }

    const requests = [];
    items.slice(0, -1).forEach(function (fromItem, index) {
      const toItem = items[index + 1];
      if (fromItem.place?.latitude == null || fromItem.place?.longitude == null
        || toItem.place?.latitude == null || toItem.place?.longitude == null) return;
      const key = segmentRouteKey(fromItem, toItem, targetDay);
      segmentModes.set(key, mode);
      segmentRouteResults.delete(key);
      requests.push(requestSegmentRoute(fromItem, toItem, mode, targetDay, {deferUiRefresh: true}));
    });
    saveSegmentModes();
    await Promise.all(requests);
    if (!dayIsActive(targetDay)) return;
    const calculation = calculateScheduleTimes(items, targetDay);
    await persistCalculatedScheduleTimes(items, targetDay, calculation);
    activeItems = items;
    activeScheduleCalculation = calculation;
    currentSegmentPairs().forEach(function (pair) {
      replaceSegmentRouteSummary(pair.fromItem, pair.toItem, false);
    });
    renderSegmentRouteTotal();
    refreshScheduleTimeDisplay({refreshMap: true});
  }

  function renderRouteSummary(result, criterion, previewOnly) {
    if (!result || !timeline?.parentElement) return;
    document.querySelectorAll(".route-optimization-summary").forEach(function (element) { element.remove(); });

    const summary = document.createElement("div");
    summary.className = "route-optimization-summary";
    summary.style.cssText = "color:#6873c7;font-size:12px;font-weight:900;line-height:1.4;";

    const savedSeconds = Number.isFinite(Number(result.savedDurationSeconds))
      ? Number(result.savedDurationSeconds)
      : Math.max(0, Number(result.originalDurationSeconds || 0) - Number(result.optimizedDurationSeconds || 0));
    const savedMinutes = Math.max(0, Math.round(savedSeconds / 60));
    const preview = document.createElement("span");
    preview.className = "route-optimization-preview";
    const savedDistance = result.originalRouteAvailable
      ? Math.max(0, Number(result.originalDistanceMeters || 0) - Number(result.optimizedDistanceMeters || 0))
      : 0;
    const improvements = [];
    if (savedMinutes > 0) improvements.push(savedMinutes + "분 절약");
    if (savedDistance > 0) improvements.push(formatRouteDistance(savedDistance) + " 단축");
    preview.textContent = improvements.length
      ? "✨ " + improvements.join(" · ")
      : "현재 경로가 이미 최적입니다.";
    summary.appendChild(preview);
    if (previewOnly) summary.classList.add("is-preview-only");
    insertScheduleSummary(summary);
  }

  function renderScheduleTimeSummary(items, day) {
    document.querySelectorAll(".schedule-time-total-summary").forEach(function (element) { element.remove(); });
    if (!timeline?.parentElement) return;
    const summary = document.createElement("div");
    summary.className = "schedule-time-total-summary";
    if (!items.length || !globalTransportMode) {
      summary.textContent = "❗ 교통수단 선택 후 첫 시작시간과 각 장소의 체류시간을 설정하면 일정이 자동 계산됩니다.";
      insertScheduleSummary(summary);
      return;
    }
    const first = activeScheduleCalculation.get(getScheduleTimeKey(items[0]));
    const last = activeScheduleCalculation.get(getScheduleTimeKey(items[items.length - 1]));
    if (!first?.startTime || !last?.endTime || activeScheduleCalculation.size < items.length) {
      summary.textContent = "❗ 교통수단 선택 후 첫 시작시간과 각 장소의 체류시간을 설정하면 일정이 자동 계산됩니다.";
      insertScheduleSummary(summary);
      return;
    }
    const totalStayMinutes = items.reduce(function (total, item) {
      return total + Number(activeScheduleCalculation.get(getScheduleTimeKey(item))?.durationMinutes || 0);
    }, 0);
    const totalTravelMinutes = items.slice(0, -1).reduce(function (total, item, index) {
      return total + Number(getCalculatedSegmentMinutes(item, items[index + 1], day) || 0);
    }, 0);
    summary.textContent = "하루 일정 " + formatTime(first.startTime) + "–" + formatTime(last.endTime)
      + " · 체류 " + formatRouteDuration(totalStayMinutes * 60)
      + " · 이동 " + formatRouteDuration(totalTravelMinutes * 60);
    insertScheduleSummary(summary);
  }

  async function loadOptimizationPreview(day, items) {
    if (!day?.tripDayId || allScheduleVisible) return;
    if (!globalTransportMode) return;
    const places = (items || []).filter(function (item) {
      return item.place?.latitude != null && item.place?.longitude != null;
    });
    if (places.length < 2) return;
    const key = String(day.tripDayId) + ":" + globalTransportMode + ":" + places.map(segmentItemKey).join(",");
    if (optimizationPreviewKey === key) return;
    optimizationPreviewKey = key;
    try {
      const result = await api(
        "/api/v1/trip-days/" + day.tripDayId + "/optimize-route?criterion=TIME&mode="
        + encodeURIComponent(globalTransportMode),
        {method: "POST"}
      );
      if (activeDay?.tripDayId !== day.tripDayId || allScheduleVisible) return;
      renderRouteSummary(result, "TIME", true);
    } catch (error) {
      // 미리보기 실패는 일정 표시를 막지 않고, 사용자가 직접 최적화를 실행할 수 있게 둔다.
    }
  }

  function formatDate(value) {
    if (!value) return "날짜 미정";
    return value.slice(5).replace("-", ".");
  }

  function formatCompactDate(value) {
    if (!value) return "날짜 미정";
    const parts = String(value).split("-");
    return parts.length >= 3 ? Number(parts[1]) + "/" + Number(parts[2]) : String(value);
  }

  function formatCompactPeriod(startDate, endDate) {
    return formatCompactDate(startDate) + "–" + formatCompactDate(endDate);
  }

  function formatTime(value) { return value ? value.slice(0, 5) : ""; }

  function getScheduleTimeKey(item) {
    return String(item?.itineraryItemId || item?.place?.externalPlaceId || item?.title || "");
  }

  function getScheduleTimeOverride(item) {
    return readScheduleTimeOverrides()[getScheduleTimeKey(item)] || {};
  }

  function getScheduleDayStartKey(day) {
    return [
      String(activeTripId || "draft"),
      String(day?.tripDayId || day?.dayNumber || "default"),
    ].join(":");
  }

  function readScheduleDayStartTimes() {
    try {
      return JSON.parse(sessionStorage.getItem(scheduleDayStartStorageKey) || "{}");
    } catch (error) {
      return {};
    }
  }

  function getScheduleDayStartTime(day, items) {
    const list = Array.isArray(items) ? items : [];
    const explicitStart = list.map(function (item) {
      const override = getScheduleTimeOverride(item);
      return override?.autoStart ? "" : override?.startTime || item?.startTime || "";
    }).find(Boolean);
    const stored = readScheduleDayStartTimes()[getScheduleDayStartKey(day)];
    return explicitStart || stored || getItemStartTime(list[0]);
  }

  function saveScheduleDayStartTime(day, startTime) {
    if (!startTime) return;
    const startTimes = readScheduleDayStartTimes();
    startTimes[getScheduleDayStartKey(day)] = startTime;
    sessionStorage.setItem(scheduleDayStartStorageKey, JSON.stringify(startTimes));
  }

  function readScheduleTimeOverrides() {
    try {
      return JSON.parse(sessionStorage.getItem(scheduleTimeStorageKey) || "{}");
    } catch (error) {
      return {};
    }
  }

  function getItemStartTime(item) {
    const override = getScheduleTimeOverride(item);
    return override?.startTime || item.startTime || "";
  }

  function getItemDurationMinutes(item, fallbackMinutes) {
    const override = getScheduleTimeOverride(item);
    const overrideDuration = Number(override?.durationMinutes);
    if (Number.isFinite(overrideDuration) && overrideDuration > 0) {
      return overrideDuration;
    }

    const start = toMinutes(override?.startTime || item?.startTime);
    const end = toMinutes(override?.endTime || item?.endTime);
    if (start !== null && end !== null && end > start) {
      return end - start;
    }

    const fallback = Number(fallbackMinutes);
    return Number.isFinite(fallback) && fallback > 0 ? fallback : null;
  }

  function getItemEndTime(item) {
    const override = getScheduleTimeOverride(item);
    return override?.endTime || item.endTime || addMinutesToTime(getItemStartTime(item), getItemDurationMinutes(item)) || "";
  }

  function minutesToTime(totalMinutes) {
    const value = Math.max(0, Math.min(24 * 60 - 1, Number(totalMinutes) || 0));
    return String(Math.floor(value / 60)).padStart(2, "0") + ":"
      + String(value % 60).padStart(2, "0");
  }

  function addMinutesToTime(startTime, durationMinutes) {
    const start = toMinutes(startTime);
    const duration = Number(durationMinutes);
    if (start === null || !Number.isFinite(duration)) return "";
    return minutesToTime(start + duration);
  }

  function getCalculatedSegmentMinutes(fromItem, toItem, day) {
    if (!globalTransportMode) return null;
    const state = segmentRouteResults.get(segmentRouteKey(fromItem, toItem, day));
    if (state?.status !== "success" || state.mode !== globalTransportMode) return null;
    const seconds = Number(state.data?.totalDurationSeconds);
    return Number.isFinite(seconds) ? Math.max(0, Math.round(seconds / 60)) : null;
  }

  function calculateScheduleTimes(items, day) {
    const calculated = new Map();
    if (!items.length) return calculated;
    const firstStart = getScheduleDayStartTime(day, items);
    let cursor = toMinutes(firstStart);
    if (cursor === null) return calculated;

    for (let index = 0; index < items.length; index += 1) {
      const item = items[index];
      if (index > 0) {
        if (!globalTransportMode) break;
        const previous = items[index - 1];
        const previousDuration = getItemDurationMinutes(previous);
        const travelMinutes = getCalculatedSegmentMinutes(previous, item, day);
        if (previousDuration === null || travelMinutes === null) break;
        cursor += previousDuration + travelMinutes;
      }
      const durationMinutes = getItemDurationMinutes(item);
      const startTime = minutesToTime(cursor);
      const endTime = durationMinutes === null ? "" : minutesToTime(cursor + durationMinutes);
      calculated.set(getScheduleTimeKey(item), {
        startTime,
        endTime,
        durationMinutes,
        autoStart: index > 0,
      });
      if (durationMinutes === null) break;
    }
    return calculated;
  }

  function saveScheduleTime(item, startTime, durationMinutes, endTime) {
    const overrides = readScheduleTimeOverrides();
    overrides[getScheduleTimeKey(item)] = {
      ...(overrides[getScheduleTimeKey(item)] || {}),
      startTime,
      endTime: endTime || "",
      durationMinutes,
    };
    sessionStorage.setItem(scheduleTimeStorageKey, JSON.stringify(overrides));
  }

  function reflowScheduleFromDayStart(items, day, startTime, firstDurationMinutes) {
    const list = Array.isArray(items) ? items : [];
    const overrides = readScheduleTimeOverrides();

    saveScheduleDayStartTime(day, startTime);
    list.forEach(function (currentItem, index) {
      const key = getScheduleTimeKey(currentItem);
      const durationMinutes = index === 0
        ? Number(firstDurationMinutes)
        : getItemDurationMinutes(currentItem);

      if (index === 0) {
        const endTime = addMinutesToTime(startTime, durationMinutes);
        overrides[key] = {
          ...(overrides[key] || {}),
          startTime,
          endTime: endTime || "",
          durationMinutes,
          autoStart: false,
        };
        currentItem.startTime = startTime || null;
        currentItem.endTime = endTime || null;
        return;
      }

      // 뒤쪽 장소는 기존 체류시간만 유지하고, 시작·종료시간은 새 기준으로 다시 만든다.
      overrides[key] = {
        ...(overrides[key] || {}),
        startTime: "",
        endTime: "",
        durationMinutes,
        autoStart: true,
      };
      currentItem.startTime = null;
      currentItem.endTime = null;
    });

    sessionStorage.setItem(scheduleTimeStorageKey, JSON.stringify(overrides));
    return calculateScheduleTimes(list, day);
  }

  function closeTimeEditor() {
    if (activeTimeEditor) {
      activeTimeEditor.remove();
    }

    if (activeTimeItem) {
      activeTimeItem.classList.remove("time-editor-open");
    }

    activeTimeEditor = null;
    activeTimeItem = null;
  }

  function durationGuideKey(item, day) {
    return getScheduleDayStartKey(day) + ":" + getScheduleTimeKey(item);
  }

  function hideDurationGuide() {
    durationGuideBubble?.remove();
    durationGuideTarget?.closest(".schedule-item")?.classList.remove("has-duration-guide");
    durationGuideBubble = null;
    durationGuideTarget = null;
  }

  function positionDurationGuide() {
    if (!durationGuideBubble || !durationGuideTarget?.isConnected || !scheduleWorkspace) return;
    const targetRect = durationGuideTarget.getBoundingClientRect();
    const workspaceRect = scheduleWorkspace.getBoundingClientRect();
    const bubbleRect = durationGuideBubble.getBoundingClientRect();
    const left = workspaceRect.left - bubbleRect.width - 12;
    if (left < 12) {
      durationGuideBubble.hidden = true;
      return;
    }
    durationGuideBubble.hidden = false;
    durationGuideBubble.style.left = Math.round(left + window.scrollX) + "px";
    durationGuideBubble.style.top = Math.round(
      targetRect.top + window.scrollY + (targetRect.height - bubbleRect.height) / 2
    ) + "px";
  }

  function updateDurationGuide() {
    hideDurationGuide();
    if (allScheduleVisible || !activeDay || activeItems.length < 2) return;
    const firstItem = activeItems[0];
    const firstStartTime = getScheduleDayStartTime(activeDay, activeItems);
    if (!firstStartTime || getItemDurationMinutes(firstItem) === null) return;

    const targetIndex = activeItems.findIndex(function (item, index) {
      return index > 0 && getItemDurationMinutes(item) === null;
    });
    if (targetIndex < 0) return;

    const targetItem = activeItems[targetIndex];
    const guideKey = durationGuideKey(targetItem, activeDay);
    if (dismissedDurationGuideKey === guideKey) return;
    const targetRow = timeline?.querySelectorAll(".schedule-item")[targetIndex];
    const targetButton = targetRow?.querySelector(".schedule-item-time");
    if (!targetRow || !targetButton) return;

    const bubble = document.createElement("aside");
    const message = document.createElement("p");
    const closeButton = document.createElement("button");
    bubble.className = "schedule-duration-guide";
    bubble.setAttribute("role", "status");
    bubble.setAttribute("aria-live", "polite");
    bubble.dataset.guideKey = guideKey;
    message.textContent = "체류시간을 설정해주세요";
    closeButton.type = "button";
    closeButton.className = "schedule-duration-guide-close";
    closeButton.setAttribute("aria-label", "체류시간 안내 닫기");
    closeButton.textContent = "×";
    closeButton.addEventListener("click", function () {
      dismissedDurationGuideKey = guideKey;
      hideDurationGuide();
    });
    bubble.append(message, closeButton);
    document.body.appendChild(bubble);
    targetRow.classList.add("has-duration-guide");
    durationGuideBubble = bubble;
    durationGuideTarget = targetButton;
    positionDurationGuide();
  }

  function openTimeEditor(item, timeButton, index) {
    const clickedControl = timeButton.closest(".schedule-time-control");

    if (activeTimeEditor && activeTimeEditor.parentElement === clickedControl) {
      closeTimeEditor();
      window.requestAnimationFrame(updateDurationGuide);
      return;
    }

    hideDurationGuide();
    closeTimeEditor();

    const autoStart = index > 0;
    const calculated = activeScheduleCalculation.get(getScheduleTimeKey(item));
    const currentTime = (autoStart ? calculated?.startTime : getItemStartTime(item)) || "09:00";
    const override = getScheduleTimeOverride(item);
    const [currentHour, currentMinute] = currentTime.split(":");
    const editor = document.createElement("div");

    editor.className = "schedule-time-editor";
    if (autoStart) editor.classList.add("is-auto-start");
    editor.setAttribute("role", "dialog");
    editor.setAttribute("aria-label", "방문 시간 설정");
    editor.innerHTML = `
      <div class="schedule-time-editor-header">
        <strong>방문 시간 설정</strong>
        <button type="button" class="schedule-time-close" aria-label="시간 설정 닫기">×</button>
      </div>
      ${autoStart ? "" : `<div class="schedule-time-picker">
        <div class="schedule-time-spinner" data-time-spinner="hour">
          <button type="button" class="schedule-time-adjust" data-time-action="hour-up" aria-label="시간 증가">⌃</button>
          <input type="text" class="schedule-time-input" data-time-hour inputmode="numeric" maxlength="2" value="${currentHour}" aria-label="시" />
          <button type="button" class="schedule-time-adjust" data-time-action="hour-down" aria-label="시간 감소">⌄</button>
        </div>
        <span class="schedule-time-colon" aria-hidden="true">:</span>
        <div class="schedule-time-spinner" data-time-spinner="minute">
          <button type="button" class="schedule-time-adjust" data-time-action="minute-up" aria-label="분 증가">⌃</button>
          <input type="text" class="schedule-time-input" data-time-minute inputmode="numeric" maxlength="2" value="${currentMinute}" aria-label="분" />
          <button type="button" class="schedule-time-adjust" data-time-action="minute-down" aria-label="분 감소">⌄</button>
        </div>
        <span class="schedule-time-format">24시간</span>
      </div>`}
      ${autoStart ? '<small class="schedule-time-auto-note">첫 장소의 시작시간과 체류시간을 기준으로 자동 계산됩니다.</small>' : ""}
      <div class="schedule-time-options">
        ${autoStart ? "" : '<button type="button" class="schedule-time-quick" data-time-action="plus-thirty">+30분</button>'}
        <select class="schedule-duration-select" data-duration aria-label="체류 시간">
          <option value="30">체류시간 30분</option>
          <option value="60">체류시간 1시간</option>
          <option value="90">체류시간 1시간 30분</option>
          <option value="120">체류시간 2시간</option>
          <option value="150">체류시간 2시간 30분</option>
          <option value="180">체류시간 3시간</option>
          <option value="210">체류시간 3시간 30분</option>
          <option value="240">체류시간 4시간</option>
          <option value="270">체류시간 4시간 30분</option>
          <option value="300">체류시간 5시간</option>
        </select>
      </div>
      <div class="schedule-time-actions">
        <button type="button" class="schedule-time-cancel">취소</button>
        <button type="button" class="schedule-time-save">저장</button>
      </div>
    `;

    const hourInput = editor.querySelector("[data-time-hour]");
    const minuteInput = editor.querySelector("[data-time-minute]");
    const durationSelect = editor.querySelector("[data-duration]");
    durationSelect.value = String(override.durationMinutes || getItemDurationMinutes(item) || 120);

    function padTime(value) { return String(value).padStart(2, "0"); }
    function normalizeHour() {
      if (!hourInput) return;
      let value = Number(hourInput.value);
      if (!Number.isFinite(value)) value = 0;
      hourInput.value = padTime(Math.max(0, Math.min(23, value)));
    }
    function normalizeMinute() {
      if (!minuteInput) return;
      let value = Number(minuteInput.value);
      if (!Number.isFinite(value)) value = 0;
      minuteInput.value = padTime(Math.max(0, Math.min(59, value)));
    }
    function changeHour(amount) {
      if (!hourInput) return;
      hourInput.value = padTime((Number(hourInput.value || 0) + amount + 24) % 24);
    }
    function changeMinute(amount) {
      if (!hourInput || !minuteInput) return;
      const oneDay = 24 * 60;
      let total = Number(hourInput.value || 0) * 60 + Number(minuteInput.value || 0) + amount;
      total = (total + oneDay) % oneDay;
      hourInput.value = padTime(Math.floor(total / 60));
      minuteInput.value = padTime(total % 60);
    }

    editor.addEventListener("click", function (event) {
      event.stopPropagation();
      const actionButton = event.target.closest("[data-time-action]");
      if (!actionButton) return;
      switch (actionButton.dataset.timeAction) {
        case "hour-up": changeHour(1); break;
        case "hour-down": changeHour(-1); break;
        case "minute-up": changeMinute(5); break;
        case "minute-down": changeMinute(-5); break;
        case "plus-thirty": changeMinute(30); break;
        default: break;
      }
    });
    if (hourInput && minuteInput) {
      hourInput.addEventListener("input", function () { hourInput.value = hourInput.value.replace(/\D/g, "").slice(0, 2); });
      minuteInput.addEventListener("input", function () { minuteInput.value = minuteInput.value.replace(/\D/g, "").slice(0, 2); });
      hourInput.addEventListener("blur", normalizeHour);
      minuteInput.addEventListener("blur", normalizeMinute);
    }
    editor.querySelector(".schedule-time-close").addEventListener("click", function () {
      closeTimeEditor();
      window.requestAnimationFrame(updateDurationGuide);
    });
    editor.querySelector(".schedule-time-cancel").addEventListener("click", function () {
      closeTimeEditor();
      window.requestAnimationFrame(updateDurationGuide);
    });
    editor.querySelector(".schedule-time-save").addEventListener("click", async function () {
      const saveTimeButton = editor.querySelector(".schedule-time-save");
      const snapshot = snapshotScheduleTimeState(activeItems);
      normalizeHour();
      normalizeMinute();
      const startTime = autoStart
        ? calculated?.startTime || getItemStartTime(item)
        : hourInput.value + ":" + minuteInput.value;
      const durationMinutes = Number(durationSelect.value);
      const startMinutes = toMinutes(startTime);
      const endMinutes = startMinutes === null ? null : startMinutes + durationMinutes;
      if (startMinutes === null || !Number.isFinite(endMinutes) || endMinutes >= 24 * 60) {
        toast("자정을 넘는 일정은 현재 저장할 수 없습니다. 종료 시각을 자정 이전으로 설정해 주세요.");
        return;
      }
      const endTime = minutesToTime(endMinutes);
      saveTimeButton.disabled = true;
      saveTimeButton.textContent = "저장 중...";
      try {
        if (!autoStart) {
          activeScheduleCalculation = reflowScheduleFromDayStart(
            activeItems,
            activeDay,
            startTime,
            durationMinutes
          );
        } else {
          saveScheduleTime(item, startTime, durationMinutes, endTime);
          item.startTime = startTime || null;
          item.endTime = endTime || null;
          activeScheduleCalculation = calculateScheduleTimes(activeItems, activeDay);
        }
        if (activeScheduleCalculation.size === activeItems.length) {
          const persisted = await persistCalculatedScheduleTimes(activeItems, activeDay, activeScheduleCalculation);
          if (!persisted) await persistScheduleItemTime(item, activeDay, startTime, endTime);
        } else {
          await persistScheduleItemTime(item, activeDay, startTime, endTime);
        }
        closeTimeEditor();
        refreshScheduleTimeDisplay();
        toast("방문 시간이 저장되었습니다.");
      } catch (error) {
        restoreScheduleTimeState(snapshot);
        activeScheduleCalculation = calculateScheduleTimes(activeItems, activeDay);
        saveTimeButton.disabled = false;
        saveTimeButton.textContent = "저장";
        toast(error.message || "시간 정보를 저장하지 못했습니다.");
      }
    });

    clickedControl.appendChild(editor);
    activeTimeEditor = editor;
    activeTimeItem = timeButton.closest(".schedule-item");
    if (activeTimeItem) activeTimeItem.classList.add("time-editor-open");
    requestAnimationFrame(function () {
      if (editor.getBoundingClientRect().right > window.innerWidth - 12) editor.classList.add("align-right");
    });
  }

  async function api(url, options) {
    const response = await fetch(url, {
      credentials: "same-origin",
      allMyTripsLoading: false,
      ...(options || {}),
      headers: { Accept: "application/json", ...(options?.headers || {}) },
    });
    if (response.status === 401) {
      const unauthorized = new Error("로그인하면 저장된 여행에 장소를 추가할 수 있습니다.");
      unauthorized.status = 401;
      throw unauthorized;
    }
    const payload = await response.json().catch(function () { return null; });
    if (!response.ok || !payload?.success) {
      const error = new Error(payload?.message || payload?.detail || "요청을 처리하지 못했습니다.");
      error.status = response.status;
      error.code = payload?.code;
      throw error;
    }
    return payload.data;
  }

  function scheduleItemUpdatePayload(item, day, startTime, endTime) {
    return {
      tripDayId: day?.tripDayId || item.tripDayId,
      itineraryItemId: item.itineraryItemId,
      placeId: item.placeId || item.place?.placeId,
      itemType: item.itemType || "PLACE",
      title: item.title || item.place?.name || "일정",
      startTime: startTime || null,
      endTime: endTime || null,
      sortOrder: item.sortOrder,
      memo: item.memo || "",
      estimatedCost: item.estimatedCost ?? null,
      currencyCode: item.currencyCode || "KRW",
      source: item.source || "MANUAL",
    };
  }

  function snapshotScheduleTimeState(items) {
    return {
      items: (items || []).map(function (item) {
        return { item, startTime: item.startTime ?? null, endTime: item.endTime ?? null };
      }),
      overrides: sessionStorage.getItem(scheduleTimeStorageKey),
      dayStarts: sessionStorage.getItem(scheduleDayStartStorageKey),
      draft: sessionStorage.getItem("tripDraft"),
    };
  }

  function restoreStorageValue(key, value) {
    if (value === null) sessionStorage.removeItem(key);
    else sessionStorage.setItem(key, value);
  }

  function restoreScheduleTimeState(snapshot) {
    if (!snapshot) return;
    snapshot.items.forEach(function (saved) {
      saved.item.startTime = saved.startTime;
      saved.item.endTime = saved.endTime;
    });
    restoreStorageValue(scheduleTimeStorageKey, snapshot.overrides);
    restoreStorageValue(scheduleDayStartStorageKey, snapshot.dayStarts);
    restoreStorageValue("tripDraft", snapshot.draft);
  }

  async function persistScheduleItemTime(item, day, startTime, endTime) {
    item.startTime = startTime || null;
    item.endTime = endTime || null;
    if (day?.tripDayId && item.itineraryItemId) {
      await api("/api/v1/trip-days/" + day.tripDayId + "/items/" + item.itineraryItemId, {
        method: "PUT",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(scheduleItemUpdatePayload(item, day, startTime, endTime)),
      });
      return;
    }
    if (!day?.tripDayId) {
      const draft = readDraft();
      const key = draftDayKey(day);
      const draftItems = draft.scheduleItems?.[key] || [];
      const target = draftItems.find(function (candidate) {
        return getScheduleTimeKey(candidate) === getScheduleTimeKey(item);
      });
      if (target) {
        target.startTime = startTime || null;
        target.endTime = endTime || null;
        draft.scheduleItems[key] = draftItems;
        sessionStorage.setItem("tripDraft", JSON.stringify(draft));
      }
    }
  }

  async function persistCalculatedScheduleTimes(items, day, calculation) {
    if (!Array.isArray(items) || !items.length || !calculation || calculation.size !== items.length
      || items.some(function (item) {
        const result = calculation.get(getScheduleTimeKey(item));
        return !result?.startTime || !result.endTime;
      })) return false;
    const calculatedItems = items.map(function (item) {
      return { item, result: calculation.get(getScheduleTimeKey(item)) };
    });
    if (day?.tripDayId) {
      if (calculatedItems.some(function (entry) { return !entry.item.itineraryItemId; })) {
        throw new Error("저장할 일정 항목 정보를 찾을 수 없습니다.");
      }
      await api("/api/v1/trip-days/" + day.tripDayId + "/schedule-times", {
        method: "PUT",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
          items: calculatedItems.map(function (entry) {
            return {
              itineraryItemId: entry.item.itineraryItemId,
              startTime: entry.result.startTime,
              endTime: entry.result.endTime,
            };
          }),
        }),
      });
    }

    const overrides = readScheduleTimeOverrides();
    items.forEach(function (item, index) {
      const result = calculation.get(getScheduleTimeKey(item));
      if (!result?.startTime || !result.endTime) return;
      overrides[getScheduleTimeKey(item)] = {
        ...(overrides[getScheduleTimeKey(item)] || {}),
        startTime: result.startTime,
        endTime: result.endTime,
        durationMinutes: result.durationMinutes,
        autoStart: index > 0,
      };
      item.startTime = result.startTime;
      item.endTime = result.endTime;
    });
    saveScheduleDayStartTime(day, calculation.get(getScheduleTimeKey(items[0])).startTime);
    sessionStorage.setItem(scheduleTimeStorageKey, JSON.stringify(overrides));
    if (!day?.tripDayId) {
      const draft = readDraft();
      const key = draftDayKey(day);
      const draftItems = draft.scheduleItems?.[key] || [];
      draftItems.forEach(function (item) {
        const result = calculation.get(getScheduleTimeKey(item));
        if (result?.startTime && result.endTime) {
          item.startTime = result.startTime;
          item.endTime = result.endTime;
        }
      });
      draft.scheduleItems[key] = draftItems;
      sessionStorage.setItem("tripDraft", JSON.stringify(draft));
    }
    return true;
  }

  function initMap() {
    if (!window.kakao?.maps) {
      if (mapStatus) mapStatus.textContent = "지도 키 필요";
      return;
    }
    window.kakao.maps.load(function () {
      mapContainer.replaceChildren();
      map = new window.kakao.maps.Map(mapContainer, {
        center: new window.kakao.maps.LatLng(37.5665, 126.9780),
        level: 6,
      });
      placesService = new window.kakao.maps.services.Places();
      if (mapStatus) mapStatus.textContent = "지도 준비 완료";
      refreshMap();
    });
  }

  function clearOverlays(overlays) {
    overlays.forEach(function (overlay) { overlay.setMap(null); });
    overlays.length = 0;
  }

  function successfulSegmentRoutes() {
    if (!globalTransportMode) return [];
    return currentSegmentPairs().map(function (pair) {
      const state = segmentRouteResults.get(pair.key);
      return state?.status === "success" && state.mode === globalTransportMode ? {pair, state} : null;
    }).filter(Boolean);
  }

  function drawSegmentRouteLines(targetMap, lines, bounds) {
    successfulSegmentRoutes().forEach(function ({state}) {
      const meta = travelModeOptions[state.mode];
      const path = (state.data.points || []).map(function (point) {
        return new window.kakao.maps.LatLng(Number(point.latitude), Number(point.longitude));
      }).filter(function (point) {
        return Number.isFinite(point.getLat()) && Number.isFinite(point.getLng());
      });
      if (path.length < 2) return;
      path.forEach(function (point) { bounds.extend(point); });
      const line = new window.kakao.maps.Polyline({
        path,
        strokeWeight: 5,
        strokeColor: meta?.color || "#6372df",
        strokeOpacity: .92,
        strokeStyle: "solid",
      });
      line.setMap(targetMap);
      lines.push(line);
    });
  }

  function refreshMap() {
    if (!map) return;
    clearOverlays(mapOverlays);
    if (routeLine) {
      routeLine.setMap(null);
      routeLine = null;
    }
    clearOverlays(segmentRouteLines);
    const mapped = activeItems.filter(function (item) {
      return item.place?.latitude && item.place?.longitude;
    });
    if (!mapped.length) return;
    const bounds = new window.kakao.maps.LatLngBounds();
    const path = [];
    mapped.forEach(function (item, index) {
      const position = new window.kakao.maps.LatLng(Number(item.place.latitude), Number(item.place.longitude));
      bounds.extend(position);
      path.push(position);
      const marker = document.createElement("button");
      marker.type = "button";
      marker.className = "number-map-marker";
      marker.textContent = String(index + 1);
      marker.title = item.title;
      marker.addEventListener("click", function () {
        selectItem(item);
      });
      mapOverlays.push(new window.kakao.maps.CustomOverlay({
        map,
        position,
        content: marker,
        yAnchor: 1,
      }));
    });
    const hasDetailedRoutes = successfulSegmentRoutes().length > 0;
    if (routeVisible && path.length > 1) {
      routeLine = new window.kakao.maps.Polyline({
        path,
        strokeWeight: hasDetailedRoutes ? 3 : 4,
        strokeColor: hasDetailedRoutes ? "#cbd1ed" : "#6372df",
        strokeOpacity: hasDetailedRoutes ? .72 : .85,
        strokeStyle: hasDetailedRoutes ? "shortdash" : "solid",
      });
      routeLine.setMap(map);
      drawSegmentRouteLines(map, segmentRouteLines, bounds);
    }
    map.setBounds(bounds);
    refreshExpandedMap();
  }

  function refreshExpandedMap() {
    if (!expandedMap) return;
    clearOverlays(expandedMapOverlays);
    if (expandedRouteLine) {
      expandedRouteLine.setMap(null);
      expandedRouteLine = null;
    }
    clearOverlays(expandedSegmentRouteLines);
    const mapped = activeItems.filter(function (item) {
      return item.place?.latitude && item.place?.longitude;
    });
    if (!mapped.length) return;
    const bounds = new window.kakao.maps.LatLngBounds();
    const path = [];
    mapped.forEach(function (item, index) {
      const position = new window.kakao.maps.LatLng(Number(item.place.latitude), Number(item.place.longitude));
      bounds.extend(position);
      path.push(position);
      const marker = document.createElement("button");
      marker.type = "button";
      marker.className = "number-map-marker";
      marker.textContent = String(index + 1);
      marker.title = item.title;
      marker.addEventListener("click", function () {
        selectItem(item);
      });
      expandedMapOverlays.push(new window.kakao.maps.CustomOverlay({
        map: expandedMap,
        position,
        content: marker,
        yAnchor: 1,
      }));
    });
    const hasDetailedRoutes = successfulSegmentRoutes().length > 0;
    if (routeVisible && path.length > 1) {
      expandedRouteLine = new window.kakao.maps.Polyline({
        path,
        strokeWeight: hasDetailedRoutes ? 3 : 4,
        strokeColor: hasDetailedRoutes ? "#cbd1ed" : "#6372df",
        strokeOpacity: hasDetailedRoutes ? .72 : .85,
        strokeStyle: hasDetailedRoutes ? "shortdash" : "solid",
      });
      expandedRouteLine.setMap(expandedMap);
      drawSegmentRouteLines(expandedMap, expandedSegmentRouteLines, bounds);
    }
    expandedMap.setBounds(bounds);
  }

  function closeMapModal() {
    if (!mapModal) return;
    mapModal.hidden = true;
    mapModal.setAttribute("aria-hidden", "true");
    document.body.classList.remove("map-modal-open");
  }

  function openMapModal() {
    if (!mapModal || !expandedMapContainer) return;
    if (!map) { toast("지도를 불러온 뒤 크게 볼 수 있습니다."); return; }
    closePlaceSearchPanel();
    closeMorePanels();
    mapModal.hidden = false;
    mapModal.setAttribute("aria-hidden", "false");
    document.body.classList.add("map-modal-open");
    window.setTimeout(function () {
      if (!expandedMap) {
        expandedMap = new window.kakao.maps.Map(expandedMapContainer, {
          center: map.getCenter(),
          level: map.getLevel(),
        });
      } else {
        expandedMap.relayout();
      }
      refreshExpandedMap();
    }, 0);
  }

  async function hydrateItems(items) {
    return Promise.all(items.map(async function (item) {
      if (!item.placeId) return item;
      try {
        const detail = await api("/api/v1/places/" + item.placeId);
        return {...item, place: detail.place};
      } catch (error) {
        return item;
      }
    }));
  }

  function updateScheduleTimeControl(timeControl, item, index, day) {
    const timeButton = timeControl?.querySelector(".schedule-item-time");
    if (!timeButton) return;
    const calculated = activeScheduleCalculation.get(getScheduleTimeKey(item));
    const savedDayStart = index === 0 ? getScheduleDayStartTime(day, [item]) : "";
    const startTime = calculated?.startTime || getItemStartTime(item) || savedDayStart;
    const durationMinutes = calculated?.durationMinutes ?? getItemDurationMinutes(item);
    const endTime = calculated?.endTime || getItemEndTime(item) || addMinutesToTime(startTime, durationMinutes);
    const autoStart = Boolean(calculated?.autoStart);
    const hasCompleteTime = Boolean(startTime && endTime);

    timeButton.replaceChildren();
    timeButton.classList.toggle("is-complete", hasCompleteTime);
    timeButton.classList.toggle("is-auto", autoStart);
    timeControl.classList.toggle("is-complete", hasCompleteTime);
    if (hasCompleteTime) {
      const startLabel = document.createElement("span");
      const endLabel = document.createElement("span");
      startLabel.className = "schedule-time-start";
      endLabel.className = "schedule-time-end";
      startLabel.textContent = formatTime(startTime);
      endLabel.textContent = "~ " + formatTime(endTime);
      timeButton.append(startLabel, endLabel);
    } else {
      timeButton.textContent = index > 0 ? "자동 계산" : "시간 설정";
    }
    timeButton.title = autoStart
      ? "첫 장소 시작시간과 체류시간으로 자동 계산됩니다."
      : "방문 시간과 체류시간 설정";
  }

  function refreshScheduleTimeDisplay(options = {}) {
    activeScheduleCalculation = calculateScheduleTimes(activeItems, activeDay);
    const rows = timeline?.querySelectorAll(".schedule-item") || [];
    activeItems.forEach(function (item, index) {
      updateScheduleTimeControl(rows[index]?.querySelector(".schedule-time-control"), item, index, activeDay);
    });
    renderScheduleTimeSummary(activeItems, activeDay);
    window.requestAnimationFrame(updateDurationGuide);
    if (options.refreshMap) refreshMap();
    window.dispatchEvent(new CustomEvent("allmytrips:schedule-changed"));
  }

  function createScheduleItem(item, index, day) {
    const row = document.createElement("article");
    row.className = "schedule-item";
    const dragHandle = document.createElement("button");
    const order = document.createElement("span");
    const copy = document.createElement("div");
    const name = document.createElement("strong");
    const meta = document.createElement("small");
    const actions = document.createElement("div");
    const deleteButton = document.createElement("button");
    const timeButton = document.createElement("button");
    const categoryTag = document.createElement("span");
    const address = document.createElement("small");
    const addressControl = document.createElement("span");
    const addressButton = document.createElement("button");
    const addressPopover = document.createElement("span");
    const titleLine = document.createElement("div");
    const timeControl = document.createElement("div");
    dragHandle.type = "button";
    dragHandle.className = "schedule-drag-handle";
    dragHandle.textContent = "⠿";
    dragHandle.title = "일정 순서 이동";
    dragHandle.setAttribute("aria-label", "일정 순서 이동");
    const canReorder = Boolean(!allScheduleVisible && day?.tripDayId && item.itineraryItemId
      && String(day.tripDayId) === String(activeDay?.tripDayId));
    dragHandle.disabled = false;
    order.textContent = index + 1;
    name.textContent = item.title || "일정";
    meta.textContent = [item.memo].filter(Boolean).join(" · ");
    timeButton.type = "button";
    timeButton.className = "schedule-item-time";
    timeButton.addEventListener("click", function () { openTimeEditor(item, timeButton, index); });
    timeControl.className = "schedule-time-control";
    timeControl.appendChild(timeButton);
    updateScheduleTimeControl(timeControl, item, index, day);
    deleteButton.type = "button";
    deleteButton.textContent = "삭제";
    deleteButton.className = "schedule-item-delete";
    deleteButton.addEventListener("click", function () { deleteItem(item, day); });
    categoryTag.className = "schedule-item-tag";
    categoryTag.textContent = getPlaceCategoryLabel(item.place);
    address.className = "schedule-item-location";
    address.textContent = item.place?.address || "";
    addressControl.className = "schedule-item-address-control";
    addressButton.type = "button";
    addressButton.className = "schedule-item-address-toggle";
    addressButton.textContent = "주소";
    addressButton.disabled = !address.textContent;
    addressButton.setAttribute("aria-expanded", "false");
    addressButton.addEventListener("click", function (event) {
      event.stopPropagation();
      if (floatingAddressPopover === addressPopover && !addressPopover.hidden) {
        closeFloatingAddressPopover();
        return;
      }
      closeFloatingAddressPopover();
      floatingAddressPopover = addressPopover;
      floatingAddressButton = addressButton;
      floatingAddressParent = addressControl;
      document.body.appendChild(addressPopover);
      addressPopover.classList.add("is-floating");
      addressPopover.hidden = false;
      addressButton.setAttribute("aria-expanded", "true");
      updateFloatingAddressPopover();
    });
    addressPopover.className = "schedule-item-address-popover";
    addressPopover.textContent = address.textContent || "주소 정보가 없습니다.";
    addressPopover.hidden = true;
    addressControl.append(addressButton, addressPopover);
    titleLine.className = "schedule-item-title-line";
    actions.className = "schedule-item-actions";
    actions.appendChild(deleteButton);
    const tags = document.createElement("div");
    tags.className = "schedule-item-tags";
    tags.append(categoryTag);
    tags.append(addressControl);
    titleLine.append(name, tags);
    copy.append(titleLine);
    if (meta.textContent) copy.append(meta);
    row.append(dragHandle, order, timeControl, copy, actions);
    if (canReorder) attachDragEvents(row);
    return row;
  }

  function ensureDropPlaceholder() {
    if (dropPlaceholder) return dropPlaceholder;
    dropPlaceholder = document.createElement("div");
    dropPlaceholder.className = "schedule-drop-placeholder";
    dropPlaceholder.textContent = "이 장소 앞에 놓기";
    dropPlaceholder.addEventListener("dragover", function (event) {
      event.preventDefault();
      event.dataTransfer.dropEffect = "move";
    });
    dropPlaceholder.addEventListener("drop", completeDrop);
    return dropPlaceholder;
  }

  async function completeDrop(event) {
    event.preventDefault();
    if (!draggedScheduleItem || !dropTargetScheduleItem?.parentElement) return;
    clearRouteDisplay();
    const parent = dropTargetScheduleItem.parentElement;
    const reference = dropBeforeTarget
      ? dropTargetScheduleItem
      : dropTargetScheduleItem.nextSibling;
    parent.insertBefore(draggedScheduleItem, reference);
    const orderIds = Array.from(timeline.querySelectorAll(".schedule-item[data-itinerary-item-id]"))
      .map(function (element) { return Number(element.dataset.itineraryItemId); });
    clearDragState();
    await saveManualOrder(orderIds);
  }

  function clearDragState() {
    if (dropPlaceholder?.parentElement) dropPlaceholder.remove();
    document.querySelectorAll(".schedule-item.is-dragging, .schedule-item.is-drag-over")
      .forEach(function (element) {
        element.classList.remove("is-dragging", "is-drag-over", "is-drop-before", "is-drop-after");
      });
    timeline?.classList.remove("is-reordering");
    draggedScheduleItem = null;
    dropTargetScheduleItem = null;
    dropBeforeTarget = true;
  }

  function attachDragEvents(row) {
    row.draggable = true;
    const handle = row.querySelector(".schedule-drag-handle");
    if (handle) {
      handle.addEventListener("pointerdown", function () {
        row.dataset.dragReady = "true";
      });
      handle.addEventListener("pointerup", function () {
        window.setTimeout(function () { delete row.dataset.dragReady; }, 0);
      });
    }
    row.addEventListener("dragstart", function (event) {
      if (row.dataset.dragReady !== "true") {
        event.preventDefault();
        return;
      }
      draggedScheduleItem = row;
      row.classList.add("is-dragging");
      timeline?.classList.add("is-reordering");
      event.dataTransfer.effectAllowed = "move";
      event.dataTransfer.setData("text/plain", row.dataset.itineraryItemId || "");
    });
    row.addEventListener("dragover", function (event) {
      if (!draggedScheduleItem || draggedScheduleItem === row) return;
      event.preventDefault();
      const rectangle = row.getBoundingClientRect();
      const before = event.clientY < rectangle.top + rectangle.height / 2;
      document.querySelectorAll(".schedule-item.is-drag-over").forEach(function (element) {
        if (element !== row) {
          element.classList.remove("is-drag-over", "is-drop-before", "is-drop-after");
        }
      });
      dropTargetScheduleItem = row;
      dropBeforeTarget = before;
      row.classList.add("is-drag-over");
      row.classList.toggle("is-drop-before", before);
      row.classList.toggle("is-drop-after", !before);
      const placeholder = ensureDropPlaceholder();
      placeholder.textContent = before ? "이 장소 앞에 놓기" : "이 장소 뒤에 놓기";
      row.appendChild(placeholder);
    });
    row.addEventListener("dragleave", function (event) {
      if (!row.contains(event.relatedTarget)) {
        row.classList.remove("is-drag-over", "is-drop-before", "is-drop-after");
        if (dropTargetScheduleItem === row) {
          if (dropPlaceholder?.parentElement) dropPlaceholder.remove();
          dropTargetScheduleItem = null;
          dropBeforeTarget = true;
        }
      }
    });
    row.addEventListener("drop", completeDrop);
    row.addEventListener("dragend", function () {
      delete row.dataset.dragReady;
      clearDragState();
    });
  }

  async function saveManualOrder(orderIds) {
    if (!orderIds.length || !activeDay) return;
    try {
      // 순서를 바꿔도 하루의 기준 시작시간은 유지한다.
      // 이후 장소의 시작시간은 새 순서와 기존 체류시간·이동시간으로 다시 계산한다.
      const preservedDayStartTime = getScheduleDayStartTime(activeDay, activeItems);
      if (preservedDayStartTime) saveScheduleDayStartTime(activeDay, preservedDayStartTime);
      if (activeDay.tripDayId) {
        const dayKey = String(activeDay.tripDayId);
        pendingReorders.set(dayKey, orderIds.slice());
        pendingOptimizationResults.delete(dayKey);
        const orderMap = new Map(orderIds.map(function (id, index) { return [String(id), index]; }));
        activeItems.sort(function (left, right) {
          return orderMap.get(String(left.itineraryItemId)) - orderMap.get(String(right.itineraryItemId));
        });
        activeItems.forEach(function (item, index) { item.sortOrder = index; });
        renderItems(activeItems);
      } else {
        const orderMap = new Map(orderIds.map(function (id, index) { return [String(id), index]; }));
        activeItems.sort(function (left, right) {
          return orderMap.get(String(left.itineraryItemId)) - orderMap.get(String(right.itineraryItemId));
        });
        activeItems.forEach(function (item, index) { item.sortOrder = index; });
        const draft = readDraft();
        draft.scheduleItems = draft.scheduleItems || {};
        draft.scheduleItems[draftDayKey(activeDay)] = activeItems;
        sessionStorage.setItem("tripDraft", JSON.stringify(draft));
        renderItems(activeItems);
      }
    } catch (error) {
      toast(error.message || "일정 순서 저장에 실패했습니다.");
      if (activeDay.tripDayId) {
        const selectedButton = Array.from(dayTabs.querySelectorAll("button"))
          .find(function (button) { return button.classList.contains("selected"); });
        await selectDay(activeDay, selectedButton);
      }
    }
  }

  async function savePendingReorders() {
    for (const [tripDayId, orderIds] of pendingReorders.entries()) {
      await api("/api/v1/trip-days/" + tripDayId + "/items/reorder", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(orderIds),
      });
    }
  }

  function applyPendingOrder(orderIds) {
    applyOrderToItems(activeItems, orderIds);
  }

  function applyOrderToItems(items, orderIds) {
    if (!Array.isArray(orderIds) || !orderIds.length) return;
    const orderMap = new Map(orderIds.map(function (id, index) { return [String(id), index]; }));
    items.sort(function (left, right) {
      return (orderMap.get(String(left.itineraryItemId)) ?? Number.MAX_SAFE_INTEGER)
        - (orderMap.get(String(right.itineraryItemId)) ?? Number.MAX_SAFE_INTEGER);
    });
    items.forEach(function (item, index) { item.sortOrder = index; });
  }

  function restorePendingDayState(day, items) {
    if (!day?.tripDayId) return;
    const dayKey = String(day.tripDayId);
    applyOrderToItems(items, pendingReorders.get(dayKey));
  }

  function getPlaceCategoryLabel(place) {
    if (!place) return "관광지";
    const categoryLabels = {
      ATTRACTION: "관광지",
      RESTAURANT: "음식점",
      CAFE: "카페",
      ACCOMMODATION: "숙소",
      TRANSPORT: "교통",
    };
    const category = place.categoryName
      || place.category_name
      || placeCategoryNames.get(String(place.externalPlaceId))
      || categoryLabels[String(place.category || "").toUpperCase()]
      || "관광지";
    const parts = String(category).split(">").map(function (part) { return part.trim(); }).filter(Boolean);
    return parts[parts.length - 1] || "관광지";
  }

  function renderItems(items) {
    setOverviewLayout(false);
    clearRouteDisplay();
    const orderedItems = items.slice().sort(function (left, right) {
      return (left.sortOrder || 0) - (right.sortOrder || 0);
    });
    timeline.classList.toggle("has-reorder-hint", orderedItems.length > 1);
    activeItems = orderedItems;
    activeScheduleCalculation = calculateScheduleTimes(orderedItems, activeDay);
    pruneSegmentRouteState();
    allScheduleVisible = false;
    if (dayTabsShell) dayTabsShell.hidden = false;
    setRouteToggleLabel("전체 일정 보기");
    if (overviewCarousel) overviewCarousel.classList.remove("is-overview");
    if (overviewPrev) overviewPrev.hidden = true;
    if (overviewNext) overviewNext.hidden = true;
    if (overviewPager) overviewPager.hidden = true;
    timeline.classList.remove("all-days-view");
    timeline.replaceChildren();
    if (!orderedItems.length) {
      hideDurationGuide();
      toggleAiEmptyCta(true);
      showEmpty(timeline, "아직 추가한 장소가 없습니다. 오른쪽에서 장소를 검색해보세요.");
      renderScheduleTimeSummary(orderedItems, activeDay);
      refreshMap();
      refreshActivePlaceSourceResults();
      window.dispatchEvent(new CustomEvent("allmytrips:schedule-changed"));
      return;
    }
    toggleAiEmptyCta(false);
    orderedItems.forEach(function (item, index) {
      const element = createScheduleItem(item, index, activeDay);
      element.dataset.itineraryItemId = item.itineraryItemId || "";
      timeline.appendChild(element);
      const nextItem = orderedItems[index + 1];
      if (nextItem && item.place?.latitude != null && item.place?.longitude != null
        && nextItem.place?.latitude != null && nextItem.place?.longitude != null) {
        timeline.appendChild(createSegmentRouteSummary(item, nextItem));
      }
    });
    if (orderedItems.length > 1) {
      const reorderHint = document.createElement("p");
      const reorderHintIcon = document.createElement("span");
      reorderHint.className = "schedule-reorder-hint";
      reorderHintIcon.className = "schedule-reorder-hint-icon";
      reorderHintIcon.setAttribute("aria-hidden", "true");
      reorderHintIcon.textContent = "⠿";
      reorderHint.append(reorderHintIcon, document.createTextNode(" 버튼으로 일정 순서를 변경할 수 있습니다."));
      timeline.appendChild(reorderHint);
    }
    renderSegmentRouteTotal();
    renderScheduleTimeSummary(orderedItems, activeDay);
    window.requestAnimationFrame(function () {
      alignScheduleMapToFirstItem();
      updateDurationGuide();
    });
    refreshMap();
    window.setTimeout(restoreSavedSegmentRoutes, 0);
    if (orderedItems[0]?.place) selectItem(orderedItems[0]);
    refreshActivePlaceSourceResults();
    window.dispatchEvent(new CustomEvent("allmytrips:schedule-changed"));
  }

  function createOverviewDayCard(group) {
    const card = document.createElement("article");
    const header = document.createElement("header");
    const heading = document.createElement("div");
    const dayLabel = document.createElement("strong");
    const dateLabel = document.createElement("span");
    const summary = document.createElement("span");
    const editButton = document.createElement("button");
    const list = document.createElement("div");

    card.className = "schedule-overview-card";
    if (activeDay && String(activeDay.dayNumber) === String(group.day.dayNumber)) {
      card.classList.add("is-selected");
    }
    header.className = "schedule-overview-card-header";
    heading.className = "schedule-overview-card-heading";
    dayLabel.className = "schedule-overview-day-label";
    dayLabel.textContent = "DAY " + group.day.dayNumber;
    dateLabel.className = "schedule-overview-date";
    dateLabel.textContent = group.day.tripDate ? formatDate(group.day.tripDate) : "날짜 미정";
    heading.append(dayLabel, dateLabel);
    summary.className = "schedule-overview-summary";
    summary.textContent = "장소 " + group.items.length + "개";
    editButton.type = "button";
    editButton.className = "schedule-overview-edit";
    editButton.textContent = "DAY " + group.day.dayNumber + " 편집  ›";
    editButton.addEventListener("click", function () {
      leaveOverviewForDay(group.day);
    });
    header.append(heading, summary, editButton);

    list.className = "schedule-overview-items";
    group.items.slice().sort(function (left, right) {
      return (left.sortOrder || 0) - (right.sortOrder || 0);
    }).forEach(function (item, index, items) {
      const row = document.createElement("div");
      const order = document.createElement("span");
      const copy = document.createElement("div");
      const time = document.createElement("time");
      const name = document.createElement("strong");
      row.className = "schedule-overview-item";
      order.className = "schedule-overview-item-order";
      order.textContent = String(index + 1);
      copy.className = "schedule-overview-item-copy";
      time.textContent = getItemStartTime(item) ? formatTime(getItemStartTime(item)) : "시간 미정";
      name.textContent = item.title || item.place?.name || "일정";
      copy.append(time, name);
      row.append(order, copy);
      list.appendChild(row);
      if (index < items.length - 1) {
        const route = document.createElement("div");
        route.className = "schedule-overview-item-route";
        route.setAttribute("aria-hidden", "true");
        list.appendChild(route);
      }
    });
    if (!group.items.length) {
      const empty = document.createElement("p");
      empty.className = "schedule-overview-empty";
      empty.textContent = "추가한 장소가 없습니다.";
      list.appendChild(empty);
    }
    card.append(header, list);
    return card;
  }

  function renderOverviewPager() {
    if (!overviewPager) return;
    const pageCount = Math.max(1, Math.ceil(overviewGroups.length / overviewPageSize));
    const start = overviewPage * overviewPageSize + 1;
    const end = Math.min(overviewGroups.length, start + overviewPageSize - 1);
    overviewPager.replaceChildren();
    if (overviewGroups.length <= overviewPageSize) {
      overviewPager.hidden = true;
      return;
    }
    overviewPager.hidden = false;
    const label = document.createElement("span");
    label.textContent = start + "–" + end + " / " + overviewGroups.length + "일";
    const dots = document.createElement("span");
    dots.className = "schedule-overview-pager-dots";
    for (let index = 0; index < pageCount; index += 1) {
      const dot = document.createElement("i");
      dot.className = index === overviewPage ? "is-active" : "";
      dot.setAttribute("aria-hidden", "true");
      dots.appendChild(dot);
    }
    overviewPager.append(label, dots);
  }

  function renderOverviewPage() {
    if (!overviewCarousel) return;
    setOverviewLayout(true);
    overviewCarousel.classList.add("is-overview");
    const pageGroups = overviewGroups.slice(
      overviewPage * overviewPageSize,
      overviewPage * overviewPageSize + overviewPageSize,
    );
    timeline.replaceChildren();
    timeline.classList.add("all-days-view");
    pageGroups.forEach(function (group) {
      timeline.appendChild(createOverviewDayCard(group));
    });
    if (!pageGroups.length) showEmpty(timeline, "아직 추가한 장소가 없습니다.");
    const hasMultiplePages = overviewGroups.length > overviewPageSize;
    if (overviewPrev) {
      overviewPrev.hidden = !hasMultiplePages;
      overviewPrev.disabled = overviewPage === 0;
    }
    if (overviewNext) {
      overviewNext.hidden = !hasMultiplePages;
      overviewNext.disabled = overviewPage >= Math.ceil(overviewGroups.length / overviewPageSize) - 1;
    }
    renderOverviewPager();
  }

  function leaveOverviewForDay(day) {
    setOverviewLayout(false);
    allScheduleVisible = false;
    overviewGroups = [];
    overviewPage = 0;
    if (overviewCarousel) overviewCarousel.classList.remove("is-overview");
    if (overviewPrev) overviewPrev.hidden = true;
    if (overviewNext) overviewNext.hidden = true;
    if (overviewPager) overviewPager.hidden = true;
    timeline.classList.remove("all-days-view");
    if (dayTabsShell) dayTabsShell.hidden = false;
    setRouteToggleLabel("전체 일정 보기");
    const selectedButton = Array.from(dayTabs.querySelectorAll("button")).find(function (button) {
      return button.textContent === "DAY " + day.dayNumber;
    });
    if (day.tripDayId) {
      selectDay(day, selectedButton);
    } else {
      activeDay = day;
      dayTabs.querySelectorAll("button").forEach(function (button) {
        button.classList.toggle("selected", button === selectedButton);
      });
      renderDraftDay(day);
    }
  }

  async function renderAllDays(days) {
    setOverviewLayout(true);
    toggleAiEmptyCta(false);
    allScheduleVisible = true;
    if (dayTabsShell) dayTabsShell.hidden = true;
    setRouteToggleLabel("DAY별 편집");
    showEmpty(timeline, "전체 일정을 불러오는 중입니다.");
    try {
      overviewGroups = await Promise.all(days.map(async function (day) {
        const items = day.tripDayId
          ? await hydrateItems(await api("/api/v1/trip-days/" + day.tripDayId + "/items"))
          : (readDraft().scheduleItems?.[draftDayKey(day)] || []);
        restorePendingDayState(day, items);
        return {day, items};
      }));
      activeItems = overviewGroups.reduce(function (all, group) { return all.concat(group.items); }, []);
      overviewPage = Math.min(overviewPage, Math.max(0, Math.ceil(overviewGroups.length / overviewPageSize) - 1));
      renderOverviewPage();
      refreshMap();
      window.dispatchEvent(new CustomEvent("allmytrips:schedule-changed"));
    } catch (error) {
      showEmpty(timeline, error.message);
    }
  }

  async function selectDay(day, selectedButton) {
    clearRouteDisplay();
    activeDay = day;
    globalTransportMode = loadDayTransportMode(day);
    syncTransportSettings(globalTransportMode);
    optimizationPreviewKey = "";
    updatePlaceAddTitle(day);
    dayTabs.querySelectorAll("button").forEach(function (button) { button.classList.toggle("selected", button === selectedButton); });
    revealDayButton(selectedButton);
    showEmpty(timeline, "DAY " + day.dayNumber + " 일정을 불러오는 중입니다.");
    try {
      const items = await hydrateItems(await api("/api/v1/trip-days/" + day.tripDayId + "/items"));
      restorePendingDayState(day, items);
      renderItems(items);
      const pendingOptimization = pendingOptimizationResults.get(String(day.tripDayId));
      if (pendingOptimization) {
        lastRouteResult = pendingOptimization.result;
        lastOptimizationCriterion = pendingOptimization.criterion;
        renderRouteSummary(lastRouteResult, lastOptimizationCriterion);
      } else {
        loadOptimizationPreview(day, activeItems.slice());
      }
    } catch (error) {
      showEmpty(timeline, error.message);
    }
  }

  function renderDays(days) {
    setOverviewLayout(false);
    scheduleDays = days;
    allScheduleVisible = false;
    if (dayTabsShell) dayTabsShell.hidden = false;
    setRouteToggleLabel("전체 일정 보기");
    if (overviewCarousel) overviewCarousel.classList.remove("is-overview");
    if (overviewPrev) overviewPrev.hidden = true;
    if (overviewNext) overviewNext.hidden = true;
    if (overviewPager) overviewPager.hidden = true;
    if (dayTabsShell) dayTabsShell.hidden = false;
    dayTabs.replaceChildren();
    if (!days.length) { showEmpty(timeline, "여행 날짜가 아직 만들어지지 않았습니다."); return; }
    days.forEach(function (day, index) {
      const button = document.createElement("button");
      button.type = "button";
      button.textContent = "DAY " + day.dayNumber;
      button.title = day.tripDate || "";
      if (index === 0) button.classList.add("selected");
      button.addEventListener("click", function () { selectDay(day, button); });
      dayTabs.appendChild(button);
    });
    updateDayNavigation();
    selectDay(days[0], dayTabs.firstElementChild);
  }

  function renderTripList(trips) {
    tripList.replaceChildren();
    if (!trips.length) { showEmpty(tripList, "아직 만든 여행이 없습니다."); return; }
    trips.forEach(function (trip) {
      const button = document.createElement("button");
      const name = document.createElement("strong");
      const destination = document.createElement("span");
      const dates = document.createElement("span");
      button.type = "button";
      button.classList.toggle("selected", trip.tripId === activeTripId);
      // 목록에서 다른 여행으로 전환할 수 있어야 한다. data-route는 navigation.js가 전역 위임으로 처리한다.
      // 초안 미리보기(tripId 없음)와 현재 열려 있는 여행에는 붙이지 않는다.
      if (trip.tripId && trip.tripId !== activeTripId) {
        button.dataset.route = "/trips/" + trip.tripId + "/schedule";
      }
      name.textContent = trip.title;
      destination.className = "trip-destination";
      destination.textContent = trip.destinationName || trip.destinationLabel || trip.destination || "목적지 미정";
      dates.textContent = formatDate(trip.startDate) + "–" + formatDate(trip.endDate);
      dates.className = "trip-dates";
      button.append(name, destination, dates);
      tripList.appendChild(button);
    });
  }

  function readDraft() {
    try { return JSON.parse(sessionStorage.getItem("tripDraft") || "{}"); } catch (error) { return {}; }
  }

  function draftDayKey(day) {
    return day.tripDate || "day-" + day.dayNumber;
  }

  function renderDraftDay(day) {
    const draft = readDraft();
    renderItems(draft.scheduleItems?.[draftDayKey(day)] || []);
  }

  function addDraftPlaceToDay(kakaoPlace) {
    const draft = readDraft();
    const key = draftDayKey(activeDay);
    draft.scheduleItems = draft.scheduleItems || {};
    const items = draft.scheduleItems[key] || [];
    if (items.some(function (item) { return String(item.place?.externalPlaceId) === String(kakaoPlace.id); })) {
      throw new Error("이미 이 DAY에 추가된 장소입니다.");
    }
    ensureItineraryCapacity(items);
    items.push({
      itineraryItemId: "draft-" + kakaoPlace.id + "-" + Date.now(),
      itemType: "PLACE",
      title: kakaoPlace.place_name,
      sortOrder: items.length + 1,
      startTime: null,
      memo: "",
      place: {
        externalProvider: "KAKAO",
        externalPlaceId: kakaoPlace.id,
        name: kakaoPlace.place_name,
        categoryName: kakaoPlace.category_name || "관광지",
        address: kakaoPlace.road_address_name || kakaoPlace.address_name,
        latitude: Number(kakaoPlace.y),
        longitude: Number(kakaoPlace.x),
        phone: kakaoPlace.phone,
      },
    });
    draft.scheduleItems[key] = items;
    sessionStorage.setItem("tripDraft", JSON.stringify(draft));
    renderItems(items);
  }

  function renderDraftSchedule() {
    const basic = readDraft().basic || {};
    if (!basic.destination && !basic.destinationLabel) return false;
    const tripName = basic.title || (basic.destinationLabel || basic.destination) + " 여행";
    title.textContent = tripName;
    if (period) period.textContent = formatCompactPeriod(basic.startDate, basic.endDate);
    if (destination) destination.textContent = (basic.destinationLabel || basic.destination) + " 여행";
    renderTripList([{tripId:null,title:tripName,startDate:basic.startDate,endDate:basic.endDate}]);
    const start = basic.startDate ? new Date(basic.startDate + "T00:00:00") : null;
    const end = basic.endDate ? new Date(basic.endDate + "T00:00:00") : null;
    const count = start && end ? Math.max(1, Math.round((end - start) / 86400000) + 1) : 1;
    const days = Array.from({length:count}, function (_, index) {
      const date = start ? new Date(start.getTime() + index * 86400000).toISOString().slice(0,10) : "";
      return {tripDayId:null,dayNumber:index+1,tripDate:date};
    });
    scheduleDays = days;
    allScheduleVisible = false;
    setRouteToggleLabel("전체 일정 보기");
    if (overviewCarousel) overviewCarousel.classList.remove("is-overview");
    if (overviewPrev) overviewPrev.hidden = true;
    if (overviewNext) overviewNext.hidden = true;
    if (overviewPager) overviewPager.hidden = true;
    dayTabs.replaceChildren();
    days.forEach(function (day, index) {
      const button = document.createElement("button");
      button.type = "button"; button.textContent = "DAY " + day.dayNumber;
      if (!index) button.classList.add("selected");
      button.addEventListener("click", function () {
        activeDay = day;
        globalTransportMode = loadDayTransportMode(day);
        syncTransportSettings(globalTransportMode);
        updatePlaceAddTitle(day);
        dayTabs.querySelectorAll("button").forEach(function (candidate) { candidate.classList.toggle("selected", candidate === button); });
        revealDayButton(button);
        renderDraftDay(day);
      });
      dayTabs.appendChild(button);
    });
    updateDayNavigation();
    activeDay = days[0];
    globalTransportMode = loadDayTransportMode(activeDay);
    syncTransportSettings(globalTransportMode);
    updatePlaceAddTitle(activeDay);
    renderDraftDay(activeDay);
    return true;
  }

  async function loadSchedule() {
    try {
      const trips = await api("/api/v1/trips");
      if (!trips.length) { renderTripList([]); showEmpty(timeline, "여행 계획을 먼저 만들어주세요."); return; }
      if (!activeTripId || !trips.some(function (trip) { return trip.tripId === activeTripId; })) {
        activeTripId = trips[0].tripId;
        window.history.replaceState(null, "", "/trips/" + activeTripId + "/schedule");
      }
      const result = await Promise.all([api("/api/v1/trips/" + activeTripId), api("/api/v1/trips/" + activeTripId + "/days")]);
      activeTrip = result[0];
      favoritePlaces = null;
      favoritePlacesRequest = null;
      recommendedPlaces = null;
      recommendedPlacesRequest = null;
      title.textContent = result[0].title;
      if (period) period.textContent = formatCompactPeriod(result[0].startDate, result[0].endDate);
      if (destination) destination.textContent = (result[0].destinationName || "여행") + " 여행";
      // 사이드바는 내 여행 전체를 보여준다. 활성 여행 하나만 넘기면 목록에서 나머지가 사라진다.
      renderTripList(trips);
      renderDays(result[1]);
    } catch (error) {
      if ((error.status === 401 || error.status === 404) && renderDraftSchedule()) return;
      showEmpty(timeline, error.message);
      showEmpty(tripList, "여행 목록을 불러오지 못했습니다.");
    }
  }

  function searchPlaces(keyword) {
    if (!placesService) { toast("카카오 지도 키를 설정한 뒤 장소를 검색할 수 있습니다."); return; }
    showEmpty(searchResults, "장소를 검색하고 있습니다.");
    placesService.keywordSearch(keyword, function (data, status) {
      if (status !== window.kakao.maps.services.Status.OK) { showEmpty(searchResults, "검색 결과가 없습니다."); return; }
      lastSearchResults = data;
      renderSearchResults(data);
      const bounds = new window.kakao.maps.LatLngBounds();
      data.forEach(function (place) { bounds.extend(new window.kakao.maps.LatLng(place.y, place.x)); });
      map.setBounds(bounds);
      focusSearchPlace(data[0]);
    });
  }

  function focusSearchPlace(place) {
    if (!map || !place) return;
    const position = new window.kakao.maps.LatLng(Number(place.y), Number(place.x));
    map.setCenter(position);
    map.setLevel(4);
    if (searchPreviewOverlay) searchPreviewOverlay.setMap(null);
    const marker = document.createElement("span");
    marker.className = "search-preview-map-marker";
    marker.textContent = "선택";
    marker.title = place.place_name;
    searchPreviewOverlay = new window.kakao.maps.CustomOverlay({map:map,position:position,content:marker,yAnchor:1});
  }

  function routePreviewCoordinates(value) {
    const place = value?.place || value || {};
    const latitude = Number(place.latitude ?? place.y);
    const longitude = Number(place.longitude ?? place.x);
    return Number.isFinite(latitude) && Number.isFinite(longitude)
      ? {latitude, longitude}
      : null;
  }

  function currentPlaceRoutePreviewOrigin() {
    for (let index = activeItems.length - 1; index >= 0; index -= 1) {
      const coordinates = routePreviewCoordinates(activeItems[index]);
      if (coordinates) return coordinates;
    }
    return null;
  }

  function placeRoutePreviewKey(origin, destinationCoordinates) {
    return [origin.latitude, origin.longitude, destinationCoordinates.latitude, destinationCoordinates.longitude]
      .map(function (value) { return Number(value).toFixed(6); })
      .join(":");
  }

  function setPlaceRoutePreviewState(element, state, text) {
    element.dataset.routePreviewState = state;
    element.textContent = text;
  }

  async function requestPlaceRoutePreview(place, element) {
    const origin = currentPlaceRoutePreviewOrigin();
    const destinationCoordinates = routePreviewCoordinates(place);
    if (!origin) {
      setPlaceRoutePreviewState(element, "first", "🚗 첫 장소로 추가됩니다.");
      return;
    }
    if (!destinationCoordinates) {
      setPlaceRoutePreviewState(element, "unavailable", "🚗 자동차 이동정보 없음");
      return;
    }
    const key = placeRoutePreviewKey(origin, destinationCoordinates);
    setPlaceRoutePreviewState(element, "loading", "🚗 이동시간 계산 중...");
    if (!placeRoutePreviewCache.has(key)) {
      placeRoutePreviewCache.set(key, api(travelModeOptions.car.endpoint, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
          startX: origin.longitude,
          startY: origin.latitude,
          endX: destinationCoordinates.longitude,
          endY: destinationCoordinates.latitude,
        }),
      }).then(function (data) {
        return {status: "success", data};
      }).catch(function () {
        return {status: "error"};
      }));
    }
    const result = await placeRoutePreviewCache.get(key);
    if (result.status !== "success") {
      setPlaceRoutePreviewState(element, "unavailable", "🚗 자동차 이동정보 없음");
      return;
    }
    setPlaceRoutePreviewState(
      element,
      "success",
      "🚗 현재 일정에서 약 " + formatRouteDuration(result.data.totalDurationSeconds)
        + " · " + formatRouteDistance(result.data.totalDistanceMeters)
    );
  }

  function attachPlaceRoutePreview(place, element, alreadyAdded) {
    if (alreadyAdded) {
      setPlaceRoutePreviewState(element, "added", "현재 일정에 추가됨");
      return;
    }
    placeRoutePreviewTargets.set(element, place);
    if (placeRoutePreviewObserver) placeRoutePreviewObserver.observe(element);
    else requestPlaceRoutePreview(place, element);
  }

  function clearPlaceRoutePreviewObservers() {
    if (!placeRoutePreviewObserver) return;
    searchResults.querySelectorAll(".place-route-preview").forEach(function (element) {
      placeRoutePreviewObserver.unobserve(element);
    });
  }

  function refreshActivePlaceSourceResults() {
    if (activePlaceSource === "favorites") {
      if (favoritePlaces) renderFavoriteResults(favoritePlaces);
      return;
    }
    if (activePlaceSource === "recommendations") {
      if (recommendedPlaces) renderRecommendedResults(recommendedPlaces);
      return;
    }
    if (lastSearchResults.length) renderSearchResults(lastSearchResults);
  }

  function renderSearchResults(results) {
    clearPlaceRoutePreviewObservers();
    searchResults.replaceChildren();
    results.forEach(function (place) {
      const row = document.createElement("article");
      const copy = document.createElement("div");
      const name = document.createElement("strong");
      const meta = document.createElement("small");
      const routePreview = document.createElement("span");
      const add = document.createElement("button");
      row.className = "place-result";
      row.tabIndex = 0;
      name.textContent = place.place_name;
      meta.textContent = [place.road_address_name || place.address_name, place.category_name, place.phone].filter(Boolean).join(" · ");
      routePreview.className = "place-route-preview";
      add.type = "button"; add.textContent = "DAY에 추가";
      const alreadyAdded = activeItems.some(function (item) {
        return String(item.place?.externalPlaceId) === String(place.id);
      });
      if (alreadyAdded) { add.disabled = true; add.textContent = "추가됨"; }
      row.addEventListener("click", function () { focusSearchPlace(place); });
      row.addEventListener("keydown", function (event) { if (event.key === "Enter") focusSearchPlace(place); });
      add.addEventListener("click", async function (event) {
        event.stopPropagation();
        if (!activeDay) { toast("장소를 추가할 DAY를 먼저 선택해주세요."); return; }
        add.disabled = true; add.textContent = "추가 중";
        try {
          if (activeDay.tripDayId) await addPlaceToDay(place);
          else addDraftPlaceToDay(place);
          add.textContent = "추가됨";
          setPlaceRoutePreviewState(routePreview, "added", "현재 일정에 추가됨");
          toast(place.place_name + "을(를) DAY " + activeDay.dayNumber + "에 추가했습니다.");
        } catch (error) {
          add.disabled = false; add.textContent = "다시 시도"; toast(error.message);
        }
      });
      copy.append(name, meta, routePreview);
      row.append(copy, add);
      searchResults.appendChild(row);
      attachPlaceRoutePreview(place, routePreview, alreadyAdded);
    });
  }

  function favoriteCategoryLabel(category) {
    return {
      ATTRACTION: "관광지",
      RESTAURANT: "음식점",
      CAFE: "카페",
      ACCOMMODATION: "숙소",
      TRANSPORT: "교통",
      SHOPPING: "쇼핑",
    }[category] || category || "장소";
  }

  function storedPlaceAsKakaoPlace(place) {
    return {
      id: place.externalPlaceId || "stored-" + place.placeId,
      place_name: place.name,
      road_address_name: place.address || "",
      address_name: [place.region, place.city].filter(Boolean).join(" "),
      x: place.longitude,
      y: place.latitude,
      phone: place.phone || "",
      category_name: favoriteCategoryLabel(place.category),
      category_group_code: "AT4",
    };
  }

  function renderStoredPlaceResults(places, emptyMessage, sourceClass) {
    clearPlaceRoutePreviewObservers();
    searchResults.replaceChildren();
    if (!places.length) {
      showEmpty(searchResults, emptyMessage);
      return;
    }
    places.forEach(function (place) {
      const row = document.createElement("article");
      const copy = document.createElement("div");
      const name = document.createElement("strong");
      const meta = document.createElement("small");
      const rating = document.createElement("span");
      const routePreview = document.createElement("span");
      const add = document.createElement("button");
      row.className = "place-result place-favorite-result " + sourceClass;
      row.tabIndex = 0;
      name.textContent = place.name;
      meta.textContent = [place.region, place.city, favoriteCategoryLabel(place.category)].filter(Boolean).join(" · ");
      if (Number(place.averageRating) > 0) {
        rating.className = "place-favorite-rating";
        rating.textContent = "★ " + Number(place.averageRating).toFixed(1);
      }
      routePreview.className = "place-route-preview";
      add.type = "button";
      add.textContent = "＋ 일정에 추가";
      const alreadyAdded = activeItems.some(function (item) {
        return Number(item.placeId || item.place?.placeId) === Number(place.placeId);
      });
      if (alreadyAdded) {
        add.disabled = true;
        add.textContent = "추가됨";
      }
      const focusStoredPlace = function () {
        if (place.latitude == null || place.longitude == null) return;
        focusSearchPlace(storedPlaceAsKakaoPlace(place));
      };
      row.addEventListener("click", focusStoredPlace);
      row.addEventListener("keydown", function (event) {
        if (event.key === "Enter") focusStoredPlace();
      });
      add.addEventListener("click", async function (event) {
        event.stopPropagation();
        if (!activeDay) {
          toast("장소를 추가할 DAY를 먼저 선택해주세요.");
          return;
        }
        add.disabled = true;
        add.textContent = "추가 중";
        try {
          await addStoredPlaceToDay(place);
          add.textContent = "추가됨";
          setPlaceRoutePreviewState(routePreview, "added", "현재 일정에 추가됨");
          toast(place.name + "을(를) DAY " + activeDay.dayNumber + "에 추가했습니다.");
        } catch (error) {
          add.disabled = false;
          add.textContent = "다시 시도";
          toast(error.message);
        }
      });
      copy.append(name, meta);
      if (rating.textContent) copy.appendChild(rating);
      copy.appendChild(routePreview);
      row.append(copy, add);
      searchResults.appendChild(row);
      attachPlaceRoutePreview(place, routePreview, alreadyAdded);
    });
  }

  function renderFavoriteResults(places) {
    renderStoredPlaceResults(places, "현재 여행지에 찜한 장소가 없습니다.", "place-favorite-source");
  }

  function renderRecommendedResults(places) {
    renderStoredPlaceResults(places, "현재 여행지에 추천할 장소가 없습니다.", "place-recommendation-source");
  }

  function sortRecommendedPlaces(places) {
    return places.sort(function (left, right) {
      const favoriteDifference = Number(Boolean(right.favorite)) - Number(Boolean(left.favorite));
      const ratingDifference = Number(right.averageRating || 0) - Number(left.averageRating || 0);
      return favoriteDifference || ratingDifference || Number(right.placeId || 0) - Number(left.placeId || 0);
    });
  }

  function normalizeRegionName(value) {
    return String(value || "")
      .trim()
      .replace(/\s+/g, "")
      .replace(/(특별자치도|특별자치시|광역시|특별시|자치시|도|시)$/u, "");
  }

  function currentRecommendationRegion() {
    if (activeTrip?.destinationName) return activeTrip.destinationName.trim();
    const basic = readDraft().basic || {};
    return String(basic.destinationLabel || basic.destination || "").trim();
  }

  function isPlaceInCurrentTripRegion(place) {
    const destinationRegion = normalizeRegionName(currentRecommendationRegion());
    if (!destinationRegion) return false;
    return [place?.region, place?.city].some(function (value) {
      const placeRegion = normalizeRegionName(value);
      return placeRegion && placeRegion === destinationRegion;
    });
  }

  async function loadFavoritePlaces() {
    if (favoritePlaces) {
      renderFavoriteResults(favoritePlaces);
      return favoritePlaces;
    }
    if (favoritePlacesRequest) return favoritePlacesRequest;
    showEmpty(searchResults, "찜한 장소를 불러오는 중입니다.");
    favoritePlacesRequest = (async function () {
      const favorites = await api("/api/v1/favorites?page=0&size=100");
      const places = await Promise.all((favorites || []).map(async function (favorite) {
        try {
          const detail = await api("/api/v1/places/" + favorite.placeId);
          return detail?.place || favorite;
        } catch (_error) {
          return {
            placeId: favorite.placeId,
            name: favorite.placeName,
            category: favorite.category,
            region: favorite.region,
            primaryImageUrl: favorite.primaryImageUrl,
          };
        }
      }));
      favoritePlaces = places.filter(function (place) {
        return place?.placeId && place?.name && isPlaceInCurrentTripRegion(place);
      });
      if (activePlaceSource === "favorites") renderFavoriteResults(favoritePlaces);
      return favoritePlaces;
    })();
    try {
      return await favoritePlacesRequest;
    } catch (error) {
      if (activePlaceSource === "favorites") {
        showEmpty(searchResults, error.message || "찜 목록을 불러오지 못했습니다.");
      }
      throw error;
    } finally {
      favoritePlacesRequest = null;
    }
  }

  async function loadRecommendedPlaces() {
    if (recommendedPlaces) {
      renderRecommendedResults(recommendedPlaces);
      return recommendedPlaces;
    }
    if (recommendedPlacesRequest) return recommendedPlacesRequest;
    showEmpty(searchResults, "현재 여행지의 추천 장소를 불러오는 중입니다.");
    recommendedPlacesRequest = (async function () {
      const region = currentRecommendationRegion();
      if (!region) {
        recommendedPlaces = [];
        if (activePlaceSource === "recommendations") renderRecommendedResults(recommendedPlaces);
        return recommendedPlaces;
      }
      const params = new URLSearchParams({
        page: "0",
        size: "100",
        keyword: normalizeRegionName(region),
      });
      const places = await api("/api/v1/places?" + params.toString());
      recommendedPlaces = sortRecommendedPlaces((places || []).filter(function (place) {
        return place?.placeId && place?.name && isPlaceInCurrentTripRegion(place);
      })).slice(0, 20);
      if (activePlaceSource === "recommendations") renderRecommendedResults(recommendedPlaces);
      return recommendedPlaces;
    })();
    try {
      return await recommendedPlacesRequest;
    } catch (error) {
      if (activePlaceSource === "recommendations") {
        showEmpty(searchResults, error.message || "추천 장소를 불러오지 못했습니다.");
      }
      throw error;
    } finally {
      recommendedPlacesRequest = null;
    }
  }

  function selectPlaceSource(source) {
    activePlaceSource = ["favorites", "recommendations"].includes(source) ? source : "search";
    placeSourceTabs.forEach(function (tab) {
      const selected = tab.dataset.placeSource === activePlaceSource;
      tab.classList.toggle("selected", selected);
      tab.setAttribute("aria-selected", String(selected));
    });
    searchForm.hidden = activePlaceSource !== "search";
    if (activePlaceSource === "favorites") {
      loadFavoritePlaces().catch(function () {});
    } else if (activePlaceSource === "recommendations") {
      loadRecommendedPlaces().catch(function () {});
    } else if (lastSearchResults.length) {
      renderSearchResults(lastSearchResults);
    } else {
      showEmpty(searchResults, "검색 결과가 여기에 표시됩니다.");
    }
  }

  function mapKakaoCategory(place) {
    return {
      AT4: "ATTRACTION",
      CT1: "ATTRACTION",
      FD6: "RESTAURANT",
      CE7: "CAFE",
      AD5: "ACCOMMODATION",
      PK6: "TRANSPORT",
      SW8: "TRANSPORT",
      OL7: "TRANSPORT",
    }[place.category_group_code] || "ATTRACTION";
  }

  async function findOrCreatePlace(kakaoPlace) {
    if (kakaoPlace.category_name) {
      rememberPlaceCategory(kakaoPlace.id, kakaoPlace.category_name);
    }
    const placePayload = {
      externalProvider:"KAKAO", externalPlaceId:kakaoPlace.id, category:mapKakaoCategory(kakaoPlace),
      name:kakaoPlace.place_name, countryCode:"KR", region:kakaoPlace.address_name?.split(" ")[0] || "",
      city:kakaoPlace.address_name?.split(" ")[1] || "", address:kakaoPlace.road_address_name || kakaoPlace.address_name,
      latitude:Number(kakaoPlace.y), longitude:Number(kakaoPlace.x), phone:kakaoPlace.phone, websiteUrl:kakaoPlace.place_url, active:true,
    };
    try {
      return await api("/api/v1/places", {method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(placePayload)});
    } catch (error) {
      if (error.status !== 409 && error.status !== 500) throw error;
      const matches = await api("/api/v1/places?keyword=" + encodeURIComponent(kakaoPlace.place_name) + "&size=20");
      const existing = matches.find(function (place) { return place.externalProvider === "KAKAO" && String(place.externalPlaceId) === String(kakaoPlace.id); });
      if (!existing) throw error;
      return existing;
    }
  }

  async function addPlaceToDay(kakaoPlace) {
    ensureItineraryCapacity(activeItems);
    const place = await findOrCreatePlace(kakaoPlace);
    const nextSortOrder = activeItems.reduce(function (max, item) {
      return Math.max(max, Number(item.sortOrder) || 0);
    }, 0) + 1;
    await api("/api/v1/trip-days/" + activeDay.tripDayId + "/items", {
      method:"POST", headers:{"Content-Type":"application/json"},
      body:JSON.stringify({placeId:place.placeId,itemType:"PLACE",title:place.name,sortOrder:nextSortOrder,currencyCode:"KRW",source:"MANUAL"}),
    });
    const selectedButton = Array.from(dayTabs.querySelectorAll("button")).find(function (button) { return button.classList.contains("selected"); });
    await selectDay(activeDay, selectedButton);
  }

  async function addStoredPlaceToDay(place) {
    const duplicate = activeItems.some(function (item) {
      return Number(item.placeId || item.place?.placeId) === Number(place.placeId);
    });
    if (duplicate) throw new Error("이미 이 DAY에 추가된 장소입니다.");
    ensureItineraryCapacity(activeItems);
    if (!activeDay.tripDayId) {
      addDraftPlaceToDay(storedPlaceAsKakaoPlace(place));
      return;
    }
    const nextSortOrder = activeItems.reduce(function (max, item) {
      return Math.max(max, Number(item.sortOrder) || 0);
    }, 0) + 1;
    await api("/api/v1/trip-days/" + activeDay.tripDayId + "/items", {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({
        placeId: place.placeId,
        itemType: "PLACE",
        title: place.name,
        sortOrder: nextSortOrder,
        currencyCode: "KRW",
        source: "MANUAL",
      }),
    });
    const selectedButton = Array.from(dayTabs.querySelectorAll("button")).find(function (button) {
      return button.classList.contains("selected");
    });
    await selectDay(activeDay, selectedButton);
  }

  function findScheduleDay(dayNumber) {
    const requestedDay = Number(dayNumber);
    return Number.isInteger(requestedDay)
      ? scheduleDays.find(function (day) { return day.dayNumber === requestedDay; })
      : activeDay;
  }

  async function loadScheduleDayItems(day) {
    if (!day?.tripDayId) return [];
    return !allScheduleVisible && day.tripDayId === activeDay?.tripDayId
      ? activeItems
      : await hydrateItems(await api("/api/v1/trip-days/" + day.tripDayId + "/items"));
  }

  function verifiedRecommendationPlaceId(recommendation) {
    const placeId = Number(recommendation?.placeId);
    return Number.isInteger(placeId) && placeId > 0 ? placeId : null;
  }

  function toMinutes(value) {
    const matched = String(value || "").trim().match(/^([01]\\d|2[0-3]):([0-5]\\d)(?::[0-5]\\d)?$/);
    if (!matched) return null;
    return Number(matched[1]) * 60 + Number(matched[2]);
  }

  function overlapsAiRecommendation(recommendation, scheduledItem) {
    const recommendationStart = toMinutes(recommendation?.time);
    const existingWindow = getScheduledItemTimeWindow(scheduledItem);
    if (recommendationStart === null || !existingWindow) return false;
    const recommendationEnd = recommendationStart + aiRecommendationDurationMinutes;
    return recommendationStart < existingWindow.end && existingWindow.start < recommendationEnd;
  }

  // 시간 편집기와 동일하게 24:00은 허용하지 않는다. AI 추천은 기본 2시간을 점유한다.
  function isAiRecommendationOutsideDay(recommendation) {
    const recommendationStart = toMinutes(recommendation?.time);
    return recommendationStart === null
      || recommendationStart + aiRecommendationDurationMinutes >= dayMinutes;
  }

  function getAiRecommendationUnavailableTimePlaceIds(recommendations) {
    return new Set((recommendations || [])
      .filter(isAiRecommendationOutsideDay)
      .map(verifiedRecommendationPlaceId)
      .filter(Boolean));
  }

  function getScheduledItemTimeWindow(scheduledItem) {
    const override = readScheduleTimeOverrides()[getScheduleTimeKey(scheduledItem)];
    const start = toMinutes(override?.startTime || scheduledItem?.startTime);
    if (start === null) return null;

    const overrideDuration = Number(override?.durationMinutes);
    const storedEnd = toMinutes(scheduledItem?.endTime);
    const end = Number.isFinite(overrideDuration) && overrideDuration > 0
      ? start + overrideDuration
      : (storedEnd !== null && storedEnd > start ? storedEnd : start + 120);
    return {start, end};
  }

  async function getAiRecommendationTimeConflicts(recommendations, recommendedDayNumber) {
    const targetDay = findScheduleDay(recommendedDayNumber);
    if (!targetDay?.tripDayId) return new Set();
    const targetItems = await loadScheduleDayItems(targetDay);
    return new Set((recommendations || [])
      .filter(function (recommendation) {
        return targetItems.some(function (item) { return overlapsAiRecommendation(recommendation, item); });
      })
      .map(verifiedRecommendationPlaceId)
      .filter(Boolean));
  }

  async function getAiRecommendationStates(recommendations, recommendedDayNumber) {
    const targetDay = findScheduleDay(recommendedDayNumber);
    if (!targetDay?.tripDayId) return new Set();
    const requestedPlaceIds = new Set((recommendations || [])
      .map(verifiedRecommendationPlaceId)
      .filter(Boolean));
    if (!requestedPlaceIds.size) return new Set();
    const targetItems = await loadScheduleDayItems(targetDay);
    return new Set(targetItems
      .map(function (item) { return Number(item.placeId); })
      .filter(function (placeId) { return requestedPlaceIds.has(placeId); }));
  }

  async function addAiRecommendations(recommendations, recommendedDayNumber) {
    const targetDay = findScheduleDay(recommendedDayNumber);
    if (!targetDay?.tripDayId) {
      throw new Error("추천 일차가 현재 여행에 없어 일정에 추가할 수 없습니다.");
    }
    const targetItems = await loadScheduleDayItems(targetDay);
    const storedPlaceIds = new Set(targetItems.map(function (item) { return Number(item.placeId); }));
    const handledPlaceIds = new Set();
    const timeConflictPlaceIds = await getAiRecommendationTimeConflicts(recommendations, recommendedDayNumber);
    const unavailableTimePlaceIds = getAiRecommendationUnavailableTimePlaceIds(recommendations);
    const result = {added: 0, alreadyAdded: 0, timeConflicts: 0, unavailableTimes: 0, failed: []};
    let nextSortOrder = targetItems.reduce(function (max, item) {
      return Math.max(max, Number(item.sortOrder) || 0);
    }, 0) + 1;

    for (const recommendation of recommendations || []) {
      const placeId = verifiedRecommendationPlaceId(recommendation);
      if (!placeId) {
        result.failed.push(recommendation?.name || "알 수 없는 장소");
        continue;
      }
      if (storedPlaceIds.has(placeId) || handledPlaceIds.has(placeId)) {
        result.alreadyAdded += 1;
        handledPlaceIds.add(placeId);
        continue;
      }
      if (unavailableTimePlaceIds.has(placeId)) {
        result.unavailableTimes += 1;
        handledPlaceIds.add(placeId);
        continue;
      }
      if (timeConflictPlaceIds.has(placeId)) {
        result.timeConflicts += 1;
        handledPlaceIds.add(placeId);
        continue;
      }
      if (targetItems.length + result.added >= maxItineraryItemsPerDay) {
        result.failed.push(recommendation.name || "알 수 없는 장소");
        continue;
      }
      try {
        await api("/api/v1/trip-days/" + targetDay.tripDayId + "/items", {
          method: "POST",
          headers: {"Content-Type": "application/json"},
          body: JSON.stringify({
            placeId,
            itemType: "PLACE",
            title: recommendation.name,
            startTime: recommendation.time || null,
            sortOrder: nextSortOrder,
            memo: recommendation.reason || null,
            currencyCode: "KRW",
            source: "AI"
          })
        });
        nextSortOrder += 1;
        storedPlaceIds.add(placeId);
        handledPlaceIds.add(placeId);
        result.added += 1;
      } catch (error) {
        if (error.code === "ITINERARY_PLACE_ALREADY_ADDED") {
          storedPlaceIds.add(placeId);
          handledPlaceIds.add(placeId);
          result.alreadyAdded += 1;
          continue;
        }
        if (error.code === "ITINERARY_TIME_CONFLICT") {
          if (unavailableTimePlaceIds.has(placeId)) result.unavailableTimes += 1;
          else result.timeConflicts += 1;
          handledPlaceIds.add(placeId);
          continue;
        }
        result.failed.push(recommendation.name || "알 수 없는 장소");
      }
    }

    const selectedButton = Array.from(dayTabs.querySelectorAll("button"))
      .find(function (button) { return button.textContent === "DAY " + targetDay.dayNumber; });
    await selectDay(targetDay, selectedButton);
    if (result.added) toast("DAY " + targetDay.dayNumber + "에 " + result.added + "개 일정을 추가했습니다.");
    if (targetItems.length + result.added >= maxItineraryItemsPerDay && result.failed.length) {
      toast(itineraryItemLimitMessage);
    }
    return result;
  }

  window.AllMyTripsSchedule = {
    getAiRecommendationStates,
    getAiRecommendationTimeConflicts,
    getAiRecommendationUnavailableTimePlaceIds,
    addAiRecommendations,
    addAiRecommendation: async function (recommendation, recommendedDayNumber) {
      const placeId = verifiedRecommendationPlaceId(recommendation);
      if (!placeId) throw new Error("실제 장소로 확인된 추천만 일정에 추가할 수 있습니다.");
      if (!activeDay || !activeDay.tripDayId) throw new Error("추가할 DAY를 먼저 선택해주세요.");
      const result = await addAiRecommendations([recommendation], recommendedDayNumber);
      if (result.failed.length) throw new Error(itineraryItemLimitMessage);
      return result;
    }
  };

  async function deleteItem(item, itemDay) {
    const targetDay = itemDay || activeDay;
    if (!targetDay) return;
    try {
      if (targetDay.tripDayId && item.itineraryItemId) {
        await api("/api/v1/trip-days/" + targetDay.tripDayId + "/items/" + item.itineraryItemId, { method: "DELETE" });
      } else {
        const draft = readDraft();
        const key = draftDayKey(targetDay);
        draft.scheduleItems = draft.scheduleItems || {};
        draft.scheduleItems[key] = (draft.scheduleItems[key] || []).filter(function (candidate) {
          return String(candidate.itineraryItemId) !== String(item.itineraryItemId);
        });
        sessionStorage.setItem("tripDraft", JSON.stringify(draft));
      }
      toast(item.title + "을(를) 일정에서 삭제했습니다.");
      if (allScheduleVisible) {
        await renderAllDays(scheduleDays);
      } else if (targetDay.tripDayId) {
        const selectedButton = Array.from(dayTabs.querySelectorAll("button")).find(function (button) { return button.classList.contains("selected"); });
        await selectDay(targetDay, selectedButton);
      } else {
        renderDraftDay(targetDay);
      }
    } catch (error) {
      toast(error.message || "일정 삭제에 실패했습니다.");
    }
  }

  function selectItem(item) {
    if (!item.place) return;
    if (map) { map.setCenter(new window.kakao.maps.LatLng(Number(item.place.latitude), Number(item.place.longitude))); map.setLevel(4); }
  }

  searchForm.addEventListener("submit", function (event) {
    event.preventDefault();
    const keyword = keywordInput.value.trim();
    if (keyword) searchPlaces(keyword);
  });
  placeSourceTabs.forEach(function (tab) {
    tab.addEventListener("click", function () {
      selectPlaceSource(tab.dataset.placeSource);
    });
  });
  const saveButton = document.querySelector("[data-schedule-save]");
  if (saveButton) saveButton.addEventListener("click", async function () {
    if (savingTrip) return;
    if (!activeTripId || !activeTrip) {
      toast("저장할 여행 정보를 찾을 수 없습니다.");
      return;
    }
    savingTrip = true;
    saveButton.disabled = true;
    saveButton.textContent = "저장 중...";
    try {
      const currentCalculation = calculateScheduleTimes(activeItems, activeDay);
      if (activeItems.length && currentCalculation.size === activeItems.length) {
        await persistCalculatedScheduleTimes(activeItems, activeDay, currentCalculation);
      }
      await savePendingReorders();
      const saved = await api("/api/v1/trips/" + activeTripId, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({...activeTrip, status: "CONFIRMED"}),
      });
      activeTrip = saved;
      pendingReorders.clear();
      pendingOptimizationResults.clear();
      const draft = readDraft();
      draft.trip = saved;
      sessionStorage.setItem("tripDraft", JSON.stringify(draft));
      saveButton.textContent = "✓ 저장 완료";
      toast("내 여행에 저장되었습니다.");
    } catch (error) {
      saveButton.disabled = false;
      saveButton.textContent = "▣ 여행 저장하기";
      if (error.code === "TRIP_PERIOD_CONFLICT") {
        toast("기간 밖 일차에 일정이 있어 변경할 수 없습니다. 일정을 이동하거나 삭제한 후 다시 시도해 주세요.");
      } else {
        toast(error.message || "여행 저장에 실패했습니다.");
      }
    } finally {
      savingTrip = false;
    }
  });
  if (mapExpandButton) mapExpandButton.addEventListener("click", openMapModal);
  document.querySelectorAll("[data-map-modal-close]").forEach(function (button) {
    button.addEventListener("click", closeMapModal);
  });
  if (backButton) backButton.addEventListener("click", function () {
    window.location.href = "/trips/new/style";
  });
  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape" && mapModal && !mapModal.hidden) closeMapModal();
    if (event.key === "Escape" && moreMenu?.open) closeMorePanels();
    if (event.key === "Escape" && transportSettingsPanel && !transportSettingsPanel.hidden) closeTransportSettings();
    if (event.key === "Escape" && placeAddPopover && !placeAddPopover.hidden) closePlaceSearchPanel();
  });
  routeToggle.addEventListener("click", function () {
    closePlaceSearchPanel();
    closeMorePanels();
    if (allScheduleVisible) {
      if (activeDay) leaveOverviewForDay(activeDay);
      return;
    }
    renderAllDays(scheduleDays);
  });
  if (overviewPrev) overviewPrev.addEventListener("click", function () {
    if (overviewPage <= 0) return;
    overviewPage -= 1;
    renderOverviewPage();
  });
  if (overviewNext) overviewNext.addEventListener("click", function () {
    const lastPage = Math.max(0, Math.ceil(overviewGroups.length / overviewPageSize) - 1);
    if (overviewPage >= lastPage) return;
    overviewPage += 1;
    renderOverviewPage();
  });
  if (dayTabs) dayTabs.addEventListener("scroll", updateDayNavigation, {passive: true});
  window.addEventListener("resize", updateDayNavigation);
  window.addEventListener("resize", positionTransportSettingsPanel);
  window.addEventListener("resize", positionPlaceSearchPanel);
  if (dayPrev) dayPrev.addEventListener("click", function () {
    dayTabs?.scrollBy({left: -Math.max(180, dayTabs.clientWidth * .8), behavior: "smooth"});
  });
  if (dayNext) dayNext.addEventListener("click", function () {
    dayTabs?.scrollBy({left: Math.max(180, dayTabs.clientWidth * .8), behavior: "smooth"});
  });
  if (placeAddTrigger && placeAddPopover) {
    placeAddTrigger.addEventListener("click", function () {
      const willOpen = placeAddPopover.hidden;
      if (willOpen) closeMorePanels();
      placeAddPopover.hidden = !willOpen;
      placeAddTrigger.setAttribute("aria-expanded", String(willOpen));
      if (optimizationSummarySlot) optimizationSummarySlot.hidden = willOpen;
      if (willOpen) window.setTimeout(function () {
        positionPlaceSearchPanel();
        if (activePlaceSource === "favorites") loadFavoritePlaces().catch(function () {});
        else if (activePlaceSource === "recommendations") loadRecommendedPlaces().catch(function () {});
        else keywordInput?.focus();
      }, 0);
    });
  }
  if (placeAddClose && placeAddPopover) placeAddClose.addEventListener("click", function () {
    closePlaceSearchPanel();
    placeAddTrigger?.focus();
  });
  if (transportTrigger) transportTrigger.addEventListener("click", function () {
    const willOpen = transportSettingsPanel?.hidden !== false;
    if (willOpen) openTransportSettings();
    else closeTransportSettings();
  });
  if (moreRouteMapButton) moreRouteMapButton.addEventListener("click", function () {
    mapRouteToggle?.click();
    closeMorePanels();
  });
  transportModeOptions.forEach(function (option) {
    option.addEventListener("click", function () {
      syncTransportSettings(option.dataset.transportMode || "");
    });
  });
  if (transportSettingsCancel) transportSettingsCancel.addEventListener("click", function () {
    syncTransportSettings(globalTransportMode);
    closeTransportSettings();
  });
  if (transportSettingsApply) transportSettingsApply.addEventListener("click", async function () {
    const selectedOption = transportModeOptions.find(function (option) {
      return option.getAttribute("aria-checked") === "true";
    });
    const mode = selectedOption?.dataset.transportMode || "";
    if (!mode) {
      toast("교통수단을 하나 선택해주세요.");
      return;
    }
    transportSettingsApply.disabled = true;
    transportModeOptions.forEach(function (option) { option.disabled = true; });
    const originalLabel = transportSettingsApply.textContent;
    transportSettingsApply.textContent = "적용 중...";
    try {
      await applyDayTransportMode(mode);
      closeTransportSettings();
      if (moreMenu) moreMenu.open = false;
      toast(travelModeOptions[mode].label + "을(를) 현재 DAY 일정에 적용했습니다.");
    } catch (error) {
      toast(error.message || "교통수단 설정을 적용하지 못했습니다.");
    } finally {
      transportSettingsApply.disabled = false;
      transportModeOptions.forEach(function (option) { option.disabled = false; });
      transportSettingsApply.textContent = originalLabel;
    }
  });
  if (moreMenu) moreMenu.addEventListener("toggle", function () {
    if (suppressMoreToggleClose) {
      suppressMoreToggleClose = false;
      return;
    }
    if (moreMenu.open) {
      closePlaceSearchPanel();
      closeTransportSettings();
    }
    else closeTransportSettings();
  });
  if (mapRouteToggle) mapRouteToggle.addEventListener("click", function () {
    routeVisible = !routeVisible;
    mapRouteToggle.textContent = routeVisible ? "경로선 ON" : "경로선 OFF";
    updateMoreRouteState();
    refreshMap();
  });
  updateMoreRouteState();
  syncTransportSettings(globalTransportMode);
  if (optimizeRouteTrigger) {
    optimizeRouteTrigger.addEventListener("click", async function () {
      const criterion = "TIME";
      if (!activeDay?.tripDayId) {
        toast("저장된 DAY에서만 동선을 최적화할 수 있습니다.");
        return;
      }
      if (!globalTransportMode) {
        toast("먼저 교통수단을 선택해주세요.");
        return;
      }
      if (activeItems.filter(function (item) { return item.place?.latitude && item.place?.longitude; }).length < 2) {
        toast("좌표가 있는 장소가 2개 이상 필요합니다.");
        return;
      }

      optimizeRouteTrigger.disabled = true;
      optimizeRouteTrigger.setAttribute("aria-busy", "true");
      try {
        const result = await api(
          "/api/v1/trip-days/" + activeDay.tripDayId
          + "/optimize-route?criterion=" + encodeURIComponent(criterion)
          + "&mode=" + encodeURIComponent(globalTransportMode),
          {method: "POST"}
        );
        const minutes = Math.round(Number(result.totalDurationSeconds || 0) / 60);
        applyPendingOrder(result.itineraryItemIds);
        const dayKey = String(activeDay.tripDayId);
        pendingReorders.set(dayKey, result.itineraryItemIds.slice());
        pendingOptimizationResults.set(dayKey, {result, criterion});
        renderItems(activeItems);
        lastRouteResult = result;
        lastOptimizationCriterion = criterion;
        renderRouteSummary(result, lastOptimizationCriterion);
        if (result.distancePriorityApplied) {
          toast("이동시간이 같아 이동 거리를 우선순위로 정렬했습니다.");
        } else {
          toast(minutes > 0
            ? "이동시간 우선으로 동선을 정리했습니다. 예상 이동시간 " + minutes + "분"
            : "이동시간 우선으로 동선을 정리했습니다.");
        }
      } catch (error) {
        toast(error.message || "동선 최적화에 실패했습니다.");
      } finally {
        optimizeRouteTrigger.disabled = false;
        optimizeRouteTrigger.removeAttribute("aria-busy");
      }
    });
  }
  initMap();
  loadSchedule();
  document.body.dataset.pageReady = "true";
});
