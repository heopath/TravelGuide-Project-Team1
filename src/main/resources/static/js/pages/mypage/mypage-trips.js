import {
    request,
    showToast,
} from "./mypage-common.js";

const TRIP_PREVIEW_COUNT = 3;
const TRIP_PAGE_SIZE = 8;

const tripStatusLabels = {
    DRAFT: "작성 중",
    PLANNED: "진행 예정",
    ONGOING: "진행 중",
    COMPLETED: "완료",
    CANCELLED: "취소",
    CONFIRMED: "확정",
};

/* =========================================================
   날짜 표시
   ========================================================= */

function formatTripPeriod(
    start,
    end,
) {
    const convert =
        (value) =>
            String(
                value || "",
            ).replaceAll(
                "-",
                ".",
            );

    const startValue =
        convert(start);

    const endValue =
        convert(end);

    if (!startValue) {
        return "";
    }

    return (
        endValue &&
        endValue !== startValue
            ? `${startValue} ~ ${endValue}`
            : startValue
    );
}


/* =========================================================
   페이지네이션
   ========================================================= */

function getPageNumbers(
    currentPage,
    totalPages,
) {
    if (totalPages <= 7) {
        return Array.from(
            {
                length: totalPages,
            },
            (_, index) =>
                index + 1,
        );
    }

    if (currentPage <= 4) {
        return [
            1,
            2,
            3,
            4,
            5,
            "...",
            totalPages,
        ];
    }

    if (
        currentPage >=
        totalPages - 3
    ) {
        return [
            1,
            "...",
            totalPages - 4,
            totalPages - 3,
            totalPages - 2,
            totalPages - 1,
            totalPages,
        ];
    }

    return [
        1,
        "...",
        currentPage - 1,
        currentPage,
        currentPage + 1,
        "...",
        totalPages,
    ];
}


function renderPagination(
    container,
    currentPage,
    totalPages,
    onChange,
) {
    if (!container) {
        return;
    }

    container.replaceChildren();

    if (totalPages <= 1) {
        container.hidden =
            true;

        return;
    }

    container.hidden =
        false;

    /* 이전 */

    const previous =
        document.createElement(
            "button",
        );

    previous.type =
        "button";

    previous.textContent =
        "‹";

    previous.disabled =
        currentPage === 1;

    previous.setAttribute(
        "aria-label",
        "이전 페이지",
    );

    previous.addEventListener(
        "click",
        () => {
            if (
                currentPage > 1
            ) {
                onChange(
                    currentPage - 1,
                );
            }
        },
    );

    container.appendChild(
        previous,
    );

    /* 페이지 번호 */

    getPageNumbers(
        currentPage,
        totalPages,
    ).forEach(
        (page) => {
            if (
                page === "..."
            ) {
                const dots =
                    document.createElement(
                        "span",
                    );

                dots.className =
                    "mypage-pagination-ellipsis";

                dots.textContent =
                    "…";

                container.appendChild(
                    dots,
                );

                return;
            }

            const button =
                document.createElement(
                    "button",
                );

            button.type =
                "button";

            button.textContent =
                String(page);

            button.setAttribute(
                "aria-label",
                `${page}페이지`,
            );

            if (
                page ===
                currentPage
            ) {
                button.classList.add(
                    "is-current",
                );

                button.disabled =
                    true;

                button.setAttribute(
                    "aria-current",
                    "page",
                );
            }

            button.addEventListener(
                "click",
                () => {
                    onChange(page);
                },
            );

            container.appendChild(
                button,
            );
        },
    );

    /* 다음 */

    const next =
        document.createElement(
            "button",
        );

    next.type =
        "button";

    next.textContent =
        "›";

    next.disabled =
        currentPage ===
        totalPages;

    next.setAttribute(
        "aria-label",
        "다음 페이지",
    );

    next.addEventListener(
        "click",
        () => {
            if (
                currentPage <
                totalPages
            ) {
                onChange(
                    currentPage + 1,
                );
            }
        },
    );

    container.appendChild(
        next,
    );
}


/* =========================================================
   여행 상태
   ========================================================= */

function getTripStatusClass(
    status,
) {
    return (
        "trip-summary-status status-" +
        String(
            status || "",
        ).toLowerCase()
    );
}


function getTripStatusLabel(
    status,
) {
    return (
        tripStatusLabels[
            status
            ] ||
        status ||
        ""
    );
}


/* =========================================================
   대시보드 여행 카드
   ========================================================= */

function createTripPreviewCard(
    trip,
) {
    const button =
        document.createElement(
            "button",
        );

    button.type =
        "button";

    button.className =
        "trip-summary-card";

    button.dataset.route =
        `/trips/${trip.tripId}/schedule`;

    const copy =
        document.createElement(
            "span",
        );

    copy.className =
        "trip-summary-copy";

    const title =
        document.createElement(
            "strong",
        );

    title.textContent =
        trip.title ||
        trip.destinationName ||
        "이름 없는 여행";

    const meta =
        document.createElement(
            "span",
        );

    meta.textContent =
        [
            trip.destinationName,

            formatTripPeriod(
                trip.startDate,
                trip.endDate,
            ),
        ]
            .filter(Boolean)
            .join(" · ");

    const status =
        document.createElement(
            "em",
        );

    status.className =
        getTripStatusClass(
            trip.status,
        );

    status.textContent =
        getTripStatusLabel(
            trip.status,
        );

    copy.append(
        title,
        meta,
    );

    button.append(
        copy,
        status,
    );

    return button;
}


/* =========================================================
   여행이 없는 경우
   ========================================================= */

function createTripEmpty() {
    const wrapper =
        document.createElement(
            "div",
        );

    wrapper.className =
        "trip-empty";

    /* 아이콘 */

    const icon =
        document.createElement(
            "span",
        );

    icon.className =
        "trip-empty-icon";

    icon.textContent =
        "◇";

    icon.setAttribute(
        "aria-hidden",
        "true",
    );

    /* 제목 */

    const title =
        document.createElement(
            "strong",
        );

    title.textContent =
        "아직 계획한 여행지가 없습니다.";

    /* 설명 */

    const description =
        document.createElement(
            "p",
        );

    description.textContent =
        "여행 계획을 추가해 보세요.";

    /* 여행 계획 만들기 버튼 */

    const button =
        document.createElement(
            "button",
        );

    button.type =
        "button";

    button.className =
        "primary-button trip-create-button";

    button.dataset.route =
        "/trips/new/plan";

    button.textContent =
        "일정 만들기 →";

    wrapper.append(
        icon,
        title,
        description,
        button,
    );

    return wrapper;
}


/* =========================================================
   내 여행 전체보기 카드
   ========================================================= */

/* =========================================================
   여행에 딸린 예약

   계획과 예약이 서로 다른 곳에 있어서, 손님은 이 여행에 무엇을 예약해 뒀는지 알려면
   예약 화면을 따로 열어 여행을 다시 골라야 했다. 여행 카드에서 바로 펼쳐 본다.
   ========================================================= */

const BOOKING_GROUPS = [
    { type: "FLIGHT", label: "항공" },
    /* 서버가 쓰는 이름은 ACCOMMODATION이다. HOTEL로 거르면 숙소가 늘 0건으로 나온다. */
    { type: "ACCOMMODATION", label: "숙소" },
    { type: "TICKET", label: "티켓·액티비티" },
];

function formatBookingAmount(item) {
    if (item.amount === null || item.amount === undefined) return "요금 미제공";
    const currency = String(item.currency || "KRW").toUpperCase();
    const value = Number(item.amount).toLocaleString("ko-KR");
    return currency === "KRW" ? `${value}원` : `${value} ${currency}`;
}

/** 한 종류의 예약을 줄로 그린다. 없으면 왜 비었는지 적는다. */
function createBookingGroup(group, items) {
    const section = document.createElement("section");
    section.className = "trip-bookings-group";

    const head = document.createElement("h4");
    head.textContent = `${group.label} ${items.length}건`;
    section.appendChild(head);

    if (!items.length) {
        const empty = document.createElement("p");
        empty.className = "trip-bookings-empty";
        empty.textContent = "아직 예약하지 않았어요.";
        section.appendChild(empty);
        return section;
    }

    const list = document.createElement("ul");
    items.forEach((item) => {
        const row = document.createElement("li");

        const title = document.createElement("strong");
        title.textContent = item.title || group.label;

        const detail = document.createElement("span");
        /* 이용일이 있으면 함께 적는다. 무엇을 언제 쓰는지가 한 줄에 보여야 한다. */
        detail.textContent = [item.detail, item.usageDate].filter(Boolean).join(" · ");

        const status = document.createElement("em");
        status.className = "trip-bookings-status";
        status.textContent = item.statusLabel || item.status || "";

        const amount = document.createElement("b");
        amount.textContent = formatBookingAmount(item);
        /* 실습·샘플 금액은 실제 결제액이 아니다. 표시를 떼면 안 된다. */
        if (item.practice) amount.title = "실습 요금 · 실제 결제 금액 아님";

        row.append(title, detail, status, amount);
        list.appendChild(row);
    });

    section.appendChild(list);
    return section;
}

/** 펼쳤을 때 한 번만 읽는다. 접었다 펴는 것만으로 매번 부르지 않는다. */
async function loadTripBookings(tripId, box) {
    box.textContent = "예약을 불러오는 중이에요.";
    try {
        const summary = await request(`/api/v1/trips/${tripId}/booking-summary`);
        const items = summary?.items || [];

        const wrap = document.createElement("div");
        wrap.className = "trip-bookings-body";

        const progress = summary?.progress;
        if (progress) {
            const line = document.createElement("p");
            line.className = "trip-bookings-progress";
            line.textContent = `예약 진행 ${progress.done} / ${progress.total}`;
            wrap.appendChild(line);
        }

        BOOKING_GROUPS.forEach((group) => {
            wrap.appendChild(createBookingGroup(
                group,
                items.filter((item) => item.type === group.type),
            ));
        });

        const money = summary?.money;
        if (money && money.estimatedTotal !== null && money.estimatedTotal !== undefined) {
            const total = document.createElement("p");
            total.className = "trip-bookings-total";
            /* 화면 비교용 스냅샷 합계다. 실제 결제액이라고 읽히면 안 된다. */
            total.textContent = `예상 합계 ${Number(money.estimatedTotal).toLocaleString("ko-KR")}원`
                + " · 실제 결제 금액과 다를 수 있어요";
            wrap.appendChild(total);
        }

        box.replaceChildren(wrap);
    } catch (error) {
        box.textContent = "예약을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.";
    }
}

/** 여행 카드 안에서 예약을 펼쳐 보는 자리. */
function createTripBookings(trip) {
    const wrap = document.createElement("div");
    wrap.className = "trip-bookings";

    const toggle = document.createElement("button");
    toggle.type = "button";
    toggle.className = "trip-bookings-toggle";
    toggle.textContent = "예약 내역 보기";
    toggle.setAttribute("aria-expanded", "false");

    const box = document.createElement("div");
    box.className = "trip-bookings-panel";
    box.hidden = true;

    let loaded = false;

    toggle.addEventListener("click", (event) => {
        /*
         * 카드 전체가 일정으로 가는 링크다. 여기서 막지 않으면 펼치려다 화면이 넘어간다.
         */
        event.preventDefault();
        event.stopPropagation();

        const open = box.hidden;
        box.hidden = !open;
        toggle.setAttribute("aria-expanded", String(open));
        toggle.textContent = open ? "예약 내역 접기" : "예약 내역 보기";

        if (open && !loaded) {
            loaded = true;
            void loadTripBookings(trip.tripId, box);
        }
    });

    wrap.append(toggle, box);
    return wrap;
}

function createTripFullCard(
    trip,
    onDelete,
) {
    const card =
        document.createElement(
            "div",
        );

    card.className =
        "trip-full-card";

    card.dataset.route =
        `/trips/${trip.tripId}/schedule`;

    card.tabIndex = 0;

    card.setAttribute(
        "role",
        "link",
    );

    card.setAttribute(
        "aria-label",
        `${trip.title || trip.destinationName || "이름 없는 여행"} 일정 열기`,
    );

    card.addEventListener(
        "keydown",
        (event) => {
            if (
                event.target === card &&
                (
                    event.key === "Enter" ||
                    event.key === " "
                )
            ) {
                event.preventDefault();
                card.click();
            }
        },
    );

    const top =
        document.createElement(
            "div",
        );

    top.className =
        "trip-full-card-top";

    const title =
        document.createElement(
            "strong",
        );

    title.className =
        "trip-full-card-title";

    title.textContent =
        trip.title ||
        trip.destinationName ||
        "이름 없는 여행";

    /*
     * 종료일이 지난 확정 여행은 완료로 보여 준다. COMPLETED로 바꾸는 코드가 없어
     * 상태만 보면 다녀온 뒤에도 "확정"에 머문다. 판단 규칙은 core/trip-status.js에 있다.
     */
    const finished =
        window.AllMyTripsTripStatus
            ?.isTripFinished(trip) ===
        true;

    const shownStatus =
        finished
            ? "COMPLETED"
            : trip.status;

    const status =
        document.createElement(
            "em",
        );

    status.className =
        getTripStatusClass(
            shownStatus,
        );

    status.textContent =
        getTripStatusLabel(
            shownStatus,
        );

    const actions =
        document.createElement(
            "div",
        );

    actions.className =
        "trip-full-card-actions";

    const deleteButton =
        document.createElement(
            "button",
        );

    deleteButton.type =
        "button";

    deleteButton.className =
        "trip-full-card-delete";

    deleteButton.dataset.noGlobalLoading =
        "";

    deleteButton.textContent =
        "×";

    deleteButton.setAttribute(
        "aria-label",
        `${title.textContent} 삭제`,
    );

    deleteButton.addEventListener(
        "click",
        (event) => {
            event.preventDefault();
            event.stopPropagation();
            onDelete?.(
                trip,
                deleteButton,
            );
        },
    );

    actions.append(
        status,
        deleteButton,
    );

    top.append(
        title,
        actions,
    );

    const location =
        document.createElement(
            "p",
        );

    location.className =
        "trip-full-card-location";

    location.textContent =
        trip.destinationName ||
        "여행지 미정";

    const period =
        document.createElement(
            "span",
        );

    period.className =
        "trip-full-card-period";

    period.textContent =
        formatTripPeriod(
            trip.startDate,
            trip.endDate,
        ) ||
        "여행 기간 미정";

    const bottom =
        document.createElement(
            "div",
        );

    bottom.className =
        "trip-full-card-bottom";

    const arrow =
        document.createElement(
            "span",
        );

    arrow.className =
        "trip-full-card-arrow";

    arrow.textContent =
        "→";

    arrow.setAttribute(
        "aria-hidden",
        "true",
    );

    /*
     * 다녀온 여행에서만 기록으로 갈 수 있다. 카드 전체가 일정으로 가는 링크라
     * 여기서 막지 않으면 기록을 누르려다 일정으로 넘어간다.
     */
    if (finished) {
        const recordButton =
            document.createElement(
                "button",
            );

        recordButton.type =
            "button";

        recordButton.className =
            "trip-full-card-record";

        recordButton.dataset.noGlobalLoading =
            "";

        recordButton.textContent =
            "여행 기록";

        recordButton.setAttribute(
            "aria-label",
            `${title.textContent} 여행 기록`,
        );

        recordButton.addEventListener(
            "click",
            (event) => {
                event.preventDefault();
                event.stopPropagation();
                window.location.href =
                    `/trips/${trip.tripId}/record`;
            },
        );

        bottom.appendChild(
            recordButton,
        );
    }

    bottom.appendChild(
        arrow,
    );

    card.append(
        top,
        location,
        period,
        bottom,
        createTripBookings(trip),
    );

    return card;
}


/* =========================================================
   여행 삭제 확인
   ========================================================= */

function confirmTripDelete(
    trip,
) {
    return new Promise(
        (resolve) => {
            const overlay =
                document.createElement(
                    "div",
                );

            overlay.className =
                "trip-delete-overlay";

            const dialog =
                document.createElement(
                    "section",
                );

            dialog.className =
                "trip-delete-dialog";

            dialog.setAttribute(
                "role",
                "alertdialog",
            );

            dialog.setAttribute(
                "aria-modal",
                "true",
            );

            dialog.setAttribute(
                "aria-labelledby",
                "trip-delete-title",
            );

            const title =
                document.createElement(
                    "h2",
                );

            title.id =
                "trip-delete-title";

            title.textContent =
                "여행을 삭제할까요?";

            const tripName =
                document.createElement(
                    "strong",
                );

            tripName.className =
                "trip-delete-dialog-name";

            tripName.textContent =
                trip.title ||
                trip.destinationName ||
                "이름 없는 여행";

            const description =
                document.createElement(
                    "p",
                );

            description.textContent =
                "삭제한 여행과 일정은 복구할 수 없습니다.";

            const footer =
                document.createElement(
                    "div",
                );

            footer.className =
                "trip-delete-dialog-actions";

            const cancelButton =
                document.createElement(
                    "button",
                );

            cancelButton.type =
                "button";

            cancelButton.className =
                "trip-delete-dialog-cancel";

            cancelButton.textContent =
                "취소";

            const confirmButton =
                document.createElement(
                    "button",
                );

            confirmButton.type =
                "button";

            confirmButton.className =
                "trip-delete-dialog-confirm";

            confirmButton.textContent =
                "여행 삭제";

            let closed = false;

            const close =
                (confirmed) => {
                    if (closed) {
                        return;
                    }

                    closed = true;
                    document.removeEventListener(
                        "keydown",
                        onKeydown,
                    );
                    overlay.remove();
                    resolve(confirmed);
                };

            const onKeydown =
                (event) => {
                    if (
                        event.key ===
                        "Escape"
                    ) {
                        close(false);
                    }
                };

            cancelButton.addEventListener(
                "click",
                () => close(false),
            );

            confirmButton.addEventListener(
                "click",
                () => close(true),
            );

            overlay.addEventListener(
                "click",
                (event) => {
                    if (
                        event.target ===
                        overlay
                    ) {
                        close(false);
                    }
                },
            );

            document.addEventListener(
                "keydown",
                onKeydown,
            );

            footer.append(
                cancelButton,
                confirmButton,
            );

            dialog.append(
                title,
                tripName,
                description,
                footer,
            );

            overlay.appendChild(
                dialog,
            );

            document.body.appendChild(
                overlay,
            );

            cancelButton.focus();
        },
    );
}


/* =========================================================
   내 여행 전체보기 빈 화면
   ========================================================= */

function createTripAllEmpty() {
    const wrapper =
        document.createElement(
            "div",
        );

    wrapper.className =
        "trip-all-empty";

    const icon =
        document.createElement(
            "span",
        );

    icon.className =
        "trip-empty-icon";

    icon.textContent =
        "◇";

    icon.setAttribute(
        "aria-hidden",
        "true",
    );

    const title =
        document.createElement(
            "strong",
        );

    title.textContent =
        "아직 계획한 여행지가 없습니다.";

    const description =
        document.createElement(
            "p",
        );

    description.textContent =
        "새로운 여행 계획을 만들어 보세요.";

    const button =
        document.createElement(
            "button",
        );

    button.type =
        "button";

    button.className =
        "primary-button trip-create-button";

    button.dataset.route =
        "/trips/new/plan";

    button.textContent =
        "여행 계획 만들기 →";

    wrapper.append(
        icon,
        title,
        description,
        button,
    );

    return wrapper;
}


/* =========================================================
   INIT
   ========================================================= */

export async function initTrips() {
    const tripPreviewList =
        document.querySelector(
            "[data-trip-list]",
        );

    const tripAllList =
        document.querySelector(
            "[data-trip-all-list]",
        );

    const tripCount =
        document.querySelector(
            "[data-trip-count]",
        );

    const tripTotalCount =
        document.querySelector(
            "[data-trip-total-count]",
        );

    const tripMoreButton =
        document.querySelector(
            "[data-trip-more]",
        );

    const tripPagination =
        document.querySelector(
            "[data-trip-pagination]",
        );

    const tripsView =
        document.querySelector(
            "[data-trips-view]",
        );

    let tripItems = [];

    let currentTripPage =
        1;

    function updateTripCounts() {
        if (tripCount) {
            tripCount.textContent =
                `${tripItems.length}개`;
        }

        if (tripTotalCount) {
            tripTotalCount.textContent =
                `${tripItems.length}개`;
        }
    }

    async function deleteTrip(
        trip,
        deleteButton,
    ) {
        const confirmed =
            await confirmTripDelete(
                trip,
            );

        if (!confirmed) {
            return;
        }

        deleteButton.disabled =
            true;

        deleteButton.classList.add(
            "is-loading",
        );

        try {
            await request(
                `/api/v1/trips/${encodeURIComponent(trip.tripId)}`,
                {
                    method:
                        "DELETE",
                },
            );

            tripItems =
                tripItems.filter(
                    (item) =>
                        String(
                            item.tripId,
                        ) !==
                        String(
                            trip.tripId,
                        ),
                );

            updateTripCounts();
            renderTripPreview();
            renderTripAll();

            showToast(
                "여행이 삭제되었습니다.",
            );
        } catch (error) {
            deleteButton.disabled =
                false;

            deleteButton.classList.remove(
                "is-loading",
            );

            showToast(
                error.message ||
                "여행을 삭제하지 못했습니다.",
            );
        }
    }


    /* =====================================================
       대시보드 최근 여행
       ===================================================== */

    function renderTripPreview() {
        if (!tripPreviewList) {
            return;
        }

        tripPreviewList.replaceChildren();

        /*
         * 여행 일정이 하나도 없는 경우
         */
        if (
            tripItems.length === 0
        ) {
            tripPreviewList.appendChild(
                createTripEmpty(),
            );

            if (tripMoreButton) {
                tripMoreButton.hidden =
                    true;
            }

            return;
        }

        /*
         * 여행 일정이 있는 경우
         */
        tripItems
            .slice(
                0,
                TRIP_PREVIEW_COUNT,
            )
            .forEach(
                (trip) => {
                    tripPreviewList.appendChild(
                        createTripPreviewCard(
                            trip,
                        ),
                    );
                },
            );

        if (tripMoreButton) {
            tripMoreButton.hidden =
                false;
        }
    }


    /* =====================================================
       내 여행 전체보기
       ===================================================== */

    function renderTripAll() {
        if (!tripAllList) {
            return;
        }

        tripAllList.replaceChildren();

        /*
         * 여행 일정이 없는 경우
         */
        if (
            tripItems.length === 0
        ) {
            tripAllList.appendChild(
                createTripAllEmpty(),
            );

            if (tripPagination) {
                tripPagination.hidden =
                    true;

                tripPagination.replaceChildren();
            }

            return;
        }

        const totalPages =
            Math.ceil(
                tripItems.length /
                TRIP_PAGE_SIZE,
            );

        if (
            currentTripPage >
            totalPages
        ) {
            currentTripPage =
                totalPages;
        }

        if (
            currentTripPage < 1
        ) {
            currentTripPage =
                1;
        }

        const start =
            (
                currentTripPage -
                1
            ) *
            TRIP_PAGE_SIZE;

        const pageItems =
            tripItems.slice(
                start,
                start +
                TRIP_PAGE_SIZE,
            );

        pageItems.forEach(
            (trip) => {
                tripAllList.appendChild(
                    createTripFullCard(
                        trip,
                        deleteTrip,
                    ),
                );
            },
        );

        renderPagination(
            tripPagination,
            currentTripPage,
            totalPages,
            (page) => {
                currentTripPage =
                    page;

                renderTripAll();

                tripsView
                    ?.scrollIntoView({
                        behavior:
                            "smooth",

                        block:
                            "start",
                    });
            },
        );
    }


    /* =====================================================
       여행 데이터 조회
       ===================================================== */

    try {
        const trips =
            await request(
                "/api/v1/trips",
            );

        tripItems =
            Array.isArray(
                trips,
            )
                ? trips.slice()
                : [];

        /*
         * 최근 생성된 여행부터 정렬
         */
        tripItems.sort(
            (a, b) => {
                return String(
                    b.createdAt ||
                    "",
                ).localeCompare(
                    String(
                        a.createdAt ||
                        "",
                    ),
                );
            },
        );

        currentTripPage =
            1;

        /*
         * 개수 표시
         */
        updateTripCounts();

        /*
         * 화면 렌더링
         */
        renderTripPreview();
        renderTripAll();

    } catch (error) {
        console.error(
            "여행 조회 실패:",
            error,
        );

        /*
         * API 오류는 빈 목록과 구분해서 표시
         */
        const targets =
            [
                tripPreviewList,
                tripAllList,
            ].filter(Boolean);

        targets.forEach(
            (target) => {
                target.replaceChildren();

                const state =
                    document.createElement(
                        "p",
                    );

                state.className =
                    "mypage-state error";

                state.textContent =
                    "여행 일정을 불러오지 못했습니다.";

                target.appendChild(
                    state,
                );
            },
        );

        if (tripCount) {
            tripCount.textContent =
                "—";
        }

        if (tripTotalCount) {
            tripTotalCount.textContent =
                "—";
        }

        if (tripMoreButton) {
            tripMoreButton.hidden =
                true;
        }

        if (tripPagination) {
            tripPagination.hidden =
                true;

            tripPagination.replaceChildren();
        }
    }
}
