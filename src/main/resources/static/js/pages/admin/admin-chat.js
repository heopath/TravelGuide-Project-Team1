/* 관리자 상담 채팅
 *
 * 맡지 않은 상담에는 답할 수 없다. 아무나 끼어들면 손님은 여러 사람이 번갈아 답하는 것을
 * 보게 되고 누가 맡았는지도 흐려진다. `내가 응대하기`를 눌러야 입력창이 열린다.
 *
 * 갱신은 폴링이다. WebSocket은 서버 구성이 늘고, 이 화면은 관리자 몇 명만 여는 자리라
 * 폴링으로 충분하다. 대화창을 열어 둔 동안만 돌고 닫으면 멈춘다.
 *
 * 챗봇은 아직 붙지 않았다. 방 상태 BOT과 보낸이 BOT은 서버가 처음부터 다루므로, 봇이
 * 붙으면 이 화면은 고칠 것이 없다.
 */
(function () {
  "use strict";

  /* 대화창을 열어 둔 동안의 갱신 주기. 사람이 타자하는 속도를 생각하면 3초로 충분하다. */
  const POLL_INTERVAL_MS = 3000;
  const ROOM_LIMIT = 30;

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
  let timer = null;

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
      startPolling();
    } catch (error) {
      messageEmpty.hidden = false;
      messageEmpty.textContent = error.message || "대화를 불러오지 못했어요.";
    }
  }

  /* 대화창을 열어 둔 동안만 돈다. 다른 화면으로 옮기면 멈춘다. */
  function startPolling() {
    stopPolling();
    timer = window.setInterval(async function () {
      if (!openRoomId || panel.hidden) return stopPolling();
      try {
        renderThread(await request(`/api/v1/admin/support-chats/${openRoomId}`));
      } catch (error) {
        stopPolling();
      }
    }, POLL_INTERVAL_MS);
  }

  function stopPolling() {
    if (timer) window.clearInterval(timer);
    timer = null;
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
