/* 관리자 예약 모니터링
 *
 * 조회만 한다. 상태 변경은 재고와 함께 움직여야 해서 이 화면에 두지 않는다.
 *
 * 만료 시각이 지났는데 PENDING인 예약을 따로 드러낸다. EXPIRED로 바꾸는 처리가 아직 없어
 * 상태만 보면 정상 대기처럼 보이지만, 실제로는 재고를 잡은 채 방치된 건이다.
 */
(function () {
  "use strict";

  const PAGE_SIZE = 20;
  const statusLabels = {
    PENDING: "대기",
    CONFIRMED: "확정",
    CANCELLED: "취소",
    EXPIRED: "만료",
    USED: "사용 완료",
  };

  const list = document.getElementById("reservationList");
  const empty = document.getElementById("reservationEmpty");
  const filter = document.querySelector("[data-reservation-filter]");
  const searchForm = document.querySelector("[data-reservation-search-form]");
  const search = document.querySelector("[data-reservation-search]");
  const searchClear = document.querySelector("[data-reservation-search-clear]");
  const refresh = document.querySelector("[data-reservation-refresh]");
  const pagination = document.querySelector("[data-reservation-pagination]");
  const alertBox = document.querySelector("[data-reservation-alert]");
  const alertText = document.querySelector("[data-reservation-alert-text]");
  const alertOpen = document.querySelector("[data-reservation-alert-open]");
  const count = document.querySelector("[data-reservation-count]");
  if (!list || !empty) return;

  let status = "";
  let keyword = "";
  let expiredPendingOnly = false;
  let currentPage = 0;
  let loadSequence = 0;

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

  function dateTime(value) {
    if (!value) return "—";
    return new Intl.DateTimeFormat("ko-KR", {
      month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit",
    }).format(new Date(value));
  }

  /* 상태마다 의미 있는 시각이 다르다. updated_at만 쓰면 무엇이 언제 바뀐 것인지 알 수 없다. */
  function changedAt(reservation) {
    if (reservation.status === "CANCELLED" && reservation.cancelledAt) return reservation.cancelledAt;
    if (reservation.status === "CONFIRMED" && reservation.confirmedAt) return reservation.confirmedAt;
    return reservation.updatedAt || reservation.createdAt;
  }

  function summary(reservation) {
    const name = reservation.productName || "상품 정보 없음";
    const extra = reservation.itemCount > 1 ? ` 외 ${reservation.itemCount - 1}건` : "";
    return `${name}${extra}`;
  }

  function money(value, currency) {
    const amount = Number(value);
    if (!Number.isFinite(amount)) return "금액 확인 불가";
    if (!currency || currency === "KRW") return `${amount.toLocaleString("ko-KR")}원`;
    return `${amount.toLocaleString("ko-KR")} ${currency}`;
  }

  function changedLabel(reservation) {
    if (reservation.status === "CANCELLED") return "취소 시각";
    if (reservation.status === "CONFIRMED") return "확정 시각";
    if (reservation.status === "USED") return "사용 처리 시각";
    return "최근 변경";
  }

  function row(reservation) {
    const item = document.createElement("div");
    item.className = "admin-monitor-row";
    item.dataset.reservationRow = String(reservation.reservationId);

    const infoCell = document.createElement("span");
    const number = document.createElement("strong");
    number.textContent = reservation.reservationNumber;
    const detail = document.createElement("small");
    detail.textContent = `${summary(reservation)} · ${reservation.nickname || "탈퇴 회원"}`;
    infoCell.append(number, detail);

    const paymentCell = document.createElement("span");
    const amount = document.createElement("strong");
    amount.dataset.reservationAmount = "";
    amount.textContent = money(reservation.totalAmount, reservation.currency);
    const trip = document.createElement("small");
    trip.dataset.reservationTrip = "";
    trip.textContent = reservation.tripId ? "여행 일정 연결됨" : "여행 미연결";
    paymentCell.append(amount, trip);

    const statusCell = document.createElement("span");
    const badge = document.createElement("span");
    badge.className = `admin-status ${String(reservation.status || "").toLowerCase()}`;
    badge.textContent = statusLabels[reservation.status] || reservation.status || "-";
    statusCell.appendChild(badge);
    if (reservation.expiredPending) {
      item.classList.add("needs-attention");
      const stale = document.createElement("small");
      stale.dataset.expiredPending = "";
      stale.textContent = `결제 기한 ${dateTime(reservation.expiresAt)} 지남`;
      statusCell.appendChild(stale);
    } else if (reservation.status === "PENDING" && reservation.expiresAt) {
      const deadline = document.createElement("small");
      deadline.dataset.paymentDeadline = "";
      deadline.textContent = `결제 기한 ${dateTime(reservation.expiresAt)}`;
      statusCell.appendChild(deadline);
    }

    const timeCell = document.createElement("span");
    timeCell.dataset.changedAt = "";
    const time = document.createElement("strong");
    time.textContent = dateTime(changedAt(reservation));
    const timeLabel = document.createElement("small");
    timeLabel.textContent = changedLabel(reservation);
    timeCell.append(time, timeLabel);

    item.append(infoCell, paymentCell, statusCell, timeCell);
    return item;
  }

  function renderPages(page, totalPages) {
    if (!pagination) return;
    pagination.replaceChildren();
    for (let index = 0; index < totalPages; index += 1) {
      if (totalPages > 7 && Math.abs(index - page) > 3 && index !== 0 && index !== totalPages - 1) continue;
      const button = document.createElement("button");
      button.type = "button";
      button.textContent = String(index + 1);
      button.className = index === page ? "is-current" : "";
      if (index === page) button.setAttribute("aria-current", "page");
      button.addEventListener("click", function () { load(index); });
      pagination.appendChild(button);
    }
  }

  /* 방치된 건수는 지금 보고 있는 필터와 무관하게 전체 기준으로 계속 알린다. */
  function renderAlert(count) {
    if (!alertBox || !alertText) return;
    if (!count) {
      alertBox.hidden = true;
      alertText.textContent = "";
      return;
    }
    alertBox.hidden = false;
    alertText.textContent = `확인이 필요한 만료 방치 예약이 ${count.toLocaleString("ko-KR")}건 있습니다.`;
  }

  function selectStatus(value) {
    expiredPendingOnly = value === "EXPIRED_PENDING";
    status = expiredPendingOnly ? "" : value;
    if (filter) {
      filter.querySelectorAll("[data-reservation-status]").forEach(function (chip) {
        const selected = chip.dataset.reservationStatus === value;
        chip.classList.toggle("on", selected);
        chip.setAttribute("aria-pressed", selected ? "true" : "false");
      });
    }
    load(0);
  }

  async function load(page) {
    const sequence = ++loadSequence;
    currentPage = Number(page) || 0;
    list.replaceChildren();
    empty.hidden = false;
    empty.textContent = "예약 목록을 불러오는 중이에요.";
    if (count) count.textContent = "조회 중…";
    const query = new URLSearchParams({ page: String(currentPage), size: String(PAGE_SIZE) });
    if (status) query.set("status", status);
    if (keyword) query.set("keyword", keyword);
    if (expiredPendingOnly) query.set("expiredPendingOnly", "true");
    try {
      const data = await request(`/api/v1/admin/reservations?${query}`);
      if (sequence !== loadSequence) return;
      renderAlert(data?.expiredPendingTotal);
      const items = data?.items || [];
      if (count) count.textContent = `조회 결과 ${Number(data?.total || 0).toLocaleString("ko-KR")}건`;
      if (!items.length) {
        empty.textContent = status || keyword || expiredPendingOnly
          ? "조건에 맞는 예약이 없어요."
          : "접수된 예약이 없어요.";
        renderPages(0, 0);
        return;
      }
      empty.hidden = true;
      items.forEach(function (reservation) { list.appendChild(row(reservation)); });
      renderPages(data.page, data.totalPages);
    } catch (error) {
      if (sequence !== loadSequence) return;
      renderAlert(0);
      if (count) count.textContent = "";
      empty.textContent = error.message || "예약 목록을 불러오지 못했어요.";
      renderPages(0, 0);
    }
  }

  if (filter) {
    filter.addEventListener("click", function (event) {
      const button = event.target.closest("[data-reservation-status]");
      if (!button) return;
      selectStatus(button.dataset.reservationStatus);
    });
    filter.querySelectorAll("[data-reservation-status]").forEach(function (chip) {
      chip.setAttribute("aria-pressed", chip.dataset.reservationStatus === "" ? "true" : "false");
    });
  }

  if (searchForm && search) {
    searchForm.addEventListener("submit", function (event) {
      event.preventDefault();
      keyword = search.value.trim();
      if (searchClear) searchClear.hidden = !keyword;
      load(0);
    });
    search.addEventListener("input", function () {
      if (searchClear) searchClear.hidden = !search.value.trim() && !keyword;
    });
  }

  if (searchClear && search) searchClear.addEventListener("click", function () {
    search.value = "";
    keyword = "";
    searchClear.hidden = true;
    search.focus();
    load(0);
  });

  if (refresh) refresh.addEventListener("click", function () { load(currentPage); });
  if (alertOpen) alertOpen.addEventListener("click", function () { selectStatus("EXPIRED_PENDING"); });

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", function () { load(0); });
  } else {
    load(0);
  }

  window.__adminReservations = { load: load };
})();
