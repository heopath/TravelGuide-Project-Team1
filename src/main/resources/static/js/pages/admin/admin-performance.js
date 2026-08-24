/* 관리자 성능 모니터링
 *
 * 값은 애플리케이션이 뜬 뒤부터의 누적이다. 구간 평균이 아니라서 오래 떠 있을수록
 * 최근 변화가 묻힌다. 화면에 기준 시각과 표본 수를 같이 적어 그 점을 드러낸다.
 */
(function () {
  "use strict";

  const panel = document.querySelector('[data-admin-section="performance"]');
  if (!panel) return;

  const fields = {
    tps: panel.querySelector('[data-metric="tps"]'),
    latency: panel.querySelector('[data-metric="latency"]'),
    failureRate: panel.querySelector('[data-metric="failureRate"]'),
  };
  const note = panel.querySelector("[data-performance-note]");
  const health = panel.querySelector("[data-performance-health]");
  const refresh = panel.querySelector("[data-performance-refresh]");
  if (!fields.tps || !fields.latency || !fields.failureRate) return;

  async function request(url) {
    const response = await fetch(url, {
      headers: { Accept: "application/json" },
      allMyTripsLoading: false,
    });
    const payload = await response.json().catch(function () { return null; });
    if (!response.ok || payload?.success === false) {
      if (response.status === 401) window.location.href = "/auth/login?redirect=/admin";
      if (response.status === 403) throw new Error("관리자만 접근할 수 있습니다.");
      throw new Error(payload?.message || "요청을 처리하지 못했습니다.");
    }
    return payload?.data ?? payload;
  }

  /* 표본이 없으면 0을 쓰지 않는다. 0.0%는 "오류 없음"으로 읽히는데 실제로는 잰 적이 없다는 뜻이다. */
  function blank() {
    Object.keys(fields).forEach(function (key) { fields[key].textContent = "—"; });
    if (health) { health.textContent = "상태를 판단할 수 없음"; health.dataset.level = "unknown"; }
  }

  function uptimeLabel(seconds) {
    if (!seconds) return "";
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    return hours > 0 ? `${hours}시간 ${minutes}분` : `${minutes}분`;
  }

  function render(data) {
    if (!data || !data.sampleCount) {
      blank();
      if (note) note.textContent = "아직 집계된 요청이 없어요. 화면을 사용하면 값이 쌓여요.";
      return;
    }
    fields.tps.textContent = data.tps.toFixed(2);
    fields.latency.textContent = `${Math.round(data.averageResponseMs).toLocaleString("ko-KR")}ms`;
    fields.failureRate.textContent = `${(data.errorRate * 100).toFixed(2)}%`;
    if (health) {
      const danger = data.averageResponseMs >= 1000 || data.errorRate >= 0.05;
      const warning = data.averageResponseMs >= 500 || data.errorRate >= 0.01;
      health.dataset.level = danger ? "danger" : (warning ? "warning" : "normal");
      health.textContent = danger ? "위험 · 즉시 확인 필요" : (warning ? "주의 · 지표 확인 필요" : "정상 범위");
    }
    if (note) {
      const uptime = uptimeLabel(data.uptimeSeconds);
      note.textContent = `가동 ${uptime || "직후"} 동안 요청 ${data.sampleCount.toLocaleString("ko-KR")}건 기준`
        + ` · 서버 오류 ${data.errorCount.toLocaleString("ko-KR")}건 (기동 후 누적)`;
    }
  }

  async function load() {
    if (refresh) refresh.disabled = true;
    try {
      render(await request("/api/v1/admin/performance"));
    } catch (error) {
      blank();
      if (note) note.textContent = error.message || "성능 지표를 불러오지 못했어요.";
    } finally {
      if (refresh) refresh.disabled = false;
    }
  }

  if (refresh) refresh.addEventListener("click", load);

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", load);
  } else {
    load();
  }

  window.__adminPerformance = { load: load };
})();
