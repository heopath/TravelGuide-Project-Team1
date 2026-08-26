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
  const pageHeading = document.querySelector("[data-plan-heading]");
  const pageDescription = document.querySelector("[data-plan-description]");
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

  function setPanelVisible(panel, visible) {
    panel.hidden = !visible;
    panel.style.display = visible ? "" : "none";
  }

  /* =========================================================
     초안 만들기 진행률 (예상)

     기다리는 5초 남짓은 거의 전부 서버가 외부 AI를 부르는 시간이다
     (AiTripPlanService.generateWithGemini). 그 안에서 알려줄 중간 단계가 없어
     서버가 진행률을 흘려보내도 "호출 중"만 반복하게 된다. 그래서 지나간 시간으로
     가늠하고, 화면에도 예상이라고 적는다.

     끝을 90%로 막아 둔다. 100%까지 채워 놓고 기다리게 하면 멈춘 것처럼 보인다.
     실제 응답이 오면 그때 100%로 채운다.
     ========================================================= */
  const PLAN_PROGRESS_EXPECTED_MS = 5500;
  const PLAN_PROGRESS_CAP = 90;
  const PLAN_PROGRESS_STEPS = [
    { until: 20, text: "여행 조건을 정리하고 있어요." },
    { until: 85, text: "AI가 일정 초안을 쓰고 있어요." },
    { until: 100, text: "거의 다 됐어요." },
  ];

  const planStepText = document.querySelector("[data-plan-loading-step]");
  const planProgressBar = document.querySelector("[data-plan-progress-bar]");
  const planProgressFill = document.querySelector("[data-plan-progress-fill]");
  const planProgressValue = document.querySelector("[data-plan-progress-value]");
  let planProgressTimer = null;
  let planProgressStartedAt = 0;

  /*
   * 막대 폭을 바꾼다.
   *
   * 폭에는 0.25초 전환이 걸려 있어 앞으로 갈 때는 부드럽게 늘어난다. 그런데 0으로
   * 되돌릴 때도 같은 전환이 걸려서, 실패 후 다시 시도하면 막대가 뒤로 미끄러진 뒤
   * 다시 앞으로 갔다. 값은 줄지 않는데 화면만 되돌아가 보였다.
   *
   * 되돌릴 때는 instant로 전환을 끄고 즉시 옮긴다.
   */
  function paintBarWidth(fill, value, instant) {
    if (!fill) return;
    if (!instant) {
      fill.style.width = value + "%";
      return;
    }
    fill.classList.add("plan-progress-instant");
    fill.style.width = value + "%";
    /* 새 폭을 확정한 뒤에 전환을 되살린다. 안 그러면 다음 증가분까지 끊겨 보인다. */
    void fill.offsetWidth;
    fill.classList.remove("plan-progress-instant");
  }

  function paintPlanProgress(percent, instant) {
    const value = Math.max(0, Math.min(100, Math.round(percent)));
    paintBarWidth(planProgressFill, value, instant);
    if (planProgressValue) planProgressValue.textContent = value + "%";
    if (planProgressBar) planProgressBar.setAttribute("aria-valuenow", String(value));
    const step = PLAN_PROGRESS_STEPS.find(function (candidate) { return value < candidate.until; })
        || PLAN_PROGRESS_STEPS[PLAN_PROGRESS_STEPS.length - 1];
    if (planStepText && planStepText.textContent !== step.text) planStepText.textContent = step.text;
  }

  function startPlanProgress() {
    /*
     * 이미 돌고 있으면 그대로 둔다. 화면이 뜰 때 showPlanState("loading")이 초기화와
     * generatePlan에서 두 번 불려, 시작하자마자 0으로 되돌리고 다시 세던 것을 막는다.
     */
    if (planProgressTimer) return;
    planProgressStartedAt = Date.now();
    paintPlanProgress(0, true);
    planProgressTimer = window.setInterval(function () {
      const elapsed = Date.now() - planProgressStartedAt;
      /*
       * 뒤로 갈수록 느려지게 한다. 예상보다 오래 걸려도 막대가 끝에 붙어 천천히
       * 기어가므로 멈춘 것처럼 보이지 않는다.
       */
      const ratio = 1 - Math.exp(-elapsed / PLAN_PROGRESS_EXPECTED_MS);
      paintPlanProgress(PLAN_PROGRESS_CAP * ratio);
    }, 120);
  }

  function stopPlanProgress() {
    if (planProgressTimer) window.clearInterval(planProgressTimer);
    planProgressTimer = null;
  }

  /** 응답이 온 뒤. 100%를 잠깐 보여주고 결과로 넘어간다. */
  function finishPlanProgress() {
    stopPlanProgress();
    paintPlanProgress(100);
  }

  function showPlanState(state) {
    if (state === "loading") startPlanProgress();
    else stopPlanProgress();
    setPanelVisible(loading, state === "loading");
    setPanelVisible(error, state === "error");
    setPanelVisible(result, state === "result");
    if (pageHeading && pageDescription) {
      const copy = {
        loading: ["AI가 여행계획을 준비하고 있어요", "입력한 기본정보와 여행 스타일을 바탕으로 일정 초안을 만들고 있어요."],
        result: ["AI가 여행계획을 준비했어요", "입력한 기본정보와 여행 스타일을 바탕으로 만든 일정 초안이에요."],
        error: ["여행계획을 만들지 못했어요", "기본정보와 여행 스타일을 확인한 뒤 다시 시도해 주세요."]
      }[state];
      if (copy) {
        pageHeading.textContent = copy[0];
        pageDescription.textContent = copy[1];
      }
    }
  }

  showPlanState("loading");

  function formatDate(value, includeYear) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return "날짜 미입력";
    const date = new Date(value + "T00:00:00");
    const weekday = new Intl.DateTimeFormat("ko-KR", { weekday: "short" }).format(date);
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return (includeYear === false ? "" : date.getFullYear() + ".") + month + "." + day + "(" + weekday + ")";
  }

  function buildAutoTitle(destinationLabel, date) {
    const label = String(destinationLabel || "").trim();
    const month = date ? Number(String(date).split("-")[1]) : 0;
    return label ? (month ? month + "월의 " : "") + label + " 여행" : "나의 여행";
  }

  function travelDays() {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(startDate) || !/^\d{4}-\d{2}-\d{2}$/.test(endDate)) return 1;
    const difference = Math.round((Date.parse(endDate + "T00:00:00") - Date.parse(startDate + "T00:00:00")) / 86400000) + 1;
    return Math.min(30, Math.max(1, difference));
  }

  function formatPeriod() {
    if (startDate === endDate) return formatDate(startDate);
    const sameYear = /^\d{4}-/.test(startDate) && startDate.slice(0, 4) === endDate.slice(0, 4);
    return formatDate(startDate) + "–" + formatDate(endDate, sameYear ? false : true);
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
      : "✦ 입력한 여행 조건을 바탕으로 기본 여행 초안을 준비했어요.";
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

  // 같은 검색어를 지도 렌더링과 저장 과정에서 반복 호출하지 않도록 재사용한다.
  const kakaoSearchCache = new Map();

  function keywordSearchAll(query) {
    if (kakaoSearchCache.has(query)) return Promise.resolve(kakaoSearchCache.get(query));
    return new Promise(function (resolve) {
      kakaoPlaces.keywordSearch(query, function (data, status) {
        const results = status === window.kakao.maps.services.Status.OK && data ? data : [];
        kakaoSearchCache.set(query, results);
        resolve(results);
      });
    });
  }

  // AI 추천 장소명과 카카오 검색 후보를 이름·주소·카테고리 기준으로 비교해 가장 적합한 후보를 고른다.
  function scoreCandidate(place, candidate, rank) {
    const name = String(candidate.place_name || "");
    const address = String(candidate.road_address_name || candidate.address_name || "");
    const category = String(candidate.category_name || "");
    let score = 0;
    if (name === place.name) score += 5;
    else if (name.includes(place.name) || (place.name && place.name.includes(name))) score += 3;
    if (destinationText && address.includes(destinationText)) score += 2;
    if (place.category && category.includes(place.category)) score += 1;
    if (candidate.y && candidate.x) score += 1;
    score -= rank * 0.1;
    return score;
  }

  function pickBestCandidate(place, candidates) {
    if (!candidates || !candidates.length) return null;
    let best = null;
    let bestScore = -Infinity;
    candidates.forEach(function (candidate, rank) {
      const score = scoreCandidate(place, candidate, rank);
      if (score > bestScore) {
        bestScore = score;
        best = candidate;
      }
    });
    return best;
  }

  const CATEGORY_FALLBACK_QUERIES = [
    { match: "숙소", query: "호텔" },
    { match: "식사", query: "맛집" },
    { match: "카페", query: "카페" },
    { match: "교통", query: "터미널" },
  ];

  function fallbackQueryFor(place) {
    const category = String(place.category || "");
    const title = String(place.name || "");
    const found = CATEGORY_FALLBACK_QUERIES.find(function (entry) {
      return category.includes(entry.match) || title.includes(entry.match);
    });
    if (found) return found.query;
    if (title.includes("귀가") || title.includes("역") || title.includes("공항")) return "역";
    return "관광명소";
  }

  async function findRecommendedPlace(place) {
    const withDestination = await keywordSearchAll(destinationText + " " + place.name);
    let best = pickBestCandidate(place, withDestination);
    if (!best) {
      const byName = await keywordSearchAll(place.name);
      best = pickBestCandidate(place, byName);
    }
    if (!best) {
      const fallback = await keywordSearchAll(destinationText + " " + fallbackQueryFor(place));
      best = pickBestCandidate(place, fallback);
    }
    return best ? { ...place, kakaoPlace: best } : null;
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
      /* 막대를 100%까지 채운 뒤 결과로 넘긴다. 90%에서 화면이 바뀌면 덜 끝난 것처럼 보인다. */
      finishPlanProgress();
      await new Promise(function (resolve) { window.setTimeout(resolve, 260); });
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

  /* =========================================================
     장소 확인 진행률 (실제)

     저장할 때는 일정에 든 장소를 하나씩 카카오에서 찾는다. 전체 개수를 알고 하나씩
     끝나므로 여기 숫자는 가늠이 아니라 실제로 끝난 개수다.
     ========================================================= */
  const saveProgressBox = document.querySelector("[data-plan-save-progress]");
  const saveProgressBar = document.querySelector("[data-plan-save-bar]");
  const saveProgressFill = document.querySelector("[data-plan-save-fill]");
  const saveProgressValue = document.querySelector("[data-plan-save-value]");

  function showSaveProgress(done, total) {
    if (!saveProgressBox) return;
    saveProgressBox.hidden = total <= 0;
    if (total <= 0) return;
    const percent = Math.round((done / total) * 100);
    /* 시작(0/N)은 지난 저장의 폭에서 되돌아오는 것이라 즉시 옮긴다. */
    paintBarWidth(saveProgressFill, percent, done === 0);
    if (saveProgressValue) saveProgressValue.textContent = done + " / " + total;
    if (saveProgressBar) saveProgressBar.setAttribute("aria-valuenow", String(percent));
  }

  function hideSaveProgress() {
    if (saveProgressBox) saveProgressBox.hidden = true;
  }

  async function resolveAllPlanPlaces() {
    if (!await ensureKakaoPlaces()) return [];
    const totalPlaces = (currentPlan?.days || [])
        .reduce(function (sum, day) { return sum + (day.places || []).length; }, 0);
    let donePlaces = 0;
    showSaveProgress(0, totalPlaces);
    /* 하나 끝날 때마다 센다. Promise.all은 전부 끝나야 돌아오므로 각 건에 매단다. */
    const countOne = function (promise) {
      return promise.then(function (found) {
        donePlaces += 1;
        showSaveProgress(donePlaces, totalPlaces);
        return found;
      });
    };
    const resolvedPlaces = [];
    for (const day of currentPlan?.days || []) {
      const foundPlaces = await Promise.all(
          (day.places || []).map(function (place) { return countOne(findRecommendedPlace(place)); }));
      foundPlaces.filter(Boolean).forEach(function (found) {
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

  // 서버 검증 실패는 errors에만 필드별 사유가 담기므로 message만 보면 원인을 알 수 없다.
  function saveErrorMessage(payload) {
    const base = payload?.message || "AI 여행을 저장하지 못했습니다.";
    const details = (payload?.errors || [])
      .map(function (error) { return error.field + ": " + error.reason; })
      .join("\n");
    return details ? base + "\n" + details : base;
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
      /* 장소 확인이 끝나면 막대는 할 일이 없다. 남겨 두면 다음 단계에서 멈춘 것처럼 보인다. */
      hideSaveProgress();
      saveButton.textContent = "일정 준비 중...";
      const response = await fetch("/api/v1/ai-trip-plans/save", {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({
          title: basic.titleAutoGenerated === false
            ? basic.title || currentPlan.title
            : buildAutoTitle(destination, startDate),
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
        throw new Error(saveErrorMessage(payload));
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
      /* 401로 일찍 빠져나가거나 저장이 실패한 경우에도 막대가 남지 않게 한다. */
      hideSaveProgress();
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
