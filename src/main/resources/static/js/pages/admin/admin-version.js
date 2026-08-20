/* 관리자 서비스 표시 버전 관리 */
(function () {
  "use strict";

  const ENDPOINT = "/api/v1/admin/service-settings/footer-version";
  const VERSION_PATTERN = /^v?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;

  const panel = document.querySelector('[data-admin-section="version"]');
  if (!panel) return;

  const form = panel.querySelector("[data-version-form]");
  const input = panel.querySelector("[data-version-input]");
  const current = panel.querySelector("[data-version-current]");
  const message = panel.querySelector("[data-version-message]");
  const submit = panel.querySelector("[data-version-submit]");
  if (!form || !input || !current || !message || !submit) return;

  async function request(options) {
    const response = await fetch(ENDPOINT, Object.assign({
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      allMyTripsLoading: false,
    }, options || {}));
    const payload = await response.json().catch(function () { return null; });
    if (!response.ok || payload?.success === false) {
      if (response.status === 401) {
        window.location.href = "/auth/login?redirect=" + encodeURIComponent("/admin?panel=version");
      }
      if (response.status === 403) throw new Error("관리자만 변경할 수 있습니다.");
      throw new Error(payload?.message || "서비스 버전을 처리하지 못했습니다.");
    }
    return payload?.data ?? payload;
  }

  function applyVersion(version) {
    const display = String(version || "");
    current.textContent = display || "—";
    input.value = display;
    document.querySelectorAll(".footer-version").forEach(function (element) {
      element.textContent = display;
    });
  }

  async function load() {
    message.textContent = "";
    try {
      const data = await request();
      applyVersion(data?.version);
    } catch (error) {
      current.textContent = "불러오지 못함";
      message.textContent = error.message || "현재 버전을 불러오지 못했습니다.";
    }
  }

  form.addEventListener("submit", async function (event) {
    event.preventDefault();
    const version = input.value.trim();
    if (!VERSION_PATTERN.test(version)) {
      message.textContent = "버전은 0.0.5 또는 v0.0.5 형식으로 입력해 주세요.";
      input.focus();
      return;
    }

    submit.disabled = true;
    message.textContent = "저장하는 중이에요.";
    try {
      const data = await request({ method: "PUT", body: JSON.stringify({ version: version }) });
      applyVersion(data?.version);
      message.textContent = "푸터 표시 버전을 변경했습니다.";
    } catch (error) {
      message.textContent = error.message || "버전을 저장하지 못했습니다.";
    } finally {
      submit.disabled = false;
    }
  });

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", load);
  } else {
    load();
  }

  window.__adminVersion = { load: load };
})();
