/* 관리자 대시보드
 *
 * 지금 실제로 서버와 이야기하는 것은 신고 목록 하나뿐이다(GET /api/v1/travel-record-reports).
 * 나머지 블록은 화면만 잡아두고 "연동 전"으로 표시한다.
 *
 * 값을 그럴듯한 숫자로 채워두지 않는 이유는, 이전 화면이 그렇게 되어 있어서
 * 연동이 빠졌다는 사실을 아무도 눈치채지 못했기 때문이다. 비어 있는 편이 정직하다.
 */
(function () {
  "use strict";

  const $ = (id) => document.getElementById(id);
  const esc = (value) => String(value ?? "").replace(/[&<>"']/g,
    (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char]));

  const REPORT_STATUS_LABEL = {
    PENDING: "접수",
    REVIEWING: "검토 중",
    RESOLVED: "처리 완료",
    REJECTED: "반려"
  };

  const REPORT_REASON_LABEL = {
    SPAM: "스팸",
    ABUSE: "욕설·비방",
    INAPPROPRIATE: "부적절",
    COPYRIGHT: "저작권",
    PRIVACY: "개인정보",
    OTHER: "기타"
  };

  const state = {
    reportStatus: "",
    reports: [],
    loading: false,
    /* 상담 채팅은 화면만 있고 연동 전이다. 선택한 필터만 기억한다. */
    chatFilter: "",
    /* 사이드바에서 고른 화면. 실연동은 신고 관리뿐이라 그것부터 연다. */
    panel: "reports"
  };

  const date = (value) => {
    if (!value) return "-";
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime())
      ? "-"
      : parsed.toLocaleDateString("ko-KR", { year: "2-digit", month: "2-digit", day: "2-digit" });
  };

  /** 권한이 없어 비어 있는 것인지, 원래 없는 것인지 화면이 구분해서 알려준다. */
  function notice(message, isError) {
    const el = $("adminAuthNotice");
    el.textContent = message || "";
    el.classList.toggle("error", Boolean(isError));
    el.hidden = !message;
  }

  function reportRow(report) {
    const status = String(report.status || "");
    const label = REPORT_STATUS_LABEL[status] || status || "-";
    const reason = REPORT_REASON_LABEL[report.reason] || report.reason || "-";

    return `<div class="admin-report-row">
      <span>#${esc(report.travelRecordId)}</span>
      <span>${esc(reason)}</span>
      <span class="admin-clamp" title="${esc(report.detail || "")}">${esc(report.detail || "상세 없음")}</span>
      <span class="admin-status ${esc(status.toLowerCase())}">${esc(label)}</span>
      <span>${esc(date(report.createdAt))}</span>
    </div>`;
  }

  function renderReports() {
    const list = $("reportList");
    const empty = $("reportEmpty");

    if (state.loading) {
      list.innerHTML = "";
      empty.hidden = false;
      empty.textContent = "신고 목록을 불러오는 중이에요.";
      return;
    }

    list.innerHTML = state.reports.map(reportRow).join("");
    empty.hidden = state.reports.length > 0;
    if (!state.reports.length) {
      empty.textContent = state.reportStatus
        ? "이 상태의 신고가 없어요."
        : "접수된 신고가 없어요.";
    }
  }

  async function loadReports() {
    state.loading = true;
    renderReports();

    const query = state.reportStatus ? `?status=${encodeURIComponent(state.reportStatus)}` : "";
    let response;
    try {
      response = await fetch(`/api/v1/travel-record-reports${query}`, {
        headers: { Accept: "application/json" },
        credentials: "same-origin"
      });
    } catch (error) {
      state.loading = false;
      state.reports = [];
      renderReports();
      $("reportEmpty").textContent = "신고 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.";
      return;
    }

    state.loading = false;

    /*
     * 관리자 계정을 지정하는 경로가 아직 없어(#163) 승격 전에는 여기서 403이 온다.
     * 화면이 그냥 비면 고장으로 읽히므로 사유를 밝힌다.
     */
    if (response.status === 401) {
      state.reports = [];
      renderReports();
      notice("로그인이 필요한 화면이에요.", true);
      $("reportEmpty").textContent = "로그인 후 다시 확인해 주세요.";
      return;
    }
    if (response.status === 403) {
      state.reports = [];
      renderReports();
      notice("이 계정은 아직 관리자 권한이 없어요. 계정을 ADMIN으로 올린 뒤 다시 로그인하면 신고 목록이 보여요.", false);
      $("reportEmpty").textContent = "관리자 권한이 필요해요.";
      return;
    }

    const payload = await response.json().catch(() => null);
    if (!response.ok || !payload?.success) {
      state.reports = [];
      renderReports();
      /*
       * message를 그대로 쓰지 않는다. 우리 ApiResponse가 아닌 응답(404 기본 오류 등)에는
       * "No static resource ..." 같은 내부 문구가 들어 있어 사용자에게 보일 말이 아니다.
       */
      const ours = payload && typeof payload.success === "boolean" && payload.message;
      $("reportEmpty").textContent = ours || "신고 목록을 불러오지 못했어요.";
      return;
    }

    notice("");
    state.reports = Array.isArray(payload.data) ? payload.data : [];
    renderReports();
  }

  /**
   * 사이드바에서 고른 화면만 보여준다.
   *
   * <p>연동 전 항목도 막지 않는다. 막아버리면 앞으로 무엇이 붙는지 볼 수 없고,
   * "연동 안 된 것을 숨기지 않는다"는 이 화면의 원칙과도 어긋난다.
   * 대신 각 화면이 `연동 전` 배지와 빈 값으로 상태를 스스로 밝힌다.
   */
  function openPanel(name) {
    const sections = document.querySelectorAll("[data-admin-section]");
    const target = [...sections].some((section) => section.dataset.adminSection === name)
      ? name
      : "reports";

    sections.forEach((section) => {
      section.hidden = section.dataset.adminSection !== target;
    });

    document.querySelectorAll("[data-admin-panel]").forEach((button) => {
      const on = button.dataset.adminPanel === target;
      button.classList.toggle("is-current", on);
      /* aria-current는 참일 때만 둔다. "false" 문자열은 읽어주는 도구가 그대로 읽는다. */
      if (on) button.setAttribute("aria-current", "page");
      else button.removeAttribute("aria-current");
    });

    state.panel = target;
  }

  function bind() {
    document.querySelectorAll("[data-admin-panel]").forEach((button) => {
      button.addEventListener("click", () => openPanel(button.dataset.adminPanel));
    });

    document.querySelectorAll("[data-report-status]").forEach((button) => {
      button.addEventListener("click", () => {
        document.querySelectorAll("[data-report-status]").forEach((item) => item.classList.remove("on"));
        button.classList.add("on");
        state.reportStatus = button.dataset.reportStatus;
        loadReports();
      });
    });

    /* 저장 API가 없어 입력과 버튼을 막아뒀지만, 엔터 제출까지는 막지 못한다. */
    $("themeForm").addEventListener("submit", (event) => event.preventDefault());
    $("chatComposer").addEventListener("submit", (event) => event.preventDefault());

    /*
     * 상담 상태 필터는 눌린 것만 표시하고 조회는 하지 않는다.
     * WebSocket과 방 목록 API가 붙으면 여기서 room 조회를 건다.
     */
    document.querySelectorAll("[data-chat-filter]").forEach((button) => {
      button.addEventListener("click", () => {
        document.querySelectorAll("[data-chat-filter]").forEach((item) => item.classList.remove("on"));
        button.classList.add("on");
        state.chatFilter = button.dataset.chatFilter;
      });
    });
  }

  function init() {
    bind();
    openPanel(state.panel);
    loadReports();
    document.body.dataset.pageReady = "true";
  }

  document.addEventListener("DOMContentLoaded", init);

  window.__adminDashboard = { state, loadReports, openPanel };
})();
