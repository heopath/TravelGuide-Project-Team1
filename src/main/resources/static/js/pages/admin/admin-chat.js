/* 관리자 상담 채팅
 *
 * 맡지 않은 상담에는 답할 수 없다. 아무나 끼어들면 손님은 여러 사람이 번갈아 답하는 것을
 * 보게 되고 누가 맡았는지도 흐려진다. `내가 응대하기`를 눌러야 입력창이 열린다.
 *
 * 갱신은 WebSocket이다(설계 문서 docs/support-chat-ai-websocket.md). 보내는 것(답장·맡기·종료)은
 * 그대로 REST POST고, 열어 둔 대화방의 새 메시지·상태 변경을 "받는" 것만 STOMP 구독으로
 * 바뀌었다 — 폴링은 더 이상 쓰지 않는다. `/webjars/sockjs-client`·`/webjars/stomp-websocket`가
 * 없는 환경(정적 미리보기, 테스트)에서는 조용히 건너뛴다 — REST 흐름은 그대로 된다.
 *
 * 챗봇이 붙었다. 방 상태 BOT과 보낸이 BOT은 서버가 처음부터 다뤘으므로 이 화면은 고칠 것이
 * 없었다 — 그대로다.
 */
(function () {
  "use strict";

  const ROOM_LIMIT = 30;
  const SOCKET_ENDPOINT = "/ws/support-chat";
  const ROOM_TOPIC_PREFIX = "/topic/support-chat/rooms/";
  const RECONNECT_DELAY_MS = 3000;

  function readCsrfCookie() {
    const match = document.cookie.match(/(?:^|;\s*)CSRF-TOKEN=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : "";
  }

  function socketAvailable() {
    return typeof window.SockJS === "function"
      && typeof window.Stomp === "object" && typeof window.Stomp.over === "function";
  }

  const statusLabels = {
    BOT: "봇 응대 중",
    WAITING: "대기",
    ASSIGNED: "응대 중",
    CLOSED: "종료",
  };
  const senderLabels = { USER: "손님", BOT: "봇", ADMIN: "관리자" };

  const panel = document.querySelector('[data-admin-section="chat"]');
  if (!panel) return;

  const $ = (id) => document.getElementById(id);
  const roomList = $("chatRoomList");
  const roomEmpty = $("chatRoomEmpty");
  const search = $("chatSearch");
  const messages = $("chatMessages");
  const messageEmpty = $("chatMessageEmpty");
  const peerName = $("chatPeerName");
  const peerMeta = $("chatPeerMeta");
  const takeover = $("chatTakeover");
  const closeButton = $("chatClose");
  const composer = $("chatComposer");
  const input = $("chatInput");
  const send = $("chatSend");
  if (!roomList || !messages || !composer) return;

  let statusFilter = "";
  let keyword = "";
  let openRoomId = null;
  let currentRoom = null;

  let stompClient = null;
  let subscription = null;
  let reconnectTimer = null;

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

  function time(value) {
    if (!value) return "";
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return "";
    return new Intl.DateTimeFormat("ko-KR", {
      month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit",
    }).format(parsed);
  }

  /* ── 방 목록 ── */

  function roomRow(room) {
    const item = document.createElement("button");
    item.type = "button";
    item.className = "admin-chat-room" + (room.supportChatRoomId === openRoomId ? " on" : "");
    item.dataset.chatRoom = String(room.supportChatRoomId);

    const head = document.createElement("strong");
    head.textContent = room.userNickname || `#${room.userId}`;

    const status = document.createElement("em");
    status.dataset.chatRoomStatus = "";
    status.textContent = statusLabels[room.status] || room.status;

    const preview = document.createElement("small");
    /* 아직 아무 말도 오가지 않은 방을 빈칸으로 두지 않는다. */
    preview.textContent = room.lastMessagePreview || "아직 대화가 없어요";

    const when = document.createElement("span");
    when.className = "admin-chat-room-time";
    when.textContent = time(room.lastMessageAt || room.createdAt);

    item.append(head, status, preview, when);
    return item;
  }

  async function loadRooms() {
    const query = new URLSearchParams({ limit: String(ROOM_LIMIT) });
    if (statusFilter) query.set("status", statusFilter);
    if (keyword) query.set("keyword", keyword);
    try {
      const rooms = await request(`/api/v1/admin/support-chats?${query}`);
      roomList.replaceChildren();
      if (!rooms || !rooms.length) {
        roomEmpty.hidden = false;
        roomEmpty.textContent = statusFilter || keyword
          ? "조건에 맞는 상담이 없어요."
          : "아직 들어온 상담이 없어요.";
        return;
      }
      roomEmpty.hidden = true;
      rooms.forEach(function (room) { roomList.appendChild(roomRow(room)); });
    } catch (error) {
      roomEmpty.hidden = false;
      roomEmpty.textContent = error.message || "상담 목록을 불러오지 못했어요.";
    }
  }

  /* ── 대화 ── */

  function messageRow(message) {
    const item = document.createElement("div");
    const kind = String(message.senderType || "").toLowerCase();
    item.className = "admin-chat-message " + kind;
    item.dataset.chatMessage = String(message.supportChatMessageId);

    const who = document.createElement("em");
    who.textContent = senderLabels[message.senderType] || message.senderType;
    if (message.senderNickname) who.textContent += ` · ${message.senderNickname}`;

    const body = document.createElement("p");
    body.textContent = message.content;

    const when = document.createElement("small");
    when.textContent = time(message.createdAt);

    item.append(who, body, when);
    return item;
  }

  /**
   * 대화창을 그린다.
   *
   * <p>내가 맡은 방일 때만 입력창을 연다. 서버도 같은 검사를 하지만, 눌러 보고 나서
   * 거부당하면 이유를 알기 어렵다.
   */
  function renderThread(view) {
    currentRoom = view.room;
    const room = view.room;
    /* 내가 맡은 방인지는 서버가 계산해 내려준다. 화면이 자기 번호를 들고 비교하지 않는다. */
    const mine = room.assignedToMe === true;

    peerName.textContent = room.userNickname || `#${room.userId}`;
    const meta = [statusLabels[room.status] || room.status];
    if (room.userEmail) meta.push(room.userEmail);
    if (room.assignedAdminNickname) meta.push(`담당 ${room.assignedAdminNickname}`);
    peerMeta.textContent = meta.join(" · ");

    const closed = room.status === "CLOSED";
    takeover.disabled = closed || room.status === "ASSIGNED";
    takeover.textContent = mine ? "내가 응대 중" : "내가 응대하기";
    closeButton.disabled = closed;

    input.disabled = !mine;
    send.disabled = !mine;
    input.placeholder = closed
      ? "종료된 상담이에요"
      : (mine ? "답장을 입력하세요" : "내가 응대하기를 누르면 답할 수 있어요");

    messages.replaceChildren();
    if (!view.messages || !view.messages.length) {
      messageEmpty.hidden = false;
      messageEmpty.textContent = "아직 오간 말이 없어요.";
      return;
    }
    messageEmpty.hidden = true;
    view.messages.forEach(function (message) { messages.appendChild(messageRow(message)); });
    /* 새 말이 아래에 쌓이므로 항상 끝을 보여준다. */
    messages.scrollTop = messages.scrollHeight;
  }

  async function openRoom(roomId) {
    openRoomId = Number(roomId);
    try {
      renderThread(await request(`/api/v1/admin/support-chats/${roomId}`));
      await loadRooms();
      connectSocket();
      resubscribe();
    } catch (error) {
      messageEmpty.hidden = false;
      messageEmpty.textContent = error.message || "대화를 불러오지 못했어요.";
    }
  }

  /* ── WebSocket 수신 ── */

  function connectSocket() {
    if (!socketAvailable() || stompClient) return;
    let socket;
    try {
      socket = new window.SockJS(SOCKET_ENDPOINT);
    } catch (error) {
      return; /* 정적 미리보기 등 SockJS가 실제로 동작하지 않는 환경. REST만으로 계속 쓸 수 있다. */
    }
    const client = window.Stomp.over(socket);
    client.debug = function () {};
    stompClient = client;
    client.connect({ "X-CSRF-TOKEN": readCsrfCookie() }, onSocketConnected, onSocketDown);
  }

  function onSocketConnected() {
    resubscribe();
  }

  function onSocketDown() {
    stompClient = null;
    subscription = null;
    scheduleReconnect();
  }

  function scheduleReconnect() {
    if (reconnectTimer || panel.hidden || !openRoomId) return;
    reconnectTimer = window.setTimeout(function () {
      reconnectTimer = null;
      connectSocket();
    }, RECONNECT_DELAY_MS);
  }

  /* 재연결 뒤에는 그 사이 놓친 이벤트가 있을 수 있으므로, 다시 구독하기 전에 REST로 먼저 맞춘다. */
  function resubscribe() {
    if (!openRoomId || !stompClient || !stompClient.connected) return;
    const roomId = openRoomId;
    request(`/api/v1/admin/support-chats/${roomId}`)
      .then(function (view) {
        if (openRoomId !== roomId) return; /* 그 사이 다른 방을 열었다. */
        renderThread(view);
        if (subscription) subscription.unsubscribe();
        subscription = stompClient.subscribe(
          ROOM_TOPIC_PREFIX + roomId,
          function (frame) { handleSocketEvent(roomId, JSON.parse(frame.body)); }
        );
      })
      .catch(function () { /* 다음 이벤트나 재연결에서 다시 시도된다. */ });
  }

  function handleSocketEvent(roomId, event) {
    if (roomId !== openRoomId) return; /* 이미 다른 방으로 옮겼다. */
    request(`/api/v1/admin/support-chats/${roomId}`)
      .then(renderThread)
      .catch(function () { /* 무시 — 다음 이벤트가 다시 맞춰 준다. */ });
    if (event.type === "MESSAGE") loadRooms();
  }

  function stopPolling() {
    if (reconnectTimer) window.clearTimeout(reconnectTimer);
    reconnectTimer = null;
    if (subscription) { try { subscription.unsubscribe(); } catch (error) { /* 이미 끊긴 연결. */ } }
    subscription = null;
    if (stompClient) { try { stompClient.disconnect(); } catch (error) { /* 이미 끊긴 연결. */ } }
    stompClient = null;
  }

  async function act(url, failMessage) {
    try {
      renderThread(await request(url, { method: "POST" }));
      await loadRooms();
    } catch (error) {
      messageEmpty.hidden = false;
      messageEmpty.textContent = error.message || failMessage;
    }
  }

  /* ── 이벤트 ── */

  roomList.addEventListener("click", function (event) {
    const button = event.target.closest("[data-chat-room]");
    if (!button) return;
    openRoom(button.dataset.chatRoom);
  });

  takeover.addEventListener("click", function () {
    if (!openRoomId) return;
    act(`/api/v1/admin/support-chats/${openRoomId}/takeover`, "상담을 맡지 못했어요.");
  });

  closeButton.addEventListener("click", function () {
    if (!openRoomId) return;
    if (!window.confirm("이 상담을 종료할까요? 손님은 더 이상 이 창으로 말할 수 없어요.")) return;
    act(`/api/v1/admin/support-chats/${openRoomId}/close`, "상담을 종료하지 못했어요.");
  });

  composer.addEventListener("submit", async function (event) {
    event.preventDefault();
    const content = input.value.trim();
    if (!content || !openRoomId) return;
    send.disabled = true;
    try {
      renderThread(await request(`/api/v1/admin/support-chats/${openRoomId}/messages`, {
        method: "POST",
        body: JSON.stringify({ content }),
      }));
      input.value = "";
      await loadRooms();
    } catch (error) {
      messageEmpty.hidden = false;
      messageEmpty.textContent = error.message || "답장을 보내지 못했어요.";
    } finally {
      send.disabled = input.disabled;
      input.focus();
    }
  });

  panel.querySelectorAll("[data-chat-filter]").forEach(function (button) {
    button.addEventListener("click", function () {
      panel.querySelectorAll("[data-chat-filter]").forEach(function (chip) {
        chip.classList.toggle("on", chip === button);
      });
      statusFilter = button.dataset.chatFilter || "";
      loadRooms();
    });
  });

  if (search) {
    search.addEventListener("keydown", function (event) {
      if (event.key !== "Enter") return;
      event.preventDefault();
      keyword = search.value.trim();
      loadRooms();
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", loadRooms);
  } else {
    loadRooms();
  }

  window.__adminChat = { loadRooms: loadRooms, openRoom: openRoom, stopPolling: stopPolling };
})();
