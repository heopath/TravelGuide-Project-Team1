/* 마이페이지 최근 본 여행지
 *
 * 지금까지 이 자리에는 "최근에 조회한 여행지가 없어요" 빈 상태가 마크업에 박혀 있을 뿐,
 * 기록하는 곳도 읽는 곳도 없었습니다. 장소 상세를 열면 서버에 남고 여기서 읽습니다.
 *
 * 곁다리 패널이라 실패해도 다른 화면을 막지 않습니다. 목록을 못 받으면 빈 상태로 둡니다.
 */
import { request, normalizeCityName } from "./mypage-common.js";

const CATEGORY_LABELS = {
    ATTRACTION: "관광지",
    RESTAURANT: "맛집",
    CAFE: "카페",
    ACCOMMODATION: "숙소",
    FESTIVAL: "축제",
    ACTIVITY: "체험",
    TRANSPORT: "교통",
};

/** 카드가 한 줄로 넘치지 않는 선. 서버 상한(30)보다 작아도 된다. */
const PREVIEW_COUNT = 8;

/**
 * 언제 봤는지.
 *
 * <p>"최근"이 핵심이라 몇 분 단위까지는 필요 없다. 오늘 안이면 상대 시간이 읽기 쉽고,
 * 하루가 넘어가면 날짜가 더 낫다.
 */
function whenText(iso) {
    const at = new Date(iso);
    if (Number.isNaN(at.getTime())) return "";

    const minutes = Math.floor((Date.now() - at.getTime()) / 60000);
    if (minutes < 1) return "방금";
    if (minutes < 60) return `${minutes}분 전`;
    if (minutes < 60 * 24) return `${Math.floor(minutes / 60)}시간 전`;
    return `${at.getMonth() + 1}월 ${at.getDate()}일`;
}

function createCard(place) {
    const article = document.createElement("article");
    article.className = "recent-place-card";

    const button = document.createElement("button");
    button.type = "button";
    button.dataset.route = `/guide/places/${place.placeId}`;

    const imageBox = document.createElement("div");
    imageBox.className = "recent-place-image";

    if (place.primaryImageUrl) {
        const image = document.createElement("img");
        image.src = place.primaryImageUrl;
        image.alt = place.placeName || "";
        image.loading = "lazy";
        imageBox.appendChild(image);
    } else {
        /* 사진이 없는 장소가 많다. 빈 네모보다 표시가 있는 편이 덜 어색하다. */
        const fallback = document.createElement("span");
        fallback.textContent = "⌖";
        fallback.setAttribute("aria-hidden", "true");
        imageBox.appendChild(fallback);
    }

    const copy = document.createElement("div");
    copy.className = "recent-place-copy";

    const name = document.createElement("strong");
    name.textContent = place.placeName || "이름 없는 여행지";

    const meta = document.createElement("span");
    meta.textContent = [
        normalizeCityName(place.region),
        CATEGORY_LABELS[place.category] || place.category,
    ].filter(Boolean).join(" · ");

    const when = document.createElement("small");
    when.textContent = whenText(place.viewedAt);

    copy.append(name, meta, when);
    button.append(imageBox, copy);
    article.appendChild(button);
    return article;
}

export async function initRecentPlaces() {
    const list = document.querySelector("[data-recent-list]");
    const panel = document.querySelector("[data-recent-panel]");
    const empty = document.querySelector("[data-recent-empty]");
    if (!list || !panel || !empty) return;

    let places = [];
    try {
        places = (await request(`/api/v1/places/recent?size=${PREVIEW_COUNT}`)) || [];
    } catch (error) {
        /* 못 받으면 빈 상태 그대로 둔다. 여기서 오류를 띄우면 마이페이지 전체가 시끄러워진다. */
        return;
    }

    if (!places.length) return;

    list.replaceChildren(...places.map(createCard));
    empty.hidden = true;
    list.hidden = false;
    // 빈 상태 전용 여백을 쓰는 클래스라, 목록이 생기면 일반 패널로 되돌린다.
    panel.classList.remove("mypage-empty-panel");
}
