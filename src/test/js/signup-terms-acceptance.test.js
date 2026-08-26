/* 회원가입 약관 수용 기준
 *
 *   1. 서비스 이용약관과 개인정보 수집·이용 동의를 따로 읽을 수 있어야 한다.
 *   2. 개인정보 동의에는 목적, 항목, 기간, 거부권과 불이익이 모두 보여야 한다.
 *   3. 두 필수 동의가 없으면 요청을 보내지 않는다.
 *   4. 전체 동의와 개별 동의 상태가 서로 맞고, 가입 요청에도 명시적인 동의값이 실린다.
 */
const { JSDOM, VirtualConsole } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const TEMPLATE = path.join(ROOT, "src/main/resources/templates/auth/signup.html");
const SCRIPT = path.join(ROOT, "src/main/resources/static/js/pages/auth/signup.js");

let passed = 0;
let failed = 0;
const T = (name, condition, detail) => {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name + (detail ? " — " + detail : "")); }
};

const read = (file) => fs.readFileSync(file, "utf8");
const html = read(TEMPLATE);

function bodyOf(source) {
  return source.slice(source.indexOf("<body"), source.indexOf("</body>") + 7);
}

async function openPage() {
  const virtualConsole = new VirtualConsole();
  virtualConsole.on("jsdomError", () => {});
  const dom = new JSDOM("<!doctype html><html>" + bodyOf(html) + "</html>", {
    url: "http://localhost:8080/auth/signup",
    runScripts: "outside-only",
    virtualConsole
  });
  const calls = [];
  dom.window.document.querySelector(".cf-turnstile")?.remove();

  const dialog = dom.window.document.querySelector("#agreement-dialog");
  dialog.showModal = function () { dialog.setAttribute("open", ""); };
  dialog.close = function () { dialog.removeAttribute("open"); };
  dom.window.fetch = async function (url, options) {
    calls.push({ url: String(url), body: JSON.parse(options.body) });
    return {
      ok: true,
      json: async () => ({ success: true, message: "회원가입이 완료되었습니다." })
    };
  };

  dom.window.eval(read(SCRIPT));
  await new Promise((resolve) => setTimeout(resolve, 0));
  return { window: dom.window, document: dom.window.document, calls };
}

function change(page, element) {
  element.dispatchEvent(new page.window.Event("change", { bubbles: true }));
}

async function submit(page) {
  page.document.querySelector("#signup-form").dispatchEvent(
      new page.window.Event("submit", { bubbles: true, cancelable: true })
  );
  await new Promise((resolve) => setTimeout(resolve, 0));
}

(async function run() {
  T("서비스 이용약관 전문이 문서 안에 있다",
    html.includes("제1조 (목적)") && html.includes("제9조 (책임 및 분쟁 해결)"));
  T("개인정보 필수 고지 네 가지가 모두 있다",
    ["수집·이용 목적", "수집 항목", "보유·이용 기간", "동의 거부 권리 및 불이익"]
      .every((text) => html.includes(text)));
  T("두 동의는 각각 필수 입력이다",
    /id="signup-terms-agreed"[^>]+required/.test(html)
      && /id="signup-privacy-agreed"[^>]+required/.test(html));

  const page = await openPage();
  const terms = page.document.querySelector("#signup-terms-agreed");
  const privacy = page.document.querySelector("#signup-privacy-agreed");
  const all = page.document.querySelector("#signup-agree-all");

  page.document.querySelector('[data-agreement-open="service-terms"]').click();
  T("이용약관 버튼은 이용약관 전문을 연다",
    page.document.querySelector("#agreement-dialog").hasAttribute("open")
      && !page.document.querySelector('[data-agreement-document="service-terms"]').hidden
      && page.document.querySelector("#agreement-dialog-title").textContent === "서비스 이용약관");
  page.document.querySelector("[data-agreement-close]").click();

  page.document.querySelector('[data-agreement-open="privacy-consent"]').click();
  T("개인정보 버튼은 개인정보 동의 내용을 연다",
    !page.document.querySelector('[data-agreement-document="privacy-consent"]').hidden
      && page.document.querySelector("#agreement-dialog-title").textContent.includes("개인정보"));
  page.document.querySelector("[data-agreement-close]").click();

  await submit(page);
  T("필수 동의가 없으면 가입 요청을 보내지 않는다",
    page.calls.length === 0
      && page.document.querySelector("#signup-error").textContent.includes("모두 동의"));

  all.checked = true;
  change(page, all);
  T("전체 동의는 두 필수 항목을 함께 선택한다", terms.checked && privacy.checked);

  privacy.checked = false;
  change(page, privacy);
  T("한 항목을 해제하면 전체 동의가 중간 상태가 된다", !all.checked && all.indeterminate);

  page.document.querySelector("#signup-email").value = "member@example.com";
  page.document.querySelector("#signup-password").value = "password123";
  page.document.querySelector("#signup-nickname").value = "여행자";
  all.checked = true;
  change(page, all);
  await submit(page);
  T("가입 요청에 두 동의값을 명시적으로 보낸다",
    page.calls.length === 1
      && page.calls[0].body.termsAgreed === true
      && page.calls[0].body.privacyAgreed === true,
    JSON.stringify(page.calls));

  console.log("");
  console.log(passed + " passed, " + failed + " failed");
  if (failed > 0) process.exit(1);
})();
