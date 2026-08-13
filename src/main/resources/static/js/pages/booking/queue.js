/* 혼잡할 때만 표시되는 티켓 예약 대기열. 서버 순번을 2초마다 갱신한다. */
(function () {
  "use strict";

  const $ = (id) => document.getElementById(id);
  const token = new URL(location.href).searchParams.get("token") || "";
  const state = { polling: false, completing: false, timer: null, status: null };

  function apiError(payload, fallback) {
    return new Error(payload?.message || fallback);
  }

  async function request(url, options = {}) {
    const response = await fetch(url, {
      credentials: "same-origin",
      headers: { Accept: "application/json", ...(options.headers || {}) },
      ...options
    });
    const payload = await response.json().catch(() => null);
    if (!response.ok || !payload?.success) throw apiError(payload, "대기열 정보를 확인하지 못했습니다.");
    return payload.data;
  }

  function waitText(seconds) {
    if (seconds < 60) return `예상 대기 ${Math.max(1, seconds)}초`;
    return `예상 대기 약 ${Math.ceil(seconds / 60)}분`;
  }

  function expiryText(expiresAt) {
    if (!expiresAt) return "";
    const seconds = Math.max(0, Math.ceil((new Date(expiresAt).getTime() - Date.now()) / 1000));
    const minutes = Math.floor(seconds / 60);
    return `순번 유지 시간 ${minutes}:${String(seconds % 60).padStart(2, "0")}`;
  }

  function render(status) {
    state.status = status;
    const ready = ["READY", "PROCESSING", "COMPLETED"].includes(status.status);
    $("queuePosition").textContent = ready ? "입장 중" : Number(status.position || 0).toLocaleString("ko-KR");
    $("queueEstimate").textContent = ready ? "예약 화면으로 연결하고 있습니다." : waitText(status.estimatedWaitSeconds || 1);
    $("queueProgress").style.width = `${status.progressPercent || 0}%`;
    $("queueProgress").parentElement.setAttribute("aria-valuenow", String(status.progressPercent || 0));
    $("queueExpiry").textContent = expiryText(status.expiresAt);
    $("queueError").hidden = true;
    $("queueRetry").hidden = true;
  }

  function fail(message) {
    clearTimeout(state.timer);
    $("queueError").textContent = message;
    $("queueError").hidden = false;
    $("queueRetry").hidden = false;
    $("queueEstimate").textContent = "자동 확인이 잠시 멈췄습니다.";
  }

  function navigate(url) {
    if (typeof window.__bookingQueueNavigate === "function") window.__bookingQueueNavigate(url);
    else location.replace(url);
  }

  async function complete(status) {
    if (state.completing) return;
    state.completing = true;
    try {
      const reservation = await request(`/api/v1/booking-queue/entries/${encodeURIComponent(token)}/reservation`, {
        method: "POST"
      });
      navigate(`/booking/flights?tab=ticket&tripId=${encodeURIComponent(reservation.tripId || status.tripId)}`);
    } catch (error) {
      state.completing = false;
      fail(error.message);
    }
  }

  async function refresh() {
    if (state.polling || !token) return;
    state.polling = true;
    try {
      const status = await request(`/api/v1/booking-queue/entries/${encodeURIComponent(token)}`);
      render(status);
      if (["READY", "COMPLETED"].includes(status.status)) await complete(status);
      else state.timer = setTimeout(refresh, 2000);
    } catch (error) {
      fail(error.message);
    } finally {
      state.polling = false;
    }
  }

  async function leave() {
    clearTimeout(state.timer);
    if (token) {
      try { await request(`/api/v1/booking-queue/entries/${encodeURIComponent(token)}`, { method: "DELETE" }); }
      catch (error) { /* 만료된 순번도 화면에서는 그대로 나갈 수 있다. */ }
    }
    const tripId = state.status?.tripId;
    navigate(`/booking/flights?tab=ticket${tripId ? `&tripId=${encodeURIComponent(tripId)}` : ""}`);
  }

  document.addEventListener("DOMContentLoaded", () => {
    $("queueRetry").addEventListener("click", refresh);
    $("queueLeave").addEventListener("click", leave);
    if (!/^[a-f0-9]{32}$/.test(token)) return fail("유효한 대기 순번이 없습니다. 티켓 상품을 다시 선택해 주세요.");
    refresh();
  });

  window.__bookingQueue = { state, refresh, leave };
})();
