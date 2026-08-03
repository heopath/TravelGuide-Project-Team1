/* AI-02: 화면 상태 제어와 AI Guide API 호출을 담당합니다. */
document.addEventListener("DOMContentLoaded", () => {
  const form = document.querySelector("[data-ai-chat-form]");
  const input = document.querySelector("#chat-question");
  const messages = document.querySelector("[data-chat-messages]");
  const errorBox = document.querySelector("[data-chat-error]");
  const mode = document.querySelector("#demo-mode");
  const submitButton = document.querySelector("[data-ai-submit]");
  let lastQuestion = "근처 저녁 맛집을 추천해줘";

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
    const headers = { "Content-Type": "application/json", Accept: "application/json" };
    if (mode.value === "failure") headers["X-AI-Mock-Mode"] = "server-error";

    const response = await fetch("/api/v1/ai-guides/generate", {
      method: "POST",
      headers,
      body: JSON.stringify({ question })
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
      .catch(() => { errorBox.hidden = false; })
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
  document.querySelector("[data-retry]").addEventListener("click", () => submit(lastQuestion));
});
