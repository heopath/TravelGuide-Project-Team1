/* 마이페이지 · 상담 채팅 (손님 쪽)
 *
 * 1:1 문의와 나란히 있지만 성격이 다르다. 문의는 남기고 답변을 기다리는 것이고,
 * 상담은 지금 붙어서 주고받는 것이다.
 *
 * 열린 상담은 하나뿐이다. 서버가 손님당 하나로 제한하고 있어, 이미 열려 있으면 그 방을 준다.
 *
 * 갱신은 WebSocket이다(설계 문서 docs/support-chat-ai-websocket.md). 보내는 것(메시지 전송)은
 * 그대로 REST POST고, 새 메시지·방 상태 변경을 "받는" 것만 STOMP 구독이다. 연결돼 있는 동안은
 * 폴링을 쓰지 않지만, `/webjars/sockjs-client`·`/webjars/stomp-websocket`가 없거나(정적
 * 미리보기, 테스트) 핸드셰이크·nginx Upgrade 설정이 실패해 연결이 계속 안 되면, 봇의 첫
 * 인사·후속 답변은 비동기로 저장되므로 받을 방법이 아예 없어져 입력창이 잠긴 채 멈춘다.
 * 그래서 연결돼 있지 않은 동안에는 제한적으로 REST 폴링을 대신 돌린다(연결되면 자동으로 멈춘다).
 */
const statusLabels = {
  BOT: "AI 상담봇이 응대하고 있어요",
  WAITING: "담당자를 기다리는 중이에요",
  ASSIGNED: "담당자가 응대 중이에요",
  CLOSED: "종료된 상담이에요",
};
const senderLabels = { USER: "나", BOT: "상담봇", ADMIN: "담당자" };

export function initSupportChat() {
  const actionLinks = Object.freeze({
    NEW_TRIP: ["여행 만들기", "/trips/new/plan"], MY_TRIPS: ["내 여행 보기", "/mypage?view=trips"],
    TRIP_SCHEDULE: ["여행 일정 열기", "/trips/schedule"], RECOMMENDED_PLACES: ["추천 장소 보기", "/guide"],
    BOOK_FLIGHT: ["항공편 찾기", "/booking/flights?tab=flight"], BOOK_HOTEL: ["숙소 찾기", "/booking/flights?tab=hotel"],
    BOOK_TICKET: ["티켓·액티비티 보기", "/booking/flights?tab=ticket"], MY_BOOKINGS: ["예약 내역 보기", "/booking/flights?tab=mine"],
    MY_TICKETS: ["예매한 티켓 보기", "/mypage?view=tickets"], FAVORITES: ["찜한 여행지 보기", "/mypage?view=favorites"],
    REVIEWS: ["리뷰·후기 보기", "/mypage?view=reviews"], NOTIFICATIONS: ["알림 보기", "/mypage?view=notifications"],
    ACCOUNT_SETTINGS: ["계정 설정 열기", "/mypage?view=settings"], SUPPORT: ["고객센터 보기", "/mypage?view=support"],
  });
  const panel = document.querySelector('[data-support-panel="chat"]');
  if (!panel) return;

  const chat = panel.querySelector("[data-support-chat]");
  const start = panel.querySelector("[data-support-chat-start]");
  const openButton = panel.querySelector("[data-support-chat-open]");
  const emptyText = panel.querySelector("[data-support-chat-empty]");
  const statusText = panel.querySelector("[data-support-chat-status]");
  const messages = panel.querySelector("[data-support-chat-messages]");
  const form = panel.querySelector("[data-support-chat-form]");
  const input = panel.querySelector("[data-support-chat-input]");
  const send = panel.querySelector("[data-support-chat-send]");
  const actions = panel.querySelector("[data-support-chat-actions]");
  const agentButton = panel.querySelector("[data-support-chat-agent]");
  const returnButton = panel.querySelector("[data-support-chat-return]");
  const restartButton = panel.querySelector("[data-support-chat-restart]");
  if (!chat || !openButton || !messages || !form) return;

  let opened = false;
  let currentRoomId = null;
  /*
   * 봇 응답 대기는 별도 서버 이벤트나 DB 저장 없이 화면에서만 다룬다(1차 범위, 설계 문서 §7).
   * 다만 값을 들고 다니지는 않는다 — render가 방 상태와 마지막 메시지로 매번 다시 판단한다.
   */
  let waitingForBot = false;
  let socketDegraded = false;

  /*
   * load()를 부를 때마다 하나 늘린다. 응답이 도착했을 때 이 값과 다르면(그 사이 더 최신
   * 조회가 시작됐다는 뜻) 화면에 반영하지 않고 버린다 — 느린 옛 응답이 빠른 새 응답을
   * 뒤늦게 덮어써 화면이 다시 낡은 상태로 되돌아가는 것을 막는다. 구독을 REST 동기화보다
   * 먼저 걸어 두는 것과는 별개의 문제다(그건 "이벤트를 놓치지 않는다", 이건 "응답 순서가
   * 뒤바뀌어도 최신이 이긴다").
   */
  let loadGeneration = 0;

  async function request(url, options) {
    const response = await fetch(url, Object.assign({
      credentials: "same-origin",
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      allMyTripsLoading: false,
    }, options || {}));
    const payload = await response.json().catch(function () { return null; });
    if (!response.ok || payload?.success === false) {
      if (response.status === 401) {
        window.location.href = "/auth/login?redirect=" + encodeURIComponent(location.pathname);
      }
      const error = new Error(payload?.message || "요청을 처리하지 못했습니다.");
      error.status = response.status;
      throw error;
    }
    return payload?.data ?? payload;
  }

  function time(value) {
    if (!value) return "";
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return "";
    return new Intl.DateTimeFormat("ko-KR", {
      hour: "2-digit", minute: "2-digit",
    }).format(parsed);
  }

  function messageRow(message) {
    const item = document.createElement("div");
    item.className = "support-chat-message " + String(message.senderType || "").toLowerCase();
    item.dataset.supportChatMessage = String(message.supportChatMessageId);

    const who = document.createElement("em");
    who.textContent = senderLabels[message.senderType] || message.senderType;

    const body = document.createElement("p");
    body.textContent = message.content;

    const when = document.createElement("small");
    when.textContent = time(message.createdAt);

    item.append(who, body, when);
    const actionBlock = Array.isArray(message.blocks)
      ? message.blocks.find((block) => block?.blockType === "ACTION_GROUP") : null;
    const blockActions = Array.isArray(actionBlock?.payload?.items) ? actionBlock.payload.items : [];
    const actionKeys = (blockActions.length ? blockActions : [message.actionKey, message.actionKey2, message.actionKey3])
      .filter((key, index, keys) => actionLinks[key] && keys.indexOf(key) === index);
    if (message.senderType === "BOT" && actionKeys.length) {
      const group = document.createElement("div");
      group.className = "support-chat-links";
      actionKeys.forEach(function (key) {
        const action = actionLinks[key];
        const link = document.createElement("button");
        link.type = "button";
        link.className = "support-chat-link";
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
      cards.className = "support-chat-place-cards";
      placeCards.forEach(function (place) {
        if (!Number.isSafeInteger(Number(place?.placeId)) || !place?.name) return;
        const card = document.createElement("button");
        card.type = "button";
        card.className = "support-chat-place-card";
        card.dataset.route = "/guide/places/" + Number(place.placeId);
        if (place.imageUrl) {
          const image = document.createElement("img");
          image.className = "support-chat-place-card-image";
          image.src = place.imageUrl;
          image.alt = place.name;
          image.loading = "lazy";
          card.appendChild(image);
        }
        const head = document.createElement("div");
        head.className = "support-chat-place-card-head";
        const title = document.createElement("strong");
        title.textContent = place.name;
        head.appendChild(title);
        const rating = Number(place.rating);
        if (Number.isFinite(rating) && rating > 0) {
          const ratingEl = document.createElement("b");
          ratingEl.className = "support-chat-place-card-rating";
          ratingEl.textContent = "★ " + rating.toFixed(1);
          head.appendChild(ratingEl);
        }
        const meta = document.createElement("span");
        meta.textContent = [place.category, place.address].filter(Boolean).join(" · ");
        const reasonText = place.reason || place.description || "자세히 보기";
        const reason = document.createElement("i");
        reason.textContent = reasonText;
        card.append(head, meta, reason);
        if (place.description && place.description !== reasonText) {
          const description = document.createElement("p");
          description.className = "support-chat-place-card-description";
          description.textContent = place.description;
          card.appendChild(description);
        }
        cards.appendChild(card);
      });
      if (cards.childElementCount) item.appendChild(cards);
    }
    return item;
  }

  function statusLabel(room) {
    if (waitingForBot && room.status === "BOT") {
      return socketDegraded
        ? "연결이 잠시 끊겼어요. 곧 다시 연결할게요…"
        : "답변을 기다리는 중입니다...";
    }
    return statusLabels[room.status] || room.status;
  }

  /*
   * 대기 상태를 상태줄 한 줄만으로는 놓치기 쉽다는 피드백이 있었다(QA). 실제 대화창
   * 안에, 메신저의 "입력 중…" 말풍선과 같은 자리에 표시해 확실히 보이게 한다.
   *
   * <p>봇 답변 대기와 상담원 연결 대기는 손님 입장에서 뜻이 다르므로(하나는 곧 봇이
   * 답한다, 하나는 사람을 기다려야 한다) 문구와 색을 구분한다.
   */
  function typingIndicatorRow(kind) {
    const item = document.createElement("div");
    item.className = "support-chat-message bot support-chat-typing support-chat-typing-" + kind;
    item.setAttribute("role", "status");
    item.setAttribute("aria-live", "polite");

    const label = document.createElement("em");
    label.textContent = kind === "handoff" ? "상담원 연결 중입니다..." : "답변을 기다리는 중입니다...";

    const dots = document.createElement("span");
    dots.className = "support-chat-typing-dots";
    dots.setAttribute("aria-hidden", "true");
    dots.append(
      document.createElement("i"),
      document.createElement("i"),
      document.createElement("i"),
    );

    item.append(label, dots);
    return item;
  }

  /** 사용자가 맨 아래를 보고 있었는지와 현재 위치를 재렌더링 전에 보관한다. */
  function captureScroll(container) {
    const distanceFromBottom = container.scrollHeight - container.scrollTop - container.clientHeight;
    return {
      stickToBottom: container.childElementCount === 0 || distanceFromBottom <= 48,
      scrollTop: container.scrollTop,
    };
  }

  function restoreScroll(container, previous) {
    if (previous.stickToBottom) {
      container.scrollTop = container.scrollHeight;
      return;
    }
    const maximum = Math.max(0, container.scrollHeight - container.clientHeight);
    container.scrollTop = Math.min(previous.scrollTop, maximum);
  }

  function render(view) {
    const previousScroll = captureScroll(messages);
    opened = true;
    chat.hidden = false;
    start.hidden = true;

    const room = view.room;
    const roomMessages = Array.isArray(view.messages) ? view.messages : [];
    currentRoomId = room.supportChatRoomId;
    const closed = room.status === "CLOSED";
    const latestMessage = roomMessages[roomMessages.length - 1];
    /*
     * 대기 여부는 로컬 플래그를 이어 붙이지 않고 서버가 준 방 상태에서 매번 다시 판단한다.
     * 그래야 새로고침하거나 다른 탭에서 열어도 같은 결론이 나온다 — 봇 차례(BOT 상태에서
     * 아직 아무 말도 없거나 마지막 말이 손님)면 대기, 봇이 답했으면 해제다. 플래그를 들고
     * 다니면 방금 질문을 보낸 창을 새로 열었을 때 입력창이 열려 같은 방에 봇 호출이 겹친다.
     */
    waitingForBot = room.status === "BOT"
      && (!latestMessage || latestMessage.senderType === "USER");
    statusText.textContent = statusLabel(room);

    input.disabled = closed || waitingForBot;
    send.disabled = closed || waitingForBot;
    input.placeholder = closed ? "종료된 상담이에요" : "궁금한 내용을 입력하세요";

    /*
     * BOT에서는 AI 대화와 별개로 사람이 직접 상담원 연결을 선택할 수 있다.
     * WAITING이면 봇으로 되돌릴 수 있고, ASSIGNED면 기존 대화를 뺏지 않고 새 상담만 연다.
     */
    const canReturn = room.status === "WAITING";
    const canRestart = room.status === "WAITING" || room.status === "ASSIGNED";
    const canRequestAgent = room.status === "BOT";
    agentButton.hidden = !canRequestAgent;
    returnButton.hidden = !canReturn;
    restartButton.hidden = !canRestart;
    actions.hidden = !canRequestAgent && !canReturn && !canRestart;

    messages.replaceChildren();
    roomMessages.forEach(function (message) {
      messages.appendChild(messageRow(message));
    });
    /* 대기 중이면 대화 맨 끝에 타이핑 말풍선을 붙인다 — 상태줄과 별개로, 놓칠 수 없는 자리에. */
    if (waitingForBot) {
      messages.appendChild(typingIndicatorRow("bot"));
    } else if (room.status === "WAITING") {
      messages.appendChild(typingIndicatorRow("handoff"));
    }
    restoreScroll(messages, previousScroll);

    if (closed) {
      live.stop();
    } else {
      live.start(currentRoomId);
    }
  }

  /*
   * 두 버튼 모두 서버가 돌려준 방을 그대로 그린다. 낙관적 갱신을 하지 않는 이유는 방 상태가
   * 경쟁하기 때문이다 — 되돌리려는 순간 관리자가 가져가면 서버가 409로 거절하고, 그때는
   * 최신 상태를 다시 받아 그려야 한다.
   */
  async function act(button, url, failureText) {
    if (button.disabled) return;
    agentButton.disabled = true;
    returnButton.disabled = true;
    restartButton.disabled = true;
    try {
      render(await request(url, { method: "POST" }));
    } catch (error) {
      statusText.textContent = error.message || failureText;
      await load(); /* 거절당했다면 서버가 아는 최신 상태로 화면을 맞춘다. */
    } finally {
      agentButton.disabled = false;
      returnButton.disabled = false;
      restartButton.disabled = false;
    }
  }

  agentButton.addEventListener("click", function () {
    if (!window.confirm("AI 상담을 종료하고 상담원 연결을 요청할까요?")) return;
    act(agentButton, "/api/v1/support/chat/request-agent", "상담원 연결을 요청하지 못했어요.");
  });

  returnButton.addEventListener("click", function () {
    act(returnButton, "/api/v1/support/chat/return-to-bot", "봇 상담으로 돌아가지 못했어요.");
  });

  restartButton.addEventListener("click", function () {
    act(restartButton, "/api/v1/support/chat/restart", "새 상담을 시작하지 못했어요.");
  });

  async function load() {
    const generation = ++loadGeneration;
    try {
      const view = await request("/api/v1/support/chat");
      if (generation !== loadGeneration) return; /* 그 사이 더 최신 조회가 시작됐다 — 낡은 응답은 버린다. */
      render(view);
    } catch (error) {
      if (generation !== loadGeneration) return;
      /* 아직 상담을 연 적이 없으면 404다. 오류가 아니라 시작 전 상태다. */
      if (error.status === 404) {
        chat.hidden = true;
        start.hidden = false;
        return;
      }
      emptyText.textContent = error.message || "상담을 불러오지 못했어요.";
      chat.hidden = true;
      start.hidden = false;
    }
  }

  function showSocketError(error) {
    if (!error) return;
    statusText.textContent = error.message || "연결에 문제가 생겼어요.";
    /* 다시 시도할 수 없는 오류라면 대기 표시를 풀어 준다 — 잠긴 입력창에 갇히지 않게. */
    if (error.retryable === true || !waitingForBot) return;
    waitingForBot = false;
    input.disabled = false;
    send.disabled = false;
  }

  function handleSocketEvent(event) {
    if (event.type === "MESSAGE" && event.message && event.message.senderType === "BOT") {
      waitingForBot = false;
    }
    if (event.type === "ROOM_STATUS" && event.room && event.room.status !== "BOT") {
      waitingForBot = false;
    }
    /*
     * 종료된 방은 GET /api/v1/support/chat의 조회 대상이 아니다. CLOSED 이벤트 뒤 그 API를
     * 다시 부르면 404가 나고 상담 시작 화면으로 돌아가므로, 종료 상태는 이벤트로 직접
     * 반영해 이미 보던 메시지를 그대로 남긴다.
     */
    if (event.type === "ROOM_STATUS" && event.room && event.room.status === "CLOSED") {
      statusText.textContent = statusLabels.CLOSED;
      input.disabled = true;
      send.disabled = true;
      input.placeholder = "종료된 상담이에요";
      live.stop();
      return;
    }
    load();
  }

  const live = window.AllMyTripsSupportChatLive.create({
    onEvent: handleSocketEvent,
    onError: showSocketError,
    onConnected: load,
    onFallbackPoll: load,
    onConnectionChange: function (connected) {
      socketDegraded = !connected;
      if (waitingForBot) statusText.textContent = statusLabel({ status: "BOT" });
    },
  });

  function stopPolling() { live.stop(); }

  function deactivateChat() {
    loadGeneration++; /* 탭을 떠나기 전에 시작된 REST 응답이 늦게 와도 render하지 않는다. */
    opened = false;
    currentRoomId = null;
    live.stop();
  }

  /* ── 이벤트 ── */

  openButton.addEventListener("click", async function () {
    openButton.disabled = true;
    try {
      const view = await request("/api/v1/support/chat", { method: "POST" });
      loadGeneration++; /* 이 결과가 최신이다 — 그 사이 시작된 배경 조회는 도착해도 버려진다. */
      render(view);
      input.focus();
    } catch (error) {
      emptyText.textContent = error.message || "상담을 시작하지 못했어요.";
    } finally {
      openButton.disabled = false;
    }
  });

  form.addEventListener("submit", async function (event) {
    event.preventDefault();
    const content = input.value.trim();
    if (!content) return;
    send.disabled = true;
    try {
      const view = await request("/api/v1/support/chat/messages", {
        method: "POST",
        body: JSON.stringify({ content }),
      });
      loadGeneration++; /* 이 결과가 최신이다 — 그 사이 시작된 배경 조회는 도착해도 버려진다. */
      /* 대기 여부는 render가 방 상태와 마지막 메시지로 판단한다(설계 문서 §7). */
      render(view);
      input.value = "";
    } catch (error) {
      statusText.textContent = error.message || "메시지를 보내지 못했어요.";
    } finally {
      send.disabled = input.disabled;
      input.focus();
    }
  });

  /* 상담 탭으로 들어올 때만 연결·폴링을 시작하고, 다른 고객센터 탭에서는 즉시 정리한다. */
  document.addEventListener("click", function (event) {
    const tab = event.target.closest("[data-support-tab]");
    if (!tab) return;
    if (tab.dataset.supportTab === "chat") {
      load();
      return;
    }
    deactivateChat();
  });

  /* 실제 페이지를 떠날 때도 재연결·폴백 타이머와 STOMP 구독을 남기지 않는다. */
  window.addEventListener("pagehide", deactivateChat);

  return { load: load, stopPolling: stopPolling };
}
