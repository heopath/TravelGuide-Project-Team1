(() => {
  const DRAFT_KEY = "tripDraft";
  const cards = [...document.querySelectorAll('[data-plan-mode]')];
  const aiStep = document.querySelector("[data-ai-step]");
  const finalStep = document.querySelector("[data-final-step]");

  function updateWizard(mode) {
    const isAi = mode === "AI";
    if (aiStep) aiStep.hidden = !isAi;
    if (finalStep) {
      finalStep.querySelector("span").textContent = isAi ? "4" : "3";
      finalStep.querySelector("b").textContent = isAi ? "추천 결과" : "여행 일정";
    }
  }

  function readDraft() {
    try {
      return JSON.parse(sessionStorage.getItem(DRAFT_KEY) || "{}");
    } catch (error) {
      return {};
    }
  }

  function savePlanMode(mode) {
    const draft = readDraft();
    draft.plan = {
      ...(draft.plan || {}),
      mode,
      source: mode === "MANUAL" ? "MANUAL" : "AI",
    };
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify(draft));
  }

  cards.forEach((card) => {
    card.addEventListener('click', () => {
      const mode = card.dataset.planMode === "manual" ? "MANUAL" : "AI";
      savePlanMode(mode);
      updateWizard(mode);
      cards.forEach((item) => {
        const selected = item === card;
        item.classList.toggle('is-selected', selected);
        item.setAttribute('aria-pressed', String(selected));
      });
    });
  });
  updateWizard((readDraft().plan || {}).mode || "MANUAL");
})();
