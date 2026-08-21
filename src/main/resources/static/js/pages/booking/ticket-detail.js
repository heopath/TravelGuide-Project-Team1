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
    /* 여행에 붙는 티켓이면 그 기간. 기간 밖 회차는 서버가 거절하므로 미리 걸러 준다. */
    tripPeriod: null,
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

  /*
   * 예약 화면으로 돌아가는 주소. tripId를 반드시 달고 간다.
   *
   * 예전에는 마크업에 `/booking/flights?tab=ticket`이 박혀 있었다. 티켓을 보러 왔다가
   * 돌아가면 여행을 잃어, 골라 둔 가는 편·오는 편·숙소가 전부 사라지고 진행 현황이
   * 3/4에서 1/4로 떨어졌다. 티켓 하나 구경한 값으로는 너무 비싸다.
   */
  function bookingUrl(tab) {
    const trip = currentTripId();
    return "/booking/flights?tab=" + (tab || "ticket")
      + (trip ? "&tripId=" + encodeURIComponent(trip) : "");
  }

  /** 이 날짜로 예매할 수 있나. 여행이 없으면 아무 날이나 된다. */
  function withinTrip(date) {
    const period = state.tripPeriod;
    if (!period || !period.startDate || !period.endDate) return true;
    return String(date) >= String(period.startDate) && String(date) <= String(period.endDate);
  }

  /*
   * 여행 기간을 미리 받아 둔다.
   *
   * 예전에는 첫 날짜가 그냥 골라졌다. 9월 여행을 짜고 들어와도 8월 회차가 골라져 있어,
   * 예매를 누르고 나서야 "여행 기간 밖"이라고 거절당했다. 무엇이 잘못인지도 알기 어려웠다.
   */
  async function loadTripPeriod() {
    const tripId = currentTripId();
    if (!tripId) return;
    try {
      const trip = await jsonRequest(`/api/v1/trips/${encodeURIComponent(tripId)}`);
      if (trip?.startDate && trip?.endDate) {
        state.tripPeriod = { startDate: trip.startDate, endDate: trip.endDate, title: trip.title };
      }
    } catch (error) { /* 못 읽어도 예매는 된다. 서버가 마지막에 한 번 더 본다. */ }
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
      /*
       * 여행 기간 밖은 눌러도 서버가 거절한다. 누르기 전에 알려 주는 편이 낫다.
       * 지우지는 않는다 — 왜 못 고르는지 보여야 날짜를 바꿀 생각을 한다.
       */
      const outside = !withinTrip(date);
      if (outside) {
        button.classList.add("out");
        button.disabled = true;
        button.title = "여행 기간 밖이에요";
      }
      button.append(day, copy, make("em", "", outside ? "여행 기간 밖" : `${count}개 회차`));
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

  /**
   * 담은 예약을 그 자리에서 결제한다.
   *
   * 결제까지 마쳐야 티켓이 나온다. 담기만 하고 창을 닫으면 손님은 산 줄 아는데 티켓이 없다.
   * 그래서 담자마자 결제창을 띄운다.
   *
   * 다만 결제는 여러 이유로 막힌다 — 카드사 점검, 잔액 부족, 창을 닫는 것. 그때 예약이
   * 사라지지는 않으므로, 마이페이지 예약 내역에서 마저 낼 수 있다고 알려 준다.
   */
  async function payNow(reservation) {
    const summary = `${reservation.productName} · ${won(reservation.totalAmount || reservation.amount || 0)}`;
    try {
      const paid = await window.AllMyTripsTicketPayment.pay({
        reservationId: reservation.reservationId,
        summary,
        request: paymentRequest,
      });

      if (paid.cancelled) {
        modalState(`예약에 담았어요. 결제는 아직이에요 — ${reservation.productName}`);
        showPayLink("결제하러 가기 →");
        return;
      }

      /* 결제까지 끝났다. 예약이 어떻게 됐는지는 마이페이지에서 이어 본다. */
      modalState(`결제가 끝났어요. 티켓이 발급됐습니다.`);
      showPayLink("예약 내역 보기 →");
      window.setTimeout(() => { window.location.href = MYPAGE_RESERVATIONS; }, 1200);
    } catch (error) {
      /*
       * 결제만 실패했을 뿐 예약은 남아 있다. 그 사실을 먼저 말해야 손님이 다시 담지 않는다.
       */
      modalState(`${error.message || "결제하지 못했어요."} 예약은 그대로 있으니 `
        + "마이페이지 · 예약 내역에서 결제하실 수 있어요.", true);
      showPayLink("마이페이지에서 결제하기 →");
    }
  }

  /* 담은 뒤에만 보인다. 담기 전에 결제로 보내면 빈 예약 목록을 보게 된다. */
  function showPayLink(label) {
    const existing = $("[data-ticket-pay]");
    const anchor = existing || document.createElement("a");
    anchor.className = "primary-button wide tk-pay";
    anchor.setAttribute("data-ticket-pay", "");
    anchor.href = MYPAGE_RESERVATIONS;
    anchor.textContent = label || "마이페이지에서 결제하기 →";
    if (existing) return;
    /* 담기 버튼 자리에 둔다. 다음에 할 일이 바로 눈에 들어와야 한다. */
    const reserve = $("[data-ticket-reserve]");
    if (reserve && reserve.parentElement) reserve.parentElement.insertBefore(anchor, reserve.nextSibling);
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
        button.textContent = "담았어요";
        await payNow(reservation);
        return;
      }
      window.location.href = `/booking/queue?token=${encodeURIComponent(queue.token)}`;
    } catch (error) {
      showError(error.message || "티켓을 담지 못했어요.");
      button.disabled = false;
    }
  }

  /* ── 시작 ── */

  /*
   * 결제 모듈은 request(method, url, body)를 부르고 응답 전체에서 data를 꺼낸다.
   * 이 화면의 jsonRequest는 data만 돌려주므로 모양을 맞춰 준다.
   */
  async function paymentRequest(method, url, body) {
    const data = await jsonRequest(url, {
      method,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    return { data };
  }

  /** 마이페이지 예약 내역. 여기서 못 낸 결제를 마저 할 수 있다. */
  const MYPAGE_RESERVATIONS = "/mypage?view=tickets";

  /* ── 구매 모달 ──
     예매 패널이 오른쪽에 붙어 있을 때는, 설명을 읽으려 내려가면 무엇을 고르던 중이었는지가
     화면 밖으로 나갔다. 살 때는 사는 일에만 집중하는 편이 낫다. */

  function buyOpen() {
    const box = $("[data-ticket-modal]");
    if (!box) return;
    box.hidden = false;
    /* 뒤 화면이 같이 밀리면 모달 안에서 길을 잃는다. */
    document.body.style.overflow = "hidden";
    const first = $("[data-ticket-dates] button") || $("[data-ticket-modal-close]");
    if (first) first.focus();
  }

  function buyClose() {
    const box = $("[data-ticket-modal]");
    if (!box) return;
    box.hidden = true;
    document.body.style.overflow = "";
    const trigger = $("[data-ticket-buy]");
    if (trigger && !trigger.hidden) trigger.focus();
  }

  const buyIsOpen = () => {
    const box = $("[data-ticket-modal]");
    return Boolean(box) && !box.hidden;
  };

  /** 모달 안에서 말한다. 뒤 화면의 상태줄은 모달에 가려 보이지 않는다. */
  function modalState(message, isError) {
    const box = $("[data-ticket-error]");
    if (!box) return;
    box.textContent = message || "";
    box.hidden = !message;
    box.classList.toggle("ok", Boolean(message) && !isError);
  }

  function bindBuyModal() {
    const trigger = $("[data-ticket-buy]");
    if (trigger) trigger.addEventListener("click", buyOpen);

    const close = $("[data-ticket-modal-close]");
    if (close) close.addEventListener("click", buyClose);

    const box = $("[data-ticket-modal]");
    /* 바깥을 눌러 닫는다. 카드 안을 누른 것은 닫지 않는다. */
    if (box) box.addEventListener("click", (event) => {
      if (event.target === box) buyClose();
    });

    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && buyIsOpen()) buyClose();
    });
  }

  /* 마크업에 박힌 주소는 여행을 모른다. 화면을 열 때 이 여행의 주소로 바꾼다. */
  function fixBackLink() {
    const back = $(".tk-back");
    if (back) back.setAttribute("href", bookingUrl("ticket"));
  }

  async function load() {
    fixBackLink();
    bindBuyModal();
    /* 상품보다 먼저 읽는다. 날짜를 고를 때 기간을 이미 알고 있어야 한다. */
    await loadTripPeriod();

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
      /*
       * 날짜를 미리 골라 둔다. 빈 화면에서 시작하지 않게 한다.
       *
       * 여행이 있으면 그 기간 안에서 고른다. 첫 회차를 그냥 고르면 9월 여행에 8월 회차가
       * 골라져, 예매를 누르고 나서야 거절당한다.
       */
      const usable = state.slots.filter((slot) => withinTrip(slot.usageDate));
      state.date = (usable[0] || state.slots[0])?.usageDate || null;
      state.slotId = null;
      state.quantity = 1;
    } catch (error) {
      setState(error.message || "티켓 정보를 불러오지 못했어요.", true);
      return;
    }

    $("[data-ticket-state]").hidden = true;
    $("[data-ticket-body]").hidden = false;
    /* 상품을 못 불러왔는데 구매 버튼만 떠 있으면 눌러도 빈 모달이 열린다. */
    const buy = $("[data-ticket-buy]");
    if (buy) buy.hidden = false;
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
