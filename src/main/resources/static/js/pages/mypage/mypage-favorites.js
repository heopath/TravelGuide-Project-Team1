import {
    request,
    renderPagination,
} from "./mypage-common.js";

const FAVORITE_PREVIEW_COUNT = 4;
const FAVORITE_PAGE_SIZE = 25;
const FAVORITE_PRIMARY_REGION_COUNT = 5;

const categoryLabels = {
    ATTRACTION: "관광지",
    RESTAURANT: "맛집",
    CAFE: "카페",
    ACCOMMODATION: "숙소",
    FESTIVAL: "축제",
    ACTIVITY: "체험",
    TRANSPORT: "교통",
};

export function initFavorites() {
    const favoritePreviewList =
        document.querySelector(
            "[data-favorite-list]",
        );

    const favoriteAllList =
        document.querySelector(
            "[data-favorite-all-list]",
        );

    const favoriteCount =
        document.querySelector(
            "[data-favorite-count]",
        );

    const favoriteTotalCount =
        document.querySelector(
            "[data-favorite-total-count]",
        );

    const favoriteMoreButton =
        document.querySelector(
            "[data-favorite-more]",
        );

    const favoritePagination =
        document.querySelector(
            "[data-favorite-pagination]",
        );

    const favoriteSearchInput =
        document.querySelector(
            "[data-favorite-search]",
        );

    const favoriteSearchClear =
        document.querySelector(
            "[data-favorite-search-clear]",
        );

    const favoriteRegionFilters =
        document.querySelector(
            "[data-favorite-region-filters]",
        );

    const favoriteRegionMore =
        document.querySelector(
            "[data-favorite-region-more]",
        );

    const favoriteResultSummary =
        document.querySelector(
            "[data-favorite-result-summary]",
        );

    const favoritesView =
        document.querySelector(
            "[data-favorites-view]",
        );

    let favoriteItems = [];
    let filteredFavoriteItems = [];

    let currentFavoritePage = 1;

    let favoriteSearchKeyword =
        "";

    let selectedFavoriteRegion =
        "ALL";

    function normalizeCityName(
        region,
    ) {
        const value =
            String(
                region || "",
            ).trim();

        if (!value) {
            return "";
        }

        const parts =
            value.split(/\s+/);

        const first =
            parts[0] || "";

        const second =
            parts[1] || "";

        if (
            first.includes(
                "특별시",
            ) ||
            first.includes(
                "광역시",
            )
        ) {
            return first
                .replace(
                    "특별시",
                    "",
                )
                .replace(
                    "광역시",
                    "",
                );
        }

        if (
            first.includes(
                "제주특별자치도",
            )
        ) {
            return "제주";
        }

        if (
            first.endsWith("도") &&
            second
        ) {
            return second
                .replace(
                    /시$/,
                    "",
                )
                .replace(
                    /군$/,
                    "",
                );
        }

        return first
            .replace(
                "특별자치시",
                "",
            )
            .replace(
                "특별자치도",
                "",
            )
            .replace(
                "특별시",
                "",
            )
            .replace(
                "광역시",
                "",
            )
            .replace(
                /시$/,
                "",
            )
            .replace(
                /군$/,
                "",
            );
    }

    function createFavoriteCard(
        favorite,
    ) {
        const article =
            document.createElement(
                "article",
            );

        article.className =
            "favorite-place-card";

        const button =
            document.createElement(
                "button",
            );

        button.type =
            "button";

        button.dataset.route =
            `/guide/places/${favorite.placeId}`;

        const imageBox =
            document.createElement(
                "div",
            );

        imageBox.className =
            "favorite-place-image";

        if (
            favorite.primaryImageUrl
        ) {
            const image =
                document.createElement(
                    "img",
                );

            image.src =
                favorite.primaryImageUrl;

            image.alt =
                favorite.placeName ||
                "";

            image.loading =
                "lazy";

            imageBox.appendChild(
                image,
            );
        } else {
            const fallback =
                document.createElement(
                    "span",
                );

            fallback.textContent =
                "♡";

            fallback.setAttribute(
                "aria-hidden",
                "true",
            );

            imageBox.appendChild(
                fallback,
            );
        }

        const copy =
            document.createElement(
                "div",
            );

        copy.className =
            "favorite-place-copy";

        const name =
            document.createElement(
                "strong",
            );

        name.textContent =
            favorite.placeName ||
            "이름 없는 여행지";

        const meta =
            document.createElement(
                "span",
            );

        meta.textContent =
            [
                normalizeCityName(
                    favorite.region,
                ),

                categoryLabels[
                    favorite.category
                    ] ||
                favorite.category,
            ]
                .filter(Boolean)
                .join(" · ");

        copy.append(
            name,
            meta,
        );

        if (favorite.memo) {
            const memo =
                document.createElement(
                    "small",
                );

            memo.textContent =
                favorite.memo;

            copy.appendChild(
                memo,
            );
        }

        button.append(
            imageBox,
            copy,
        );

        article.appendChild(
            button,
        );

        return article;
    }

    function createFavoriteEmpty() {
        const wrapper =
            document.createElement(
                "div",
            );

        wrapper.className =
            "favorite-empty";

        const icon =
            document.createElement(
                "span",
            );

        icon.className =
            "favorite-empty-icon";

        icon.textContent =
            "♡";

        const title =
            document.createElement(
                "strong",
            );

        title.textContent =
            "아직 찜한 여행지가 없습니다.";

        const text =
            document.createElement(
                "p",
            );

        text.textContent =
            "추천 장소에서 관심 있는 여행지를 추가해보세요.";

        const button =
            document.createElement(
                "button",
            );

        button.type =
            "button";

        button.className =
            "primary-button";

        button.dataset.route =
            "/guide";

        button.textContent =
            "추천 장소 보기 →";

        wrapper.append(
            icon,
            title,
            text,
            button,
        );

        return wrapper;
    }

    function createSearchEmpty() {
        const wrapper =
            document.createElement(
                "div",
            );

        wrapper.className =
            "favorite-search-empty";

        const title =
            document.createElement(
                "strong",
            );

        title.textContent =
            "검색 결과가 없습니다.";

        const text =
            document.createElement(
                "p",
            );

        text.textContent =
            "다른 도시 또는 여행지 이름으로 검색해보세요.";

        const button =
            document.createElement(
                "button",
            );

        button.type =
            "button";

        button.className =
            "outline-button";

        button.textContent =
            "검색 초기화";

        button.addEventListener(
            "click",
            resetFavoriteFilters,
        );

        wrapper.append(
            title,
            text,
            button,
        );

        return wrapper;
    }

    function getRegionCounts() {
        const counts =
            new Map();

        favoriteItems.forEach(
            (favorite) => {
                const city =
                    normalizeCityName(
                        favorite.region,
                    );

                if (!city) {
                    return;
                }

                counts.set(
                    city,
                    (counts.get(city) || 0) +
                    1,
                );
            },
        );

        return [
            ...counts.entries(),
        ].sort(
            (a, b) =>
                b[1] - a[1],
        );
    }

    function createRegionButton(
        name,
        count,
        value,
    ) {
        const button =
            document.createElement(
                "button",
            );

        button.type =
            "button";

        button.className =
            "favorite-region-button";

        button.textContent =
            `${name} ${count}`;

        if (
            selectedFavoriteRegion ===
            value
        ) {
            button.classList.add(
                "is-current",
            );
        }

        button.addEventListener(
            "click",
            () => {
                selectedFavoriteRegion =
                    value;

                currentFavoritePage =
                    1;

                if (
                    favoriteRegionMore
                ) {
                    favoriteRegionMore.value =
                        "";
                }

                applyFavoriteFilters();
            },
        );

        return button;
    }

    function renderFavoriteRegions() {
        if (
            !favoriteRegionFilters
        ) {
            return;
        }

        favoriteRegionFilters
            .replaceChildren();

        const regions =
            getRegionCounts();

        favoriteRegionFilters
            .appendChild(
                createRegionButton(
                    "전체",
                    favoriteItems.length,
                    "ALL",
                ),
            );

        regions
            .slice(
                0,
                FAVORITE_PRIMARY_REGION_COUNT,
            )
            .forEach(
                ([region, count]) => {
                    favoriteRegionFilters
                        .appendChild(
                            createRegionButton(
                                region,
                                count,
                                region,
                            ),
                        );
                },
            );

        if (!favoriteRegionMore) {
            return;
        }

        const extras =
            regions.slice(
                FAVORITE_PRIMARY_REGION_COUNT,
            );

        favoriteRegionMore
            .replaceChildren();

        const defaultOption =
            document.createElement(
                "option",
            );

        defaultOption.value =
            "";

        defaultOption.textContent =
            "더보기";

        favoriteRegionMore
            .appendChild(
                defaultOption,
            );

        extras.forEach(
            ([region, count]) => {
                const option =
                    document
                        .createElement(
                            "option",
                        );

                option.value =
                    region;

                option.textContent =
                    `${region} ${count}`;

                favoriteRegionMore
                    .appendChild(
                        option,
                    );
            },
        );

        favoriteRegionMore.hidden =
            extras.length === 0;
    }

    function renderFavoritePreview() {
        if (!favoritePreviewList) {
            return;
        }

        favoritePreviewList
            .replaceChildren();

        if (!favoriteItems.length) {
            favoritePreviewList
                .appendChild(
                    createFavoriteEmpty(),
                );

            if (favoriteMoreButton) {
                favoriteMoreButton.hidden =
                    true;
            }

            return;
        }

        favoriteItems
            .slice(
                0,
                FAVORITE_PREVIEW_COUNT,
            )
            .forEach(
                (favorite) => {
                    favoritePreviewList
                        .appendChild(
                            createFavoriteCard(
                                favorite,
                            ),
                        );
                },
            );

        if (favoriteMoreButton) {
            favoriteMoreButton.hidden =
                false;
        }
    }

    function renderFavoriteAll() {
        if (!favoriteAllList) {
            return;
        }

        favoriteAllList
            .replaceChildren();

        if (!favoriteItems.length) {
            favoriteAllList
                .appendChild(
                    createFavoriteEmpty(),
                );

            if (favoritePagination) {
                favoritePagination.hidden =
                    true;

                favoritePagination
                    .replaceChildren();
            }

            return;
        }

        if (
            !filteredFavoriteItems
                .length
        ) {
            favoriteAllList
                .appendChild(
                    createSearchEmpty(),
                );

            if (favoritePagination) {
                favoritePagination.hidden =
                    true;

                favoritePagination
                    .replaceChildren();
            }

            return;
        }

        const totalPages =
            Math.ceil(
                filteredFavoriteItems
                    .length /
                FAVORITE_PAGE_SIZE,
            );

        if (
            currentFavoritePage >
            totalPages
        ) {
            currentFavoritePage =
                totalPages;
        }

        const start =
            (currentFavoritePage - 1) *
            FAVORITE_PAGE_SIZE;

        filteredFavoriteItems
            .slice(
                start,
                start +
                FAVORITE_PAGE_SIZE,
            )
            .forEach(
                (favorite) => {
                    favoriteAllList
                        .appendChild(
                            createFavoriteCard(
                                favorite,
                            ),
                        );
                },
            );

        renderPagination(
            favoritePagination,
            currentFavoritePage,
            totalPages,
            (page) => {
                currentFavoritePage =
                    page;

                renderFavoriteAll();

                favoritesView
                    ?.scrollIntoView({
                        behavior:
                            "smooth",
                        block:
                            "start",
                    });
            },
        );
    }

    function applyFavoriteFilters() {
        const keyword =
            favoriteSearchKeyword
                .trim()
                .toLowerCase();

        filteredFavoriteItems =
            favoriteItems.filter(
                (favorite) => {
                    const name =
                        String(
                            favorite.placeName ||
                            "",
                        ).toLowerCase();

                    const region =
                        String(
                            favorite.region ||
                            "",
                        ).toLowerCase();

                    const city =
                        normalizeCityName(
                            favorite.region,
                        );

                    const keywordMatched =
                        !keyword ||
                        name.includes(
                            keyword,
                        ) ||
                        region.includes(
                            keyword,
                        ) ||
                        city
                            .toLowerCase()
                            .includes(
                                keyword,
                            );

                    const regionMatched =
                        selectedFavoriteRegion ===
                        "ALL" ||
                        city ===
                        selectedFavoriteRegion;

                    return (
                        keywordMatched &&
                        regionMatched
                    );
                },
            );

        currentFavoritePage = 1;

        renderFavoriteRegions();
        renderFavoriteAll();

        if (
            favoriteResultSummary
        ) {
            const filtering =
                Boolean(
                    favoriteSearchKeyword
                        .trim(),
                ) ||
                selectedFavoriteRegion !==
                "ALL";

            favoriteResultSummary.hidden =
                !filtering;

            favoriteResultSummary.textContent =
                filtering
                    ? `검색 결과 ${filteredFavoriteItems.length}곳`
                    : "";
        }
    }

    function resetFavoriteFilters() {
        favoriteSearchKeyword =
            "";

        selectedFavoriteRegion =
            "ALL";

        currentFavoritePage = 1;

        if (
            favoriteSearchInput
        ) {
            favoriteSearchInput.value =
                "";
        }

        if (
            favoriteSearchClear
        ) {
            favoriteSearchClear.hidden =
                true;
        }

        if (
            favoriteRegionMore
        ) {
            favoriteRegionMore.value =
                "";
        }

        applyFavoriteFilters();
    }

    favoriteSearchInput
        ?.addEventListener(
            "input",
            () => {
                favoriteSearchKeyword =
                    favoriteSearchInput
                        .value;

                if (
                    favoriteSearchClear
                ) {
                    favoriteSearchClear.hidden =
                        !favoriteSearchKeyword;
                }

                applyFavoriteFilters();
            },
        );

    favoriteSearchClear
        ?.addEventListener(
            "click",
            resetFavoriteFilters,
        );

    favoriteRegionMore
        ?.addEventListener(
            "change",
            () => {
                if (
                    !favoriteRegionMore
                        .value
                ) {
                    return;
                }

                selectedFavoriteRegion =
                    favoriteRegionMore
                        .value;

                currentFavoritePage =
                    1;

                applyFavoriteFilters();
            },
        );

    async function loadFavorites() {
        try {
            /*
             * 둘 다 GET 요청이므로
             * 공통 request() 사용 가능.
             */
            const [
                favorites,
                count,
            ] =
                await Promise.all([
                    request(
                        "/api/v1/favorites?page=0&size=100",
                    ),

                    request(
                        "/api/v1/favorites/count",
                    ),
                ]);

            favoriteItems =
                Array.isArray(favorites)
                    ? favorites
                    : [];

            filteredFavoriteItems =
                [...favoriteItems];

            const total =
                Number(count) ||
                favoriteItems.length;

            if (favoriteCount) {
                favoriteCount.textContent =
                    `${total}곳`;
            }

            if (
                favoriteTotalCount
            ) {
                favoriteTotalCount.textContent =
                    `총 ${total}곳`;
            }

            renderFavoritePreview();
            renderFavoriteRegions();
            renderFavoriteAll();
        } catch (error) {
            console.error(
                "찜 조회 실패:",
                error,
            );

            if (
                favoritePreviewList
            ) {
                const state =
                    document.createElement(
                        "p",
                    );

                state.className =
                    "favorite-place-state error";

                state.textContent =
                    "찜한 여행지를 불러오지 못했습니다.";

                favoritePreviewList
                    .replaceChildren(
                        state,
                    );
            }

            throw error;
        }
    }

    return loadFavorites();
}