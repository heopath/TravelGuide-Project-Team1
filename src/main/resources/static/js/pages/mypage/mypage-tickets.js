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

/** 날짜 하나. 날짜만 온 값(이용일)과 시각까지 온 값(결제일)을 함께 받는다. */
function formatDate(
    value,
) {
    if (!value) {
        return "";
    }

    const day = String(value).slice(0, 10);

    return /^\d{4}-\d{2}-\d{2}$/.test(day)
        ? day.replaceAll("-", ". ")
        : "";
}

/**
 * 이용 시간. 끝 시각이 없으면 시작만 적는다.
 *
 * <p>없는 끝 시각을 "10:00-" 처럼 적으면 화면이 고장 난 것처럼 보이고, 임의로 채우면
 * 손님이 그 시각을 믿고 늦게 온다.
 */
function formatTimeRange(
    start,
    end,
) {
    const from = String(start || "").slice(0, 5);
    const to = String(end || "").slice(0, 5);

    if (!from) {
        return "";
    }

    return to ? `${from}–${to}` : from;
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

/**
 * 티켓 화면 두 개가 같은 코드를 쓴다. (#281)
 *
 * <p>대시보드의 `최근 예약 내역`은 3건까지 보여주는 미리보기이고, `예약 내역`은 전체를
 * 상태별로 보며 티켓 한 장을 통째로 확인하는 화면이다. 보여주는 모양만 다를 뿐, 결제·
 * 취소·입장 QR·여행 연결은 완전히 같은 동작이다.
 *
 * <p>둘로 나눠 각자 구현하면 결제 흐름이 두 벌이 된다. #276에서 결제 버튼이 한쪽 화면에만
 * 있어 여행 없이 담은 티켓을 결제할 방법이 없었던 것과 같은 종류의 사고가 난다.
 */
function createTicketScreen(mode) {
    /* 예매한 티켓 화면인지. 아니면 대시보드 미리보기다. */
    const history = mode === "history";

    const tabs =
        document.querySelector(
            "[data-ticket-tabs]",
        );

    const picker =
        document.querySelector(
            "[data-ticket-picker]",
        );

    const detail =
        document.querySelector(
            "[data-ticket-detail]",
        );

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

    const ready = history
        ? Boolean(tabs && picker && detail)
        : Boolean(list && empty);

    if (!ready) {
        return Promise.resolve();
    }

    let tickets = [];
    let trips = [];
    /* 예매한 티켓 화면에서 고른 상태 탭과 티켓. 미리보기에서는 쓰지 않는다. */
    let activeGroup = "ALL";
    let selectedId = null;

    function setEmpty(
        title,
        description,
    ) {
        if (history) {
            /* 목록 자리와 상세 자리가 따로라 둘 다 비워야 앞의 티켓이 남지 않는다. */
            picker.replaceChildren(
                message(title, description),
            );

            detail.replaceChildren(
                message(
                    "표시할 티켓이 없어요",
                    "왼쪽에서 티켓을 고르면 이곳에 표시됩니다.",
                ),
            );

            return;
        }

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

    /* 비었을 때·못 불러왔을 때 자리에 놓는 안내. 두 칸 모두 같은 모양을 쓴다. */
    function message(
        title,
        description,
    ) {
        const box =
            document.createElement("div");

        box.className =
            "mypage-ticket-message";

        const strong =
            document.createElement("strong");

        strong.textContent = title;
        box.appendChild(strong);

        if (description) {
            const text =
                document.createElement("p");

            text.textContent = description;
            box.appendChild(text);
        }

        return box;
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
        /*
         * 결제한 예약을 취소하면 발급된 티켓이 무효가 된다. 결제 전 취소와 같은 문구를
         * 쓰면 티켓이 사라지는 줄 모르고 누른다. 예약 화면도 둘을 갈라 묻는다. (#276)
         */
        if (!window.confirm(
            ticket.status === "CONFIRMED"
                ? "결제한 예약을 취소할까요? 환불되고, 발급된 티켓은 사용할 수 없게 됩니다."
                : "이 예약을 취소할까요? 잡아둔 자리가 다시 열립니다.",
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

    /* ── 예매한 티켓 화면 (#281) ── */

    /*
     * 상태를 손님이 쓰는 말로 묶는다. PENDING(결제 대기)과 CONFIRMED(결제 완료)는
     * 아직 안 쓴 티켓이라 손님에게는 둘 다 `사용 예정`이다. 만료는 자리를 반납한
     * 것이라 취소와 같은 칸에 둔다 — 어느 쪽이든 이제 못 쓰는 티켓이다.
     */
    const ticketGroups = [
        { id: "ALL", label: "전체", statuses: null },
        { id: "UPCOMING", label: "사용 예정", statuses: ["PENDING", "CONFIRMED"] },
        { id: "USED", label: "사용 완료", statuses: ["USED"] },
        { id: "CLOSED", label: "취소·환불", statuses: ["CANCELLED", "EXPIRED"] },
    ];

    function inGroup(
        ticket,
        group,
    ) {
        return !group.statuses
            || group.statuses.includes(ticket.status);
    }

    function renderTabs() {
        tabs.replaceChildren();

        ticketGroups.forEach((group) => {
            const button =
                document.createElement("button");

            button.type = "button";
            button.role = "tab";
            button.className =
                "mypage-ticket-tab";
            button.dataset.ticketTab = group.id;

            /*
             * 개수는 받은 목록에서 센다. 서버에 따로 물으면 목록과 개수가 어긋난
             * 화면이 나온다 — 사이에 결제나 취소가 들어오면 실제로 갈린다.
             */
            const total = tickets.filter(
                (ticket) => inGroup(ticket, group),
            ).length;

            const name =
                document.createElement("span");

            name.textContent = group.label;

            const badge =
                document.createElement("em");

            badge.textContent = String(total);

            button.append(name, badge);

            if (group.id === activeGroup) {
                button.classList.add("is-active");
                button.setAttribute("aria-selected", "true");
            }

            button.addEventListener("click", () => {
                activeGroup = group.id;
                /* 탭을 바꾸면 앞서 고른 티켓이 이 탭에 없을 수 있다. 다시 고른다. */
                selectedId = null;
                renderHistory();
            });

            tabs.appendChild(button);
        });
    }

    function renderHistory() {
        renderTabs();

        if (!tickets.length) {
            setEmpty(
                "아직 예약 내역이 없어요",
                "예약 화면에서 티켓을 담으면 이곳에 표시됩니다.",
            );

            return;
        }

        const group = ticketGroups.find(
            (item) => item.id === activeGroup,
        ) || ticketGroups[0];

        const shown = tickets.filter(
            (ticket) => inGroup(ticket, group),
        );

        if (!shown.length) {
            picker.replaceChildren(
                message(
                    `${group.label} 티켓이 없어요`,
                    "다른 탭을 눌러 보세요.",
                ),
            );

            detail.replaceChildren(
                message(
                    "표시할 티켓이 없어요",
                    "왼쪽에서 티켓을 고르면 이곳에 표시됩니다.",
                ),
            );

            return;
        }

        const selected =
            shown.find(
                (ticket) =>
                    String(ticket.reservationId) ===
                    String(selectedId),
            ) || shown[0];

        selectedId = selected.reservationId;

        picker.replaceChildren(
            ...shown.map(
                (ticket) => pickerRow(ticket, selected),
            ),
        );

        detail.replaceChildren(
            ticketDetail(selected),
        );
    }

    function pickerRow(
        ticket,
        selected,
    ) {
        const row =
            document.createElement("button");

        row.type = "button";
        row.className = "mypage-ticket-pick";
        row.dataset.ticketPick =
            String(ticket.reservationId);

        if (String(ticket.reservationId)
            === String(selected.reservationId)) {
            row.classList.add("is-active");
            row.setAttribute("aria-current", "true");
        }

        /*
         * 상품 사진이 없다. 없는 사진 자리를 비워 두면 줄이 흔들려 보여서, 상태에 따라
         * 색이 갈리는 자리표를 둔다. 사진이 생기면 이 자리에 넣으면 된다.
         */
        const thumb =
            document.createElement("span");

        thumb.className = "mypage-ticket-thumb";
        thumb.dataset.ticketStatusTone = ticket.status || "";
        thumb.setAttribute("aria-hidden", "true");
        thumb.textContent = "▤";

        const body =
            document.createElement("span");

        body.className = "mypage-ticket-pick-body";

        const title =
            document.createElement("strong");

        title.textContent =
            ticket.productName || "티켓";

        const when =
            document.createElement("small");

        when.textContent = [
            formatUsage(
                ticket.usageDate,
                ticket.usageStartTime,
            ),
            ticket.optionName,
        ].filter(Boolean).join(" · ");

        const status =
            document.createElement("span");

        status.className = "mypage-ticket-status";
        status.dataset.ticketStatus = ticket.status || "";
        status.textContent =
            ticketStatusLabels[ticket.status]
            || ticket.status || "";

        body.append(title, when, status);

        const chevron =
            document.createElement("span");

        chevron.className = "mypage-ticket-chevron";
        chevron.setAttribute("aria-hidden", "true");
        chevron.textContent = "›";

        row.append(thumb, body, chevron);

        row.addEventListener("click", () => {
            selectedId = ticket.reservationId;
            renderHistory();
        });

        return row;
    }

    /**
     * 티켓 한 장. 종이 티켓처럼 본권과 절취선 오른쪽의 반쪽으로 나눈다.
     *
     * <p>없는 값은 줄째로 뺀다. 장소나 이용 시간은 상품이 지워지면 비어 오는데, 빈칸을
     * 그리면 화면이 고장 난 것처럼 보이고 지어내면 손님이 그 정보를 믿는다.
     */
    function ticketDetail(
        ticket,
    ) {
        const wrap =
            document.createElement("div");

        wrap.className = "mypage-ticket-detail-body";
        wrap.dataset.ticketRow =
            String(ticket.reservationId);

        const card =
            document.createElement("article");

        card.className = "ticket-card";
        card.dataset.ticketStatusTone = ticket.status || "";

        /* 상단 띠. 종이 티켓의 인쇄면에 해당하는 자리라 상태를 여기에 둔다. */
        const top =
            document.createElement("header");

        top.className = "ticket-card-top";

        const brand =
            document.createElement("span");

        brand.className = "ticket-card-brand";
        brand.textContent = "All My Trips";

        const kind =
            document.createElement("strong");

        kind.className = "ticket-card-kind";
        kind.textContent =
            ticket.productName || "티켓";

        const state =
            document.createElement("span");

        state.className = "ticket-card-state";
        state.dataset.ticketStatus = ticket.status || "";
        state.textContent =
            ticketStatusLabels[ticket.status]
            || ticket.status || "";

        top.append(brand, kind, state);

        const body =
            document.createElement("div");

        body.className = "ticket-card-body";

        const main =
            document.createElement("div");

        main.className = "ticket-card-main";

        const name =
            document.createElement("h4");

        name.textContent = [
            ticket.productName,
            ticket.optionName,
        ].filter(Boolean).join(" · ") || "티켓";

        const number =
            document.createElement("p");

        number.className = "ticket-card-number";
        number.textContent =
            `예약번호 ${ticket.reservationNumber || "—"}`;

        main.append(name, number);
        main.appendChild(
            facts([
                ["이용일", formatDate(ticket.usageDate)],
                ["이용 시간", formatTimeRange(
                    ticket.usageStartTime,
                    ticket.usageEndTime,
                )],
                ["장소", ticket.placeName],
                ["인원", ticket.quantity
                    ? `${ticket.quantity}명`
                    : ""],
            ]),
        );

        /* 절취선 오른쪽. 현장에서 내미는 반쪽이라 QR과 티켓 번호만 둔다. */
        const stub =
            document.createElement("div");

        stub.className = "ticket-card-stub";

        const stubTitle =
            document.createElement("span");

        stubTitle.className = "ticket-card-stub-title";
        stubTitle.textContent =
            ticket.status === "CONFIRMED"
                ? "입장 시 스캔"
                : "입장 코드";

        stub.appendChild(stubTitle);

        if (ticket.status === "CONFIRMED") {
            /*
             * QR을 처음부터 그려 두지 않는다. 입장 코드는 5분만 살아 있어서, 열어 둔 채
             * 두면 정작 현장에서 만료된 QR을 내밀게 된다. 누를 때 새로 발급받는다. (#265)
             */
            stub.appendChild(qrSection(ticket));
        } else {
            const wait =
                document.createElement("p");

            wait.className = "ticket-card-stub-note";
            wait.textContent =
                ticket.status === "PENDING"
                    ? "결제하면 입장 코드가 발급돼요."
                    : "이 티켓은 입장 코드를 쓸 수 없어요.";

            stub.appendChild(wait);
        }

        if (ticket.quantity) {
            const count =
                document.createElement("small");

            count.className = "ticket-card-stub-count";
            count.textContent = `${ticket.quantity}매`;

            stub.appendChild(count);
        }

        body.append(main, stub);
        card.append(top, body);
        wrap.appendChild(card);

        const notice =
            document.createElement("p");

        notice.className = "ticket-card-notice";
        notice.textContent =
            "현장에서 입장 코드를 보여주세요. 코드는 5분 뒤 만료되며 다시 발급할 수 있어요.";

        wrap.appendChild(notice);

        wrap.appendChild(detailPanels(ticket));
        wrap.appendChild(tripControl(ticket));

        const actions =
            document.createElement("div");

        actions.className = "ticket-card-actions";

        if (ticket.status === "PENDING") {
            actions.appendChild(paySection(ticket));
        } else if (ticket.status === "CONFIRMED") {
            const cancel =
                document.createElement("button");

            cancel.type = "button";
            cancel.className = "text-button";
            cancel.dataset.ticketCancel =
                String(ticket.reservationId);
            cancel.textContent = "예약 취소";

            cancel.addEventListener("click", () => {
                cancelTicket(ticket, cancel);
            });

            actions.appendChild(cancel);
        }

        if (actions.childElementCount) {
            wrap.appendChild(actions);
        }

        return wrap;
    }

    /** 이름과 값 짝을 늘어놓는다. 값이 없는 짝은 아예 넣지 않는다. */
    function facts(
        pairs,
    ) {
        const box =
            document.createElement("dl");

        box.className = "ticket-card-facts";

        pairs
            .filter(([, value]) => Boolean(value))
            .forEach(([label, value]) => {
                const row =
                    document.createElement("div");

                const dt =
                    document.createElement("dt");

                dt.textContent = label;

                const dd =
                    document.createElement("dd");

                dd.textContent = value;

                row.append(dt, dd);
                box.appendChild(row);
            });

        return box;
    }

    /* 이용 안내와 결제 정보. 결제 전 예약에는 결제 정보가 아직 없다. */
    function detailPanels(
        ticket,
    ) {
        const wrap =
            document.createElement("div");

        wrap.className = "ticket-card-panels";

        const usage =
            document.createElement("section");

        usage.className = "ticket-card-panel";
        usage.append(
            heading("이용 안내"),
            facts([
                ["이용 시간", formatTimeRange(
                    ticket.usageStartTime,
                    ticket.usageEndTime,
                )],
                ["이용일", formatDate(ticket.usageDate)],
                /* 서버 규칙 그대로다. 이용일이 지난 뒤의 취소는 받지 않는다. */
                ["취소 가능", "이용일 당일까지"],
            ]),
        );

        const payment =
            document.createElement("section");

        payment.className = "ticket-card-panel";
        payment.dataset.ticketPayment = "";

        const paid = Boolean(ticket.paidAt || ticket.paymentMethod);

        payment.append(
            heading("결제 정보"),
            facts([
                ["결제 금액", formatAmount(
                    ticket.totalAmount,
                    ticket.currency,
                )],
                ["결제 수단", paid
                    ? window.AllMyTripsPayment.labelOf(
                        ticket.paymentMethod,
                        ticket.paymentProvider,
                    )
                    : ""],
                ["결제일", formatDate(ticket.paidAt)],
                ["상태", paid ? "" : "결제 전"],
            ]),
        );

        wrap.append(usage, payment);

        return wrap;
    }

    function heading(
        text,
    ) {
        const title =
            document.createElement("h5");

        title.textContent = text;

        return title;
    }

    function render() {
        if (history) {
            renderHistory();

            return;
        }

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

/** 대시보드의 `최근 예약 내역`. 3건까지 보여주는 미리보기다. */
export function initTickets() {
    return createTicketScreen("preview");
}

/**
 * 사이드바의 `예약 내역` — 예매한 티켓 화면. (#281)
 *
 * <p>대시보드와 함께 뜨지 않고 화면을 열 때 부른다. 숨어 있는 화면 때문에 첫 화면이
 * 같은 목록을 두 번 받게 두지 않는다.
 */
export function initTicketHistory() {
    return createTicketScreen("history");
}
