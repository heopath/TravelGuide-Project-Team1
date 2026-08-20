/* 결제수단별 결제창 (모의)
 *
 * 수단을 고른 뒤 실제 결제처럼 한 단계를 더 거친다. 카드면 카드 정보를 넣고, 간편결제면
 * QR을 띄워 폰으로 승인하고, 계좌이체면 입금 계좌를 보여준다.
 *
 * **실제 돈이 오가지 않는다.** 진짜 PG를 붙이면 이 자리에서 그 회사 결제창을 띄우게 된다.
 * 지금은 그 흐름만 흉내 내므로, 상태 전이·멱등키·환불은 그대로 배울 수 있다.
 *
 * <b>카드번호는 서버로 보내지 않는다.</b> 형식만 화면에서 확인하고 버린다 — 우리 결제 API는
 * 카드번호를 받지 않고, 받을 이유도 없다. 모의라도 실제 카드번호를 다루는 길을 만들어 두면
 * 나중에 그 길로 진짜 번호가 흐른다.
 *
 * payment-methods.js와 같은 이유로 ES 모듈이 아니라 window에 붙는 고전 스크립트다.
 */
(function () {
    "use strict";

    /* 사업자별 모양. 실제 브랜드 색을 흉내 내되 로고 이미지는 쓰지 않는다. */
    const BRANDS = {
        KAKAO_PAY: { label: "카카오페이", accent: "#FEE500", ink: "#191600", app: "카카오톡" },
        TOSS_PAY: { label: "토스페이", accent: "#3182F6", ink: "#ffffff", app: "토스" },
        QR_PAY: { label: "QR 결제", accent: "#5b6bff", ink: "#ffffff", app: "휴대폰" },
    };

    /**
     * `카카오톡으로` / `토스로` — 받침에 따라 조사를 고른다.
     *
     * <p>사업자 이름을 문장에 끼워 넣는 자리라 `토스으로`처럼 어색해지기 쉽다. 한글 음절은
     * 코드값으로 받침을 알 수 있고, ㄹ받침은 받침이 있어도 `로`를 쓴다.
     */
    function withRo(word) {
        const last = String(word || "").slice(-1);
        const code = last.charCodeAt(0) - 0xac00;
        if (code < 0 || code > 11171) return `${word}로`;
        const jong = code % 28;
        return jong === 0 || jong === 8 ? `${word}로` : `${word}으로`;
    }

    /* 카드번호 앞자리로 카드사를 짐작한다. 국내 카드는 BIN이 제각각이라 국제 브랜드만 본다. */
    function cardBrand(digits) {
        if (/^4/.test(digits)) return "VISA";
        if (/^5[1-5]/.test(digits)) return "MasterCard";
        if (/^3[47]/.test(digits)) return "AMEX";
        if (/^62/.test(digits)) return "UnionPay";
        if (/^35/.test(digits)) return "JCB";
        return "";
    }

    /**
     * 카드번호 검사(Luhn).
     *
     * <p>실제 카드번호는 마지막 자리가 검사용이라, 오타를 낸 번호는 여기서 걸린다. 모의
     * 결제라도 아무 숫자나 통과시키면 결제창을 흉내 내는 의미가 없다.
     *
     * <p>시험용 번호(4242 4242 4242 4242)는 통과한다.
     */
    function luhnValid(digits) {
        if (!/^\d{13,19}$/.test(digits)) return false;
        let sum = 0;
        let double = false;
        for (let i = digits.length - 1; i >= 0; i -= 1) {
            let value = Number(digits[i]);
            if (double) {
                value *= 2;
                if (value > 9) value -= 9;
            }
            sum += value;
            double = !double;
        }
        return sum % 10 === 0;
    }

    function overlay(label) {
        const root = document.createElement("div");
        root.className = "pay-checkout-overlay";
        root.dataset.payCheckout = "";
        root.setAttribute("role", "dialog");
        root.setAttribute("aria-modal", "true");
        root.setAttribute("aria-label", label);
        return root;
    }

    function panel(brand) {
        const box = document.createElement("div");
        box.className = "pay-checkout";
        if (brand) {
            box.style.setProperty("--brand", brand.accent);
            box.style.setProperty("--brand-ink", brand.ink);
        }
        return box;
    }

    function head(title, summary) {
        const wrap = document.createElement("div");
        wrap.className = "pay-checkout-head";

        const name = document.createElement("strong");
        name.textContent = title;
        wrap.appendChild(name);

        if (summary) {
            const text = document.createElement("span");
            text.textContent = summary;
            wrap.appendChild(text);
        }
        return wrap;
    }

    function amountRow(amountText) {
        const row = document.createElement("div");
        row.className = "pay-checkout-amount";

        const label = document.createElement("span");
        label.textContent = "결제 금액";

        const value = document.createElement("strong");
        value.textContent = amountText || "";

        row.append(label, value);
        return row;
    }

    function notice(text) {
        const line = document.createElement("p");
        line.className = "pay-checkout-notice";
        line.textContent = text;
        return line;
    }

    function actions(cancelLabel, confirmLabel) {
        const wrap = document.createElement("div");
        wrap.className = "pay-checkout-actions";

        const cancel = document.createElement("button");
        cancel.type = "button";
        cancel.className = "pay-checkout-cancel";
        cancel.textContent = cancelLabel;

        const confirm = document.createElement("button");
        confirm.type = "button";
        confirm.className = "pay-checkout-confirm";
        confirm.textContent = confirmLabel;

        wrap.append(cancel, confirm);
        return { wrap, cancel, confirm };
    }

    /**
     * 카드 결제창. 번호·유효기간·CVC를 받는다.
     *
     * <p>돌려주는 값에 카드 정보는 없다. 화면에서 형식만 보고 버린다.
     */
    function cardCheckout(settings) {
        return new Promise((resolve) => {
            const root = overlay("카드 결제");
            const box = panel(null);
            let closed = false;

            box.append(head("카드 결제", settings.summary), amountRow(settings.amountText));

            const form = document.createElement("form");
            form.className = "pay-card-form";
            form.noValidate = true;

            const number = field(form, "카드번호", "1234 1234 1234 1234", "cardNumber");
            const brandTag = document.createElement("em");
            brandTag.className = "pay-card-brand";
            number.label.appendChild(brandTag);

            const row = document.createElement("div");
            row.className = "pay-card-row";
            form.appendChild(row);

            const expiry = field(row, "유효기간", "MM/YY", "cardExpiry");
            const cvc = field(row, "CVC", "3자리", "cardCvc");

            const install = document.createElement("label");
            install.className = "pay-card-field";
            install.innerHTML = "<span>할부</span>";
            const select = document.createElement("select");
            select.dataset.payField = "cardInstallment";
            ["일시불", "2개월", "3개월", "6개월", "12개월"].forEach((text, index) => {
                const option = document.createElement("option");
                option.value = String(index);
                option.textContent = text;
                select.appendChild(option);
            });
            install.appendChild(select);
            form.appendChild(install);

            const error = document.createElement("p");
            error.className = "pay-checkout-error";
            error.dataset.payError = "";
            error.hidden = true;

            box.append(form, error,
                notice("모의 결제입니다. 카드 정보는 서버로 보내지 않고 형식만 확인합니다."));

            /* 네 자리마다 띄어 쓰고 카드사를 알려준다. 실제 결제창이 하는 일이다. */
            number.input.addEventListener("input", () => {
                const digits = number.input.value.replace(/\D/g, "").slice(0, 19);
                number.input.value = digits.replace(/(.{4})/g, "$1 ").trim();
                brandTag.textContent = cardBrand(digits);
            });

            expiry.input.addEventListener("input", () => {
                const digits = expiry.input.value.replace(/\D/g, "").slice(0, 4);
                expiry.input.value = digits.length > 2
                    ? `${digits.slice(0, 2)}/${digits.slice(2)}`
                    : digits;
            });

            cvc.input.addEventListener("input", () => {
                cvc.input.value = cvc.input.value.replace(/\D/g, "").slice(0, 4);
            });

            const buttons = actions("취소", "결제하기");
            box.appendChild(buttons.wrap);

            buttons.cancel.addEventListener("click", () => finish(null));
            buttons.confirm.addEventListener("click", () => {
                const digits = number.input.value.replace(/\D/g, "");
                const [month, year] = expiry.input.value.split("/");

                const problem = !luhnValid(digits)
                    ? "카드번호를 다시 확인해 주세요."
                    : !(month && year && Number(month) >= 1 && Number(month) <= 12)
                        ? "유효기간을 MM/YY로 입력해 주세요."
                        : expired(month, year)
                            ? "유효기간이 지난 카드입니다."
                            : cvc.input.value.length < 3
                                ? "CVC 3자리를 입력해 주세요."
                                : null;

                if (problem) {
                    error.textContent = problem;
                    error.hidden = false;
                    return;
                }
                /* 번호는 여기서 버린다. 마지막 네 자리만 화면 안내에 쓴다. */
                finish({ method: "CARD", cardLast4: digits.slice(-4),
                    installment: Number(select.value) });
            });

            form.addEventListener("submit", (event) => {
                event.preventDefault();
                buttons.confirm.click();
            });

            function finish(result) {
                if (closed) return;
                closed = true;
                document.removeEventListener("keydown", onKeyDown);
                root.remove();
                resolve(result);
            }

            function onKeyDown(event) {
                if (event.key === "Escape") finish(null);
            }

            root.appendChild(box);
            root.addEventListener("click", (event) => {
                if (event.target === root) finish(null);
            });
            document.addEventListener("keydown", onKeyDown);
            document.body.appendChild(root);
            number.input.focus();
        });
    }

    /** 유효기간이 지났는지. `MM/YY`의 YY는 2000년대다. */
    function expired(month, year) {
        const now = new Date();
        const end = new Date(2000 + Number(year), Number(month), 0, 23, 59, 59);
        return end.getTime() < now.getTime();
    }

    function field(parent, labelText, placeholder, name) {
        const label = document.createElement("label");
        label.className = "pay-card-field";

        const text = document.createElement("span");
        text.textContent = labelText;

        const input = document.createElement("input");
        input.type = "text";
        input.inputMode = "numeric";
        input.autocomplete = "off";
        input.placeholder = placeholder;
        input.dataset.payField = name;

        label.append(text, input);
        parent.appendChild(label);
        return { label, input };
    }

    /**
     * 계좌이체·가상계좌 창. 입금할 계좌를 보여주고 입금 확인을 기다린다.
     *
     * <p>계좌번호는 실제 계좌가 아니다. 모의 결제라는 사실을 창 안에 적어 둔다.
     */
    function transferCheckout(settings) {
        return new Promise((resolve) => {
            const root = overlay("계좌 결제");
            const box = panel(null);
            let closed = false;

            const virtual = settings.method === "VIRTUAL_ACCOUNT";
            box.append(
                head(virtual ? "가상계좌 입금" : "계좌이체", settings.summary),
                amountRow(settings.amountText),
            );

            const account = document.createElement("div");
            account.className = "pay-account";
            account.innerHTML = "<span>입금 계좌</span>";

            const number = document.createElement("strong");
            number.textContent = virtual
                ? "올마이트립은행 302-0000-0000 (모의)"
                : "올마이트립은행 302-1111-1111 (모의)";
            account.appendChild(number);

            const holder = document.createElement("small");
            holder.textContent = "예금주 올마이트립 · 실제 입금하지 마세요";
            account.appendChild(holder);

            box.append(account, notice(virtual
                ? "모의 결제입니다. 실제로는 입금이 확인되면 결제가 끝납니다."
                : "모의 결제입니다. 실제로는 은행 앱으로 이동해 이체합니다."));

            const buttons = actions("취소", "입금 확인");
            box.appendChild(buttons.wrap);

            buttons.cancel.addEventListener("click", () => finish(null));
            buttons.confirm.addEventListener("click", () => finish({ method: settings.method }));

            function finish(result) {
                if (closed) return;
                closed = true;
                document.removeEventListener("keydown", onKeyDown);
                root.remove();
                resolve(result);
            }

            function onKeyDown(event) {
                if (event.key === "Escape") finish(null);
            }

            root.appendChild(box);
            root.addEventListener("click", (event) => {
                if (event.target === root) finish(null);
            });
            document.addEventListener("keydown", onKeyDown);
            document.body.appendChild(root);
            buttons.confirm.focus();
        });
    }

    /**
     * 간편결제 창. 사업자 모양으로 QR을 띄우고 폰에서 승인하기를 기다린다.
     *
     * <p>승인은 이 창이 아니라 QR을 찍은 폰에서 일어난다. 그래서 결제가 끝났는지 이 화면은
     * 알 수 없고, 주기적으로 물어보는 수밖에 없다. 발권이 곧 결제 완료라 티켓이 하나라도
     * 생기면 끝난 것이다.
     *
     * @param settings.provider  KAKAO_PAY · TOSS_PAY · QR_PAY
     * @param settings.issueQr   QR을 발급하는 함수. {token, expiresAt, serverTime}을 준다
     * @param settings.pollPaid  결제가 끝났는지 묻는 함수. 참이면 끝난 것이다
     * @param settings.drawQr    문자열을 QR로 그리는 함수. 없으면 주소를 글자로 보여준다
     */
    function easyPayCheckout(settings) {
        const brand = BRANDS[settings.provider] || BRANDS.QR_PAY;

        return new Promise((resolve) => {
            const root = overlay(`${brand.label} 결제`);
            const box = panel(brand);
            box.classList.add("pay-checkout-brand");
            let closed = false;
            let countdown = null;
            let polling = null;

            box.append(head(brand.label, settings.summary), amountRow(settings.amountText));

            const code = document.createElement("div");
            code.className = "pay-qr-code";
            code.dataset.payQrCode = "";
            box.appendChild(code);

            const remain = document.createElement("p");
            remain.className = "pay-qr-remain";
            remain.dataset.payQrRemain = "";
            box.appendChild(remain);

            const state = document.createElement("p");
            state.className = "pay-qr-state";
            state.dataset.payQrState = "";
            state.textContent = "결제 QR을 띄우는 중이에요.";
            box.appendChild(state);

            const buttons = actions("취소", "");
            buttons.confirm.hidden = true;
            box.appendChild(buttons.wrap);
            buttons.cancel.addEventListener("click", () => finish(null));

            root.appendChild(box);
            root.addEventListener("click", (event) => {
                if (event.target === root) finish(null);
            });
            document.addEventListener("keydown", onKeyDown);
            document.body.appendChild(root);

            start();

            async function start() {
                let issued;
                try {
                    issued = await settings.issueQr(settings.provider);
                } catch (error) {
                    state.textContent = error.message || "결제 QR을 띄우지 못했어요.";
                    return;
                }

                /*
                 * QR에는 승인 화면 주소를 담는다. 토큰만 담으면 찍어도 아무 데도 가지 않는다.
                 * 주소를 서버가 아니라 화면에서 만드는 이유는, 서버가 만들면 배포 주소를
                 * 설정으로 들고 있어야 하고 로컬·운영이 어긋나면 엉뚱한 곳으로 보내기 때문이다.
                 */
                const url = `${window.location.origin}/pay/qr`
                    + `?token=${encodeURIComponent(issued.token)}`;

                try {
                    if (settings.drawQr) code.appendChild(settings.drawQr(url));
                    else throw new Error("no drawer");
                } catch (error) {
                    /* QR을 못 그려도 결제는 이어져야 한다. 주소를 눌러 열 수 있게 둔다. */
                    const link = document.createElement("a");
                    link.href = url;
                    link.target = "_blank";
                    link.rel = "noopener";
                    link.textContent = "승인 화면 열기";
                    code.appendChild(link);
                }

                state.textContent = `${withRo(brand.app)} 결제 요청을 보냈어요. `
                    + "휴대폰에서 금액을 확인하고 승인해 주세요.";

                /* 남은 시간은 서버가 준 두 값의 차이로 센다. 손님 기기 시계는 믿을 수 없다. */
                const total = new Date(issued.expiresAt).getTime()
                    - new Date(issued.serverTime).getTime();
                const startedAt = Date.now();

                const tick = () => {
                    const left = Math.max(0, total - (Date.now() - startedAt));
                    if (left === 0) {
                        stop();
                        code.hidden = true;
                        remain.textContent = "";
                        state.textContent = "QR이 만료됐어요. 창을 닫고 다시 결제해 주세요.";
                        return;
                    }
                    const seconds = Math.ceil(left / 1000);
                    remain.textContent = `${Math.floor(seconds / 60)}분 `
                        + `${String(seconds % 60).padStart(2, "0")}초 뒤 만료돼요.`;
                };

                tick();
                countdown = window.setInterval(tick, 1000);

                /*
                 * 폴링 간격은 2.5초다. 더 짧게 하면 승인 한 번을 위해 요청만 늘고, 더 길면
                 * 폰에서 승인하고 화면이 바뀌기까지 어색하게 기다린다.
                 */
                polling = window.setInterval(async () => {
                    let paid = false;
                    try {
                        paid = await settings.pollPaid();
                    } catch (error) {
                        /* 한 번 실패는 넘긴다. 다음 차례에 다시 묻는다. */
                        return;
                    }
                    if (paid) finish({ paid: true, method: "EASY_PAY",
                        easyPayProvider: settings.provider });
                }, 2500);
            }

            function stop() {
                window.clearInterval(countdown);
                window.clearInterval(polling);
            }

            function onKeyDown(event) {
                if (event.key === "Escape") finish(null);
            }

            function finish(result) {
                if (closed) return;
                closed = true;
                stop();
                document.removeEventListener("keydown", onKeyDown);
                root.remove();
                resolve(result);
            }
        });
    }

    /* ── 토스페이먼츠 결제위젯 ── */

    const TOSS_SDK = "https://js.tosspayments.com/v2/standard";
    let sdkLoading = null;

    /**
     * 설정된 클라이언트 키. 비어 있으면 토스를 쓰지 않는다.
     *
     * <p>화면이 meta 태그로 실어 준다. 서버 설정에 키가 없으면 빈 값이라, 키를 넣지 않은
     * 환경에서는 토스 결제수단 자체가 목록에 뜨지 않는다.
     */
    function tossClientKey() {
        const meta = document.querySelector('meta[name="toss-client-key"]');
        return (meta && meta.content) || "";
    }

    /** SDK는 처음 쓸 때 한 번만 받는다. 결제창을 안 여는 손님에게 미리 받게 하지 않는다. */
    function loadTossSdk() {
        if (window.TossPayments) return Promise.resolve(window.TossPayments);
        if (sdkLoading) return sdkLoading;

        sdkLoading = new Promise((resolve, reject) => {
            const script = document.createElement("script");
            script.src = TOSS_SDK;
            script.async = true;
            script.addEventListener("load", () => resolve(window.TossPayments));
            script.addEventListener("error", () => {
                sdkLoading = null;
                reject(new Error("토스 결제창을 불러오지 못했어요."));
            });
            document.head.appendChild(script);
        });
        return sdkLoading;
    }

    /**
     * 주문번호. `AMT-{예약번호}-{난수}` 모양이다.
     *
     * <p>서버가 이 값에서 예약을 꺼낸다 — 주문번호는 결제창을 띄울 때 토스에 함께 넘어가
     * 그 결제에 묶이므로, 화면이 승인만 다른 예약에 붙일 수 없다.
     */
    function orderIdOf(reservationId) {
        const random = window.crypto?.randomUUID
            ? window.crypto.randomUUID().replace(/-/g, "").slice(0, 12)
            : String(Date.now());
        return `AMT-${reservationId}-${random}`;
    }

    /**
     * 토스 결제창(위젯)을 띄운다.
     *
     * <p>여기서 결제가 끝나지 않는다. 토스가 인증을 마치면 successUrl로 브라우저를 돌려보내고,
     * 그 화면이 서버에 승인을 요청한다. 승인은 시크릿 키가 필요해 서버만 할 수 있다.
     *
     * <p>테스트 키를 쓰면 실제 돈이 오가지 않는다. 그 사실을 창 안에 적어 둔다.
     */
    async function tossCheckout(settings) {
        const clientKey = tossClientKey();
        if (!clientKey) return null;

        const TossPayments = await loadTossSdk();
        const root = overlay("토스 결제");
        const box = panel(null);

        box.append(head("토스페이먼츠 결제", settings.summary), amountRow(settings.amountText));

        const widgetBox = document.createElement("div");
        widgetBox.className = "pay-toss-widget";
        widgetBox.dataset.tossPaymentMethod = "";

        const agreementBox = document.createElement("div");
        agreementBox.dataset.tossAgreement = "";

        const error = document.createElement("p");
        error.className = "pay-checkout-error";
        error.dataset.payError = "";
        error.hidden = true;

        box.append(widgetBox, agreementBox, error,
            notice("토스페이먼츠 테스트 결제창입니다. 실제 돈이 빠져나가지 않습니다."));

        const buttons = actions("취소", "결제하기");
        buttons.confirm.disabled = true;
        box.appendChild(buttons.wrap);
        root.appendChild(box);
        document.body.appendChild(root);

        /*
         * customerKey는 손님을 구분하는 값이다. 예약 번호로 만들면 같은 사람이 다른 예약을
         * 결제할 때마다 다른 사람으로 보여 간편결제 등록이 쌓이지 않는다. 로그인 사용자
         * 식별자가 화면에 없으므로 브라우저에 하나 만들어 두고 계속 쓴다.
         */
        const customerKey = rememberedCustomerKey();

        const widgets = TossPayments(clientKey).widgets({ customerKey });
        await widgets.setAmount({ currency: "KRW", value: settings.amount });
        await Promise.all([
            widgets.renderPaymentMethods({ selector: "[data-toss-payment-method]", variantKey: "DEFAULT" }),
            widgets.renderAgreement({ selector: "[data-toss-agreement]", variantKey: "AGREEMENT" })
        ]);
        buttons.confirm.disabled = false;

        return new Promise((resolve) => {
            let closed = false;

            buttons.cancel.addEventListener("click", () => finish(null));
            buttons.confirm.addEventListener("click", async () => {
                buttons.confirm.disabled = true;
                error.hidden = true;
                try {
                    /*
                     * 여기서 화면이 토스로 넘어간다. 돌아오는 주소에 결제 결과가 실려 오고,
                     * 그 화면이 승인을 요청한다.
                     */
                    rememberReturn();
                    await widgets.requestPayment({
                        orderId: orderIdOf(settings.reservationId),
                        orderName: settings.orderName || "티켓 예약",
                        successUrl: `${window.location.origin}/pay/toss`,
                        failUrl: `${window.location.origin}/pay/toss`
                    });
                } catch (exception) {
                    /* 손님이 결제창에서 닫은 경우가 대부분이라 오류로 떠들지 않는다. */
                    error.textContent = exception?.message || "결제를 진행하지 못했어요.";
                    error.hidden = false;
                    buttons.confirm.disabled = false;
                }
            });

            function finish(result) {
                if (closed) return;
                closed = true;
                root.remove();
                resolve(result);
            }
        });
    }

    /**
     * 결제를 시작한 화면을 적어 둔다.
     *
     * <p>토스에서 돌아오면 /pay/toss가 열린다. 그 화면은 손님이 어디서 결제를 시작했는지
     * 알 방법이 없어서, 여기서 남겨 둬야 마이페이지든 예약 화면이든 제자리로 보낼 수 있다.
     */
    function rememberReturn() {
        try {
            window.sessionStorage.setItem("allmytrips.tossReturnTo",
                window.location.pathname + window.location.search);
        } catch (error) {
            /* 저장을 막아둔 브라우저다. 돌아가는 화면이 기본 주소로 보낼 뿐 결제는 된다. */
        }
    }

    /** 같은 브라우저에서는 같은 손님으로 본다. 간편결제 등록이 매번 초기화되지 않게 한다. */
    function rememberedCustomerKey() {
        const KEY = "allmytrips.tossCustomerKey";
        try {
            const saved = window.localStorage.getItem(KEY);
            if (saved) return saved;
            const made = window.crypto?.randomUUID
                ? window.crypto.randomUUID()
                : `amt-${Date.now()}`;
            window.localStorage.setItem(KEY, made);
            return made;
        } catch (error) {
            /* 저장을 막아둔 브라우저다. 이번 결제만 쓰는 값으로 넘어간다. */
            return `amt-${Date.now()}`;
        }
    }

    window.AllMyTripsCheckout = {
        cardCheckout, transferCheckout, easyPayCheckout, tossCheckout, tossClientKey,
        BRANDS, luhnValid, cardBrand, withRo
    };
})();
