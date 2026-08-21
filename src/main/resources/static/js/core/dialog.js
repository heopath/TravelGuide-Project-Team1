/* 화면 안 확인 대화상자
 *
 * 브라우저 기본 `window.confirm`을 대신한다.
 *
 * 기본 대화상자를 쓰면 손님이 한 번이라도 "이 페이지에서 추가 대화상자 생성 안 함"에
 * 체크하는 순간, 그 뒤로 confirm이 **묻지도 않고 false를 돌려준다.** 화면에서는 버튼이
 * 아예 안 눌리는 것처럼 보이고, 예약 취소나 결제가 조용히 아무 일도 안 하게 된다.
 * 실제로 그 증상으로 취소가 막혔다.
 *
 * `core/payment-methods.js`와 같은 이유로 ES 모듈이 아니라 window에 붙는 고전
 * 스크립트다 — 예약 화면(flights.js)이 IIFE라 import를 쓸 수 없다.
 */
(function () {
    "use strict";

    /**
     * 확인 창을 띄우고 예/아니오를 돌려준다.
     *
     * <p>취소하면 false다. `if (!(await confirm(...))) return;` 처럼 기존 window.confirm
     * 자리에 그대로 놓을 수 있다.
     *
     * @param options.message 본문. 줄바꿈(\n)은 문단으로 나뉜다
     * @param options.tone `danger`면 확인 버튼이 위험한 동작 색으로 바뀐다
     */
    function confirm(options) {
        const settings = typeof options === "string" ? { message: options } : (options || {});

        return new Promise((resolve) => {
            let closed = false;

            const overlay = document.createElement("div");
            overlay.className = "amt-dialog-overlay";
            overlay.dataset.amtDialog = "";
            overlay.setAttribute("role", "dialog");
            overlay.setAttribute("aria-modal", "true");

            const panel = document.createElement("div");
            panel.className = "amt-dialog";

            if (settings.title) {
                const title = document.createElement("h2");
                title.className = "amt-dialog-title";
                title.textContent = settings.title;
                panel.appendChild(title);
                overlay.setAttribute("aria-label", settings.title);
            }

            /*
             * 줄바꿈을 문단으로 나눈다. 기본 대화상자는 \n을 알아서 처리해 주는데, 그대로
             * 옮기면 한 줄로 붙어 읽기 어려워진다.
             */
            String(settings.message || "").split("\n").forEach((line) => {
                if (!line.trim()) return;
                const text = document.createElement("p");
                text.className = "amt-dialog-text";
                text.textContent = line;
                panel.appendChild(text);
            });

            const actions = document.createElement("div");
            actions.className = "amt-dialog-actions";

            const cancel = document.createElement("button");
            cancel.type = "button";
            cancel.className = "amt-dialog-cancel";
            cancel.textContent = settings.cancelLabel || "아니요";
            cancel.addEventListener("click", () => finish(false));

            const ok = document.createElement("button");
            ok.type = "button";
            ok.className = `amt-dialog-ok${settings.tone === "danger" ? " danger" : ""}`;
            ok.textContent = settings.confirmLabel || "네";
            ok.addEventListener("click", () => finish(true));

            actions.append(cancel, ok);
            panel.appendChild(actions);
            overlay.appendChild(panel);

            overlay.addEventListener("click", (event) => {
                /* 패널 안을 눌렀을 때는 닫지 않는다. 글자를 끌어 선택하다 닫히면 황당하다. */
                if (event.target === overlay) finish(false);
            });

            function onKeyDown(event) {
                if (event.key === "Escape") finish(false);
            }

            function finish(answer) {
                if (closed) return;
                closed = true;
                document.removeEventListener("keydown", onKeyDown);
                overlay.remove();
                resolve(answer);
            }

            document.addEventListener("keydown", onKeyDown);
            document.body.appendChild(overlay);
            /*
             * 취소에 먼저 초점을 준다. 되돌릴 수 없는 동작을 묻는 자리라, 엔터를 습관적으로
             * 눌렀을 때 실행이 아니라 취소가 되어야 한다.
             */
            cancel.focus();
        });
    }

    window.AllMyTripsDialog = { confirm };
})();
