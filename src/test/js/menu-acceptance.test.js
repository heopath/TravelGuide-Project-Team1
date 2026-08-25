/* ☰ 메뉴 수용 기준
 *
 * 좁은 화면(<=760px)에서는 헤더의 nav·아바타·로그인 버튼이 전부 숨겨진다
 * (components.css의 `.app-header nav,.avatar,.login-pill{display:none}`).
 * 그래서 이 메뉴가 유일한 길이 된다. 지켜야 할 것이 넷이다.
 *
 *   1. 비로그인은 로그인·회원가입에 닿아야 한다. 모바일에서는 헤더의 로그인 버튼이
 *      없어서 여기가 막히면 로그인할 방법이 사라진다.
 *   2. 로그인하면 마이 페이지에 닿아야 한다. 아바타도 모바일에서는 숨는다.
 *   3. 관리자 항목은 관리자에게만 보인다.
 *   4. 열어서 오류가 나는 주소를 넣지 않는다. 예전 화면 목록에는 /trips/1/record처럼
 *      고정 번호가 있어, 그 여행이 없는 손님은 안내 문구를 봤다.
 *
 * 화면 목록(?screens=1)은 검수·시연용으로 남겨 두었고 여기서 함께 검사한다.
 *
 * 실행: src/test/js 에서 `npm test`
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const APP_JS = path.join(ROOT, "src/main/resources/static/js/app.js");

let passed = 0;
let failed = 0;
const T = (name, condition, detail) => {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name + (detail ? " — " + detail : "")); }
};

/**
 * 메뉴를 연 문서를 만든다.
 *
 * @param auth  {authenticated, role, route, search}
 */
function openMenu(auth) {
  const search = auth.search || "";
  const dom = new JSDOM(
    `<!doctype html><html data-authenticated="${auth.authenticated}" data-user-role="${auth.role || ""}">
     <body data-route="${auth.route || "/home"}">
       <div id="modal-root"></div><div id="directory-root"></div><div id="toast-root"></div>
     </body></html>`,
    { url: "http://localhost:8080" + (auth.route || "/home") + search, runScripts: "outside-only" }
  );
  /* app.js는 맨 위에서 window.fetch를 감싼다. 메뉴와는 무관하지만 없으면 로드가 멈춘다. */
  dom.window.fetch = async function () { throw new Error("메뉴는 서버를 부르지 않는다"); };
  dom.window.Headers = global.Headers;
  dom.window.eval(fs.readFileSync(APP_JS, "utf8"));
  dom.window.AllMyTripsModal.openDirectory();
  const root = dom.window.document.querySelector("#directory-root");
  return {
    window: dom.window,
    root: root,
    title: root.querySelector(".drawer-head h2").textContent.trim(),
    groups: [...root.querySelectorAll("section h3")].map((h) => h.textContent.trim()),
    labels: [...root.querySelectorAll("section button b")].map((b) => b.textContent.trim()),
    routes: [...root.querySelectorAll("section button[data-route]")].map((b) => b.dataset.route),
    activeLabels: [...root.querySelectorAll("section button.active b")].map((b) => b.textContent.trim()),
  };
}

/* =========================================================
   1. 비로그인은 로그인·회원가입에 닿는다
   ========================================================= */

const guest = openMenu({ authenticated: false });

T("제목은 '메뉴'다", guest.title === "메뉴", guest.title);
T("그룹 제목이 한국어다", guest.groups.every((g) => /[가-힣]/.test(g)), guest.groups.join(", "));
T("로그인으로 갈 수 있다", guest.routes.includes("/auth/login"), guest.routes.join(", "));
T("회원가입으로 갈 수 있다", guest.routes.includes("/auth/signup"));
T("마이 페이지는 보이지 않는다", !guest.routes.includes("/mypage"));
T("관리자도 보이지 않는다", !guest.routes.includes("/admin"));
T("관리 그룹 제목이 빈 채로 남지 않는다", !guest.groups.includes("관리"), guest.groups.join(", "));

/* =========================================================
   2. 로그인하면 마이 페이지에 닿는다
   ========================================================= */

const member = openMenu({ authenticated: true, role: "USER" });

T("마이 페이지로 갈 수 있다", member.routes.includes("/mypage"), member.routes.join(", "));
T("로그인·회원가입은 사라진다",
  !member.routes.includes("/auth/login") && !member.routes.includes("/auth/signup"));
T("일반 회원에게 관리자는 보이지 않는다", !member.routes.includes("/admin"));

/* =========================================================
   3. 관리자 항목은 관리자에게만
   ========================================================= */

const admin = openMenu({ authenticated: true, role: "ADMIN" });
T("관리자로 갈 수 있다", admin.routes.includes("/admin"), admin.routes.join(", "));
T("관리 그룹이 생긴다", admin.groups.includes("관리"), admin.groups.join(", "));

/* =========================================================
   4. 항상 있는 길과, 넣지 않는 길

   예전 화면 목록에는 고정 번호(/trips/1/record, /booking/tickets/1)와 쿼리 변형
   (?panel=, ?view=, ?tab=)이 섞여 있었다. 손님 메뉴에는 넣지 않는다.
   ========================================================= */

["/home", "/trips/new/plan", "/guide", "/booking"].forEach((route) => {
  T("비로그인도 " + route + "에 닿는다", guest.routes.includes(route));
  T("로그인해도 " + route + "에 닿는다", member.routes.includes(route));
});

T("고정 번호가 박힌 주소가 없다",
  admin.routes.every((r) => !/\/\d+(\/|$)/.test(r)), admin.routes.join(", "));
T("쿼리 변형이 없다",
  admin.routes.every((r) => !r.includes("?")), admin.routes.join(", "));
T("한 화면에 들어갈 만큼만 있다", admin.routes.length <= 8, "항목 " + admin.routes.length + "개");

/* =========================================================
   5. 현재 위치 표시

   경로를 그대로 비교하면 /trips/new/basic에서 "여행 계획"이 꺼진 것처럼 보인다.
   ========================================================= */

const onBasic = openMenu({ authenticated: true, role: "USER", route: "/trips/new/basic" });
T("하위 경로에서도 '여행 계획'이 켜진다",
  onBasic.activeLabels.includes("여행 계획"), onBasic.activeLabels.join(", "));

const onHome = openMenu({ authenticated: false, route: "/home" });
T("메인에서는 '메인'만 켜진다",
  onHome.activeLabels.length === 1 && onHome.activeLabels[0] === "메인",
  onHome.activeLabels.join(", "));

/* =========================================================
   6. 화면 목록은 ?screens=1로 남겨 둔다

   검수와 시연에서 전 화면을 훑는 수단이라 없애지 않는다.
   ========================================================= */

const screens = openMenu({ authenticated: true, role: "ADMIN", search: "?screens=1" });
T("?screens=1이면 화면 목록이 열린다", screens.title.startsWith("전체 화면"), screens.title);
T("화면 목록에는 예전 항목이 그대로 있다",
  screens.routes.includes("/trips/1/record") && screens.routes.some((r) => r.includes("?panel=")),
  "항목 " + screens.routes.length + "개");
T("평소 메뉴보다 훨씬 많다", screens.routes.length > admin.routes.length * 3,
  screens.routes.length + " vs " + admin.routes.length);

console.log("");
console.log(passed + " passed, " + failed + " failed");
if (failed > 0) process.exit(1);
