/* 마이페이지 알림 (#191)
 *
 * 지금까지 사이드바에 `준비 중` 버튼만 있었고 뒤에 아무것도 없었습니다.
 *
 * 알림은 곁다리라 실패해도 다른 화면을 막지 않습니다. 목록을 못 받으면 그 자리에만
 * 알리고, 배지는 조용히 감춥니다.
 */
import { request } from "./mypage-common.js";

const TYPE_ICON = {
    PAYMENT_COMPLETED: "▦",
    SUPPORT_REPLIED: "◎",
};

let loaded = false;

function el(selector) {
    return document.querySelector(selector);
}

/**
 * 언제 왔는지.
 *
 * <p>알림은 "방금 왔나"가 중요하고 정확한 시각은 그다음이다. 하루가 넘어가면 날짜를
 * 보여 주는 편이 오히려 읽기 쉽다.
 */
function whenText(iso) {
    const at = new Date(iso);
    if (Number.isNaN(at.getTime())) return "";

    const gap = Date.now() - at.getTime();
    const minute = 60 * 1000;

    if (gap < minute) return "방금";
    if (gap < 60 * minute) return `${Math.floor(gap / minute)}분 전`;
    if (gap < 24 * 60 * minute) return `${Math.floor(gap / (60 * minute))}시간 전`;
    return at.toLocaleDateString("ko-KR");
}

function setBadge(unread) {
    const badge = el("[data-notification-badge]");
    if (!badge) return;
    /* 0을 붙여 두면 읽을 것이 있는 줄 알고 눌러 보게 된다. */
    badge.hidden = !unread;
    badge.textContent = unread > 99 ? "99+" : String(unread || "");
}

function row(item) {
    const li = document.createElement("li");
    li.className = "notification-item";
    li.dataset.notificationId = item.notificationId;
    if (!item.readAt) li.dataset.unread = "1";

    const icon = document.createElement("span");
    icon.className = "notification-icon";
    icon.setAttribute("aria-hidden", "true");
    icon.textContent = TYPE_ICON[item.type] || "◇";

    const text = document.createElement("div");
    text.className = "notification-text";

    const title = document.createElement("strong");
    title.textContent = item.title || "";
    text.appendChild(title);

    if (item.body) {
        const body = document.createElement("p");
        body.textContent = item.body;
        text.appendChild(body);
    }

    const when = document.createElement("small");
    when.textContent = whenText(item.createdAt);
    text.appendChild(when);

    /*
     * 갈 곳이 있으면 링크로 만든다. 주소는 서버가 우리 사이트 안의 경로만 담도록
     * 제약을 걸어 두었지만, 화면에서도 한 번 더 본다. 바깥 주소가 섞이면 알림이
     * 손님을 사이트 밖으로 보내는 발판이 된다.
     */
    const safeLink = typeof item.link === "string"
        && item.link.startsWith("/") && !item.link.startsWith("//")
        ? item.link
        : null;

    if (safeLink) {
        const go = document.createElement("a");
        go.className = "notification-go";
        go.href = safeLink;
        go.textContent = "보러 가기 →";
        text.appendChild(go);
    }

    li.append(icon, text);
    return li;
}

function render(data) {
    const state = el("[data-notifications-state]");
    const list = el("[data-notifications-list]");
    const readAll = el("[data-notifications-read-all]");
    const items = Array.isArray(data?.items) ? data.items : [];

    setBadge(Number(data?.unread || 0));

    if (items.length === 0) {
        state.textContent = "아직 받은 알림이 없어요.";
        state.hidden = false;
        list.hidden = true;
        if (readAll) readAll.hidden = true;
        return;
    }

    list.replaceChildren(...items.map(row));
    list.hidden = false;
    state.hidden = true;
    if (readAll) readAll.hidden = Number(data?.unread || 0) === 0;
}

async function load() {
    try {
        render(await request("/api/v1/notifications?page=0&size=20"));
    } catch (error) {
        const state = el("[data-notifications-state]");
        state.textContent = error.message || "알림을 불러오지 못했어요.";
        state.hidden = false;
        el("[data-notifications-list]").hidden = true;
    }
}

/**
 * 안 읽은 개수만 받아 배지에 그린다.
 *
 * <p>알림 화면을 열지 않아도 사이드바에 표시가 떠야 한다. 목록까지 받으면 낭비다.
 * 실패하면 조용히 넘어간다 — 배지 하나 때문에 마이페이지에 오류를 띄울 이유가 없다.
 */
export async function initNotificationBadge() {
    try {
        const data = await request("/api/v1/notifications/unread-count");
        setBadge(Number(data?.unread || 0));
    } catch (error) {
        setBadge(0);
    }
}

/** 알림 화면을 처음 열 때 한 번 준비한다. */
export function initNotifications() {
    if (loaded) {
        load();
        return;
    }
    loaded = true;

    const list = el("[data-notifications-list]");
    const readAll = el("[data-notifications-read-all]");

    /*
     * 목록 전체에 한 번만 건다. 줄마다 걸면 다시 그릴 때 앞의 것이 남아 같은 처리가
     * 두 번 돈다.
     */
    list?.addEventListener("click", async (event) => {
        const item = event.target.closest("[data-notification-id]");
        if (!item || !item.dataset.unread) return;

        /* 눌러서 옮겨 가더라도 읽음 표시는 남긴다. 먼저 보내고 화면을 고친다. */
        delete item.dataset.unread;
        try {
            await request(`/api/v1/notifications/${item.dataset.notificationId}/read`,
                { method: "PATCH" });
            await initNotificationBadge();
        } catch (error) {
            /* 실패하면 안 읽음으로 되돌린다. 읽은 것처럼 보이면 손님이 놓친다. */
            item.dataset.unread = "1";
        }
    });

    readAll?.addEventListener("click", async () => {
        readAll.disabled = true;
        try {
            await request("/api/v1/notifications/read-all", { method: "PATCH" });
            await load();
        } catch (error) {
            /* 그대로 둔다. 다시 누를 수 있다. */
        } finally {
            readAll.disabled = false;
        }
    });

    load();
}
