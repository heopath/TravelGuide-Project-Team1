/* 마이페이지 · 상담 채팅 (손님 쪽)
 *
 * 1:1 문의와 나란히 있지만 성격이 다르다. 문의는 남기고 답변을 기다리는 것이고,
 * 상담은 지금 붙어서 주고받는 것이다.
 *
 * 열린 상담은 하나뿐이다. 서버가 손님당 하나로 제한하고 있어, 이미 열려 있으면 그 방을 준다.
 *
 * 갱신은 WebSocket이다(설계 문서 docs/support-chat-ai-websocket.md). 보내는 것(메시지 전송)은
 * 그대로 REST POST고, 새 메시지·방 상태 변경을 "받는" 것만 STOMP 구독으로 바뀌었다 — 폴링은
 * 더 이상 쓰지 않는다. `/webjars/sockjs-client`·`/webjars/stomp-websocket`가 없는 환경
 * (정적 미리보기, 테스트)에서는 조용히 건너뛴다 — REST 흐름(열기·보내기·새로고침)은 그대로 된다.
 */
const SOCKET_ENDPOINT = "/ws/support-chat";
const ROOM_TOPIC_PREFIX = "/topic/support-chat/rooms/";
/* 서버가 복구 가능한 오류를 보내는 본인 전용 큐(설계 문서 §3). */
const ERROR_QUEUE = "/user/queue/support-chat/errors";
const RECONNECT_DELAY_MS = 3000;

const statusLabels = {
  BOT: "상담원을 연결하고 있어요",
  WAITING: "담당자를 기다리는 중이에요",
  ASSIGNED: "담당자가 응대 중이에요",
  CLOSED: "종료된 상담이에요",
};
const senderLabels = { USER: "나", BOT: "상담봇", ADMIN: "담당자" };

function readCsrfCookie() {
  const match = document.cookie.match(/(?:^|;\s*)CSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : "";
}

function socketAvailable() {
  return typeof window.SockJS === "function"
    && typeof window.Stomp === "object" && typeof window.Stomp.over === "function";
}

export function initSupportChat() {
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
  if (!chat || !openButton || !messages || !form) return;

  let opened = false;
  let currentRoomId = null;
  /*
   * 봇 응답 대기는 별도 서버 이벤트나 DB 저장 없이 화면에서만 다룬다(1차 범위, 설계 문서 §7).
   * 다만 값을 들고 다니지는 않는다 — render가 방 상태와 마지막 메시지로 매번 다시 판단한다.
   */
  let waitingForBot = false;
  let socketDegraded = false;

  let stompClient = null;
  let subscription = null;
  let errorSubscription = null;
  let reconnectTimer = null;
  /* 구독을 먼저 걸고 REST로 맞추는 동안 도착한 이벤트는 동기화가 끝난 뒤 한 번에 반영한다. */
  let syncing = false;
  let resyncPending = false;

  async function request(url, options) {
    const response = await fetch(url, Object.assign({
      credentials: "same-origin",
      headers: { Accept: "application/json", "Content-Type": "application/json" },
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
    return item;
  }

  function statusLabel(room) {
    if (waitingForBot && room.status === "BOT") {
      return socketDegraded
        ? "연결이 잠시 끊겼어요. 곧 다시 연결할게요…"
        : "답변을 준비하고 있습니다...";
    }
    return statusLabels[room.status] || room.status;
  }

  function render(view) {
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

    messages.replaceChildren();
    roomMessages.forEach(function (message) {
      messages.appendChild(messageRow(message));
    });
    messages.scrollTop = messages.scrollHeight;

    if (closed) disconnectSocket();
    else connectSocket();
  }

  async function load() {
    try {
      render(await request("/api/v1/support/chat"));
    } catch (error) {
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

  /* ── WebSocket 수신 ── */

  function connectSocket() {
    if (!socketAvailable() || stompClient || !currentRoomId) return;
    let socket;
    try {
      socket = new window.SockJS(SOCKET_ENDPOINT);
    } catch (error) {
      return; /* 정적 미리보기 등 SockJS가 실제로 동작하지 않는 환경. REST만으로 계속 쓸 수 있다. */
    }
    const client = window.Stomp.over(socket);
    client.debug = function () {}; /* 콘솔 소음만 줄인다. */
    stompClient = client;
    client.connect(
      { "X-CSRF-TOKEN": readCsrfCookie() },
      onSocketConnected,
      onSocketDown
    );
  }

  function onSocketConnected() {
    socketDegraded = false;
    if (waitingForBot) statusText.textContent = statusLabel({ status: "BOT" });
    resubscribe();
  }

  function onSocketDown() {
    stompClient = null;
    subscription = null;
    errorSubscription = null; /* 끊긴 연결의 구독은 남겨 두면 재연결 뒤 다시 걸지 않는다. */
    socketDegraded = true;
    if (waitingForBot) statusText.textContent = statusLabel({ status: "BOT" });
    scheduleReconnect();
  }

  function scheduleReconnect() {
    if (reconnectTimer || !opened || !currentRoomId) return;
    reconnectTimer = window.setTimeout(function () {
      reconnectTimer = null;
      connectSocket();
    }, RECONNECT_DELAY_MS);
  }

  /**
   * 구독을 먼저 걸고, 그다음에 REST로 방 전체를 맞춘다.
   *
   * <p>순서가 반대면 REST 응답과 SUBSCRIBE 사이에 저장된 봇 답변을 아무도 받지 못한다.
   * 화면은 계속 "답변을 준비하고 있습니다..."인 채 입력창이 잠겨 손님이 빠져나올 수 없다.
   * 먼저 구독해 두면 그 틈이 없고, 동기화 도중 도착한 이벤트는 끝난 뒤 한 번 더 불러
   * 반영한다(구독 시점과 REST 시점이 겹쳐 같은 메시지를 두 번 봐도 REST 결과로 다시 그리므로
   * 중복은 남지 않는다).
   */
  function resubscribe() {
    if (!currentRoomId || !stompClient || !stompClient.connected) return;
    const roomId = currentRoomId;
    if (subscription) { try { subscription.unsubscribe(); } catch (error) { /* 이미 끊긴 연결. */ } }
    syncing = true;
    resyncPending = false;
    subscription = stompClient.subscribe(
      ROOM_TOPIC_PREFIX + roomId,
      function (frame) { handleSocketEvent(JSON.parse(frame.body)); }
    );
    subscribeErrors();
    load().then(finishSync, finishSync);
  }

  function finishSync() {
    syncing = false;
    if (!resyncPending) return;
    resyncPending = false;
    load();
  }

  /* 서버가 보내는 복구 가능한 오류를 받을 자리(설계 문서 §3). 없으면 오류가 조용히 사라진다. */
  function subscribeErrors() {
    if (errorSubscription || !stompClient || !stompClient.connected) return;
    errorSubscription = stompClient.subscribe(
      ERROR_QUEUE,
      function (frame) { showSocketError(JSON.parse(frame.body)); }
    );
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
      disconnectSocket();
      return;
    }
    /* 동기화 중이면 지금 부르지 않는다 — 끝난 직후 한 번만 다시 부른다. */
    if (syncing) {
      resyncPending = true;
      return;
    }
    load();
  }

  function disconnectSocket() {
    if (reconnectTimer) window.clearTimeout(reconnectTimer);
    reconnectTimer = null;
    if (subscription) { try { subscription.unsubscribe(); } catch (error) { /* 이미 끊긴 연결. */ } }
    subscription = null;
    if (errorSubscription) { try { errorSubscription.unsubscribe(); } catch (error) { /* 이미 끊긴 연결. */ } }
    errorSubscription = null;
    if (stompClient) { try { stompClient.disconnect(); } catch (error) { /* 이미 끊긴 연결. */ } }
    stompClient = null;
  }

  function stopPolling() {
    disconnectSocket();
  }

  /* ── 이벤트 ── */

  openButton.addEventListener("click", async function () {
    openButton.disabled = true;
    try {
      render(await request("/api/v1/support/chat", { method: "POST" }));
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

  /* 상담 탭으로 들어올 때 불러온다. 다른 탭에 있는 동안은 아무것도 하지 않는다. */
  document.addEventListener("click", function (event) {
    const tab = event.target.closest('[data-support-tab="chat"]');
    if (!tab) return;
    load();
  });

  return { load: load, stopPolling: stopPolling };
}
