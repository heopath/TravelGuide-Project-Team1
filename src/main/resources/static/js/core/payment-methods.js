/* 결제수단 선택 (#281)
 *
 * 결제 경로가 두 곳이다 — 마이페이지 `내 티켓`(ES 모듈)과 예약 화면 `내 예약`(고전 스크립트).
 * 그래서 이 파일은 모듈이 아니라 window에 붙는 고전 스크립트다. `flights.js`가 IIFE라
 * import를 쓸 수 없고, 목록을 양쪽에 복사하면 화면 목록(ALL_MY_TRIPS_SCREENS)처럼 조용히
 * 갈라진다. 값이 갈리면 한쪽에서만 되는 결제수단이 생긴다.
 *
 * 실제 돈이 오가지 않는다. 카카오페이·토스도 이름만 빌린 모의 결제다. 나중에 진짜 PG를
 * 붙이면 confirm 이후 그 사업자 창을 띄우는 단계가 이 자리에 들어간다.
 */
(function () {
    "use strict";

    /*
     * method는 서버의 CHECK 제약(CARD/TRANSFER/VIRTUAL_ACCOUNT/EASY_PAY)과 같아야 한다.
     * 카카오페이·토스는 둘 다 EASY_PAY이고, 어디로 결제했는지는 provider로 구분한다.
     */
    const METHODS = [
        {
            id: "CARD",
            method: "CARD",
            provider: null,
            label: "신용·체크카드",
            hint: "가장 익숙한 방법이에요.",
        },
        {
            id: "EASY_PAY:KAKAO_PAY",
            method: "EASY_PAY",
            provider: "KAKAO_PAY",
            label: "카카오페이",
            hint: "간편결제 · 모의",
        },
        {
            id: "EASY_PAY:TOSS_PAY",
            method: "EASY_PAY",
            provider: "TOSS_PAY",
            label: "토스페이",
            hint: "간편결제 · 모의",
        },
        {
            id: "TRANSFER",
            method: "TRANSFER",
            provider: null,
            label: "계좌이체",
            hint: "은행 계좌에서 바로 빠져나가요.",
        },
        {
            id: "VIRTUAL_ACCOUNT",
            method: "VIRTUAL_ACCOUNT",
            provider: null,
            label: "가상계좌",
            hint: "받은 계좌로 입금하는 방식이에요.",
        },
    ];

    const DEFAULT_ID = "CARD";

    /**
     * 결제수단을 고르게 하고 고른 값을 돌려준다.
     *
     * <p>취소하면 null이다. 부르는 쪽은 null이면 아무것도 하지 않으면 된다 —
     * 앞서 쓰던 window.confirm과 같은 자리에 그대로 놓을 수 있다.
     */
    function choose(options) {
        const settings = options || {};
        return new Promise((resolve) => {
            let selectedId = DEFAULT_ID;
            let closed = false;

            const overlay = document.createElement("div");
            overlay.className = "pay-method-overlay";
            /*
             * 뒤 화면을 읽지 못하게 막는다. 결제 금액을 확인하는 자리라 뒤에 있는 목록을
             * 스크린리더가 계속 읽으면 무엇을 결제하는지 놓친다.
             */
            overlay.setAttribute("role", "dialog");
            overlay.setAttribute("aria-modal", "true");
            overlay.setAttribute("aria-label", "결제수단 선택");

            const panel = document.createElement("div");
            panel.className = "pay-method-panel";

            const title = document.createElement("h2");
            title.className = "pay-method-title";
            title.textContent = settings.title || "결제수단을 골라 주세요";
            panel.appendChild(title);

            if (settings.summary) {
                const summary = document.createElement("p");
                summary.className = "pay-method-summary";
                summary.textContent = settings.summary;
                panel.appendChild(summary);
            }

            const list = document.createElement("div");
            list.className = "pay-method-list";

            /*
             * 라디오로 둔다. 누르면 바로 결제되는 버튼으로 만들면 잘못 눌렀을 때 되돌릴
             * 방법이 없다. 고르고 나서 한 번 더 누르게 한다.
             */
            const groupName = `pay-method-${Date.now()}`;
            METHODS.forEach((item) => {
                const row = document.createElement("label");
                row.className = "pay-method-item";

                const input = document.createElement("input");
                input.type = "radio";
                input.name = groupName;
                input.value = item.id;
                input.checked = item.id === selectedId;
                input.addEventListener("change", () => {
                    selectedId = item.id;
                });

                const text = document.createElement("span");
                text.className = "pay-method-text";

                const name = document.createElement("strong");
                name.textContent = item.label;
                text.appendChild(name);

                if (item.hint) {
                    const hint = document.createElement("small");
                    hint.textContent = item.hint;
                    text.appendChild(hint);
                }

                row.append(input, text);
                list.appendChild(row);
            });

            panel.appendChild(list);

            /*
             * 모의 결제라는 사실을 고르는 화면에 둔다. 카카오페이·토스라는 이름을 보고
             * 진짜 돈이 나간다고 믿게 두면 안 된다.
             */
            const notice = document.createElement("p");
            notice.className = "pay-method-notice";
            notice.textContent =
                "모의 결제입니다. 실제 돈이 빠져나가지 않고, 결제하면 티켓이 발급됩니다.";
            panel.appendChild(notice);

            const actions = document.createElement("div");
            actions.className = "pay-method-actions";

            const cancel = document.createElement("button");
            cancel.type = "button";
            cancel.className = "text-button";
            cancel.textContent = "취소";
            cancel.addEventListener("click", () => finish(null));

            const confirm = document.createElement("button");
            confirm.type = "button";
            confirm.className = "primary-button";
            confirm.textContent = settings.confirmLabel || "결제하기";
            confirm.addEventListener("click", () => {
                const picked = METHODS.find((item) => item.id === selectedId);
                finish(picked
                    ? { method: picked.method, easyPayProvider: picked.provider }
                    : null);
            });

            actions.append(cancel, confirm);
            panel.appendChild(actions);
            overlay.appendChild(panel);

            overlay.addEventListener("click", (event) => {
                /* 패널 안을 눌렀을 때는 닫지 않는다. 라디오를 고르다 닫히면 황당하다. */
                if (event.target === overlay) finish(null);
            });

            function onKeyDown(event) {
                if (event.key === "Escape") finish(null);
            }

            function finish(result) {
                if (closed) return;
                closed = true;
                document.removeEventListener("keydown", onKeyDown);
                overlay.remove();
                resolve(result);
            }

            document.addEventListener("keydown", onKeyDown);
            document.body.appendChild(overlay);
            confirm.focus();
        });
    }

    window.AllMyTripsPayment = { METHODS, choose };
})();
