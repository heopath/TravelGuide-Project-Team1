/* AI-02: 화면 상태 제어와 AI Guide API 호출을 담당합니다. */
document.addEventListener("DOMContentLoaded", () => {
  const form = document.querySelector("[data-ai-chat-form]");
  const input = document.querySelector("#chat-question");
  const messages = document.querySelector("[data-chat-messages]");
  const errorBox = document.querySelector("[data-chat-error]");
  const errorTitle = document.querySelector("[data-chat-error-title]");
  const errorMessage = document.querySelector("[data-chat-error-message]");
  const retryButton = document.querySelector("[data-retry]");
  const mode = document.querySelector("#demo-mode");
  const mockEnabled = document.body.dataset.aiMockEnabled === "true";
  const rawTripId = new URLSearchParams(window.location.search).get("tripId");
  const tripId = rawTripId && /^[1-9]\d*$/.test(rawTripId) ? Number(rawTripId) : null;
  const submitButton = document.querySelector("[data-ai-submit]");
  let lastQuestion = "근처 저녁 맛집을 추천해줘";
  let csrfToken;

  const showError = (title, message, retryable = true) => {
    errorTitle.textContent = title;
    errorMessage.textContent = message;
    retryButton.hidden = !retryable;
    errorBox.hidden = false;
  };

  const disableQuestionInput = () => {
    input.disabled = true;
    submitButton.disabled = true;
    document.querySelectorAll("[data-chat-question]").forEach((button) => {
      button.disabled = true;
    });
  };

  const create = (tag, text, className) => {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text) element.textContent = text;
    return element;
  };

  const appendMessage = (className, buildContent) => {
    const item = create("div", "", className);
    buildContent(item);
    messages.appendChild(item);
    item.scrollIntoView({ behavior: "smooth", block: "nearest" });
    return item;
  };

  const appendUserMessage = (question) => appendMessage("user-message", (item) => {
    item.textContent = question;
  });

  const appendLoading = () => appendMessage("ai-loading", (item) => {
    item.append(create("span", "✦", "loading-dot"));
    const label = create("p", "여행 조건을 분석하고 있어요", "");
    label.append(create("span", "...", "loading-ellipsis"));
    item.append(label);
  });

  const safeExternalUrl = (value) => {
    try {
      const url = new URL(value);
      return ["https:", "http:"].includes(url.protocol) ? url.href : null;
    } catch (error) {
      return null;
    }
  };

  const renderResponse = (response) => {
    appendMessage("ai-message", (item) => {
      item.append(create("p", response.data.answer));

      const days = create("div", "", "ai-days");
      response.data.days.forEach((day) => {
        const dayCard = create("section", "", "ai-day-card");
        dayCard.append(create("h3", day.title));
        const list = create("ul", "", "ai-recommendations");
        day.items.forEach((schedule) => {
          const scheduleItem = create("li");
          scheduleItem.append(create("time", schedule.time));
          const copy = create("div");
          copy.append(create("strong", schedule.name));
          copy.append(create("span", schedule.reason));
          scheduleItem.append(copy);
          list.append(scheduleItem);
        });
        dayCard.append(list);
        days.append(dayCard);
      });
      item.append(days);

      const links = create("div", "", "ai-links");
      response.data.externalLinks.forEach((link) => {
        const href = safeExternalUrl(link.url);
        if (!href) return;
        const anchor = create("a", `${link.label} ↗`);
        anchor.href = href;
        anchor.target = "_blank";
        anchor.rel = "noopener noreferrer";
        links.append(anchor);
      });
      item.append(links);
      item.append(create("small", response.data.sources.join(" · ")));
    });
  };

  const requestAiGuide = async (question) => {
    if (!csrfToken) {
      const csrfResponse = await fetch("/api/v1/csrf", {
        headers: { Accept: "application/json" },
        credentials: "same-origin"
      });
      const csrfPayload = await csrfResponse.json().catch(() => null);
      if (!csrfResponse.ok || !csrfPayload?.headerName || !csrfPayload?.token) {
        throw new Error("CSRF_TOKEN_REQUEST_FAILED");
      }
      csrfToken = csrfPayload;
    }

    const headers = { "Content-Type": "application/json", Accept: "application/json" };
    headers[csrfToken.headerName] = csrfToken.token;
    if (mockEnabled && mode?.value === "failure") headers["X-AI-Mock-Mode"] = "server-error";

    const response = await fetch("/api/v1/ai-guides/generate", {
      method: "POST",
      headers,
      body: JSON.stringify({
        question,
        tripId
      }),
      credentials: "same-origin"
    });
    const payload = await response.json().catch(() => null);
    if (!response.ok || !payload?.success) throw new Error(payload?.message || "AI_GUIDE_REQUEST_FAILED");
    return payload;
  };

  const submit = (question) => {
    lastQuestion = question;
    errorBox.hidden = true;
    appendUserMessage(question);
    const loading = appendLoading();
    form.classList.add("is-disabled");
    submitButton.disabled = true;
    requestAiGuide(question)
      .then(renderResponse)
      .catch(() => showError("추천을 불러오지 못했어요", "잠시 후 다시 시도하거나 질문을 조금 바꿔보세요."))
      .finally(() => {
        loading.remove();
        form.classList.remove("is-disabled");
        submitButton.disabled = false;
      });
  };

  const submitQuestion = () => {
    const question = input.value.trim();
    if (!question || submitButton.disabled) return;
    input.value = "";
    submit(question);
  };

  submitButton.addEventListener("click", submitQuestion);
  input.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.isComposing) {
      event.preventDefault();
      submitQuestion();
    }
  });
  document.querySelectorAll("[data-chat-question]").forEach((button) => button.addEventListener("click", () => {
    input.value = button.dataset.chatQuestion;
    input.focus();
  }));
  retryButton.addEventListener("click", () => submit(lastQuestion));

  if (!tripId) {
    const message = rawTripId
      ? "올바른 여행 정보를 찾을 수 없어요. 여행 일정에서 다시 AI 가이드를 열어주세요."
      : "여행 일정에서 여행을 선택한 뒤 AI 가이드를 이용해 주세요.";
    showError("여행을 먼저 선택해 주세요", message, false);
    disableQuestionInput();
  }
});
