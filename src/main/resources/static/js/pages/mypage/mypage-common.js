export function showToast(message) {
    if (window.AllMyTripsModal?.showToast) {
        window.AllMyTripsModal.showToast(message);
        return;
    }

    alert(message);
}

function getCookie(name) {
    const target = encodeURIComponent(name) + "=";

    const cookie = document.cookie
        .split(";")
        .map((item) => item.trim())
        .find((item) => item.startsWith(target));

    return cookie
        ? decodeURIComponent(
            cookie.substring(target.length),
        )
        : "";
}

export async function request(
    url,
    options = {},
) {
    const method = String(
        options.method || "GET",
    ).toUpperCase();

    const headers = {
        Accept: "application/json",

        ...(options.body
            ? {
                "Content-Type":
                    "application/json",
            }
            : {}),

        ...(options.headers || {}),
    };

    if (
        ![
            "GET",
            "HEAD",
            "OPTIONS",
            "TRACE",
        ].includes(method)
    ) {
        const csrfToken =
            getCookie(
                "CSRF-TOKEN",
            );

        if (csrfToken) {
            headers[
                "X-CSRF-TOKEN"
                ] =
                csrfToken;
        }
    }

    const controller =
        new AbortController();

    const timeoutId =
        window.setTimeout(
            () => {
                controller.abort();
            },
            10000,
        );

    try {
        const response =
            await fetch(
                url,
                {
                    ...options,
                    method,
                    credentials:
                        "same-origin",
                    headers,
                    signal:
                    controller.signal,
                },
            );

        const result =
            await response
                .json()
                .catch(
                    () => null,
                );

        if (
            response.status ===
            401
        ) {
            const error =
                new Error(
                    result?.message ||
                    "로그인이 필요합니다.",
                );

            error.code =
                result?.code ||
                "UNAUTHORIZED";

            throw error;
        }

        if (
            !response.ok ||
            !result?.success
        ) {
            const error =
                new Error(
                    result?.message ||
                    "요청을 처리하지 못했습니다.",
                );

            error.code =
                result?.code ||
                "";

            error.status =
                response.status;

            throw error;
        }

        return result.data;
    } catch (error) {
        if (
            error.name ===
            "AbortError"
        ) {
            const timeoutError =
                new Error(
                    "서버 응답이 지연되고 있습니다. 잠시 후 다시 시도해주세요.",
                );

            timeoutError.code =
                "REQUEST_TIMEOUT";

            throw timeoutError;
        }

        throw error;
    } finally {
        window.clearTimeout(
            timeoutId,
        );
    }
}

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

export function renderPagination(
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
        container.hidden = true;
        return;
    }

    container.hidden = false;

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
            if (currentPage > 1) {
                onChange(
                    currentPage - 1,
                );
            }
        },
    );

    container.appendChild(
        previous,
    );

    getPageNumbers(
        currentPage,
        totalPages,
    ).forEach(
        (page) => {
            if (page === "...") {
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
/**
 * 행정구역 이름을 카드에 들어갈 만큼 줄인다.
 *
 * <p>카카오가 주는 지역은 "서울특별시", "제주특별자치도"처럼 길어서 좁은 카드에서 잘린다.
 * 사람이 알아보는 최소 단위로 줄인다. "경기도 수원시"처럼 도 단위면 시·군 이름이 더 쓸모 있다.
 *
 * <p>찜한 여행지와 최근 본 여행지가 같은 장소를 서로 다르게 적으면 다른 곳으로 읽힌다.
 * 그래서 두 패널이 이 함수를 함께 쓴다.
 */
export function normalizeCityName(region) {
    const value = String(region || "").trim();
    if (!value) return "";

    const parts = value.split(/\s+/);
    const first = parts[0] || "";
    const second = parts[1] || "";

    if (first.includes("특별시") || first.includes("광역시")) {
        return first.replace("특별시", "").replace("광역시", "");
    }

    if (first.includes("제주특별자치도")) return "제주";

    /* "경기도 수원시"는 "경기"보다 "수원"이 알아보기 쉽다. */
    if (first.endsWith("도") && second) {
        return second.replace(/시$/, "").replace(/군$/, "");
    }

    return first
        .replace("특별자치시", "")
        .replace("특별자치도", "")
        .replace("특별시", "")
        .replace("광역시", "")
        .replace(/시$/, "")
        .replace(/군$/, "");
}
