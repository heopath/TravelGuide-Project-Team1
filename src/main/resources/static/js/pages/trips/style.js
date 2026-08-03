/* 여행 스타일 전용 JavaScript */
document.addEventListener("DOMContentLoaded", function () {
  const draftKey = "all-my-trips-trip-draft";
  const purposeGroup = document.querySelector("[data-purpose-group]");
  const paceGroup = document.querySelector("[data-pace-group]");
  const budgetStyleGroup = document.querySelector("[data-budget-style-group]");
  const basicSummary = document.querySelector("[data-style-basic]");
  const purposeSummary = document.querySelector("[data-style-purpose]");
  const optionSummary = document.querySelector("[data-style-options]");
  const nextButton = document.querySelector("[data-style-next]");
  let draft;

  try {
    draft = JSON.parse(sessionStorage.getItem(draftKey) || "null");
  } catch (error) {
    draft = null;
  }

  if (!draft || !draft.destinationName || !draft.startDate || !draft.endDate) {
    window.AllMyTripsModal.showToast("기본 여행 정보를 먼저 입력해주세요.");
    window.setTimeout(function () { window.location.href = "/trips/new/basic"; }, 500);
    return;
  }

  if (draft.purpose) {
    const savedPurposes = draft.purpose.split(",");
    purposeGroup.querySelectorAll("button").forEach(function (button) {
      button.classList.toggle("selected", savedPurposes.includes(button.textContent.trim()));
    });
  }
  if (draft.pace) {
    paceGroup.querySelectorAll("button").forEach(function (button) {
      button.classList.toggle("selected", button.dataset.value === draft.pace);
    });
  }
  if (draft.accommodationStyle) {
    budgetStyleGroup.querySelectorAll("button").forEach(function (button) {
      button.classList.toggle("selected", button.dataset.value === draft.accommodationStyle);
    });
  }

  function selectedTexts(group) {
    return Array.from(group.querySelectorAll(".selected")).map(function (button) {
      return button.textContent.trim();
    });
  }

  function durationLabel() {
    const start = new Date(draft.startDate + "T00:00:00");
    const end = new Date(draft.endDate + "T00:00:00");
    const nights = Math.round((end - start) / 86400000);
    return nights + "박 " + (nights + 1) + "일";
  }

  function updateSummary() {
    const purposes = selectedTexts(purposeGroup);
    const pace = paceGroup.querySelector(".selected");
    const budgetStyle = budgetStyleGroup.querySelector(".selected");
    basicSummary.textContent = (draft.companionLabel || draft.companionType) + " · " + durationLabel() + " · " + draft.destinationName;
    purposeSummary.textContent = purposes.length ? purposes.join(" + ") : "취향을 선택해주세요.";
    optionSummary.textContent = pace.textContent.trim() + " · " + budgetStyle.textContent.trim();
  }

  [purposeGroup, paceGroup, budgetStyleGroup].forEach(function (group) {
    group.addEventListener("click", function () { window.setTimeout(updateSummary, 0); });
  });

  nextButton.addEventListener("click", function () {
    const purposes = selectedTexts(purposeGroup);
    if (purposes.length === 0) {
      window.AllMyTripsModal.showToast("여행 취향을 하나 이상 선택해주세요.");
      return;
    }
    draft.purpose = purposes.join(",");
    draft.pace = paceGroup.querySelector(".selected").dataset.value;
    draft.accommodationStyle = budgetStyleGroup.querySelector(".selected").dataset.value;
    draft.status = "DRAFT";
    draft.source = draft.source || "MANUAL";
    sessionStorage.setItem(draftKey, JSON.stringify(draft));
    window.location.href = "/trips/recommendations";
  });

  updateSummary();
  document.body.dataset.pageReady = "true";
});
