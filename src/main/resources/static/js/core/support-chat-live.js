/* 상담 채팅 공통 수신 연결.
 *
 * WebSocket(STOMP)을 우선 사용하고, 연결되지 않은 동안에만 3초 REST 폴백을 호출한다.
 * 마이페이지 고객센터와 마이티가 같은 연결·복구 규칙을 공유하도록 UI와 분리했다.
 */
(function () {
  "use strict";

  const SOCKET_ENDPOINT = "/ws/support-chat";
  const ROOM_TOPIC_PREFIX = "/topic/support-chat/rooms/";
  const ERROR_QUEUE = "/user/queue/support-chat/errors";
  const RECONNECT_DELAY_MS = 3000;
  const FALLBACK_POLL_INTERVAL_MS = 3000;
  const SOCK_JS = "/webjars/sockjs-client/1.5.1/sockjs.min.js";
  const STOMP_JS = "/webjars/stomp-websocket/2.3.4/stomp.min.js";

  let dependencyPromise = null;

  function available() {
    return typeof window.SockJS === "function"
      && typeof window.Stomp === "object"
      && typeof window.Stomp.over === "function";
  }

  function loadScript(source) {
    return new Promise(function (resolve, reject) {
      const existing = document.querySelector(`script[src="${source}"]`);
      if (existing) {
        if (existing.dataset.loaded === "true") resolve();
        else {
          existing.addEventListener("load", resolve, { once: true });
          existing.addEventListener("error", reject, { once: true });
        }
        return;
      }
      const script = document.createElement("script");
      script.src = source;
      script.addEventListener("load", function () {
        script.dataset.loaded = "true";
        resolve();
      }, { once: true });
      script.addEventListener("error", reject, { once: true });
      document.head.appendChild(script);
    });
  }

  function ensureDependencies() {
    if (available()) return Promise.resolve();
    if (!dependencyPromise) {
      dependencyPromise = loadScript(SOCK_JS)
        .then(function () { return loadScript(STOMP_JS); })
        .catch(function (error) {
          dependencyPromise = null;
          throw error;
        });
    }
    return dependencyPromise;
  }

  function csrfToken() {
    const match = document.cookie.match(/(?:^|;\s*)CSRF-TOKEN=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : "";
  }

  function parse(frame) {
    try { return JSON.parse(frame.body); }
    catch (error) { return null; }
  }

  function create(options) {
    const settings = options || {};
    let active = false;
    let roomId = null;
    let client = null;
    let roomSubscription = null;
    let errorSubscription = null;
    let reconnectTimer = null;
    let pollTimer = null;
    let connecting = false;

    function connected() {
      return Boolean(client && client.connected);
    }

    function notifyConnection(value) {
      if (typeof settings.onConnectionChange === "function") {
        settings.onConnectionChange(value);
      }
    }

    function pollWhenDisconnected() {
      if (!active || connected() || typeof settings.onFallbackPoll !== "function") return;
      settings.onFallbackPoll();
    }

    function startPolling() {
      if (pollTimer) return;
      pollTimer = window.setInterval(pollWhenDisconnected, FALLBACK_POLL_INTERVAL_MS);
    }

    function clearSubscriptions() {
      if (roomSubscription) {
        try { roomSubscription.unsubscribe(); } catch (error) { /* 이미 끊긴 연결. */ }
      }
      if (errorSubscription) {
        try { errorSubscription.unsubscribe(); } catch (error) { /* 이미 끊긴 연결. */ }
      }
      roomSubscription = null;
      errorSubscription = null;
    }

    function subscribe() {
      if (!active || !roomId || !connected()) return;
      clearSubscriptions();
      roomSubscription = client.subscribe(ROOM_TOPIC_PREFIX + roomId, function (frame) {
        const event = parse(frame);
        if (event && typeof settings.onEvent === "function") settings.onEvent(event);
      });
      errorSubscription = client.subscribe(ERROR_QUEUE, function (frame) {
        const error = parse(frame);
        if (error && typeof settings.onError === "function") settings.onError(error);
      });
      /* 구독을 먼저 건 뒤 REST로 동기화해 구독 직전의 메시지도 놓치지 않는다. */
      if (typeof settings.onConnected === "function") settings.onConnected();
    }

    function scheduleReconnect() {
      if (!active || reconnectTimer) return;
      reconnectTimer = window.setTimeout(function () {
        reconnectTimer = null;
        connect();
      }, RECONNECT_DELAY_MS);
    }

    function down() {
      clearSubscriptions();
      client = null;
      connecting = false;
      notifyConnection(false);
      scheduleReconnect();
    }

    function connect() {
      if (!active || !roomId || connected() || connecting) return;
      connecting = true;
      ensureDependencies().then(function () {
        if (!active || !roomId || connected()) {
          connecting = false;
          return;
        }
        let socket;
        try { socket = new window.SockJS(SOCKET_ENDPOINT); }
        catch (error) { down(); return; }
        client = window.Stomp.over(socket);
        client.debug = function () {};
        client.connect({ "X-CSRF-TOKEN": csrfToken() }, function () {
          connecting = false;
          notifyConnection(true);
          subscribe();
        }, down);
      }).catch(down);
    }

    function setRoom(nextRoomId) {
      const normalized = nextRoomId == null ? null : String(nextRoomId);
      if (roomId === normalized) return;
      roomId = normalized;
      if (connected()) subscribe();
      else connect();
    }

    function start(nextRoomId) {
      active = true;
      startPolling();
      if (nextRoomId !== undefined) setRoom(nextRoomId);
      connect();
    }

    function stop() {
      active = false;
      if (pollTimer) window.clearInterval(pollTimer);
      if (reconnectTimer) window.clearTimeout(reconnectTimer);
      pollTimer = null;
      reconnectTimer = null;
      clearSubscriptions();
      if (client) {
        try { client.disconnect(); } catch (error) { /* 이미 끊긴 연결. */ }
      }
      client = null;
      connecting = false;
      notifyConnection(false);
    }

    return { start, stop, setRoom, isConnected: connected };
  }

  window.AllMyTripsSupportChatLive = { create };
})();
