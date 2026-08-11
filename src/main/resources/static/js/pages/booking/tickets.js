/* 예약 화면 · 티켓/액티비티 탭. 내부 실습 상품만 다루며 실제 결제를 하지 않는다. */
(function () {
  "use strict";

  const $ = (id) => document.getElementById(id);
  const esc = (value) => String(value ?? "").replace(/[&<>"']/g,
    (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const won = (value) => `${Number(value || 0).toLocaleString("ko-KR")}원`;
  const airportName = { CJU: "제주", PUS: "부산", GMP: "서울", ICN: "인천" };
  const state = { loading: false, searched: false, offers: [], reservation: null, error: null };

  function params() {
    const flight = window.__flightBooking?.getSearch?.() || {};
    const url = new URL(location.href);
    return {
      tripId: /^\d+$/.test(url.searchParams.get("tripId") || "") ? url.searchParams.get("tripId") : null,
      destination: airportName[flight.destination] || flight.destination || "",
      from: flight.departureDate,
      to: flight.returnDate || flight.departureDate
    };
  }

  function status(message, error) {
    $("ticketStatus").textContent = message;
    $("ticketStatus").className = `ticket-status${error ? " error" : ""}`;
    $("ticketStatus").hidden = false;
  }

  function card(offer) {
    const time = offer.startTime ? `${offer.startTime.slice(0, 5)}${offer.endTime ? `–${offer.endTime.slice(0, 5)}` : ""}` : "시간 자유";
    return `<article class="ticket-card" data-ticket-slot="${offer.slotId}">
      <div class="ticket-copy">
        <span>${esc(offer.region || offer.city || "여행지")} · ${esc(offer.usageDate)} · ${esc(time)}</span>
        <h3>${esc(offer.productName)}</h3>
        <p>${esc(offer.placeName)} · ${esc(offer.optionName)}</p>
        <small>남은 수량 ${offer.remainingQuantity}개 · 1인 최대 ${offer.maxQuantityPerUser}개</small>
      </div>
      <div class="ticket-action">
        <strong>${won(offer.unitPrice)}</strong><small>1인 · 실습가</small>
        <label>수량 <input type="number" min="1" max="${offer.maxQuantityPerUser}" value="1" data-ticket-quantity></label>
        <button type="button" data-ticket-reserve="${offer.slotId}">모의 예약 담기</button>
      </div>
    </article>`;
  }

  function render() {
    $("ticketList").innerHTML = state.offers.map(card).join("");
    if (state.loading) return status("티켓 정보를 불러오는 중이에요.");
    if (state.error) return status(state.error, true);
    if (state.searched && !state.offers.length) return status("여행 기간에 판매 중인 실습 티켓이 없습니다.");
    $("ticketStatus").hidden = true;
  }

  async function search() {
    if (state.loading || state.searched) return;
    const query = params();
    if (!query.from || !query.to) return status("여행 날짜를 먼저 선택해 주세요.", true);
    state.loading = true;
    state.error = null;
    render();
    try {
      const url = `/api/v1/tickets?destination=${encodeURIComponent(query.destination)}&from=${query.from}&to=${query.to}`;
      const response = await fetch(url, { headers: { Accept: "application/json" }, credentials: "same-origin" });
      const payload = await response.json().catch(() => null);
      if (!response.ok || !payload?.success) throw new Error("TICKET_SEARCH_FAILED");
      state.offers = payload.data || [];
      state.searched = true;
    } catch (error) {
      state.searched = true;
      state.error = "티켓 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
    } finally {
      state.loading = false;
      render();
    }
  }

  async function restore() {
    const query = params();
    if (!query.tripId) return;
    try {
      const response = await fetch(`/api/v1/ticket-reservations?tripId=${query.tripId}`, {
        headers: { Accept: "application/json" }, credentials: "same-origin"
      });
      const payload = await response.json();
      const reservation = (payload?.data || []).find((item) => !["CANCELLED", "EXPIRED"].includes(item.status)) || null;
      if (reservation) selected(reservation);
      else state.reservation = null;
    } catch (error) { /* 복원 실패가 상품 조회를 막지는 않는다. */ }
  }

  function selected(reservation) {
    state.reservation = reservation;
    window.dispatchEvent(new CustomEvent("allmytrips:ticket-reserved", { detail: { reservation } }));
    status(`${reservation.productName}을(를) 모의 예약에 담았습니다. 실제 결제는 이루어지지 않았습니다.`);
  }

  async function reserve(slotId, button) {
    const query = params();
    if (!query.tripId) return status("여행을 먼저 선택해야 티켓을 담을 수 있습니다.", true);
    const card = button.closest("[data-ticket-slot]");
    const quantity = Math.max(1, Number(card.querySelector("[data-ticket-quantity]").value) || 1);
    button.disabled = true;
    try {
      const requestKey = crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${slotId}`;
      const response = await fetch("/api/v1/ticket-reservations", {
        method: "POST", credentials: "same-origin",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ tripId: Number(query.tripId), slotId: Number(slotId), quantity, requestKey })
      });
      const payload = await response.json().catch(() => null);
      if (!response.ok || !payload?.success) throw new Error(payload?.message || "TICKET_RESERVE_FAILED");
      selected(payload.data);
    } catch (error) {
      status(error.message === "TICKET_RESERVE_FAILED" ? "티켓을 담지 못했습니다." : error.message, true);
    } finally { button.disabled = false; }
  }

  function bind() {
    window.addEventListener("allmytrips:booking-tab-changed", (event) => {
      if (event.detail?.tab === "ticket") search();
    });
    window.addEventListener("allmytrips:ticket-cancelled", (event) => {
      if (String(state.reservation?.reservationId) !== String(event.detail?.reservationId)) return;
      state.reservation = null;
      status("모의 예약을 취소했습니다. 취소한 수량은 다시 예약할 수 있습니다.");
      void restore();
    });
    $("ticketList").addEventListener("click", (event) => {
      const button = event.target.closest("[data-ticket-reserve]");
      if (button) reserve(button.dataset.ticketReserve, button);
    });
  }

  document.addEventListener("DOMContentLoaded", () => {
    bind();
    restore();
    if (new URL(location.href).searchParams.get("tab") === "ticket") search();
  });
  window.__ticketBooking = { state, search, reserve, restore };
})();
