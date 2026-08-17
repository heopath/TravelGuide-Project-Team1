/* 마이페이지 · 상담 채팅 (손님 쪽)
 *
 * 1:1 문의와 나란히 있지만 성격이 다르다. 문의는 남기고 답변을 기다리는 것이고,
 * 상담은 지금 붙어서 주고받는 것이다.
 *
 * 열린 상담은 하나뿐이다. 서버가 손님당 하나로 제한하고 있어, 이미 열려 있으면 그 방을 준다.
 *
 * 갱신은 폴링이다. 상담 탭을 열어 둔 동안만 돌고, 다른 탭으로 옮기면 멈춘다.
 * 대기 화면에서 폴링 주기가 곧 체감이라는 것을 봤는데, 여기는 사람이 답하는 자리라
 * 3초면 충분하다.
 */
const POLL_INTERVAL_MS = 3000;

const statusLabels = {
  BOT: "상담원을 연결하고 있어요",
  WAITING: "담당자를 기다리는 중이에요",
  ASSIGNED: "담당자가 응대 중이에요",
  CLOSED: "종료된 상담이에요",
};
const senderLabels = { USER: "나", BOT: "상담봇", ADMIN: "담당자" };

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

  let timer = null;
  let opened = false;

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

  function render(view) {
    opened = true;
    chat.hidden = false;
    start.hidden = true;

    const room = view.room;
    const closed = room.status === "CLOSED";
    statusText.textContent = statusLabels[room.status] || room.status;

    input.disabled = closed;
    send.disabled = closed;
    input.placeholder = closed ? "종료된 상담이에요" : "궁금한 내용을 입력하세요";

    messages.replaceChildren();
    (view.messages || []).forEach(function (message) {
      messages.appendChild(messageRow(message));
    });
    messages.scrollTop = messages.scrollHeight;

    if (closed) stopPolling();
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

  function startPolling() {
    stopPolling();
    timer = window.setInterval(async function () {
      /* 탭을 벗어나면 멈춘다. 보지 않는 화면 때문에 계속 부를 이유가 없다. */
      if (panel.hidden || !opened) return stopPolling();
      try {
        render(await request("/api/v1/support/chat"));
      } catch (error) {
        stopPolling();
      }
    }, POLL_INTERVAL_MS);
  }

  function stopPolling() {
    if (timer) window.clearInterval(timer);
    timer = null;
  }

  openButton.addEventListener("click", async function () {
    openButton.disabled = true;
    try {
      render(await request("/api/v1/support/chat", { method: "POST" }));
      startPolling();
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
      render(await request("/api/v1/support/chat/messages", {
        method: "POST",
        body: JSON.stringify({ content }),
      }));
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
    load().then(function () { if (opened) startPolling(); });
  });

  return { load: load, stopPolling: stopPolling };
}
