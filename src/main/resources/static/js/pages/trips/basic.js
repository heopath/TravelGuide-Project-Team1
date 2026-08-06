/* 여행 기본 정보 입력과 초안 저장 */
document.addEventListener("DOMContentLoaded", function () {
  const DRAFT_KEY = "tripDraft";
  const form = document.querySelector("#tripBasicForm");
  if (!form) return;

  const fields = {
    destination: document.querySelector("#destination"),
    destinationLabel: document.querySelector("#destinationLabel"),
    destinationTrigger: document.querySelector("#destinationTrigger"),
    startDate: document.querySelector("#startDate"),
    endDate: document.querySelector("#endDate"),
    totalBudget: document.querySelector("#totalBudget"),
    budgetPerPerson: document.querySelector("#budgetPerPerson"),
    budgetPerPersonHint: document.querySelector("#budgetPerPersonHint"),
    travelerCountValue: document.querySelector("#travelerCountValue"),
    travelerCountMinus: document.querySelector("#travelerCountMinus"),
    travelerCountPlus: document.querySelector("#travelerCountPlus"),
    duration: document.querySelector("#tripDurationValue"),
    message: document.querySelector("#basicFormMessage"),
    nextButton: document.querySelector("#basicNextButton"),
  };
  const companionButtons = document.querySelectorAll("[data-companion]");
  let selectedCompanion = "";
  let selectedDestination = null;
  let saving = false;
  let calendarViewDate = new Date();
  let destinationSearchTimer = null;
  let destinationRequestId = 0;
  let travelerCount = 1;
  const MAX_TRAVELERS = 20;
  const MAX_TRIP_DAYS = 30;
  const companionTypeMap = { ALONE: "SOLO", FRIEND: "FRIENDS", COUPLE: "COUPLE", FAMILY: "FAMILY", PARENTS: "FAMILY", CHILDREN: "FAMILY" };

  const DEFAULT_DESTINATIONS = [
    { label: "서울", value: "SEOUL", countryCode: "KR" },
    { label: "부산", value: "BUSAN", countryCode: "KR" },
    { label: "제주도", value: "JEJU", countryCode: "KR" },
    { label: "경주", value: "GYEONGJU", countryCode: "KR" },
    { label: "강릉", value: "GANGNEUNG", countryCode: "KR" },
    { label: "도쿄", value: "TOKYO", countryCode: "JP" },
    { label: "오사카", value: "OSAKA", countryCode: "JP" },
    { label: "파리", value: "PARIS", countryCode: "FR" },
    { label: "바르셀로나", value: "BARCELONA", countryCode: "ES" },
    { label: "방콕", value: "BANGKOK", countryCode: "TH" },
    { label: "다낭", value: "DA_NANG", countryCode: "VN" },
    { label: "뉴욕", value: "NEW_YORK", countryCode: "US" },
  ];
  const COUNTRY_NAMES = {
    KR: "대한민국",
    JP: "일본",
    FR: "프랑스",
    ES: "스페인",
    TH: "태국",
    VN: "베트남",
    US: "미국",
  };
  const COUNTRY_TIME_ZONES = {
    KR: "Asia/Seoul",
    JP: "Asia/Tokyo",
    FR: "Europe/Paris",
    ES: "Europe/Madrid",
    TH: "Asia/Bangkok",
    VN: "Asia/Ho_Chi_Minh",
    US: "America/New_York",
  };

  function getCountryName(destination) {
    return destination.countryName || COUNTRY_NAMES[destination.countryCode] || destination.countryCode || "";
  }

  function getDestinationDisplayName(destination) {
    const countryName = getCountryName(destination);
    return countryName ? destination.label + " · " + countryName : destination.label;
  }

  function getCityName(place) {
    const region = String(place.region || "").trim();
    const city = String(place.city || "").trim();
    const metropolitanNames = {
      서울: "서울특별시",
      부산: "부산광역시",
      대구: "대구광역시",
      인천: "인천광역시",
      광주: "광주광역시",
      대전: "대전광역시",
      울산: "울산광역시",
      세종: "세종특별자치시",
    };

    // API의 city 값이 수영구·해운대구처럼 구 단위로 내려오면 상위 도시명만 사용한다.
    if (region && city.endsWith("구")) return metropolitanNames[region] || region;
    if (region && metropolitanNames[region]) return metropolitanNames[region];
    return city || region || String(place.name || "").trim();
  }

  function readDraft() {
    try {
      return JSON.parse(sessionStorage.getItem(DRAFT_KEY) || "{}");
    } catch (error) {
      return {};
    }
  }

  function writeDraft(draft) {
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify(draft));
  }

  function isAiPlan() {
    return (readDraft().plan || {}).mode === "AI";
  }

  function updateFlowUi() {
    const ai = isAiPlan();
    const aiStep = document.querySelector("[data-ai-step]");
    const finalStep = document.querySelector("[data-final-step]");
    if (aiStep) aiStep.hidden = !ai;
    if (finalStep) {
      finalStep.querySelector("span").textContent = ai ? "4" : "3";
      finalStep.querySelector("b").textContent = ai ? "추천 결과" : "여행 일정";
    }
    fields.nextButton.textContent = ai ? "여행 스타일 설정 →" : "여행 일정 만들기 →";
  }

  function buildAutoTitle(destinationLabel, startDate) {
    const label = String(destinationLabel || "").trim();
    const month = startDate ? Number(String(startDate).split("-")[1]) : 0;
    return label ? (month ? month + "월의 " : "") + label + " 여행" : "나의 여행";
  }

  function setError(id, message) {
    const target = document.querySelector("#" + id);
    if (target) target.textContent = message || "";
  }

  function parseDate(value) {
    return value ? new Date(value + "T00:00:00") : null;
  }

  function getDestinationTimeZone() {
    return COUNTRY_TIME_ZONES[selectedDestination && selectedDestination.countryCode]
      || Intl.DateTimeFormat().resolvedOptions().timeZone
      || "Asia/Seoul";
  }

  function getTodayKeyInDestinationTimeZone() {
    const parts = new Intl.DateTimeFormat("en-CA", {
      timeZone: getDestinationTimeZone(),
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).formatToParts(new Date());
    const values = {};
    parts.forEach(function (part) {
      if (part.type !== "literal") values[part.type] = part.value;
    });
    return values.year + "-" + values.month + "-" + values.day;
  }

  function syncTravelDateMinimum() {
    const minimum = getTodayKeyInDestinationTimeZone();
    fields.startDate.min = minimum;
    fields.endDate.min = fields.startDate.value && fields.startDate.value >= minimum
      ? fields.startDate.value
      : minimum;
    if (fields.startDate.value && fields.startDate.value < minimum) fields.startDate.value = "";
    if (fields.endDate.value && fields.endDate.value < fields.endDate.min) fields.endDate.value = "";
    const minimumDate = parseDate(minimum);
    if (calendarViewDate < new Date(minimumDate.getFullYear(), minimumDate.getMonth(), 1)) {
      calendarViewDate = new Date(minimumDate.getFullYear(), minimumDate.getMonth(), 1);
    }
  }

  function updateDuration() {
    syncTravelDateMinimum();
    const start = parseDate(fields.startDate.value);
    const end = parseDate(fields.endDate.value);
    fields.endDate.min = fields.startDate.value || getTodayKeyInDestinationTimeZone();
    if (!start || !end || end < start) {
      fields.duration.textContent = "기간을 선택해주세요.";
      return;
    }
    const nights = Math.round((end - start) / 86400000);
    fields.duration.textContent = nights + "박 " + (nights + 1) + "일";
  }

  function updateBudgetSummary() {
    const totalBudget = Number(fields.totalBudget.value) || 0;
    const budgetPerPerson = Math.floor(totalBudget / travelerCount);
    fields.budgetPerPerson.value = String(budgetPerPerson);
    fields.travelerCountValue.textContent = travelerCount + "명";
    fields.budgetPerPersonHint.textContent = "1인당 약 " + budgetPerPerson.toLocaleString("ko-KR") + "원";
    fields.travelerCountMinus.disabled = travelerCount <= 1;
    fields.travelerCountPlus.disabled = selectedCompanion === "ALONE" || travelerCount >= MAX_TRAVELERS;
  }

  function openRangeCalendar() {
    const calendar = document.querySelector("#tripRangeCalendar");
    if (calendar) {
      calendar.classList.add("is-open");
      renderRangeCalendar();
    }
  }

  function closeRangeCalendar() {
    const calendar = document.querySelector("#tripRangeCalendar");
    if (calendar) calendar.classList.remove("is-open");
  }

  function dateKey(date) {
    return date.getFullYear() + "-" + String(date.getMonth() + 1).padStart(2, "0") + "-" + String(date.getDate()).padStart(2, "0");
  }

  function renderRangeCalendar() {
    syncTravelDateMinimum();
    const title = document.querySelector("#rangeCalendarTitle");
    const hint = document.querySelector("#rangeCalendarHint");
    const startValue = fields.startDate.value;
    const endValue = fields.endDate.value;
    const monthNames = ["1월", "2월", "3월", "4월", "5월", "6월", "7월", "8월", "9월", "10월", "11월", "12월"];
    if (title) title.textContent = startValue && endValue ? startValue + " ~ " + endValue : "여행 기간을 선택하세요";
    if (hint) hint.textContent = startValue && !endValue ? "종료일을 선택하세요." : "시작일을 선택한 후 종료일을 선택하세요.";

    [0, 1].forEach(function (offset) {
      const monthDate = new Date(calendarViewDate.getFullYear(), calendarViewDate.getMonth() + offset, 1);
      const monthTitle = document.querySelector("#rangeMonthTitle" + offset);
      const grid = document.querySelector("#rangeMonthGrid" + offset);
      if (!monthTitle || !grid) return;
      monthTitle.textContent = monthDate.getFullYear() + "년 " + monthNames[monthDate.getMonth()];
      grid.replaceChildren();
      for (let blank = 0; blank < monthDate.getDay(); blank += 1) grid.appendChild(document.createElement("span"));
      const lastDay = new Date(monthDate.getFullYear(), monthDate.getMonth() + 1, 0).getDate();
      for (let day = 1; day <= lastDay; day += 1) {
        const current = new Date(monthDate.getFullYear(), monthDate.getMonth(), day);
        const key = dateKey(current);
        const button = document.createElement("button");
        button.type = "button";
        button.textContent = String(day);
        button.dataset.date = key;
        const isPastDestinationDate = key < getTodayKeyInDestinationTimeZone();
        const startDate = startValue ? parseDate(startValue) : null;
        const maximumEndDate = startDate
          ? new Date(startDate.getTime() + (MAX_TRIP_DAYS - 1) * 86400000)
          : null;
        const isOverMaximumTripDate = maximumEndDate && current > maximumEndDate && !endValue;
        if (isPastDestinationDate || isOverMaximumTripDate) {
          button.disabled = true;
          button.setAttribute("aria-disabled", "true");
          button.classList.add("is-disabled");
        }
        if (key === startValue) button.classList.add("is-start");
        if (key === endValue) button.classList.add("is-end");
        if (startValue && endValue && key > startValue && key < endValue) button.classList.add("is-between");
        button.addEventListener("click", function () {
          if (button.disabled) return;
          if (!fields.startDate.value || fields.endDate.value) {
            fields.startDate.value = key;
            fields.endDate.value = "";
          } else if (key < fields.startDate.value) {
            fields.startDate.value = key;
          } else if (startDate && current > maximumEndDate) {
            return;
          } else {
            fields.endDate.value = key;
          }
          fields.endDate.min = fields.startDate.value || getTodayKeyInDestinationTimeZone();
          updateDuration();
          setError("startDateError", "");
          setError("endDateError", "");
          renderRangeCalendar();
          if (fields.startDate.value && fields.endDate.value) closeRangeCalendar();
        });
        grid.appendChild(button);
      }
    });
  }

  function selectCompanion(button, applyDefaultCount) {
    selectedCompanion = button.dataset.companion;
    if (applyDefaultCount) {
      travelerCount = selectedCompanion === "ALONE" ? 1 : 2;
    }
    companionButtons.forEach(function (candidate) {
      const selected = candidate === button;
      candidate.classList.toggle("selected", selected);
      candidate.setAttribute("aria-pressed", String(selected));
    });
    updateBudgetSummary();
    setError("companionError", "");
  }

  function renderDestinationResults(list, destinations, message) {
    list.replaceChildren();
    if (!destinations.length) {
      const empty = document.createElement("p");
      empty.className = "destination-empty";
      empty.textContent = message || "검색 결과가 없습니다.";
      list.appendChild(empty);
      return;
    }
    destinations.forEach(function (destination) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "destination-option";
      button.setAttribute("role", "option");
      const label = document.createElement("span");
      const country = document.createElement("small");
      label.textContent = destination.label;
      country.textContent = getCountryName(destination);
      button.append(label, country);
      button.addEventListener("click", function () {
        applyDestination(destination);
      });
      list.appendChild(button);
    });
  }

  function applyDestination(destination) {
    const draft = readDraft();
    const basic = draft.basic || {};
    const previousAutoTitle = buildAutoTitle(basic.destinationLabel || basic.destination, basic.startDate);
    const titleIsAutoGenerated = !basic.title || basic.titleAutoGenerated !== false || basic.title === previousAutoTitle;
    selectedDestination = destination;
    fields.destination.value = destination.value;
    fields.destinationLabel.textContent = destination.label;
    fields.destinationTrigger.value = getDestinationDisplayName(destination);
    fields.destinationTrigger.classList.add("has-value");
    fields.destinationTrigger.setAttribute("aria-expanded", "false");
    setError("destinationError", "");
    syncTravelDateMinimum();
    const destinationPanel = document.querySelector("#destinationPanel");
    const destinationList = document.querySelector("#destinationInlineList");
    if (destinationList) destinationList.replaceChildren();
    if (destinationPanel) destinationPanel.classList.remove("is-active");
    if (titleIsAutoGenerated) {
      basic.title = buildAutoTitle(destination.label, basic.startDate);
      basic.titleAutoGenerated = true;
      draft.basic = basic;
      writeDraft(draft);
    }
  }

  async function searchDestinations(keyword, list) {
    const requestId = ++destinationRequestId;
    renderDestinationResults(list, [], "목적지를 찾는 중입니다.");
    const params = new URLSearchParams({ page: "0", size: "100" });
    if (keyword) params.set("keyword", keyword);
    try {
      const response = await fetch("/api/v1/places?" + params.toString(), {
        headers: { Accept: "application/json" },
        allMyTripsLoading: false,
      });
      if (!response.ok) throw new Error("목적지 검색 요청에 실패했습니다.");
      const payload = await response.json();
      const places = Array.isArray(payload) ? payload : (payload.data || payload.content || []);
      const unique = new Map();
      places.forEach(function (place) {
        const label = getCityName(place);
        if (!label || unique.has(label)) return;
        unique.set(label, {
          label: label,
          value: place.placeId || place.id || place.region || place.city || place.name,
          countryCode: place.countryCode,
        });
      });
      if (requestId !== destinationRequestId) return;
      renderDestinationResults(list, Array.from(unique.values()).slice(0, 8), "일치하는 도시나 지역이 없습니다.");
    } catch (error) {
      if (requestId !== destinationRequestId) return;
      const normalizedKeyword = keyword.toLowerCase();
      const fallback = DEFAULT_DESTINATIONS.filter(function (destination) {
        const matchesKeyword = !normalizedKeyword || destination.label.toLowerCase().includes(normalizedKeyword);
        return matchesKeyword;
      });
      renderDestinationResults(list, fallback.slice(0, 8), "일치하는 도시나 지역이 없습니다.");
    }
  }

  function validate() {
    let valid = true;
    ["destinationError", "startDateError", "endDateError", "companionError", "budgetError"].forEach(function (id) {
      setError(id, "");
    });
    fields.message.textContent = "";
    fields.message.classList.remove("is-error", "is-success");
    const budgetInput = document.querySelector(".budget-input");
    if (budgetInput) budgetInput.classList.remove("is-error");

    if (!fields.destination.value.trim()) {
      setError("destinationError", "여행 목적지를 입력해주세요.");
      valid = false;
    }
    const minimum = parseDate(getTodayKeyInDestinationTimeZone());
    const start = parseDate(fields.startDate.value);
    const end = parseDate(fields.endDate.value);
    if (start && start < minimum) {
      setError("startDateError", "여행지 현지 기준 오늘 이전 날짜는 선택할 수 없습니다.");
      valid = false;
    }
    if (end && end < minimum) {
      setError("endDateError", "여행지 현지 기준 오늘 이전 날짜는 선택할 수 없습니다.");
      valid = false;
    }
    if (!start) {
      setError("startDateError", "시작일을 선택해주세요.");
      valid = false;
    }
    if (!end) {
      setError("endDateError", "종료일을 선택해주세요.");
      valid = false;
    } else if (start && end < start) {
      setError("endDateError", "종료일은 시작일보다 빠를 수 없습니다.");
      valid = false;
    } else if (start && end && Math.floor((end - start) / 86400000) + 1 > MAX_TRIP_DAYS) {
      setError("endDateError", "여행 기간은 시작일과 종료일을 포함해 최대 30일까지 선택할 수 있습니다.");
      valid = false;
    }
    if (!selectedCompanion) {
      setError("companionError", "동행자 유형을 선택해주세요.");
      valid = false;
    }
    const budgetValue = fields.totalBudget.value.trim();
    const budget = Number(budgetValue);
    if (!budgetValue || !Number.isFinite(budget) || budget < 0) {
      if (budgetInput) budgetInput.classList.add("is-error");
      setError("budgetError", "");
      valid = false;
    }
    if (!Number.isInteger(travelerCount) || travelerCount < 1 || travelerCount > MAX_TRAVELERS) {
      setError("companionError", "여행 인원은 1명부터 최대 20명까지 선택할 수 있습니다.");
      valid = false;
    }
    return valid;
  }

  function collectBasic() {
    const previous = readDraft().basic || {};
    return {
      title: previous.titleAutoGenerated === false
        ? previous.title
        : buildAutoTitle(fields.destinationLabel.textContent, fields.startDate.value),
      titleAutoGenerated: previous.titleAutoGenerated !== false,
      destination: fields.destination.value.trim(),
      destinationLabel: fields.destinationLabel.textContent,
      country: selectedDestination && selectedDestination.countryCode || "",
      countryCode: selectedDestination && selectedDestination.countryCode || "",
      startDate: fields.startDate.value,
      endDate: fields.endDate.value,
      companion: selectedCompanion,
      travelerCount: travelerCount,
      totalBudget: Number(fields.totalBudget.value),
      budgetPerPerson: Number(fields.budgetPerPerson.value),
    };
  }

  function setSaving(active) {
    saving = active;
    fields.nextButton.disabled = active;
    fields.nextButton.setAttribute("aria-busy", String(active));
    fields.nextButton.textContent = active
      ? (isAiPlan() ? "입력 내용 저장 중..." : "여행 생성 중...")
      : (isAiPlan() ? "여행 스타일 설정 →" : "여행 일정 만들기 →");
  }

  async function createManualTrip(draft) {
    const basic = draft.basic || {};
    const response = await fetch("/api/v1/trips", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({
        title: basic.title || buildAutoTitle(basic.destinationLabel, basic.startDate),
        destinationName: basic.destinationLabel || basic.destination,
        startDate: basic.startDate,
        endDate: basic.endDate,
        companionType: companionTypeMap[basic.companion] || "OTHER",
        companionCount: Number(basic.travelerCount || 1),
        budgetAmount: Number(basic.totalBudget || 0),
      }),
    });
    if (response.status === 401) {
      const unauthorized = new Error("로그인이 필요합니다.");
      unauthorized.unauthorized = true;
      throw unauthorized;
    }
    const body = await response.json().catch(function () { return {}; });
    if (!response.ok) throw new Error(body.message || "여행 정보를 저장하지 못했습니다.");
    const tripId = Number(body.data && body.data.tripId);
    if (!Number.isInteger(tripId) || tripId < 1) throw new Error("생성된 여행 ID를 확인할 수 없습니다.");
    return tripId;
  }

  // Trip API가 준비되기 전까지는 현재 브라우저의 sessionStorage를 mock 저장소로 사용한다.
  // API 연결 시 window.ALL_MY_TRIPS_TRIP_API_READY를 true로 바꾸면 DraftStore를 사용할 수 있다.
  async function saveDraft(draft) {
    if (window.ALL_MY_TRIPS_TRIP_API_READY === true
      && window.AllMyTripsDraftStore
      && typeof window.AllMyTripsDraftStore.save === "function") {
      return window.AllMyTripsDraftStore.save(draft);
    }

    await new Promise(function (resolve) { window.setTimeout(resolve, 350); });
    return {
      data: { mock: true, nextUrl: "/trips/new/style" },
      message: "여행 기본정보를 임시 저장했습니다.",
    };
  }

  companionButtons.forEach(function (button) {
    button.addEventListener("click", function () {
      if (selectedCompanion === button.dataset.companion) {
        selectedCompanion = "";
        travelerCount = 1;
        companionButtons.forEach(function (candidate) {
          candidate.classList.remove("selected");
          candidate.setAttribute("aria-pressed", "false");
        });
        updateBudgetSummary();
        return;
      }
      selectCompanion(button, true);
    });
  });
  const destinationSearchInput = fields.destinationTrigger;
  const destinationList = document.querySelector("#destinationInlineList");
  const destinationPanel = document.querySelector("#destinationPanel");
  if (destinationSearchInput && destinationPanel && destinationList) {
    destinationSearchInput.addEventListener("focus", function () {
      destinationPanel.classList.add("is-active");
      destinationSearchInput.setAttribute("aria-expanded", "true");
    });
    destinationSearchInput.addEventListener("input", function () {
      destinationPanel.classList.add("is-active");
      window.clearTimeout(destinationSearchTimer);
      destinationSearchTimer = window.setTimeout(function () {
        searchDestinations(destinationSearchInput.value.trim(), destinationList);
      }, 220);
    });
    destinationSearchInput.addEventListener("keydown", function (event) {
      const options = Array.from(destinationList.querySelectorAll(".destination-option"));
      if (event.key === "ArrowDown" && options.length) {
        event.preventDefault();
        options[0].focus();
      }
    });
    destinationSearchInput.addEventListener("keydown", function (event) {
      if (event.key === "Enter") {
        event.preventDefault();
        const firstOption = destinationList.querySelector(".destination-option");
        if (firstOption) {
          firstOption.click();
        } else {
          searchDestinations(destinationSearchInput.value.trim(), destinationList);
        }
      }
    });
    searchDestinations("", destinationList);
  }
  document.querySelectorAll("[data-destination-keyword]").forEach(function (button) {
    button.addEventListener("click", function () {
      if (!destinationSearchInput || !destinationList) return;
      const keyword = button.dataset.destinationKeyword;
      const destination = DEFAULT_DESTINATIONS.find(function (candidate) {
        return candidate.label === keyword;
      });
      destinationSearchInput.value = keyword;
      if (destination) {
        applyDestination(destination);
      } else {
        destinationPanel.classList.add("is-active");
        searchDestinations(keyword, destinationList);
      }
    });
  });
  document.addEventListener("click", function (event) {
    const destinationControl = document.querySelector("#destinationControl");
    if (destinationControl && !destinationControl.contains(event.target) && destinationPanel) {
      destinationPanel.classList.remove("is-active");
      if (fields.destinationTrigger) fields.destinationTrigger.setAttribute("aria-expanded", "false");
    }
  });
  document.querySelectorAll("[data-budget]").forEach(function (button) {
    button.addEventListener("click", function () {
      fields.totalBudget.value = button.dataset.budget;
      document.querySelectorAll("[data-budget]").forEach(function (candidate) {
        candidate.classList.toggle("is-selected", candidate === button);
      });
      updateBudgetSummary();
      setError("budgetError", "");
      fields.totalBudget.closest(".budget-input").classList.remove("is-error");
    });
  });
  fields.totalBudget.addEventListener("input", function () {
    document.querySelectorAll("[data-budget]").forEach(function (candidate) {
      candidate.classList.remove("is-selected");
    });
    updateBudgetSummary();
    setError("budgetError", "");
    fields.totalBudget.closest(".budget-input").classList.remove("is-error");
  });
  fields.travelerCountMinus.addEventListener("click", function () {
    travelerCount = Math.max(1, travelerCount - 1);
    updateBudgetSummary();
  });
  fields.travelerCountPlus.addEventListener("click", function () {
    if (selectedCompanion === "ALONE") {
      travelerCount = 1;
      updateBudgetSummary();
      return;
    }
    travelerCount = Math.min(MAX_TRAVELERS, travelerCount + 1);
    updateBudgetSummary();
  });
  const rangePrev = document.querySelector("#rangeCalendarPrev");
  const rangeNext = document.querySelector("#rangeCalendarNext");
  const rangeClose = document.querySelector("#rangeCalendarClose");
  fields.startDate.addEventListener("click", openRangeCalendar);
  fields.endDate.addEventListener("click", openRangeCalendar);
  if (rangeClose) rangeClose.addEventListener("click", closeRangeCalendar);
  if (rangePrev) rangePrev.addEventListener("click", function () {
    calendarViewDate = new Date(calendarViewDate.getFullYear(), calendarViewDate.getMonth() - 1, 1);
    renderRangeCalendar();
  });
  if (rangeNext) rangeNext.addEventListener("click", function () {
    calendarViewDate = new Date(calendarViewDate.getFullYear(), calendarViewDate.getMonth() + 1, 1);
    renderRangeCalendar();
  });
  fields.startDate.addEventListener("change", function () {
    updateDuration();
    setError("startDateError", "");
    setError("endDateError", "");
    renderRangeCalendar();
  });
  fields.endDate.addEventListener("change", function () {
    updateDuration();
    setError("startDateError", "");
    setError("endDateError", "");
    renderRangeCalendar();
  });

  form.addEventListener("submit", async function (event) {
    event.preventDefault();
    if (saving) return;
    if (!validate()) {
      window.alert("모든 설정을 완료 후 버튼을 눌러주세요.");
      return;
    }

    const draft = readDraft();
    draft.basic = collectBasic();
    writeDraft(draft);
    setSaving(true);
    fields.message.textContent = "";

    try {
      if (!isAiPlan()) {
        const tripId = await createManualTrip(draft);
        window.location.href = "/trips/" + tripId + "/schedule";
        return;
      }
      const response = await saveDraft(draft);
      fields.message.textContent = "";
      fields.message.classList.remove("is-success", "is-error");
      window.setTimeout(function () {
        window.location.href = (response.data && response.data.nextUrl) || "/trips/new/style";
      }, 350);
    } catch (error) {
      setSaving(false);
      if (error && error.unauthorized) {
        if (window.confirm("로그인이 필요합니다. 로그인 페이지로 이동할까요?")) {
          const redirect = window.location.pathname + window.location.search;
          window.location.href = "/auth/login?redirect=" + encodeURIComponent(redirect);
        }
        return;
      }
      const reason = error && error.message
        ? error.message
        : "알 수 없는 오류가 발생했습니다.";
      window.alert("다음 단계로 이동하지 못했습니다.\n사유: " + reason);
    }
  });

  const saved = readDraft().basic || {};
  updateFlowUi();
  if (!saved.title || saved.titleAutoGenerated !== false) {
    saved.title = buildAutoTitle(saved.destinationLabel || saved.destination, saved.startDate);
    saved.titleAutoGenerated = true;
    const initialDraft = readDraft();
    initialDraft.basic = saved;
    writeDraft(initialDraft);
  }
  fields.destination.value = saved.destination || "";
  if (saved.destination) {
    const savedDestination = DEFAULT_DESTINATIONS.find(function (destination) {
      return destination.value === saved.destination;
    });
    selectedDestination = savedDestination || {
      value: saved.destination,
      label: saved.destinationLabel || saved.destination,
      countryCode: saved.countryCode || "",
    };
    fields.destinationLabel.textContent = selectedDestination.label;
    fields.destinationLabel.classList.add("is-visible");
    fields.destinationTrigger.value = getDestinationDisplayName(selectedDestination);
    fields.destinationTrigger.classList.add("has-value");
  }
  fields.startDate.value = saved.startDate || "";
  fields.endDate.value = saved.endDate || "";
  travelerCount = Math.min(MAX_TRAVELERS, Math.max(1, Number(saved.travelerCount) || 1));
  fields.totalBudget.value = saved.totalBudget ?? saved.budgetPerPerson ?? "";
  if (saved.companion) {
    const savedButton = document.querySelector('[data-companion="' + saved.companion + '"]');
    if (savedButton) selectCompanion(savedButton, false);
  }
  updateDuration();
  updateBudgetSummary();
  if (fields.startDate.value) {
    const savedStart = parseDate(fields.startDate.value);
    calendarViewDate = new Date(savedStart.getFullYear(), savedStart.getMonth(), 1);
  }
  renderRangeCalendar();
  document.body.dataset.pageReady = "true";
});
