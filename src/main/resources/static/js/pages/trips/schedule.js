/* 여행 일정 편집: DAY 일정, 카카오 지도, 장소 검색, 날씨와 주차장 */
document.addEventListener("DOMContentLoaded", function () {
  const requestedTripId = Number(document.body.dataset.tripId);
  let activeTripId = Number.isInteger(requestedTripId) && requestedTripId > 0 ? requestedTripId : null;
  let activeDay = null;
  let activeItems = [];
  let map = null;
  let expandedMap = null;
  let placesService = null;
  let routeVisible = true;
  let routeLine = null;
  let mapOverlays = [];
  let expandedMapOverlays = [];
  let parkingOverlays = [];
  let expandedRouteLine = null;
  let searchPreviewOverlay = null;
  let selectedContext = null;
  let lastSearchResults = [];
  let activeTrip = null;
  let savingTrip = false;
  let scheduleDays = [];
  let lastRouteResult = null;
  let transitRouteResult = null;
  let transitRoutePath = null;
  let allScheduleVisible = false;
  const placeCategoryNames = new Map();
  const placeCategoryStorageKey = "allMyTrips.kakaoPlaceCategoryNames";
  const scheduleTimeStorageKey = "tripScheduleTimeOverrides";
  let activeTimeEditor = null;
  let activeTimeItem = null;
  let draggedScheduleItem = null;
  let dropPlaceholder = null;
  const pendingReorders = new Map();
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

  loadPlaceCategoryNames();

  const tripList = document.querySelector("[data-trip-list]");
  const title = document.querySelector("[data-schedule-title]");
  const dayTabs = document.querySelector("[data-day-tabs]");
  const timeline = document.querySelector("[data-timeline]");
  const period = document.querySelector("[data-schedule-period]");
  const destination = document.querySelector("[data-schedule-destination]");
  const mapContainer = document.querySelector("[data-schedule-map]");
  const mapExpandButton = document.querySelector(".map-expand-button");
  const mapRouteToggle = document.querySelector("[data-toggle-route-map]");
  const optimizeRouteButton = document.querySelector("[data-optimize-route]");
  const mapModal = document.querySelector("[data-map-modal]");
  const expandedMapContainer = document.querySelector("[data-schedule-map-expanded]");
  const mapStatus = document.querySelector("[data-map-status]");
  const searchForm = document.querySelector("[data-place-search-form]");
  const keywordInput = document.querySelector("[data-place-keyword]");
  const searchResults = document.querySelector("[data-place-results]");
  const insightTitle = document.querySelector("[data-insight-title]");
  const weatherResult = document.querySelector("[data-weather-result]");
  const parkingResults = document.querySelector("[data-parking-results]");
  const infoModal = document.querySelector("[data-place-insight]");
  const routeToggle = document.querySelector("[data-toggle-route]");
  const backButton = document.querySelector("[data-schedule-back]");
  const aiEmptyCta = document.querySelector("[data-schedule-ai-empty-cta]");

  const transitRouteButton = document.createElement("button");
  transitRouteButton.type = "button";
  transitRouteButton.className = "route-transit-button";
  transitRouteButton.textContent = "대중교통 경로";
  transitRouteButton.style.cssText = "margin-left:8px;padding:10px 14px;border:1px solid #dfe3ff;border-radius:10px;background:#fff;color:#5967d8;font-weight:700;cursor:pointer;";
  if (optimizeRouteButton?.parentElement) {
    optimizeRouteButton.parentElement.insertBefore(transitRouteButton, optimizeRouteButton.nextSibling);
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
    transitRouteResult = null;
    transitRoutePath = null;
    document.querySelectorAll(".route-optimization-summary, .route-segment-summary")
      .forEach(function (element) { element.remove(); });
    document.querySelectorAll(".transit-route-summary, .transit-route-segment-summary")
      .forEach(function (element) { element.remove(); });
  }

  function renderRouteSummary(result) {
    if (!result || !timeline?.parentElement) return;
    document.querySelectorAll(".route-optimization-summary").forEach(function (element) { element.remove(); });

    const summary = document.createElement("div");
    summary.className = "route-optimization-summary";
    summary.style.cssText = "margin:12px 0 16px;padding:14px 16px;border:1px solid #dfe3ff;border-radius:14px;background:#f8f9ff;color:#23305f;font-size:13px;line-height:1.7;";

    const title = document.createElement("strong");
    title.textContent = "추천 동선 이동 정보";
    title.style.display = "block";
    title.style.marginBottom = "4px";
    summary.appendChild(title);

    const total = document.createElement("div");
    total.textContent = "총 이동시간 " + formatRouteDuration(result.totalDurationSeconds)
      + " · 총 이동거리 " + formatRouteDistance(result.totalDistanceMeters);
    summary.appendChild(total);

    if (result.originalRouteAvailable) {
      const comparison = document.createElement("div");
      comparison.textContent = "기존 " + formatRouteDuration(result.originalDurationSeconds)
        + " → 최적화 후 " + formatRouteDuration(result.optimizedDurationSeconds)
        + " · " + formatRouteDuration(result.savedDurationSeconds) + " 절약";
      summary.appendChild(comparison);
    }

    if (result.distancePriorityApplied) {
      const tieBreak = document.createElement("div");
      tieBreak.textContent = "이동시간이 같아 이동 거리를 우선순위로 정렬했습니다.";
      tieBreak.style.color = "#6873c7";
      summary.appendChild(tieBreak);
    }

    timeline.parentElement.insertBefore(summary, timeline);
  }

  function renderRouteSegments(result) {
    if (!result || !Array.isArray(result.segments) || !result.segments.length) return;
    document.querySelectorAll(".route-segment-summary").forEach(function (element) { element.remove(); });
    const cards = Array.from(timeline.querySelectorAll(":scope > .schedule-item"));
    result.segments.forEach(function (segment, index) {
      const fromCard = cards[index];
      if (!fromCard) return;
      const segmentElement = document.createElement("div");
      segmentElement.className = "route-segment-summary";
      segmentElement.style.cssText = "margin:-4px 18px 8px;padding:5px 8px;color:#7480c8;font-size:12px;text-align:center;";
      segmentElement.textContent = "↓ 자동차 " + formatRouteDuration(segment.durationSeconds)
        + " · " + formatRouteDistance(segment.distanceMeters);
      fromCard.after(segmentElement);
    });
  }

  function transitModeLabel(section) {
    const routeName = section.routeName ? " " + section.routeName : "";
    return section.mode + routeName;
  }

  function renderTransitRoute(result) {
    if (!result || !timeline?.parentElement) return;
    document.querySelectorAll(".transit-route-summary, .transit-route-segment-summary")
      .forEach(function (element) { element.remove(); });

    const summary = document.createElement("div");
    summary.className = "transit-route-summary";
    summary.style.cssText = "margin:12px 0 16px;padding:14px 16px;border:1px solid #dfe3ff;border-radius:14px;background:#f8f9ff;color:#23305f;font-size:13px;line-height:1.7;";
    summary.textContent = "대중교통 총 " + formatRouteDuration(result.totalDurationSeconds)
      + " · " + formatRouteDistance(result.totalDistanceMeters)
      + " · 도보 " + formatRouteDistance(result.totalWalkMeters);
    timeline.parentElement.insertBefore(summary, timeline);

    const cards = Array.from(timeline.querySelectorAll(":scope > .schedule-item"));
    (result.legs || []).forEach(function (leg, index) {
      const fromCard = cards[index];
      if (!fromCard) return;
      const element = document.createElement("div");
      element.className = "transit-route-segment-summary";
      element.style.cssText = "margin:-4px 18px 8px;padding:6px 8px;color:#5967d8;font-size:12px;text-align:center;";
      const modes = (leg.sections || []).map(transitModeLabel).join(" · ");
      element.textContent = "↓ " + modes + " · " + formatRouteDuration(leg.totalDurationSeconds)
        + " · " + formatRouteDistance(leg.totalDistanceMeters);
      fromCard.after(element);
    });
  }

  async function loadTransitRoutes() {
    const mapped = activeItems.filter(function (item) {
      return item.place?.latitude != null && item.place?.longitude != null;
    });
    if (mapped.length < 2) throw new Error("좌표가 있는 장소가 2개 이상 필요합니다.");

    const legs = [];
    const allPoints = [];
    for (let index = 0; index < mapped.length - 1; index++) {
      const from = mapped[index];
      const to = mapped[index + 1];
      const result = await api("/api/v1/routes/transit", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          startX: Number(from.place.longitude),
          startY: Number(from.place.latitude),
          endX: Number(to.place.longitude),
          endY: Number(to.place.latitude)
        })
      });
      legs.push({
        fromTitle: from.title,
        toTitle: to.title,
        totalDurationSeconds: result.totalDurationSeconds,
        totalDistanceMeters: result.totalDistanceMeters,
        sections: result.sections || []
      });
      (result.points || []).forEach(function (point) {
        allPoints.push(new window.kakao.maps.LatLng(Number(point.latitude), Number(point.longitude)));
      });
    }
    const totalDurationSeconds = legs.reduce(function (sum, leg) { return sum + Number(leg.totalDurationSeconds || 0); }, 0);
    const totalDistanceMeters = legs.reduce(function (sum, leg) { return sum + Number(leg.totalDistanceMeters || 0); }, 0);
    const totalWalkMeters = legs.reduce(function (sum, leg) {
      return sum + (leg.sections || []).filter(function (section) { return section.mode === "도보"; })
        .reduce(function (sectionSum, section) { return sectionSum + Number(section.distanceMeters || 0); }, 0);
    }, 0);
    return { legs, totalDurationSeconds, totalDistanceMeters, totalWalkMeters, points: allPoints };
  }

  function formatDate(value) {
    if (!value) return "날짜 미정";
    return value.slice(5).replace("-", ".");
  }

  function formatTime(value) { return value ? value.slice(0, 5) : ""; }

  function getScheduleTimeKey(item) {
    return String(item.itineraryItemId || item.place?.externalPlaceId || item.title || "");
  }

  function readScheduleTimeOverrides() {
    try {
      return JSON.parse(sessionStorage.getItem(scheduleTimeStorageKey) || "{}");
    } catch (error) {
      return {};
    }
  }

  function getItemStartTime(item) {
    const override = readScheduleTimeOverrides()[getScheduleTimeKey(item)];
    return override?.startTime || item.startTime || "";
  }

  function getItemDuration(item) {
    const override = readScheduleTimeOverrides()[getScheduleTimeKey(item)];
    return formatDuration(override?.durationMinutes);
  }

  function saveScheduleTime(item, startTime, durationMinutes) {
    const overrides = readScheduleTimeOverrides();
    overrides[getScheduleTimeKey(item)] = {startTime, durationMinutes};
    sessionStorage.setItem(scheduleTimeStorageKey, JSON.stringify(overrides));
  }

  function createSelectOptions(select, start, end, formatter) {
    for (let value = start; value <= end; value += 1) {
      const option = document.createElement("option");
      option.value = String(value).padStart(2, "0");
      option.textContent = formatter ? formatter(value) : option.value;
      select.appendChild(option);
    }
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

  function formatDuration(minutes) {
    const value = Number(minutes);

    if (!Number.isFinite(value) || value <= 0) {
      return "";
    }

    if (value < 60) {
      return "체류 " + value + "분";
    }

    const hour = Math.floor(value / 60);
    const minute = value % 60;

    if (minute === 0) {
      return "체류 " + hour + "시간";
    }

    return "체류 " + hour + "시간 " + minute + "분";
  }

  function openTimeEditor(item, timeButton) {
    const clickedControl = timeButton.closest(".schedule-time-control");

    if (activeTimeEditor && activeTimeEditor.parentElement === clickedControl) {
      closeTimeEditor();
      return;
    }

    closeTimeEditor();

    const currentTime = getItemStartTime(item) || "09:00";
    const override = readScheduleTimeOverrides()[getScheduleTimeKey(item)] || {};
    const [currentHour, currentMinute] = currentTime.split(":");
    const editor = document.createElement("div");

    editor.className = "schedule-time-editor";
    editor.setAttribute("role", "dialog");
    editor.setAttribute("aria-label", "방문 시간 설정");
    editor.innerHTML = `
      <div class="schedule-time-editor-header">
        <strong>방문 시간 설정</strong>
        <button type="button" class="schedule-time-close" aria-label="시간 설정 닫기">×</button>
      </div>
      <div class="schedule-time-picker">
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
      </div>
      <div class="schedule-time-options">
        <button type="button" class="schedule-time-quick" data-time-action="plus-thirty">+30분</button>
        <select class="schedule-duration-select" data-duration aria-label="체류 시간">
          <option value="30">체류시간 30분</option>
          <option value="60">체류시간 1시간</option>
          <option value="90">체류시간 1시간 30분</option>
          <option value="120">체류시간 2시간</option>
          <option value="150">체류시간 2시간 30분</option>
          <option value="180">체류시간 3시간</option>
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
    durationSelect.value = String(override.durationMinutes || 120);

    function padTime(value) { return String(value).padStart(2, "0"); }
    function normalizeHour() {
      let value = Number(hourInput.value);
      if (!Number.isFinite(value)) value = 0;
      hourInput.value = padTime(Math.max(0, Math.min(23, value)));
    }
    function normalizeMinute() {
      let value = Number(minuteInput.value);
      if (!Number.isFinite(value)) value = 0;
      minuteInput.value = padTime(Math.max(0, Math.min(59, value)));
    }
    function changeHour(amount) {
      hourInput.value = padTime((Number(hourInput.value || 0) + amount + 24) % 24);
    }
    function changeMinute(amount) {
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
    hourInput.addEventListener("input", function () { hourInput.value = hourInput.value.replace(/\D/g, "").slice(0, 2); });
    minuteInput.addEventListener("input", function () { minuteInput.value = minuteInput.value.replace(/\D/g, "").slice(0, 2); });
    hourInput.addEventListener("blur", normalizeHour);
    minuteInput.addEventListener("blur", normalizeMinute);
    editor.querySelector(".schedule-time-close").addEventListener("click", closeTimeEditor);
    editor.querySelector(".schedule-time-cancel").addEventListener("click", closeTimeEditor);
    editor.querySelector(".schedule-time-save").addEventListener("click", function () {
      normalizeHour();
      normalizeMinute();
      const startTime = hourInput.value + ":" + minuteInput.value;
      const durationMinutes = Number(durationSelect.value);
      saveScheduleTime(item, startTime, durationMinutes);
      timeButton.innerHTML = `<span aria-hidden="true">◷</span><span>${startTime}</span>`;
      const durationTag = timeButton.closest(".schedule-item")?.querySelector(".schedule-item-duration");
      if (durationTag) durationTag.textContent = formatDuration(durationMinutes);
      closeTimeEditor();
      toast("방문 시간이 저장되었습니다.");
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
      throw error;
    }
    return payload.data;
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

  function refreshMap() {
    if (!map) return;
    clearOverlays(mapOverlays);
    if (routeLine) {
      routeLine.setMap(null);
      routeLine = null;
    }
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
    const displayedPath = transitRoutePath?.length > 1 ? transitRoutePath : path;
    if (routeVisible && displayedPath.length > 1) {
      routeLine = new window.kakao.maps.Polyline({
        path: displayedPath,
        strokeWeight: 4,
        strokeColor: "#6372df",
        strokeOpacity: .85,
        strokeStyle: "solid",
      });
      routeLine.setMap(map);
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
    const displayedPath = transitRoutePath?.length > 1 ? transitRoutePath : path;
    if (routeVisible && displayedPath.length > 1) {
      expandedRouteLine = new window.kakao.maps.Polyline({
        path: displayedPath,
        strokeWeight: 4,
        strokeColor: "#6372df",
        strokeOpacity: .85,
        strokeStyle: "solid",
      });
      expandedRouteLine.setMap(expandedMap);
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

  function createScheduleItem(item, index, day) {
    const row = document.createElement("article");
    row.className = "schedule-item";
    const dragHandle = document.createElement("button");
    const order = document.createElement("span");
    const copy = document.createElement("div");
    const name = document.createElement("strong");
    const meta = document.createElement("small");
    const actions = document.createElement("div");
    const infoButton = document.createElement("button");
    const deleteButton = document.createElement("button");
    const timeButton = document.createElement("button");
    const categoryTag = document.createElement("span");
    const durationTag = document.createElement("span");
    const weatherTag = document.createElement("span");
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
    const startTime = getItemStartTime(item);
    meta.textContent = [item.memo].filter(Boolean).join(" · ");
    timeButton.type = "button";
    timeButton.className = "schedule-item-time";
    timeButton.textContent = startTime ? "◷ " + formatTime(startTime) : "시간 설정";
    timeButton.addEventListener("click", function () { openTimeEditor(item, timeButton); });
    timeControl.className = "schedule-time-control";
    timeControl.appendChild(timeButton);
    infoButton.type = "button";
    infoButton.textContent = "P 주차";
    infoButton.disabled = !item.place;
    infoButton.className = "schedule-item-info";
    infoButton.addEventListener("click", function () { selectItem(item, true); });
    deleteButton.type = "button";
    deleteButton.textContent = "삭제";
    deleteButton.className = "schedule-item-delete";
    deleteButton.addEventListener("click", function () { deleteItem(item, day); });
    categoryTag.className = "schedule-item-tag";
    categoryTag.textContent = getPlaceCategoryLabel(item.place);
    durationTag.className = "schedule-item-duration";
    durationTag.textContent = getItemDuration(item);
    weatherTag.className = "schedule-item-weather";
    weatherTag.textContent = item.place ? "날씨 확인 중" : "날씨 정보 없음";
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
    titleLine.append(name);
    actions.className = "schedule-item-actions";
    actions.appendChild(deleteButton);
    const tags = document.createElement("div");
    tags.className = "schedule-item-tags";
    tags.append(categoryTag);
    if (durationTag.textContent) tags.append(durationTag);
    tags.append(weatherTag, infoButton, addressControl);
    copy.append(titleLine, tags);
    if (meta.textContent) copy.append(meta);
    row.append(dragHandle, order, timeControl, copy, actions);
    if (canReorder) attachDragEvents(row);
    if (item.place) loadScheduleWeather(item, day?.tripDate, weatherTag);
    return row;
  }

  function ensureDropPlaceholder() {
    if (dropPlaceholder) return dropPlaceholder;
    dropPlaceholder = document.createElement("div");
    dropPlaceholder.className = "schedule-drop-placeholder";
    dropPlaceholder.textContent = "여기에 놓으면 순서가 변경됩니다.";
    dropPlaceholder.addEventListener("dragover", function (event) {
      event.preventDefault();
      event.dataTransfer.dropEffect = "move";
    });
    dropPlaceholder.addEventListener("drop", completeDrop);
    return dropPlaceholder;
  }

  async function completeDrop(event) {
    event.preventDefault();
    if (!draggedScheduleItem || !dropPlaceholder?.parentElement) return;
    clearRouteDisplay();
    dropPlaceholder.parentElement.insertBefore(draggedScheduleItem, dropPlaceholder);
    const orderIds = Array.from(timeline.querySelectorAll(".schedule-item[data-itinerary-item-id]"))
      .map(function (element) { return Number(element.dataset.itineraryItemId); });
    clearDragState();
    await saveManualOrder(orderIds);
  }

  function clearDragState() {
    if (dropPlaceholder?.parentElement) dropPlaceholder.remove();
    document.querySelectorAll(".schedule-item.is-dragging, .schedule-item.is-drag-over")
      .forEach(function (element) { element.classList.remove("is-dragging", "is-drag-over"); });
    draggedScheduleItem = null;
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
      event.dataTransfer.effectAllowed = "move";
      event.dataTransfer.setData("text/plain", row.dataset.itineraryItemId || "");
    });
    row.addEventListener("dragover", function (event) {
      if (!draggedScheduleItem || draggedScheduleItem === row) return;
      event.preventDefault();
      const rectangle = row.getBoundingClientRect();
      const before = event.clientY < rectangle.top + rectangle.height / 2;
      row.classList.toggle("is-drag-over", true);
      if (before) row.parentElement.insertBefore(ensureDropPlaceholder(), row);
      else row.parentElement.insertBefore(ensureDropPlaceholder(), row.nextSibling);
    });
    row.addEventListener("dragleave", function (event) {
      if (!row.contains(event.relatedTarget)) row.classList.remove("is-drag-over");
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
      if (activeDay.tripDayId) {
        pendingReorders.set(String(activeDay.tripDayId), orderIds.slice());
        const orderMap = new Map(orderIds.map(function (id, index) { return [String(id), index]; }));
        activeItems.sort(function (left, right) {
          return orderMap.get(String(left.itineraryItemId)) - orderMap.get(String(right.itineraryItemId));
        });
        activeItems.forEach(function (item, index) { item.sortOrder = index; });
        renderItems(activeItems);
        refreshMap();
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
        refreshMap();
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
    if (!Array.isArray(orderIds) || !orderIds.length) return;
    const orderMap = new Map(orderIds.map(function (id, index) { return [String(id), index]; }));
    activeItems.sort(function (left, right) {
      return (orderMap.get(String(left.itineraryItemId)) ?? Number.MAX_SAFE_INTEGER)
        - (orderMap.get(String(right.itineraryItemId)) ?? Number.MAX_SAFE_INTEGER);
    });
    activeItems.forEach(function (item, index) { item.sortOrder = index; });
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
    return place.categoryName
      || place.category_name
      || placeCategoryNames.get(String(place.externalPlaceId))
      || categoryLabels[String(place.category || "").toUpperCase()]
      || "관광지";
  }

  function renderItems(items) {
    clearRouteDisplay();
    const orderedItems = items.slice().sort(function (left, right) {
      return (left.sortOrder || 0) - (right.sortOrder || 0);
    });
    activeItems = orderedItems;
    allScheduleVisible = false;
    dayTabs.hidden = false;
    routeToggle.textContent = "전체 보기";
    timeline.classList.remove("all-days-view");
    timeline.replaceChildren();
    if (!orderedItems.length) {
      toggleAiEmptyCta(true);
      showEmpty(timeline, "아직 추가한 장소가 없습니다. 오른쪽에서 장소를 검색해보세요.");
      refreshMap();
      if (lastSearchResults.length) renderSearchResults(lastSearchResults);
      return;
    }
    toggleAiEmptyCta(false);
    orderedItems.forEach(function (item, index) {
      const element = createScheduleItem(item, index, activeDay);
      element.dataset.itineraryItemId = item.itineraryItemId || "";
      timeline.appendChild(element);
    });
    refreshMap();
    if (orderedItems[0]?.place) selectItem(orderedItems[0], false);
    if (lastSearchResults.length) renderSearchResults(lastSearchResults);
  }

  async function renderAllDays(days) {
    toggleAiEmptyCta(false);
    allScheduleVisible = true;
    dayTabs.hidden = true;
    routeToggle.textContent = "개별 일정 보기";
    timeline.classList.add("all-days-view");
    showEmpty(timeline, "전체 일정을 불러오는 중입니다.");
    try {
      const groups = await Promise.all(days.map(async function (day) {
        const items = day.tripDayId
          ? await hydrateItems(await api("/api/v1/trip-days/" + day.tripDayId + "/items"))
          : (readDraft().scheduleItems?.[draftDayKey(day)] || []);
        return {day, items};
      }));
      timeline.replaceChildren();
      activeItems = groups.reduce(function (all, group) { return all.concat(group.items); }, []);
      let globalOrder = 0;
      groups.forEach(function (group) {
        const section = document.createElement("section");
        const heading = document.createElement("h3");
        const list = document.createElement("div");
        section.className = "schedule-day-group";
        heading.textContent = "DAY " + group.day.dayNumber + (group.day.tripDate ? " · " + formatDate(group.day.tripDate) : "");
        list.className = "schedule-day-items";
        group.items.forEach(function (item) {
          list.appendChild(createScheduleItem(item, globalOrder, group.day));
          globalOrder += 1;
        });
        section.append(heading, list);
        timeline.appendChild(section);
      });
      if (!activeItems.length) showEmpty(timeline, "아직 추가한 장소가 없습니다.");
      refreshMap();
    } catch (error) {
      showEmpty(timeline, error.message);
    }
  }

  async function selectDay(day, selectedButton) {
    clearRouteDisplay();
    activeDay = day;
    dayTabs.querySelectorAll("button").forEach(function (button) { button.classList.toggle("selected", button === selectedButton); });
    showEmpty(timeline, "DAY " + day.dayNumber + " 일정을 불러오는 중입니다.");
    try {
      renderItems(await hydrateItems(await api("/api/v1/trip-days/" + day.tripDayId + "/items")));
    } catch (error) {
      showEmpty(timeline, error.message);
    }
  }

  function renderDays(days) {
    scheduleDays = days;
    allScheduleVisible = false;
    dayTabs.hidden = false;
    routeToggle.textContent = "전체 보기";
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
    if (period) period.textContent = "여행 기간 · " + (basic.startDate || "미정") + " ~ " + (basic.endDate || "미정");
    if (destination) destination.textContent = "목적지 · " + (basic.destinationLabel || basic.destination);
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
    routeToggle.textContent = "전체 보기";
    dayTabs.replaceChildren();
    days.forEach(function (day, index) {
      const button = document.createElement("button");
      button.type = "button"; button.textContent = "DAY " + day.dayNumber;
      if (!index) button.classList.add("selected");
      button.addEventListener("click", function () {
        activeDay = day;
        dayTabs.querySelectorAll("button").forEach(function (candidate) { candidate.classList.toggle("selected", candidate === button); });
        renderDraftDay(day);
      });
      dayTabs.appendChild(button);
    });
    activeDay = days[0];
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
      title.textContent = result[0].title;
      if (period) period.textContent = "여행 기간 · " + result[0].startDate + " ~ " + result[0].endDate;
      if (destination) destination.textContent = "목적지 · " + result[0].destinationName;
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

  function renderSearchResults(results) {
    searchResults.replaceChildren();
    results.forEach(function (place) {
      const row = document.createElement("article");
      const copy = document.createElement("div");
      const name = document.createElement("strong");
      const meta = document.createElement("small");
      const add = document.createElement("button");
      row.className = "place-result";
      row.tabIndex = 0;
      name.textContent = place.place_name;
      meta.textContent = [place.road_address_name || place.address_name, place.category_name, place.phone].filter(Boolean).join(" · ");
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
          toast(place.place_name + "을(를) DAY " + activeDay.dayNumber + "에 추가했습니다.");
        } catch (error) {
          add.disabled = false; add.textContent = "다시 시도"; toast(error.message);
        }
      });
      copy.append(name, meta); row.append(copy, add); searchResults.appendChild(row);
    });
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

  window.AllMyTripsSchedule = {
    addAiRecommendation: async function (recommendation, recommendedDayNumber) {
      if (!activeDay || !activeDay.tripDayId) {
        throw new Error("추가할 DAY를 먼저 선택해주세요.");
      }
      const requestedDay = Number(recommendedDayNumber);
      const targetDay = Number.isInteger(requestedDay)
        ? scheduleDays.find(function (day) { return day.dayNumber === requestedDay; })
        : activeDay;
      if (!targetDay?.tripDayId) {
        throw new Error("\uCD94\uCC9C \uC77C\uCC28\uAC00 \uD604\uC7AC \uC5EC\uD589\uC5D0 \uC5C6\uC5B4 \uC77C\uC815\uC5D0 \uCD94\uAC00\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4.");
      }
      const targetItems = targetDay.tripDayId === activeDay.tripDayId
        ? activeItems
        : await hydrateItems(await api("/api/v1/trip-days/" + targetDay.tripDayId + "/items"));
      const nextSortOrder = targetItems.reduce(function (max, item) {
        return Math.max(max, Number(item.sortOrder) || 0);
      }, 0) + 1;
      await api("/api/v1/trip-days/" + targetDay.tripDayId + "/items", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          itemType: "NOTE",
          title: recommendation.name,
          startTime: recommendation.time || null,
          sortOrder: nextSortOrder,
          memo: recommendation.reason || null,
          currencyCode: "KRW",
          source: "AI"
        })
      });
      const selectedButton = Array.from(dayTabs.querySelectorAll("button"))
        .find(function (button) { return button.textContent === "DAY " + targetDay.dayNumber; });
      await selectDay(targetDay, selectedButton);
    }
  };

  function featuredKakaoPlace(element) {
    return {
      id: element.dataset.placeId,
      place_name: element.dataset.placeName,
      road_address_name: element.dataset.placeAddress,
      address_name: element.dataset.placeAddress,
      x: element.dataset.placeLongitude,
      y: element.dataset.placeLatitude,
      phone: "",
      category_group_code: "AT4",
    };
  }

  async function addFeaturedPlace(element, addButton) {
    if (!activeDay) {
      toast("장소를 추가할 DAY를 먼저 선택해주세요.");
      return;
    }
    const kakaoPlace = featuredKakaoPlace(element);
    const addLabel = addButton.querySelector(".featured-place-add-label");
    const alreadyAdded = activeItems.some(function (item) {
      return String(item.place?.externalPlaceId) === String(kakaoPlace.id);
    });
    if (alreadyAdded) {
      addButton.disabled = true;
      if (addLabel) addLabel.textContent = "추가됨";
      return;
    }
    addButton.disabled = true;
    if (addLabel) addLabel.textContent = "추가 중";
    try {
      if (activeDay.tripDayId) await addPlaceToDay(kakaoPlace);
      else addDraftPlaceToDay(kakaoPlace);
      if (addLabel) addLabel.textContent = "추가됨";
      toast(kakaoPlace.place_name + "을(를) DAY " + activeDay.dayNumber + "에 추가했습니다.");
    } catch (error) {
      addButton.disabled = false;
      if (addLabel) addLabel.textContent = "일정에 추가하기";
      toast(error.message);
    }
  }

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

  function openInfoModal() {
    if (!infoModal) return;
    infoModal.hidden = false;
    infoModal.setAttribute("aria-hidden", "false");
    document.body.classList.add("schedule-info-modal-open");
  }

  function closeInfoModal() {
    if (!infoModal) return;
    infoModal.hidden = true;
    infoModal.setAttribute("aria-hidden", "true");
    document.body.classList.remove("schedule-info-modal-open");
  }

  function selectItem(item, shouldOpen) {
    if (!item.place) return;
    selectedContext = item;
    insightTitle.textContent = item.title;
    parkingResults.replaceChildren(Object.assign(document.createElement("p"), {textContent:"주차장 검색을 눌러주세요."}));
    loadWeather(item);
    if (map) { map.setCenter(new window.kakao.maps.LatLng(Number(item.place.latitude), Number(item.place.longitude))); map.setLevel(4); }
    if (shouldOpen) openInfoModal();
  }

  async function fetchWeather(item, tripDate) {
    const date = tripDate || activeDay?.tripDate || activeTrip?.startDate;
    if (!item?.place || !date) return null;
    const params = new URLSearchParams({latitude:item.place.latitude,longitude:item.place.longitude,date,time:formatTime(getItemStartTime(item))||"12:00"});
    return api("/api/v1/weather?" + params);
  }

  async function loadScheduleWeather(item, tripDate, weatherTag) {
    try {
      const weather = await fetchWeather(item, tripDate);
      if (weatherTag && weather) weatherTag.textContent = weather.icon + " " + weather.temperature + "℃";
    } catch (error) {
      if (weatherTag) weatherTag.textContent = "날씨 정보 없음";
    }
  }

  async function loadWeather(item) {
    weatherResult.replaceChildren(Object.assign(document.createElement("p"), {textContent:"날씨를 불러오는 중입니다."}));
    try {
      const weather = await fetchWeather(item);
      if (!weather) throw new Error("날씨 정보를 확인할 수 없습니다.");
      weatherResult.replaceChildren();
      const main = document.createElement("div"); main.className = "weather-main";
      const icon = document.createElement("span"); icon.className = "weather-icon"; icon.textContent = weather.icon;
      const copy = document.createElement("div"); const type = document.createElement("strong"); const note = document.createElement("small");
      type.textContent = weather.weatherType; note.textContent = weather.visitDate + " · 강수확률 " + weather.rainPercent + "% · " + weather.recommendation; copy.append(type,note);
      const temp = document.createElement("span"); temp.className = "weather-temp"; temp.textContent = weather.temperature + "℃";
      main.append(icon,copy,temp); weatherResult.append(main,Object.assign(document.createElement("p"),{textContent:weather.message}));
    } catch (error) {
      weatherResult.replaceChildren(Object.assign(document.createElement("p"), {textContent:error.message}));
    }
  }

  function findParking() {
    if (!selectedContext?.place || !placesService) {
      toast("주차 정보를 확인하려면 장소를 일정에 먼저 추가해 주세요.");
      return;
    }
    showEmpty(parkingResults, "주변 주차장을 찾고 있습니다.");
    const location = new window.kakao.maps.LatLng(Number(selectedContext.place.latitude), Number(selectedContext.place.longitude));
    placesService.categorySearch("PK6", function (data, status) {
      clearOverlays(parkingOverlays);
      if (status !== window.kakao.maps.services.Status.OK) {
        showEmpty(parkingResults, "반경 1.5km 내 주차장을 찾지 못했습니다.");
        return;
      }
      parkingResults.replaceChildren();
      const list = data.slice(0, 5);
      list.forEach(function (parking) {
        const row = document.createElement("button");
        const copy = document.createElement("div");
        const name = document.createElement("strong");
        const meta = document.createElement("small");
        row.type = "button";
        row.className = "parking-result";
        name.textContent = parking.place_name;
        meta.textContent = parking.road_address_name || parking.address_name;
        copy.append(name, meta);
        row.append(copy, Object.assign(document.createElement("span"), { textContent: "지도 보기" }));
        const position = new window.kakao.maps.LatLng(parking.y,parking.x);
        const marker = document.createElement("span");
        marker.className = "parking-map-marker";
        marker.textContent = "P";
        parkingOverlays.push(new window.kakao.maps.CustomOverlay({
          map,
          position,
          content: marker,
          yAnchor: 1,
        }));
        row.addEventListener("click", function () {
          closeInfoModal();
          map.setCenter(position);
          map.setLevel(3);
        });
        parkingResults.appendChild(row);
      });
    }, {
      location,
      radius: 1500,
      sort: window.kakao.maps.services.SortBy.DISTANCE,
    });
  }

  searchForm.addEventListener("submit", function (event) {
    event.preventDefault();
    const keyword = keywordInput.value.trim();
    if (keyword) searchPlaces(keyword);
  });
  document.querySelector("[data-find-parking]").addEventListener("click", findParking);
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
      await savePendingReorders();
      const saved = await api("/api/v1/trips/" + activeTripId, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({...activeTrip, status: "CONFIRMED"}),
      });
      activeTrip = saved;
      pendingReorders.clear();
      const draft = readDraft();
      draft.trip = saved;
      sessionStorage.setItem("tripDraft", JSON.stringify(draft));
      saveButton.textContent = "✓ 저장 완료";
      toast("내 여행에 저장되었습니다.");
    } catch (error) {
      saveButton.disabled = false;
      saveButton.textContent = "▣ 여행 저장하기";
      toast(error.message || "여행 저장에 실패했습니다.");
    } finally {
      savingTrip = false;
    }
  });
  if (mapExpandButton) mapExpandButton.addEventListener("click", openMapModal);
  document.querySelectorAll("[data-map-modal-close]").forEach(function (button) {
    button.addEventListener("click", closeMapModal);
  });
  if (backButton) backButton.addEventListener("click", function () {
    window.history.back();
  });
  document.querySelectorAll("[data-schedule-info-close]").forEach(function (button) {
    button.addEventListener("click", closeInfoModal);
  });
  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape" && mapModal && !mapModal.hidden) closeMapModal();
    if (event.key === "Escape" && infoModal && !infoModal.hidden) closeInfoModal();
  });
  routeToggle.addEventListener("click", function () {
    if (allScheduleVisible) {
      allScheduleVisible = false;
      timeline.classList.remove("all-days-view");
      dayTabs.hidden = false;
      routeToggle.textContent = "전체 보기";
      if (activeDay?.tripDayId) {
        const selectedButton = Array.from(dayTabs.querySelectorAll("button")).find(function (button) { return button.classList.contains("selected"); });
        selectDay(activeDay, selectedButton);
      } else if (activeDay) {
        renderDraftDay(activeDay);
      }
      return;
    }
    renderAllDays(scheduleDays);
  });
  if (mapRouteToggle) mapRouteToggle.addEventListener("click", function () {
    routeVisible = !routeVisible;
    mapRouteToggle.textContent = routeVisible ? "경로선 ON" : "경로선 OFF";
    refreshMap();
  });
  if (optimizeRouteButton) optimizeRouteButton.addEventListener("click", async function () {
    if (!activeDay?.tripDayId) {
      toast("저장된 DAY에서만 동선을 최적화할 수 있습니다.");
      return;
    }
    if (activeItems.filter(function (item) { return item.place?.latitude && item.place?.longitude; }).length < 2) {
      toast("좌표가 있는 장소가 2개 이상 필요합니다.");
      return;
    }
    optimizeRouteButton.disabled = true;
    optimizeRouteButton.textContent = "최적화 중...";
    try {
      const result = await api("/api/v1/trip-days/" + activeDay.tripDayId + "/optimize-route", {method: "POST"});
      const minutes = Math.round(Number(result.totalDurationSeconds || 0) / 60);
      applyPendingOrder(result.itineraryItemIds);
      pendingReorders.set(String(activeDay.tripDayId), result.itineraryItemIds.slice());
      renderItems(activeItems);
      lastRouteResult = result;
      renderRouteSummary(result);
      renderRouteSegments(result);
      if (result.distancePriorityApplied) {
        toast("이동시간이 같아 이동 거리를 우선순위로 정렬했습니다.");
      } else {
        toast(minutes > 0
          ? "이동시간 기준으로 동선을 정리했습니다. 예상 이동시간 " + minutes + "분"
          : "이동시간 기준으로 동선을 정리했습니다.");
      }
    } catch (error) {
      toast(error.message || "동선 최적화에 실패했습니다.");
    } finally {
      optimizeRouteButton.disabled = false;
      optimizeRouteButton.textContent = "✨ 동선 최적화";
    }
  });
  transitRouteButton.addEventListener("click", async function () {
    if (!activeDay) {
      toast("일정을 먼저 선택해주세요.");
      return;
    }
    transitRouteButton.disabled = true;
    transitRouteButton.textContent = "조회 중...";
    try {
      const result = await loadTransitRoutes();
      transitRouteResult = result;
      transitRoutePath = result.points;
      renderTransitRoute(result);
      refreshMap();
    } catch (error) {
      transitRouteResult = null;
      transitRoutePath = null;
      refreshMap();
      toast(error.message || "대중교통 경로 조회에 실패했습니다.");
    } finally {
      transitRouteButton.disabled = false;
      transitRouteButton.textContent = "대중교통 경로";
    }
  });
  document.querySelectorAll("[data-schedule-guide]").forEach(function (button) {
    button.addEventListener("click", function () {
      keywordInput.focus();
    });
  });
  document.querySelectorAll("[data-featured-place]").forEach(function (place) {
    const addButton = place.querySelector(".featured-place-add");
    if (addButton) {
      addButton.addEventListener("click", function () {
        addFeaturedPlace(place, addButton);
      });
    }
  });

  initMap();
  loadSchedule();
  document.body.dataset.pageReady = "true";
});
