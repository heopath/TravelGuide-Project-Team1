document.addEventListener("DOMContentLoaded", function () {
  const form = document.querySelector("#reset-form");
  if (!form) {
    return;
  }

  const errorMessage = document.querySelector("#reset-error");
  const doneMessage = document.querySelector("#reset-done");
  const submitButton = document.querySelector("#reset-submit");
  const token = new URLSearchParams(window.location.search).get("token") || "";

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

  /* 우리 API의 답에는 늘 success가 붙어 있다. 없으면 스프링이 낸 기본 오류(404 등)라
     "No static resource ..." 같은 속사정이 손님 화면에 그대로 뜬다. 그럴 땐 우리 말로 바꾼다. */
  function messageOf(result, fallback) {
    return typeof result.success === "boolean" && result.message ? result.message : fallback;
  }

  function showError(message) {
    errorMessage.textContent = message;
    errorMessage.hidden = false;
  }

  function disableForm() {
    submitButton.disabled = true;
    form.querySelectorAll("input").forEach(function (input) {
      input.disabled = true;
    });
  }

  // 새 비밀번호를 다 입력한 뒤에야 링크가 죽었다는 걸 알면 헛수고다. 화면을 열 때 먼저 확인한다.
  (async function verifyLink() {
    if (!token) {
      showError("링크가 올바르지 않습니다. 비밀번호 찾기부터 다시 진행해 주세요.");
      disableForm();
      return;
    }

    try {
      const response = await fetch(
        "/api/v1/auth/password-reset?token=" + encodeURIComponent(token),
        { credentials: "same-origin", headers: { "Accept": "application/json" } }
      );
      const result = await readJsonResponse(
        response,
        "서버 응답이 올바르지 않습니다. 잠시 후 다시 시도해 주세요."
      );

      if (!response.ok || !result.success) {
        throw new Error(messageOf(result, "링크가 만료되었거나 이미 사용되었습니다. 비밀번호 찾기부터 다시 진행해 주세요."));
      }
    } catch (error) {
      showError(error.message);
      disableForm();
    }
  })();

  form.addEventListener("submit", async function (event) {
    event.preventDefault();

    errorMessage.hidden = true;
    doneMessage.hidden = true;

    const password = document.querySelector("#reset-password").value;
    const confirm = document.querySelector("#reset-password-confirm").value;

    if (password.length < 8) {
      showError("비밀번호는 8자 이상이어야 합니다.");
      return;
    }
    if (password !== confirm) {
      showError("두 비밀번호가 서로 다릅니다.");
      return;
    }

    submitButton.disabled = true;
    submitButton.textContent = "변경 중...";

    try {
      const securityHeaders = await csrfHeaders();
      const response = await fetch("/api/v1/auth/password-reset/confirm", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Accept": "application/json",
          ...securityHeaders
        },
        credentials: "same-origin",
        body: JSON.stringify({ token: token, newPassword: password })
      });

      const result = await readJsonResponse(
        response,
        "서버 응답이 올바르지 않습니다. 잠시 후 다시 시도해 주세요."
      );

      if (!response.ok || !result.success) {
        throw new Error(messageOf(result, "비밀번호를 변경하지 못했습니다. 잠시 후 다시 시도해 주세요."));
      }

      doneMessage.textContent = result.message + " 잠시 후 로그인 화면으로 이동합니다.";
      doneMessage.hidden = false;
      disableForm();
      window.setTimeout(function () {
        window.location.href = "/auth/login";
      }, 1500);
    } catch (error) {
      showError(error.message);
      submitButton.disabled = false;
    } finally {
      submitButton.textContent = "비밀번호 변경";
    }
  });
});
