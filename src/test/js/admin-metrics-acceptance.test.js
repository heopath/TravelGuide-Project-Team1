/* 관리자 운영 홈 지표 수용 기준
 * 실행: src/test/js 에서 `npm test`
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");
const { readMarkup } = require("./markup");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/admin/admin.html");
const JS = path.join(ROOT, "src/main/resources/static/js/pages/admin/admin-metrics.js");

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
  todayReservations: 1284,
  openInquiries: 7,
  lowStockSlots: 3,
  lowStockThreshold: 5,
  errorRate: 0.0125,
  collectedAt: "2026-08-13T09:00:00Z",
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

const value = (d, name) =>
  d.querySelector(`[data-admin-section="overview"] [data-metric="${name}"]`).textContent;
const note = (d) => d.querySelector("[data-metrics-note]").textContent;

async function run() {
  /* ── 마크업 ── */
  {
    const markup = readMarkup(HTML);
    const section = markup.slice(
      markup.indexOf('data-admin-section="overview"'),
      markup.indexOf('data-admin-section="version"')
    );

    T("운영 홈 패널은 실연동으로 표시된다",
      /<span class="admin-tag live" data-admin-state="overview">실연동<\/span>/.test(section));
    T("사이드바에 운영 홈 항목이 있다",
      /data-admin-panel="overview">[\s\S]*?<em class="live">실연동<\/em>/.test(markup));
    T("네 칸의 값 자리가 모두 비어 있다",
      (section.match(/<strong data-metric="[a-zA-Z]+">—<\/strong>/g) || []).length === 4);
    T("마크업에 집계 숫자가 박혀 있지 않다", !/\d{1,3},\d{3}/.test(section));
    T("셀 원본이 없는 대기 사용자 칸은 남기지 않았다", !section.includes("waitingUsers"));
    T("페이지 스크립트를 실제로 불러온다", markup.includes("/js/pages/admin/admin-metrics.js"));
  }

  /* ── 지표 표시 ── */
  {
    const { d, calls } = await boot(() => ok(metrics()));
    await until(() => value(d, "todayReservations") !== "—");

    T("운영 현황 API를 호출한다", calls[0] === "/api/v1/admin/operation-metrics");
    T("오늘 예약 수를 천 단위로 보여준다", value(d, "todayReservations") === "1,284");
    T("미처리 문의 수를 보여준다", value(d, "openInquiries") === "7");
    T("재고 경고 수를 보여준다", value(d, "lowStockSlots") === "3");
    T("오류율을 백분율로 보여준다", value(d, "errorRate") === "1.25%");
    T("재고 경고 기준을 함께 밝힌다",
      d.querySelector("[data-stock-caption]").textContent === "남은 자리 5개 이하");
  }

  /* ── 0건과 못 잰 것을 구분한다 ── */
  {
    const { d } = await boot(() => ok(metrics({
      todayReservations: 0, openInquiries: 0, lowStockSlots: 0, errorRate: null,
    })));
    await until(() => value(d, "errorRate") !== "—" || value(d, "todayReservations") === "0");

    T("실제로 0건이면 0으로 보여준다",
      value(d, "todayReservations") === "0" && value(d, "openInquiries") === "0");
    T("잰 적이 없는 오류율은 0%가 아니라 빈 자리다", value(d, "errorRate") === "—");
  }

  /* ── 새로고침 ── */
  {
    const { d, calls } = await boot(() => ok(metrics()));
    await until(() => value(d, "todayReservations") !== "—");

    d.querySelector("[data-metrics-refresh]").click();
    await until(() => calls.length === 2);

    T("새로고침으로 다시 조회한다", calls[1] === "/api/v1/admin/operation-metrics");
  }

  /* ── 실패 ── */
  {
    const { d } = await boot(() => fail(403, "FORBIDDEN", "권한이 없습니다."));
    await until(() => note(d).includes("관리자"));

    T("403이면 관리자 전용임을 알린다", note(d).includes("관리자만 접근할 수 있습니다."));
    T("실패하면 네 칸을 모두 비운다",
      ["todayReservations", "openInquiries", "lowStockSlots", "errorRate"]
        .every((name) => value(d, name) === "—"));
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed > 0) process.exit(1);
}

run().catch((error) => { console.error(error); process.exit(1); });
