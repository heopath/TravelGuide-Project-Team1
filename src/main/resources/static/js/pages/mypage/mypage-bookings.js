/* 예약 내역 — 항목별 조회
 *
 * 이 화면은 원래 티켓만 보여줬다. 항공과 숙소는 예약 화면 안에만 있어서, 무엇을 예약해
 * 뒀는지 한자리에서 볼 수 없었다.
 *
 * 티켓 쪽 화면(목록·상세 두 칸)은 그대로 둔다. 티켓은 입장 코드와 이용 방법까지 봐야 하는
 * 물건이라 줄 하나로 줄일 수 없다. 항공·숙소는 무엇을 언제 얼마에 잡았는지가 전부라
 * 줄로 보여준다.
 */
import { request } from "./mypage-common.js";

const TYPES = [
    { key: "", label: "전체", count: "total" },
    { key: "FLIGHT", label: "항공", count: "flight" },
    { key: "ACCOMMODATION", label: "숙소", count: "hotel" },
    { key: "TICKET", label: "티켓", count: "ticket" },
];

const state = {
    type: "",
    counts: null,
    loading: false,
};

const $ = (selector) => document.querySelector(selector);

function amountText(entry) {
    if (entry.amount === null || entry.amount === undefined) return "요금 미제공";
    const currency = String(entry.currency || "KRW").toUpperCase();
    const value = Number(entry.amount).toLocaleString("ko-KR");
    return currency === "KRW" ? `${value}원` : `${value} ${currency}`;
}

/* 서버가 쓰는 종류 이름. 숙소는 ACCOMMODATION이다. */
const TYPE_LABELS = { FLIGHT: "항공", ACCOMMODATION: "숙소", TICKET: "티켓" };

function renderTabs() {
    const box = $("[data-booking-type-tabs]");
    if (!box) return;

    box.replaceChildren(...TYPES.map((type) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = `mypage-ticket-tab${type.key === state.type ? " on" : ""}`;
        button.setAttribute("role", "tab");
        button.setAttribute("aria-selected", String(type.key === state.type));
        button.dataset.bookingType = type.key;

        const label = document.createElement("span");
        label.textContent = type.label;
        button.appendChild(label);

        /* 개수는 고른 종류와 무관하게 전부 센 값이다. 항공만 보고 있어도 숙소가 몇 건인지 보인다. */
        if (state.counts) {
            const count = document.createElement("b");
            count.textContent = String(state.counts[type.count] ?? 0);
            button.appendChild(count);
        }
        return button;
    }));
}

function renderList(items) {
    const box = $("[data-booking-list]");
    if (!box) return;

    if (!items.length) {
        const empty = document.createElement("p");
        empty.className = "mypage-state";
        empty.textContent = state.type
            ? `아직 예약한 ${TYPE_LABELS[state.type] || ""}이(가) 없어요.`
            : "아직 예약한 내역이 없어요.";
        box.replaceChildren(empty);
        return;
    }

    const list = document.createElement("ul");
    list.className = "booking-entry-list";

    items.forEach((entry) => {
        const row = document.createElement("li");

        const kind = document.createElement("span");
        kind.className = "booking-entry-kind";
        kind.textContent = TYPE_LABELS[entry.type] || entry.type;

        const title = document.createElement("strong");
        title.textContent = entry.title || TYPE_LABELS[entry.type] || "예약";

        const detail = document.createElement("span");
        detail.className = "booking-entry-detail";
        /* 어느 여행 것인지를 함께 적는다. 종류로 모으면 그 정보가 사라진다. */
        detail.textContent = [entry.tripTitle, entry.detail, entry.usageDate]
            .filter(Boolean).join(" · ");

        const status = document.createElement("em");
        status.className = "booking-entry-status";
        status.textContent = entry.statusLabel || entry.status || "";

        const amount = document.createElement("b");
        amount.textContent = amountText(entry);
        /* 실습·샘플 금액은 실제 결제액이 아니다. 표시를 떼면 안 된다. */
        if (entry.practice) amount.title = "실습 요금 · 실제 결제 금액 아님";

        row.append(kind, title, status, amount, detail);
        list.appendChild(row);
    });

    box.replaceChildren(list);
}

/** 티켓은 원래 화면이 훨씬 자세하다. 티켓만 볼 때는 그쪽을 쓴다. */
function toggleTicketPanels() {
    const ticketLayout = $(".mypage-ticket-layout");
    const statusTabs = $("[data-ticket-tabs]");
    const showTicketUi = state.type === "TICKET";

    if (ticketLayout) ticketLayout.hidden = !showTicketUi;
    if (statusTabs) statusTabs.hidden = !showTicketUi;

    const list = $("[data-booking-list]");
    if (list) list.hidden = showTicketUi;
}

async function load() {
    if (state.loading) return;
    state.loading = true;

    const box = $("[data-booking-list]");
    if (box && state.type !== "TICKET") {
        const loading = document.createElement("p");
        loading.className = "mypage-state";
        loading.textContent = "예약을 불러오는 중이에요.";
        box.replaceChildren(loading);
    }

    try {
        const query = state.type ? `?type=${encodeURIComponent(state.type)}` : "";
        const data = await request(`/api/v1/my-bookings${query}`);
        state.counts = data?.counts || null;
        renderTabs();
        renderList(data?.items || []);
    } catch (error) {
        state.counts = null;
        renderTabs();
        if (box) {
            const failed = document.createElement("p");
            failed.className = "mypage-state";
            failed.textContent = "예약을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.";
            box.replaceChildren(failed);
        }
    } finally {
        state.loading = false;
        toggleTicketPanels();
    }
}

export function initBookings() {
    const tabs = $("[data-booking-type-tabs]");
    if (!tabs) return;

    renderTabs();
    toggleTicketPanels();

    tabs.addEventListener("click", (event) => {
        const button = event.target.closest("[data-booking-type]");
        if (!button) return;
        const next = button.dataset.bookingType;
        if (next === state.type) return;
        state.type = next;
        renderTabs();
        void load();
    });

    void load();
}
