document.addEventListener("DOMContentLoaded", function () {
  const params = new URLSearchParams(window.location.search);
  const destination = (params.get("destination") || "").trim();
  const startDate = params.get("startDate") || params.get("departureDate") || "";
  const endDate = params.get("endDate") || startDate;
  const travelers = Math.min(20, Math.max(1, Number(params.get("travelers")) || 1));
  const form = document.querySelector("[data-plan-form]");
  const empty = document.querySelector("[data-plan-empty]");
  const loading = document.querySelector("[data-plan-loading]");
  const result = document.querySelector("[data-plan-result]");
  const error = document.querySelector("[data-plan-error]");
  const mapContainer = document.querySelector("[data-plan-map]");
  const mapStatus = document.querySelector("[data-plan-map-status]");
  const destinationText = destination || "여행지 미입력";
  let kakaoMap = null;
  let kakaoPlaces = null;
  let kakaoOverlays = [];
  let kakaoRouteLine = null;
  let mapRenderSequence = 0;
  let currentPlan = null;
  let activeDayIndex = 0;

  function setPanelVisible(panel, visible) {
    panel.hidden = !visible;
    panel.style.display = visible ? "" : "none";
  }

  function showPlanState(state) {
    setPanelVisible(empty, state === "empty");
    setPanelVisible(loading, state === "loading");
    setPanelVisible(error, state === "error");
    setPanelVisible(result, state === "result");
  }

  showPlanState("empty");

  function formatDate(value) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return "날짜 미입력";
    const date = new Date(value + "T00:00:00");
    return new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "long", day: "numeric", weekday: "short" }).format(date);
  }

  function travelDays() {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(startDate) || !/^\d{4}-\d{2}-\d{2}$/.test(endDate)) return 1;
    const difference = Math.round((Date.parse(endDate + "T00:00:00") - Date.parse(startDate + "T00:00:00")) / 86400000) + 1;
    return Math.min(30, Math.max(1, difference));
  }

  function formatPeriod() {
    if (startDate === endDate) return formatDate(startDate);
    return formatDate(startDate) + " ~ " + formatDate(endDate);
  }

  document.querySelector("[data-plan-destination]").textContent = destinationText;
  document.querySelector("[data-plan-date]").textContent = formatPeriod();
  document.querySelector("[data-plan-travelers]").textContent = travelers + "명";

  function item(time, title, detail) {
    const row = document.createElement("li");
    const timeElement = document.createElement("time");
    const copy = document.createElement("div");
    const titleElement = document.createElement("strong");
    const detailElement = document.createElement("span");
    timeElement.textContent = time;
    titleElement.textContent = title;
    detailElement.textContent = detail;
    copy.append(titleElement, detailElement);
    row.append(timeElement, copy);
    return row;
  }

  function renderDaySchedule(day) {
    const days = document.querySelector("[data-plan-days]");
    const section = document.createElement("section");
    const heading = document.createElement("h3");
    const timeline = document.createElement("ol");
    heading.textContent = day.title;
    timeline.className = "plan-timeline";
    timeline.append(...(day.items || []).map(function (schedule) {
      return item(schedule.time, schedule.title, schedule.description);
    }));
    section.append(heading, timeline);
    days.replaceChildren(section);
  }

  function selectPlanDay(index) {
    if (!currentPlan?.days?.[index]) return;
    activeDayIndex = index;
    const day = currentPlan.days[index];
    document.querySelector("[data-plan-map-day]").textContent = "DAY " + day.day + " 추천 장소 지도";
    document.querySelectorAll("[data-plan-day-tabs] button").forEach(function (button, buttonIndex) {
      const isSelected = buttonIndex === index;
      button.classList.toggle("is-active", isSelected);
      button.setAttribute("aria-selected", String(isSelected));
    });
    renderDaySchedule(day);
    renderRecommendedPlaces(day.places || []);
    renderKakaoMap(day.places || []);
  }

  function renderPlan(plan) {
    const title = document.querySelector("[data-plan-title]");
    const summary = document.querySelector("[data-plan-summary]");
    const source = document.querySelector("[data-plan-source]");
    const dayTabs = document.querySelector("[data-plan-day-tabs]");
    title.textContent = plan.title;
    summary.textContent = plan.summary;
    source.textContent = plan.generatedBy === "GEMINI"
      ? "✦ Gemini가 입력한 여행 조건을 바탕으로 생성한 초안입니다."
      : "✦ Gemini API 키가 설정되지 않아 기본 여행 초안을 보여드리고 있어요.";
    currentPlan = plan;
    activeDayIndex = 0;
    dayTabs.replaceChildren(...(plan.days || []).map(function (day, index) {
      const button = document.createElement("button");
      button.type = "button";
      button.textContent = "DAY " + day.day;
      button.setAttribute("aria-selected", String(index === 0));
      button.addEventListener("click", function () { selectPlanDay(index); });
      return button;
    }));
  }

  function renderRecommendedPlaces(places) {
    const placeList = document.querySelector("[data-plan-place-list]");
    placeList.replaceChildren(...(places || []).map(function (place) {
      const item = document.createElement("li");
      const copy = document.createElement("div");
      const category = document.createElement("small");
      const name = document.createElement("strong");
      const description = document.createElement("p");

      category.textContent = place.category;
      name.textContent = place.number + ". " + place.name;
      description.textContent = place.description;
      copy.append(category, name, description);
      item.append(copy);
      return item;
    }));
  }

  function clearKakaoOverlays() {
    kakaoOverlays.forEach(function (overlay) { overlay.setMap(null); });
    kakaoOverlays = [];
    if (kakaoRouteLine) {
      kakaoRouteLine.setMap(null);
      kakaoRouteLine = null;
    }
  }

  function keywordSearch(query) {
    return new Promise(function (resolve) {
      kakaoPlaces.keywordSearch(query, function (data, status) {
        if (status === window.kakao.maps.services.Status.OK && data.length) {
          resolve(data[0]);
          return;
        }
        resolve(null);
      });
    });
  }

  async function findRecommendedPlace(place) {
    const withDestination = await keywordSearch(destinationText + " " + place.name);
    if (withDestination) return { ...place, kakaoPlace: withDestination };
    const byName = await keywordSearch(place.name);
    if (byName) return { ...place, kakaoPlace: byName };
    if (place.category === "숙소") {
      const accommodation = await keywordSearch(destinationText + " 호텔");
      if (accommodation) return { ...place, kakaoPlace: accommodation };
    }
    return null;
  }

  async function renderKakaoMap(places) {
    const renderSequence = ++mapRenderSequence;
    if (!window.kakao?.maps) {
      mapStatus.textContent = "카카오 지도 키를 설정하면 실제 지도와 마커가 표시됩니다.";
      return;
    }

    window.kakao.maps.load(async function () {
      if (!kakaoMap) {
        mapContainer.replaceChildren();
        kakaoMap = new window.kakao.maps.Map(mapContainer, {
          center: new window.kakao.maps.LatLng(37.5665, 126.9780),
          level: 6,
        });
        kakaoPlaces = new window.kakao.maps.services.Places();
      }

      clearKakaoOverlays();
      if (!places?.length) {
        mapStatus.textContent = "이 날짜에 지도에 표시할 추천 장소가 없어요.";
        return;
      }
      mapStatus.textContent = "추천 장소를 지도에서 찾는 중";
      const foundPlaces = (await Promise.all((places || []).map(findRecommendedPlace))).filter(Boolean);
      if (renderSequence !== mapRenderSequence) return;
      if (!foundPlaces.length) {
        mapStatus.textContent = "추천 장소의 좌표를 찾지 못했어요.";
        return;
      }

      const bounds = new window.kakao.maps.LatLngBounds();
      const path = [];
      foundPlaces.forEach(function (place) {
        const position = new window.kakao.maps.LatLng(Number(place.kakaoPlace.y), Number(place.kakaoPlace.x));
        const marker = document.createElement("button");
        marker.type = "button";
        marker.className = "number-map-marker";
        marker.textContent = String(place.number);
        marker.title = place.category + " · " + place.kakaoPlace.place_name;
        kakaoOverlays.push(new window.kakao.maps.CustomOverlay({
          map: kakaoMap,
          position: position,
          content: marker,
          yAnchor: 1,
        }));
        bounds.extend(position);
        path.push(position);
      });
      if (path.length > 1) {
        kakaoRouteLine = new window.kakao.maps.Polyline({
          path: path,
          strokeWeight: 4,
          strokeColor: "#6372df",
          strokeOpacity: .85,
          strokeStyle: "solid",
        });
        kakaoRouteLine.setMap(kakaoMap);
      }
      kakaoMap.relayout();
      kakaoMap.setBounds(bounds);
      mapStatus.textContent = foundPlaces.length + "곳의 추천 장소를 지도에 표시했어요.";
    });
  }

  async function generatePlan() {
    const submitButton = form.querySelector("button");
    const request = {
      destination: destination,
      startDate: startDate,
      endDate: endDate,
      travelers: travelers,
      companion: form.elements.companion.value,
      theme: form.elements.theme.value,
      pace: form.elements.pace.value,
      budget: form.elements.budget.value,
    };
    showPlanState("loading");
    submitButton.disabled = true;
    try {
      const response = await fetch("/api/v1/ai-trip-plans/generate", {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify(request),
      });
      const payload = await response.json().catch(function () { return null; });
      if (!response.ok || !payload || !payload.success) throw new Error("AI_TRIP_PLAN_REQUEST_FAILED");
      renderPlan(payload.data);
      showPlanState("result");
      window.requestAnimationFrame(function () { selectPlanDay(activeDayIndex); });
    } catch (requestError) {
      showPlanState("error");
    } finally {
      submitButton.disabled = false;
    }
  }

  form.addEventListener("submit", function (event) {
    event.preventDefault();
    generatePlan();
  });

  document.querySelector("[data-plan-retry]").addEventListener("click", generatePlan);
});
