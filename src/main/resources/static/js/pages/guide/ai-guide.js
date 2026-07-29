/* AI-01: DTO 샘플을 사용하는 모의 응답 화면. 실제 API 연결 시 requestMock만 교체합니다. */
document.addEventListener("DOMContentLoaded", () => {
  const form = document.querySelector("[data-ai-chat-form]");
  const input = document.querySelector("#chat-question");
  const messages = document.querySelector("[data-chat-messages]");
  const errorBox = document.querySelector("[data-chat-error]");
  const mode = document.querySelector("#demo-mode");
  const submitButton = document.querySelector("[data-ai-submit]");
  let lastQuestion = "근처 저녁 맛집을 추천해줘";

  const append = (className, html) => {
    const item = document.createElement("div");
    item.className = className;
    item.innerHTML = html;
    messages.appendChild(item);
    item.scrollIntoView({ behavior: "smooth", block: "nearest" });
  };

  const renderResponse = (response) => {
    const places = response.data.recommendations.map((item) => `<li><strong>${item.name}</strong><span>${item.reason}</span></li>`).join("");
    const links = response.data.externalLinks.map((item) => `<a href="${item.url}" target="_blank" rel="noopener noreferrer">${item.label} ↗</a>`).join("");
    append("ai-message", `<p>${response.data.answer}</p><ul class="ai-recommendations">${places}</ul><div class="ai-links">${links}</div><small>${response.data.sources.join(" · ")}</small>`);
  };

  const requestMock = (question) => new Promise((resolve, reject) => {
    window.setTimeout(() => {
      if (mode.value === "failure") return reject(new Error("AI_MOCK_FAILURE"));
      resolve({ success: true, data: { answer: "광안리 일정 뒤에는 민락회센터에서 저녁을 먹고, 해변 산책으로 마무리하는 코스를 추천해요.", recommendations: [{ name: "민락회센터", reason: "광안리에서 도보 10분 · 저녁 식사에 적합" }, { name: "광안리 해변 산책", reason: "식사 후 이동 부담이 적은 코스" }], externalLinks: [{ type: "FLIGHT", label: "항공권 검색", url: "/booking/flights" }, { type: "HOTEL", label: "숙소 검색", url: "/booking/hotels" }], sources: ["현재 일정", "부산 장소 데이터", `질문: ${question}`] }, message: "추천이 완료되었습니다." });
    }, 900);
  });

  const submit = (question) => {
    lastQuestion = question;
    errorBox.hidden = true;
    append("user-message", question);
    append("ai-loading", "<span class=\"loading-dot\">✦</span><p>여행 조건을 분석하고 있어요<span class=\"loading-ellipsis\">...</span></p>");
    form.classList.add("is-disabled");
    submitButton.disabled = true;
    requestMock(question).then((response) => { messages.querySelector(".ai-loading")?.remove(); renderResponse(response); }).catch(() => { messages.querySelector(".ai-loading")?.remove(); errorBox.hidden = false; }).finally(() => { form.classList.remove("is-disabled"); form.querySelector("button").disabled = false; });
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
  document.querySelectorAll("[data-chat-question]").forEach((button) => button.addEventListener("click", () => { input.value = button.dataset.chatQuestion; input.focus(); }));
  document.querySelector("[data-retry]").addEventListener("click", () => submit(lastQuestion));
});
