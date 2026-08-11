/* 마이페이지 고객센터 수용 기준 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/mypage/mypage.html");
const SUPPORT_JS = path.join(ROOT, "src/main/resources/static/js/pages/mypage/mypage-support.js");

let passed = 0;
let failed = 0;
function test(name, condition) {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name); }
}
function until(predicate, timeoutMs = 3000) {
  return new Promise((resolve, reject) => {
    const started = Date.now();
    const tick = () => {
      if (predicate()) return resolve();
      if (Date.now() - started > timeoutMs) return reject(new Error("timeout"));
      setTimeout(tick, 10);
    };
    tick();
  });
}

async function run() {
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/mypage?view=support",
    runScripts: "outside-only",
  });
  const w = dom.window;
  const d = w.document;
  let submitted = null;
  w.request = async (url, options = {}) => {
    if (options.method === "POST") {
      submitted = JSON.parse(options.body);
      return { supportInquiryId: 11 };
    }
    if (url.includes("/inquiries/me")) {
      return {
        inquiries: [{
          supportInquiryId: 11,
          category: "AI_PLAN",
          title: "AI 일정 문의",
          status: "OPEN",
          createdAt: "2026-08-11T10:00:00+09:00",
        }],
        page: 0,
        totalElements: 1,
        totalPages: 1,
      };
    }
    return {};
  };
  w.renderPagination = (container) => { container.hidden = true; };
  w.showToast = () => {};

  const source = fs.readFileSync(SUPPORT_JS, "utf8")
    .replace(/import\s*\{[\s\S]*?\}\s*from\s*"\.\/mypage-common\.js";/, "")
    .replace("export function initSupport()", "window.initSupport = function initSupport()");
  w.eval(source);
  await w.initSupport();

  test("고객센터 메뉴가 활성 버튼이다", !d.querySelector("[data-open-support]").disabled);
  test("FAQ 질문을 기본 표시한다", d.querySelectorAll(".support-faq-item").length >= 5);
  d.querySelector(".support-faq-item button").click();
  test("FAQ 답변이 화면 안에서 펼쳐진다", !d.querySelector(".support-faq-item p").hidden);

  d.querySelector('[data-support-tab="write"]').click();
  d.querySelector("[data-support-category]").value = "AI_PLAN";
  d.querySelector("[data-support-title]").value = "AI 일정 문의";
  d.querySelector("[data-support-content]").value = "일정 수정 방법이 궁금합니다.";
  d.querySelector("[data-support-form]").dispatchEvent(new w.Event("submit", { bubbles: true, cancelable: true }));
  await until(() => submitted !== null && d.querySelectorAll(".support-inquiry-item").length === 1);

  test("1:1 문의 내용을 API로 전송한다", submitted.category === "AI_PLAN" && submitted.title === "AI 일정 문의");
  test("접수 후 내 문의 내역으로 이동한다", !d.querySelector('[data-support-panel="mine"]').hidden);
  test("접수한 문의와 처리 상태를 표시한다", d.querySelector(".support-inquiry-item").textContent.includes("AI 일정 문의")
    && d.querySelector(".support-inquiry-item").textContent.includes("접수 완료"));
  test("문의 폼은 전역 로딩창을 사용하지 않는다", d.querySelector("[data-support-form]").hasAttribute("data-no-global-loading"));

  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed) process.exitCode = 1;
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
