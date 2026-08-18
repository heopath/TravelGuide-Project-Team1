import { request, renderPagination, showToast } from "./mypage-common.js";

const PAGE_SIZE = 10;
const categoryLabels = {
    ALL: "전체",
    TRIP_PLAN: "여행 일정",
    AI_PLAN: "AI 여행 계획",
    PLACE_FAVORITE: "추천 장소·찜",
    /* 티켓을 사이트 안에서 사게 되면서(v0.7.0) 항공·숙소만 가리키던 이름을 넓혔다. */
    BOOKING: "예약·결제",
    ACCOUNT: "계정·로그인",
    ERROR: "오류·기타",
};
const statusLabels = {
    OPEN: "접수 완료",
    IN_PROGRESS: "답변 준비 중",
    ANSWERED: "답변 완료",
    CLOSED: "종료",
};
const faqs = [
    { category: "TRIP_PLAN", question: "직접 만든 여행 일정은 어디에서 수정하나요?", answer: "마이페이지의 내 여행에서 일정을 선택하면 날짜별 장소와 시간을 수정할 수 있습니다." },
    { category: "TRIP_PLAN", question: "일정에 찜한 장소를 추가할 수 있나요?", answer: "일정 편집 화면의 장소 추가 기능에서 찜한 여행지를 불러올 수 있습니다." },
    { category: "AI_PLAN", question: "AI 여행 계획은 어떤 정보를 기준으로 만들어지나요?", answer: "목적지, 여행 기간, 인원, 여행 목적, 일정 속도, 이동·음식·숙박 선호를 반영해 초안을 만듭니다." },
    { category: "AI_PLAN", question: "AI가 만든 일정도 직접 수정할 수 있나요?", answer: "추천 결과에서 일정 편집으로 이동하면 일반 일정과 동일하게 장소와 시간을 조정할 수 있습니다." },
    { category: "PLACE_FAVORITE", question: "찜한 장소는 어디에서 확인하나요?", answer: "마이페이지의 찜한 여행지 메뉴에서 저장한 장소를 확인할 수 있습니다." },
    { category: "PLACE_FAVORITE", question: "장소 후기는 어떻게 작성하나요?", answer: "추천 장소 상세 페이지의 방문자 후기 영역에서 별점과 내용을 작성할 수 있습니다." },
    /*
     * 항공·숙소와 티켓은 결제하는 곳이 다르다. 이 차이를 모르면 "왜 어떤 건 여기서 사고
     * 어떤 건 밖에서 사냐"고 묻게 되므로 한 항목에서 함께 설명한다.
     */
    { category: "BOOKING", question: "항공권과 숙소도 사이트 안에서 결제하나요?", answer: "아니요. 항공권과 숙소는 예약 정보와 예상 금액만 확인하고, 실제 결제는 항공사·여행사·숙소 예약 사이트에서 진행합니다. 티켓·액티비티만 사이트 안에서 결제합니다." },
    { category: "BOOKING", question: "티켓 결제는 실제로 돈이 빠져나가나요?", answer: "아니요. 지금은 모의 결제라 실제 청구가 일어나지 않습니다. 화면에도 모의 결제임을 표시하고 있습니다." },
    { category: "BOOKING", question: "예약을 담아뒀는데 사라졌어요.", answer: "티켓을 담으면 자리를 15분 동안 잡아둡니다. 그 안에 결제하지 않으면 자리가 자동으로 반납되어 다른 분이 예약할 수 있게 됩니다. 다시 담아 주세요." },
    { category: "BOOKING", question: "대기 화면에서 차례가 왔는데 예약을 놓쳤어요.", answer: "차례가 온 뒤 2분 안에 예약을 마쳐야 합니다. 시간이 지나면 자리가 다음 분에게 넘어가므로 처음부터 다시 줄을 서야 합니다." },
    { category: "BOOKING", question: "입장 코드가 안 보여요.", answer: "입장 코드는 보안을 위해 결제 직후 한 번만 표시하고 서버에 남기지 않습니다. 새로고침하면 사라지며, 이후에는 티켓 번호와 이용 기간만 확인할 수 있습니다. 현장에서 입장이 어려우면 티켓 번호로 문의해 주세요." },
    { category: "BOOKING", question: "결제한 티켓을 취소하고 환불받을 수 있나요?", answer: "내 예약에서 결제 취소를 누르면 환불되고 발급된 티켓은 사용할 수 없게 됩니다. 다만 티켓을 한 장이라도 사용했거나 이용일이 지난 경우에는 취소할 수 없습니다. 이용일 당일까지는 취소할 수 있습니다." },
    { category: "ACCOUNT", question: "닉네임과 비밀번호는 어디에서 변경하나요?", answer: "마이페이지의 계정 설정에서 닉네임과 비밀번호를 변경할 수 있습니다." },
    /*
     * 1:1 문의와 상담 채팅이 나란히 있어 어느 쪽으로 물어야 할지 헷갈린다.
     * 기다림의 성격이 다르다는 것을 알려주는 편이 실제로 도움이 된다.
     */
    { category: "ERROR", question: "1:1 문의와 상담 채팅은 무엇이 다른가요?", answer: "1:1 문의는 내용을 남기면 담당자가 확인한 뒤 답변을 등록하는 방식이라 시간이 걸립니다. 상담 채팅은 담당자와 바로 주고받는 방식이며, 담당자가 연결될 때까지 기다려야 합니다. 급하지 않은 내용은 1:1 문의를 권합니다." },
    { category: "ERROR", question: "화면이 멈추거나 오류가 반복되면 어떻게 하나요?", answer: "먼저 Ctrl+F5로 새로고침한 뒤에도 반복되면 오류가 발생한 화면과 상황을 1:1 문의로 알려주세요." },
];

function formatDate(value) {
    if (!value) return "";
    return new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit" })
        .format(new Date(value));
}

export function initSupport() {
    const tabs = Array.from(document.querySelectorAll("[data-support-tab]"));
    const panels = Array.from(document.querySelectorAll("[data-support-panel]"));
    const search = document.querySelector("[data-support-search]");
    const categoryRoot = document.querySelector("[data-support-faq-categories]");
    const faqList = document.querySelector("[data-support-faq-list]");
    const faqEmpty = document.querySelector("[data-support-faq-empty]");
    const form = document.querySelector("[data-support-form]");
    const formCategory = document.querySelector("[data-support-category]");
    const formTitle = document.querySelector("[data-support-title]");
    const formContent = document.querySelector("[data-support-content]");
    const formLength = document.querySelector("[data-support-content-length]");
    const formMessage = document.querySelector("[data-support-form-message]");
    const submit = document.querySelector("[data-support-submit]");
    const inquiryList = document.querySelector("[data-support-inquiry-list]");
    const total = document.querySelector("[data-support-total]");
    const pagination = document.querySelector("[data-support-pagination]");
    const detail = document.querySelector("[data-support-detail]");
    const detailContent = document.querySelector("[data-support-detail-content]");
    if (!form || !faqList) return Promise.resolve();

    let selectedCategory = "ALL";
    let inquiriesLoaded = false;

    function activateTab(name) {
        tabs.forEach((tab) => {
            const current = tab.dataset.supportTab === name;
            tab.classList.toggle("is-current", current);
            tab.setAttribute("aria-selected", String(current));
        });
        panels.forEach((panel) => { panel.hidden = panel.dataset.supportPanel !== name; });
        if (name === "mine" && !inquiriesLoaded) loadInquiries();
    }

    function renderFaqs() {
        const keyword = search.value.trim().toLowerCase();
        const filtered = faqs.filter((faq) => {
            const categoryMatches = selectedCategory === "ALL" || faq.category === selectedCategory;
            const keywordMatches = !keyword || `${faq.question} ${faq.answer}`.toLowerCase().includes(keyword);
            return categoryMatches && keywordMatches;
        });
        faqList.replaceChildren();
        filtered.forEach((faq) => {
            const item = document.createElement("article");
            item.className = "support-faq-item";
            const button = document.createElement("button");
            button.type = "button";
            button.setAttribute("aria-expanded", "false");
            const label = document.createElement("span");
            label.textContent = faq.question;
            const mark = document.createElement("i");
            mark.textContent = "+";
            button.append(label, mark);
            const answer = document.createElement("p");
            answer.textContent = faq.answer;
            answer.hidden = true;
            button.addEventListener("click", () => {
                const open = answer.hidden;
                answer.hidden = !open;
                button.setAttribute("aria-expanded", String(open));
                mark.textContent = open ? "−" : "+";
            });
            item.append(button, answer);
            faqList.appendChild(item);
        });
        faqEmpty.hidden = filtered.length > 0;
    }

    Object.entries(categoryLabels).forEach(([value, label]) => {
        const button = document.createElement("button");
        button.type = "button";
        button.textContent = label;
        button.className = value === "ALL" ? "is-current" : "";
        button.addEventListener("click", () => {
            selectedCategory = value;
            Array.from(categoryRoot.children).forEach((item) => item.classList.toggle("is-current", item === button));
            renderFaqs();
        });
        categoryRoot.appendChild(button);
    });

    function createInquiryItem(inquiry) {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "support-inquiry-item";
        const top = document.createElement("div");
        const category = document.createElement("span");
        category.textContent = categoryLabels[inquiry.category] || "기타";
        const status = document.createElement("em");
        status.className = `status-${String(inquiry.status || "").toLowerCase()}`;
        status.textContent = statusLabels[inquiry.status] || inquiry.status;
        top.append(category, status);
        const title = document.createElement("strong");
        title.textContent = inquiry.title;
        const meta = document.createElement("small");
        meta.textContent = formatDate(inquiry.createdAt);
        button.append(top, title, meta);
        button.addEventListener("click", () => openDetail(inquiry.supportInquiryId));
        return button;
    }

    async function loadInquiries(page = 1) {
        inquiryList.replaceChildren();
        const state = document.createElement("p");
        state.className = "mypage-state";
        state.textContent = "문의 내역을 불러오는 중입니다.";
        inquiryList.appendChild(state);
        try {
            const data = await request(`/api/v1/support/inquiries/me?page=${page - 1}&size=${PAGE_SIZE}`, { allMyTripsLoading: false });
            inquiriesLoaded = true;
            total.textContent = `${Number(data.totalElements || 0).toLocaleString("ko-KR")}개`;
            inquiryList.replaceChildren();
            if (!data.inquiries?.length) {
                state.textContent = "아직 접수한 문의가 없습니다.";
                inquiryList.appendChild(state);
            } else {
                data.inquiries.forEach((inquiry) => inquiryList.appendChild(createInquiryItem(inquiry)));
            }
            renderPagination(pagination, Number(data.page || 0) + 1, Number(data.totalPages || 0), loadInquiries);
        } catch (error) {
            total.textContent = "—";
            state.classList.add("error");
            state.textContent = error.message || "문의 내역을 불러오지 못했습니다.";
            inquiryList.replaceChildren(state);
        }
    }

    async function openDetail(inquiryId) {
        detail.hidden = false;
        detailContent.innerHTML = '<p class="mypage-state">문의 내용을 불러오는 중입니다.</p>';
        document.body.style.overflow = "hidden";
        try {
            const data = await request(`/api/v1/support/inquiries/${inquiryId}`, { allMyTripsLoading: false });
            detailContent.replaceChildren();
            const heading = document.createElement("div");
            heading.className = "support-detail-heading";
            const status = document.createElement("span");
            status.textContent = statusLabels[data.status] || data.status;
            const title = document.createElement("h3");
            title.id = "support-detail-title";
            title.textContent = data.title;
            const meta = document.createElement("small");
            meta.textContent = `${categoryLabels[data.category] || "기타"} · ${formatDate(data.createdAt)}`;
            heading.append(status, title, meta);
            const question = document.createElement("p");
            question.className = "support-detail-question";
            question.textContent = data.content;
            const replies = document.createElement("section");
            replies.className = "support-detail-replies";
            const repliesTitle = document.createElement("h4");
            repliesTitle.textContent = "관리자 답변";
            replies.appendChild(repliesTitle);
            if (!data.replies?.length) {
                const waiting = document.createElement("p");
                waiting.className = "support-detail-waiting";
                waiting.textContent = "담당자가 문의를 확인하고 있습니다. 답변이 등록되면 이곳에서 확인할 수 있어요.";
                replies.appendChild(waiting);
            } else {
                data.replies.forEach((reply) => {
                    const item = document.createElement("article");
                    const meta = document.createElement("small");
                    meta.textContent = `${reply.adminNickname || "관리자"} · ${formatDate(reply.createdAt)}`;
                    const content = document.createElement("p");
                    content.textContent = reply.content;
                    item.append(meta, content);
                    replies.appendChild(item);
                });
            }
            detailContent.append(heading, question, replies);
        } catch (error) {
            detailContent.textContent = error.message || "문의 내용을 불러오지 못했습니다.";
        }
    }

    function closeDetail() {
        detail.hidden = true;
        document.body.style.overflow = "";
    }

    tabs.forEach((tab) => tab.addEventListener("click", () => activateTab(tab.dataset.supportTab)));
    search.addEventListener("input", renderFaqs);
    formContent.addEventListener("input", () => { formLength.textContent = formContent.value.length; });
    document.querySelectorAll("[data-support-detail-close]").forEach((button) => button.addEventListener("click", closeDetail));
    document.addEventListener("keydown", (event) => { if (event.key === "Escape" && !detail.hidden) closeDetail(); });
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        formMessage.textContent = "";
        submit.disabled = true;
        try {
            await request("/api/v1/support/inquiries", {
                method: "POST",
                allMyTripsLoading: false,
                body: JSON.stringify({
                    category: formCategory.value,
                    title: formTitle.value.trim(),
                    content: formContent.value.trim(),
                }),
            });
            form.reset();
            formLength.textContent = "0";
            inquiriesLoaded = false;
            showToast("문의가 접수되었습니다.");
            activateTab("mine");
        } catch (error) {
            formMessage.textContent = error.message || "문의를 접수하지 못했습니다.";
        } finally {
            submit.disabled = false;
        }
    });

    renderFaqs();
    return Promise.resolve();
}
