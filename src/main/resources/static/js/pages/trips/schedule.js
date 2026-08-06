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

  const tripList = document.querySelector("[data-trip-list]");
  const title = document.querySelector("[data-schedule-title]");
  const dayTabs = document.querySelector("[data-day-tabs]");
  const timeline = document.querySelector("[data-timeline]");
  const period = document.querySelector("[data-schedule-period]");
  const destination = document.querySelector("[data-schedule-destination]");
  const mapContainer = document.querySelector("[data-schedule-map]");
  const mapExpandButton = document.querySelector(".map-expand-button");
  const mapModal = document.querySelector("[data-map-modal]");
  const expandedMapContainer = document.querySelector("[data-schedule-map-expanded]");
  const mapStatus = document.querySelector("[data-map-status]");
  const searchForm = document.querySelector("[data-place-search-form]");
  const keywordInput = document.querySelector("[data-place-keyword]");
  const searchResults = document.querySelector("[data-place-results]");
  const insight = document.querySelector("[data-place-insight]");
  const insightTitle = document.querySelector("[data-insight-title]");
  const weatherResult = document.querySelector("[data-weather-result]");
  const parkingResults = document.querySelector("[data-parking-results]");
  const routeToggle = document.querySelector("[data-toggle-route]");

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

  function formatDate(value) {
    if (!value) return "날짜 미정";
    return value.slice(5).replace("-", ".");
  }

  function formatTime(value) { return value ? value.slice(0, 5) : ""; }

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
    if (routeVisible && path.length > 1) {
      routeLine = new window.kakao.maps.Polyline({
        path,
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
    if (routeVisible && path.length > 1) {
      expandedRouteLine = new window.kakao.maps.Polyline({
        path,
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

  function renderItems(items) {
    activeItems = items;
    timeline.replaceChildren();
    if (!items.length) {
      showEmpty(timeline, "아직 추가한 장소가 없습니다. 오른쪽에서 장소를 검색해보세요.");
      refreshMap();
      if (lastSearchResults.length) renderSearchResults(lastSearchResults);
      return;
    }
    items.forEach(function (item, index) {
      const row = document.createElement("article");
      row.className = "schedule-item";
      const order = document.createElement("span");
      const copy = document.createElement("div");
      const name = document.createElement("strong");
      const meta = document.createElement("small");
      const actions = document.createElement("div");
      const infoButton = document.createElement("button");
      order.textContent = item.sortOrder || index + 1;
      name.textContent = item.title || "일정";
      meta.textContent = [formatTime(item.startTime) || "시간 미정", item.place?.address, item.memo].filter(Boolean).join(" · ");
      infoButton.type = "button";
      infoButton.textContent = "날씨·주차";
      infoButton.disabled = !item.place;
      infoButton.addEventListener("click", function () { selectItem(item); });
      actions.className = "schedule-item-actions";
      actions.appendChild(infoButton);
      copy.append(name, meta);
      row.append(order, copy, actions);
      timeline.appendChild(row);
    });
    refreshMap();
    if (items[0]?.place) selectItem(items[0]);
    if (lastSearchResults.length) renderSearchResults(lastSearchResults);
  }

  async function selectDay(day, selectedButton) {
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
      renderTripList([result[0]]);
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
    await api("/api/v1/trip-days/" + activeDay.tripDayId + "/items", {
      method:"POST", headers:{"Content-Type":"application/json"},
      body:JSON.stringify({placeId:place.placeId,itemType:"PLACE",title:place.name,sortOrder:activeItems.length+1,currencyCode:"KRW",source:"MANUAL"}),
    });
    const selectedButton = Array.from(dayTabs.querySelectorAll("button")).find(function (button) { return button.classList.contains("selected"); });
    await selectDay(activeDay, selectedButton);
  }

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

  function selectItem(item) {
    if (!item.place) return;
    selectedContext = item;
    insight.hidden = false;
    insightTitle.textContent = item.title;
    parkingResults.replaceChildren(Object.assign(document.createElement("p"), {textContent:"주차장 검색을 눌러주세요."}));
    loadWeather(item);
    if (map) { map.setCenter(new window.kakao.maps.LatLng(Number(item.place.latitude), Number(item.place.longitude))); map.setLevel(4); }
  }

  async function loadWeather(item) {
    weatherResult.replaceChildren(Object.assign(document.createElement("p"), {textContent:"날씨를 불러오는 중입니다."}));
    try {
      const params = new URLSearchParams({latitude:item.place.latitude,longitude:item.place.longitude,date:activeDay.tripDate,time:formatTime(item.startTime)||"12:00"});
      const weather = await api("/api/v1/weather?" + params);
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
      const saved = await api("/api/v1/trips/" + activeTripId, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({...activeTrip, status: "CONFIRMED"}),
      });
      activeTrip = saved;
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
  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape" && mapModal && !mapModal.hidden) closeMapModal();
  });
  routeToggle.addEventListener("click", function () {
    routeVisible = !routeVisible;
    routeToggle.textContent = routeVisible ? "경로선 ON" : "경로선 OFF";
    refreshMap();
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
