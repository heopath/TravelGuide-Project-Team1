/* 관리자 티켓 검표
 *
 * 실패도 정상 결과다. 없는 코드나 이미 쓴 티켓은 오류가 아니라 검표가 답해야 할 상황이라,
 * 서버가 200에 결과를 담아 준다. 화면도 오류 자리가 아니라 결과 자리에 보여준다.
 * 현장에서는 "왜 안 되는지"가 가장 중요하다.
 *
 * 확인한 뒤 입력창을 비우고 다시 포커스를 준다. 줄 서 있는 손님을 연달아 처리하는 화면이라
 * 매번 마우스를 잡게 하면 안 된다.
 */
(function () {
  "use strict";

  const LOG_SIZE = 30;

  const resultLabels = {
    SUCCESS: "입장",
    NOT_FOUND: "없는 코드",
    ALREADY_USED: "사용됨",
    CANCELLED: "취소됨",
    EXPIRED: "기간 밖",
  };
  const channelLabels = {
    ADMIN_WEB: "수동 입력",
    MOCK_SCANNER: "QR 스캔",
  };

  const panel = document.querySelector('[data-admin-section="validation"]');
  if (!panel) return;

  const form = panel.querySelector("[data-validation-form]");
  const token = panel.querySelector("[data-validation-token]");
  const submit = panel.querySelector("[data-validation-submit]");
  const resultBox = panel.querySelector("[data-validation-result-box]");
  const filter = panel.querySelector("[data-validation-filter]");
  const refresh = panel.querySelector("[data-validation-refresh]");
  const count = panel.querySelector("[data-validation-count]");
  const list = panel.querySelector("[data-validation-list]");
  const empty = panel.querySelector("[data-validation-empty]");
  if (!form || !token || !list || !empty) return;

  let resultFilter = "";
  let logLoadSequence = 0;
  let validating = false;

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

  function dateTime(value) {
    if (!value) return "—";
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return "—";
    return new Intl.DateTimeFormat("ko-KR", {
      month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit",
    }).format(parsed);
  }

  /*
   * 색만으로 구분하지 않는다. 결과 문구가 스스로 무엇인지 말하고, 색은 거들기만 한다.
   * 현장은 화면을 오래 들여다보는 자리가 아니라 흘끗 보는 자리다.
   */
  function showResult(data) {
    resultBox.hidden = false;
    resultBox.className = "admin-scan-result " + (data.admitted ? "ok" : "no");
    resultBox.replaceChildren();

    const headline = document.createElement("strong");
    headline.dataset.validationHeadline = "";
    headline.textContent = (data.admitted ? "입장 가능 · " : "입장 불가 · ")
      + (resultLabels[data.result] || data.result);

    const message = document.createElement("p");
    message.dataset.validationMessage = "";
    message.textContent = data.message || "";

    resultBox.append(headline, message);

    if (data.ticketNumber) {
      const detail = document.createElement("small");
      detail.dataset.validationTicket = "";
      const parts = [data.ticketNumber];
      if (data.productName) parts.push(data.productName);
      if (data.optionName) parts.push(data.optionName);
      if (data.usageDate) parts.push(data.usageDate);
      detail.textContent = parts.join(" · ");
      resultBox.appendChild(detail);
    }

    if (data.validFrom || data.validUntil) {
      const validity = document.createElement("small");
      validity.dataset.validationValidity = "";
      validity.textContent = `입장 가능 시간 ${dateTime(data.validFrom)} ~ ${dateTime(data.validUntil)}`;
      resultBox.appendChild(validity);
    }

    const actions = document.createElement("div");
    actions.className = "admin-scan-result-actions";
    const next = document.createElement("button");
    next.type = "button";
    next.className = "admin-chip";
    next.dataset.validationNext = "";
    next.textContent = "다음 손님 검표";
    next.addEventListener("click", resetResult);
    actions.appendChild(next);
    resultBox.appendChild(actions);
  }

  function resetResult() {
    resultBox.hidden = true;
    resultBox.className = "admin-scan-result";
    resultBox.replaceChildren();
    token.value = "";
    token.focus();
  }

  function row(entry) {
    const item = document.createElement("div");
    item.className = "admin-validation-row";
    item.dataset.validationRow = String(entry.ticketValidationLogId);

    const time = document.createElement("span");
    time.textContent = dateTime(entry.validatedAt);

    const result = document.createElement("span");
    result.dataset.validationResultCell = "";
    const resultBadge = document.createElement("span");
    resultBadge.className = "admin-status " + (entry.validationResult === "SUCCESS" ? "confirmed" : "cancelled");
    resultBadge.textContent = resultLabels[entry.validationResult] || entry.validationResult;
    result.appendChild(resultBadge);
    if (entry.failureReason) {
      const failure = document.createElement("small");
      failure.dataset.validationFailureReason = "";
      failure.textContent = entry.failureReason;
      result.appendChild(failure);
    }

    const ticket = document.createElement("span");
    /* 없는 코드로 시도한 기록은 티켓이 없다. 빈칸으로 두지 않고 그 사실을 적는다. */
    if (entry.ticketNumber) {
      const number = document.createElement("strong");
      number.textContent = entry.ticketNumber;
      ticket.appendChild(number);
      if (entry.productName) {
        const product = document.createElement("small");
        product.textContent = entry.productName;
        ticket.appendChild(product);
      }
    } else {
      ticket.textContent = "확인되지 않은 코드";
    }

    const validator = document.createElement("span");
    const channel = document.createElement("strong");
    channel.dataset.validationChannel = "";
    channel.textContent = channelLabels[entry.validationChannel] || entry.validationChannel || "입력 경로 미상";
    const validatorName = document.createElement("small");
    validatorName.textContent = entry.validatorNickname
      || (entry.validatorUserId ? `검표자 #${entry.validatorUserId}` : "검표자 알 수 없음");
    validator.append(channel, validatorName);

    item.append(time, result, ticket, validator);
    return item;
  }

  async function loadLogs() {
    const sequence = ++logLoadSequence;
    list.replaceChildren();
    empty.hidden = false;
    empty.textContent = "검표 기록을 불러오는 중이에요.";
    if (count) count.textContent = "기록을 불러오는 중…";
    const query = new URLSearchParams({ limit: String(LOG_SIZE) });
    if (resultFilter) query.set("result", resultFilter);
    try {
      const entries = await request(`/api/v1/admin/ticket-validations?${query}`);
      if (sequence !== logLoadSequence) return;
      if (count) count.textContent = `최근 기록 ${Number(entries?.length || 0).toLocaleString("ko-KR")}건`;
      if (!entries || !entries.length) {
        empty.textContent = resultFilter ? "조건에 맞는 기록이 없어요." : "아직 검표 기록이 없어요.";
        return;
      }
      empty.hidden = true;
      entries.forEach(function (entry) { list.appendChild(row(entry)); });
    } catch (error) {
      if (sequence !== logLoadSequence) return;
      if (count) count.textContent = "";
      empty.textContent = error.message || "검표 기록을 불러오지 못했어요.";
    }
  }

  async function validate(value) {
    if (validating) return;
    validating = true;
    submit.disabled = true;
    const submitLabel = submit.textContent;
    submit.textContent = "확인 중…";
    try {
      const data = await request("/api/v1/admin/ticket-validations", {
        method: "POST",
        body: JSON.stringify({ token: value }),
      });
      showResult(data);
      await loadLogs();
    } catch (error) {
      /*
       * 여기 오는 것은 검표 결과가 아니라 요청 자체가 실패한 경우다(권한·네트워크).
       * 결과 자리에 같은 모양으로 보여주되 입장 불가로 둔다.
       */
      resultBox.hidden = false;
      resultBox.className = "admin-scan-result no";
      resultBox.replaceChildren();
      const headline = document.createElement("strong");
      headline.dataset.validationHeadline = "";
      headline.textContent = "확인하지 못했어요";
      const message = document.createElement("p");
      message.dataset.validationMessage = "";
      message.textContent = error.message || "요청을 처리하지 못했습니다.";
      resultBox.append(headline, message);
    } finally {
      validating = false;
      submit.disabled = false;
      submit.textContent = submitLabel;
      /* 다음 손님을 바로 받을 수 있게 비우고 포커스를 되돌린다. */
      token.value = "";
      token.focus();
    }
  }

  form.addEventListener("submit", function (event) {
    event.preventDefault();
    const value = token.value.trim();
    if (!value) return;
    validate(value);
  });

  if (filter) {
    filter.addEventListener("click", function (event) {
      const button = event.target.closest("[data-validation-result]");
      if (!button) return;
      resultFilter = button.dataset.validationResult || "";
      filter.querySelectorAll("[data-validation-result]").forEach(function (chip) {
        const selected = chip === button;
        chip.classList.toggle("on", selected);
        chip.setAttribute("aria-pressed", selected ? "true" : "false");
      });
      loadLogs();
    });
    filter.querySelectorAll("[data-validation-result]").forEach(function (chip) {
      chip.setAttribute("aria-pressed", chip.dataset.validationResult === "" ? "true" : "false");
    });
  }

  if (refresh) refresh.addEventListener("click", loadLogs);

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", loadLogs);
  } else {
    loadLogs();
  }

  window.__adminValidation = { loadLogs: loadLogs, validate: validate };
})();
