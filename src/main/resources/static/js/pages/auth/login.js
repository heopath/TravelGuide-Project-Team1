document.addEventListener("DOMContentLoaded", function () {
  const form = document.querySelector("#login-form");
  const errorMessage = document.querySelector("#login-error");
  const submitButton = document.querySelector("#login-submit");

  // 로그인 필요 화면에 직접 진입해 막힌 경우에만 SecurityConfig가 redirect 파라미터를 붙인다.
  // 화면 안에서 401을 받아 스스로 로그인으로 온 경우(style.js 등)에는 파라미터가 없어 /home으로 간다.
  function resolvePostLoginTarget() {
    const requested = new URLSearchParams(window.location.search).get("redirect");
    if (!requested) {
      return "/home";
    }
    // 문자열 검사로는 "//evil.com"은 막아도 "/\evil.com"이 통과한다.
    // 브라우저가 역슬래시를 슬래시로 해석하므로, 파싱한 origin을 직접 비교해야 안전하다.
    let target;
    try {
      target = new URL(requested, window.location.origin);
    } catch (error) {
      return "/home";
    }
    return target.origin === window.location.origin
      ? target.pathname + target.search
      : "/home";
  }

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

      window.location.href = resolvePostLoginTarget();
    } catch (error) {
      errorMessage.textContent = error.message;
      errorMessage.hidden = false;
    } finally {
      submitButton.disabled = false;
      submitButton.textContent = "로그인";
    }
  });
});
