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
  /* 봇 응답을 기다리는 동안 로컬로만 관리한다 — 서버 이벤트나 DB 저장 없음(1차 범위, 설계 문서 §7). */
  let waitingForBot = false;
  let socketDegraded = false;

  let stompClient = null;
  let subscription = null;
  let reconnectTimer = null;

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
     * 새 방의 첫 인사도 일반 봇 답변과 같은 대기 상태로 취급한다. 재연결 중 이벤트를 놓쳤어도
     * REST 동기화 결과의 마지막 메시지가 BOT이면 대기를 풀어 중복 질문 전송을 막는다.
     */
    if (room.status === "BOT" && roomMessages.length === 0) waitingForBot = true;
    if (waitingForBot && latestMessage && latestMessage.senderType === "BOT") waitingForBot = false;
    if (room.status !== "BOT") waitingForBot = false;
    statusText.textContent = statusLabel(room);

    const waiting = waitingForBot && room.status === "BOT";
    input.disabled = closed || waiting;
    send.disabled = closed || waiting;
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

  /* 재연결 뒤에는 그 사이 놓친 이벤트가 있을 수 있으므로, 다시 구독하기 전에 REST로 먼저 맞춘다. */
  function resubscribe() {
    if (!currentRoomId) return;
    load().then(function () {
      if (!stompClient || !stompClient.connected || !currentRoomId) return;
      if (subscription) subscription.unsubscribe();
      subscription = stompClient.subscribe(
        ROOM_TOPIC_PREFIX + currentRoomId,
        function (frame) { handleSocketEvent(JSON.parse(frame.body)); }
      );
    });
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
    load();
  }

  function disconnectSocket() {
    if (reconnectTimer) window.clearTimeout(reconnectTimer);
    reconnectTimer = null;
    if (subscription) { try { subscription.unsubscribe(); } catch (error) { /* 이미 끊긴 연결. */ } }
    subscription = null;
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
      /* 방이 여전히 BOT이면 봇 응답이 WebSocket으로 올 때까지 기다린다(설계 문서 §7). */
      waitingForBot = view.room.status === "BOT";
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
