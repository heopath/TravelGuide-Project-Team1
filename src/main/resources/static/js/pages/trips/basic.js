/* 여행 기본 정보 입력과 초안 저장 */
document.addEventListener("DOMContentLoaded", function () {
  const DRAFT_KEY = "tripDraft";
  const PENDING_BOOKING_KEY = "allMyTrips.pendingBooking";
  const BOOKING_RESUME_DRAFT_BACKUP_KEY = "allMyTrips.bookingResumeDraftBackup";
  const bookingResume = new URLSearchParams(window.location.search).get("bookingResume") === "1";
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
  if ([
    fields.destination,
    fields.destinationLabel,
    fields.destinationTrigger,
    fields.startDate,
    fields.endDate,
    fields.totalBudget,
    fields.budgetPerPerson,
    fields.budgetPerPersonHint,
    fields.travelerCountValue,
    fields.travelerCountMinus,
    fields.travelerCountPlus,
    fields.duration,
    fields.message,
    fields.nextButton
  ].some(function (field) { return !field; })) return;
  const companionButtons = document.querySelectorAll("[data-companion]");
  let selectedCompanion = "";
  let selectedDestination = null;
  let saving = false;
  let bookingResumeCompleted = false;
  let calendarViewDate = new Date();
  let destinationSearchTimer = null;
  let travelerCount = 1;
  const MAX_TRAVELERS = 20;
  const MAX_TRIP_DAYS = 30;
  const companionTypeMap = { ALONE: "SOLO", FRIEND: "FRIENDS", COUPLE: "COUPLE", FAMILY: "FAMILY", PARENTS: "FAMILY", CHILDREN: "FAMILY" };

  /*
   * 목적지 목록.
   *
   * 예전에는 /api/v1/places를 검색해 지역명을 추려 썼다. 그런데 그 표에는 사용자가
   * 일정에 담은 카카오 장소가 모두 들어오고, 카카오는 region·city를 늘 채워주지 않는다.
   * 지역을 모르면 장소 이름으로 대신 채우고 있어서 "오장동흥남집 본점", "월드타워식당가"
   * 같은 가게가 목적지 후보로 올라왔다. 실제로 활성 장소 273곳 중 140곳이 그랬다.
   *
   * 목적지는 성격상 고정된 목록이다. 사용자가 무엇을 담았는지에 따라 흔들려서는 안 된다.
   *
   * 국내 이름은 TourApiAccommodationSearchProvider의 지역 표(AREAS)에 있는 정식명칭을
   * 그대로 쓴다. 여기 label이 trips.destination_name으로 그대로 저장되고, 숙소 조회가
   * 그 문자열을 지역 코드로 되돌리기 때문에 두 곳의 표기가 어긋나면 안 된다.
   */
  const DEFAULT_DESTINATIONS = [
    { label: "서울특별시", value: "서울특별시", countryCode: "KR", aliases: ["서울"] },
    { label: "부산광역시", value: "부산광역시", countryCode: "KR", aliases: ["부산"] },
    { label: "인천광역시", value: "인천광역시", countryCode: "KR", aliases: ["인천"] },
    { label: "대전광역시", value: "대전광역시", countryCode: "KR", aliases: ["대전"] },
    { label: "대구광역시", value: "대구광역시", countryCode: "KR", aliases: ["대구"] },
    { label: "광주광역시", value: "광주광역시", countryCode: "KR", aliases: ["광주"] },
    { label: "울산광역시", value: "울산광역시", countryCode: "KR", aliases: ["울산"] },
    { label: "세종특별자치시", value: "세종특별자치시", countryCode: "KR", aliases: ["세종"] },
    { label: "경기도", value: "경기도", countryCode: "KR", aliases: ["경기"] },
    { label: "강원특별자치도", value: "강원특별자치도", countryCode: "KR", aliases: ["강원", "강원도"] },
    { label: "충청북도", value: "충청북도", countryCode: "KR", aliases: ["충북"] },
    { label: "충청남도", value: "충청남도", countryCode: "KR", aliases: ["충남"] },
    { label: "전북특별자치도", value: "전북특별자치도", countryCode: "KR", aliases: ["전북", "전라북도"] },
    { label: "전라남도", value: "전라남도", countryCode: "KR", aliases: ["전남"] },
    { label: "경상북도", value: "경상북도", countryCode: "KR", aliases: ["경북"] },
    { label: "경상남도", value: "경상남도", countryCode: "KR", aliases: ["경남"] },
    { label: "제주특별자치도", value: "제주특별자치도", countryCode: "KR", aliases: ["제주", "제주도"] },
    /*
     * 광역시·도만 두면 "강릉"을 고르려는 사람이 강원특별자치도를 눌러야 한다.
     * 이미 여행이 많이 만들어진 도시들이라 그대로 남긴다. 숙소 조회는 광역 지역을
     * 못 찾으면 키워드 검색으로 넘어가므로 이 이름들도 처리된다.
     */
    { label: "강릉", value: "강릉", countryCode: "KR" },
    { label: "경주", value: "경주", countryCode: "KR" },
    { label: "여수", value: "여수", countryCode: "KR" },
    { label: "전주", value: "전주", countryCode: "KR" },
    { label: "도쿄", value: "도쿄", countryCode: "JP" },
    { label: "오사카", value: "오사카", countryCode: "JP" },
    { label: "파리", value: "파리", countryCode: "FR" },
    { label: "바르셀로나", value: "바르셀로나", countryCode: "ES" },
    { label: "방콕", value: "방콕", countryCode: "TH" },
    { label: "다낭", value: "다낭", countryCode: "VN" },
    { label: "뉴욕", value: "뉴욕", countryCode: "US" },
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

  function discardAbandonedBookingResume() {
    if (!bookingResume || bookingResumeCompleted) return;
    sessionStorage.removeItem(PENDING_BOOKING_KEY);
    const previousDraft = sessionStorage.getItem(BOOKING_RESUME_DRAFT_BACKUP_KEY);
    if (previousDraft) sessionStorage.setItem(DRAFT_KEY, previousDraft);
    else sessionStorage.removeItem(DRAFT_KEY);
    sessionStorage.removeItem(BOOKING_RESUME_DRAFT_BACKUP_KEY);
  }

  /* 예약에서 시작한 베이직 입력은 정상 제출 전까지만 임시 데이터다. 다른 페이지 이동,
     뒤로가기, 탭 닫기 시에는 예약 대기 정보와 이 화면에서 고른 값을 남기지 않는다. */
  window.addEventListener("pagehide", discardAbandonedBookingResume);

  function isAiPlan() {
    return (readDraft().plan || {}).mode === "AI";
  }

  function updateFlowUi() {
    fields.nextButton.textContent = "다음으로->";
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

  /** "충남"으로 "충청남도"를 찾을 수 있도록 정식명칭과 줄임말을 함께 본다. */
  function destinationMatches(destination, normalizedKeyword) {
    if (!normalizedKeyword) return true;
    const names = [destination.label].concat(destination.aliases || []);
    return names.some(function (name) {
      return name.toLowerCase().includes(normalizedKeyword);
    });
  }

  /* 고정 목록에서 고른다. 서버를 부르지 않으므로 결과가 매번 같고 순서도 흔들리지 않는다. */
  function searchDestinations(keyword, list) {
    const normalizedKeyword = String(keyword || "").trim().toLowerCase();
    const matched = DEFAULT_DESTINATIONS.filter(function (destination) {
      return destinationMatches(destination, normalizedKeyword);
    });
    renderDestinationResults(list, matched.slice(0, 8), "일치하는 도시나 지역이 없습니다.");
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
      ? (isAiPlan() ? "입력 내용 저장 중..." : "여행 정보 저장 중...")
      : "다음으로->";
  }

  function manualTripPayload(draft) {
    const basic = draft.basic || {};

    return {
      title: basic.titleAutoGenerated === false
          ? basic.title || buildAutoTitle(basic.destinationLabel, basic.startDate)
          : buildAutoTitle(basic.destinationLabel, basic.startDate),

      destinationName:
          basic.destinationLabel ||
          basic.destination,

      startDate: basic.startDate,
      endDate: basic.endDate,

      companionType:
          companionTypeMap[basic.companion] ||
          "OTHER",

      companionCount:
          Number(basic.travelerCount || 1),

      // 직접 계획하기에서는 스타일 입력 단계를 거치지 않음
      purpose: null,

      budgetAmount:
          Number(basic.totalBudget || 0),

      currencyCode: "KRW",

      transportPreference: null,
      foodPreference: null,
      pace: null,
      accommodationStyle: null,

      status: "DRAFT",
    };
  }

  async function saveManualTrip(draft) {
    const response = await fetch(
        "/api/v1/trips",
        {
          method: "POST",

          headers: {
            "Content-Type": "application/json",
            Accept: "application/json",
          },

          body: JSON.stringify(
              manualTripPayload(draft)
          ),
        }
    );

    if (response.status === 401) {
      const unauthorized =
          new Error("로그인이 필요합니다.");

      unauthorized.unauthorized = true;

      throw unauthorized;
    }

    const body = await response
        .json()
        .catch(function () {
          return {};
        });

    if (!response.ok) {
      console.error(
          "여행 생성 실패:",
          response.status,
          body
      );

      throw new Error(
          body.message ||
          body.detail ||
          "서버에서 오류가 발생했습니다."
      );
    }

    const tripId = Number(
        body.data && body.data.tripId
    );

    if (
        !Number.isInteger(tripId) ||
        tripId < 1
    ) {
      throw new Error(
          "생성된 여행 ID를 확인할 수 없습니다."
      );
    }

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
      } else if (event.key === "Enter") {
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
        const tripId =
            await saveManualTrip(draft);

        draft.trip = {
          tripId: tripId,
          source: "MANUAL",
          status: "DRAFT",
        };

        // 직접 계획에서는 이전 스타일 정보 사용 안 함
        delete draft.style;

        writeDraft(draft);

        /* 예약은 스케줄을 최종 저장하기 전까지 여행과 분리한다. 여기서는 DRAFT 여행만
           만들고, 예약 묶음은 스케줄의 "여행 저장하기" 성공 시점에 연결한다. */
        if (bookingResume && sessionStorage.getItem(PENDING_BOOKING_KEY)) {
          bookingResumeCompleted = true;
          window.location.href =
              "/trips/" +
              encodeURIComponent(tripId) +
              "/schedule?resumeBooking=1";
        } else {
          // 기본정보 저장 후 바로 일정 화면으로 이동
          window.location.href =
              "/trips/" +
              tripId +
              "/schedule";
        }

        return;
      }

      // AI 계획은 기존대로 style 화면으로 진행
      const response =
          await saveDraft(draft);

      fields.message.textContent = "";
      fields.message.classList.remove(
          "is-success",
          "is-error"
      );

      window.setTimeout(function () {
        window.location.href =
            (response.data &&
                response.data.nextUrl) ||
            "/trips/new/style";
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
  /* 예약에서 넘어온 인원 수로 동행 관계를 추측하지 않고 매번 사용자가 직접 선택한다. */
  if (bookingResume) saved.companion = "";
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
  fields.totalBudget.value = saved.totalBudget ?? "";
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
