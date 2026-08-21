/* 관리자 운영 센터
 *
 * 운영 홈을 기본으로 열고, 업무별 메뉴와 숫자 카드를 누르면 해당 관리 화면으로 이동한다.
 * 화면을 새로고침해도 같은 업무를 이어갈 수 있도록 선택한 패널을 주소에 보존한다.
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
    selectedReport: null,
    loading: false,
    /* 관리자 진입 시 오늘 처리할 일을 먼저 보여준다. */
    panel: "overview"
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

    return `<div class="admin-report-row" data-report-row="${esc(report.travelRecordReportId)}">
      <span><strong>#${esc(report.travelRecordReportId)}</strong><small>기록 #${esc(report.travelRecordId)}</small></span>
      <span>${esc(reason)}</span>
      <span class="admin-clamp" title="${esc(report.detail || "")}">${esc(report.detail || "상세 없음")}</span>
      <span class="admin-status ${esc(status.toLowerCase())}">${esc(label)}</span>
      <span>${esc(date(report.createdAt))}</span>
      <span><button type="button" class="admin-chip" data-report-open="${esc(report.travelRecordReportId)}">${status === "RESOLVED" || status === "REJECTED" ? "결과 보기" : "검토하기"}</button></span>
    </div>`;
  }

  function renderReports() {
    const list = $("reportList");
    const empty = $("reportEmpty");

    if (state.loading) {
      list.innerHTML = "";
      empty.hidden = false;
      empty.textContent = "신고 목록을 불러오는 중이에요.";
      const count = document.querySelector("[data-report-count]");
      if (count) count.textContent = "조회 중…";
      return;
    }

    list.innerHTML = state.reports.map(reportRow).join("");
    const count = document.querySelector("[data-report-count]");
    if (count) count.textContent = `조회 결과 ${state.reports.length.toLocaleString("ko-KR")}건`;
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

  function reportFeedback(message, error) {
    const element = document.querySelector("[data-report-feedback]");
    if (!element) return;
    element.textContent = message || "";
    element.classList.toggle("error", Boolean(error));
    element.hidden = !message;
  }

  function addReportMeta(label, value) {
    const meta = document.querySelector("[data-report-meta]");
    if (!meta) return;
    const term = document.createElement("dt");
    term.textContent = label;
    const description = document.createElement("dd");
    description.textContent = value || "—";
    meta.append(term, description);
  }

  function openReport(reportId, trigger) {
    const report = state.reports.find((item) => String(item.travelRecordReportId) === String(reportId));
    const modal = document.querySelector("[data-report-modal]");
    if (!report || !modal) return;
    state.selectedReport = report;
    modal.dataset.returnFocus = trigger?.dataset.reportOpen || "";
    document.querySelector("[data-report-target]").textContent =
      `신고 #${report.travelRecordReportId} · 여행 기록 #${report.travelRecordId}`;
    const meta = document.querySelector("[data-report-meta]");
    meta.replaceChildren();
    addReportMeta("신고 사유", REPORT_REASON_LABEL[report.reason] || report.reason);
    addReportMeta("신고자", report.reporterUserId ? `회원 #${report.reporterUserId}` : "알 수 없음");
    addReportMeta("접수일", date(report.createdAt));
    addReportMeta("현재 상태", REPORT_STATUS_LABEL[report.status] || report.status);
    if (report.processedBy) addReportMeta("처리 관리자", `회원 #${report.processedBy}`);
    if (report.processedAt) addReportMeta("처리일", date(report.processedAt));
    document.querySelector("[data-report-detail]").textContent = report.detail || "작성된 상세 내용이 없습니다.";

    const finished = report.status === "RESOLVED" || report.status === "REJECTED";
    const resolution = document.querySelector("[data-report-resolution]");
    resolution.hidden = !finished;
    document.querySelector("[data-report-resolution-note]").textContent =
      report.resolutionNote || "처리 사유가 기록되지 않았습니다.";
    const form = document.querySelector("[data-report-action-form]");
    form.hidden = finished;
    if (!finished) {
      document.querySelector("[data-report-action-status]").value = report.status === "REVIEWING" ? "RESOLVED" : "REVIEWING";
      document.querySelector("[data-report-action-note]").value = "";
      document.querySelector("[data-report-action-error]").textContent = "";
    }
    modal.hidden = false;
    document.body.dataset.reportModalOpen = "";
    document.querySelector("[data-report-modal-card]").focus();
  }

  function closeReport() {
    const modal = document.querySelector("[data-report-modal]");
    if (!modal || modal.hidden) return;
    const returnId = modal.dataset.returnFocus;
    modal.hidden = true;
    delete document.body.dataset.reportModalOpen;
    state.selectedReport = null;
    if (returnId) document.querySelector(`[data-report-open="${returnId}"]`)?.focus();
  }

  async function processReport(event) {
    event.preventDefault();
    const report = state.selectedReport;
    if (!report) return;
    const status = document.querySelector("[data-report-action-status]").value;
    const note = document.querySelector("[data-report-action-note]").value.trim();
    const error = document.querySelector("[data-report-action-error]");
    const submit = document.querySelector("[data-report-action-submit]");
    if (!note) {
      error.textContent = "처리 사유를 입력해 주세요.";
      document.querySelector("[data-report-action-note]").focus();
      return;
    }
    submit.disabled = true;
    error.textContent = "";
    try {
      const response = await fetch(`/api/v1/travel-record-reports/${report.travelRecordReportId}`, {
        method: "PATCH",
        headers: { Accept: "application/json", "Content-Type": "application/json" },
        body: JSON.stringify({ status: status, resolutionNote: note }),
        allMyTripsLoading: false,
      });
      const payload = await response.json().catch(() => null);
      if (!response.ok || payload?.success === false) {
        throw new Error(payload?.message || "신고 처리 결과를 저장하지 못했어요.");
      }
      closeReport();
      reportFeedback(`신고 #${report.travelRecordReportId} 처리 결과를 저장했습니다.`, false);
      await loadReports();
    } catch (reason) {
      error.textContent = reason.message || "신고 처리 결과를 저장하지 못했어요.";
    } finally {
      submit.disabled = false;
    }
  }

  /** 사이드바나 운영 홈에서 고른 업무 화면 하나만 보여준다. */
  function openPanel(name) {
    const sections = document.querySelectorAll("[data-admin-section]");
    const target = [...sections].some((section) => section.dataset.adminSection === name)
      ? name
      : "overview";

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
    return target;
  }

  /** 화면을 바꾸고 새로고침해도 같은 업무가 열리도록 주소를 함께 갱신한다. */
  function navigatePanel(name) {
    const opened = openPanel(name);
    const url = new URL(window.location.href);
    if (opened === "overview") url.searchParams.delete("panel");
    else url.searchParams.set("panel", opened);
    window.history.replaceState({}, "", url);
    return opened;
  }

  /**
   * 주소로 특정 화면을 바로 연다. `/admin?panel=chat` 처럼 쓴다.
   *
   * <p>모르는 값이 들어오면 openPanel이 운영 홈으로 되돌린다. 이때 주소만 남아 있으면
   * 새로고침할 때마다 같은 일이 반복되므로, 실제로 열린 화면에 맞춰 주소를 정리한다.
   */
  function openPanelFromUrl() {
    const rawRequested = new URLSearchParams(window.location.search).get("panel");
    /* 기존 스토리보드·즐겨찾기의 metrics 주소는 운영 홈으로 자연스럽게 보낸다. */
    const requested = rawRequested === "metrics" ? "overview" : rawRequested;
    const opened = openPanel(requested || state.panel);

    if (rawRequested && (requested !== opened || rawRequested !== requested)) {
      const url = new URL(window.location.href);
      if (opened === "overview") url.searchParams.delete("panel");
      else url.searchParams.set("panel", opened);
      window.history.replaceState({}, "", url);
    }
  }

  function bind() {
    document.querySelectorAll("[data-admin-panel]").forEach((button) => {
      button.addEventListener("click", () => navigatePanel(button.dataset.adminPanel));
    });

    document.querySelectorAll("[data-admin-target]").forEach((button) => {
      button.addEventListener("click", () => navigatePanel(button.dataset.adminTarget));
    });

    document.querySelectorAll("[data-report-status]").forEach((button) => {
      button.addEventListener("click", () => {
        document.querySelectorAll("[data-report-status]").forEach((item) => {
          const selected = item === button;
          item.classList.toggle("on", selected);
          item.setAttribute("aria-pressed", selected ? "true" : "false");
        });
        state.reportStatus = button.dataset.reportStatus;
        loadReports();
      });
    });

    $("reportList").addEventListener("click", (event) => {
      const button = event.target.closest("[data-report-open]");
      if (button) openReport(button.dataset.reportOpen, button);
    });
    document.querySelector("[data-report-refresh]")?.addEventListener("click", loadReports);
    document.querySelector("[data-report-action-form]")?.addEventListener("submit", processReport);
    document.querySelector("[data-report-modal-close]")?.addEventListener("click", closeReport);
    document.querySelector("[data-report-action-cancel]")?.addEventListener("click", closeReport);
    document.querySelector("[data-report-modal]")?.addEventListener("click", (event) => {
      if (event.target.matches("[data-report-modal]")) closeReport();
    });
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape") closeReport();
    });

    /* 상담 채팅은 admin-chat.js가 맡는다. 여기서는 아무것도 걸지 않는다. */
  }

  function init() {
    bind();
    openPanelFromUrl();
    loadReports();
    document.body.dataset.pageReady = "true";
  }

  document.addEventListener("DOMContentLoaded", init);

  window.__adminDashboard = { state, loadReports, openPanel, navigatePanel, openPanelFromUrl };
})();
