(function () {
  "use strict";

  const PAGE_SIZE = 20;
  const categoryLabels = {
    TRIP_PLAN: "여행 일정", AI_PLAN: "AI 여행 계획", PLACE_FAVORITE: "추천 장소·찜",
    BOOKING: "예약", ACCOUNT: "계정·로그인", ERROR: "오류", OTHER: "기타",
  };
  const statusLabels = { OPEN: "접수", IN_PROGRESS: "처리 중", ANSWERED: "답변 완료", CLOSED: "종료" };
  let status = "";
  let selectedId = null;
  let currentPage = 0;

  const list = document.querySelector("[data-inquiry-list]");
  const total = document.querySelector("[data-total]");
  const pagination = document.querySelector("[data-pagination]");
  const empty = document.querySelector("[data-detail-empty]");
  const detail = document.querySelector("[data-detail]");
  const statusSelect = document.querySelector("[data-status-select]");
  const replyForm = document.querySelector("[data-reply-form]");
  const feedback = document.querySelector("[data-support-feedback]");
  const refresh = document.querySelector("[data-support-refresh]");
  if (!list || !detail || !replyForm) return;

  function formatDate(value, withTime) {
    if (!value) return "";
    const options = { year: "numeric", month: "2-digit", day: "2-digit" };
    if (withTime) Object.assign(options, { hour: "2-digit", minute: "2-digit" });
    return new Intl.DateTimeFormat("ko-KR", options).format(new Date(value));
  }

  async function request(url, options) {
    const response = await fetch(url, Object.assign({
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      allMyTripsLoading: false,
    }, options || {}));
    const payload = await response.json().catch(function () { return null; });
    if (!response.ok || payload?.success === false) {
      if (response.status === 401) window.location.href = "/auth/login?redirect=/admin";
      if (response.status === 403) throw new Error("관리자만 접근할 수 있습니다.");
      throw new Error(payload?.message || "요청을 처리하지 못했습니다.");
    }
    return payload?.data ?? payload;
  }

  function state(message, error) {
    const p = document.createElement("p");
    p.className = `support-admin-state${error ? " error" : ""}`;
    p.textContent = message;
    return p;
  }

  function showFeedback(message, error) {
    if (!feedback) return;
    feedback.textContent = message || "";
    feedback.classList.toggle("error", Boolean(error));
    feedback.hidden = !message;
  }

  function renderPages(page, totalPages) {
    pagination.replaceChildren();
    for (let index = 0; index < totalPages; index += 1) {
      if (totalPages > 7 && Math.abs(index - page) > 3 && index !== 0 && index !== totalPages - 1) continue;
      const button = document.createElement("button");
      button.type = "button";
      button.textContent = String(index + 1);
      button.className = index === page ? "is-current" : "";
      if (index === page) button.setAttribute("aria-current", "page");
      button.addEventListener("click", function () { loadList(index); });
      pagination.appendChild(button);
    }
  }

  function inquiryButton(inquiry) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `support-admin-item${selectedId === inquiry.supportInquiryId ? " is-current" : ""}`;
    button.dataset.inquiryId = inquiry.supportInquiryId;
    if (selectedId === inquiry.supportInquiryId) button.setAttribute("aria-current", "true");
    const top = document.createElement("div");
    const category = document.createElement("span");
    category.textContent = categoryLabels[inquiry.category] || "기타";
    const badge = document.createElement("em");
    badge.className = `status-${String(inquiry.status).toLowerCase()}`;
    badge.textContent = statusLabels[inquiry.status] || inquiry.status;
    top.append(category, badge);
    const title = document.createElement("strong");
    title.textContent = inquiry.title;
    const member = document.createElement("small");
    member.textContent = `${inquiry.userNickname || inquiry.userEmail} · ${formatDate(inquiry.createdAt)}`;
    button.append(top, title, member);
    button.addEventListener("click", function () { openDetail(inquiry.supportInquiryId); });
    return button;
  }

  async function loadList(page) {
    currentPage = page || 0;
    list.replaceChildren(state("문의 목록을 불러오는 중입니다."));
    try {
      const query = new URLSearchParams({ page: currentPage, size: PAGE_SIZE });
      if (status) query.set("status", status);
      const data = await request(`/api/v1/admin/support/inquiries?${query}`);
      total.textContent = `${Number(data.totalElements || 0).toLocaleString("ko-KR")}건`;
      list.replaceChildren();
      if (!data.inquiries?.length) list.appendChild(state("해당 상태의 문의가 없습니다."));
      else data.inquiries.forEach(function (inquiry) { list.appendChild(inquiryButton(inquiry)); });
      renderPages(Number(data.page || 0), Number(data.totalPages || 0));
    } catch (error) {
      total.textContent = "—";
      list.replaceChildren(state(error.message, true));
    }
  }

  function renderDetail(data) {
    const inquiry = data.inquiry;
    empty.hidden = true;
    detail.hidden = false;
    document.querySelector("[data-detail-category]").textContent = categoryLabels[inquiry.category] || "기타";
    document.querySelector("[data-detail-title]").textContent = inquiry.title;
    document.querySelector("[data-detail-member]").textContent = `${inquiry.userNickname || "회원"} · ${inquiry.userEmail || ""} · ${formatDate(inquiry.createdAt, true)}`;
    document.querySelector("[data-detail-content]").textContent = inquiry.content;
    statusSelect.value = inquiry.status;
    const content = document.querySelector("[data-reply-content]");
    const submit = document.querySelector("[data-reply-submit]");
    const closed = inquiry.status === "CLOSED";
    content.disabled = closed;
    submit.disabled = closed;
    content.placeholder = closed ? "종료된 문의입니다." : "회원에게 전달할 답변을 입력해 주세요.";

    const replies = document.querySelector("[data-reply-list]");
    replies.replaceChildren();
    if (!data.replies?.length) {
      const none = document.createElement("p");
      none.className = "support-admin-no-reply";
      none.textContent = "등록된 답변이 없습니다.";
      replies.appendChild(none);
    } else {
      data.replies.forEach(function (reply) {
        const article = document.createElement("article");
        article.className = "support-admin-reply";
        const meta = document.createElement("div");
        const author = document.createElement("strong");
        author.textContent = reply.adminNickname || "관리자";
        const date = document.createElement("time");
        date.textContent = formatDate(reply.createdAt, true);
        meta.append(author, date);
        const text = document.createElement("p");
        text.textContent = reply.content;
        article.append(meta, text);
        replies.appendChild(article);
      });
    }
  }

  async function openDetail(inquiryId) {
    selectedId = Number(inquiryId);
    document.querySelectorAll("[data-inquiry-id]").forEach(function (item) {
      item.classList.toggle("is-current", Number(item.dataset.inquiryId) === selectedId);
    });
    empty.hidden = false;
    empty.querySelector("strong").textContent = "문의 내용을 불러오는 중입니다.";
    empty.querySelector("p").textContent = "잠시만 기다려 주세요.";
    detail.hidden = true;
    try {
      renderDetail(await request(`/api/v1/admin/support/inquiries/${selectedId}`));
    } catch (error) {
      empty.hidden = false;
      empty.querySelector("strong").textContent = "문의 내용을 불러오지 못했습니다.";
      empty.querySelector("p").textContent = error.message;
    }
  }

  document.querySelectorAll("[data-status]").forEach(function (button) {
    button.addEventListener("click", function () {
      status = button.dataset.status;
      selectedId = null;
      document.querySelectorAll("[data-status]").forEach(function (item) { item.classList.toggle("is-current", item === button); });
      document.querySelectorAll("[data-status]").forEach(function (item) {
        if (item === button) item.setAttribute("aria-current", "true");
        else item.removeAttribute("aria-current");
      });
      empty.hidden = false;
      empty.querySelector("strong").textContent = "확인할 문의를 선택해 주세요.";
      empty.querySelector("p").textContent = "왼쪽 목록에서 문의를 선택하면 내용과 답변 내역이 표시됩니다.";
      detail.hidden = true;
      loadList(0);
    });
  });

  statusSelect.addEventListener("change", async function () {
    if (!selectedId) return;
    statusSelect.disabled = true;
    try {
      renderDetail(await request(`/api/v1/admin/support/inquiries/${selectedId}/status`, {
        method: "PATCH", body: JSON.stringify({ status: statusSelect.value }),
      }));
      showFeedback(`문의 상태를 ${statusLabels[statusSelect.value] || statusSelect.value}(으)로 변경했습니다.`);
      await loadList(currentPage);
    } catch (error) {
      showFeedback(error.message || "문의 상태를 변경하지 못했습니다.", true);
      await openDetail(selectedId);
    } finally {
      statusSelect.disabled = false;
    }
  });

  replyForm.addEventListener("submit", async function (event) {
    event.preventDefault();
    const content = document.querySelector("[data-reply-content]");
    const message = document.querySelector("[data-form-message]");
    const submit = document.querySelector("[data-reply-submit]");
    message.textContent = "";
    if (!content.value.trim()) { message.textContent = "답변 내용을 입력해 주세요."; return; }
    submit.disabled = true;
    try {
      renderDetail(await request(`/api/v1/admin/support/inquiries/${selectedId}/replies`, {
        method: "POST", body: JSON.stringify({ content: content.value.trim() }),
      }));
      content.value = "";
      showFeedback("답변을 등록했습니다.");
      window.AllMyTripsModal?.showToast("답변이 등록되었습니다.");
      await loadList(currentPage);
    } catch (error) {
      message.textContent = error.message;
    } finally {
      submit.disabled = false;
    }
  });

  refresh?.addEventListener("click", function () {
    showFeedback("");
    loadList(currentPage);
    if (selectedId) openDetail(selectedId);
  });

  loadList(0);
})();
