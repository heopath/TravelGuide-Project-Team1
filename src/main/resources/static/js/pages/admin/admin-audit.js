/* 관리자 조작 이력
 *
 * 읽기 전용이다. 이 화면에는 수정·삭제가 없다.
 * 필터 선택지는 서버가 실제로 쌓인 action_type을 내려준다. 화면에 목록을 박아두면
 * 새 동작이 추가될 때마다 여기도 같이 고쳐야 하고, 빠뜨리면 조용히 안 보인다.
 */
(function () {
  "use strict";

  const PAGE_SIZE = 30;

  /* 사람이 읽을 이름. 서버가 모르는 동작을 내려주면 원문을 그대로 쓴다. */
  const actionLabels = {
    PLACE_CREATE: "장소 등록",
    PLACE_UPDATE: "장소 수정",
    PLACE_VISIBILITY_CHANGE: "장소 공개 전환",
    TICKET_PRODUCT_CREATE: "상품 등록",
    TICKET_PRODUCT_UPDATE: "상품 수정",
    TICKET_PRODUCT_STATUS_CHANGE: "판매 상태 변경",
    TICKET_INVENTORY_CHANGE: "재고 조정",
    SUPPORT_REPLY: "문의 답변",
    SUPPORT_STATUS_CHANGE: "문의 상태 변경",
    REPORT_PROCESS: "신고 처리",
  };
  const targetLabels = {
    PLACE: "추천 장소",
    TICKET_PRODUCT: "예약 상품",
    TICKET_TIME_SLOT: "시간대",
    SUPPORT_INQUIRY: "1:1 문의",
    TRAVEL_RECORD_REPORT: "신고",
  };

  const panel = document.querySelector('[data-admin-section="audit"]');
  if (!panel) return;

  const list = panel.querySelector("[data-audit-list]");
  const empty = panel.querySelector("[data-audit-empty]");
  const filter = panel.querySelector("[data-audit-filter]");
  const search = panel.querySelector("[data-audit-search]");
  const pagination = panel.querySelector("[data-audit-pagination]");
  const notice = panel.querySelector("[data-audit-notice]");
  if (!list || !empty) return;

  let actionType = "";
  let targetId = "";
  let filterBuilt = false;

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
      month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit",
    }).format(new Date(value));
  }

  function parse(json) {
    if (!json) return null;
    try {
      return JSON.parse(json);
    } catch (error) {
      return null;
    }
  }

  /*
   * 전후 값을 `키: 이전 → 이후`로 합친다. 한쪽에만 있는 키도 빠뜨리지 않는다.
   * 등록은 before가 없고 삭제성 동작은 after가 없다.
   */
  function changeLines(entry) {
    const before = parse(entry.beforeData) || {};
    const after = parse(entry.afterData) || {};
    const keys = [...new Set([...Object.keys(before), ...Object.keys(after)])];
    return keys.map(function (key) {
      const from = before[key];
      const to = after[key];
      if (from === undefined) return `${key}: ${to}`;
      if (to === undefined) return `${key}: ${from} →`;
      if (String(from) === String(to)) return `${key}: ${to}`;
      return `${key}: ${from} → ${to}`;
    });
  }

  function row(entry) {
    const item = document.createElement("div");
    item.className = "admin-audit-row";
    item.dataset.auditRow = String(entry.adminAuditLogId);

    const timeCell = document.createElement("span");
    timeCell.dataset.auditTime = "";
    timeCell.textContent = dateTime(entry.occurredAt);

    const adminCell = document.createElement("span");
    adminCell.dataset.auditAdmin = "";
    /* 계정이 지워지면 admin_user_id가 비므로 닉네임도 없다. 그래도 기록은 남아야 한다. */
    adminCell.textContent = entry.adminNickname || (entry.adminUserId ? `#${entry.adminUserId}` : "알 수 없음");

    const actionCell = document.createElement("span");
    const action = document.createElement("strong");
    action.textContent = actionLabels[entry.actionType] || entry.actionType;
    const target = document.createElement("small");
    const targetName = targetLabels[entry.targetType] || entry.targetType;
    target.textContent = entry.targetId ? `${targetName} ${entry.targetId}` : targetName;
    actionCell.append(action, target);

    const changeCell = document.createElement("span");
    changeCell.dataset.auditChange = "";
    const lines = changeLines(entry);
    if (!lines.length) {
      changeCell.textContent = "기록된 값 없음";
      changeCell.dataset.auditEmptyPayload = "";
    } else {
      lines.forEach(function (line) {
        const row = document.createElement("small");
        row.textContent = line;
        changeCell.appendChild(row);
      });
    }

    item.append(timeCell, adminCell, actionCell, changeCell);
    return item;
  }

  /* 동작 종류는 서버가 준 것만 그린다. 전체 버튼은 마크업에 이미 있다. */
  function buildFilter(actionTypes) {
    if (filterBuilt || !filter || !actionTypes) return;
    actionTypes.forEach(function (value) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "admin-chip";
      button.dataset.auditAction = value;
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
      button.addEventListener("click", function () { load(index); });
      pagination.appendChild(button);
    }
  }

  /*
   * 접속 IP가 전부 로컬호스트로 남는 동안은 그 사실을 화면이 밝힌다(#218).
   * 값을 그냥 보여주면 관리자가 실제로 서버에서 접속한 것으로 읽는다.
   */
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
    list.replaceChildren();
    empty.hidden = false;
    empty.textContent = "조작 이력을 불러오는 중이에요.";
    const query = new URLSearchParams({ page: String(page || 0), size: String(PAGE_SIZE) });
    if (actionType) query.set("actionType", actionType);
    if (targetId) query.set("targetId", targetId);
    try {
      const data = await request(`/api/v1/admin/audit-logs?${query}`);
      buildFilter(data?.actionTypes);
      const items = data?.items || [];
      renderNotice(items);
      if (!items.length) {
        empty.textContent = actionType || targetId
          ? "조건에 맞는 이력이 없어요."
          : "아직 기록된 조작 이력이 없어요.";
        renderPages(0, 0);
        return;
      }
      empty.hidden = true;
      items.forEach(function (entry) { list.appendChild(row(entry)); });
      renderPages(data.page, data.totalPages);
    } catch (error) {
      renderNotice([]);
      empty.textContent = error.message || "조작 이력을 불러오지 못했어요.";
      renderPages(0, 0);
    }
  }

  if (filter) {
    filter.addEventListener("click", function (event) {
      const button = event.target.closest("[data-audit-action]");
      if (!button) return;
      actionType = button.dataset.auditAction || "";
      filter.querySelectorAll("[data-audit-action]").forEach(function (chip) {
        chip.classList.toggle("on", chip === button);
      });
      load(0);
    });
  }

  if (search) {
    search.addEventListener("keydown", function (event) {
      if (event.key !== "Enter") return;
      event.preventDefault();
      targetId = search.value.trim();
      load(0);
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", function () { load(0); });
  } else {
    load(0);
  }

  window.__adminAudit = { load: load };
})();
