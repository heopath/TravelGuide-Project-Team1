/* 관리자 대시보드 수용 기준
 * 실행: src/test/js 에서 `npm test`
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/admin/admin.html");
const ADMIN_JS = path.join(ROOT, "src/main/resources/static/js/pages/admin/admin.js");

let passed = 0;
let failed = 0;
const T = (name, condition) => {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name); }
};

const report = (id, status, reason) => ({
  travelRecordReportId: id, travelRecordId: 100 + id, reporterUserId: 9,
  reason, detail: "여행 기록과 무관한 광고성 링크가 포함되어 있습니다.",
  status, processedBy: null, processedAt: null, resolutionNote: null,
  createdAt: "2026-08-10T09:10:00Z"
});

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

/** 신고 목록 응답을 상황별로 바꿔 끼울 수 있는 화면 한 벌. */
async function boot(responder) {
  const calls = [];
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/admin",
    runScripts: "outside-only"
  });
  const w = dom.window;
  const d = w.document;

  w.fetch = async (url) => {
    calls.push(url);
    return responder(url);
  };

  w.eval(fs.readFileSync(ADMIN_JS, "utf8"));
  if (d.readyState !== "loading") d.dispatchEvent(new w.Event("DOMContentLoaded"));
  await until(() => d.body.dataset.pageReady === "true" && !w.__adminDashboard.state.loading);

  return { w, d, calls, api: w.__adminDashboard };
}

const ok = (data) => ({
  ok: true, status: 200, json: async () => ({ success: true, code: "SUCCESS", data })
});
const denied = (status) => ({
  ok: false, status, json: async () => ({ success: false, code: status === 403 ? "FORBIDDEN" : "UNAUTHORIZED" })
});

async function run() {
  /* ── 정적 마크업: 연동 전 자리에 가짜 값이 없어야 한다 ── */
  {
    /* 주석은 화면에 그려지지 않으므로 검사에서 뺀다. 주석에 남긴 과거 사례까지 걸리면 안 된다. */
    const markup = fs.readFileSync(HTML, "utf8").replace(/<!--[\s\S]*?-->/g, "");

    /*
     * 이전 화면은 집계 숫자가 마크업에 박혀 있어, 연동이 빠진 것을 아무도 몰랐다.
     * 자릿수 구분 쉼표가 들어간 숫자는 집계값이 박제됐다는 뜻이라 통째로 막는다.
     */
    T("마크업에 자릿수 쉼표가 들어간 가짜 집계 숫자가 없다", !/\d,\d{3}/.test(markup));
    T("이전 하드코딩 지표가 남아 있지 않다",
      !markup.includes("1,284") && !markup.includes("328명") && !markup.includes("0.18%"));
    T("이전 하드코딩 상품명이 남아 있지 않다",
      !markup.includes("성산일출봉") && !markup.includes("에버랜드") && !markup.includes("빛의 벙커"));
  }

  /* ── 화면 구성: 피그마 No 21-21의 블록이 모두 있다 ── */
  {
    const { d } = await boot(() => ok([]));
    const headings = [...d.querySelectorAll(".admin-section-head h2")].map((el) => el.textContent);

    T("운영 지표 블록이 있다", headings.includes("운영 지표"));
    T("예약 상품·재고 블록이 있다", headings.includes("예약 상품·재고 관리"));
    T("테마 등록 블록이 있다", headings.includes("테마 여행 등록"));
    T("예약 모니터링 블록이 있다", headings.includes("예약 모니터링"));
    T("성능 모니터링 블록이 있다", headings.includes("성능 모니터링"));
    T("신고 관리 블록이 있다", headings.includes("신고 관리"));

    T("연동 전 지표는 값 자리를 비워 둔다",
      [...d.querySelectorAll(".admin-metric strong")].every((el) => el.textContent.trim() === "—"));
    T("연동 전 블록에는 연동 전 표시가 붙는다",
      d.querySelectorAll('.admin-tag:not(.live)').length >= 5);
    T("저장 API가 없는 테마 폼은 제출을 막아둔다",
      d.getElementById("themeSubmit").disabled
        && [...d.querySelectorAll("#themeForm input")].every((input) => input.disabled));
  }

  /* ── 신고 목록: 유일하게 실제로 붙어 있는 API ── */
  {
    const { d, calls, api } = await boot(() => ok([
      report(1, "PENDING", "INAPPROPRIATE"),
      report(2, "RESOLVED", "SPAM")
    ]));

    T("진입 시 신고 목록 API를 호출한다", calls.includes("/api/v1/travel-record-reports"));
    T("받아온 신고가 모두 표시된다", d.querySelectorAll("#reportList > div").length === 2);
    T("신고 사유와 상태를 한글 라벨로 보여준다",
      d.getElementById("reportList").textContent.includes("부적절")
        && d.getElementById("reportList").textContent.includes("처리 완료"));
    T("목록이 있으면 빈 안내를 감춘다", d.getElementById("reportEmpty").hidden);

    d.querySelector('[data-report-status="PENDING"]').click();
    await until(() => calls.some((url) => url.includes("status=PENDING")));
    T("상태 필터가 쿼리로 전달된다", calls.some((url) => url.includes("status=PENDING")));
    T("선택한 필터만 활성 표시된다",
      d.querySelectorAll(".admin-chip.on").length === 1
        && d.querySelector(".admin-chip.on").dataset.reportStatus === "PENDING");
    T("api 상태에 선택한 필터가 남는다", api.state.reportStatus === "PENDING");
  }

  /* ── 권한: 승격 전에는 403이 온다(#163). 빈 화면으로 두지 않는다 ── */
  {
    const { d } = await boot(() => denied(403));

    T("관리자 권한이 없으면 이유를 화면에 밝힌다",
      !d.getElementById("adminAuthNotice").hidden
        && d.getElementById("adminAuthNotice").textContent.includes("관리자 권한"));
    T("권한이 없을 때 목록 자리에도 사유를 남긴다",
      d.getElementById("reportEmpty").textContent.includes("관리자 권한"));
    T("권한 안내는 오류가 아니라 안내로 표시한다",
      !d.getElementById("adminAuthNotice").classList.contains("error"));
  }

  {
    const { d } = await boot(() => denied(401));
    T("비로그인은 오류로 구분해 표시한다",
      d.getElementById("adminAuthNotice").classList.contains("error")
        && d.getElementById("adminAuthNotice").textContent.includes("로그인"));
  }

  /* ── 신고가 하나도 없을 때 ── */
  {
    const { d } = await boot(() => ok([]));
    T("신고가 없으면 비어 있다고 알려준다",
      !d.getElementById("reportEmpty").hidden
        && d.getElementById("reportEmpty").textContent.includes("접수된 신고가 없어요"));
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  process.exit(failed === 0 ? 0 : 1);
}

run().catch((error) => { console.error(error); process.exit(1); });
