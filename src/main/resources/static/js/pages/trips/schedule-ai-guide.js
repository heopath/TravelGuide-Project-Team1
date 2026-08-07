document.addEventListener("DOMContentLoaded", function () {
  const modal = document.querySelector("[data-schedule-ai-modal]");
  const dialog = modal?.querySelector(".schedule-ai-dialog");
  const openButtons = Array.from(document.querySelectorAll("[data-schedule-ai-open]"));
  const form = document.querySelector("[data-schedule-ai-form]");
  const input = document.querySelector("#schedule-ai-question");
  const messages = document.querySelector("[data-schedule-ai-messages]");
  const errorBox = document.querySelector("[data-schedule-ai-error]");
  const errorMessage = document.querySelector("[data-schedule-ai-error-message]");
  const retryButton = document.querySelector("[data-schedule-ai-retry]");
  if (!modal || !dialog || !openButtons.length || !form || !input || !messages) return;

  let csrfToken;
  let lastQuestion = "";
  let submitting = false;
  let lastOpenButton = openButtons[0];
  const nudge = document.querySelector("[data-schedule-ai-nudge]");
  const nudgeStorageKey = "allMyTrips.scheduleAiNudgeSeen";

  function create(tag, text, className) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text) element.textContent = text;
    return element;
  }

  function currentTripId() {
    const tripId = Number(document.body.dataset.tripId);
    return Number.isInteger(tripId) && tripId > 0 ? tripId : null;
  }

  function appendMessage(className, build) {
    const message = create("div", "", "schedule-ai-message " + className);
    build(message);
    messages.appendChild(message);
    message.scrollIntoView({ behavior: "smooth", block: "nearest" });
    return message;
  }

  function appendUserMessage(question) {
    appendMessage("schedule-ai-user", function (message) { message.textContent = question; });
  }

  function appendLoading() {
    return appendMessage("schedule-ai-assistant schedule-ai-loading", function (message) {
      message.append(create("p", "여행 일정과 장소를 살펴보고 있어요..."));
    });
  }

  function safeExternalUrl(value) {
    try {
      const url = new URL(value);
      return ["https:", "http:"].includes(url.protocol) ? url.href : null;
    } catch (error) {
      return null;
    }
  }

  function renderResponse(payload) {
    const response = payload.data;
    appendMessage("schedule-ai-assistant", function (message) {
      message.append(create("p", response.answer));
      (response.days || []).forEach(function (day) {
        const dayCard = create("section", "", "schedule-ai-day");
        dayCard.append(create("h3", day.title));
        const list = create("ul");
        (day.items || []).forEach(function (item) {
          const row = create("li");
          row.append(create("time", item.time));
          const copy = create("div");
          copy.append(create("strong", item.name));
          copy.append(create("span", item.reason));
          row.append(copy);
          const addButton = create("button", "일정에 추가", "schedule-ai-add-item");
          addButton.type = "button";
          addButton.addEventListener("click", async function () {
            if (!window.AllMyTripsSchedule?.addAiRecommendation) {
              showError("일정 화면을 준비하지 못했습니다. 새로고침 후 다시 시도해주세요.");
              return;
            }
            addButton.disabled = true;
            addButton.textContent = "추가 중";
            try {
              await window.AllMyTripsSchedule.addAiRecommendation(item, day.day);
              addButton.textContent = "추가됨";
            } catch (error) {
              addButton.disabled = false;
              addButton.textContent = "일정에 추가";
              showError(error.message || "일정을 추가하지 못했습니다.");
            }
          });
          row.append(addButton);
          list.append(row);
        });
        dayCard.append(list);
        message.append(dayCard);
      });
      const links = create("div", "", "schedule-ai-links");
      (response.externalLinks || []).forEach(function (link) {
        const href = safeExternalUrl(link.url);
        if (!href) return;
        const anchor = create("a", link.label + " ↗");
        anchor.href = href;
        anchor.target = "_blank";
        anchor.rel = "noopener noreferrer";
        links.append(anchor);
      });
      if (links.childElementCount) message.append(links);
    });
  }

  async function requestGuide(question) {
    const tripId = currentTripId();
    if (!tripId) throw new Error("현재 여행을 불러온 뒤 AI 가이드를 이용해주세요.");
    if (!csrfToken) {
      const csrfResponse = await fetch("/api/v1/csrf", {
        headers: { Accept: "application/json" },
        credentials: "same-origin",
        allMyTripsLoading: false
      });
      const csrfPayload = await csrfResponse.json().catch(function () { return null; });
      if (!csrfResponse.ok || !csrfPayload?.headerName || !csrfPayload?.token) {
        throw new Error("보안 토큰을 준비하지 못했습니다.");
      }
      csrfToken = csrfPayload;
    }
    const headers = { "Content-Type": "application/json", Accept: "application/json" };
    headers[csrfToken.headerName] = csrfToken.token;
    const response = await fetch("/api/v1/ai-guides/generate", {
      method: "POST",
      credentials: "same-origin",
      allMyTripsLoading: false,
      headers,
      body: JSON.stringify({ tripId, question })
    });
    const payload = await response.json().catch(function () { return null; });
    if (!response.ok || !payload?.success) {
      throw new Error(payload?.message || "AI 추천을 생성하지 못했습니다.");
    }
    return payload;
  }

  function showError(message) {
    errorMessage.textContent = message;
    errorBox.hidden = false;
  }

  async function submit(question) {
    if (!question || submitting) return;
    lastQuestion = question;
    submitting = true;
    errorBox.hidden = true;
    input.disabled = true;
    appendUserMessage(question);
    const loading = appendLoading();
    try {
      renderResponse(await requestGuide(question));
    } catch (error) {
      showError(error.message || "잠시 후 다시 시도해주세요.");
    } finally {
      loading.remove();
      submitting = false;
      input.disabled = false;
      input.focus();
    }
  }

  function openModal() {
    try {
      localStorage.setItem(nudgeStorageKey, "true");
    } catch (error) {
      // Ignore storage access errors and keep the guide usable.
    }
    if (nudge) nudge.hidden = true;
    modal.hidden = false;
    modal.setAttribute("aria-hidden", "false");
    window.setTimeout(function () { input.focus(); }, 0);
  }

  function closeModal() {
    modal.hidden = true;
    modal.setAttribute("aria-hidden", "true");
    lastOpenButton.focus();
  }

  openButtons.forEach(function (button) {
    button.addEventListener("click", function () {
      lastOpenButton = button;
      openModal();
    });
  });
  modal.querySelectorAll("[data-schedule-ai-close]").forEach(function (button) {
    button.addEventListener("click", closeModal);
  });
  document.addEventListener("click", function (event) {
    const clickedOpenButton = openButtons.some(function (button) {
      return button.contains(event.target);
    });
    if (!modal.hidden && !dialog.contains(event.target) && !clickedOpenButton) {
      closeModal();
    }
  });
  form.addEventListener("submit", function (event) {
    event.preventDefault();
    const question = input.value.trim();
    if (!question) return;
    input.value = "";
    submit(question);
  });
  document.querySelectorAll("[data-schedule-ai-question]").forEach(function (button) {
    button.addEventListener("click", function () {
      input.value = button.dataset.scheduleAiQuestion;
      input.focus();
    });
  });
  retryButton.addEventListener("click", function () { submit(lastQuestion); });
  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape" && !modal.hidden) closeModal();
  });

  if (nudge) {
    try {
      if (localStorage.getItem(nudgeStorageKey) !== "true") {
        window.setTimeout(function () { nudge.hidden = false; }, 600);
      }
    } catch (error) {
      window.setTimeout(function () { nudge.hidden = false; }, 600);
    }
  }
});
