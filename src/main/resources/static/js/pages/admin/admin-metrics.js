/* 관리자 운영 지표
 *
 * 네 칸 중 세 칸은 DB 집계, 오류율은 성능 지표와 같은 원본을 쓴다.
 * 값이 없는 칸은 0이 아니라 빈 자리로 둔다. 0은 "없음"으로 읽히는데,
 * 잰 적이 없다는 뜻일 때는 정반대로 오해된다.
 */
(function () {
  "use strict";

  const panel = document.querySelector('[data-admin-section="overview"]');
  if (!panel) return;

  const field = (name) => panel.querySelector(`[data-metric="${name}"]`);
  const fields = {
    todayReservations: field("todayReservations"),
    openInquiries: field("openInquiries"),
    lowStockSlots: field("lowStockSlots"),
    errorRate: field("errorRate"),
  };
  const note = panel.querySelector("[data-metrics-note]");
  const refresh = panel.querySelector("[data-metrics-refresh]");
  const stockCaption = panel.querySelector("[data-stock-caption]");
  if (!fields.todayReservations || !fields.errorRate) return;

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

  function blank() {
    Object.keys(fields).forEach(function (key) {
      if (fields[key]) fields[key].textContent = "—";
    });
  }

  function count(value) {
    return typeof value === "number" ? value.toLocaleString("ko-KR") : "—";
  }

  function render(data) {
    if (!data) {
      blank();
      return;
    }
    fields.todayReservations.textContent = count(data.todayReservations);
    if (fields.openInquiries) fields.openInquiries.textContent = count(data.openInquiries);
    if (fields.lowStockSlots) fields.lowStockSlots.textContent = count(data.lowStockSlots);

    /* 요청이 한 건도 없으면 서버가 errorRate를 내려보내지 않는다. 0%로 채우지 않는다. */
    fields.errorRate.textContent = typeof data.errorRate === "number"
      ? `${(data.errorRate * 100).toFixed(2)}%`
      : "—";

    /* 기준을 숨기면 숫자만 보고 판단하게 된다. 몇 개 이하를 센 것인지 함께 밝힌다. */
    if (stockCaption && typeof data.lowStockThreshold === "number") {
      stockCaption.textContent = `남은 자리 ${data.lowStockThreshold}개 이하`;
    }
    if (note) note.textContent = "";
  }

  async function load() {
    if (refresh) refresh.disabled = true;
    try {
      render(await request("/api/v1/admin/operation-metrics"));
    } catch (error) {
      blank();
      if (note) note.textContent = error.message || "운영 현황을 불러오지 못했어요.";
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

  window.__adminMetrics = { load: load };
})();
