import {
    initAccount,
} from "./mypage-account.js";

import {
    initTrips,
} from "./mypage-trips.js";

import {
    initFavorites,
} from "./mypage-favorites.js";

import {
    initReviews,
} from "./mypage-reviews.js";

document.addEventListener(
    "DOMContentLoaded",
    () => {
        const dashboardView =
            document.querySelector(
                "[data-dashboard-view]",
            );

        const tripsView =
            document.querySelector(
                "[data-trips-view]",
            );

        const favoritesView =
            document.querySelector(
                "[data-favorites-view]",
            );

        const reviewsView =
            document.querySelector(
                "[data-reviews-view]",
            );

        const settingsView =
            document.querySelector(
                "[data-settings-view]",
            );

        const dashboardButton =
            document.querySelector(
                "[data-open-dashboard]",
            );

        const openTripButtons =
            document.querySelectorAll(
                "[data-open-trips]",
            );

        const openFavoriteButtons =
            document.querySelectorAll(
                "[data-open-favorites]",
            );

        const openReviewButtons =
            document.querySelectorAll(
                "[data-open-reviews]",
            );

        const openSettingsButton =
            document.querySelector(
                "[data-open-settings]",
            );

        const closeTripButton =
            document.querySelector(
                "[data-close-trips]",
            );

        const closeFavoriteButton =
            document.querySelector(
                "[data-close-favorites]",
            );

        const closeReviewButton =
            document.querySelector(
                "[data-close-reviews]",
            );

        const closeSettingsButton =
            document.querySelector(
                "[data-close-settings]",
            );

        function setCurrent(
            button,
            active,
        ) {
            if (!button) {
                return;
            }

            button.classList.toggle(
                "is-current",
                active,
            );

            if (active) {
                button.setAttribute(
                    "aria-current",
                    "page",
                );
            } else {
                button.removeAttribute(
                    "aria-current",
                );
            }
        }

        function setCurrentMenu(
            view,
        ) {
            setCurrent(
                dashboardButton,
                view ===
                "dashboard",
            );

            openTripButtons
                .forEach(
                    (button) => {
                        setCurrent(
                            button,
                            view ===
                            "trips",
                        );
                    },
                );

            openFavoriteButtons
                .forEach(
                    (button) => {
                        setCurrent(
                            button,
                            view ===
                            "favorites",
                        );
                    },
                );

            openReviewButtons
                .forEach(
                    (button) => {
                        setCurrent(
                            button,
                            view === "reviews",
                        );
                    },
                );

            setCurrent(
                openSettingsButton,
                view ===
                "settings",
            );
        }

        function updateViewUrl(
            view,
        ) {
            const url =
                new URL(
                    window.location.href,
                );

            if (
                view ===
                "dashboard"
            ) {
                url.searchParams.delete(
                    "view",
                );
            } else {
                url.searchParams.set(
                    "view",
                    view,
                );
            }

            window.history.pushState(
                {
                    view,
                },
                "",
                url,
            );
        }

        function applyView(
            view,
            options = {},
        ) {
            const {
                updateUrl = true,
                scroll = true,
            } = options;

            if (dashboardView) {
                dashboardView.hidden =
                    view !==
                    "dashboard";
            }

            if (tripsView) {
                tripsView.hidden =
                    view !==
                    "trips";
            }

            if (favoritesView) {
                favoritesView.hidden =
                    view !==
                    "favorites";
            }

            if (reviewsView) {
                reviewsView.hidden =
                    view !== "reviews";
            }

            if (settingsView) {
                settingsView.hidden =
                    view !==
                    "settings";
            }

            setCurrentMenu(view);

            if (updateUrl) {
                updateViewUrl(view);
            }

            if (!scroll) {
                return;
            }

            const target =
                view === "trips"
                    ? tripsView
                    : view ===
                    "favorites"
                        ? favoritesView
                        : view === "reviews"
                            ? reviewsView
                        : view ===
                        "settings"
                            ? settingsView
                            : dashboardView;

            target?.scrollIntoView({
                behavior:
                    "smooth",
                block:
                    "start",
            });
        }

        function applyViewFromUrl() {
            const params =
                new URLSearchParams(
                    window.location.search,
                );

            const value =
                params.get(
                    "view",
                );

            const view =
                [
                    "trips",
                    "favorites",
                    "reviews",
                    "settings",
                ].includes(value)
                    ? value
                    : "dashboard";

            applyView(
                view,
                {
                    updateUrl:
                        false,
                    scroll:
                        false,
                },
            );
        }

        dashboardButton
            ?.addEventListener(
                "click",
                () => {
                    applyView(
                        "dashboard",
                    );
                },
            );

        openTripButtons
            .forEach(
                (button) => {
                    button
                        .addEventListener(
                            "click",
                            () => {
                                applyView(
                                    "trips",
                                );
                            },
                        );
                },
            );

        openFavoriteButtons
            .forEach(
                (button) => {
                    button
                        .addEventListener(
                            "click",
                            () => {
                                applyView(
                                    "favorites",
                                );
                            },
                        );
                },
            );

        openReviewButtons
            .forEach(
                (button) => {
                    button.addEventListener(
                        "click",
                        () => {
                            applyView("reviews");
                        },
                    );
                },
            );

        openSettingsButton
            ?.addEventListener(
                "click",
                () => {
                    applyView(
                        "settings",
                    );
                },
            );

        closeTripButton
            ?.addEventListener(
                "click",
                () => {
                    applyView(
                        "dashboard",
                    );
                },
            );

        closeFavoriteButton
            ?.addEventListener(
                "click",
                () => {
                    applyView(
                        "dashboard",
                    );
                },
            );

        closeReviewButton
            ?.addEventListener(
                "click",
                () => {
                    applyView("dashboard");
                },
            );

        closeSettingsButton
            ?.addEventListener(
                "click",
                () => {
                    applyView(
                        "dashboard",
                    );
                },
            );

        window.addEventListener(
            "popstate",
            applyViewFromUrl,
        );

        /*
         * data-route를 가진 동적 카드 이동.
         *
         * 여행/찜 카드는 JS에서 나중에 생성되므로
         * 각 카드마다 이벤트를 반복 등록하지 않고
         * document에서 이벤트 위임 처리한다.
         */
        document.addEventListener(
            "click",
            (event) => {
                const target =
                    event.target.closest(
                        "button[data-route], a[data-route]",
                    );

                if (!target) {
                    return;
                }

                const route =
                    target.dataset.route;

                if (!route) {
                    return;
                }

                window.location.href =
                    route;
            },
        );

        applyViewFromUrl();

        Promise.allSettled([
            initAccount(),
            initTrips(),
            initFavorites(),
            initReviews(),
        ]).then(
            () => {
                document.body
                    .dataset
                    .pageReady =
                    "true";
            },
        );
    },
);
