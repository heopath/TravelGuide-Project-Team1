import {
    request,
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

function createTripFullCard(
    trip,
) {
    const card =
        document.createElement(
            "button",
        );

    card.type =
        "button";

    card.className =
        "trip-full-card";

    card.dataset.route =
        `/trips/${trip.tripId}/schedule`;

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

    top.append(
        title,
        status,
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

    bottom.appendChild(
        arrow,
    );

    card.append(
        top,
        location,
        period,
        bottom,
    );

    return card;
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
        if (tripCount) {
            tripCount.textContent =
                `${tripItems.length}개`;
        }

        if (tripTotalCount) {
            tripTotalCount.textContent =
                `${tripItems.length}개`;
        }

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