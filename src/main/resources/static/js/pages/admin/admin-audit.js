/* 관리자 조작 이력
 *
 * 기록은 읽기 전용이다. 목록에는 훑어보는 데 필요한 요약만 두고, 전후 값과 요청 정보는
 * 상세 창에서 확인한다. 필터 선택지는 서버가 실제로 쌓인 action_type을 내려준다.
 */
(function () {
  "use strict";

  const PAGE_SIZE = 30;
  const actionLabels = {
    PLACE_CREATE: "장소 등록",
    PLACE_UPDATE: "장소 수정",
    PLACE_VISIBILITY_CHANGE: "장소 공개 전환",
    PLACE_RECOMMENDATION_CHANGE: "추천 상태 변경",
    PLACE_RECOMMEND_BULK: "추천 장소 일괄 처리",
    PLACE_IMAGE_FILL: "장소 이미지 보완",
    TICKET_PRODUCT_CREATE: "상품 등록",
    TICKET_PRODUCT_UPDATE: "상품 수정",
    TICKET_PRODUCT_STATUS_CHANGE: "판매 상태 변경",
    TICKET_INVENTORY_CHANGE: "재고 조정",
    TICKET_OPTION_CREATE: "상품 옵션 등록",
    TICKET_OPTION_UPDATE: "상품 옵션 수정",
    TICKET_SLOT_CREATE: "시간대 등록",
    TICKET_SLOT_STATUS_CHANGE: "시간대 상태 변경",
    SUPPORT_REPLY: "문의 답변",
    SUPPORT_STATUS_CHANGE: "문의 상태 변경",
    REPORT_PROCESS: "신고 처리",
    MEMBER_STATUS_CHANGE: "회원 상태 변경",
    MEMBER_ROLE_CHANGE: "회원 권한 변경",
  };
  const targetLabels = {
    PLACE: "추천 장소",
    TICKET_PRODUCT: "예약 상품",
    TICKET_PRODUCT_OPTION: "상품 옵션",
    TICKET_TIME_SLOT: "시간대",
    SUPPORT_INQUIRY: "1:1 문의",
    TRAVEL_RECORD_REPORT: "신고",
    USER: "회원",
  };
  const fieldLabels = {
    status: "상태",
    role: "권한",
    reason: "변경 사유",
    name: "이름",
    title: "제목",
    visible: "공개 여부",
    recommended: "추천 여부",
    quantity: "수량",
    totalQuantity: "판매 수량",
    reservedQuantity: "예약 수량",
    remainingQuantity: "남은 수량",
    price: "가격",
    startTime: "시작 시각",
    endTime: "종료 시각",
    operatingDate: "이용일",
  };

  const panel = document.querySelector('[data-admin-section="audit"]');
  if (!panel) return;

  const list = panel.querySelector("[data-audit-list]");
  const empty = panel.querySelector("[data-audit-empty]");
  const filter = panel.querySelector("[data-audit-filter]");
  const searchForm = panel.querySelector("[data-audit-search-form]");
  const search = panel.querySelector("[data-audit-search]");
  const searchClear = panel.querySelector("[data-audit-search-clear]");
  const refresh = panel.querySelector("[data-audit-refresh]");
  const count = panel.querySelector("[data-audit-count]");
  const pagination = panel.querySelector("[data-audit-pagination]");
  const notice = panel.querySelector("[data-audit-notice]");
  const modal = panel.querySelector("[data-audit-modal]");
  const modalCard = panel.querySelector("[data-audit-modal-card]");
  const modalClose = panel.querySelector("[data-audit-modal-close]");
  const modalCancel = panel.querySelector("[data-audit-modal-cancel]");
  const detailSummary = panel.querySelector("[data-audit-detail-summary]");
  const detailMeta = panel.querySelector("[data-audit-detail-meta]");
  const detailChanges = panel.querySelector("[data-audit-detail-changes]");
  if (!list || !empty) return;

  let actionType = "";
  let targetId = "";
  let currentPage = 0;
  let filterBuilt = false;
  let loadSequence = 0;
  let returnFocus = null;

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

  function dateTime(value, includeYear) {
    if (!value) return "—";
    const options = includeYear
      ? { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit" }
      : { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" };
    return new Intl.DateTimeFormat("ko-KR", options).format(new Date(value));
  }

  function parse(json) {
    if (!json) return null;
    try {
      const value = JSON.parse(json);
      return value && typeof value === "object" && !Array.isArray(value) ? value : null;
    } catch (error) {
      return null;
    }
  }

  function displayValue(value) {
    if (value === undefined || value === null || value === "") return "—";
    if (typeof value === "boolean") return value ? "예" : "아니요";
    if (typeof value === "object") return JSON.stringify(value);
    return String(value);
  }

  function changeEntries(entry) {
    const before = parse(entry.beforeData) || {};
    const after = parse(entry.afterData) || {};
    const keys = [...new Set([...Object.keys(before), ...Object.keys(after)])];
    return keys.map(function (key) {
      return {
        key: key,
        label: fieldLabels[key] || key,
        before: before[key],
        after: after[key],
      };
    });
  }

  function changeLine(change) {
    const from = change.before;
    const to = change.after;
    if (from === undefined) return `${change.label}: ${displayValue(to)}`;
    if (to === undefined) return `${change.label}: ${displayValue(from)} → 삭제`;
    if (JSON.stringify(from) === JSON.stringify(to)) return `${change.label}: ${displayValue(to)}`;
    return `${change.label}: ${displayValue(from)} → ${displayValue(to)}`;
  }

  function targetText(entry) {
    const targetName = targetLabels[entry.targetType] || entry.targetType || "대상";
    return entry.targetId ? `${targetName} ${entry.targetId}` : targetName;
  }

  function row(entry) {
    const item = document.createElement("div");
    item.className = "admin-audit-row";
    item.dataset.auditRow = String(entry.adminAuditLogId);

    const timeCell = document.createElement("span");
    timeCell.dataset.auditTime = "";
    timeCell.textContent = dateTime(entry.occurredAt, false);

    const adminCell = document.createElement("span");
    adminCell.dataset.auditAdmin = "";
    adminCell.textContent = entry.adminNickname || (entry.adminUserId ? `#${entry.adminUserId}` : "알 수 없음");

    const actionCell = document.createElement("span");
    const action = document.createElement("strong");
    action.textContent = actionLabels[entry.actionType] || entry.actionType;
    const target = document.createElement("small");
    target.textContent = targetText(entry);
    actionCell.append(action, target);

    const changeCell = document.createElement("span");
    changeCell.dataset.auditChange = "";
    const changes = changeEntries(entry);
    if (!changes.length) {
      changeCell.textContent = "기록된 값 없음";
      changeCell.dataset.auditEmptyPayload = "";
    } else {
      const first = document.createElement("small");
      first.textContent = changeLine(changes[0]);
      changeCell.appendChild(first);
      if (changes.length > 1) {
        const more = document.createElement("small");
        more.className = "admin-audit-more";
        more.textContent = `외 ${changes.length - 1}개 변경`;
        changeCell.appendChild(more);
      }
    }

    const actionButtonCell = document.createElement("span");
    const detailButton = document.createElement("button");
    detailButton.type = "button";
    detailButton.className = "admin-chip";
    detailButton.dataset.auditDetail = String(entry.adminAuditLogId);
    detailButton.textContent = "상세 보기";
    detailButton.addEventListener("click", function () { openDetail(entry, detailButton); });
    actionButtonCell.appendChild(detailButton);

    item.append(timeCell, adminCell, actionCell, changeCell, actionButtonCell);
    return item;
  }

  function addMeta(label, value) {
    if (!detailMeta || !value) return;
    const term = document.createElement("dt");
    term.textContent = label;
    const description = document.createElement("dd");
    description.textContent = value;
    detailMeta.append(term, description);
  }

  function renderDetailChanges(entry) {
    if (!detailChanges) return;
    detailChanges.replaceChildren();
    const changes = changeEntries(entry);
    if (!changes.length) {
      const message = document.createElement("p");
      message.className = "admin-empty";
      message.textContent = "이 기록에는 저장된 변경 값이 없습니다.";
      detailChanges.appendChild(message);
      return;
    }

    const head = document.createElement("div");
    head.className = "admin-audit-change-row is-head";
    ["항목", "변경 전", "변경 후"].forEach(function (text) {
      const cell = document.createElement("span");
      cell.textContent = text;
      head.appendChild(cell);
    });
    detailChanges.appendChild(head);

    changes.forEach(function (change) {
      const changeRow = document.createElement("div");
      changeRow.className = "admin-audit-change-row";
      [change.label, displayValue(change.before), displayValue(change.after)].forEach(function (text) {
        const cell = document.createElement("span");
        cell.textContent = text;
        changeRow.appendChild(cell);
      });
      detailChanges.appendChild(changeRow);
    });
  }

  function openDetail(entry, trigger) {
    if (!modal || !modalCard) return;
    returnFocus = trigger || document.activeElement;
    if (detailSummary) {
      detailSummary.textContent = `${actionLabels[entry.actionType] || entry.actionType} · ${targetText(entry)}`;
    }
    if (detailMeta) {
      detailMeta.replaceChildren();
      addMeta("처리 시각", dateTime(entry.occurredAt, true));
      addMeta("처리 관리자", entry.adminNickname || (entry.adminUserId ? `사용자 #${entry.adminUserId}` : "알 수 없음"));
      addMeta("접속 IP", entry.ipAddress || "기록 없음");
      addMeta("요청 번호", entry.requestId || "기록 없음");
      addMeta("접속 환경", entry.userAgent || "기록 없음");
    }
    renderDetailChanges(entry);
    modal.hidden = false;
    document.body.dataset.auditModalOpen = "";
    modalCard.focus();
  }

  function closeDetail() {
    if (!modal || modal.hidden) return;
    modal.hidden = true;
    delete document.body.dataset.auditModalOpen;
    const target = returnFocus;
    returnFocus = null;
    target?.focus?.();
  }

  function buildFilter(actionTypes) {
    if (filterBuilt || !filter || !actionTypes) return;
    actionTypes.forEach(function (value) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "admin-chip";
      button.dataset.auditAction = value;
      button.setAttribute("aria-pressed", "false");
      button.textContent = actionLabels[value] || value;
      filter.appendChild(button);
    });
    filterBuilt = true;
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

  function renderNotice(items) {
    if (!notice) return;
    const localOnly = items.length > 0
      && items.every(function (entry) { return !entry.ipAddress || entry.ipAddress === "::1" || entry.ipAddress === "127.0.0.1"; });
    notice.hidden = !localOnly;
    notice.textContent = localOnly
      ? "접속 IP가 모두 로컬 주소로 기록돼 있어요. 프록시가 원래 주소를 넘겨주지 않는 상태예요."
      : "";
  }

  async function load(page) {
    const sequence = ++loadSequence;
    currentPage = Number.isInteger(page) ? page : 0;
    list.replaceChildren();
    empty.hidden = false;
    empty.textContent = "변경 이력을 불러오는 중이에요.";
    if (count) count.textContent = "조회 중…";
    const query = new URLSearchParams({ page: String(currentPage), size: String(PAGE_SIZE) });
    if (actionType) query.set("actionType", actionType);
    if (targetId) query.set("targetId", targetId);
    try {
      const data = await request(`/api/v1/admin/audit-logs?${query}`);
      if (sequence !== loadSequence) return;
      buildFilter(data?.actionTypes);
      const items = data?.items || [];
      const total = Number(data?.total ?? items.length);
      if (count) count.textContent = `조회 결과 ${total.toLocaleString("ko-KR")}건`;
      renderNotice(items);
      if (!items.length) {
        empty.textContent = actionType || targetId
          ? "조건에 맞는 이력이 없어요. 필터나 검색어를 바꿔보세요."
          : "아직 기록된 변경 이력이 없어요.";
        renderPages(0, 0);
        return;
      }
      empty.hidden = true;
      items.forEach(function (entry) { list.appendChild(row(entry)); });
      renderPages(data.page, data.totalPages);
    } catch (error) {
      if (sequence !== loadSequence) return;
      renderNotice([]);
      if (count) count.textContent = "조회하지 못함";
      empty.textContent = error.message || "변경 이력을 불러오지 못했어요.";
      renderPages(0, 0);
    }
  }

  if (filter) {
    filter.addEventListener("click", function (event) {
      const button = event.target.closest("[data-audit-action]");
      if (!button) return;
      actionType = button.dataset.auditAction || "";
      filter.querySelectorAll("[data-audit-action]").forEach(function (chip) {
        const selected = chip === button;
        chip.classList.toggle("on", selected);
        chip.setAttribute("aria-pressed", selected ? "true" : "false");
      });
      load(0);
    });
  }

  searchForm?.addEventListener("submit", function (event) {
    event.preventDefault();
    targetId = search?.value.trim() || "";
    if (searchClear) searchClear.hidden = !targetId;
    load(0);
  });

  searchClear?.addEventListener("click", function () {
    if (search) search.value = "";
    targetId = "";
    searchClear.hidden = true;
    load(0);
    search?.focus();
  });

  refresh?.addEventListener("click", function () { load(currentPage); });
  modalClose?.addEventListener("click", closeDetail);
  modalCancel?.addEventListener("click", closeDetail);
  modal?.addEventListener("click", function (event) {
    if (event.target === modal) closeDetail();
  });
  document.addEventListener("keydown", function (event) {
    if (!modal || modal.hidden) return;
    if (event.key === "Escape") {
      closeDetail();
      return;
    }
    if (event.key !== "Tab" || !modalCard) return;
    const focusable = Array.from(modalCard.querySelectorAll('button:not([disabled]), a[href]'));
    if (!focusable.length) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  });

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", function () { load(0); });
  } else {
    load(0);
  }

  window.__adminAudit = { load: load };
})();
