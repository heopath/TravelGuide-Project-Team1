/* AI 여행 스타일 선택과 초안 저장 */
document.addEventListener("DOMContentLoaded", function () {
  const DRAFT_KEY = "tripDraft";
  const previousButton = document.querySelector("#stylePreviousButton");
  const nextButton = document.querySelector("#styleNextButton");
  const formMessage = document.querySelector("#styleFormMessage");
  if (!previousButton || !nextButton || !formMessage) return;

  const labels = {
    SIGHTSEEING: "관광", FOOD: "맛집", CAFE: "카페", HEALING: "힐링", SHOPPING: "쇼핑",
    ACTIVITY: "액티비티", CULTURE: "문화·전시", PHOTO: "사진", NATURE: "자연·풍경",
    NIGHT: "야경·야간", LOCAL: "로컬 체험", FAMILY: "가족 체험",
    VERY_RELAXED: "아주여유롭게", RELAXED: "여유로운", BALANCED: "균형있는", FULL: "알찬", THEME_FOCUSED: "테마집중",
    PUBLIC_TRANSIT: "대중교통", CAR: "자차·렌터카", TAXI: "택시", WALK_BIKE: "도보·자전거", MIXED: "상황에 따라 혼합",
    FAMOUS: "유명 맛집", CAFE_DESSERT: "카페·디저트", VALUE: "가성비 식당", FINE_DINING: "고급 식당", VEGETARIAN: "채식",
    HOTEL: "호텔", RESORT: "리조트", PENSION: "펜션·풀빌라", GUESTHOUSE: "게스트하우스·호스텔", HANOK: "한옥", CAMPING: "캠핑·글램핑",
    NO_PREFERENCE: "선호 없음", ALONE: "혼자", FRIEND: "친구", COUPLE: "연인", PARENTS: "부모님", CHILDREN: "아이와 함께"
  };
  const state = { purposes: [], pace: "", transportPreference: "", foodPreference: "", accommodationStyle: "" };
  const groupConfig = {
    schedule: ["pace", "scheduleError"],
    transport: ["transportPreference", "transportError"],
    food: ["foodPreference", "foodError"],
    accommodation: ["accommodationStyle", "accommodationError"]
  };

  function readDraft() { try { return JSON.parse(sessionStorage.getItem(DRAFT_KEY) || "{}"); } catch (error) { return {}; } }
  function writeDraft(draft) { sessionStorage.setItem(DRAFT_KEY, JSON.stringify(draft)); }
  function selectButton(button, selected) { button.classList.toggle("selected", selected); button.setAttribute("aria-pressed", String(selected)); }
  function showError(id, message) { const target = document.querySelector("#" + id); if (target) target.textContent = message || ""; }
  function label(value) { return labels[value] || value || ""; }

  if ((readDraft().plan || {}).mode !== "AI") {
    window.location.replace("/trips/new/plan");
    return;
  }

  function saveStyle() {
    const draft = readDraft();
    draft.style = {
      purposes: state.purposes.slice(),
      purpose: state.purposes.map(label).join(", "),
      pace: label(state.pace),
      paceCode: state.pace,
      transportPreference: label(state.transportPreference),
      transportPreferenceCode: state.transportPreference,
      foodPreference: label(state.foodPreference),
      foodPreferenceCode: state.foodPreference,
      accommodationStyle: label(state.accommodationStyle),
      accommodationStyleCode: state.accommodationStyle
    };
    writeDraft(draft);
  }

  function updateSummary() {
    const basic = readDraft().basic || {};
    const start = basic.startDate ? new Date(basic.startDate + "T00:00:00") : null;
    const end = basic.endDate ? new Date(basic.endDate + "T00:00:00") : null;
    const nights = start && end ? Math.round((end - start) / 86400000) : null;
    document.querySelector("#basicSummary").textContent = [basic.destinationLabel || basic.destination, label(basic.companion), nights === null ? "" : nights + "박 " + (nights + 1) + "일"].filter(Boolean).join(" · ") || "기본정보를 확인해주세요.";
    document.querySelector("#purposeSummary").textContent = state.purposes.map(label).join(" + ") || "여행 목적을 선택해주세요.";
    document.querySelector("#styleSummary").textContent = [label(state.pace), label(state.transportPreference)].filter(Boolean).join(" · ") || "일정과 이동 선호를 선택해주세요.";
    document.querySelector("#preferenceSummary").textContent = [label(state.foodPreference), label(state.accommodationStyle)].filter(Boolean).join(" · ") || "음식과 숙박 선호를 선택해주세요.";
    document.querySelector("#tripTitleDisplay").textContent = basic.title || "나의 여행";
  }

  function validate() {
    ["purposeError", "scheduleError", "transportError", "foodError", "accommodationError"].forEach(function (id) { showError(id, ""); });
    let valid = true;
    if (!state.purposes.length) { showError("purposeError", "여행 목적을 하나 이상 선택해주세요."); valid = false; }
    Object.entries(groupConfig).forEach(function (entry) {
      const config = entry[1];
      if (!state[config[0]]) { showError(config[1], "한 가지를 선택해주세요."); valid = false; }
    });
    return valid;
  }

  document.querySelectorAll('[data-style-group="purpose"]').forEach(function (button) {
    button.addEventListener("click", function () {
      const value = button.dataset.styleValue;
      const index = state.purposes.indexOf(value);
      if (index >= 0) state.purposes.splice(index, 1); else state.purposes.push(value);
      selectButton(button, index < 0);
      saveStyle(); updateSummary(); showError("purposeError", "");
    });
  });

  Object.entries(groupConfig).forEach(function (entry) {
    const group = entry[0]; const stateKey = entry[1][0]; const errorId = entry[1][1];
    const buttons = document.querySelectorAll('[data-style-group="' + group + '"]');
    buttons.forEach(function (button) {
      button.addEventListener("click", function () {
        buttons.forEach(function (candidate) { selectButton(candidate, candidate === button); });
        state[stateKey] = button.dataset.styleValue;
        showError(errorId, ""); saveStyle(); updateSummary();
      });
    });
  });

  previousButton.addEventListener("click", function () { saveStyle(); window.location.href = "/trips/new/basic"; });
  nextButton.addEventListener("click", function () {
    formMessage.textContent = "";
    if (!validate()) { formMessage.textContent = "선택하지 않은 여행 스타일이 있습니다."; formMessage.classList.add("is-error"); return; }
    saveStyle();
    window.location.href = "/ai-trip-plan";
  });

  const saved = readDraft().style || {};
  state.purposes = Array.isArray(saved.purposes) ? saved.purposes.slice() : [];
  state.pace = saved.paceCode || saved.scheduleStyle || "";
  state.transportPreference = saved.transportPreferenceCode || "";
  state.foodPreference = saved.foodPreferenceCode || "";
  state.accommodationStyle = saved.accommodationStyleCode || "";
  document.querySelectorAll("[data-style-group]").forEach(function (button) {
    const group = button.dataset.styleGroup;
    const selected = group === "purpose" ? state.purposes.includes(button.dataset.styleValue) : state[groupConfig[group] && groupConfig[group][0]] === button.dataset.styleValue;
    selectButton(button, selected);
  });
  updateSummary();
  document.body.dataset.pageReady = "true";
});
