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
    const head = root.querySelector(".mighty-head");
    const closeButton = root.querySelector("[data-mighty-close]");
    const log = root.querySelector("[data-mighty-log]");
    const empty = root.querySelector("[data-mighty-empty]");
    const form = root.querySelector("[data-mighty-form]");
    const input = root.querySelector("[data-mighty-input]");
    const sendButton = root.querySelector("[data-mighty-send]");
    const state = root.querySelector("[data-mighty-state]");
    const dot = root.querySelector("[data-mighty-dot]");
    const actions = root.querySelector("[data-mighty-actions]");
    const toolsButton = root.querySelector("[data-mighty-tools]");
    const agentButton = root.querySelector("[data-mighty-agent]");
    const returnButton = root.querySelector("[data-mighty-return]");
    const restartButton = root.querySelector("[data-mighty-restart]");

    const REQUEST_TIMEOUT_MS = 12000;

    let known = 0;
    let sending = false;
    let refreshing = false;
    let toolsExpanded = false;
    /* 드래그 뒤에 따라오는 click까지 열기/닫기로 처리하면 놓은 자리에서 창이 튕긴다. */
    let suppressNextClick = false;

    function setToolsExpanded(expanded) {
        toolsExpanded = Boolean(expanded && !toolsButton.hidden);
        actions.hidden = !toolsExpanded;
        toolsButton.setAttribute("aria-expanded", String(toolsExpanded));
        toolsButton.setAttribute("aria-label", toolsExpanded ? "상담 옵션 닫기" : "상담 옵션 열기");
    }

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
    function stateText(status, waitingForBot) {
        if (waitingForBot) return "답변을 기다리는 중입니다...";
        if (status === "WAITING") return "상담원을 기다리는 중이에요";
        if (status === "ASSIGNED") return "상담원과 이야기하는 중이에요";
        if (status === "CLOSED") return "지난 상담이에요";
        return "여행에 대해 무엇이든 물어보세요";
    }

    /*
     * 대기 상태를 상태줄 한 줄만으로는 놓치기 쉽다는 피드백이 있었다(마이페이지 상담 채팅에서
     * 먼저 반영, QA). 마이티도 실제 대화창 안에, 메신저의 "입력 중…"과 같은 자리에 표시해
     * 확실히 보이게 한다. 봇 답변 대기와 상담원 연결 대기는 손님 입장에서 뜻이 다르므로
     * 문구·색으로 구분한다.
     */
    function typingRow(kind) {
        const item = document.createElement("li");
        item.className = "mighty-line them mighty-typing mighty-typing-" + kind;
        item.setAttribute("role", "status");
        item.setAttribute("aria-live", "polite");

        const body = document.createElement("p");
        const label = document.createElement("em");
        label.textContent = kind === "handoff" ? "상담원 연결 중입니다..." : "답변을 기다리는 중입니다...";
        const dots = document.createElement("span");
        dots.className = "mighty-typing-dots";
        dots.setAttribute("aria-hidden", "true");
        dots.append(document.createElement("i"), document.createElement("i"), document.createElement("i"));
        body.append(label, dots);
        item.appendChild(body);
        return item;
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
        const actionBlock = Array.isArray(message.blocks)
            ? message.blocks.find((block) => block?.blockType === "ACTION_GROUP") : null;
        const blockActions = Array.isArray(actionBlock?.payload?.items) ? actionBlock.payload.items : [];
        const actionKeys = (blockActions.length ? blockActions : [message.actionKey, message.actionKey2, message.actionKey3])
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
        const placeBlock = Array.isArray(message.blocks)
            ? message.blocks.find((block) => block?.blockType === "PLACE_CARDS") : null;
        const placeCards = Array.isArray(placeBlock?.payload?.items) ? placeBlock.payload.items.slice(0, 3) : [];
        if (message.senderType === "BOT" && placeCards.length) {
            const cards = document.createElement("div");
            cards.className = "mighty-place-cards";
            placeCards.forEach(function (place) {
                if (!Number.isSafeInteger(Number(place?.placeId)) || !place?.name) return;
                const card = document.createElement("button");
                card.type = "button";
                card.className = "mighty-place-card";
                card.dataset.route = "/guide/places/" + Number(place.placeId);
                if (place.imageUrl) {
                    const image = document.createElement("img");
                    image.className = "mighty-place-card-image";
                    image.src = place.imageUrl;
                    image.alt = place.name;
                    image.loading = "lazy";
                    card.appendChild(image);
                }
                const head = document.createElement("div");
                head.className = "mighty-place-card-head";
                const title = document.createElement("strong");
                title.textContent = place.name;
                head.appendChild(title);
                const rating = Number(place.rating);
                if (Number.isFinite(rating) && rating > 0) {
                    const ratingEl = document.createElement("b");
                    ratingEl.className = "mighty-place-card-rating";
                    ratingEl.textContent = "★ " + rating.toFixed(1);
                    head.appendChild(ratingEl);
                }
                const meta = document.createElement("span");
                meta.textContent = [place.category, place.address].filter(Boolean).join(" · ");
                const reasonText = place.reason || place.description || "자세히 보기";
                const reason = document.createElement("small");
                reason.textContent = reasonText;
                card.append(head, meta, reason);
                if (place.description && place.description !== reasonText) {
                    const description = document.createElement("p");
                    description.className = "mighty-place-card-description";
                    description.textContent = place.description;
                    card.appendChild(description);
                }
                cards.appendChild(card);
            });
            if (cards.childElementCount) item.appendChild(cards);
        }
        return item;
    }

    /** 실시간 이벤트나 폴백 조회로 다시 그리기 전에 읽던 위치와 하단 여부를 보관한다. */
    function captureScroll() {
        const distanceFromBottom = log.scrollHeight - log.scrollTop - log.clientHeight;
        return {
            stickToBottom: log.childElementCount === 0 || distanceFromBottom <= 48,
            scrollTop: log.scrollTop,
        };
    }

    function restoreScroll(previous) {
        if (previous.stickToBottom) {
            log.scrollTop = log.scrollHeight;
            return;
        }
        const maximum = Math.max(0, log.scrollHeight - log.clientHeight);
        log.scrollTop = Math.min(previous.scrollTop, maximum);
    }

    function draw(data) {
        const previousScroll = captureScroll();
        const messages = Array.isArray(data?.messages) ? data.messages : [];
        const status = data?.room?.status;
        live.setRoom(data?.room?.supportChatRoomId);
        /*
         * 대기 여부는 로컬 플래그를 이어 붙이지 않고 서버가 준 방 상태로 매번 다시 판단한다
         * (마이페이지 상담 채팅과 같은 판단 방식). 봇 차례(BOT 상태에서 아직 아무 말도
         * 없거나 마지막 말이 손님)면 대기, 봇이 답했으면 해제다.
         */
        const lastMessage = messages[messages.length - 1];
        const waitingForBot = status === "BOT" && (!lastMessage || lastMessage.senderType === "USER");
        state.textContent = stateText(status, waitingForBot);

        /*
         * BOT에서는 AI 대화와 별개로 사람이 직접 상담원 연결을 선택할 수 있다.
         * WAITING이면 봇으로 되돌릴 수 있고, ASSIGNED면 기존 대화를 뺏지 않고 새 상담만 연다.
         */
        const canReturn = status === "WAITING";
        const canRestart = status === "WAITING" || status === "ASSIGNED";
        const canRequestAgent = status === "BOT";
        agentButton.hidden = !canRequestAgent;
        returnButton.hidden = !canReturn;
        restartButton.hidden = !canRestart;
        const hasActions = canRequestAgent || canReturn || canRestart;
        toolsButton.hidden = !hasActions;
        if (!hasActions) setToolsExpanded(false);
        else actions.hidden = !toolsExpanded;

        log.replaceChildren(...messages.map(row));
        /* 대기 중이면 대화 맨 끝에 타이핑 말풍선을 붙인다 — 상태줄과 별개로, 놓칠 수 없는 자리에. */
        if (waitingForBot) {
            log.appendChild(typingRow("bot"));
        } else if (status === "WAITING") {
            log.appendChild(typingRow("handoff"));
        }
        say(messages.length || waitingForBot ? "" : "아직 나눈 이야기가 없어요.");

        /* 아래를 보고 있던 경우만 새 말까지 따라간다. 위를 읽고 있으면 그 위치를 유지한다. */
        restoreScroll(previousScroll);

        const theirs = messages.filter((m) => m.senderType !== "USER").length;
        if (panel.hidden && theirs > known) {
            dot.hidden = false;
        }
        known = theirs;

        /*
         * open() 직후의 보정은 "대화를 불러오는 중이에요" placeholder 크기 기준이라
         * 실제 메시지가 그려지면 대화창 높이가 달라질 수 있다. 내용이 실제로 반영된
         * 뒤에도 다시 확인해야 화면 밖으로 넘치는 걸 놓치지 않는다.
         */
        keepMightyInViewport();
    }

    /*
     * 서버가 돌려준 방을 그대로 그린다. 낙관적 갱신을 하지 않는 이유는 방 상태가 경쟁하기
     * 때문이다 — 되돌리려는 순간 관리자가 가져가면 서버가 409로 거절하고, 그때는 최신
     * 상태를 다시 받아 그려야 한다.
     */
    async function act(url, failureText) {
        agentButton.disabled = true;
        returnButton.disabled = true;
        restartButton.disabled = true;
        try {
            draw(await call(url));
        } catch (error) {
            say(error.message || failureText);
            await refresh(); /* 거절당했다면 서버가 아는 최신 상태로 화면을 맞춘다. */
        } finally {
            agentButton.disabled = false;
            returnButton.disabled = false;
            restartButton.disabled = false;
        }
    }

    agentButton.addEventListener("click", function () {
        if (!window.confirm("AI 상담을 종료하고 상담원 연결을 요청할까요?")) return;
        setToolsExpanded(false);
        act("/api/v1/support/chat/request-agent", "상담원 연결을 요청하지 못했어요.");
    });

    returnButton.addEventListener("click", function () {
        setToolsExpanded(false);
        act("/api/v1/support/chat/return-to-bot", "마이티에게 돌아가지 못했어요.");
    });

    restartButton.addEventListener("click", function () {
        setToolsExpanded(false);
        act("/api/v1/support/chat/restart", "새 상담을 시작하지 못했어요.");
    });

    toolsButton.addEventListener("click", function () {
        setToolsExpanded(!toolsExpanded);
    });

    async function refresh() {
        /* 폴백 주기보다 응답이 늦거나 WebSocket 이벤트가 겹쳐도 같은 요청을 쌓지 않는다. */
        if (refreshing) return;
        refreshing = true;
        try {
            draw(await call("/api/v1/support/chat"));
        } catch (error) {
            if (error.needsLogin) {
                live.stop();
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

    function handleSocketEvent(event) {
        if (event?.type === "ROOM_STATUS" && event?.room?.status === "CLOSED") {
            live.stop();
        }
        refresh();
    }

    function showSocketError(error) {
        say(error?.message || "연결에 문제가 생겼어요.");
    }

    const live = window.AllMyTripsSupportChatLive.create({
        onEvent: handleSocketEvent,
        onError: showSocketError,
        onConnected: refresh,
        onFallbackPoll: refresh,
        onConnectionChange: function (connected) {
            if (!connected && !panel.hidden) state.textContent = "연결이 끊겨 다시 연결하는 중이에요";
        },
    });

    function open() {
        panel.hidden = false;
        openButton.setAttribute("aria-expanded", "true");
        dot.hidden = true;
        form.hidden = false;
        state.textContent = "마이티를 연결하는 중이에요";
        say("대화를 불러오는 중이에요.");
        live.start();
        refresh();
        input.focus();
        /* 대화창이 커지며 아이콘 자리 기준으로 화면 밖까지 넘칠 수 있다 — 열 때마다 보정한다. */
        keepMightyInViewport();
    }

    function close() {
        setToolsExpanded(false);
        panel.hidden = true;
        openButton.setAttribute("aria-expanded", "false");
        live.stop();
        openButton.focus();
    }

    openButton.addEventListener("click", function () {
        if (suppressNextClick) { suppressNextClick = false; return; }
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
        if (document.hidden) live.stop();
        else { live.start(); refresh(); }
    });

    /*
     * 여행 가이드 같은 화면에서는 마이티가 기본 자리(오른쪽 아래)에 있으면 다른 버튼과
     * 겹친다. 손님이 직접 끌어서 옮길 수 있게 하고, 위치는 localStorage에 저장해 다음
     * 방문·다른 화면에서도 유지한다. 닫혀 있을 때는 버튼을, 열려 있을 때는 대화창
     * 머리글(닫기 버튼 제외)을 손잡이로 쓴다 — 둘 다 같은 root를 움직인다.
     *
     * 기준점(pivot)은 항상 아이콘이다 — top·right만 쓰고 bottom·left는 쓰지 않는다.
     * 대화창은 같은 root 안에서 아이콘 다음 순서로 세로로 쌓이므로(align-items: flex-end,
     * DOM 순서상 버튼 다음이 대화창) 아이콘 아래로 펼쳐지는 모양이 레이아웃만으로 나온다.
     * 대화창이 열리며 커진 root가 화면 아래·왼쪽으로 넘치면 keepMightyInViewport()가
     * pivot 자체를 안으로 당겨 보정한다(오른쪽 anchor라 오른쪽으로 넘치는 경우는 없다).
     */
    const POSITION_STORAGE_KEY = "allMyTripsMightyPosition";
    const DRAG_THRESHOLD = 4;
    let dragging = false;
    let dragMoved = false;
    let dragPointerId = null;
    let dragStartX = 0;
    let dragStartY = 0;
    let dragOriginTop = 0;
    let dragOriginRight = 0;

    function clampPx(value, max) {
        return Math.min(Math.max(value, 0), Math.max(0, max));
    }

    function applyMightyPosition(top, right) {
        const rect = root.getBoundingClientRect();
        root.style.top = clampPx(top, window.innerHeight - rect.height) + "px";
        root.style.right = clampPx(right, window.innerWidth - rect.width) + "px";
        root.style.left = "auto";
        root.style.bottom = "auto";
    }

    function saveMightyPosition() {
        try {
            window.localStorage.setItem(POSITION_STORAGE_KEY, JSON.stringify({
                top: parseFloat(root.style.top) || 0,
                right: parseFloat(root.style.right) || 0,
            }));
        } catch (error) { /* 저장 공간이 없거나 막혀 있어도 화면 이동 자체는 계속된다. */ }
    }

    function restoreMightyPosition() {
        let saved = null;
        try {
            saved = JSON.parse(window.localStorage.getItem(POSITION_STORAGE_KEY) || "null");
        } catch (error) {
            saved = null;
        }
        if (!saved || typeof saved.top !== "number" || typeof saved.right !== "number") return;
        applyMightyPosition(saved.top, saved.right);
    }

    /*
     * 대화창이 열리거나 창 크기가 바뀌어 root(아이콘+대화창)가 화면 아래·왼쪽으로 넘치면
     * pivot을 안으로 당긴다. 한 번도 끌어본 적 없으면(기본 CSS의 오른쪽 아래 고정 그대로)
     * 손대지 않는다 — 그 상태는 이미 항상 화면 안에 들어오게 설계돼 있다.
     */
    function keepMightyInViewport() {
        if (root.style.top === "" || root.style.top === "auto") return;
        const rect = root.getBoundingClientRect();
        let nextTop = parseFloat(root.style.top) || 0;
        let nextRight = parseFloat(root.style.right) || 0;
        if (rect.bottom > window.innerHeight) nextTop -= (rect.bottom - window.innerHeight);
        if (nextTop < 0) nextTop = 0;
        if (rect.left < 0) nextRight += rect.left; /* rect.left가 음수인 만큼 오른쪽 여백을 줄여 안으로 당긴다 */
        if (nextRight < 0) nextRight = 0;
        applyMightyPosition(nextTop, nextRight);
        saveMightyPosition();
    }

    function onMightyPointerDown(event) {
        if (typeof event.button === "number" && event.button !== 0) return; /* 왼쪽 버튼·터치만 */
        if (event.target.closest("[data-mighty-close]")) return;
        dragging = true;
        dragMoved = false;
        dragPointerId = event.pointerId;
        const rect = root.getBoundingClientRect();
        dragStartX = event.clientX;
        dragStartY = event.clientY;
        dragOriginTop = rect.top;
        dragOriginRight = window.innerWidth - rect.right;
        if (typeof event.currentTarget.setPointerCapture === "function") {
            try { event.currentTarget.setPointerCapture(dragPointerId); } catch (error) { /* 무시 */ }
        }
    }

    function onMightyPointerMove(event) {
        if (!dragging || event.pointerId !== dragPointerId) return;
        const dx = event.clientX - dragStartX;
        const dy = event.clientY - dragStartY;
        if (!dragMoved && Math.hypot(dx, dy) < DRAG_THRESHOLD) return;
        dragMoved = true;
        event.preventDefault();
        applyMightyPosition(dragOriginTop + dy, dragOriginRight - dx);
    }

    function onMightyPointerUp(event) {
        if (!dragging || event.pointerId !== dragPointerId) return;
        dragging = false;
        dragPointerId = null;
        if (dragMoved) {
            saveMightyPosition();
            suppressNextClick = true; /* 놓는 순간 뒤따르는 click이 버튼을 열고 닫지 않게 한다. */
        }
    }

    /* Pointer Events가 없는 환경(구형 브라우저, 일부 테스트 DOM)에서는 그냥 고정 위치로 둔다. */
    if (typeof window.PointerEvent === "function") {
        [openButton, head].forEach(function (handle) {
            if (!handle) return;
            handle.style.touchAction = "none";
            handle.addEventListener("pointerdown", onMightyPointerDown);
        });
        document.addEventListener("pointermove", onMightyPointerMove);
        document.addEventListener("pointerup", onMightyPointerUp);
        document.addEventListener("pointercancel", onMightyPointerUp);
        window.addEventListener("resize", keepMightyInViewport);
        restoreMightyPosition();
    }

    window.AllMyTripsMighty = { open, close, refresh };
})();
