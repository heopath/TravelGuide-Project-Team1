/*
 * All My Trips 화면 점검 — Figma 플러그인 (#191)
 *
 * 코드에 있는 화면 목록과 Figma 파일의 프레임을 대조한다.
 *
 * 이 플러그인은 <b>기존 프레임을 고치거나 지우지 않는다.</b> 사람이 그린 시안을 자동으로
 * 건드리면 되돌리기가 어렵고, 이름만 보고 같은 화면인지 판단하는 것도 확실하지 않다.
 * 빠진 화면은 별도 페이지에 만들어 두므로, 마음에 안 들면 그 페이지만 지우면 된다.
 */

// <screens>
/* 손으로 고치지 마세요. build-screens.js가 app.js에서 만들어 넣습니다. */
const SCREENS = [
  { path: "/home", name: "메인 화면", group: "home" },
  { path: "/auth/login", name: "로그인", group: "auth" },
  { path: "/auth/signup", name: "회원가입", group: "auth" },
  { path: "/trips/new/plan", name: "여행 계획 방식", group: "trips" },
  { path: "/trips/new/basic", name: "여행 기본 정보", group: "trips" },
  { path: "/trips/new/style", name: "여행 스타일", group: "trips" },
  { path: "/guide", name: "추천 장소", group: "guide" },
  { path: "/guide/places/haeundae", name: "장소 상세", group: "guide" },
  { path: "/trips/schedule", name: "여행 일정 편집", group: "trips" },
  { path: "/trips/busan/map", name: "지도 기반 경로", group: "trips" },
  { path: "/ai-trip-plan", name: "AI 여행계획", group: "trips" },
  { path: "/ai-guide", name: "AI 여행 가이드", group: "guide" },
  { path: "/trips/busan/optimize", name: "AI 일정 최적화", group: "trips" },
  { path: "/guide/themes", name: "테마 여행", group: "guide" },
  { path: "/booking/flights?tab=flight", name: "예약 · 항공편 검색", group: "booking" },
  { path: "/booking/flights?tab=hotel", name: "예약 · 숙소 검색", group: "booking" },
  { path: "/booking/flights?tab=ticket", name: "예약 · 티켓·액티비티", group: "booking" },
  { path: "/booking/flights?tab=mine", name: "예약 · 내 예약", group: "booking" },
  { path: "/booking/tickets/1", name: "티켓 상세·예약", group: "booking" },
  { path: "/booking/queue", name: "예약 대기열", group: "booking" },
  { path: "/pay/qr", name: "QR 결제 승인", group: "booking" },
  { path: "/pay/toss", name: "토스 결제 결과", group: "booking" },
  { path: "/pay/kakao", name: "카카오페이 결제 결과", group: "booking" },
  { path: "/mypage", name: "마이 페이지", group: "mypage" },
  { path: "/mypage?view=trips", name: "마이페이지 · 내 여행", group: "mypage" },
  { path: "/mypage?view=tickets", name: "마이페이지 · 예매한 티켓", group: "mypage" },
  { path: "/mypage?view=favorites", name: "마이페이지 · 찜한 여행지", group: "mypage" },
  { path: "/mypage?view=reviews", name: "마이페이지 · 리뷰 & 후기", group: "mypage" },
  { path: "/mypage?view=support", name: "마이페이지 · 고객센터 문의", group: "mypage" },
  { path: "/mypage?view=settings", name: "마이페이지 · 계정 설정", group: "mypage" },
  { path: "/trips/1/record", name: "여행 기록", group: "trips" },
  { path: "/admin?panel=reports", name: "관리자 · 신고 관리", group: "admin" },
  { path: "/admin/places", name: "관리자 · 추천 장소 관리", group: "admin" },
  { path: "/admin/scan", name: "관리자 · 현장 검표 (폰)", group: "admin" },
  { path: "/admin?panel=metrics", name: "관리자 · 운영 지표", group: "admin" },
  { path: "/admin?panel=products", name: "관리자 · 예약 상품·재고", group: "admin" },
  { path: "/admin?panel=reservations", name: "관리자 · 예약 모니터링", group: "admin" },
  { path: "/admin?panel=performance", name: "관리자 · 성능 모니터링", group: "admin" },
  { path: "/admin?panel=chat", name: "관리자 · 상담 채팅", group: "admin" },
  { path: "/admin?panel=support", name: "관리자 · 1:1 문의 관리", group: "admin" },
  { path: "/admin?panel=audit", name: "관리자 · 조작 이력", group: "admin" },
];
// </screens>

/** 폰으로 쓰는 화면. 데스크톱 크기로 만들면 시안에서 크기가 거짓말을 한다. */
const MOBILE_PATHS = ["/admin/scan"];

const DESKTOP = { width: 1440, height: 900 };
const MOBILE = { width: 390, height: 844 };

/* 자동으로 만든 것만 모아 두는 페이지. 사람이 그린 페이지와 섞이지 않게 한다. */
const GENERATED_PAGE = "코드 기준 — 자동 생성";

const GROUP_LABEL = {
    home: "홈", auth: "인증", trips: "여행", guide: "추천 장소",
    booking: "예약", mypage: "마이페이지", admin: "관리자",
};

figma.showUI(__html__, { width: 460, height: 620 });

/**
 * 이름을 견주기 좋게 다듬는다.
 *
 * <p>시안 이름은 `예약 · 내 예약`, `[예약] 내 예약`, `내예약 v2`처럼 제각각이다. 띄어쓰기와
 * 구분 기호를 걷어내고 소문자로 맞춰야 사람이 같다고 보는 것들이 같아진다.
 */
function normalize(text) {
    return String(text || "")
        .toLowerCase()
        .replace(/[\s·・\-_/[\]()|,.]/g, "");
}

/** 화면 하나가 시안 어디에 있는지 찾는다. 못 찾으면 null이다. */
function findFrame(screen, frames) {
    const wantedPath = normalize(screen.path);
    const wantedName = normalize(screen.name);
    /* `예약 · 내 예약`처럼 그룹이 앞에 붙은 이름은 뒤쪽만 견준다. */
    const shortName = normalize(screen.name.replace(/^[^·]+·\s*/, ""));

    /*
     * 주소로 먼저 맞춘다. 이름은 사람이 바꾸기 쉽지만 주소는 화면을 특정하는 값이라
     * 적혀 있다면 그쪽이 확실하다.
     */
    const byPath = frames.find((f) => normalize(f.node.name).indexOf(wantedPath) >= 0);
    if (byPath) return { frame: byPath, how: "주소" };

    const byName = frames.find((f) => {
        const got = normalize(f.node.name);
        return got.indexOf(wantedName) >= 0 || (shortName.length >= 3 && got.indexOf(shortName) >= 0);
    });
    if (byName) return { frame: byName, how: "이름" };

    return null;
}

/** 파일 전체에서 프레임을 모은다. 화면이 여러 페이지에 흩어져 있을 수 있다. */
function collectFrames() {
    const frames = [];
    for (const page of figma.root.children) {
        if (page.name === GENERATED_PAGE) continue;
        for (const node of page.children) {
            if (node.type === "FRAME" || node.type === "COMPONENT" || node.type === "SECTION") {
                frames.push({ node: node, page: page.name });
            }
        }
    }
    return frames;
}

function inspect() {
    const frames = collectFrames();
    const used = new Set();
    const found = [];
    const missing = [];

    for (const screen of SCREENS) {
        const hit = findFrame(screen, frames.filter((f) => !used.has(f.node.id)));
        if (hit) {
            used.add(hit.frame.node.id);
            found.push({
                path: screen.path, name: screen.name,
                frame: hit.frame.node.name, page: hit.frame.page, how: hit.how,
            });
        } else {
            missing.push({ path: screen.path, name: screen.name, group: screen.group });
        }
    }

    /*
     * 어느 화면과도 맞지 않은 프레임이다. 지워야 할 것일 수도 있고, 이름만 다르거나
     * 부품·표지처럼 화면이 아닌 것일 수도 있다. 판단은 사람이 한다.
     */
    const extra = frames
        .filter((f) => !used.has(f.node.id))
        .map((f) => ({ frame: f.node.name, page: f.page }));

    return { total: SCREENS.length, found: found, missing: missing, extra: extra };
}

/** 한글이 나오는 글꼴을 고른다. Inter만 쓰면 한글이 네모로 나온다. */
async function pickFont() {
    const wanted = [
        { family: "Pretendard", style: "Regular" },
        { family: "Noto Sans KR", style: "Regular" },
        { family: "Apple SD Gothic Neo", style: "Regular" },
        { family: "Malgun Gothic", style: "Regular" },
        { family: "Inter", style: "Regular" },
    ];
    const available = await figma.listAvailableFontsAsync();
    const has = new Set(available.map((f) => f.fontName.family + "|" + f.fontName.style));

    for (const font of wanted) {
        if (has.has(font.family + "|" + font.style)) {
            await figma.loadFontAsync(font);
            return font;
        }
    }
    const fallback = { family: "Inter", style: "Regular" };
    await figma.loadFontAsync(fallback);
    return fallback;
}

function generatedPage() {
    const existing = figma.root.children.find((p) => p.name === GENERATED_PAGE);
    if (existing) return existing;

    const page = figma.createPage();
    page.name = GENERATED_PAGE;
    return page;
}

async function createMissing(missing) {
    if (missing.length === 0) return { created: 0 };

    const font = await pickFont();
    const page = generatedPage();

    /* 이미 만들어 둔 것 위에 겹치지 않게, 있던 것들 아래에서 시작한다. */
    let baseY = 0;
    for (const node of page.children) {
        if ("y" in node && "height" in node) baseY = Math.max(baseY, node.y + node.height);
    }
    if (baseY > 0) baseY += 200;

    const GAP = 120;
    let x = 0;
    let rowHeight = 0;
    let y = baseY;
    let perRow = 0;
    let created = 0;

    for (const screen of missing) {
        const size = MOBILE_PATHS.indexOf(screen.path) >= 0 ? MOBILE : DESKTOP;

        const frame = figma.createFrame();
        frame.name = `${screen.name} — ${screen.path}`;
        frame.resize(size.width, size.height);
        frame.x = x;
        frame.y = y;
        frame.fills = [{ type: "SOLID", color: { r: 1, g: 1, b: 1 } }];
        frame.strokes = [{ type: "SOLID", color: { r: 0.85, g: 0.87, b: 0.95 } }];
        frame.strokeAlign = "OUTSIDE";
        frame.dashPattern = [8, 6];

        const title = figma.createText();
        title.fontName = font;
        title.characters = screen.name;
        title.fontSize = 28;
        title.x = 48;
        title.y = 48;
        frame.appendChild(title);

        const meta = figma.createText();
        meta.fontName = font;
        meta.characters = `${screen.path}\n${GROUP_LABEL[screen.group] || screen.group}`;
        meta.fontSize = 15;
        meta.lineHeight = { unit: "PIXELS", value: 24 };
        meta.fills = [{ type: "SOLID", color: { r: 0.45, g: 0.48, b: 0.56 } }];
        meta.x = 48;
        meta.y = 92;
        frame.appendChild(meta);

        const note = figma.createText();
        note.fontName = font;
        note.characters = "코드에는 있으나 시안에 없던 화면입니다.\n실제 화면을 캡처해 이 프레임을 채워 주세요.";
        note.fontSize = 14;
        note.lineHeight = { unit: "PIXELS", value: 22 };
        note.fills = [{ type: "SOLID", color: { r: 0.6, g: 0.62, b: 0.7 } }];
        note.x = 48;
        note.y = 152;
        frame.appendChild(note);

        page.appendChild(frame);
        created += 1;

        rowHeight = Math.max(rowHeight, size.height);
        x += size.width + GAP;
        perRow += 1;
        /* 한 줄에 넷까지. 더 늘리면 캔버스를 옆으로 한참 끌어야 한다. */
        if (perRow >= 4) {
            x = 0;
            y += rowHeight + GAP;
            rowHeight = 0;
            perRow = 0;
        }
    }

    return { created: created, page: page.name };
}

figma.ui.onmessage = async (message) => {
    try {
        if (message.type === "inspect") {
            figma.ui.postMessage({ type: "report", report: inspect() });
            return;
        }
        if (message.type === "create-missing") {
            const report = inspect();
            const result = await createMissing(report.missing);
            figma.ui.postMessage({ type: "created", result: result, report: inspect() });
            figma.notify(result.created > 0
                ? `${result.created}개 화면을 "${GENERATED_PAGE}" 페이지에 만들었습니다.`
                : "빠진 화면이 없습니다.");
            return;
        }
        if (message.type === "close") {
            figma.closePlugin();
        }
    } catch (error) {
        figma.ui.postMessage({ type: "error", message: String(error && error.message ? error.message : error) });
    }
};
