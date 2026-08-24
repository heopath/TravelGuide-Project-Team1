document.addEventListener("DOMContentLoaded", function () {
  const form = document.querySelector("#signup-form");
  const errorMessage = document.querySelector("#signup-error");
  const submitButton = document.querySelector("#signup-submit");

  if (!form) {
    return;
  }

  form.addEventListener("submit", async function (event) {
    event.preventDefault();

    errorMessage.hidden = true;
    submitButton.disabled = true;
    submitButton.textContent = "가입 중...";

    const turnstileWidget = form.querySelector(".cf-turnstile");
    const turnstileToken = form.querySelector('[name="cf-turnstile-response"]')?.value || "";
    if (turnstileWidget && !turnstileToken) {
      errorMessage.textContent = "사람인지 확인이 끝날 때까지 잠시 기다려 주세요.";
      errorMessage.hidden = false;
      submitButton.disabled = false;
      submitButton.textContent = "회원가입 완료";
      return;
    }

    const request = {
      email: document.querySelector("#signup-email").value.trim(),
      password: document.querySelector("#signup-password").value,
      nickname: document.querySelector("#signup-nickname").value.trim(),
      turnstileToken: turnstileToken
    };

    try {
      const response = await fetch("/api/v1/auth/signup", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Accept": "application/json"
        },
        credentials: "same-origin",
        body: JSON.stringify(request)
      });

      const result = await response.json().catch(function () {
        return null;
      });

      if (!response.ok || !result?.success) {
        throw new Error(
            result?.message || "회원가입에 실패했습니다."
        );
      }

      window.location.href = "/auth/login";
    } catch (error) {
      errorMessage.textContent = error.message;
      errorMessage.hidden = false;
      if (turnstileWidget && window.turnstile) {
        window.turnstile.reset();
      }
    } finally {
      submitButton.disabled = false;
      submitButton.textContent = "회원가입 완료";
    }
  });
});
