document.addEventListener("DOMContentLoaded", function () {
  const form = document.querySelector("#login-form");
  const errorMessage = document.querySelector("#login-error");
  const submitButton = document.querySelector("#login-submit");

  if (!form) {
    return;
  }

  form.addEventListener("submit", async function (event) {
    event.preventDefault();

    errorMessage.hidden = true;
    submitButton.disabled = true;
    submitButton.textContent = "로그인 중...";

    const request = {
      email: document.querySelector("#login-email").value.trim(),
      password: document.querySelector("#login-password").value
    };

    try {
      const response = await fetch("/api/v1/auth/login", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Accept": "application/json"
        },
        credentials: "same-origin",
        body: JSON.stringify(request)
      });

      const result = await response.json();

      if (!response.ok || !result.success) {
        throw new Error(result.message || "로그인에 실패했습니다.");
      }

      const redirectUrl = sessionStorage.getItem("postLoginRedirect") || "/home";
      sessionStorage.removeItem("postLoginRedirect");
      const safeRedirect = redirectUrl.startsWith("/") && !redirectUrl.startsWith("//")
        ? redirectUrl
        : "/home";
      window.location.href = safeRedirect;
    } catch (error) {
      errorMessage.textContent = error.message;
      errorMessage.hidden = false;
    } finally {
      submitButton.disabled = false;
      submitButton.textContent = "로그인";
    }
  });
});
