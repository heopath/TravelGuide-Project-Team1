/* 관리자 상담 채팅
 *
 * 맡지 않은 상담에는 답할 수 없다. 아무나 끼어들면 손님은 여러 사람이 번갈아 답하는 것을
 * 보게 되고 누가 맡았는지도 흐려진다. `내가 응대하기`를 눌러야 입력창이 열린다.
 *
 * 갱신은 WebSocket이다(설계 문서 docs/support-chat-ai-websocket.md). 보내는 것(답장·맡기·종료)은
 * 그대로 REST POST고, 새 메시지·상태 변경을 "받는" 것만 STOMP 구독이다. 연결돼 있는 동안은
 * 폴링을 쓰지 않지만, `/webjars/sockjs-client`·`/webjars/stomp-websocket`가 없거나(정적
 * 미리보기, 테스트) 핸드셰이크·nginx Upgrade 설정이 실패해 연결이 계속 안 되면, 새 상담도
 * 대기열에 못 뜨고 비동기로 저장되는 봇 답변도 알 길이 없어진다. 그래서 연결돼 있지 않은
 * 동안에는 제한적으로 REST 폴링을 대신 돌린다(연결되면 자동으로 멈춘다).
 *
 * 구독은 두 갈래다. 열어 둔 대화방 토픽은 그 방의 메시지·상태를 받고, 관리자 대기열
 * 토픽(`/topic/support-chat/admin/rooms`)은 "목록이 달라졌다"를 받는다. 대기열 토픽이 없으면
 * 새 상담이 들어오거나 다른 방이 대기로 넘어가도 새로고침 전까지 목록이 멈춰 있다.
 *
 * 챗봇이 붙었다. 방 상태 BOT과 보낸이 BOT은 서버가 처음부터 다뤘으므로 표시 규칙은 그대로다.
 */
(function () {
  "use strict";

  const ROOM_LIMIT = 30;
  const SOCKET_ENDPOINT = "/ws/support-chat";
  const ROOM_TOPIC_PREFIX = "/topic/support-chat/rooms/";
  /* 방 목록에 영향을 주는 변화(새 상담, 상태 전환, 마지막 메시지)를 모아 받는 자리. */
  const ADMIN_ROOMS_TOPIC = "/topic/support-chat/admin/rooms";
  /* 서버가 복구 가능한 오류를 보내는 본인 전용 큐(설계 문서 §3). */
  const ERROR_QUEUE = "/user/queue/support-chat/errors";
  const RECONNECT_DELAY_MS = 3000;
  /* 한 방에서 말이 몇 마디 연달아 오가도 목록 조회는 한 번만 나가게 묶는다. */
  const ROOM_LIST_REFRESH_DELAY_MS = 300;
  /* 연결이 안 되는 동안만 도는 대체 경로. WebSocket이 정상이면 이 주기는 의미가 없다. */
  const FALLBACK_POLL_INTERVAL_MS = 5000;

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
  const searchForm = panel.querySelector("[data-chat-search-form]");
  const searchClear = panel.querySelector("[data-chat-search-clear]");
  const refresh = panel.querySelector("[data-chat-refresh]");
  const roomCount = panel.querySelector("[data-chat-count]");
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
  let adminSubscription = null;
  let errorSubscription = null;
  let reconnectTimer = null;
  let roomListTimer = null;
  let pollTimer = null;
  /*
   * loadRooms()/syncThread()를 부를 때마다 하나씩 늘린다. 응답이 도착했을 때 이 값과
   * 다르면(그 사이 더 최신 조회가 시작됐다는 뜻) 화면에 반영하지 않고 버린다 — 느리게 온
   * 옛 응답이 빠른 새 응답을 뒤늦게 덮어써 목록·대화창이 다시 낡은 상태로 되돌아가는 것을
   * 막는다. 구독을 REST보다 먼저 거는 것과는 별개의 문제다(그건 "이벤트를 놓치지 않는다",
   * 이건 "응답이 순서대로 안 와도 최신이 이긴다").
   */
  let roomsGeneration = 0;
  let threadGeneration = 0;

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
    const generation = ++roomsGeneration;
    const query = new URLSearchParams({ limit: String(ROOM_LIMIT) });
    if (statusFilter) query.set("status", statusFilter);
    if (keyword) query.set("keyword", keyword);
    try {
      const rooms = await request(`/api/v1/admin/support-chats?${query}`);
      if (generation !== roomsGeneration) return; /* 그 사이 더 최신 조회가 시작됐다 — 낡은 응답은 버린다. */
      roomList.replaceChildren();
      if (roomCount) roomCount.textContent = `조회 결과 ${(rooms || []).length.toLocaleString("ko-KR")}건`;
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
      if (generation !== roomsGeneration) return;
      if (roomCount) roomCount.textContent = "조회하지 못함";
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
    const targetRoomId = Number(roomId);
    openRoomId = targetRoomId;
    connectSocket();
    ensureFallbackPolling();
    /* 먼저 구독하고 그다음에 읽는다 — 읽는 사이에 저장된 메시지를 놓치지 않도록. */
    subscribeRoom();
    try {
      await syncThread(targetRoomId);
      await loadRooms();
    } catch (error) {
      if (openRoomId !== targetRoomId) return; /* 그 사이 다른 방으로 옮겼다 — 낡은 오류다. */
      messageEmpty.hidden = false;
      messageEmpty.textContent = error.message || "대화를 불러오지 못했어요.";
    }
  }

  /* ── WebSocket 수신 ── */

  function connectSocket() {
    if (!socketAvailable() || stompClient) return;
    /* 방을 열지 않아도 연결한다 — 대기열 갱신은 어떤 방을 보고 있는지와 무관하다. */
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
    /* 끊긴 연결의 구독은 남겨 두면 재연결 뒤 다시 걸지 않는다. */
    adminSubscription = null;
    errorSubscription = null;
    scheduleReconnect();
  }

  /* 방을 열어 두지 않아도 다시 붙는다 — 대기열 갱신이 연결에 달려 있다. */
  function scheduleReconnect() {
    if (reconnectTimer) return;
    reconnectTimer = window.setTimeout(function () {
      reconnectTimer = null;
      connectSocket();
    }, RECONNECT_DELAY_MS);
  }

  /**
   * 연결이 서면 필요한 구독을 모두 건다.
   *
   * <p>열어 둔 방이 있으면 그 방 토픽을 먼저 걸고 나서 REST로 대화를 다시 읽는다. 순서가
   * 반대면 REST 응답과 SUBSCRIBE 사이에 저장된 메시지를 아무도 받지 못한다.
   */
  function resubscribe() {
    subscribeAdminRooms();
    subscribeErrors();
    subscribeRoom();
    if (openRoomId) syncThread(openRoomId).catch(function () { /* 다음 이벤트나 재연결에서 다시 시도된다. */ });
    loadRooms();
  }

  function subscribeAdminRooms() {
    if (adminSubscription || !stompClient || !stompClient.connected) return;
    adminSubscription = stompClient.subscribe(ADMIN_ROOMS_TOPIC, refreshRoomsSoon);
  }

  /* 서버가 보내는 복구 가능한 오류를 받을 자리(설계 문서 §3). 없으면 오류가 조용히 사라진다. */
  function subscribeErrors() {
    if (errorSubscription || !stompClient || !stompClient.connected) return;
    errorSubscription = stompClient.subscribe(ERROR_QUEUE, function (frame) {
      const error = JSON.parse(frame.body);
      if (!error) return;
      messageEmpty.hidden = false;
      messageEmpty.textContent = error.message || "실시간 갱신에 문제가 생겼어요.";
    });
  }

  function subscribeRoom() {
    if (!openRoomId || !stompClient || !stompClient.connected) return;
    const roomId = openRoomId;
    if (subscription) { try { subscription.unsubscribe(); } catch (error) { /* 이미 끊긴 연결. */ } }
    subscription = stompClient.subscribe(
      ROOM_TOPIC_PREFIX + roomId,
      function () { handleRoomEvent(roomId); }
    );
  }

  /**
   * 지금 열어 둔 방을 REST로 다시 읽어 그린다.
   *
   * <p>여러 경로(재구독, 방 토픽 이벤트, 폴백 폴링)가 동시에 이 방을 다시 읽을 수 있다.
   * 응답이 도착한 시점에 이미 다른 방으로 옮겼거나, 그 사이 더 최신 조회가 시작됐으면
   * (느리게 온 응답이라는 뜻) 반영하지 않는다.
   */
  function syncThread(roomId) {
    const generation = ++threadGeneration;
    return request(`/api/v1/admin/support-chats/${roomId}`).then(function (view) {
      if (roomId !== openRoomId || generation !== threadGeneration) return;
      renderThread(view);
    });
  }

  function handleRoomEvent(roomId) {
    if (roomId !== openRoomId) return; /* 이미 다른 방으로 옮겼다. */
    syncThread(roomId).catch(function () { /* 무시 — 다음 이벤트가 다시 맞춰 준다. */ });
  }

  function refreshRoomsSoon() {
    if (roomListTimer) return;
    roomListTimer = window.setTimeout(function () {
      roomListTimer = null;
      loadRooms();
    }, ROOM_LIST_REFRESH_DELAY_MS);
  }

  /**
   * WebSocket이 안 붙어 있는 동안만 도는 대체 경로.
   *
   * <p>스크립트가 아예 없거나(정적 미리보기) 핸드셰이크·nginx Upgrade 설정이 실패해 연결이
   * 계속 안 되면, 새 상담이 대기열에 뜨지도 않고 비동기로 저장되는 봇 답변도 알 길이 없다.
   * 매 틱마다 연결 상태를 다시 확인하므로 시작·중지를 이벤트마다 정교하게 맞출 필요 없이,
   * 연결되면 스스로 조용해진다.
   */
  function ensureFallbackPolling() {
    if (pollTimer) return;
    pollTimer = window.setInterval(function () {
      if (stompClient && stompClient.connected) return;
      loadRooms();
      if (openRoomId) syncThread(openRoomId).catch(function () { /* 다음 폴링에서 다시 시도된다. */ });
    }, FALLBACK_POLL_INTERVAL_MS);
  }

  function stopFallbackPolling() {
    if (pollTimer) window.clearInterval(pollTimer);
    pollTimer = null;
  }

  function stopPolling() {
    stopFallbackPolling();
    if (reconnectTimer) window.clearTimeout(reconnectTimer);
    reconnectTimer = null;
    if (roomListTimer) window.clearTimeout(roomListTimer);
    roomListTimer = null;
    [subscription, adminSubscription, errorSubscription].forEach(function (each) {
      if (each) { try { each.unsubscribe(); } catch (error) { /* 이미 끊긴 연결. */ } }
    });
    subscription = null;
    adminSubscription = null;
    errorSubscription = null;
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
        const selected = chip === button;
        chip.classList.toggle("on", selected);
        chip.setAttribute("aria-pressed", selected ? "true" : "false");
      });
      statusFilter = button.dataset.chatFilter || "";
      loadRooms();
    });
  });

  searchForm?.addEventListener("submit", function (event) {
    event.preventDefault();
    keyword = search?.value.trim() || "";
    if (searchClear) searchClear.hidden = !keyword;
    loadRooms();
  });
  searchClear?.addEventListener("click", function () {
    keyword = "";
    if (search) search.value = "";
    searchClear.hidden = true;
    loadRooms();
    search?.focus();
  });
  refresh?.addEventListener("click", loadRooms);

  function boot() {
    loadRooms();
    /* 방을 고르기 전에도 연결해 둔다 — 새 상담이 들어오면 바로 목록에 뜨도록. */
    connectSocket();
    ensureFallbackPolling();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }

  window.__adminChat = { loadRooms: loadRooms, openRoom: openRoom, stopPolling: stopPolling };
})();
