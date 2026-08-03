/* 여행지 추천 전용 JavaScript */
document.addEventListener("DOMContentLoaded", function () {
  const draftKey = "all-my-trips-trip-draft";
  const buttons = Array.from(document.querySelectorAll("[data-create-trip]"));
  const conditionLabel = document.querySelector("[data-trip-condition]");
  let draft;

  try {
    draft = JSON.parse(sessionStorage.getItem(draftKey) || "null");
  } catch (error) {
    draft = null;
  }

  if (!draft || !draft.startDate || !draft.endDate) {
    window.AllMyTripsModal.showToast("여행 정보를 먼저 입력해주세요.");
    window.setTimeout(function () { window.location.href = "/trips/new/basic"; }, 500);
    return;
  }

  const start = new Date(draft.startDate + "T00:00:00");
  const end = new Date(draft.endDate + "T00:00:00");
  const nights = Math.round((end - start) / 86400000);
  conditionLabel.textContent = [
    draft.companionLabel || draft.companionType,
    nights + "박 " + (nights + 1) + "일",
    (draft.purpose || "").split(",").slice(0, 2).join(" · "),
  ].filter(Boolean).join(" · ");

  if (draft.destinationName) {
    const firstCard = document.querySelector("[data-recommendation]");
    firstCard.dataset.destination = draft.destinationName;
    firstCard.querySelector("h2").textContent = draft.destinationName;
    firstCard.querySelector("p").textContent = "선택한 조건과 가장 잘 맞는 여행지";
    firstCard.querySelector("[data-create-trip]").textContent = draft.destinationName + "으로 계획하기";
  }

  async function createTripDay(tripId, dayNumber, tripDate) {
    const response = await fetch("/api/trips/" + tripId + "/days", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      credentials: "same-origin",
      body: JSON.stringify({
        dayNumber: dayNumber,
        tripDate: tripDate,
        title: "DAY " + dayNumber,
      }),
      allMyTripsLoading: false,
    });
    if (!response.ok) throw new Error("여행 날짜를 생성하지 못했습니다.");
  }

  async function createTrip(destination, button) {
    buttons.forEach(function (item) { item.disabled = true; });
    const originalText = button.textContent;
    button.textContent = "여행을 만드는 중...";

    try {
      const purposeName = (draft.purpose || "여행").split(",")[0];
      const response = await fetch("/api/trips", {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        credentials: "same-origin",
        body: JSON.stringify({
          title: draft.title || destination + " " + purposeName + " 여행",
          destinationName: destination,
          startDate: draft.startDate,
          endDate: draft.endDate,
          companionType: draft.companionType,
          companionCount: draft.companionCount || 1,
          purpose: draft.purpose,
          budgetAmount: draft.budgetAmount,
          currencyCode: draft.currencyCode || "KRW",
          pace: draft.pace,
          accommodationStyle: draft.accommodationStyle,
          status: "DRAFT",
          source: draft.source || "MANUAL",
        }),
        allMyTripsLoading: false,
      });

      if (response.status === 401) {
        window.AllMyTripsModal.showToast("로그인 후 여행을 만들 수 있습니다.");
        window.setTimeout(function () { window.location.href = "/auth/login"; }, 700);
        return;
      }
      if (!response.ok) throw new Error("여행을 생성하지 못했습니다.");
      const trip = await response.json();

      const currentDate = new Date(draft.startDate + "T00:00:00");
      for (let dayNumber = 1; dayNumber <= nights + 1; dayNumber += 1) {
        const tripDate = new Date(currentDate);
        tripDate.setDate(currentDate.getDate() + dayNumber - 1);
        const dateValue = tripDate.getFullYear() + "-" +
          String(tripDate.getMonth() + 1).padStart(2, "0") + "-" +
          String(tripDate.getDate()).padStart(2, "0");
        await createTripDay(trip.tripId, dayNumber, dateValue);
      }

      sessionStorage.removeItem(draftKey);
      sessionStorage.removeItem("all-my-trips-destination");
      window.AllMyTripsModal.showToast("여행과 날짜별 일정을 만들었습니다.");
      window.setTimeout(function () {
        window.location.href = "/trips/" + trip.tripId + "/schedule";
      }, 500);
    } catch (error) {
      window.AllMyTripsModal.showToast(error.message || "여행을 만들지 못했습니다.");
      buttons.forEach(function (item) { item.disabled = false; });
      button.textContent = originalText;
    }
  }

  buttons.forEach(function (button) {
    button.addEventListener("click", function () {
      createTrip(button.closest("[data-recommendation]").dataset.destination, button);
    });
  });

  document.body.dataset.pageReady = "true";
});
