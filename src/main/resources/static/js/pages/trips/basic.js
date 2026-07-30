/* 여행 기본 정보 전용 JavaScript */
document.addEventListener("DOMContentLoaded", async function () {
  document.body.dataset.pageReady = "true";

  const TRIP_DRAFT_KEY = "tripDraft";
  if (window.AllMyTripsDraftStore) {
    try {
      await window.AllMyTripsDraftStore.restoreIfNeeded();
    } catch (error) {
      console.warn("서버 여행 초안을 복원하지 못했습니다.", error);
    }
  }
  const form = document.querySelector("#tripBasicForm");
  const tripNameInput = document.querySelector("#tripName");
  const startDateInput = document.querySelector("#startDate");
  const endDateInput = document.querySelector("#endDate");
  const companionButtons = document.querySelectorAll("[data-companion]");
  const duration = document.querySelector("#tripDuration");
  const formMessage = document.querySelector("#basicFormMessage");

  if (!form || !tripNameInput || !startDateInput || !endDateInput || !duration || !formMessage) {
    return;
  }

  let selectedCompanion = "";

  function readDraft() {
    try {
      return JSON.parse(sessionStorage.getItem(TRIP_DRAFT_KEY) || "{}");
    } catch (error) {
      console.warn("여행 임시 데이터를 읽지 못했습니다.", error);
      return {};
    }
  }

  function writeDraft(draft) {
    sessionStorage.setItem(TRIP_DRAFT_KEY, JSON.stringify(draft));
  }

  function setError(id, message) {
    const element = document.querySelector("#" + id);
    if (element) {
      element.textContent = message;
    }
  }

  function parseLocalDate(value) {
    return value ? new Date(value + "T00:00:00") : null;
  }

  function updateDuration() {
    const startDate = parseLocalDate(startDateInput.value);
    const endDate = parseLocalDate(endDateInput.value);
    endDateInput.min = startDateInput.value;

    if (!startDate || !endDate || endDate < startDate) {
      duration.textContent = "여행 기간을 선택해주세요.";
      return;
    }

    const nights = Math.round((endDate.getTime() - startDate.getTime()) / 86400000);
    duration.textContent = nights + "박 " + (nights + 1) + "일 일정입니다.";
  }

  function selectCompanion(value) {
    selectedCompanion = value;
    companionButtons.forEach(function (button) {
      const selected = button.dataset.companion === value;
      button.classList.toggle("selected", selected);
      button.setAttribute("aria-pressed", String(selected));
    });
    setError("companionError", "");
  }

  function validate() {
    let valid = true;
    const startDate = parseLocalDate(startDateInput.value);
    const endDate = parseLocalDate(endDateInput.value);

    setError("tripNameError", "");
    setError("startDateError", "");
    setError("endDateError", "");
    setError("companionError", "");
    formMessage.textContent = "";
    formMessage.classList.remove("is-error");

    if (!tripNameInput.value.trim()) {
      setError("tripNameError", "여행 이름을 입력해주세요.");
      valid = false;
    }
    if (!startDate) {
      setError("startDateError", "시작일을 선택해주세요.");
      valid = false;
    }
    if (!endDate) {
      setError("endDateError", "종료일을 선택해주세요.");
      valid = false;
    } else if (startDate && endDate < startDate) {
      setError("endDateError", "종료일은 시작일보다 빠를 수 없습니다.");
      valid = false;
    }
    if (!selectedCompanion) {
      setError("companionError", "동행자 유형을 선택해주세요.");
      valid = false;
    }

    return valid;
  }

  companionButtons.forEach(function (button) {
    button.addEventListener("click", function () {
      selectCompanion(button.dataset.companion);
    });
  });
  startDateInput.addEventListener("change", updateDuration);
  endDateInput.addEventListener("change", updateDuration);

  form.addEventListener("submit", function (event) {
    event.preventDefault();

    if (!validate()) {
      formMessage.textContent = "입력하지 않은 기본 정보가 있습니다.";
      formMessage.classList.add("is-error");
      return;
    }

    const draft = readDraft();
    draft.basic = {
      tripName: tripNameInput.value.trim(),
      startDate: startDateInput.value,
      endDate: endDateInput.value,
      companion: selectedCompanion,
    };
    writeDraft(draft);
    window.location.href = "/trips/new/style";
  });

  const savedBasic = readDraft().basic || {};
  tripNameInput.value = savedBasic.tripName || "";
  startDateInput.value = savedBasic.startDate || "";
  endDateInput.value = savedBasic.endDate || "";
  if (savedBasic.companion) {
    selectCompanion(savedBasic.companion);
  }
  updateDuration();
});
