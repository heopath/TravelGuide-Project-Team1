/* 결제수단 선택 (#281)
 *
 * 결제 경로가 두 곳이다 — 마이페이지 `내 티켓`(ES 모듈)과 예약 화면 `내 예약`(고전 스크립트).
 * 그래서 이 파일은 모듈이 아니라 window에 붙는 고전 스크립트다. `flights.js`가 IIFE라
 * import를 쓸 수 없고, 목록을 양쪽에 복사하면 화면 목록(ALL_MY_TRIPS_SCREENS)처럼 조용히
 * 갈라진다. 값이 갈리면 한쪽에서만 되는 결제수단이 생긴다.
 *
 * 실제 돈이 오가지 않는다. 카카오페이·토스페이는 이름만 빌린 모의 결제다. 다만
 * `토스페이먼츠로 결제`(flow: TOSS)만은 진짜 결제사를 거친다 — 테스트 키라 돈은
 * 나가지 않지만 결제창·승인·실패 처리는 실제와 같다.
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
        /*
         * QR 결제는 다른 것들과 달리 바로 결제되지 않는다. QR을 띄우고 손님이 폰으로
         * 스캔해 승인해야 끝난다. 그래서 flow로 갈라 두고, 부르는 쪽이 분기한다.
         *
         * 예약 화면에서는 내주지 않는다(allowQr). QR을 그리는 core/qr-encoder.js가 ES
         * 모듈인데 flights.js는 고전 스크립트라 불러올 수 없다. 고를 수는 있는데 QR이
         * 안 그려지는 것보다 아예 안 보이는 편이 낫다.
         */
        {
            id: "QR",
            method: "EASY_PAY",
            provider: "QR_PAY",
            flow: "QR",
            label: "QR 결제",
            hint: "휴대폰으로 QR을 찍어 승인해요.",
        },
        /*
         * 유일하게 진짜 결제사를 거치는 수단이다. (#281) 토스 결제창을 띄우고, 승인은
         * 서버가 토스에 요청한다. 테스트 키라 실제 돈은 오가지 않지만 흐름은 진짜다.
         *
         * 클라이언트 키가 없는 환경에서는 목록에 넣지 않는다 — 골라도 창이 안 뜬다.
         */
        {
            id: "TOSS",
            method: "CARD",
            provider: null,
            flow: "TOSS",
            label: "토스페이먼츠로 결제",
            hint: "카드·간편결제 · 실제 결제창(테스트)",
        },
        /*
         * 카카오페이도 진짜 결제사를 거친다. (#281) 다만 토스와 달리 결제창을 우리 화면 안에
         * 띄우지 못해, 카카오 화면으로 아예 다녀온다. 그래서 흐름을 따로 둔다.
         *
         * 시크릿 키가 없는 환경에서는 목록에 넣지 않는다 — 골라도 창이 안 뜬다.
         */
        {
            id: "KAKAO",
            method: "EASY_PAY",
            provider: "KAKAO_PAY",
            flow: "KAKAO",
            label: "카카오페이로 결제",
            hint: "실제 카카오페이 결제창(테스트)",
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

    /** 토스 클라이언트 키가 화면에 실려 있는지. 없으면 토스 수단을 내주지 않는다. */
    function tossReady() {
        const meta = document.querySelector('meta[name="toss-client-key"]');
        return Boolean(meta && meta.content);
    }

    /**
     * 카카오페이 결제가 켜져 있는지.
     *
     * <p>카카오는 화면이 쓸 공개 키가 없다 — 결제 시작부터 서버가 부른다. 그래서 키가 아니라
     * 켜졌는지만 실어 준다.
     */
    function kakaoReady() {
        const meta = document.querySelector('meta[name="kakao-pay-enabled"]');
        return Boolean(meta && meta.content === "true");
    }

    /**
     * 결제 기록에 남은 값을 사람이 읽는 이름으로 되돌린다. (#281)
     *
     * <p>서버는 `결제사_사업자`로 적는다 — 모의 카카오페이면 {@code MOCK_KAKAO_PAY},
     * 카카오를 거친 카카오페이면 {@code KAKAO_KAKAO_PAY}다. 손님에게는 어느 결제사를 거쳤는지가
     * 아니라 무엇으로 냈는지가 중요하므로, 앞의 결제사를 떼고 맞춘다.
     *
     * <p>간편결제가 아니면 사업자 자리가 없어 결제사만 적힌다({@code MOCK}, {@code TOSS}).
     * 그때는 수단만으로 맞춘다 — 떼고 나면 빈 문자열이 되기 때문이다.
     */
    function labelOf(method, provider) {
        const cleaned = String(provider || "").replace(/^(MOCK|TOSS|KAKAO)_?/, "");
        const found = METHODS.find(
            (item) => item.method === method
                && (item.provider || "") === cleaned
                /* 여러 항목이 같은 수단을 쓴다. 먼저 놓인 모의 결제 쪽 이름을 쓴다. */
                && !item.flow,
        );
        return found ? found.label : "결제";
    }

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
            const offered = METHODS.filter((item) => {
                if (item.flow === "QR") return Boolean(settings.allowQr);
                if (item.flow === "TOSS") return Boolean(tossReady());
                if (item.flow === "KAKAO") return Boolean(kakaoReady());
                return true;
            });
            offered.forEach((item) => {
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
            /*
             * 토스도 테스트 키라 돈이 나가지 않는다. 다만 그쪽은 실제 결제창을 거치므로
             * `모의 결제`라고만 적으면 결제창이 뜬 순간 손님이 당황한다.
             */
            notice.textContent = tossReady() || kakaoReady()
                ? "실제 돈이 빠져나가지 않습니다. 토스·카카오페이는 테스트 결제창이고, 나머지는 모의 결제입니다."
                : "모의 결제입니다. 실제 돈이 빠져나가지 않고, 결제하면 티켓이 발급됩니다.";
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
                const picked = offered.find((item) => item.id === selectedId);
                finish(picked
                    ? {
                        method: picked.method,
                        easyPayProvider: picked.provider,
                        /* QR은 바로 결제되지 않는다. 부르는 쪽이 이 값으로 갈라야 한다. */
                        flow: picked.flow || "DIRECT",
                        label: picked.label,
                    }
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

    window.AllMyTripsPayment = { METHODS, choose, labelOf };
})();
