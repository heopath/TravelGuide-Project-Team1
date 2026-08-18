import {
    request,
    showToast,
} from "./mypage-common.js";

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

        return item;
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
