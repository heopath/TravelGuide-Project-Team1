/* 티켓 상품 상세 (#281)
 *
 * 상품 하나를 통째로 보여주고 그 자리에서 예매까지 한다. 예전에는 예약 화면의 목록 안에서
 * 시간대만 펼쳐 봤는데, 무엇을 사는지(어디서·몇 시에·얼마에)를 알 수 없었다. 티켓은 그
 * 내용을 보고 사는 물건이라 상세가 따로 있어야 한다.
 *
 * 예매는 예약 화면과 같은 길을 탄다 — 대기열에 서고, 순서가 되면 예약이 만들어진다.
 * 결제는 여기서 하지 않는다. 담은 뒤 마이페이지나 `내 예약`에서 결제한다.
 */
(function () {
  "use strict";

  const $ = (selector) => document.querySelector(selector);
  const won = (value) => `${Number(value || 0).toLocaleString("ko-KR")}원`;
  const hhmm = (value) => String(value || "").slice(0, 5);

  /* 장소 분류는 코드로 온다. 화면에는 사람이 쓰는 말로 적는다. */
  const CATEGORY_LABELS = {
    ATTRACTION: "관광지", ACTIVITY: "체험·액티비티", CAFE: "카페",
    RESTAURANT: "맛집", CULTURE: "문화·전시", NATURE: "자연", SHOPPING: "쇼핑",
  };

  const SALE_BADGES = {
    ON_SALE: "예매 가능",
    SCHEDULED: "오픈 예정",
    ENDED: "판매 종료",
  };

  const DAY_LABELS = ["일", "월", "화", "수", "목", "금", "토"];

  const state = {
    product: null,
    slots: [],
    serverTime: null,
    date: null,
    slotId: null,
    quantity: 1,
    countdown: null,
  };

  /** 주소는 `/booking/tickets/{상품번호}`다. 번호가 아니면 상세를 열 수 없다. */
  function productId() {
    const last = window.location.pathname.split("/").filter(Boolean).pop();
    return /^\d+$/.test(last) ? last : null;
  }

  /* 여행은 선택이다. 있으면 예약에 붙이고, 없으면 여행 없는 티켓으로 산다. (#255) */
  function currentTripId() {
    const value = new URL(window.location.href).searchParams.get("tripId") || "";
    return /^\d+$/.test(value) ? value : null;
  }

  function setState(message, isError) {
    const el = $("[data-ticket-state]");
    el.textContent = message;
    el.classList.toggle("error", Boolean(isError));
    el.hidden = false;
  }

  async function jsonRequest(url, options = {}) {
    const response = await fetch(url, {
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
        ...(options.body ? { "Content-Type": "application/json" } : {}),
      },
      ...options,
    });
    const payload = await response.json().catch(() => null);

    if (response.status === 401) {
      /*
       * 화면을 그릴 때 로그인 여부를 미리 판정하지 않는다. auth-state.js가 응답을 받기
       * 전에는 값이 비어 있어 레이스가 생긴다. 401을 받고 나서 보낸다.
       */
      const back = encodeURIComponent(window.location.pathname + window.location.search);
      window.location.href = `/auth/login?redirect=${back}`;
      throw new Error("로그인이 필요합니다.");
    }
    if (!response.ok || !payload?.success) {
      throw new Error(payload?.message || "요청을 처리하지 못했습니다.");
    }
    return payload.data;
  }

  /* ── 그리기 ── */

  function make(tagName, className, text) {
    const node = document.createElement(tagName);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = String(text);
    return node;
  }

  /* 서버 문자열은 innerHTML로 넣지 않는다. 상품명·옵션명·장소명은 관리자 입력값이다. */
  function fact(label, value) {
    if (!value) return null;
    const row = make("div");
    row.append(make("dt", "", label), make("dd", "", value));
    return row;
  }

  function safeImageUrl(value) {
    if (!value) return null;
    try {
      const parsed = new URL(String(value), window.location.origin);
      return ["http:", "https:"].includes(parsed.protocol) ? parsed.href : null;
    } catch (_error) {
      return null;
    }
  }

  function dateParts(value) {
    const date = new Date(`${value}T00:00:00`);
    if (Number.isNaN(date.getTime())) return { day: value, month: "", weekday: "" };
    return {
      day: String(date.getDate()),
      month: `${date.getMonth() + 1}월`,
      weekday: `${DAY_LABELS[date.getDay()]}요일`,
    };
  }

  function periodLabel(product) {
    if (!product.firstUsageDate) return "";
    return product.firstUsageDate === product.lastUsageDate
      ? product.firstUsageDate
      : `${product.firstUsageDate} ~ ${product.lastUsageDate}`;
  }

  /** 옵션별 가격. `성인 10,000원 · 청소년 8,000원` */
  function priceLabel() {
    const seen = new Map();
    state.slots.forEach((slot) => {
      if (!seen.has(slot.optionName)) seen.set(slot.optionName, slot.unitPrice);
    });
    return [...seen.entries()]
      .map(([name, price]) => `${name} ${won(price)}`)
      .join(" · ");
  }

  /** 회차 시간대. 시간이 없는 상품(종일권)은 그렇게 적는다. */
  function timeLabel(slot) {
    if (!slot.startTime) return "시간 자유";
    return slot.endTime
      ? `${hhmm(slot.startTime)}–${hhmm(slot.endTime)}`
      : hhmm(slot.startTime);
  }

  function renderHead() {
    const product = state.product;
    const category = CATEGORY_LABELS[product.category] || "티켓·액티비티";
    $("[data-ticket-category]").textContent = category;
    $("[data-ticket-region]").textContent = product.region || product.city || "";
    $("[data-ticket-name]").textContent = product.productName;
    $("[data-ticket-place]").textContent = product.placeName || "";
    $("[data-ticket-poster-category]").textContent = category.toUpperCase();
    $("[data-ticket-poster-name]").textContent = product.productName;

    const image = $("[data-ticket-image]");
    const fallback = $("[data-ticket-image-fallback]");
    const imageUrl = safeImageUrl(product.imageUrl);
    image.hidden = !imageUrl;
    fallback.hidden = Boolean(imageUrl);
    if (imageUrl) {
      image.src = imageUrl;
      image.alt = `${product.productName} 대표 이미지`;
      image.addEventListener("error", () => {
        image.hidden = true;
        fallback.hidden = false;
      }, { once: true });
    } else {
      image.removeAttribute("src");
      image.alt = "";
    }

    const badge = $("[data-ticket-sale-badge]");
    const label = SALE_BADGES[product.saleState] || "판매 상태 확인";
    badge.textContent = label;
    badge.dataset.state = product.saleState || "";

    $("[data-ticket-head]").hidden = false;
    document.title = `${product.productName} — All My Trips`;
  }

  function renderInfo() {
    const product = state.product;
    const rows = [
      fact("이용 기간", periodLabel(product)),
      fact("이용 시간", [...new Set(state.slots.map(timeLabel))].join(" · ")),
      fact("장소", product.placeName),
      fact("가격", priceLabel()),
      fact("남은 수량", Number.isFinite(Number(product.remainingQuantity))
        ? `${product.remainingQuantity}개` : ""),
      fact("1인 최대", state.slots[0]?.maxQuantityPerUser
        ? `${state.slots[0].maxQuantityPerUser}매` : ""),
    ].filter(Boolean);
    $("[data-ticket-facts]").replaceChildren(...rows);
    $("[data-ticket-min-price]").textContent = won(product.minUnitPrice);

    const about = $("[data-ticket-about]");
    about.hidden = !product.description;
    if (product.description) $("[data-ticket-description]").textContent = product.description;

    const venue = $("[data-ticket-venue]");
    venue.hidden = !product.placeName;
    if (product.placeName) {
      $("[data-ticket-venue-name]").textContent = product.placeName;
      /* 주소는 등록할 때 선택 항목이다. 없으면 그 줄만 비운다. */
      $("[data-ticket-venue-address]").textContent =
        product.address || "상세 주소가 등록되지 않았어요.";
    }
  }

  function renderBookingPanel() {
    const upcoming = state.product.saleState === "SCHEDULED";
    const ended = state.product.saleState === "ENDED";

    $("[data-ticket-book-title]").textContent = upcoming
      ? "오픈 예정"
      : ended ? "판매 종료" : "예매하기";

    const lead = $("[data-ticket-book-lead]");
    if (upcoming) {
      lead.textContent = opensLabel();
      startCountdown();
    } else if (ended) {
      lead.textContent = "판매가 끝난 상품이에요.";
    } else {
      lead.textContent = "날짜와 회차를 골라 주세요.";
    }

    renderDates();
    renderSlots();
    renderQuantity();
    renderTotal();
    renderReserveButton();
    renderSteps();
  }

  function renderDates() {
    const dates = [...new Set(state.slots.map((slot) => slot.usageDate))].sort();
    const nodes = dates.map((date) => {
      const parts = dateParts(date);
      const button = make("button", `tk-chip tk-date-option${date === state.date ? " on" : ""}`);
      button.type = "button";
      button.dataset.ticketDate = date;
      button.setAttribute("aria-pressed", String(date === state.date));

      const day = make("span", "tk-date-day", parts.day);
      const copy = make("span", "tk-date-copy");
      copy.append(make("strong", "", `${parts.month} ${parts.weekday}`), make("small", "", date.replaceAll("-", ".")));
      const count = state.slots.filter((slot) => slot.usageDate === date).length;
      button.append(day, copy, make("em", "", `${count}개 회차`));
      return button;
    });
    $("[data-ticket-dates]").replaceChildren(...nodes);
  }

  function slotsOfDate() {
    return state.slots.filter((slot) => slot.usageDate === state.date);
  }

  function renderSlots() {
    const field = $("[data-ticket-slot-field]");
    field.hidden = !state.date;
    if (!state.date) return;

    const nodes = slotsOfDate().map((slot) => {
      const selected = String(slot.slotId) === String(state.slotId);
      const soldOut = Number(slot.remainingQuantity) <= 0;
      const button = make("button", `tk-chip tk-slot-option${selected ? " on" : ""}`);
      button.type = "button";
      button.dataset.ticketSlot = String(slot.slotId);
      button.disabled = soldOut;
      button.setAttribute("aria-pressed", String(selected));
      button.append(
        make("strong", "", `${timeLabel(slot)} · ${slot.optionName || "기본권"}`),
        make("span", "", won(slot.unitPrice)),
        make("small", "", soldOut ? "선택할 수 없는 회차" : `1인 최대 ${slot.maxQuantityPerUser || 1}매`),
        make("em", "", soldOut ? "매진" : `남은 ${slot.remainingQuantity}개`),
      );
      return button;
    });
    $("[data-ticket-slots]").replaceChildren(...nodes);
  }

  function selectedSlot() {
    return state.slots.find((slot) => String(slot.slotId) === String(state.slotId)) || null;
  }

  function renderQuantity() {
    const slot = selectedSlot();
    const field = $("[data-ticket-quantity-field]");
    field.hidden = !slot;
    if (!slot) return;

    const max = Math.max(0, Math.min(
      Number(slot.maxQuantityPerUser) || 1,
      Number(slot.remainingQuantity) || 0,
    ));
    if (max === 0) {
      field.hidden = true;
      return;
    }
    const input = $("[data-ticket-quantity]");
    input.max = String(max);
    if (state.quantity > max) state.quantity = max;
    input.value = String(state.quantity);
    $("[data-ticket-qty-note]").textContent =
      `1인 최대 ${slot.maxQuantityPerUser}매 · 남은 수량 ${slot.remainingQuantity}개`;
  }

  function renderTotal() {
    const slot = selectedSlot();
    const row = $("[data-ticket-total-row]");
    row.hidden = !slot;
    if (slot) {
      $("[data-ticket-total]").textContent = won(slot.unitPrice * state.quantity);
      $("[data-ticket-total-detail]").textContent = `${slot.optionName || "티켓"} ${state.quantity}매`;
    }
  }

  function renderReserveButton() {
    const button = $("[data-ticket-reserve]");
    const slot = selectedSlot();
    const upcoming = state.product.saleState === "SCHEDULED";
    const ended = state.product.saleState === "ENDED";

    const soldOut = slot && Number(slot.remainingQuantity) <= 0;
    button.disabled = Boolean(upcoming || ended || soldOut || !slot);
    button.textContent = upcoming ? "오픈 전이에요"
      : ended ? "판매가 끝났어요"
        : soldOut ? "매진된 회차예요"
          : slot ? "선택한 티켓 예매하기" : "회차를 골라 주세요";
  }

  function renderSteps() {
    const slot = selectedSlot();
    const steps = {
      date: Boolean(state.date),
      slot: Boolean(slot),
      quantity: Boolean(slot),
    };
    document.querySelectorAll("[data-ticket-step]").forEach((item) => {
      const name = item.dataset.ticketStep;
      item.classList.toggle("done", steps[name] && name !== "quantity");
      item.classList.toggle("on",
        (name === "date" && !state.date)
        || (name === "slot" && state.date && !slot)
        || (name === "quantity" && Boolean(slot)));
    });
  }

  /* ── 오픈 예정 카운트다운 (#256) ── */

  /** 남은 시간은 서버가 준 두 시각의 차이로 센다. 손님 기기 시계는 믿을 수 없다. */
  function opensLabel() {
    const opensAt = new Date(state.product.opensAt).getTime();
    const offset = new Date(state.serverTime).getTime() - Date.now();
    const left = opensAt - (Date.now() + offset);
    if (left <= 0) return "곧 열려요.";

    const seconds = Math.ceil(left / 1000);
    if (seconds >= 86400) return `${Math.floor(seconds / 86400)}일 뒤에 열려요.`;
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    return hours > 0
      ? `${hours}시간 ${minutes}분 뒤에 열려요.`
      : `${minutes}분 ${String(seconds % 60).padStart(2, "0")}초 뒤에 열려요.`;
  }

  /**
   * 오픈 시각이 지나면 다시 받아 온다.
   *
   * <p>0이 되자마자 화면이 스스로 파는 상태로 바꾸지 않는다. 여는 쪽 판단은 서버에 있고,
   * 화면이 정하면 아직 안 열린 상품을 살 수 있는 것처럼 보인다.
   */
  function startCountdown() {
    stopCountdown();
    state.countdown = window.setInterval(() => {
      const opensAt = new Date(state.product.opensAt).getTime();
      const offset = new Date(state.serverTime).getTime() - Date.now();
      if (opensAt - (Date.now() + offset) <= 0) {
        stopCountdown();
        void load();
        return;
      }
      $("[data-ticket-book-lead]").textContent = opensLabel();
    }, 1000);
  }

  function stopCountdown() {
    if (state.countdown) window.clearInterval(state.countdown);
    state.countdown = null;
  }

  /* ── 예매 ── */

  function showError(message) {
    const box = $("[data-ticket-error]");
    box.textContent = message;
    box.hidden = !message;
  }

  async function reserve(button) {
    const slot = selectedSlot();
    if (!slot) return;

    button.disabled = true;
    showError("");

    try {
      const requestKey = window.crypto?.randomUUID
        ? window.crypto.randomUUID()
        : `${Date.now()}-${slot.slotId}`;
      const tripId = currentTripId();
      const body = { slotId: Number(slot.slotId), quantity: state.quantity, requestKey };
      if (tripId) body.tripId = Number(tripId);

      const queue = await jsonRequest("/api/v1/booking-queue/entries", {
        method: "POST",
        body: JSON.stringify(body),
      });

      /*
       * 대기열이 한산하면 바로 담기고, 몰리면 대기 화면으로 보낸다. 예약 화면과 같은 길이다.
       */
      if (queue.status === "READY") {
        const reservation = await jsonRequest(
          `/api/v1/booking-queue/entries/${encodeURIComponent(queue.token)}/reservation`,
          { method: "POST" });
        setState(`${reservation.productName}을(를) 모의 예약에 담았어요. `
          + "마이페이지 · 예약 내역에서 결제하면 티켓이 발급됩니다.");
        button.textContent = "담았어요";
        return;
      }
      window.location.href = `/booking/queue?token=${encodeURIComponent(queue.token)}`;
    } catch (error) {
      showError(error.message || "티켓을 담지 못했어요.");
      button.disabled = false;
    }
  }

  /* ── 시작 ── */

  async function load() {
    const id = productId();
    if (!id) {
      setState("상품을 찾을 수 없어요. 티켓 목록에서 다시 골라 주세요.", true);
      return;
    }

    try {
      const detail = await jsonRequest(`/api/v1/tickets/products/${encodeURIComponent(id)}`);
      state.product = detail.product;
      state.slots = detail.slots || [];
      state.serverTime = detail.serverTime;
      /* 날짜는 첫 회차로 미리 골라 둔다. 빈 화면에서 시작하지 않게 한다. */
      state.date = state.slots[0]?.usageDate || null;
      state.slotId = null;
      state.quantity = 1;
    } catch (error) {
      setState(error.message || "티켓 정보를 불러오지 못했어요.", true);
      return;
    }

    $("[data-ticket-state]").hidden = true;
    $("[data-ticket-body]").hidden = false;
    renderHead();
    renderInfo();
    renderBookingPanel();
  }

  function bind() {
    document.addEventListener("click", (event) => {
      const date = event.target.closest("[data-ticket-date]");
      if (date) {
        state.date = date.dataset.ticketDate;
        state.slotId = null;
        renderDates();
        renderSlots();
        renderQuantity();
        renderTotal();
        renderReserveButton();
        renderSteps();
        return;
      }

      const slot = event.target.closest("[data-ticket-slot]");
      if (slot) {
        state.slotId = slot.dataset.ticketSlot;
        state.quantity = 1;
        renderSlots();
        renderQuantity();
        renderTotal();
        renderReserveButton();
        renderSteps();
        return;
      }

      const down = event.target.closest("[data-ticket-qty-down]");
      const up = event.target.closest("[data-ticket-qty-up]");
      if (down || up) {
        const input = $("[data-ticket-quantity]");
        const max = Number(input.max) || 1;
        state.quantity = Math.min(max, Math.max(1, state.quantity + (up ? 1 : -1)));
        renderQuantity();
        renderTotal();
        return;
      }

      const reserveButton = event.target.closest("[data-ticket-reserve]");
      if (reserveButton) void reserve(reserveButton);
    });

    document.addEventListener("input", (event) => {
      if (!event.target.matches("[data-ticket-quantity]")) return;
      const max = Number(event.target.max) || 1;
      state.quantity = Math.min(max, Math.max(1, Number(event.target.value) || 1));
      renderTotal();
    });
  }

  document.addEventListener("DOMContentLoaded", () => {
    bind();
    void load();
  });

  window.__ticketDetail = { state, load, reserve };
})();
