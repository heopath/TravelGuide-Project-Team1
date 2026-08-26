/* 관리자 외부 API 키 관리
 *
 * 저장 버튼은 처음에 잠겨 있다. [연결 테스트]가 성공한 값만 저장할 수 있다.
 * 사용량이 차서 급히 키를 바꾸는 상황은 대개 서비스가 이미 반쯤 멈춘 시점이라,
 * 거기서 오타 난 키를 저장하면 멀쩡하던 옛 키까지 잃고 완전히 멈춘다.
 *
 * 화면은 키 전체 값을 절대 받지 않는다. 서버가 내려주는 것은 마스킹된 문자열뿐이다.
 */
(function () {
  "use strict";

  const ENDPOINT = "/api/v1/admin/api-keys";

  const panel = document.querySelector('[data-admin-section="apikeys"]');
  if (!panel) return;

  const list = panel.querySelector("[data-apikey-list]");
  const warning = panel.querySelector("[data-apikey-warning]");
  if (!list) return;

  const SOURCE_LABEL = {
    STORED: "관리자 저장값 사용 중",
    ENV: "서버 환경변수 사용 중",
    NONE: "설정 안 됨"
  };

  /* 테스트를 통과한 입력값. 입력이 바뀌면 지워서 저장 버튼을 다시 잠근다. */
  const verified = Object.create(null);
  let encryptionReady = false;

  const esc = (value) => String(value ?? "").replace(/[&<>"']/g,
    (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char]));

  async function request(path, options) {
    const response = await fetch(ENDPOINT + (path || ""), Object.assign({
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      credentials: "same-origin",
      allMyTripsLoading: false
    }, options || {}));

    const payload = await response.json().catch(function () { return null; });
    if (!response.ok || payload?.success === false) {
      /*
       * 화면 로드 시점의 인증 상태로 미리 판단하지 않고, 401을 받은 뒤에 로그인으로 보낸다.
       * auth-state.js가 응답을 받기 전에는 dataset이 비어 있어 레이스가 생긴다.
       */
      if (response.status === 401) {
        window.location.href = "/auth/login?redirect=" + encodeURIComponent("/admin?panel=apikeys");
      }
      if (response.status === 403) throw new Error("관리자만 변경할 수 있습니다.");
      throw new Error(payload?.message || "요청을 처리하지 못했습니다.");
    }
    return payload?.data ?? payload;
  }

  function updatedAtText(key) {
    if (!key.updatedAt) return "";
    const parsed = new Date(key.updatedAt);
    if (Number.isNaN(parsed.getTime())) return "";
    return "마지막 변경 " + parsed.toLocaleString("ko-KR", {
      year: "2-digit", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit"
    });
  }

  function card(key) {
    const source = String(key.source || "NONE");
    const masked = key.maskedValue ? esc(key.maskedValue) : "없음";
    const stamp = updatedAtText(key);

    return `<article class="admin-apikey-card" data-apikey-card="${esc(key.name)}">
      <div class="admin-apikey-head">
        <h3>${esc(key.label)}</h3>
        <span class="admin-apikey-source ${esc(source.toLowerCase())}">${esc(SOURCE_LABEL[source] || source)}</span>
      </div>
      <p class="admin-apikey-desc">${esc(key.description)}</p>
      <p class="admin-apikey-current">
        현재 키 <strong>${masked}</strong>
        ${stamp ? `<span>${esc(stamp)}</span>` : ""}
      </p>
      <form class="admin-apikey-form" data-apikey-form data-no-global-loading>
        <label>
          새 키
          <input type="password" name="apiKey" data-apikey-input autocomplete="new-password"
                 spellcheck="false" maxlength="500" placeholder="발급받은 키를 붙여 넣으세요" />
        </label>
        <button type="button" class="secondary-button" data-apikey-test>연결 테스트</button>
        <button type="submit" class="primary-button" data-apikey-save disabled>저장</button>
      </form>
      <div class="admin-apikey-actions">
        <button type="button" class="secondary-button" data-apikey-check>지금 키 상태 확인</button>
        ${source === "STORED"
          ? `<button type="button" class="secondary-button" data-apikey-reset>환경변수 값으로 되돌리기</button>`
          : ""}
      </div>
      <p class="admin-apikey-message" data-apikey-message role="status"></p>
    </article>`;
  }

  function render(data) {
    encryptionReady = Boolean(data?.encryptionReady);
    if (warning) warning.hidden = encryptionReady;

    const keys = Array.isArray(data?.keys) ? data.keys : [];
    list.innerHTML = keys.length
      ? keys.map(card).join("")
      : `<p class="admin-empty">관리할 수 있는 키가 없습니다.</p>`;
  }

  function cardOf(element) {
    return element.closest("[data-apikey-card]");
  }

  /**
   * 목록을 다시 그린 뒤 같은 카드를 찾는다.
   *
   * 선택자에 이름을 끼워 넣지 않고 훑는다. 이름은 서버가 정한 값이라 지금은 안전하지만,
   * 선택자 문자열을 조립하는 습관은 값이 하나 바뀌는 순간 조용히 깨진다.
   */
  function findCard(name) {
    return [...list.querySelectorAll("[data-apikey-card]")]
      .find((card) => card.dataset.apikeyCard === name) || null;
  }

  function say(card, text, tone) {
    const message = card.querySelector("[data-apikey-message]");
    if (!message) return;
    message.textContent = text || "";
    message.classList.toggle("error", tone === "error");
    message.classList.toggle("ok", tone === "ok");
  }

  function lockSave(card) {
    const name = card.dataset.apikeyCard;
    delete verified[name];
    const save = card.querySelector("[data-apikey-save]");
    if (save) save.disabled = true;
  }

  async function runTest(card, candidate, checkingCurrent) {
    const name = card.dataset.apikeyCard;
    const buttons = card.querySelectorAll("button");
    buttons.forEach((button) => { button.disabled = true; });
    say(card, "외부 서버에 확인하는 중이에요.");

    try {
      const result = await request("/" + encodeURIComponent(name) + "/test", {
        method: "POST",
        body: JSON.stringify({ apiKey: candidate })
      });

      if (result?.valid) {
        say(card, checkingCurrent ? "지금 키는 정상입니다." : "확인했습니다. 저장할 수 있어요.", "ok");
        if (!checkingCurrent) verified[name] = candidate;
      } else {
        say(card, result?.message || "키를 확인하지 못했습니다.", "error");
        if (!checkingCurrent) delete verified[name];
      }
    } catch (error) {
      say(card, error.message || "확인하지 못했습니다.", "error");
      if (!checkingCurrent) delete verified[name];
    } finally {
      buttons.forEach((button) => { button.disabled = false; });
      const save = card.querySelector("[data-apikey-save]");
      /* 암호화 설정이 없으면 확인에 성공해도 저장은 서버가 거절한다. 미리 잠가 둔다. */
      if (save) save.disabled = !encryptionReady || !verified[name];
    }
  }

  async function save(card) {
    const name = card.dataset.apikeyCard;
    const candidate = verified[name];
    if (!candidate) {
      say(card, "먼저 연결 테스트를 통과해야 저장할 수 있어요.", "error");
      return;
    }

    const buttons = card.querySelectorAll("button");
    buttons.forEach((button) => { button.disabled = true; });
    say(card, "저장하는 중이에요.");

    try {
      await request("/" + encodeURIComponent(name), {
        method: "PUT",
        body: JSON.stringify({ apiKey: candidate })
      });
      delete verified[name];
      await load();
      const refreshed = findCard(name);
      if (refreshed) say(refreshed, "새 키를 저장했습니다. 다음 요청부터 적용됩니다.", "ok");
    } catch (error) {
      buttons.forEach((button) => { button.disabled = false; });
      say(card, error.message || "저장하지 못했습니다.", "error");
    }
  }

  async function reset(card) {
    const name = card.dataset.apikeyCard;
    if (!window.confirm("저장한 키를 지우고 서버 환경변수 값으로 되돌릴까요?")) return;

    try {
      await request("/" + encodeURIComponent(name), { method: "DELETE" });
      delete verified[name];
      await load();
      const refreshed = findCard(name);
      if (refreshed) say(refreshed, "환경변수 값으로 되돌렸습니다.", "ok");
    } catch (error) {
      say(card, error.message || "되돌리지 못했습니다.", "error");
    }
  }

  list.addEventListener("click", function (event) {
    const target = event.target;
    const card = cardOf(target);
    if (!card) return;

    if (target.closest("[data-apikey-test]")) {
      const input = card.querySelector("[data-apikey-input]");
      const value = input ? input.value.trim() : "";
      if (!value) {
        say(card, "확인할 키를 입력해 주세요.", "error");
        input?.focus();
        return;
      }
      runTest(card, value, false);
      return;
    }

    if (target.closest("[data-apikey-check]")) {
      /* 값을 비워 보내면 서버가 "지금 쓰이는 키"로 확인한다. */
      runTest(card, "", true);
      return;
    }

    if (target.closest("[data-apikey-reset]")) reset(card);
  });

  list.addEventListener("input", function (event) {
    if (!event.target.matches("[data-apikey-input]")) return;
    const card = cardOf(event.target);
    if (!card) return;
    lockSave(card);
    say(card, "");
  });

  list.addEventListener("submit", function (event) {
    if (!event.target.matches("[data-apikey-form]")) return;
    event.preventDefault();
    const card = cardOf(event.target);
    if (card) save(card);
  });

  async function load() {
    try {
      render(await request());
    } catch (error) {
      list.innerHTML = `<p class="admin-empty">${esc(error.message || "키 목록을 불러오지 못했습니다.")}</p>`;
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", load);
  } else {
    load();
  }

  window.__adminApiKeys = { load: load };
})();
