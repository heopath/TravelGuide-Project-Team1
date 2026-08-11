import {
    request,
    renderPagination,
} from "./mypage-common.js";

const REVIEW_PAGE_SIZE = 10;

const categoryLabels = {
    ATTRACTION: "관광지",
    RESTAURANT: "맛집",
    CAFE: "카페",
    ACCOMMODATION: "숙소",
    FESTIVAL: "축제",
    ACTIVITY: "체험",
    TRANSPORT: "교통",
};

function formatDate(value) {
    if (!value) return "";
    return new Intl.DateTimeFormat("ko-KR", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
    }).format(new Date(value));
}

function createReviewCard(review) {
    const article = document.createElement("article");
    article.className = "mypage-review-card";

    const image = document.createElement("div");
    image.className = "mypage-review-image";
    if (review.placeImageUrl) {
        const img = document.createElement("img");
        img.src = review.placeImageUrl;
        img.alt = `${review.placeName || "장소"} 대표 이미지`;
        img.loading = "lazy";
        image.appendChild(img);
    } else {
        const fallback = document.createElement("span");
        fallback.textContent = categoryLabels[review.placeCategory] || "장소";
        image.appendChild(fallback);
    }

    const body = document.createElement("div");
    body.className = "mypage-review-body";

    const head = document.createElement("div");
    head.className = "mypage-review-head";
    const titleGroup = document.createElement("div");
    const category = document.createElement("span");
    category.className = "mypage-review-category";
    category.textContent = categoryLabels[review.placeCategory] || "장소";
    const title = document.createElement("h3");
    title.textContent = review.placeName || "장소 정보 없음";
    titleGroup.append(category, title);

    const date = document.createElement("time");
    date.dateTime = review.updatedAt || review.createdAt || "";
    date.textContent = formatDate(review.updatedAt || review.createdAt);
    head.append(titleGroup, date);

    const meta = document.createElement("div");
    meta.className = "mypage-review-meta";
    const stars = document.createElement("span");
    stars.className = "mypage-review-stars";
    stars.setAttribute("aria-label", `별점 ${review.rating}점`);
    stars.textContent = "★".repeat(Number(review.rating) || 0)
        + "☆".repeat(5 - (Number(review.rating) || 0));
    meta.appendChild(stars);
    if (review.verifiedVisit) {
        const verified = document.createElement("span");
        verified.className = "mypage-review-verified";
        verified.textContent = "방문 인증";
        meta.appendChild(verified);
    }

    const content = document.createElement("p");
    content.className = "mypage-review-content";
    content.textContent = review.content;

    const foot = document.createElement("div");
    foot.className = "mypage-review-foot";
    if (review.placeAddress) {
        const address = document.createElement("span");
        address.textContent = review.placeAddress;
        foot.appendChild(address);
    }
    const link = document.createElement("a");
    link.href = `/guide/places/${review.placeId}`;
    link.textContent = "장소 상세에서 보기";
    foot.appendChild(link);

    body.append(head, meta, content, foot);
    article.append(image, body);
    return article;
}

export function initReviews() {
    const list = document.querySelector("[data-review-list]");
    const totalCount = document.querySelector("[data-review-total-count]");
    const pagination = document.querySelector("[data-review-pagination]");
    if (!list) return Promise.resolve();

    async function load(page = 1) {
        list.replaceChildren();
        const loading = document.createElement("p");
        loading.className = "mypage-state";
        loading.textContent = "작성한 후기를 불러오는 중입니다.";
        list.appendChild(loading);

        try {
            const data = await request(
                `/api/v1/members/me/place-reviews?page=${page - 1}&size=${REVIEW_PAGE_SIZE}`,
                { allMyTripsLoading: false },
            );
            totalCount.textContent = `${Number(data.totalElements || 0).toLocaleString("ko-KR")}개`;
            list.replaceChildren();

            if (!data.reviews?.length) {
                const empty = document.createElement("div");
                empty.className = "mypage-review-empty";
                const title = document.createElement("strong");
                title.textContent = "아직 작성한 후기가 없습니다.";
                const copy = document.createElement("p");
                copy.textContent = "추천 장소 상세에서 방문 경험과 별점을 남겨보세요.";
                const link = document.createElement("a");
                link.href = "/guide";
                link.textContent = "추천 장소 둘러보기";
                empty.append(title, copy, link);
                list.appendChild(empty);
            } else {
                data.reviews.forEach((review) => list.appendChild(createReviewCard(review)));
            }

            renderPagination(
                pagination,
                Number(data.page || 0) + 1,
                Number(data.totalPages || 0),
                (nextPage) => {
                    load(nextPage);
                    document.querySelector("[data-reviews-view]")?.scrollIntoView({
                        behavior: "smooth",
                        block: "start",
                    });
                },
            );
        } catch (error) {
            totalCount.textContent = "—";
            pagination.hidden = true;
            list.replaceChildren();
            const state = document.createElement("p");
            state.className = "mypage-state error";
            state.textContent = error.message || "작성한 후기를 불러오지 못했습니다.";
            list.appendChild(state);
        }
    }

    return load();
}
