/* 카카오페이 결제 결과 화면 (#281)
 *
 * 카카오페이에서 인증이 끝나면 브라우저가 이 주소로 돌아온다. 성공이면 pg_token이,
 * 취소·실패면 우리가 미리 붙여 둔 result 값이 실려 온다.
 *
 * 돌아왔다고 결제가 끝난 것이 아니다. <b>승인은 서버가 한다.</b> 시크릿 키가 필요하고,
 * 어느 예약을 결제하는지도 서버가 결제를 시작할 때 적어 둔 기록에서 꺼낸다. 화면이 들고
 * 있다가 되돌려주는 값이면 다른 결제에 승인을 붙일 수 있다.
 *
 * CSRF 토큰은 app.js가 fetch를 감싸며 붙인다. 여기서 따로 챙기지 않는다.
 */

const params = new URLSearchParams(window.location.search);

const state = document.querySelector("[data-kakao-state]");
const notice = document.querySelector("[data-kakao-notice]");
const backLink = document.querySelector("[data-kakao-back]");

/* 결제를 시작한 화면. 토스와 같은 자리를 쓴다 — 결제사가 늘 때마다 칸을 만들 이유가 없다. */
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
    const pgToken = params.get("pg_token");
    const result = params.get("result");

    /* 손님이 카카오 화면에서 되돌아 나온 경우다. 서버에 남은 기록을 지워 달라고 알린다. */
    if (!pgToken) {
        setState(result === "fail"
            ? "결제가 완료되지 않았어요. 다시 시도해 주세요."
            : "결제를 취소했어요.");
        showBack("다시 시도하기");
        /* 실패해도 화면이 할 일은 없다. 다음 결제가 헌 거래번호를 물지 않게 하는 정리다. */
        await call("/api/v1/payments/kakao/cancel", { method: "POST" }).catch(() => {});
        return;
    }

    notice.hidden = false;

    try {
        const approved = await call("/api/v1/payments/kakao/approve", {
            method: "POST",
            body: JSON.stringify({ pgToken }),
        });
        setState(approved?.replayed
            ? "이미 결제된 예약이에요. 티켓을 확인해 주세요."
            : "결제가 완료됐어요. 티켓을 확인해 주세요.");
        showBack("내 티켓 보기");
    } catch (error) {
        setState(error.message || "결제를 확인하지 못했어요.");
        showBack("돌아가기");
    }
}

run();
