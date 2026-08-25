document.addEventListener("DOMContentLoaded", function () {
  const form = document.querySelector("#signup-form");
  const errorMessage = document.querySelector("#signup-error");
  const submitButton = document.querySelector("#signup-submit");
  const agreeAll = document.querySelector("#signup-agree-all");
  const termsAgreed = document.querySelector("#signup-terms-agreed");
  const privacyAgreed = document.querySelector("#signup-privacy-agreed");
  const agreementDialog = document.querySelector("#agreement-dialog");

  if (!form) {
    return;
  }

  function syncAgreementState() {
    const termsChecked = Boolean(termsAgreed?.checked);
    const privacyChecked = Boolean(privacyAgreed?.checked);
    if (agreeAll) {
      agreeAll.checked = termsChecked && privacyChecked;
      agreeAll.indeterminate = termsChecked !== privacyChecked;
    }
  }

  agreeAll?.addEventListener("change", function () {
    const checked = agreeAll.checked;
    termsAgreed.checked = checked;
    privacyAgreed.checked = checked;
    agreeAll.indeterminate = false;
  });

  termsAgreed?.addEventListener("change", syncAgreementState);
  privacyAgreed?.addEventListener("change", syncAgreementState);

  document.querySelectorAll("[data-agreement-open]").forEach(function (button) {
    button.addEventListener("click", function () {
      const documentName = button.dataset.agreementOpen;
      const selectedDocument = agreementDialog?.querySelector(
          `[data-agreement-document="${documentName}"]`
      );
      if (!agreementDialog || !selectedDocument) {
        return;
      }

      agreementDialog.querySelectorAll("[data-agreement-document]").forEach(function (documentPanel) {
        documentPanel.hidden = documentPanel !== selectedDocument;
      });
      const title = documentName === "service-terms"
          ? "서비스 이용약관"
          : "개인정보 수집·이용 동의";
      agreementDialog.querySelector("#agreement-dialog-title").textContent = title;

      if (typeof agreementDialog.showModal === "function") {
        agreementDialog.showModal();
      } else {
        agreementDialog.setAttribute("open", "");
      }
    });
  });

  agreementDialog?.querySelectorAll("[data-agreement-close]").forEach(function (button) {
    button.addEventListener("click", function () {
      if (typeof agreementDialog.close === "function") {
        agreementDialog.close();
      } else {
        agreementDialog.removeAttribute("open");
      }
    });
  });

  form.addEventListener("submit", async function (event) {
    event.preventDefault();

    errorMessage.hidden = true;

    if (!termsAgreed.checked || !privacyAgreed.checked) {
      errorMessage.textContent = "서비스 이용약관과 개인정보 수집·이용에 모두 동의해 주세요.";
      errorMessage.hidden = false;
      (termsAgreed.checked ? privacyAgreed : termsAgreed).focus();
      return;
    }

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
      termsAgreed: termsAgreed.checked,
      privacyAgreed: privacyAgreed.checked,
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
