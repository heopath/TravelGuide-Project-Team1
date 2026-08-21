/* 관리자 서비스 표시 버전 수용 기준 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/admin/admin.html");
const JS = path.join(ROOT, "src/main/resources/static/js/pages/admin/admin-version.js");

let passed = 0;
let failed = 0;
const T = (name, condition) => {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name); }
};

function until(predicate, timeoutMs = 4000) {
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

const ok = (version) => ({
  ok: true, status: 200,
  json: async () => ({ success: true, data: { version } }),
});

async function boot(responder) {
  const calls = [];
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/admin?panel=version", runScripts: "outside-only",
  });
  const w = dom.window;
  const d = w.document;
  // 원본 템플릿에서는 th:replace 전이라 공통 푸터 내부가 없다. 렌더링 완료 상태를 재현한다.
  const footerVersion = d.createElement("span");
  footerVersion.className = "footer-version";
  footerVersion.textContent = "v0.9.0";
  d.body.appendChild(footerVersion);
  w.fetch = async (url, options) => {
    calls.push({ url: String(url), options: options || {} });
    return responder(String(url), options || {});
  };
  w.eval(fs.readFileSync(JS, "utf8"));
  if (d.readyState !== "loading") d.dispatchEvent(new w.Event("DOMContentLoaded"));
  await until(() => calls.length > 0);
  return { w, d, calls };
}

async function run() {
  {
    const markup = fs.readFileSync(HTML, "utf8");
    const script = fs.readFileSync(JS, "utf8");
    T("사이드바에 서비스 버전 메뉴가 있다", markup.includes('data-admin-panel="version"'));
    T("서비스 버전 패널이 실연동이다",
      /data-admin-section="version"[\s\S]*?data-admin-state="version">실연동/.test(markup));
    T("페이지 스크립트를 실제로 불러온다", markup.includes("/js/pages/admin/admin-version.js"));
    T("401 로그인 복귀 주소에 버전 패널 쿼리까지 인코딩한다",
      script.includes('encodeURIComponent("/admin?panel=version")'));
  }

  {
    const { d, calls } = await boot(() => ok("v0.9.0"));
    await until(() => d.querySelector("[data-version-input]").value === "v0.9.0");

    T("진입 시 현재 버전을 조회한다",
      calls[0].url === "/api/v1/admin/service-settings/footer-version");
    T("현재 버전을 입력란과 요약에 함께 표시한다",
      d.querySelector("[data-version-current]").textContent === "v0.9.0"
        && d.querySelector("[data-version-input]").value === "v0.9.0");
  }

  {
    const { d, calls } = await boot((url, options) =>
      options.method === "PUT" ? ok("v0.9.1") : ok("v0.9.0"));
    await until(() => d.querySelector("[data-version-input]").value === "v0.9.0");

    d.querySelector("[data-version-input]").value = "0.9.1";
    d.querySelector("[data-version-form]")
      .dispatchEvent(new d.defaultView.Event("submit", { bubbles: true, cancelable: true }));
    await until(() => calls.some((call) => call.options.method === "PUT"));
    await until(() => d.querySelector("[data-version-current]").textContent === "v0.9.1");

    const put = calls.find((call) => call.options.method === "PUT");
    T("저장은 관리자 설정 API의 PUT을 사용한다", put.url.endsWith("/footer-version"));
    T("입력한 버전을 JSON 본문에 담는다", JSON.parse(put.options.body).version === "0.9.1");
    T("저장 결과로 현재 화면 푸터도 즉시 갱신한다",
      d.querySelector(".footer-version").textContent === "v0.9.1");
    T("저장 성공을 관리자에게 알린다",
      d.querySelector("[data-version-message]").textContent.includes("변경했습니다"));
  }

  {
    const { d, calls } = await boot(() => ok("v0.9.0"));
    await until(() => d.querySelector("[data-version-input]").value === "v0.9.0");
    d.querySelector("[data-version-input]").value = "0.9";
    d.querySelector("[data-version-form]")
      .dispatchEvent(new d.defaultView.Event("submit", { bubbles: true, cancelable: true }));
    await new Promise((resolve) => setTimeout(resolve, 30));

    T("잘못된 형식은 서버로 보내지 않는다",
      !calls.some((call) => call.options.method === "PUT"));
    /* 예시로 든 버전을 박아 두면 서비스 버전이 오를 때마다 이 테스트가 깨진다.
     * 보려는 것은 "세 자리 형식을 예시로 보여준다"는 사실이다. */
    T("허용 형식을 화면에서 안내한다",
      /v?\d+\.\d+\.\d+/.test(d.querySelector("[data-version-message]").textContent),
      d.querySelector("[data-version-message]").textContent);
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed > 0) process.exit(1);
}

run().catch((error) => { console.error(error); process.exit(1); });
