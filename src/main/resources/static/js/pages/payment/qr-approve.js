/* QR 결제 승인 화면 (#281)
 *
 * PC 화면에 뜬 결제 QR을 폰으로 스캔하면 이 화면이 열린다. 여기서 누르는 순간이
 * 실제 결제다. 발권 QR(#265)과 헷갈리기 쉬운데, 그쪽은 산 티켓으로 입장하는 코드다.
 *
 * CSRF 토큰은 app.js가 fetch를 감싸며 붙인다. 여기서 따로 챙기지 않는다.
 */

const params = new URLSearchParams(window.location.search);
const token = params.get("token") || "";

const panel = document.querySelector("[data-pay-approve]");
const state = document.querySelector("[data-pay-state]");
const detail = document.querySelector("[data-pay-detail]");
const remain = document.querySelector("[data-pay-remain]");
const notice = document.querySelector("[data-pay-notice]");
const approveButton = document.querySelector("[data-pay-approve-button]");
const mypageLink = document.querySelector("[data-pay-mypage]");

/*
 * 남은 시간은 서버 시각으로 센다. 폰 시계가 몇 분 어긋나 있는 일이 흔한데, 그대로 믿으면
 * 아직 살아 있는 QR을 만료라고 하거나 그 반대가 된다. 응답의 serverTime과 받은 순간의
 * 차이를 재 두고, 이후 계산에 그 차이를 얹는다.
 */
let clockOffset = 0;
let expiresAt = null;
let countdownTimer = null;

function formatAmount(amount, currency) {
    const value = Number(amount || 0).toLocaleString("ko-KR");
    return currency === "KRW" ? `${value}원` : `${value} ${currency || ""}`.trim();
}

function setState(message) {
    state.textContent = message;
    state.hidden = false;
}

async function call(url, options) {
    const response = await fetch(url, {
        credentials: "same-origin",
        headers: {
            Accept: "application/json",
            ...(options?.body ? { "Content-Type": "application/json" } : {}),
        },
        ...(options || {}),
    });
    const payload = await response.json().catch(() => null);

    if (response.status === 401) {
        /*
         * 화면을 그릴 때 로그인 여부를 미리 판정하지 않는다. auth-state.js가 응답을 받기
         * 전에는 값이 비어 있어 레이스가 생긴다. 401을 받고 나서 보낸다.
         */
        const back = encodeURIComponent(window.location.pathname + window.location.search);
        window.location.href = `/auth/login?redirect=${back}`;
        throw new Error("로그인이 필요합니다.");
    }
    if (!response.ok || !payload?.success) {
        throw new Error(payload?.message || "요청을 처리하지 못했습니다.");
    }
    return payload.data;
}

function startCountdown() {
    stopCountdown();
    countdownTimer = window.setInterval(tick, 1000);
    tick();
}

function stopCountdown() {
    if (countdownTimer) window.clearInterval(countdownTimer);
    countdownTimer = null;
}

function tick() {
    if (!expiresAt) return;
    const left = expiresAt - (Date.now() + clockOffset);
    if (left <= 0) {
        stopCountdown();
        remain.hidden = true;
        approveButton.hidden = true;
        setState("결제 QR의 유효 시간이 지났습니다. 결제 화면에서 다시 띄워 주세요.");
        return;
    }
    const seconds = Math.ceil(left / 1000);
    remain.hidden = false;
    remain.textContent = `${Math.floor(seconds / 60)}분 ${String(seconds % 60).padStart(2, "0")}초 안에 승인해 주세요.`;
}

function renderSummary(summary) {
    clockOffset = new Date(summary.serverTime).getTime() - Date.now();
    expiresAt = new Date(summary.expiresAt).getTime();

    document.querySelector("[data-pay-product]").textContent =
        [summary.productName, summary.optionName].filter(Boolean).join(" · ") || "티켓";
    document.querySelector("[data-pay-quantity]").textContent = `${summary.quantity || 1}매`;
    document.querySelector("[data-pay-number]").textContent = summary.reservationNumber || "—";
    document.querySelector("[data-pay-amount]").textContent =
        formatAmount(summary.amount, summary.currency);
    detail.hidden = false;

    /* 스캔이 늦어 이미 결제가 끝난 경우다. 승인 버튼을 두면 눌러 봐야 거절만 당한다. */
    if (summary.alreadyPaid) {
        setState("이미 결제가 끝난 예약입니다.");
        mypageLink.hidden = false;
        return;
    }

    state.hidden = true;
    notice.hidden = false;
    approveButton.hidden = false;
    startCountdown();
}

async function approve() {
    approveButton.disabled = true;
    setState("결제를 승인하는 중입니다…");

    try {
        const result = await call("/api/v1/payments/qr/approve", {
            method: "POST",
            body: JSON.stringify({ token }),
        });
        stopCountdown();
        remain.hidden = true;
        approveButton.hidden = true;
        notice.hidden = true;
        const count = (result?.tickets || []).length;
        setState(count
            ? `결제가 끝났습니다. 티켓 ${count}장이 발급됐어요.`
            : "결제가 끝났습니다.");
        mypageLink.hidden = false;
        panel.dataset.payDone = "1";
    } catch (error) {
        setState(error.message || "결제를 승인하지 못했습니다.");
        /*
         * 다시 누를 수 있게 열어 둔다. 같은 QR로 다시 승인해도 멱등키가 같아 두 번
         * 결제되지 않는다. 서버가 앞의 결과를 그대로 돌려준다.
         */
        approveButton.disabled = false;
    }
}

async function init() {
    if (!token) {
        setState("결제 QR 정보가 없습니다. 결제 화면에서 QR을 다시 띄워 주세요.");
        return;
    }
    approveButton.addEventListener("click", approve);

    try {
        renderSummary(await call(`/api/v1/payments/qr?token=${encodeURIComponent(token)}`));
    } catch (error) {
        setState(error.message || "결제 내용을 불러오지 못했습니다.");
    }
}

init();
