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
    /*
     * 서버 시각과 이 기기 시각의 차이(ms). 오픈까지 남은 시간을 기기 시계로 세면 시계가
     * 틀어진 사람은 일찍 눌러 실패하거나 늦게 눌러 놓친다. 응답을 받을 때마다 다시 잰다. (#256)
     */
    clockOffset: 0,
    countdownTimer: null,
  };

  /** 서버 기준 현재 시각. */
  const serverNow = () => Date.now() + state.clockOffset;

  function syncClock(serverTime) {
    if (!serverTime) return;
    const parsed = new Date(serverTime).getTime();
    if (!Number.isNaN(parsed)) state.clockOffset = parsed - Date.now();
  }

  /** 오픈까지 남은 시간. 하루가 넘으면 날짜만 말한다 — 초 단위는 그때 의미가 없다. */
  function opensInLabel(opensAt) {
    const left = new Date(opensAt).getTime() - serverNow();
    if (left <= 0) return "곧 열려요";

    const seconds = Math.ceil(left / 1000);
    if (seconds >= 86400) return `${Math.floor(seconds / 86400)}일 뒤 오픈`;

    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const rest = seconds % 60;
    return hours > 0
      ? `${hours}시간 ${minutes}분 뒤 오픈`
      : `${minutes}분 ${String(rest).padStart(2, "0")}초 뒤 오픈`;
  }

  const isUpcoming = (product) => product?.saleState === "SCHEDULED";

  /** 오픈 시각. `2026-09-01T10:00` → `9월 1일 10:00` */
  function opensAtLabel(opensAt) {
    const at = new Date(opensAt);
    if (Number.isNaN(at.getTime())) return "";
    return `${at.getMonth() + 1}월 ${at.getDate()}일 `
      + `${String(at.getHours()).padStart(2, "0")}:${String(at.getMinutes()).padStart(2, "0")} 오픈`;
  }

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
    /*
     * 오픈 예정 상품도 목록에 나온다. (#256) 미리 보여야 손님이 그 시각에 모인다.
     * 둘러보는 것은 막지 않는다 — 무엇을 파는지 봐야 그 시각에 올지 정한다.
     */
    const upcoming = isUpcoming(product);
    return `<article class="ticket-card${upcoming ? " upcoming" : ""}" data-ticket-product="${product.productId}">
      <div class="ticket-copy">
        <span>${esc(product.region || product.city || "여행지")} · ${esc(periodLabel(product))}</span>
        <h3>${upcoming ? `<em class="ticket-badge">오픈 예정</em> ` : ""}${esc(product.productName)}</h3>
        <p>${esc(product.placeName)}</p>
        ${upcoming
          ? `<small class="ticket-opens" data-ticket-opens="${esc(product.opensAt)}">${
              esc(opensAtLabel(product.opensAt))} · ${esc(opensInLabel(product.opensAt))}</small>`
          : `<small>선택 가능한 시간대 ${product.availableSlotCount}개 · 남은 수량 ${product.remainingQuantity}개</small>`}
      </div>
      <div class="ticket-action">
        <strong>${won(product.minUnitPrice)}</strong><small>1인 최저가 · 실습가</small>
        <button type="button" data-ticket-open="${product.productId}">${upcoming ? "미리 보기" : "날짜 고르기"}</button>
      </div>
    </article>`;
  }

  function slotCard(offer) {
    /* 오픈 전에는 담을 수 없다. 눌리는 버튼을 두면 눌러 보고서야 안 된다는 걸 안다. */
    const upcoming = isUpcoming(offer);
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
        <label>수량 <input type="number" min="1" max="${offer.maxQuantityPerUser}" value="1" data-ticket-quantity${
          upcoming ? " disabled" : ""}></label>
        <button type="button" data-ticket-reserve="${offer.slotId}"${upcoming ? " disabled" : ""}>${
          upcoming ? "오픈 전이에요" : "모의 예약 담기"}</button>
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
    /* 화면을 다시 그릴 때마다 카운트다운 대상이 바뀐다. 그릴 때 함께 맞춘다. */
    window.setTimeout(syncCountdown, 0);
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

  /**
   * 오픈까지 남은 시간을 1초마다 다시 적는다. (#256)
   *
   * <p>남은 시간이 0이 되면 목록을 다시 받는다. 오픈 시각이 지나도 화면이 그대로면 손님이
   * 새로고침해야 살 수 있게 되는데, 그 몇 초가 지정 시각 판매에서는 결정적이다.
   *
   * <p>0이 되자마자 파는 상태로 바꾸지 않고 서버에 다시 묻는 이유는, 여는 쪽 판단이 서버에
   * 있기 때문이다. 화면이 스스로 열었다고 정하면 아직 안 열린 상품을 살 수 있는 것처럼 보인다.
   */
  function syncCountdown() {
    const labels = document.querySelectorAll("[data-ticket-opens]");

    if (!labels.length) {
      if (state.countdownTimer) {
        window.clearInterval(state.countdownTimer);
        state.countdownTimer = null;
      }
      return;
    }

    let opened = false;
    labels.forEach((label) => {
      const opensAt = label.dataset.ticketOpens;
      if (new Date(opensAt).getTime() - serverNow() <= 0) {
        opened = true;
        return;
      }
      label.textContent = `${opensAtLabel(opensAt)} · ${opensInLabel(opensAt)}`;
    });

    if (opened) reloadAfterOpen();

    if (!state.countdownTimer) {
      state.countdownTimer = window.setInterval(syncCountdown, 1000);
    }
  }

  /** 오픈 시각이 지났을 때 한 번만 다시 받는다. */
  async function reloadAfterOpen() {
    if (state.loading) return;
    if (state.detail) {
      await openProduct(state.detail.product.productId);
      return;
    }
    state.loaded = false;
    await loadProducts();
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
      syncClock(page?.serverTime);
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
      syncClock(state.detail?.serverTime);
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
