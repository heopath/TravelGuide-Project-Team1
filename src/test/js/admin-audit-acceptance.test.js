/* 관리자 조작 이력 조회 수용 기준
 * 실행: src/test/js 에서 `npm test`
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");
const { readMarkup } = require("./markup");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/admin/admin.html");
const JS = path.join(ROOT, "src/main/resources/static/js/pages/admin/admin-audit.js");

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

const entry = (id, overrides) => Object.assign({
  adminAuditLogId: id,
  adminUserId: 12,
  adminNickname: "민재",
  actionType: "TICKET_PRODUCT_STATUS_CHANGE",
  targetType: "TICKET_PRODUCT",
  targetId: "20",
  beforeData: '{"status":"DRAFT"}',
  afterData: '{"status":"SOLD_OUT","name":"모의 관광 티켓 20"}',
  ipAddress: "203.0.113.9",
  userAgent: "Mozilla/5.0",
  occurredAt: "2026-08-13T06:15:14Z",
}, overrides || {});

const ok = (data) => ({
  ok: true, status: 200, json: async () => ({ success: true, code: "SUCCESS", data })
});
const fail = (status, code, message) => ({
  ok: false, status, json: async () => ({ success: false, code, message })
});
const page = (items, actionTypes) => ({
  items, page: 0, size: 30, total: items.length, totalPages: 1,
  actionTypes: actionTypes || ["TICKET_PRODUCT_STATUS_CHANGE", "PLACE_UPDATE"],
});

async function boot(responder) {
  const calls = [];
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/admin", runScripts: "outside-only"
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

const rows = (d) => [...d.querySelectorAll("[data-audit-list] .admin-audit-row")];
const changes = (row) => [...row.querySelectorAll("[data-audit-change] small")].map((el) => el.textContent);
const notice = (d) => d.querySelector("[data-audit-notice]");
const detailRows = (d) => [...d.querySelectorAll("[data-audit-detail-changes] .admin-audit-change-row:not(.is-head)")];

async function run() {
  /* ── 마크업 ── */
  {
    const markup = readMarkup(HTML);
    const section = markup.slice(
      markup.indexOf('data-admin-section="audit"'),
      markup.indexOf('data-admin-section="reservations"')
    );

    T("조작 이력 패널이 실연동으로 표시된다",
      /<span class="admin-tag live" data-admin-state="audit">실연동<\/span>/.test(section));
    T("사이드바에 조작 이력 항목이 있다",
      /data-admin-panel="audit">[\s\S]*?<em class="live">실연동<\/em>/.test(markup));
    T("수정·삭제 입력을 두지 않는다",
      !section.includes("data-audit-delete") && !section.includes("data-audit-update") && !section.includes("<select"));
    T("동작 필터는 전체 버튼만 두고 나머지는 서버가 채운다",
      (section.match(/data-audit-action="/g) || []).length === 1);
    T("대상 검색은 입력창과 검색 버튼을 함께 둔다",
      section.includes("data-audit-search-form") && section.includes('type="submit"'));
    T("조회 건수와 새로고침 자리가 있다",
      section.includes("data-audit-count") && section.includes("data-audit-refresh"));
    T("선택한 기록을 확인하는 상세 창이 있다", section.includes("data-audit-modal"));
    T("페이지 스크립트를 실제로 불러온다", markup.includes("/js/pages/admin/admin-audit.js"));
  }

  /* ── 목록 ── */
  {
    const { d, calls } = await boot(() => ok(page([entry(1), entry(2)])));
    await until(() => rows(d).length === 2);

    T("감사 로그 API를 호출한다", calls[0].startsWith("/api/v1/admin/audit-logs?"));
    T("이력마다 한 행을 그린다", rows(d).length === 2);
    T("관리자 닉네임을 보여준다",
      rows(d)[0].querySelector("[data-audit-admin]").textContent === "민재");
    T("동작을 한국어로 보여준다",
      rows(d)[0].querySelector("strong").textContent === "판매 상태 변경");
    T("대상 종류와 번호를 함께 보여준다",
      rows(d)[0].querySelector("small").textContent === "예약 상품 20");
    T("전체 조회 건수를 보여준다",
      d.querySelector("[data-audit-count]").textContent === "조회 결과 2건");
    T("각 이력에 상세 보기 버튼이 있다",
      rows(d)[0].querySelector("[data-audit-detail]").textContent === "상세 보기");
  }

  /* ── 목록 요약과 상세 전후 값 ── */
  {
    const { d } = await boot(() => ok(page([entry(1)])));
    await until(() => rows(d).length === 1);

    const lines = changes(rows(d)[0]);
    T("목록에는 첫 변경을 쉬운 이름으로 요약한다", lines.includes("상태: DRAFT → SOLD_OUT"));
    T("목록에서 나머지 변경 개수를 알려준다", lines.includes("외 1개 변경"));

    rows(d)[0].querySelector("[data-audit-detail]").click();
    const modal = d.querySelector("[data-audit-modal]");
    T("상세 보기를 누르면 상세 창이 열린다", modal.hidden === false);
    T("상세 창에 동작과 대상을 함께 보여준다",
      d.querySelector("[data-audit-detail-summary]").textContent.includes("판매 상태 변경 · 예약 상품 20"));
    T("상세 창에는 모든 변경 항목을 보여준다", detailRows(d).length === 2);
    T("상세 창은 이전 값과 이후 값을 나눠 보여준다",
      detailRows(d)[0].textContent.includes("DRAFT") && detailRows(d)[0].textContent.includes("SOLD_OUT"));
    T("한쪽에만 있는 값도 상세 창에서 빠뜨리지 않는다",
      detailRows(d)[1].textContent.includes("모의 관광 티켓 20"));
    T("접속 IP와 요청 정보를 상세 창에 보여준다",
      d.querySelector("[data-audit-detail-meta]").textContent.includes("203.0.113.9"));

    d.querySelector("[data-audit-modal-cancel]").click();
    T("닫기 버튼으로 상세 창을 닫는다", modal.hidden === true);

    rows(d)[0].querySelector("[data-audit-detail]").click();
    d.dispatchEvent(new d.defaultView.KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    T("Esc 키로도 상세 창을 닫는다", modal.hidden === true);
  }

  /* ── 등록처럼 before가 없는 경우 ── */
  {
    const { d } = await boot(() => ok(page([
      entry(1, { actionType: "TICKET_PRODUCT_CREATE", beforeData: null, afterData: '{"name":"새 상품"}' }),
    ])));
    await until(() => rows(d).length === 1);

    T("before가 없으면 이후 값만 보여준다", changes(rows(d)[0]).includes("이름: 새 상품"));
    T("등록 동작도 한국어로 보여준다",
      rows(d)[0].querySelector("strong").textContent === "상품 등록");
  }

  /* ── 본문이 아예 없는 경우 ── */
  {
    const { d } = await boot(() => ok(page([entry(1, { beforeData: null, afterData: null })])));
    await until(() => rows(d).length === 1);

    T("본문이 없으면 그 사실을 밝힌다",
      rows(d)[0].querySelector("[data-audit-empty-payload]").textContent === "기록된 값 없음");
  }

  /* ── 탈퇴한 관리자 ── */
  {
    const { d } = await boot(() => ok(page([entry(1, { adminUserId: null, adminNickname: null })])));
    await until(() => rows(d).length === 1);

    T("계정이 지워져도 이력을 감추지 않는다", rows(d).length === 1);
    T("작성자를 알 수 없다고 표시한다",
      rows(d)[0].querySelector("[data-audit-admin]").textContent === "알 수 없음");
  }

  /* ── 필터는 서버가 준 종류로 만든다 ── */
  {
    const { d, calls } = await boot(() => ok(page([entry(1)], ["PLACE_UPDATE", "REPORT_PROCESS"])));
    await until(() => rows(d).length === 1);

    const chips = [...d.querySelectorAll("[data-audit-action]")].map((b) => b.dataset.auditAction);
    T("서버가 준 동작만 필터로 만든다",
      chips.includes("PLACE_UPDATE") && chips.includes("REPORT_PROCESS")
        && !chips.includes("TICKET_PRODUCT_STATUS_CHANGE"));

    d.querySelector('[data-audit-action="PLACE_UPDATE"]').click();
    await until(() => calls.length === 2);
    T("필터를 고르면 actionType을 붙여 조회한다", calls[1].includes("actionType=PLACE_UPDATE"));
    T("고른 필터 하나만 활성 표시된다",
      d.querySelectorAll("[data-audit-action].on").length === 1);
    T("고른 필터를 보조기기에도 알린다",
      d.querySelector('[data-audit-action="PLACE_UPDATE"]').getAttribute("aria-pressed") === "true");
  }

  /* ── 대상 검색·해제·새로고침 ── */
  {
    const { d, calls } = await boot(() => ok(page([entry(1)])));
    await until(() => rows(d).length === 1);

    const search = d.querySelector("[data-audit-search]");
    search.value = "20";
    d.querySelector("[data-audit-search-form]").dispatchEvent(
      new d.defaultView.Event("submit", { bubbles: true, cancelable: true })
    );
    await until(() => calls.length === 2);

    T("대상 번호로 조회한다", calls[1].includes("targetId=20"));
    T("검색 중에는 검색 해제 버튼을 보여준다",
      d.querySelector("[data-audit-search-clear]").hidden === false);

    d.querySelector("[data-audit-refresh]").click();
    await until(() => calls.length === 3);
    T("새로고침해도 검색 조건을 유지한다", calls[2].includes("targetId=20"));

    d.querySelector("[data-audit-search-clear]").click();
    await until(() => calls.length === 4);
    T("검색 해제는 대상 조건 없이 다시 조회한다", !calls[3].includes("targetId="));
    T("검색 해제 뒤 입력창을 비운다", search.value === "");
  }

  /* ── IP가 전부 로컬이면 그 사실을 밝힌다 (#218) ── */
  {
    const { d } = await boot(() => ok(page([entry(1, { ipAddress: "::1" }), entry(2, { ipAddress: "::1" })])));
    await until(() => rows(d).length === 2);

    T("접속 IP가 모두 로컬이면 안내를 띄운다",
      notice(d).hidden === false && notice(d).textContent.includes("프록시"));
  }
  {
    const { d } = await boot(() => ok(page([entry(1)])));
    await until(() => rows(d).length === 1);

    T("실제 IP가 있으면 안내를 띄우지 않는다", notice(d).hidden === true);
  }

  /* ── 빈 목록과 실패 ── */
  {
    const { d } = await boot(() => ok(page([])));
    await until(() => d.querySelector("[data-audit-empty]").textContent.includes("없어요"));

    T("이력이 없으면 그대로 알린다",
      d.querySelector("[data-audit-empty]").textContent === "아직 기록된 변경 이력이 없어요.");
  }
  {
    const { d } = await boot(() => fail(403, "FORBIDDEN", "권한이 없습니다."));
    await until(() => d.querySelector("[data-audit-empty]").textContent.includes("관리자"));

    T("403이면 관리자 전용임을 알린다",
      d.querySelector("[data-audit-empty]").textContent.includes("관리자만 접근할 수 있습니다."));
    T("403이면 행을 그리지 않는다", rows(d).length === 0);
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed > 0) process.exit(1);
}

run().catch((error) => { console.error(error); process.exit(1); });
