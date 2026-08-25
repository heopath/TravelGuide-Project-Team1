document.addEventListener("DOMContentLoaded", function () {
  const form = document.querySelector("#forgot-form");
  if (!form) {
    return;
  }

  const errorMessage = document.querySelector("#forgot-error");
  const doneMessage = document.querySelector("#forgot-done");
  const submitButton = document.querySelector("#forgot-submit");

  async function readJsonResponse(response, fallbackMessage) {
    const contentType = response.headers.get("content-type") || "";
    if (!contentType.includes("application/json")) {
      throw new Error(fallbackMessage);
    }
    try {
      return await response.json();
    } catch (error) {
      throw new Error(fallbackMessage);
    }
  }

  async function csrfHeaders() {
    const response = await fetch("/api/v1/csrf", {
      credentials: "same-origin",
      headers: { "Accept": "application/json" },
      allMyTripsLoading: false
    });
    const payload = await readJsonResponse(
      response,
      "보안 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."
    );

    if (!response.ok || !payload.headerName || !payload.token) {
      throw new Error(
        payload.message || "보안 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."
      );
    }

    return { [payload.headerName]: payload.token };
  }

  /* 우리 API의 답에는 늘 success가 붙어 있다. 없으면 스프링이 낸 기본 오류라
     속사정이 그대로 화면에 뜬다. 그럴 땐 우리 말로 바꾼다. */
  function messageOf(result, fallback) {
    return typeof result.success === "boolean" && result.message ? result.message : fallback;
  }

  form.addEventListener("submit", async function (event) {
    event.preventDefault();

    errorMessage.hidden = true;
    doneMessage.hidden = true;
    submitButton.disabled = true;
    submitButton.textContent = "보내는 중...";

    const email = document.querySelector("#forgot-email").value.trim();

    try {
      const securityHeaders = await csrfHeaders();
      const response = await fetch("/api/v1/auth/password-reset", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Accept": "application/json",
          ...securityHeaders
        },
        credentials: "same-origin",
        body: JSON.stringify({ email: email })
      });

      const result = await readJsonResponse(
        response,
        "서버 응답이 올바르지 않습니다. 잠시 후 다시 시도해 주세요."
      );

      if (!response.ok || !result.success) {
        throw new Error(messageOf(result, "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."));
      }

      // 가입한 적 없는 이메일이어도 서버는 같은 답을 준다. 화면에서도 구별하지 않는다.
      doneMessage.textContent = result.message;
      doneMessage.hidden = false;
      form.querySelector("#forgot-email").value = "";
    } catch (error) {
      errorMessage.textContent = error.message;
      errorMessage.hidden = false;
    } finally {
      submitButton.disabled = false;
      submitButton.textContent = "재설정 링크 보내기";
    }
  });
});
