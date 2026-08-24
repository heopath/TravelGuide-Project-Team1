const fs = require("node:fs");
const path = require("node:path");

const root = path.resolve(__dirname, "../../main/resources");
const html = fs.readFileSync(path.join(root, "templates/admin/admin.html"), "utf8");
const js = fs.readFileSync(path.join(root, "static/js/pages/admin/admin-support.js"), "utf8");
const css = fs.readFileSync(path.join(root, "static/css/pages/admin/admin-support.css"), "utf8");

let passed = 0;
let failed = 0;
function test(name, assertion) {
  try {
    if (!assertion()) throw new Error("조건을 만족하지 않습니다.");
    passed += 1;
    console.log(`PASS ${name}`);
  } catch (error) {
    failed += 1;
    console.error(`FAIL ${name}: ${error.message}`);
  }
}

test("관리자 페이지에서 전용 문의 CSS를 함께 사용한다", () =>
  html.includes("/css/pages/admin/admin-support.css") && html.includes("/css/pages/admin/admin.css"));
test("관리자 페이지에서 전용 문의 JS를 함께 사용한다", () =>
  html.includes("/js/pages/admin/admin-support.js") && html.includes("/js/pages/admin/admin.js"));
test("별도 페이지 이동 없이 관리자 패널로 연다", () =>
  html.includes('data-admin-panel="support"') && html.includes('data-admin-section="support"')
    && !html.includes('href="/admin/support"'));
test("문의 상태 필터 네 종류가 있다", () =>
  ["OPEN", "IN_PROGRESS", "ANSWERED", "CLOSED"].every((value) => html.includes(`data-status=\"${value}\"`)));
test("문의 목록과 상세 영역이 분리되어 있다", () =>
  html.includes("data-inquiry-list") && html.includes("data-detail"));
test("관리자 문의 목록 API를 호출한다", () =>
  js.includes("/api/v1/admin/support/inquiries?"));
test("답변 등록 API를 호출한다", () =>
  js.includes("/${selectedId}/replies") && js.includes('method: "POST"'));
test("상태 변경 API를 호출한다", () =>
  js.includes("/${selectedId}/status") && js.includes('method: "PATCH"'));
test("문의 목록을 직접 새로고침할 수 있다", () =>
  html.includes("data-support-refresh") && js.includes('refresh?.addEventListener("click"'));
test("답변·상태 변경 결과를 화면에 남긴다", () =>
  html.includes("data-support-feedback") && js.includes("showFeedback"));
test("전용 화면 스타일이 존재한다", () => css.includes(".support-admin-workspace"));
test("상세 로딩 영역은 응답 후 확실히 숨겨진다", () =>
  css.includes(".support-admin-empty[hidden]") && css.includes("display: none !important"));

console.log(`\n${passed} passed, ${failed} failed`);
if (failed) process.exit(1);
