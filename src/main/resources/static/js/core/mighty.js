/* 마이티 — 화면 구석의 상담 도우미 (#191)
 *
 * 기존 상담 채팅(/api/v1/support/chat)을 그대로 쓴다. 별도 대화방을 만들지 않으므로
 * 마이페이지 고객센터에서 하던 대화가 여기서 이어진다.
 *
 * 모듈이 아니라 window에 붙는 고전 스크립트다. 화면마다 모듈 체계가 달라(마이페이지는
 * ES 모듈, 예약 화면은 IIFE) 어느 쪽에서도 부담 없이 올라가야 한다.
 *
 * CSRF 토큰은 app.js가 fetch를 감싸며 붙인다. 여기서 따로 챙기지 않는다.
 */
(function () {
    const actionLinks = Object.freeze({
        NEW_TRIP: ["여행 만들기", "/trips/new/plan"], MY_TRIPS: ["내 여행 보기", "/mypage?view=trips"],
        TRIP_SCHEDULE: ["여행 일정 열기", "/trips/schedule"], RECOMMENDED_PLACES: ["추천 장소 보기", "/guide"],
        BOOK_FLIGHT: ["항공편 찾기", "/booking/flights?tab=flight"], BOOK_HOTEL: ["숙소 찾기", "/booking/flights?tab=hotel"],
        BOOK_TICKET: ["티켓·액티비티 보기", "/booking/flights?tab=ticket"], MY_BOOKINGS: ["예약 내역 보기", "/booking/flights?tab=mine"],
        MY_TICKETS: ["예매한 티켓 보기", "/mypage?view=tickets"], FAVORITES: ["찜한 여행지 보기", "/mypage?view=favorites"],
        REVIEWS: ["리뷰·후기 보기", "/mypage?view=reviews"], NOTIFICATIONS: ["알림 보기", "/mypage?view=notifications"],
        ACCOUNT_SETTINGS: ["계정 설정 열기", "/mypage?view=settings"], SUPPORT: ["고객센터 보기", "/mypage?view=support"],
    });
    "use strict";

    const root = document.querySelector("[data-mighty-root]");
    if (!root) return;

    const openButton = root.querySelector("[data-mighty-open]");
    const panel = root.querySelector("[data-mighty-panel]");
    const closeButton = root.querySelector("[data-mighty-close]");
    const log = root.querySelector("[data-mighty-log]");
    const empty = root.querySelector("[data-mighty-empty]");
    const form = root.querySelector("[data-mighty-form]");
    const input = root.querySelector("[data-mighty-input]");
    const sendButton = root.querySelector("[data-mighty-send]");
    const state = root.querySelector("[data-mighty-state]");
    const dot = root.querySelector("[data-mighty-dot]");
    const actions = root.querySelector("[data-mighty-actions]");
    const returnButton = root.querySelector("[data-mighty-return]");
    const restartButton = root.querySelector("[data-mighty-restart]");

    /*
     * 열려 있는 동안에만 새 말을 확인한다. 실시간 수신(WebSocket)은 상담 화면이 쓰는데,
     * 그쪽은 화면마다 스크립트가 달라 여기서 같이 쓰기 어렵다. 열었을 때만 3초마다 보는
     * 편이 모든 화면에서 똑같이 도는 방법이다.
     */
    const POLL_MS = 3000;
    const REQUEST_TIMEOUT_MS = 12000;

    let timer = null;
    let known = 0;
    let sending = false;
    let refreshing = false;

    function say(message) {
        empty.textContent = message;
        empty.hidden = !message;
    }

    async function call(url, body) {
        const controller = typeof window.AbortController === "function"
            ? new window.AbortController()
            : null;
        const timeout = controller
            ? window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)
            : null;

        try {
            const response = await fetch(url, {
                method: "POST",
                credentials: "same-origin",
                headers: {
                    Accept: "application/json",
                    ...(body ? { "Content-Type": "application/json" } : {}),
                },
                ...(body ? { body: JSON.stringify(body) } : {}),
                ...(controller ? { signal: controller.signal } : {}),
                /* 봇 창 안에 로딩 안내가 있으므로 화면 전체를 가리지 않는다. */
                allMyTripsLoading: false,
            });

            if (response.status === 401) {
                const error = new Error("로그인이 필요해요.");
                error.needsLogin = true;
                throw error;
            }
            const payload = await response.json().catch(() => null);
            if (!response.ok || !payload?.success) {
                throw new Error(payload?.message || "지금은 답할 수 없어요.");
            }
            return payload.data;
        } catch (error) {
            if (error?.name === "AbortError") {
                throw new Error("응답이 늦어지고 있어요. 잠시 후 다시 시도해 주세요.");
            }
            throw error;
        } finally {
            if (timeout !== null) window.clearTimeout(timeout);
        }
    }

    /** 방 상태를 사람이 읽는 말로. 답을 기다려도 되는지가 여기서 갈린다. */
    function stateText(status) {
        if (status === "WAITING") return "상담원을 기다리는 중이에요";
        if (status === "ASSIGNED") return "상담원과 이야기하는 중이에요";
        if (status === "CLOSED") return "지난 상담이에요";
        return "여행에 대해 무엇이든 물어보세요";
    }

    function row(message) {
        const item = document.createElement("li");
        /* 보낸 사람에 따라 좌우가 갈린다. 손님 말만 오른쪽이다. */
        item.className = "mighty-line " + (message.senderType === "USER" ? "me" : "them");

        if (message.senderType !== "USER") {
            const who = document.createElement("small");
            who.textContent = message.senderType === "ADMIN" ? "상담원" : "마이티";
            item.appendChild(who);
        }

        const body = document.createElement("p");
        body.textContent = message.content || "";
        item.appendChild(body);
        const actionKeys = [message.actionKey, message.actionKey2, message.actionKey3]
            .filter((key, index, keys) => actionLinks[key] && keys.indexOf(key) === index);
        if (message.senderType === "BOT" && actionKeys.length) {
            const group = document.createElement("div");
            group.className = "mighty-links";
            actionKeys.forEach(function (key) {
                const action = actionLinks[key];
                const link = document.createElement("button");
                link.type = "button";
                link.className = "mighty-link";
                link.dataset.route = action[1];
                link.textContent = action[0] + " →";
                group.appendChild(link);
            });
            item.appendChild(group);
        }
        return item;
    }

    function draw(data) {
        const messages = Array.isArray(data?.messages) ? data.messages : [];
        const status = data?.room?.status;
        state.textContent = stateText(status);

        /*
         * 탈출구 노출. WAITING이면 봇으로 되돌릴 수 있고, 사람이 이미 붙은 ASSIGNED면
         * 그 대화를 뺏지 않고 새 상담만 연다. BOT·CLOSED에서는 필요 없다.
         */
        const canReturn = status === "WAITING";
        const canRestart = status === "WAITING" || status === "ASSIGNED";
        returnButton.hidden = !canReturn;
        restartButton.hidden = !canRestart;
        actions.hidden = !canReturn && !canRestart;

        log.replaceChildren(...messages.map(row));
        say(messages.length ? "" : "아직 나눈 이야기가 없어요.");

        /* 새 말은 아래에 붙는다. 열 때마다 끝을 보여줘야 방금 온 답이 보인다. */
        log.scrollTop = log.scrollHeight;

        const theirs = messages.filter((m) => m.senderType !== "USER").length;
        if (panel.hidden && theirs > known) {
            dot.hidden = false;
        }
        known = theirs;
    }

    /*
     * 서버가 돌려준 방을 그대로 그린다. 낙관적 갱신을 하지 않는 이유는 방 상태가 경쟁하기
     * 때문이다 — 되돌리려는 순간 관리자가 가져가면 서버가 409로 거절하고, 그때는 최신
     * 상태를 다시 받아 그려야 한다.
     */
    async function act(url, failureText) {
        returnButton.disabled = true;
        restartButton.disabled = true;
        try {
            draw(await call(url));
        } catch (error) {
            say(error.message || failureText);
            await refresh(); /* 거절당했다면 서버가 아는 최신 상태로 화면을 맞춘다. */
        } finally {
            returnButton.disabled = false;
            restartButton.disabled = false;
        }
    }

    returnButton.addEventListener("click", function () {
        act("/api/v1/support/chat/return-to-bot", "마이티에게 돌아가지 못했어요.");
    });

    restartButton.addEventListener("click", function () {
        act("/api/v1/support/chat/restart", "새 상담을 시작하지 못했어요.");
    });

    async function refresh() {
        /* 3초 확인 주기보다 응답이 늦어도 같은 요청을 계속 쌓지 않는다. */
        if (refreshing) return;
        refreshing = true;
        try {
            draw(await call("/api/v1/support/chat"));
        } catch (error) {
            if (error.needsLogin) {
                stop();
                say("로그인하면 마이티와 이야기할 수 있어요.");
                state.textContent = "로그인이 필요해요";
                form.hidden = true;
                return;
            }
            say(error.message);
        } finally {
            refreshing = false;
        }
    }

    function start() {
        stop();
        timer = window.setInterval(refresh, POLL_MS);
    }

    function stop() {
        if (timer) window.clearInterval(timer);
        timer = null;
    }

    function open() {
        panel.hidden = false;
        openButton.setAttribute("aria-expanded", "true");
        dot.hidden = true;
        form.hidden = false;
        state.textContent = "마이티를 연결하는 중이에요";
        say("대화를 불러오는 중이에요.");
        refresh();
        start();
        input.focus();
    }

    function close() {
        panel.hidden = true;
        openButton.setAttribute("aria-expanded", "false");
        stop();
        openButton.focus();
    }

    openButton.addEventListener("click", function () {
        if (panel.hidden) open();
        else close();
    });

    closeButton.addEventListener("click", close);

    document.addEventListener("keydown", function (event) {
        if (event.key === "Escape" && !panel.hidden) close();
    });

    form.addEventListener("submit", async function (event) {
        event.preventDefault();
        const text = input.value.trim();
        if (!text || sending) return;

        sending = true;
        sendButton.disabled = true;
        /*
         * 보낸 말을 먼저 그린다. 답이 올 때까지 화면이 그대로면 눌렸는지 알 수 없어
         * 다시 누르게 된다.
         */
        log.appendChild(row({ senderType: "USER", content: text }));
        log.scrollTop = log.scrollHeight;
        input.value = "";
        say("");

        try {
            await call("/api/v1/support/chat/messages", { content: text });
            await refresh();
        } catch (error) {
            if (error.needsLogin) {
                say("로그인하면 마이티와 이야기할 수 있어요.");
                form.hidden = true;
            } else {
                say(error.message);
                /* 못 보낸 말을 돌려준다. 다시 치게 하면 화가 난다. */
                input.value = text;
            }
        } finally {
            sending = false;
            sendButton.disabled = false;
        }
    });

    /* 창을 덮어 두면 굳이 물어볼 이유가 없다. 돌아오면 다시 본다. */
    document.addEventListener("visibilitychange", function () {
        if (panel.hidden) return;
        if (document.hidden) stop();
        else { refresh(); start(); }
    });

    window.AllMyTripsMighty = { open, close, refresh };
})();
