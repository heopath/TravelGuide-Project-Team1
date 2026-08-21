(() => {
  const DRAFT_KEY = "tripDraft";
  const cards = [...document.querySelectorAll('[data-plan-mode]')];

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
      cards.forEach((item) => {
        const selected = item === card;
        item.classList.toggle('is-selected', selected);
        item.setAttribute('aria-pressed', String(selected));
      });
    });
  });
})();
