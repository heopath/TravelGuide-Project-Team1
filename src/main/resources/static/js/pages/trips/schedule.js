/* 여행 일정 편집 전용 JavaScript */
document.addEventListener("DOMContentLoaded", function () {
  const requestedTripId = Number(document.body.dataset.tripId);
  let activeTripId = Number.isInteger(requestedTripId) && requestedTripId > 0
    ? requestedTripId
    : null;
  const tripList = document.querySelector("[data-trip-list]");
  const title = document.querySelector("[data-schedule-title]");
  const dayTabs = document.querySelector("[data-day-tabs]");
  const timeline = document.querySelector("[data-timeline]");
  const period = document.querySelector("[data-schedule-period]");
  const destination = document.querySelector("[data-schedule-destination]");

  function showEmpty(container, message) {
    container.replaceChildren();
    const text = document.createElement("p");
    text.className = "schedule-empty";
    text.textContent = message;
    container.appendChild(text);
  }

  function formatDate(value) {
    if (!value) return "날짜 미정";
    return value.slice(5).replace("-", ".");
  }

  function formatTime(value) {
    return value ? value.slice(0, 5) : "";
  }

  function formatCost(item) {
    if (item.estimatedCost == null) return "";
    const amount = Number(item.estimatedCost);
    if (!Number.isFinite(amount)) return "";
    return amount.toLocaleString("ko-KR") + " " + (item.currencyCode || "KRW");
  }

  async function requestJson(url) {
    const response = await fetch(url, {
      headers: { Accept: "application/json" },
      credentials: "same-origin",
      allMyTripsLoading: false,
    });
    if (response.status === 401) {
      window.AllMyTripsModal.showToast("로그인 후 여행 일정을 확인할 수 있습니다.");
      window.setTimeout(function () { window.location.href = "/auth/login"; }, 700);
      throw new Error("로그인이 필요합니다.");
    }
    const payload = await response.json().catch(function () { return null; });
    if (!response.ok || !payload?.success) {
      throw new Error(payload?.message || "여행 일정을 불러오지 못했습니다.");
    }
    return payload.data;
  }

  function renderItems(items) {
    timeline.replaceChildren();
    if (items.length === 0) {
      showEmpty(timeline, "아직 추가한 장소가 없습니다. 여행 가이드에서 장소를 추가해보세요.");
      return;
    }
    items.forEach(function (item, index) {
      const button = document.createElement("button");
      const order = document.createElement("span");
      const copy = document.createElement("div");
      const name = document.createElement("strong");
      const meta = document.createElement("small");
      button.type = "button";
      button.className = "schedule-item";
      button.dataset.itineraryItemId = item.itineraryItemId;
      order.textContent = item.sortOrder || index + 1;
      name.textContent = item.title || "일정";
      const timeRange = [formatTime(item.startTime), formatTime(item.endTime)]
        .filter(Boolean)
        .join(" ~ ");
      meta.textContent = [timeRange || "시간 미정", item.memo, formatCost(item)]
        .filter(Boolean)
        .join(" · ");
      copy.append(name, meta);
      button.append(order, copy);
      if (item.placeId) {
        button.classList.add("linked");
        button.addEventListener("click", function () {
          window.location.href = "/guide/places/" + item.placeId;
        });
      }
      timeline.appendChild(button);
    });
  }

  async function selectDay(day, selectedButton) {
    dayTabs.querySelectorAll("button").forEach(function (button) {
      button.classList.toggle("selected", button === selectedButton);
    });
    showEmpty(timeline, "DAY " + day.dayNumber + " 일정을 불러오는 중입니다.");
    try {
      renderItems(await requestJson("/api/v1/trip-days/" + day.tripDayId + "/items"));
    } catch (error) {
      showEmpty(timeline, error.message);
    }
  }

  function renderDays(days) {
    dayTabs.replaceChildren();
    if (days.length === 0) {
      showEmpty(timeline, "여행 날짜가 아직 만들어지지 않았습니다.");
      return;
    }
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
    if (trips.length === 0) {
      showEmpty(tripList, "아직 만든 여행이 없습니다.");
      return;
    }
    trips.forEach(function (trip) {
      const button = document.createElement("button");
      const name = document.createElement("strong");
      const dates = document.createElement("span");
      button.type = "button";
      button.classList.toggle("selected", trip.tripId === activeTripId);
      name.textContent = trip.title;
      dates.textContent = formatDate(trip.startDate) + "–" + formatDate(trip.endDate);
      button.append(name, dates);
      button.addEventListener("click", function () {
        window.location.href = "/trips/" + trip.tripId + "/schedule";
      });
      tripList.appendChild(button);
    });
  }

  async function loadSchedule() {
    try {
      const trips = await requestJson("/api/v1/trips");
      if (trips.length === 0) {
        renderTripList(trips);
        title.textContent = "아직 만든 여행이 없습니다";
        period.textContent = "여행 계획을 먼저 만들어주세요.";
        destination.textContent = "목적지 · 미정";
        showEmpty(timeline, "새 여행을 만든 뒤 장소를 일정에 추가해보세요.");
        return;
      }

      if (!activeTripId || !trips.some(function (trip) { return trip.tripId === activeTripId; })) {
        activeTripId = trips[0].tripId;
        window.history.replaceState(null, "", "/trips/" + activeTripId + "/schedule");
      }

      const results = await Promise.all([
        requestJson("/api/v1/trips/" + activeTripId),
        requestJson("/api/v1/trips/" + activeTripId + "/days"),
      ]);
      const trip = results[0];
      title.textContent = trip.title;
      period.textContent = "여행 기간 · " + trip.startDate + " ~ " + trip.endDate;
      destination.textContent = "목적지 · " + trip.destinationName;
      renderTripList(trips);
      renderDays(results[1]);
    } catch (error) {
      showEmpty(timeline, error.message);
      showEmpty(tripList, "여행 목록을 불러오지 못했습니다.");
    }
  }

  document.querySelectorAll("[data-schedule-guide]").forEach(function (button) {
    button.addEventListener("click", function () { window.location.href = "/guide"; });
  });

  loadSchedule();
  document.body.dataset.pageReady = "true";
});
