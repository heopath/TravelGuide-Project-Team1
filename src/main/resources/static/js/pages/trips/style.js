/* 여행 스타일 전용 JavaScript */
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
  const STYLE_LABELS = {
    SIGHTSEEING: "관광",
    FOOD: "맛집",
    CAFE: "카페",
    HEALING: "힐링",
    SHOPPING: "쇼핑",
    ACTIVITY: "액티비티",
    CULTURE: "문화·전시",
    PHOTO: "사진",
    RELAXED: "여유로운 일정",
    NORMAL: "일반적인 일정",
    FULL: "꽉 찬 일정",
    VALUE: "가성비 예산",
    STANDARD: "일반 예산",
    LUXURY: "럭셔리 예산",
    ALONE: "혼자",
    FRIEND: "친구",
    COUPLE: "연인",
    FAMILY: "가족",
    PARENTS: "부모님",
    CHILDREN: "아이와 함께",
  };

  const purposeButtons = document.querySelectorAll('[data-style-group="purpose"]');
  const scheduleButtons = document.querySelectorAll('[data-style-group="schedule"]');
  const budgetButtons = document.querySelectorAll('[data-style-group="budget"]');
  const basicSummary = document.querySelector("#basicSummary");
  const purposeSummary = document.querySelector("#purposeSummary");
  const styleSummary = document.querySelector("#styleSummary");
  const formMessage = document.querySelector("#styleFormMessage");
  const previousButton = document.querySelector("#stylePreviousButton");
  const nextButton = document.querySelector("#styleNextButton");

  if (!basicSummary || !purposeSummary || !styleSummary || !formMessage || !previousButton || !nextButton) {
    return;
  }

  const selectedStyles = {
    purposes: [],
    scheduleStyle: "",
    budgetStyle: "",
  };
  let saving = false;

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

  function setButtonSelected(button, selected) {
    button.classList.toggle("selected", selected);
    button.setAttribute("aria-pressed", String(selected));
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

  function durationLabel(startValue, endValue) {
    const start = parseLocalDate(startValue);
    const end = parseLocalDate(endValue);
    if (!start || !end || end < start) {
      return "";
    }
    const nights = Math.round((end.getTime() - start.getTime()) / 86400000);
    return nights + "박 " + (nights + 1) + "일";
  }

  function updateSummary() {
    const basic = readDraft().basic || {};
    const basicParts = [
      STYLE_LABELS[basic.companion],
      durationLabel(basic.startDate, basic.endDate),
    ].filter(Boolean);
    const purposeLabels = selectedStyles.purposes.map(function (value) {
      return STYLE_LABELS[value] || value;
    });
    const styleParts = [
      STYLE_LABELS[selectedStyles.scheduleStyle],
      STYLE_LABELS[selectedStyles.budgetStyle],
    ].filter(Boolean);

    basicSummary.textContent = basicParts.length ? basicParts.join(" · ") : "기본정보를 확인해주세요.";
    purposeSummary.textContent = purposeLabels.length ? purposeLabels.join(" + ") : "여행 목적을 선택해주세요.";
    styleSummary.textContent = styleParts.length ? styleParts.join(" · ") : "일정과 예산 스타일을 선택해주세요.";
  }

  function saveStyleDraft() {
    const draft = readDraft();
    draft.style = {
      purposes: selectedStyles.purposes.slice(),
      scheduleStyle: selectedStyles.scheduleStyle,
      budgetStyle: selectedStyles.budgetStyle,
    };
    writeDraft(draft);
    return draft;
  }

  function togglePurpose(button) {
    const value = button.dataset.styleValue;
    const index = selectedStyles.purposes.indexOf(value);
    if (index >= 0) {
      selectedStyles.purposes.splice(index, 1);
      setButtonSelected(button, false);
    } else {
      selectedStyles.purposes.push(value);
      setButtonSelected(button, true);
      setError("purposeError", "");
    }
    saveStyleDraft();
    updateSummary();
  }

  function selectSingle(button, buttons, key, errorId) {
    buttons.forEach(function (candidate) {
      setButtonSelected(candidate, candidate === button);
    });
    selectedStyles[key] = button.dataset.styleValue;
    setError(errorId, "");
    saveStyleDraft();
    updateSummary();
  }

  function validateBasic() {
    const basic = readDraft().basic || {};
    const start = parseLocalDate(basic.startDate);
    const end = parseLocalDate(basic.endDate);
    return Boolean(
      basic.tripName &&
      start &&
      end &&
      basic.companion &&
      end >= start
    );
  }

  function validateStyles() {
    let valid = true;
    setError("purposeError", "");
    setError("scheduleError", "");
    setError("budgetError", "");

    if (!selectedStyles.purposes.length) {
      setError("purposeError", "여행 목적을 하나 이상 선택해주세요.");
      valid = false;
    }
    if (!selectedStyles.scheduleStyle) {
      setError("scheduleError", "일정 스타일을 선택해주세요.");
      valid = false;
    }
    if (!selectedStyles.budgetStyle) {
      setError("budgetError", "예산 스타일을 선택해주세요.");
      valid = false;
    }
    return valid;
  }

  function setSaving(active) {
    saving = active;
    previousButton.disabled = active;
    nextButton.disabled = active;
    nextButton.setAttribute("aria-busy", String(active));
    nextButton.textContent = active ? "여행 정보 저장 중..." : "맞춤 여행지 추천받기";
  }

  function setFormMessage(message, type) {
    formMessage.textContent = message;
    formMessage.classList.toggle("is-error", type === "error");
    formMessage.classList.toggle("is-success", type === "success");
  }

  purposeButtons.forEach(function (button) {
    button.addEventListener("click", function () {
      togglePurpose(button);
    });
  });
  scheduleButtons.forEach(function (button) {
    button.addEventListener("click", function () {
      selectSingle(button, scheduleButtons, "scheduleStyle", "scheduleError");
    });
  });
  budgetButtons.forEach(function (button) {
    button.addEventListener("click", function () {
      selectSingle(button, budgetButtons, "budgetStyle", "budgetError");
    });
  });

  previousButton.addEventListener("click", function () {
    if (saving) {
      return;
    }
    saveStyleDraft();
    window.location.href = "/trips/new/basic";
  });

  nextButton.addEventListener("click", async function () {
    if (saving) {
      return;
    }

    setFormMessage("", "");
    if (!validateStyles()) {
      setFormMessage("선택하지 않은 여행 스타일이 있습니다.", "error");
      formMessage.focus();
      return;
    }
    if (!validateBasic()) {
      setFormMessage("기본 여행정보가 없습니다. 이전 단계에서 다시 입력해주세요.", "error");
      formMessage.focus();
      return;
    }

    const draft = saveStyleDraft();
    setSaving(true);
    setFormMessage("여행 정보를 저장하고 있습니다.", "");

    try {
      const response = await window.AllMyTripsDraftStore.save(draft);
      setFormMessage(response.message || "여행 정보가 저장되었습니다.", "success");
      if (window.AllMyTripsModal) {
        window.AllMyTripsModal.showToast("여행 정보 저장을 완료했습니다.");
      }
      window.setTimeout(function () {
        window.location.href = response.data.nextUrl;
      }, 600);
    } catch (error) {
      console.error("여행 정보 저장 실패:", error);
      setSaving(false);
      setFormMessage("저장에 실패했습니다. 잠시 후 다시 시도해주세요.", "error");
      formMessage.focus();
    }
  });

  const savedStyle = readDraft().style || {};
  selectedStyles.purposes = Array.isArray(savedStyle.purposes) ? savedStyle.purposes.slice() : [];
  selectedStyles.scheduleStyle = savedStyle.scheduleStyle || "";
  selectedStyles.budgetStyle = savedStyle.budgetStyle || "";

  purposeButtons.forEach(function (button) {
    setButtonSelected(button, selectedStyles.purposes.includes(button.dataset.styleValue));
  });
  scheduleButtons.forEach(function (button) {
    setButtonSelected(button, selectedStyles.scheduleStyle === button.dataset.styleValue);
  });
  budgetButtons.forEach(function (button) {
    setButtonSelected(button, selectedStyles.budgetStyle === button.dataset.styleValue);
  });
  updateSummary();
});
