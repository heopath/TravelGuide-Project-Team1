/*
 * 프레임 짝짓기 수용 기준.
 *
 * 이 판단이 틀리면 리포트 전체가 거짓말이 된다. 있는 화면을 없다고 하면 쓸데없는 프레임을
 * 만들고, 없는 화면을 있다고 하면 빠진 채로 넘어간다.
 *
 *   node tools/figma-plugin/match.test.js
 */
const fs = require("fs");
const path = require("path");

/*
 * 플러그인 코드는 Figma 안에서만 도는 전역(figma)을 쓴다. 여기서는 대조 함수만 떼어
 * 확인하므로, 그 전역을 흉내 내는 대신 showUI 한 줄만 지나가게 해 준다.
 */
const source = fs.readFileSync(path.join(__dirname, "code.js"), "utf8");
const sandbox = {
    figma: {
        showUI() {},
        get ui() { return { onmessage: null, postMessage() {} }; },
        set ui(v) {},
        root: { children: [] },
    },
    __html__: "",
    module: { exports: {} },
};
sandbox.figma.ui = { onmessage: null, postMessage() {} };

const vm = require("node:vm");
const context = vm.createContext(sandbox);
vm.runInContext(source + "\n;globalThis.__test = { SCREENS, normalize, findFrame };", context);
const { SCREENS, normalize, findFrame } = sandbox.__test;

let passed = 0;
let failed = 0;
function test(name, condition, detail) {
    if (condition) { passed++; console.log("PASS " + name); }
    else { failed++; console.log("FAIL " + name + (detail ? " — " + detail : "")); }
}

const frames = (...names) => names.map((n, i) => ({ node: { name: n, id: "n" + i }, page: "화면" }));
const screen = (p) => SCREENS.find((s) => s.path === p);

/* ── 목록 자체 ── */
test("화면 목록이 비어 있지 않다", SCREENS.length > 0, String(SCREENS.length));
test("모든 항목에 경로·이름·그룹이 있다",
    SCREENS.every((s) => s.path && s.name && s.group),
    JSON.stringify(SCREENS.find((s) => !(s.path && s.name && s.group))));
/* 칸이 밀리면 이름 자리에 경로가 들어간다. 그때 이 검사가 걸린다. */
test("이름 자리에 경로가 들어가 있지 않다", SCREENS.every((s) => !s.name.startsWith("/")));
test("경로는 모두 /로 시작한다", SCREENS.every((s) => s.path.startsWith("/")));
test("경로가 겹치지 않는다", new Set(SCREENS.map((s) => s.path)).size === SCREENS.length);

/* ── 이름 다듬기 ── */
test("띄어쓰기와 구분 기호를 걷어낸다", normalize("예약 · 내 예약") === normalize("[예약]내예약"));
test("대소문자를 가리지 않는다", normalize("QR 결제") === normalize("qr결제"));

/* ── 주소로 맞추기 ── */
{
    const hit = findFrame(screen("/pay/toss"), frames("결제 실패", "토스 복귀 /pay/toss", "홈"));
    test("이름에 주소가 적혀 있으면 그걸로 맞춘다", hit && hit.how === "주소", JSON.stringify(hit));
}

/* ── 이름으로 맞추기 ── */
{
    const hit = findFrame(screen("/pay/kakao"), frames("카카오페이 결제 결과"));
    test("주소가 없으면 이름으로 맞춘다", hit && hit.how === "이름", JSON.stringify(hit));
}
{
    /* 시안은 그룹 접두어를 잘 안 붙인다. `마이페이지 · 예매한 티켓` ↔ `예매한 티켓` */
    const hit = findFrame(screen("/mypage?view=tickets"), frames("예매한 티켓"));
    test("그룹 접두어가 없어도 맞춘다", hit !== null, JSON.stringify(hit));
}
{
    const hit = findFrame(screen("/booking/flights?tab=mine"), frames("내 예약 v2"));
    test("뒤에 버전이 붙어 있어도 맞춘다", hit !== null, JSON.stringify(hit));
}

/* ── 틀리게 맞추지 않기 ── */
{
    const hit = findFrame(screen("/pay/toss"), frames("장소 상세", "여행 기록", "고객센터 문의"));
    test("상관없는 프레임을 짝으로 삼지 않는다", hit === null, JSON.stringify(hit));
}
{
    /*
     * `마이 페이지`(대시보드)와 `마이페이지 · 내 여행`은 다른 화면이다. 짧은 이름이
     * 긴 이름 안에 들어 있다고 같다고 보면 엉뚱하게 붙는다.
     */
    const only = frames("마이페이지 · 내 여행");
    const hit = findFrame(screen("/mypage?view=trips"), only);
    test("내 여행은 내 여행 프레임에 붙는다", hit !== null);
}
{
    /* 두 화면이 같은 프레임을 나눠 가지면 하나는 없는 것으로 세야 한다. */
    const pool = frames("결제 결과");
    const used = new Set();
    const first = findFrame(screen("/pay/toss"), pool.filter((f) => !used.has(f.node.id)));
    if (first) used.add(first.frame.node.id);
    const second = findFrame(screen("/pay/kakao"), pool.filter((f) => !used.has(f.node.id)));
    test("한 프레임을 두 화면이 나눠 갖지 않는다", second === null, JSON.stringify(second));
}

console.log("\n" + passed + " passed, " + failed + " failed");
if (failed > 0) process.exitCode = 1;
