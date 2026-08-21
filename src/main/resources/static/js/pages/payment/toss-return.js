/* 토스 결제 결과 화면 (#281)
 *
 * 토스 결제창에서 인증이 끝나면 브라우저가 이 주소로 돌아온다. 성공이면 결제 정보가,
 * 실패면 실패 사유가 주소에 실려 온다.
 *
 * 돌아왔다고 결제가 끝난 것이 아니다. <b>승인은 서버가 한다.</b> 시크릿 키가 필요하고,
 * 그 키가 브라우저에 있으면 누구나 우리 이름으로 승인을 부를 수 있다. 이 화면은 서버에
 * 승인을 요청하고 결과를 보여 줄 뿐이다.
 *
 * CSRF 토큰은 app.js가 fetch를 감싸며 붙인다. 여기서 따로 챙기지 않는다.
 */

const params = new URLSearchParams(window.location.search);

const state = document.querySelector("[data-toss-state]");
const detail = document.querySelector("[data-toss-detail]");
const orderCell = document.querySelector("[data-toss-order]");
const amountCell = document.querySelector("[data-toss-amount]");
const notice = document.querySelector("[data-toss-notice]");
const backLink = document.querySelector("[data-toss-back]");

/* 결제를 시작한 화면. 없으면 마이페이지로 보낸다 — 티켓은 거기서도 확인할 수 있다. */
const RETURN_KEY = "allmytrips.payReturnTo";

function setState(message) {
    state.textContent = message;
}

function showBack(label) {
    backLink.textContent = label;
    backLink.href = returnTo();
    backLink.hidden = false;
}

/**
 * 돌아갈 주소.
 *
 * <p>저장된 값을 그대로 쓰지 않고 우리 사이트 안의 경로인지 확인한다. 저장소는 다른
 * 스크립트도 건드릴 수 있어서, 검사 없이 쓰면 바깥 주소로 보내는 발판이 된다.
 * 로그인 복귀 주소를 origin으로 검증하는 login.js와 같은 이유다.
 */
function returnTo() {
    let saved = "";
    try {
        saved = window.sessionStorage.getItem(RETURN_KEY) || "";
    } catch (error) {
        /* 저장을 막아둔 브라우저다. 기본 주소로 간다. */
        saved = "";
    }
    if (!saved.startsWith("/") || saved.startsWith("//")) return "/mypage";
    return saved;
}

function formatAmount(value) {
    return `${Number(value || 0).toLocaleString("ko-KR")}원`;
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
        const back = encodeURIComponent(window.location.pathname + window.location.search);
        window.location.href = `/auth/login?redirect=${back}`;
        throw new Error("로그인이 필요합니다.");
    }
    if (!response.ok || !payload?.success) {
        throw new Error(payload?.message || "결제를 확인하지 못했습니다.");
    }
    return payload.data;
}

async function run() {
    const paymentKey = params.get("paymentKey");
    const orderId = params.get("orderId");
    const amount = params.get("amount");

    /* 실패로 돌아온 경우다. 토스가 code·message를 실어 준다. */
    if (!paymentKey) {
        const message = params.get("message");
        setState(message || "결제가 취소되었거나 완료되지 않았어요.");
        showBack("다시 시도하기");
        return;
    }

    orderCell.textContent = orderId || "—";
    amountCell.textContent = formatAmount(amount);
    detail.hidden = false;
    notice.hidden = false;

    try {
        const result = await call("/api/v1/payments/toss/confirm", {
            method: "POST",
            body: JSON.stringify({
                paymentKey,
                orderId,
                /* 주소에는 문자열로 실려 온다. 서버는 숫자를 받는다. */
                amount: Number(amount),
            }),
        });
        setState(result?.replayed
            ? "이미 결제된 예약이에요. 티켓을 확인해 주세요."
            : "결제가 완료됐어요. 티켓을 확인해 주세요.");
        showBack("내 티켓 보기");
    } catch (error) {
        setState(error.message || "결제를 확인하지 못했어요.");
        showBack("돌아가기");
    }
}

run();
