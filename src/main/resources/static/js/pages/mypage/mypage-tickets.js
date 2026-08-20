import {
    request,
    showToast,
} from "./mypage-common.js";

import {
    createQrSvg,
} from "../../core/qr-encoder.js";

/*
 * 마이페이지 · 내 티켓 (#253)
 *
 * 여행별이 아니라 사용자 기준으로 받는다. #255로 티켓이 여행에 붙지 않은 채로도
 * 존재할 수 있게 되어, 여행을 골라야만 보는 방식으로는 산 티켓을 다 볼 수 없다.
 *
 * 입장 코드는 여기서 보여줄 수 없다. 서버가 해시만 저장하고 발급 직후 한 번만
 * 내려주므로 다시 불러올 방법이 없다. 목록에는 상태까지만 둔다.
 */

const TICKET_PREVIEW_COUNT = 3;

const ticketStatusLabels = {
    PENDING: "결제 대기",
    CONFIRMED: "결제 완료",
    CANCELLED: "취소됨",
    EXPIRED: "기한 만료",
    USED: "사용 완료",
};

/* 지난 일은 되돌릴 수 없다. 여행 연결을 바꿀 수 있는 상태만 추린다. */
const linkableStatuses = [
    "PENDING",
    "CONFIRMED",
];

function formatUsage(
    date,
    time,
) {
    const day = String(
        date || "",
    ).replaceAll("-", ".");

    if (!day) {
        return "이용일 미정";
    }

    const clock = String(
        time || "",
    ).slice(0, 5);

    return clock
        ? `${day} ${clock}`
        : day;
}

function formatAmount(
    amount,
    currency,
) {
    const value = Number(
        amount || 0,
    ).toLocaleString("ko-KR");

    return currency === "KRW"
        ? `${value}원`
        : `${value} ${currency || ""}`.trim();
}

export function initTickets() {
    const list =
        document.querySelector(
            "[data-ticket-list]",
        );

    const empty =
        document.querySelector(
            "[data-ticket-empty]",
        );

    const emptyTitle =
        document.querySelector(
            "[data-ticket-empty-title]",
        );

    const emptyDescription =
        document.querySelector(
            "[data-ticket-empty-description]",
        );

    const count =
        document.querySelector(
            "[data-ticket-count]",
        );

    if (!list || !empty) {
        return Promise.resolve();
    }

    let tickets = [];
    let trips = [];

    function setEmpty(
        title,
        description,
    ) {
        empty.hidden = false;

        if (emptyTitle) {
            emptyTitle.textContent =
                title;
        }

        if (emptyDescription) {
            emptyDescription
                .textContent =
                description || "";
        }
    }

    function tripLabel(
        tripId,
    ) {
        const trip = trips.find(
            (item) =>
                String(item.tripId) ===
                String(tripId),
        );

        return trip
            ? (trip.title ||
                trip.destinationName ||
                `여행 ${trip.tripId}`)
            : `여행 ${tripId}`;
    }

    /*
     * 이 티켓을 붙일 수 있는 여행만 고른다. 이용일이 여행 기간 밖이면 서버가
     * TICKET_TRIP_PERIOD_MISMATCH로 거부하므로, 고를 수 있게 두면 눌러야만 알게 된다.
     */
    function linkableTrips(
        ticket,
    ) {
        if (!ticket.usageDate) {
            return [];
        }

        return trips.filter(
            (trip) =>
                trip.startDate &&
                trip.endDate &&
                trip.startDate <=
                ticket.usageDate &&
                ticket.usageDate <=
                trip.endDate,
        );
    }

    async function changeTrip(
        ticket,
        tripId,
        control,
    ) {
        control.disabled = true;

        try {
            await request(
                `/api/v1/ticket-reservations/${ticket.reservationId}/trip`,
                {
                    method: "PATCH",
                    body: JSON.stringify({
                        tripId: tripId,
                    }),
                },
            );

            ticket.tripId = tripId;

            showToast(
                tripId
                    ? "여행에 연결했어요."
                    : "여행 연결을 해제했어요.",
            );

            render();
        } catch (error) {
            showToast(
                error.message ||
                "여행 연결을 바꾸지 못했어요.",
            );
        } finally {
            control.disabled = false;
        }
    }

    function tripControl(
        ticket,
    ) {
        const wrap =
            document.createElement(
                "div",
            );

        wrap.className =
            "mypage-ticket-trip";

        if (ticket.tripId) {
            const label =
                document.createElement(
                    "span",
                );

            label.textContent =
                tripLabel(
                    ticket.tripId,
                );

            wrap.appendChild(label);

            if (
                linkableStatuses
                    .includes(
                        ticket.status,
                    )
            ) {
                const unlink =
                    document.createElement(
                        "button",
                    );

                unlink.type = "button";
                unlink.className =
                    "text-button";
                unlink.textContent =
                    "연결 해제";

                unlink.addEventListener(
                    "click",
                    () => {
                        changeTrip(
                            ticket,
                            null,
                            unlink,
                        );
                    },
                );

                wrap.appendChild(unlink);
            }

            return wrap;
        }

        const options =
            linkableStatuses
                .includes(ticket.status)
                ? linkableTrips(ticket)
                : [];

        if (!options.length) {
            const note =
                document.createElement(
                    "span",
                );

            note.dataset
                .ticketUnlinked = "";

            /* 왜 못 붙이는지 나눠서 밝힌다. 한 문구로 뭉치면 원인을 알 수 없다. */
            note.textContent =
                linkableStatuses
                    .includes(ticket.status)
                    ? "이용일에 맞는 여행이 없어요"
                    : "여행에 연결되지 않음";

            wrap.appendChild(note);

            return wrap;
        }

        const select =
            document.createElement(
                "select",
            );

        select.setAttribute(
            "aria-label",
            "여행에 연결",
        );

        const placeholder =
            document.createElement(
                "option",
            );

        placeholder.value = "";
        placeholder.textContent =
            "여행에 연결하기";

        select.appendChild(
            placeholder,
        );

        options.forEach(
            (trip) => {
                const option =
                    document.createElement(
                        "option",
                    );

                option.value =
                    String(trip.tripId);

                option.textContent =
                    trip.title ||
                    trip.destinationName ||
                    `여행 ${trip.tripId}`;

                select.appendChild(
                    option,
                );
            },
        );

        select.addEventListener(
            "change",
            () => {
                if (!select.value) {
                    return;
                }

                changeTrip(
                    ticket,
                    Number(select.value),
                    select,
                );
            },
        );

        wrap.appendChild(select);

        return wrap;
    }

    function ticketRow(
        ticket,
    ) {
        const item =
            document.createElement(
                "article",
            );

        item.className =
            "mypage-ticket-item";

        item.dataset.ticketRow =
            String(
                ticket.reservationId,
            );

        const head =
            document.createElement(
                "div",
            );

        head.className =
            "mypage-ticket-head";

        const title =
            document.createElement(
                "strong",
            );

        title.textContent =
            ticket.productName ||
            "티켓";

        const status =
            document.createElement(
                "span",
            );

        status.className =
            "mypage-ticket-status";

        status.dataset
            .ticketStatus =
            ticket.status || "";

        status.textContent =
            ticketStatusLabels[
                ticket.status
                ] || ticket.status || "";

        head.append(title, status);

        const meta =
            document.createElement(
                "p",
            );

        meta.className =
            "mypage-ticket-meta";

        meta.textContent = [
            ticket.optionName,
            formatUsage(
                ticket.usageDate,
                ticket.usageStartTime,
            ),
            `${ticket.quantity || 0}매`,
            formatAmount(
                ticket.totalAmount,
                ticket.currency,
            ),
        ].filter(Boolean)
            .join(" · ");

        const number =
            document.createElement(
                "small",
            );

        number.className =
            "mypage-ticket-number";

        number.textContent =
            ticket.reservationNumber ||
            "";

        item.append(
            head,
            meta,
            number,
            tripControl(ticket),
        );

        /*
         * 결제를 마친 예약에만 입장 QR을 둔다. 결제 전에는 티켓이 아직 없고, 취소·사용
         * 완료된 티켓은 서버가 발급을 거부한다.
         */
        if (ticket.status === "CONFIRMED") {
            item.appendChild(
                qrSection(ticket),
            );
        }

        /*
         * 결제 전 예약에는 결제 경로를 둔다. #255로 여행 없이 살 수 있게 됐는데 결제 버튼은
         * 예약 화면의 `내 예약` 탭(여행 기준)에만 있어, 여행 없이 담으면 결제할 방법이
         * 아예 없었다. (#276)
         */
        if (ticket.status === "PENDING") {
            item.appendChild(
                paySection(ticket),
            );
        }

        return item;
    }

    /* ── 결제 (#276) ── */

    function paySection(
        ticket,
    ) {
        const wrap =
            document.createElement(
                "div",
            );

        wrap.className =
            "mypage-ticket-pay";

        /*
         * 남은 시간을 밝힌다. 예약은 결제하지 않으면 자리를 반납하는데, 안 보여주면
         * 손님은 담아둔 것이 왜 사라졌는지 알 수 없다.
         */
        const remain =
            document.createElement(
                "span",
            );

        remain.dataset.payRemain = "";
        remain.textContent =
            expiryText(ticket.expiresAt);

        const pay =
            document.createElement(
                "button",
            );

        pay.type = "button";
        pay.className =
            "primary-button";
        pay.dataset.ticketPay =
            String(
                ticket.reservationId,
            );
        pay.textContent =
            "모의 결제하기";

        pay.addEventListener(
            "click",
            () => {
                payTicket(ticket, pay);
            },
        );

        const cancel =
            document.createElement(
                "button",
            );

        cancel.type = "button";
        cancel.className =
            "text-button";
        cancel.dataset.ticketCancel =
            String(
                ticket.reservationId,
            );
        cancel.textContent =
            "예약 취소";

        cancel.addEventListener(
            "click",
            () => {
                cancelTicket(ticket, cancel);
            },
        );

        wrap.append(remain, pay, cancel);

        return wrap;
    }

    function expiryText(
        expiresAt,
    ) {
        if (!expiresAt) {
            return "결제하면 티켓이 발급돼요.";
        }

        const left =
            new Date(expiresAt).getTime() -
            Date.now();

        if (left <= 0) {
            return "결제 시간이 지나 자리가 반납됐을 수 있어요.";
        }

        const minutes =
            Math.ceil(left / 60000);

        return `${minutes}분 안에 결제해야 자리가 유지돼요.`;
    }

    async function payTicket(
        ticket,
        button,
    ) {
        /*
         * 결제수단을 고르게 한다. (#281) 고르는 창은 예약 화면과 같은 것을 쓴다 —
         * `core/payment-methods.js`가 window에 붙여 둔다. 취소하면 null이라 앞서 쓰던
         * window.confirm 자리에 그대로 들어간다.
         */
        const picked = await window.AllMyTripsPayment.choose({
            summary: `${ticket.productName || "티켓"} · ${formatAmount(
                ticket.totalAmount,
                ticket.currency,
            )}`,
            confirmLabel: "모의 결제하기",
            /* QR 결제는 여기서만 내준다. 이유는 payment-methods.js 주석 참고. (#281) */
            allowQr: true,
        });

        if (!picked) {
            return;
        }

        /*
         * QR 결제는 여기서 끝나지 않는다. QR을 띄우고 손님이 폰으로 스캔해 승인해야
         * 결제된다. 승인은 다른 기기에서 일어나므로 이 화면은 기다리며 지켜본다.
         */
        if (picked.flow === "QR") {
            await startQrPayment(ticket, button);
            return;
        }

        button.disabled = true;

        try {
            /*
             * 멱등키는 화면에서 만든다. 응답이 유실되어 다시 눌러도 같은 키로 들어가면
             * 서버가 앞의 결과를 돌려주고 두 번 결제되지 않는다. 예약 화면도 같은 방식이다.
             */
            const idempotencyKey =
                window.crypto?.randomUUID
                    ? window.crypto.randomUUID()
                    : `pay-${ticket.reservationId}-${Date.now()}`;

            await request(
                `/api/v1/ticket-reservations/${ticket.reservationId}/payment`,
                {
                    method: "POST",
                    body: JSON.stringify({
                        method: picked.method,
                        idempotencyKey,
                        easyPayProvider:
                            picked.easyPayProvider,
                    }),
                },
            );

            showToast(
                "결제가 완료됐어요. 입장 QR을 확인해 주세요.",
            );

            /* 결제하면 상태와 발급 티켓이 바뀐다. 목록을 다시 받아 그린다. */
            await load();
        } catch (error) {
            showToast(
                error.message ||
                "결제하지 못했어요.",
            );

            button.disabled = false;
        }
    }

    /* ── QR 결제 (#281) ── */

    /**
     * 결제 QR을 띄우고 승인을 기다린다.
     *
     * <p>승인은 이 화면이 아니라 QR을 찍은 폰에서 일어난다. 그래서 결제가 끝났는지를
     * 이 화면은 알 수 없고, 티켓이 발급됐는지 주기적으로 물어보는 수밖에 없다.
     * 발권이 곧 결제 완료라 발급된 티켓이 하나라도 생기면 끝난 것이다.
     */
    async function startQrPayment(
        ticket,
        button,
    ) {
        button.disabled = true;

        let issued;

        try {
            issued = await request(
                `/api/v1/ticket-reservations/${ticket.reservationId}/payment/qr`,
                { method: "POST" },
            );
        } catch (error) {
            showToast(
                error.message ||
                "결제 QR을 띄우지 못했어요.",
            );

            button.disabled = false;

            return;
        }

        openQrPaymentPanel(ticket, button, issued);
    }

    function openQrPaymentPanel(
        ticket,
        button,
        issued,
    ) {
        const overlay =
            document.createElement("div");

        overlay.className = "pay-qr-overlay";
        overlay.dataset.payQr = "";
        overlay.setAttribute("role", "dialog");
        overlay.setAttribute("aria-modal", "true");
        overlay.setAttribute("aria-label", "QR 결제");

        const panel =
            document.createElement("div");

        panel.className = "pay-qr-panel";

        const title =
            document.createElement("h2");

        title.textContent =
            "휴대폰으로 QR을 찍어 주세요";

        const summary =
            document.createElement("p");

        summary.className = "pay-qr-summary";
        summary.textContent =
            `${ticket.productName || "티켓"} · ${formatAmount(
                ticket.totalAmount,
                ticket.currency,
            )}`;

        const code =
            document.createElement("div");

        code.className = "pay-qr-code";
        code.dataset.payQrCode = "";

        /*
         * QR에는 승인 화면 주소를 담는다. 토큰만 담으면 찍어도 아무 데도 가지 않는다.
         * 주소를 서버가 아니라 화면에서 만드는 이유는, 서버가 만들면 배포 주소를 설정으로
         * 들고 있어야 하고 로컬·운영이 어긋나면 엉뚱한 곳으로 보내기 때문이다.
         */
        const approveUrl =
            `${window.location.origin}/pay/qr`
            + `?token=${encodeURIComponent(issued.token)}`;

        try {
            code.appendChild(
                createQrSvg(
                    approveUrl,
                    { label: "결제 승인 QR" },
                ),
            );
        } catch (error) {
            const failed =
                document.createElement("p");

            failed.textContent =
                "QR을 그리지 못했어요.";

            code.appendChild(failed);
        }

        const remain =
            document.createElement("p");

        remain.className = "pay-qr-remain";
        remain.dataset.payQrRemain = "";

        const state =
            document.createElement("p");

        state.className = "pay-qr-state";
        state.dataset.payQrState = "";
        state.textContent =
            "승인을 기다리는 중이에요. 폰에서 금액을 확인하고 승인해 주세요.";

        const close =
            document.createElement("button");

        close.type = "button";
        close.className = "text-button";
        close.textContent = "닫기";

        panel.append(
            title,
            summary,
            code,
            remain,
            state,
            close,
        );

        overlay.appendChild(panel);
        document.body.appendChild(overlay);

        let finished = false;

        /* 서버가 준 두 값의 차이로 센다. 손님 기기 시계는 믿을 수 없다. */
        const total =
            new Date(issued.expiresAt).getTime()
            - new Date(issued.serverTime).getTime();

        const startedAt = Date.now();

        /*
         * tick()을 바로 한 번 부르는데, 이미 만료된 QR이면 그 자리에서 stop()이 불린다.
         * 그때 두 타이머가 아직 만들어지기 전이라 미리 자리를 잡아 둔다.
         */
        let countdown = null;
        let polling = null;

        const tick = () => {
            const left = Math.max(
                0,
                total - (Date.now() - startedAt),
            );

            if (left === 0) {
                stop();
                code.hidden = true;
                remain.textContent = "";
                state.textContent =
                    "QR이 만료됐어요. 창을 닫고 다시 결제해 주세요.";

                return;
            }

            const seconds =
                Math.ceil(left / 1000);

            remain.textContent =
                `${Math.floor(seconds / 60)}분 `
                + `${String(seconds % 60).padStart(2, "0")}초 뒤 만료돼요.`;
        };

        /* 먼저 한 번 그린다. 안 그러면 창이 열리고 1초 동안 남은 시간 자리가 비어 있다. */
        tick();

        countdown =
            window.setInterval(tick, 1000);

        /*
         * 승인됐는지 물어본다. 티켓 목록은 결제 전에는 비어 있고 결제하는 순간 채워진다.
         * 폴링 간격은 2.5초다 — 더 짧게 하면 승인 한 번을 위해 요청만 늘고, 더 길면
         * 폰에서 승인하고 PC 화면이 바뀌기까지 어색하게 기다린다.
         */
        polling =
            window.setInterval(async () => {
                let tickets;

                try {
                    tickets = await request(
                        `/api/v1/ticket-reservations/${ticket.reservationId}/tickets`,
                    );
                } catch (error) {
                    /* 한 번 실패는 넘긴다. 다음 차례에 다시 묻는다. */
                    return;
                }

                if (!Array.isArray(tickets) || !tickets.length) {
                    return;
                }

                finished = true;
                stop();
                overlay.remove();

                showToast(
                    "결제가 완료됐어요. 입장 QR을 확인해 주세요.",
                );

                await load();
            }, 2500);

        function stop() {
            window.clearInterval(countdown);
            window.clearInterval(polling);
        }

        close.addEventListener("click", () => {
            stop();
            overlay.remove();

            /*
             * 닫아도 결제를 되돌리지는 않는다. 폰에서 이미 승인했을 수 있어서다.
             * 목록을 다시 받아 지금 상태를 보여준다.
             */
            if (!finished) {
                button.disabled = false;
                load();
            }
        });
    }

    async function cancelTicket(
        ticket,
        button,
    ) {
        if (!window.confirm(
            "이 예약을 취소할까요? 잡아둔 자리가 다시 열립니다.",
        )) {
            return;
        }

        button.disabled = true;

        try {
            await request(
                `/api/v1/ticket-reservations/${ticket.reservationId}`,
                {
                    method: "DELETE",
                },
            );

            showToast(
                "예약을 취소했어요.",
            );

            await load();
        } catch (error) {
            showToast(
                error.message ||
                "취소하지 못했어요.",
            );

            button.disabled = false;
        }
    }

    /* ── 입장 QR ── */

    function qrSection(
        ticket,
    ) {
        const wrap =
            document.createElement(
                "div",
            );

        wrap.className =
            "mypage-ticket-qr";

        const button =
            document.createElement(
                "button",
            );

        button.type = "button";
        button.className =
            "text-button";
        button.dataset.ticketQrOpen =
            String(
                ticket.reservationId,
            );
        button.textContent =
            "입장 QR 보기";

        const panel =
            document.createElement(
                "div",
            );

        panel.dataset.ticketQrPanel =
            String(
                ticket.reservationId,
            );

        panel.hidden = true;

        button.addEventListener(
            "click",
            () => {
                if (!panel.hidden) {
                    closeQr(panel);

                    button.textContent =
                        "입장 QR 보기";

                    return;
                }

                button.textContent =
                    "QR 닫기";

                openQr(
                    ticket,
                    panel,
                );
            },
        );

        wrap.append(button, panel);

        return wrap;
    }

    /* 타이머를 걸어둔 채 화면을 지우면 계속 돈다. 닫을 때 반드시 멈춘다. */
    function closeQr(
        panel,
    ) {
        const timer =
            Number(
                panel.dataset.qrTimer ||
                0,
            );

        if (timer) {
            window.clearInterval(timer);
        }

        delete panel.dataset.qrTimer;

        panel.replaceChildren();
        panel.hidden = true;
    }

    async function openQr(
        ticket,
        panel,
    ) {
        closeQr(panel);
        panel.hidden = false;

        const notice =
            document.createElement(
                "p",
            );

        notice.className =
            "mypage-ticket-qr-note";

        notice.textContent =
            "입장 코드를 불러오는 중이에요.";

        panel.appendChild(notice);

        let tickets;

        try {
            tickets = await request(
                `/api/v1/ticket-reservations/${ticket.reservationId}/tickets`,
            );
        } catch (error) {
            notice.textContent =
                error.message ||
                "입장 코드를 불러오지 못했어요.";

            return;
        }

        /*
         * 수량만큼 티켓이 따로 발급된다. 2매면 QR도 2개다. 한 장만 보여주면 일행 중
         * 한 명이 못 들어간다.
         */
        const usable =
            (Array.isArray(tickets)
                ? tickets
                : []).filter(
                (item) =>
                    item.status ===
                    "ISSUED",
            );

        if (!usable.length) {
            notice.textContent =
                "쓸 수 있는 티켓이 없어요.";

            return;
        }

        const issued = [];

        for (const item of usable) {
            try {
                issued.push(
                    await request(
                        `/api/v1/ticket-reservations/${ticket.reservationId}`
                        + `/tickets/${item.issuedTicketId}/qr`,
                        {
                            method: "POST",
                        },
                    ),
                );
            } catch (error) {
                notice.textContent =
                    error.message ||
                    "입장 코드를 발급하지 못했어요.";

                return;
            }
        }

        panel.replaceChildren();

        issued.forEach(
            (qr) => {
                panel.appendChild(
                    qrCard(qr),
                );
            },
        );

        const remain =
            document.createElement(
                "p",
            );

        remain.className =
            "mypage-ticket-qr-note";

        remain.dataset.qrRemain = "";

        panel.appendChild(remain);

        startCountdown(
            panel,
            remain,
            issued[0],
        );
    }

    function qrCard(
        qr,
    ) {
        const card =
            document.createElement(
                "div",
            );

        card.className =
            "mypage-ticket-qr-card";

        card.dataset.qrCard =
            String(
                qr.issuedTicketId,
            );

        try {
            card.appendChild(
                createQrSvg(
                    qr.token,
                    {
                        label:
                            `${qr.ticketNumber} 입장 코드`,
                    },
                ),
            );
        } catch (error) {
            const failed =
                document.createElement(
                    "p",
                );

            failed.textContent =
                "QR을 그리지 못했어요.";

            card.appendChild(failed);
        }

        const number =
            document.createElement(
                "small",
            );

        number.textContent =
            qr.ticketNumber;

        card.appendChild(number);

        return card;
    }

    /*
     * 남은 시간은 서버가 준 두 값의 차이로 센다. 손님 기기 시계로 계산하면 시계가 틀어진
     * 기기에서 아직 유효한 QR을 만료로 표시하거나 그 반대가 된다.
     */
    function startCountdown(
        panel,
        target,
        qr,
    ) {
        const total =
            new Date(qr.expiresAt).getTime() -
            new Date(qr.serverTime).getTime();

        const startedAt = Date.now();

        const tick = () => {
            const left = Math.max(
                0,
                total -
                (Date.now() - startedAt),
            );

            if (left === 0) {
                target.textContent =
                    "입장 코드가 만료됐어요. 다시 열면 새로 발급돼요.";

                closeQrKeepingMessage(
                    panel,
                    target,
                );

                return;
            }

            const seconds =
                Math.ceil(left / 1000);

            target.textContent =
                `${Math.floor(seconds / 60)}분 ${String(seconds % 60).padStart(2, "0")}초 뒤 만료돼요.`;
        };

        tick();

        panel.dataset.qrTimer =
            String(
                window.setInterval(
                    tick,
                    1000,
                ),
            );
    }

    /* 만료되면 QR만 지우고 안내는 남긴다. 통하지 않는 QR을 들고 서 있지 않게 한다. */
    function closeQrKeepingMessage(
        panel,
        message,
    ) {
        const timer =
            Number(
                panel.dataset.qrTimer ||
                0,
            );

        if (timer) {
            window.clearInterval(timer);
        }

        delete panel.dataset.qrTimer;

        panel.replaceChildren(message);
    }

    function render() {
        list.replaceChildren();

        if (!tickets.length) {
            setEmpty(
                "아직 예약 내역이 없어요",
                "예약 화면에서 티켓을 담으면 이곳에 표시됩니다.",
            );

            if (count) {
                count.hidden = true;
            }

            return;
        }

        empty.hidden = true;

        if (count) {
            count.hidden = false;
            count.textContent =
                `${tickets.length}건`;
        }

        tickets
            .slice(
                0,
                TICKET_PREVIEW_COUNT,
            )
            .forEach(
                (ticket) => {
                    list.appendChild(
                        ticketRow(ticket),
                    );
                },
            );
    }

    async function load() {
        try {
            /* tripId를 붙이지 않는다. 붙이면 여행 없는 티켓이 빠진다. (#255) */
            const received =
                await request(
                    "/api/v1/ticket-reservations",
                );

            tickets =
                Array.isArray(received)
                    ? received
                    : [];
        } catch (error) {
            setEmpty(
                "예약 내역을 불러오지 못했어요",
                error.message || "",
            );

            return;
        }

        /*
         * 여행 목록은 연결 칸에만 쓴다. 실패해도 티켓은 보여야 하므로 따로 잡는다.
         */
        try {
            const page =
                await request(
                    "/api/v1/trips?page=0&size=50",
                );

            trips = Array.isArray(page)
                ? page
                : (page?.items ||
                    page?.content ||
                    []);
        } catch (error) {
            trips = [];
        }

        render();
    }

    return load();
}
