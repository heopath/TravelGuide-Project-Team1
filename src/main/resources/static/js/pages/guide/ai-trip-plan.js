document.addEventListener("DOMContentLoaded", function () {
  const params = new URLSearchParams(window.location.search);
  let draft = {};
  try { draft = JSON.parse(sessionStorage.getItem("tripDraft") || "{}"); } catch (error) { draft = {}; }
  const basic = draft.basic || {};
  const style = draft.style || {};
  const destination = String(basic.destinationLabel || basic.destination || params.get("destination") || "").trim();
  const startDate = basic.startDate || params.get("startDate") || params.get("departureDate") || "";
  const endDate = basic.endDate || params.get("endDate") || startDate;
  const travelers = Math.min(20, Math.max(1, Number(basic.travelerCount || params.get("travelers")) || 1));
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
  let currentConditions = null;
  let activeDayIndex = 0;
  let savingPlan = false;
  const kakaoSearchCache = new Map();
  const MAX_PLACES_PER_DAY = 5;

  function setPanelVisible(panel, visible) {
    panel.hidden = !visible;
    panel.style.display = visible ? "" : "none";
  }

  function showPlanState(state) {
    setPanelVisible(loading, state === "loading");
    setPanelVisible(error, state === "error");
    setPanelVisible(result, state === "result");
  }

  showPlanState("loading");

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
    const normalizedQuery = String(query || "").trim();
    if (!normalizedQuery) return Promise.resolve([]);
    if (kakaoSearchCache.has(normalizedQuery)) return kakaoSearchCache.get(normalizedQuery);
    const request = new Promise(function (resolve) {
      kakaoPlaces.keywordSearch(query, function (data, status) {
        if (status === window.kakao.maps.services.Status.OK && data.length) {
          resolve(data);
          return;
        }
        resolve([]);
      });
    });
    kakaoSearchCache.set(normalizedQuery, request);
    return request;
  }

  function normalizePlaceText(value) {
    return String(value || "").toLowerCase().replace(/[^0-9a-z가-힣]/g, "");
  }

  function destinationKeyword() {
    return destinationText
      .replace(/(특별자치도|특별자치시|특별시|광역시|도|시|군|구)$/u, "")
      .trim() || destinationText;
  }

  function categoryKeyword(place) {
    const text = normalizePlaceText((place?.category || "") + " " + (place?.name || ""));
    if (/(숙박|숙소|호텔|리조트)/u.test(text)) return "호텔";
    if (/(음식|식사|맛집|점심|저녁|아침)/u.test(text)) return "맛집";
    if (/(카페|커피|휴식)/u.test(text)) return "카페";
    if (/(교통|역|터미널|공항|귀가)/u.test(text)) return "역";
    return "관광명소";
  }

  function candidateScore(place, candidate, resultIndex) {
    const targetName = normalizePlaceText(place?.name);
    const candidateName = normalizePlaceText(candidate?.place_name);
    const candidateAddress = normalizePlaceText(
      (candidate?.road_address_name || "") + " " + (candidate?.address_name || "")
    );
    const candidateCategory = normalizePlaceText(candidate?.category_name);
    const region = normalizePlaceText(destinationKeyword());
    const category = normalizePlaceText(categoryKeyword(place));
    let score = Math.max(0, 15 - resultIndex);
    if (candidateName === targetName) score += 120;
    else if (candidateName.includes(targetName) || targetName.includes(candidateName)) score += 70;
    if (region && candidateAddress.includes(region)) score += 45;
    if (category && candidateCategory.includes(category)) score += 30;
    if (Number.isFinite(Number(candidate?.x)) && Number.isFinite(Number(candidate?.y))) score += 5;
    return score;
  }

  async function findRecommendedPlace(place) {
    const region = destinationKeyword();
    const queries = [
      region + " " + place.name,
      place.name,
      region + " " + categoryKeyword(place),
    ];
    const candidateMap = new Map();
    for (const query of queries) {
      const candidates = await keywordSearch(query);
      candidates.forEach(function (candidate, index) {
        const key = String(candidate.id || candidate.place_url || candidate.place_name);
        const score = candidateScore(place, candidate, index);
        const previous = candidateMap.get(key);
        if (!previous || score > previous.score) candidateMap.set(key, { candidate: candidate, score: score });
      });
    }
    const best = Array.from(candidateMap.values()).sort(function (left, right) {
      return right.score - left.score;
    })[0];
    return best ? { ...place, kakaoPlace: best.candidate } : null;
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
    const companionLabels = { ALONE: "혼자", FRIEND: "친구", COUPLE: "커플", FAMILY: "가족", PARENTS: "부모님", CHILDREN: "아이와 함께" };
    const request = {
      destination: destination,
      startDate: startDate,
      endDate: endDate,
      travelers: travelers,
      companion: companionLabels[basic.companion] || basic.companion || "선호 없음",
      purpose: style.purpose || "관광",
      pace: style.pace || "균형있는",
      transport_preference: style.transportPreference || "선호 없음",
      food_preference: style.foodPreference || "선호 없음",
      accommodation_style: style.accommodationStyle || "선호 없음",
      budget_amount: Number(basic.totalBudget || 0),
    };
    currentConditions = request;
    showPlanState("loading");
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
    } finally {}
  }

  function ensureKakaoPlaces() {
    return new Promise(function (resolve) {
      if (!window.kakao?.maps) { resolve(false); return; }
      window.kakao.maps.load(function () {
        if (!kakaoPlaces) kakaoPlaces = new window.kakao.maps.services.Places();
        resolve(true);
      });
    });
  }

  async function resolveAllPlanPlaces() {
    if (!await ensureKakaoPlaces()) {
      throw new Error("카카오 지도를 불러오지 못해 AI 추천 장소를 확인할 수 없습니다.");
    }
    const resolvedPlaces = [];
    for (const day of currentPlan?.days || []) {
      const schedules = day.items || [];
      const recommendations = day.places || [];
      if (recommendations.length < 1 || recommendations.length > MAX_PLACES_PER_DAY) {
        throw new Error("AI 추천 일정은 하루 1개부터 " + MAX_PLACES_PER_DAY + "개 장소까지 저장할 수 있습니다.");
      }
      if (schedules.length !== recommendations.length) {
        throw new Error("AI 일정의 모든 항목에 장소 정보가 필요합니다. 일정을 다시 생성해 주세요.");
      }
      const foundPlaces = await Promise.all(recommendations.map(findRecommendedPlace));
      const unresolvedNames = recommendations.filter(function (_, index) {
        return !foundPlaces[index];
      }).map(function (place) { return place.name; });
      if (unresolvedNames.length) {
        throw new Error("카카오 장소를 찾지 못했습니다: " + unresolvedNames.join(", ") + ". 일정을 다시 생성해 주세요.");
      }
      foundPlaces.forEach(function (found) {
        const kakaoPlace = found.kakaoPlace;
        resolvedPlaces.push({
          day: day.day,
          number: found.number,
          externalPlaceId: String(kakaoPlace.id),
          name: kakaoPlace.place_name,
          address: kakaoPlace.road_address_name || kakaoPlace.address_name || "",
          latitude: Number(kakaoPlace.y),
          longitude: Number(kakaoPlace.x),
          phone: kakaoPlace.phone || "",
          websiteUrl: kakaoPlace.place_url || "",
          category: kakaoPlace.category_name || found.category || "",
          description: found.description || "",
        });
      });
    }
    return resolvedPlaces;
  }

  async function savePlan() {
    const saveButton = document.querySelector("[data-plan-save]");
    if (savingPlan || !currentPlan || !currentConditions || !saveButton) return;
    savingPlan = true;
    saveButton.disabled = true;
    saveButton.setAttribute("aria-busy", "true");
    saveButton.textContent = "장소 확인 중...";
    try {
      const resolvedPlaces = await resolveAllPlanPlaces();
      saveButton.textContent = "일정 준비 중...";
      const response = await fetch("/api/v1/ai-trip-plans/save", {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({
          title: basic.title || currentPlan.title,
          conditions: currentConditions,
          plan: currentPlan,
          resolvedPlaces: resolvedPlaces,
        }),
      });
      const payload = await response.json().catch(function () { return null; });
      if (response.status === 401) {
        saveButton.disabled = false;
        saveButton.removeAttribute("aria-busy");
        saveButton.textContent = "이 일정으로 직접 수정하기";
        if (window.confirm("AI 여행을 저장하려면 로그인이 필요합니다. 로그인 페이지로 이동할까요?")) {
          window.location.href = "/auth/login?redirect=" + encodeURIComponent(window.location.pathname);
        }
        return;
      }
      if (!response.ok || !payload?.success || !payload.data?.tripId) {
        throw new Error(payload?.message || "AI 여행을 저장하지 못했습니다.");
      }
      const savedDraft = readCurrentDraft();
      savedDraft.trip = {
        tripId: payload.data.tripId,
        source: "AI",
        status: "CONFIRMED",
      };
      sessionStorage.setItem("tripDraft", JSON.stringify(savedDraft));
      saveButton.textContent = "일정 불러오는 중...";
      window.setTimeout(function () {
        window.location.href = "/trips/" + payload.data.tripId + "/schedule";
      }, 500);
    } catch (saveError) {
      saveButton.disabled = false;
      saveButton.removeAttribute("aria-busy");
      saveButton.textContent = "이 일정으로 직접 수정하기";
      window.alert(saveError.message || "AI 여행을 저장하지 못했습니다.");
    } finally {
      savingPlan = false;
    }
  }

  function readCurrentDraft() {
    try { return JSON.parse(sessionStorage.getItem("tripDraft") || "{}"); } catch (error) { return {}; }
  }

  document.querySelector("[data-plan-retry]").addEventListener("click", generatePlan);
  document.querySelector("[data-plan-save]").addEventListener("click", savePlan);
  if (!destination || !startDate || !endDate || !style.purpose) {
    showPlanState("error");
    document.querySelector("[data-plan-error] p").textContent = "기본정보와 여행 스타일을 모두 입력한 뒤 다시 시도해 주세요.";
  } else {
    generatePlan();
  }
});
