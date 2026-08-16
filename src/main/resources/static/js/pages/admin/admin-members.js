/* 관리자 회원 관리
 *
 * 정지와 관리자 승격은 이 서비스에서 가장 되돌리기 어려운 동작이다. 두 자리에서 버튼을 잠근다.
 *
 *   1. 자기 자신 — 스스로를 정지하거나 권한을 내리면 그 즉시 이 화면에 못 들어간다.
 *   2. 마지막 관리자 — 활동 중인 관리자가 하나뿐이면 정지도 강등도 막는다.
 *
 * 서버가 같은 검사를 한 번 더 한다. 여기서 잠그는 것은 방어가 아니라, 눌러 보고 나서
 * 거부당하는 일을 줄이려는 것이다. 잠근 버튼에는 왜 잠겼는지 title로 이유를 붙인다.
 */
(function () {
  "use strict";

  const PAGE_SIZE = 20;

  const statusLabels = { ACTIVE: "활동", SUSPENDED: "정지", WITHDRAWN: "탈퇴" };
  const roleLabels = { ADMIN: "관리자", USER: "일반" };

  const panel = document.querySelector('[data-admin-section="members"]');
  if (!panel) return;

  const list = panel.querySelector("[data-member-list]");
  const empty = panel.querySelector("[data-member-empty]");
  const statusFilter = panel.querySelector("[data-member-filter]");
  const roleFilter = panel.querySelector("[data-member-role-filter]");
  const search = panel.querySelector("[data-member-search]");
  const pagination = panel.querySelector("[data-member-pagination]");
  const notice = panel.querySelector("[data-member-notice]");
  if (!list || !empty) return;

  let status = "";
  let role = "";
  let keyword = "";
  let currentPage = 0;
  /* 서버가 목록과 함께 내려준다. 버튼을 잠글지 여기서 판단한다. */
  let activeAdminCount = 0;
  let currentAdminUserId = null;

  async function request(url, options) {
    const response = await fetch(url, Object.assign({
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      allMyTripsLoading: false,
    }, options || {}));
    const payload = await response.json().catch(function () { return null; });
    if (!response.ok || payload?.success === false) {
      if (response.status === 401) window.location.href = "/auth/login?redirect=/admin";
      if (response.status === 403) throw new Error("관리자만 접근할 수 있습니다.");
      const error = new Error(payload?.message || "요청을 처리하지 못했습니다.");
      error.code = payload?.code;
      throw error;
    }
    return payload?.data ?? payload;
  }

  function dateTime(value) {
    if (!value) return "—";
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return "—";
    return new Intl.DateTimeFormat("ko-KR", {
      year: "2-digit", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit",
    }).format(parsed);
  }

  /*
   * 이 회원에게 잠글 이유가 있으면 그 이유를 돌려준다. 없으면 빈 문자열이다.
   * 정지·강등 양쪽이 같은 이유를 쓰므로 한 곳에서 판단한다.
   */
  function lockReason(member, action) {
    if (currentAdminUserId != null && member.userId === currentAdminUserId) {
      return "자기 자신의 권한과 상태는 바꿀 수 없어요.";
    }
    if (member.status === "WITHDRAWN") {
      return "탈퇴한 회원은 바꿀 수 없어요.";
    }
    const losesAdmin = member.role === "ADMIN"
      && (action === "demote" || (action === "suspend" && member.status === "ACTIVE"));
    if (losesAdmin && activeAdminCount <= 1) {
      return "마지막 관리자예요. 다른 관리자를 먼저 지정해 주세요.";
    }
    if (action === "promote" && member.status !== "ACTIVE") {
      return "정지된 회원은 관리자로 올릴 수 없어요.";
    }
    return "";
  }

  function actionButton(member, action, label) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "admin-chip";
    button.textContent = label;
    button.dataset.memberAction = action;
    button.dataset.memberId = String(member.userId);

    const reason = lockReason(member, action);
    if (reason) {
      button.disabled = true;
      button.title = reason;
    }
    return button;
  }

  function row(member) {
    const item = document.createElement("div");
    item.className = "admin-member-row";
    item.dataset.memberRow = String(member.userId);

    const who = document.createElement("span");
    const nickname = document.createElement("strong");
    nickname.textContent = member.nickname || `#${member.userId}`;
    const email = document.createElement("small");
    email.textContent = member.email || "";
    who.append(nickname, email);

    const roleCell = document.createElement("span");
    roleCell.dataset.memberRoleCell = "";
    roleCell.textContent = roleLabels[member.role] || member.role || "—";

    const statusCell = document.createElement("span");
    statusCell.dataset.memberStatusCell = "";
    statusCell.className = `admin-status ${String(member.status || "").toLowerCase()}`;
    statusCell.textContent = statusLabels[member.status] || member.status || "—";

    const loginCell = document.createElement("span");
    loginCell.textContent = dateTime(member.lastLoginAt);

    const actions = document.createElement("span");
    actions.className = "admin-member-actions";
    if (member.status === "SUSPENDED") {
      actions.appendChild(actionButton(member, "activate", "정지 해제"));
    } else {
      actions.appendChild(actionButton(member, "suspend", "정지"));
    }
    if (member.role === "ADMIN") {
      actions.appendChild(actionButton(member, "demote", "관리자 해제"));
    } else {
      actions.appendChild(actionButton(member, "promote", "관리자 승격"));
    }

    item.append(who, roleCell, statusCell, loginCell, actions);
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
      button.addEventListener("click", function () { load(index); });
      pagination.appendChild(button);
    }
  }

  /*
   * 관리자가 한 명뿐이면 그 사실을 미리 알린다. 버튼이 왜 잠겼는지 title에도 있지만,
   * 잠긴 버튼에는 마우스를 올려보지 않는 사람이 많다.
   */
  function renderNotice() {
    if (!notice) return;
    const alone = activeAdminCount === 1;
    notice.hidden = !alone;
    notice.textContent = alone
      ? "활동 중인 관리자가 한 명뿐이에요. 그 계정은 정지와 권한 해제가 잠겨 있어요."
      : "";
  }

  async function load(page) {
    currentPage = page || 0;
    list.replaceChildren();
    empty.hidden = false;
    empty.textContent = "회원 목록을 불러오는 중이에요.";

    const query = new URLSearchParams({ page: String(currentPage), size: String(PAGE_SIZE) });
    if (status) query.set("status", status);
    if (role) query.set("role", role);
    if (keyword) query.set("keyword", keyword);

    try {
      const data = await request(`/api/v1/admin/members?${query}`);
      activeAdminCount = Number(data?.activeAdminCount ?? 0);
      currentAdminUserId = data?.currentAdminUserId ?? null;
      renderNotice();

      const items = data?.items || [];
      if (!items.length) {
        empty.textContent = status || role || keyword
          ? "조건에 맞는 회원이 없어요."
          : "표시할 회원이 없어요.";
        renderPages(0, 0);
        return;
      }
      empty.hidden = true;
      items.forEach(function (member) { list.appendChild(row(member)); });
      renderPages(data.page, data.totalPages);
    } catch (error) {
      activeAdminCount = 0;
      renderNotice();
      empty.textContent = error.message || "회원 목록을 불러오지 못했어요.";
      renderPages(0, 0);
    }
  }

  const confirmText = {
    suspend: "이 회원을 정지할까요? 정지된 회원은 로그인할 수 없어요.",
    activate: "이 회원의 정지를 해제할까요?",
    promote: "이 회원을 관리자로 올릴까요? 관리자는 회원 정지와 권한 변경을 할 수 있어요.",
    demote: "이 회원의 관리자 권한을 해제할까요?",
  };

  async function act(button) {
    const action = button.dataset.memberAction;
    const userId = button.dataset.memberId;
    if (!action || !userId) return;
    if (!window.confirm(confirmText[action])) return;

    const url = action === "promote" || action === "demote"
      ? `/api/v1/admin/members/${userId}/role`
      : `/api/v1/admin/members/${userId}/status`;
    const body = action === "promote" ? { role: "ADMIN" }
      : action === "demote" ? { role: "USER" }
      : action === "suspend" ? { status: "SUSPENDED" }
      : { status: "ACTIVE" };

    button.disabled = true;
    try {
      await request(url, { method: "PATCH", body: JSON.stringify(body) });
      /*
       * 바뀐 줄만 고치지 않고 목록을 다시 받는다. 관리자 수가 달라지면 다른 줄의 버튼도
       * 같이 잠기거나 풀려야 하는데, 한 줄만 고치면 나머지가 옛 상태로 남는다.
       */
      await load(currentPage);
    } catch (error) {
      button.disabled = false;
      empty.hidden = false;
      empty.textContent = error.message || "회원 정보를 바꾸지 못했어요.";
    }
  }

  list.addEventListener("click", function (event) {
    const button = event.target.closest("[data-member-action]");
    if (!button || button.disabled) return;
    act(button);
  });

  if (statusFilter) {
    statusFilter.addEventListener("click", function (event) {
      const button = event.target.closest("[data-member-status]");
      if (!button) return;
      status = button.dataset.memberStatus || "";
      statusFilter.querySelectorAll("[data-member-status]").forEach(function (chip) {
        chip.classList.toggle("on", chip === button);
      });
      load(0);
    });
  }

  if (roleFilter) {
    roleFilter.addEventListener("click", function (event) {
      const button = event.target.closest("[data-member-role]");
      if (!button) return;
      role = button.dataset.memberRole || "";
      roleFilter.querySelectorAll("[data-member-role]").forEach(function (chip) {
        chip.classList.toggle("on", chip === button);
      });
      load(0);
    });
  }

  if (search) {
    search.addEventListener("keydown", function (event) {
      if (event.key !== "Enter") return;
      event.preventDefault();
      keyword = search.value.trim();
      load(0);
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", function () { load(0); });
  } else {
    load(0);
  }

  window.__adminMembers = { load: load };
})();
