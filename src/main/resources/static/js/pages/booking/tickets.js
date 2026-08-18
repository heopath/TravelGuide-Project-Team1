/* 예약 화면 · 티켓/액티비티 탭. 내부 실습 상품만 다루며 실제 결제를 하지 않는다.
 *
 * 상품을 먼저 고르고 그 안에서 날짜를 고른다. (#255)
 * 예전에는 여행 기간으로 시간대를 훑었는데, 여행 날짜와 겹치는 시간대가 없으면 팔고 있는
 * 티켓인데도 화면이 통째로 비었다. 실제로 8월 여행에서 9월에만 열린 상품 20개가 전부
 * 안 보였다. 티켓은 여행 계획과 무관하게 팔리므로 상품이 먼저다.
 */
(function () {
  "use strict";

  const $ = (id) => document.getElementById(id);
  const esc = (value) => String(value ?? "").replace(/[&<>"']/g,
    (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const won = (value) => `${Number(value || 0).toLocaleString("ko-KR")}원`;
  const hhmm = (value) => String(value || "").slice(0, 5);

  const state = {
    loading: false,
    loaded: false,
    products: [],
    /* null이면 상품 목록, 값이 있으면 그 상품의 시간대 화면이다. */
    detail: null,
    reservation: null,
    error: null,
  };

  /* 여행은 이제 선택이다. 있으면 예약에 붙이고, 없으면 여행 없는 티켓으로 산다. */
  function currentTripId() {
    const value = new URL(location.href).searchParams.get("tripId") || "";
    return /^\d+$/.test(value) ? value : null;
  }

  function status(message, error) {
    const el = $("ticketStatus");
    el.textContent = message;
    el.className = `ticket-status${error ? " error" : ""}`;
    el.hidden = false;
  }

  function periodLabel(product) {
    if (!product.firstUsageDate) return "이용일 미정";
    return product.firstUsageDate === product.lastUsageDate
      ? product.firstUsageDate
      : `${product.firstUsageDate} ~ ${product.lastUsageDate}`;
  }

  function productCard(product) {
    return `<article class="ticket-card" data-ticket-product="${product.productId}">
      <div class="ticket-copy">
        <span>${esc(product.region || product.city || "여행지")} · ${esc(periodLabel(product))}</span>
        <h3>${esc(product.productName)}</h3>
        <p>${esc(product.placeName)}</p>
        <small>선택 가능한 시간대 ${product.availableSlotCount}개 · 남은 수량 ${product.remainingQuantity}개</small>
      </div>
      <div class="ticket-action">
        <strong>${won(product.minUnitPrice)}</strong><small>1인 최저가 · 실습가</small>
        <button type="button" data-ticket-open="${product.productId}">날짜 고르기</button>
      </div>
    </article>`;
  }

  function slotCard(offer) {
    const time = offer.startTime
      ? `${hhmm(offer.startTime)}${offer.endTime ? `–${hhmm(offer.endTime)}` : ""}`
      : "시간 자유";
    return `<article class="ticket-card" data-ticket-slot="${offer.slotId}">
      <div class="ticket-copy">
        <span>${esc(offer.usageDate)} · ${esc(time)}</span>
        <h3>${esc(offer.optionName)}</h3>
        <p>${esc(offer.placeName)}</p>
        <small>남은 수량 ${offer.remainingQuantity}개 · 1인 최대 ${offer.maxQuantityPerUser}개</small>
      </div>
      <div class="ticket-action">
        <strong>${won(offer.unitPrice)}</strong><small>1인 · 실습가</small>
        <label>수량 <input type="number" min="1" max="${offer.maxQuantityPerUser}" value="1" data-ticket-quantity></label>
        <button type="button" data-ticket-reserve="${offer.slotId}">모의 예약 담기</button>
      </div>
    </article>`;
  }

  function detailHeader(product) {
    return `<div class="ticket-detail-head">
      <button type="button" data-ticket-back>← 상품 목록</button>
      <div>
        <h3>${esc(product.productName)}</h3>
        <p>${esc(product.placeName)} · ${esc(periodLabel(product))}</p>
      </div>
    </div>`;
  }

  function render() {
    const list = $("ticketList");
    if (state.detail) {
      list.innerHTML = detailHeader(state.detail.product)
        + state.detail.slots.map(slotCard).join("");
    } else {
      list.innerHTML = state.products.map(productCard).join("");
    }
    if (state.loading) return status("티켓 정보를 불러오는 중이에요.");
    if (state.error) return status(state.error, true);
    if (state.detail && !state.detail.slots.length) {
      return status("이 상품은 지금 살 수 있는 시간대가 없어요.", true);
    }
    if (!state.detail && state.loaded && !state.products.length) {
      return status("지금 판매 중인 실습 티켓이 없습니다.");
    }
    $("ticketStatus").hidden = true;
  }

  async function jsonRequest(url, options = {}) {
    const response = await fetch(url, {
      credentials: "same-origin",
      headers: { Accept: "application/json", ...(options.headers || {}) },
      ...options,
    });
    const payload = await response.json().catch(() => null);
    if (!response.ok || !payload?.success) throw new Error(payload?.message || "TICKET_REQUEST_FAILED");
    return payload.data;
  }

  /* 날짜도 여행도 필요 없다. 판매 중인 상품을 그대로 받는다. */
  async function loadProducts() {
    if (state.loading || state.loaded) return;
    state.loading = true;
    state.error = null;
    render();
    try {
      const page = await jsonRequest("/api/v1/tickets/products?page=0&size=20");
      state.products = page?.items || [];
      state.loaded = true;
    } catch (error) {
      state.loaded = true;
      state.error = "티켓 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
    } finally {
      state.loading = false;
      render();
    }
  }

  async function openProduct(productId) {
    state.loading = true;
    state.error = null;
    render();
    try {
      state.detail = await jsonRequest(`/api/v1/tickets/products/${encodeURIComponent(productId)}`);
    } catch (error) {
      state.error = "상품 정보를 불러오지 못했습니다.";
    } finally {
      state.loading = false;
      render();
    }
  }

  function backToList() {
    state.detail = null;
    state.error = null;
    render();
  }

  async function restore() {
    const tripId = currentTripId();
    try {
      /* 여행이 없으면 사용자 전체 티켓에서 살아 있는 것을 찾는다. (#255) */
      const url = tripId ? `/api/v1/ticket-reservations?tripId=${tripId}` : "/api/v1/ticket-reservations";
      const items = await jsonRequest(url);
      const reservation = (items || [])
        .find((item) => !["CANCELLED", "EXPIRED"].includes(item.status)) || null;
      if (reservation) selected(reservation);
      else state.reservation = null;
    } catch (error) { /* 로그인 전이거나 복원 실패. 상품 조회를 막지는 않는다. */ }
  }

  function selected(reservation) {
    state.reservation = reservation;
    window.dispatchEvent(new CustomEvent("allmytrips:ticket-reserved", { detail: { reservation } }));
    status(`${reservation.productName}을(를) 모의 예약에 담았습니다. 실제 결제는 이루어지지 않았습니다.`);
  }

  async function completeQueuedReservation(token) {
    return jsonRequest(`/api/v1/booking-queue/entries/${encodeURIComponent(token)}/reservation`, {
      method: "POST",
    });
  }

  async function reserve(slotId, button) {
    const card = button.closest("[data-ticket-slot]");
    const quantity = Math.max(1, Number(card.querySelector("[data-ticket-quantity]").value) || 1);
    button.disabled = true;
    try {
      const requestKey = crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${slotId}`;
      const tripId = currentTripId();
      /* tripId는 있을 때만 싣는다. 없으면 여행에 붙지 않은 티켓으로 산다. */
      const request = { slotId: Number(slotId), quantity, requestKey };
      if (tripId) request.tripId = Number(tripId);
      const queue = await jsonRequest("/api/v1/booking-queue/entries", {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify(request),
      });
      if (queue.status === "READY") {
        selected(await completeQueuedReservation(queue.token));
      } else {
        location.href = `/booking/queue?token=${encodeURIComponent(queue.token)}`;
      }
    } catch (error) {
      status(error.message === "TICKET_REQUEST_FAILED" ? "티켓을 담지 못했습니다." : error.message, true);
    } finally {
      button.disabled = false;
    }
  }

  function bind() {
    window.addEventListener("allmytrips:booking-tab-changed", (event) => {
      if (event.detail?.tab === "ticket") loadProducts();
    });
    window.addEventListener("allmytrips:ticket-cancelled", (event) => {
      if (String(state.reservation?.reservationId) !== String(event.detail?.reservationId)) return;
      state.reservation = null;
      status("모의 예약을 취소했습니다. 취소한 수량은 다시 예약할 수 있습니다.");
      void restore();
    });
    $("ticketList").addEventListener("click", (event) => {
      const back = event.target.closest("[data-ticket-back]");
      if (back) return backToList();
      const open = event.target.closest("[data-ticket-open]");
      if (open) return void openProduct(open.dataset.ticketOpen);
      const button = event.target.closest("[data-ticket-reserve]");
      if (button) void reserve(button.dataset.ticketReserve, button);
    });
  }

  document.addEventListener("DOMContentLoaded", () => {
    bind();
    restore();
    if (new URL(location.href).searchParams.get("tab") === "ticket") loadProducts();
  });
  window.__ticketBooking = { state, loadProducts, openProduct, reserve, restore };
})();
