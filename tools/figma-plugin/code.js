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
  { path: "/admin?panel=version", name: "관리자 · 서비스 버전", group: "admin" },
  { path: "/admin?panel=members", name: "관리자 · 회원 관리", group: "admin" },
  { path: "/admin?panel=validation", name: "관리자 · 티켓 검표", group: "admin" },
];
// </screens>

// <modals>
/* 손으로 고치지 마세요. build-screens.js가 만들어 넣습니다. */
const MODALS = [
  {"no":"M01","name":"목적지 검색","group":"안내","open":"AllMyTripsModal.openModal('destination')","note":""},
  {"no":"M02","name":"날짜 선택","group":"안내","open":"AllMyTripsModal.openModal('date')","note":""},
  {"no":"M03","name":"인원 선택","group":"안내","open":"AllMyTripsModal.openModal('guests')","note":""},
  {"no":"M04","name":"약관 상세","group":"안내","open":"AllMyTripsModal.openModal('terms')","note":""},
  {"no":"M05","name":"새 여행 만들기","group":"안내","open":"AllMyTripsModal.openModal('new-trip')","note":""},
  {"no":"M06","name":"일정에 장소 추가","group":"안내","open":"AllMyTripsModal.openModal('add-place')","note":""},
  {"no":"M07","name":"일정 충돌 경고","group":"경고","open":"AllMyTripsModal.openModal('conflict')","note":""},
  {"no":"M08","name":"여행 공유","group":"안내","open":"AllMyTripsModal.openModal('share')","note":""},
  {"no":"M09","name":"AI 추천 적용","group":"안내","open":"AllMyTripsModal.openModal('apply-ai')","note":""},
  {"no":"M10","name":"예약 옵션 및 결제","group":"결제","open":"AllMyTripsModal.openModal('payment')","note":""},
  {"no":"M11","name":"예약 취소 및 환불","group":"경고","open":"AllMyTripsModal.openModal('refund')","note":""},
  {"no":"M12","name":"대기열 만료","group":"경고","open":"AllMyTripsModal.openModal('queue')","note":""},
  {"no":"M13","name":"여행 사진 업로드","group":"안내","open":"AllMyTripsModal.openModal('upload')","note":""},
  {"no":"M14","name":"로그인 필요","group":"안내","open":"AllMyTripsModal.openModal('login-required')","note":""},
  {"no":"M15","name":"회원 탈퇴","group":"경고","open":"AllMyTripsModal.openModal('delete-account')","note":""},
  {"no":"M16","name":"상품 및 재고 수정","group":"안내","open":"AllMyTripsModal.openModal('admin-product')","note":""},
  {"no":"M17","name":"결제수단 선택","group":"결제","open":"AllMyTripsPayment.choose({ summary: '제주 아쿠아리움 입장권 · 40,000원', confirmLabel: '다음', allowQr: true })","note":""},
  {"no":"M18","name":"카드 결제","group":"결제","open":"AllMyTripsCheckout.cardCheckout({ summary: '제주 아쿠아리움 입장권 · 성인 2매', amountText: '40,000원' })","note":""},
  {"no":"M19","name":"계좌이체","group":"결제","open":"AllMyTripsCheckout.transferCheckout({ summary: '제주 아쿠아리움 입장권 · 성인 2매', amountText: '40,000원', method: 'TRANSFER' })","note":""},
  {"no":"M20","name":"가상계좌","group":"결제","open":"AllMyTripsCheckout.transferCheckout({ summary: '제주 아쿠아리움 입장권 · 성인 2매', amountText: '40,000원', method: 'VIRTUAL_ACCOUNT' })","note":""},
  {"no":"M21","name":"QR 간편결제","group":"결제","open":"AllMyTripsCheckout.easyPayCheckout({ summary: '제주 아쿠아리움 입장권 · 성인 2매', amountText: '40,000원', provider: 'QR_PAY', drawQr: function (t) { return AllMyTripsQr.createQrSvg(t, { label: '결제 승인 QR' }); }, issueQr: async function () { return { approveUrl: location.origin + '/pay/qr?token=example', expiresAt: new Date(Date.now() + 300000).toISOString(), serverTime: new Date().toISOString() }; }, pollPaid: async function () { return false; } })","note":"QR은 서버에서 받은 토큰으로 그린다. 찍을 때는 예시 토큰을 넣는다 — 실제 결제 토큰을 스토리보드에 남기지 않는다."},
  {"no":"M22","name":"카카오페이 이동","group":"결제","open":"AllMyTripsCheckout.kakaoCheckout({ summary: '제주 아쿠아리움 입장권 · 성인 2매', amountText: '40,000원', ready: async function () { return { redirectUrl: 'https://online-pay.kakao.com/example' }; } })","note":""},
  {"no":"M23","name":"토스 결제위젯","group":"결제","open":"AllMyTripsCheckout.tossCheckout({ summary: '제주 아쿠아리움 입장권 · 성인 2매', amountText: '40,000원', amount: 40000, reservationId: 1, orderName: '제주 아쿠아리움 입장권' })","note":"토스 SDK를 받아 그리므로 뜨는 데 시간이 더 걸린다."},
  {"no":"M24","name":"확인 대화상자","group":"경고","open":"AllMyTripsDialog.confirm({ title: '결제한 예약을 취소할까요?', message: '발급된 티켓이 무효가 됩니다.\\n취소하면 되돌릴 수 없습니다.', confirmLabel: '예약 취소', tone: 'danger' })","note":"브라우저 기본 confirm을 대신한다. 손님이 `추가 대화상자 생성 안 함`에 체크하면 기본 confirm은 묻지도 않고 false를 돌려줘, 버튼이 안 눌리는 것처럼 보였다. (#276)"},
  {"no":"M25","name":"전체 화면 목록","group":"안내","open":"AllMyTripsModal.openDirectory()","note":""},
  {"no":"M26","name":"불러오는 중","group":"안내","open":"AllMyTripsLoading.show()","note":""},
];
// </modals>

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

/** 제목에 쓸 굵은 글꼴. 없으면 보통 굵기로 돌아간다. */
async function pickBoldFont(regular) {
    const available = await figma.listAvailableFontsAsync();
    const has = new Set(available.map((f) => f.fontName.family + "|" + f.fontName.style));

    for (const style of ["Bold", "SemiBold", "Medium"]) {
        if (has.has(regular.family + "|" + style)) {
            const font = { family: regular.family, style: style };
            await figma.loadFontAsync(font);
            return font;
        }
    }
    return regular;
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

/* ─────────────────────────────────────────────────────────────
 * 스토리보드 만들기 (#191)
 *
 * 12번 페이지의 카드 형식을 따른다 — 머리띠에 `SB-01 · 그룹 · 이름`과 주소,
 * 본문에 화면, 우상단에 다음 카드로 가는 링크.
 *
 * 본문은 손으로 그리지 않고 실제 화면 캡처를 넣는다. 그려 넣으면 실제와 다른
 * 디자인을 지어내게 되고, 화면이 바뀌면 그림이 조용히 낡는다.
 * ───────────────────────────────────────────────────────────── */

const STORYBOARD_PAGE = "13 · 스토리보드 v3 · 실제 구현";

/* 손님이 서비스를 만나는 순서. 캡처 목록(make-shotlist.js)과 같아야 번호가 맞는다. */
const STORY_ORDER = ["home", "auth", "trips", "guide", "booking", "mypage", "admin"];

const CARD = { width: 1488, band: 48, body: 1043 };
const CARD_GAP = { x: 96, y: 120 };
const PER_ROW = 4;
const SECTION_PAD = 80;

const INK = { r: 0.11, g: 0.12, b: 0.15 };
const MUTED = { r: 0.55, g: 0.58, b: 0.66 };
const BAND = { r: 0.96, g: 0.97, b: 0.99 };
const LINE = { r: 0.87, g: 0.89, b: 0.94 };
const ACCENT = { r: 0.357, g: 0.42, b: 1 };

/** 스토리보드 순서로 세운 화면 목록. 번호(SB-01…)는 이 순서에서 나온다. */
function storyScreens() {
    const sorted = SCREENS.slice().sort(
        (a, b) => STORY_ORDER.indexOf(a.group) - STORY_ORDER.indexOf(b.group));
    return sorted.map((s, i) => {
        const no = "SB-" + String(i + 1).padStart(2, "0");
        return { no: no, file: no.toLowerCase() + ".png", path: s.path, name: s.name, group: s.group };
    });
}

function text(font, characters, size, color, weight) {
    const node = figma.createText();
    node.fontName = weight || font;
    node.characters = characters;
    node.fontSize = size;
    node.fills = [{ type: "SOLID", color: color }];
    return node;
}

/**
 * 카드 한 장.
 *
 * <p>캡처가 없으면 본문을 비워 두고 그렇다고 적는다. 빈 자리를 아무 색으로 채워 두면
 * 캡처를 넣은 카드와 구분이 안 돼, 무엇이 남았는지 알 수 없다.
 */
function buildCard(screen, font, bold, image) {
    const frame = figma.createFrame();
    frame.name = screen.no + " · " + screen.name + " — " + screen.path;
    frame.resize(CARD.width, CARD.band + CARD.body);
    frame.fills = [{ type: "SOLID", color: { r: 1, g: 1, b: 1 } }];
    frame.clipsContent = true;

    /* 머리띠 */
    const band = figma.createRectangle();
    band.resize(CARD.width, CARD.band);
    band.x = 0;
    band.y = 0;
    band.fills = [{ type: "SOLID", color: BAND }];
    frame.appendChild(band);

    const title = text(font, screen.no + " · " + GROUP_LABEL[screen.group] + " · "
        + screen.name.replace(/^[^·]+·\s*/, ""), 17, INK, bold);
    title.x = 24;
    title.y = 15;
    frame.appendChild(title);

    /* 주소가 있어야 이 카드가 어느 화면인지 코드와 대조된다. */
    const route = text(font, screen.path, 15, MUTED);
    route.x = 470;
    route.y = 16;
    frame.appendChild(route);

    const rule = figma.createRectangle();
    rule.resize(CARD.width, 1);
    rule.x = 0;
    rule.y = CARD.band;
    rule.fills = [{ type: "SOLID", color: LINE }];
    frame.appendChild(rule);

    /* 본문 */
    if (image) {
        const shot = figma.createRectangle();
        const ratio = image.width / image.height;
        /* 폰 화면은 가로가 남는다. 늘리지 않고 가운데 둔다. */
        let w = CARD.width;
        let h = Math.round(CARD.width / ratio);
        if (h > CARD.body) {
            h = CARD.body;
            w = Math.round(CARD.body * ratio);
        }
        shot.resize(w, h);
        shot.x = Math.round((CARD.width - w) / 2);
        shot.y = CARD.band + Math.round((CARD.body - h) / 2);
        shot.fills = [{ type: "IMAGE", imageHash: image.hash, scaleMode: "FILL" }];
        frame.appendChild(shot);
    } else {
        const note = text(font, "캡처 없음 — " + screen.file, 16, MUTED);
        note.x = 24;
        note.y = CARD.band + 24;
        frame.appendChild(note);
    }

    return frame;
}

/** 그룹마다 섹션으로 묶는다. 41장을 격자로만 늘어놓으면 어디가 어느 묶음인지 안 보인다. */
function buildStoryboard(images) {
    const screens = storyScreens();
    const page = figma.root.children.find((p) => p.name === STORYBOARD_PAGE) || figma.createPage();
    page.name = STORYBOARD_PAGE;

    /* 다시 돌려도 겹치지 않게, 있던 것을 치우고 새로 놓는다. */
    for (const node of page.children.slice()) node.remove();

    const made = [];
    let cursorY = 0;

    for (const group of STORY_ORDER) {
        const inGroup = screens.filter((s) => s.group === group);
        if (inGroup.length === 0) continue;

        const rows = Math.ceil(inGroup.length / PER_ROW);
        const cols = Math.min(inGroup.length, PER_ROW);
        const cardH = CARD.band + CARD.body;
        const width = cols * CARD.width + (cols - 1) * CARD_GAP.x + SECTION_PAD * 2;
        const height = rows * cardH + (rows - 1) * CARD_GAP.y + SECTION_PAD * 2;

        let container = page;
        let originX = 0;
        let originY = cursorY;

        try {
            const section = figma.createSection();
            section.name = GROUP_LABEL[group] + " (" + inGroup.length + "장)";
            section.x = 0;
            section.y = cursorY;
            section.resizeWithoutConstraints(width, height);
            page.appendChild(section);
            container = section;
            originX = 0;
            originY = 0;
        } catch (error) {
            /* 섹션을 못 만드는 버전이면 페이지에 그대로 놓는다. 배치만 밋밋해질 뿐이다. */
        }

        inGroup.forEach((screen, i) => {
            const card = buildCard(screen, images.font, images.bold, images.byFile[screen.file]);
            container.appendChild(card);
            card.x = originX + SECTION_PAD + (i % PER_ROW) * (CARD.width + CARD_GAP.x);
            card.y = originY + SECTION_PAD + Math.floor(i / PER_ROW) * (cardH + CARD_GAP.y);
            made.push({ screen: screen, card: card });
        });

        cursorY += height + 240;
    }

    /*
     * 다음 카드로 가는 링크는 카드를 다 만든 뒤에 건다. 만들면서 걸면 아직 없는 카드를
     * 가리키게 된다.
     */
    made.forEach((entry, i) => {
        const next = made[i + 1];
        if (!next) return;
        const label = text(images.font, "다음 · " + next.screen.no + " →", 15, ACCENT, images.bold);
        label.x = CARD.width - 220;
        label.y = 15;
        entry.card.appendChild(label);
        try {
            label.setRangeHyperlink(0, label.characters.length,
                { type: "NODE", value: next.card.id });
        } catch (error) {
            /* 링크를 못 걸어도 카드는 남는다. 번호가 적혀 있어 찾아갈 수는 있다. */
        }
    });

    return { page: page.name, cards: made.length,
        withShot: made.filter((m) => images.byFile[m.screen.file]).length };
}


const MODAL_PAGE = "14 · 모달 & 상태";

/** 모달 묶음 순서. 손님이 자주 보는 것부터 둔다. */
const MODAL_GROUPS = ["안내", "결제", "경고"];

/**
 * 모달 카드 한 장.
 *
 * <p>화면 카드와 같은 머리띠를 쓴다. 다른 모양으로 두면 같은 스토리보드인데 두 벌처럼
 * 보인다. 다만 주소 자리에는 <b>여는 방법</b>을 적는다 — 모달은 주소가 없고, 어떻게
 * 띄우는지가 그 자리에서 알아야 할 값이다.
 */
function buildModalCard(modal, font, bold, image) {
    const frame = figma.createFrame();
    frame.name = modal.no + " · " + modal.name;
    frame.resize(CARD.width, CARD.band + CARD.body);
    frame.fills = [{ type: "SOLID", color: { r: 1, g: 1, b: 1 } }];
    frame.clipsContent = true;

    const band = figma.createRectangle();
    band.resize(CARD.width, CARD.band);
    band.fills = [{ type: "SOLID", color: BAND }];
    frame.appendChild(band);

    const title = text(font, modal.no + " · " + modal.group + " · " + modal.name, 17, INK, bold);
    title.x = 24;
    title.y = 15;
    frame.appendChild(title);

    const how = text(font, modal.open, 12, MUTED);
    how.x = 470;
    how.y = 18;
    how.resize(980, 18);
    frame.appendChild(how);

    const rule = figma.createRectangle();
    rule.resize(CARD.width, 1);
    rule.y = CARD.band;
    rule.fills = [{ type: "SOLID", color: LINE }];
    frame.appendChild(rule);

    if (image) {
        const shot = figma.createRectangle();
        const ratio = image.width / image.height;
        let w = CARD.width;
        let h = Math.round(CARD.width / ratio);
        if (h > CARD.body) { h = CARD.body; w = Math.round(CARD.body * ratio); }
        shot.resize(w, h);
        shot.x = Math.round((CARD.width - w) / 2);
        shot.y = CARD.band + Math.round((CARD.body - h) / 2);
        shot.fills = [{ type: "IMAGE", imageHash: image.hash, scaleMode: "FILL" }];
        frame.appendChild(shot);
    } else {
        const note = text(font, "캡처 없음 — " + modal.no.toLowerCase() + ".png", 16, MUTED);
        note.x = 24;
        note.y = CARD.band + 24;
        frame.appendChild(note);
    }

    /* 왜 이렇게 생겼는지 적어 둔 모달이 있다. 그 사유가 카드에 남아야 다음 사람이 안다. */
    if (modal.note) {
        const why = text(font, modal.note, 13, MUTED);
        why.x = 24;
        why.y = CARD.band + CARD.body - 56;
        why.resize(CARD.width - 48, 40);
        frame.appendChild(why);
    }

    return frame;
}

function buildModalBoard(images) {
    const page = figma.root.children.find((p) => p.name === MODAL_PAGE) || figma.createPage();
    page.name = MODAL_PAGE;
    for (const node of page.children.slice()) node.remove();

    const cardH = CARD.band + CARD.body;
    let cursorY = 0;
    let made = 0;
    let withShot = 0;

    for (const group of MODAL_GROUPS) {
        const inGroup = MODALS.filter((m) => m.group === group);
        if (inGroup.length === 0) continue;

        const rows = Math.ceil(inGroup.length / PER_ROW);
        const cols = Math.min(inGroup.length, PER_ROW);
        const width = cols * CARD.width + (cols - 1) * CARD_GAP.x + SECTION_PAD * 2;
        const height = rows * cardH + (rows - 1) * CARD_GAP.y + SECTION_PAD * 2;

        let container = page;
        let originX = 0;
        let originY = cursorY;
        try {
            const section = figma.createSection();
            section.name = group + " (" + inGroup.length + "장)";
            section.x = 0;
            section.y = cursorY;
            section.resizeWithoutConstraints(width, height);
            page.appendChild(section);
            container = section;
            originX = 0;
            originY = 0;
        } catch (error) {
            /* 섹션을 못 만드는 버전이면 페이지에 그대로 놓는다. */
        }

        inGroup.forEach((modal, i) => {
            const image = images.byFile[modal.no.toLowerCase() + ".png"];
            const card = buildModalCard(modal, images.font, images.bold, image);
            container.appendChild(card);
            card.x = originX + SECTION_PAD + (i % PER_ROW) * (CARD.width + CARD_GAP.x);
            card.y = originY + SECTION_PAD + Math.floor(i / PER_ROW) * (cardH + CARD_GAP.y);
            made += 1;
            if (image) withShot += 1;
        });

        cursorY += height + 240;
    }

    return { page: page.name, cards: made, withShot: withShot };
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
        if (message.type === "storyboard") {
            /*
             * 캡처는 화면(UI) 쪽에서 파일로 읽어 넘어온다. 플러그인 본체는 파일 시스템에
             * 손댈 수 없고, 바깥으로 나가는 것도 막아 두었다.
             */
            const font = await pickFont();
            const bold = await pickBoldFont(font);
            const byFile = {};

            for (const item of message.images || []) {
                try {
                    const image = figma.createImage(new Uint8Array(item.bytes));
                    const size = await image.getSizeAsync();
                    byFile[item.file] = { hash: image.hash, width: size.width, height: size.height };
                } catch (error) {
                    /* 한 장이 잘못돼도 나머지는 만든다. 그 카드만 캡처 없음으로 남는다. */
                }
            }

            const result = buildStoryboard({ font: font, bold: bold, byFile: byFile });
            figma.ui.postMessage({ type: "storyboard-done", result: result });
            figma.notify(`${result.cards}장을 "${result.page}"에 만들었습니다. `
                + `캡처 ${result.withShot}장.`);
            return;
        }
        if (message.type === "modals") {
            const font = await pickFont();
            const bold = await pickBoldFont(font);
            const byFile = {};

            for (const item of message.images || []) {
                try {
                    const image = figma.createImage(new Uint8Array(item.bytes));
                    const size = await image.getSizeAsync();
                    byFile[item.file] = { hash: image.hash, width: size.width, height: size.height };
                } catch (error) {
                    /* 한 장이 잘못돼도 나머지는 만든다. */
                }
            }

            const result = buildModalBoard({ font: font, bold: bold, byFile: byFile });
            figma.ui.postMessage({ type: "storyboard-done", result: result });
            figma.notify(`모달 ${result.cards}장을 "${result.page}"에 만들었습니다. `
                + `캡처 ${result.withShot}장.`);
            return;
        }
        if (message.type === "close") {
            figma.closePlugin();
        }
    } catch (error) {
        figma.ui.postMessage({ type: "error", message: String(error && error.message ? error.message : error) });
    }
};
