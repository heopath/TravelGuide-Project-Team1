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
        if (!window.confirm(
            "모의 결제를 진행할까요? 실제 결제는 이루어지지 않고, 결제하면 티켓이 발급됩니다.",
        )) {
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
                        method: "CARD",
                        idempotencyKey,
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
