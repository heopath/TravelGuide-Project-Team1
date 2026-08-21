/* 티켓 예약 결제 (#281)
 *
 * 예약 화면의 `내 예약`과 티켓 판매 페이지가 같은 결제를 한다. 두 곳에 같은 코드를 두면
 * 한쪽만 고쳐지는 날이 온다. 수단 고르기 → 수단별 결제창 → 승인 요청까지를 여기서 한 번에 한다.
 *
 * 모듈이 아니라 window에 붙는 고전 스크립트다. 두 화면의 모듈 체계가 서로 다르다.
 *
 * 부르는 쪽이 request(method, url, body)를 넘긴다. 401 복귀나 CSRF 재시도 방식이 화면마다
 * 달라서, 그 판단은 화면에 맡기고 여기서는 부르기만 한다.
 */
(function () {
    "use strict";

    /** 요약 문구(`상품 · 10,000원`)에서 금액만 떼어낸다. */
    function amountTextOf(summary) {
        const matched = /([\d,]+원)/.exec(String(summary || ""));
        return matched ? matched[1] : "";
    }

    /** 고른 수단의 결제창을 띄운다. 닫으면 null이 돌아오고 아무 일도 일어나지 않는다. */
    function runCheckout(reservationId, picked, summary, request) {
        const checkout = window.AllMyTripsCheckout;
        const amountText = amountTextOf(summary);
        const view = { summary, amountText };

        /*
         * 토스와 카카오페이는 우리 창에서 끝나지 않는다. 결제창에서 인증을 마치면 브라우저가
         * /pay/toss · /pay/kakao로 넘어가고 그 화면이 승인을 요청한다. 여기로 돌아오지 않는다.
         */
        if (picked.flow === "KAKAO") {
            return checkout.kakaoCheckout({
                ...view,
                ready: async () => {
                    const payload = await request("POST", "/api/v1/payments/kakao/ready", { reservationId });
                    return payload?.data;
                }
            });
        }

        if (picked.flow === "TOSS") {
            return checkout.tossCheckout({
                ...view,
                reservationId,
                amount: Number(amountText.replace(/[^\d]/g, "")),
                orderName: String(summary || "티켓 예약").split(" · ")[0]
            });
        }

        if (picked.method === "CARD") return checkout.cardCheckout(view);
        if (picked.method === "TRANSFER" || picked.method === "VIRTUAL_ACCOUNT") {
            return checkout.transferCheckout({ ...view, method: picked.method });
        }

        return checkout.easyPayCheckout({
            ...view,
            provider: picked.easyPayProvider || "QR_PAY",
            drawQr: (text) => window.AllMyTripsQr.createQrSvg(text, { label: "결제 승인 QR" }),
            issueQr: async (provider) => {
                const payload = await request("POST",
                    `/api/v1/ticket-reservations/${reservationId}/payment/qr`
                    + `?provider=${encodeURIComponent(provider)}`);
                return payload?.data;
            },
            /* 발권이 곧 결제 완료다. 티켓이 하나라도 생기면 폰에서 승인이 끝난 것이다. */
            pollPaid: async () => {
                const payload = await request("GET",
                    `/api/v1/ticket-reservations/${reservationId}/tickets`);
                return Array.isArray(payload?.data) && payload.data.length > 0;
            }
        });
    }

    /**
     * 예약 하나를 결제한다.
     *
     * 손님이 창을 닫으면 `{ cancelled: true }`다. 실패는 예외로 올린다 — 부르는 쪽이
     * 화면에 맞는 말로 안내해야 해서, 여기서 삼키면 아무 말도 못 하게 된다.
     */
    async function pay(options) {
        const { reservationId, summary, request, onStart } = options || {};
        if (!reservationId || typeof request !== "function") {
            throw new Error("결제할 예약을 찾지 못했습니다.");
        }

        const picked = await window.AllMyTripsPayment.choose({
            summary,
            confirmLabel: "다음",
            /* QR 인코더를 올려 둔 화면에서만 QR 결제를 내준다. 못 그리는 창을 띄우지 않는다. */
            allowQr: Boolean(window.AllMyTripsQr),
        });
        if (!picked) return { cancelled: true };

        /*
         * 여기서부터가 실제 결제다. 수단을 고르는 동안에는 부르는 쪽 버튼을 잠그지 않는다.
         * 창을 닫고 마음을 바꾸는 것은 흔한 일이라, 그때마다 버튼이 잠겨 있으면 답답하다.
         */
        if (typeof onStart === "function") onStart();

        const checkout = await runCheckout(reservationId, picked, summary, request);
        if (!checkout) return { cancelled: true };

        /* 간편결제는 폰에서 이미 승인돼 결제가 끝났다. 다시 결제하지 않는다. */
        if (checkout.paid) return { paid: true, tickets: null };

        /*
         * 멱등키를 화면에서 만든다. 응답이 유실되어 다시 눌러도 같은 키로 들어가면 서버가
         * 앞의 결과를 그대로 돌려주고 두 번 결제되지 않는다.
         */
        const idempotencyKey = window.crypto?.randomUUID
            ? window.crypto.randomUUID()
            : `pay-${reservationId}-${Date.now()}`;

        /* 카드 정보는 보내지 않는다. 우리 결제 API는 카드번호를 받지 않는다. */
        const result = await request("POST", `/api/v1/ticket-reservations/${reservationId}/payment`, {
            method: checkout.method || picked.method,
            idempotencyKey,
            easyPayProvider: checkout.easyPayProvider || picked.easyPayProvider,
        });

        return { paid: true, tickets: result?.data?.tickets || [] };
    }

    window.AllMyTripsTicketPayment = { pay, amountTextOf };
})();
