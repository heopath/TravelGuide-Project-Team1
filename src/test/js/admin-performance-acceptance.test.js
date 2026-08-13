/* 관리자 성능 모니터링 수용 기준
 * 실행: src/test/js 에서 `npm test`
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/admin/admin.html");
const JS = path.join(ROOT, "src/main/resources/static/js/pages/admin/admin-performance.js");

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

const metrics = (overrides) => Object.assign({
  tps: 12.34,
  averageResponseMs: 87.6,
  errorRate: 0.0125,
  sampleCount: 4321,
  errorCount: 54,
  uptimeSeconds: 5400,
  collectedAt: "2026-08-12T09:00:00Z",
}, overrides || {});

const ok = (data) => ({
  ok: true, status: 200, json: async () => ({ success: true, code: "SUCCESS", data })
});
const fail = (status, code, message) => ({
  ok: false, status, json: async () => ({ success: false, code, message })
});

async function boot(responder) {
  const calls = [];
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/admin",
    runScripts: "outside-only"
  });
  const w = dom.window;
  const d = w.document;

  w.fetch = async (url) => {
    calls.push(String(url));
    return responder(String(url));
  };

  w.eval(fs.readFileSync(JS, "utf8"));
  if (d.readyState !== "loading") d.dispatchEvent(new w.Event("DOMContentLoaded"));
  await until(() => calls.length > 0);
  return { w, d, calls };
}

const value = (d, name) => d.querySelector(`[data-admin-section="performance"] [data-metric="${name}"]`).textContent;
const note = (d) => d.querySelector("[data-performance-note]").textContent;

async function run() {
  /* ── 마크업 ── */
  {
    const markup = fs.readFileSync(HTML, "utf8").replace(/<!--[\s\S]*?-->/g, "");
    const section = markup.slice(
      markup.indexOf('data-admin-section="performance"'),
      markup.indexOf('data-admin-section="chat"')
    );

    T("성능 패널은 실연동으로 표시된다",
      /<span class="admin-tag live" data-admin-state="performance">실연동<\/span>/.test(section));
    T("사이드바의 성능 항목도 실연동이다",
      /data-admin-panel="performance">[\s\S]*?<em class="live">실연동<\/em>/.test(markup));
    T("값 자리는 마크업에서 비어 있다",
      (section.match(/<strong data-metric="[a-zA-Z]+">—<\/strong>/g) || []).length === 3);
    T("페이지 스크립트를 실제로 불러온다",
      markup.includes("/js/pages/admin/admin-performance.js"));
  }

  /* ── 지표 표시 ── */
  {
    const { d, calls } = await boot(() => ok(metrics()));
    await until(() => value(d, "tps") !== "—");

    T("관리자 성능 API를 호출한다", calls[0] === "/api/v1/admin/performance");
    T("Actuator 주소를 직접 부르지 않는다", !calls.some((url) => url.includes("/actuator")));
    T("TPS를 소수 둘째 자리까지 보여준다", value(d, "tps") === "12.34");
    T("평균 응답시간에 단위를 붙인다", value(d, "latency") === "88ms");
    T("오류율을 백분율로 보여준다", value(d, "failureRate") === "1.25%");
    T("표본 수와 가동 시간을 함께 밝힌다",
      note(d).includes("4,321건") && note(d).includes("1시간 30분"));
    T("누적값임을 밝힌다", note(d).includes("누적"));
  }

  /* ── 표본이 없으면 0을 쓰지 않는다 ── */
  {
    const { d } = await boot(() => ok(metrics({
      tps: 0, averageResponseMs: 0, errorRate: 0, sampleCount: 0, errorCount: 0, uptimeSeconds: 3,
    })));
    await until(() => note(d).includes("집계된 요청이 없어요"));

    T("표본이 없으면 값 자리를 비워 둔다",
      value(d, "tps") === "—" && value(d, "latency") === "—" && value(d, "failureRate") === "—");
    T("0%를 오류 없음으로 오해하게 두지 않는다", !note(d).includes("0.00%"));
  }

  /* ── 새로고침 ── */
  {
    const { d, calls } = await boot(() => ok(metrics()));
    await until(() => value(d, "tps") !== "—");

    d.querySelector("[data-performance-refresh]").click();
    await until(() => calls.length === 2);

    T("새로고침으로 다시 조회한다", calls.length === 2 && calls[1] === "/api/v1/admin/performance");
  }

  /* ── 실패 ── */
  {
    const { d } = await boot(() => fail(403, "FORBIDDEN", "권한이 없습니다."));
    await until(() => note(d).includes("관리자"));

    T("403이면 관리자 전용임을 알린다", note(d).includes("관리자만 접근할 수 있습니다."));
    T("실패하면 값 자리를 비워 둔다",
      value(d, "tps") === "—" && value(d, "failureRate") === "—");
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed > 0) process.exit(1);
}

run().catch((error) => { console.error(error); process.exit(1); });
