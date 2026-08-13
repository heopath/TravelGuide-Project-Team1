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
async function boot(responder, url) {
  const calls = [];
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: url || "http://localhost/admin",
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

    /*
     * 공통 스크립트 프래그먼트는 페이지 JS를 포함하지 않는다. 페이지가 직접 걸어야 하는데
     * 이 태그가 빠져 있어서 기존 admin.js는 한 번도 실행되지 않는 죽은 코드였다.
     * 아래 테스트들은 JS를 직접 eval하므로 이 누락을 잡지 못한다. 마크업에서 확인한다.
     */
    T("페이지 스크립트를 실제로 불러온다", markup.includes("/js/pages/admin/admin.js"));
  }

  /* ── 화면 구성: 피그마 No 21-21의 블록이 모두 있다 ── */
  {
    const { w, d } = await boot(() => ok([]));
    const headings = [...d.querySelectorAll(".admin-section-head h2")].map((el) => el.textContent);

    T("운영 지표 블록이 있다", headings.includes("운영 지표"));
    T("예약 상품·재고 블록이 있다", headings.includes("예약 상품·재고 관리"));
    T("구현 범위에서 뺀 테마 등록 블록은 없다", !headings.includes("테마 여행 등록"));
    T("예약 모니터링 블록이 있다", headings.includes("예약 모니터링"));
    T("성능 모니터링 블록이 있다", headings.includes("성능 모니터링"));
    T("신고 관리 블록이 있다", headings.includes("신고 관리"));

    /* 연동 여부와 무관하게 지켜야 한다. 값을 마크업에 박으면 연동이 빠져도 아무도 모른다. */
    T("지표 값은 마크업에 박아두지 않는다",
      [...d.querySelectorAll(".admin-metric strong")].every((el) => el.textContent.trim() === "—"));
    /*
     * 연동된 패널이 늘어날 때마다 개수를 고쳐야 하는 단정은 두지 않는다. 숫자를 낮추는 것으로
     * 통과시키게 되어 무엇을 지키려던 것인지 잊힌다. 지켜야 할 것은 두 가지다.
     *   1. 사이드바 배지와 패널 태그가 같은 말을 한다
     *   2. 연동 전 태그에는 다른 문구를 넣지 않는다
     */
    const mismatched = [...d.querySelectorAll("[data-admin-panel]")].filter((button) => {
      const tag = d.querySelector(`[data-admin-state="${button.dataset.adminPanel}"]`);
      if (!tag) return false;
      return Boolean(button.querySelector("em.live")) !== tag.classList.contains("live");
    });
    T("사이드바 배지와 패널 표시가 어긋나지 않는다", mismatched.length === 0);
    T("연동 전 블록에는 연동 전 표시가 붙는다",
      [...d.querySelectorAll(".admin-tag:not(.live)")]
        .every((tag) => tag.textContent.trim() === "연동 전"));
    T("아직 연동되지 않은 패널이 남아 있다",
      d.querySelectorAll(".admin-tag:not(.live)").length > 0);
    /*
     * 테마 여행 등록은 구현 범위에서 뺐다. `연동 전`으로 남겨두면 앞으로 붙일 것으로 읽히므로
     * 패널째 지웠다. 되살아나면 다시 "곧 붙는 화면"으로 오해되므로 없는 것을 확인한다.
     */
    T("테마 여행 등록 패널은 남아 있지 않다",
      d.querySelector('[data-admin-panel="theme"]') === null
        && d.querySelector('[data-admin-section="theme"]') === null
        && d.getElementById("themeForm") === null);

    /* ── 상담 채팅: 방 목록 + 대화. 연동 전이라 비어 있고 입력이 막혀 있어야 한다 ── */
    T("상담 채팅 블록이 있다", headings.includes("상담 채팅"));
    T("방 목록과 대화창이 함께 있다",
      Boolean(d.getElementById("chatRoomList")) && Boolean(d.getElementById("chatMessages")));
    T("봇→관리자 전환 버튼 자리가 있다", Boolean(d.getElementById("chatTakeover")));
    T("상담 상태 필터가 봇·대기·내 담당·종료를 구분한다",
      ["BOT", "WAITING", "ASSIGNED", "CLOSED"]
        .every((value) => d.querySelector(`[data-chat-filter="${value}"]`)));
    T("연동 전에는 가짜 대화를 넣지 않는다",
      d.getElementById("chatRoomList").children.length === 0
        && d.getElementById("chatMessages").children.length === 0);
    T("연동 전에는 답장 입력을 막아둔다",
      d.getElementById("chatInput").disabled
        && d.getElementById("chatSend").disabled
        && d.getElementById("chatTakeover").disabled);

    d.querySelector('[data-chat-filter="WAITING"]').click();
    T("상담 필터는 선택만 바뀌고 조회는 하지 않는다",
      w.__adminDashboard.state.chatFilter === "WAITING"
        && d.querySelectorAll('[data-chat-filter].on').length === 1);
  }

  /* ── 사이드바: 고른 화면 하나만 보여준다 (#165) ── */
  {
    const { w, d, calls } = await boot(() => ok([]));
    const shown = () => [...d.querySelectorAll("[data-admin-section]")]
      .filter((section) => !section.hidden)
      .map((section) => section.dataset.adminSection);

    /*
     * 개수를 세는 대신 어떤 패널이 있어야 하는지를 적는다. 숫자만 두면 패널이 하나 사라져도
     * 다른 하나가 늘어나면 통과한다. 추천 장소 관리는 별도 주소라 data-route를 쓰므로 빠진다.
     */
    T("사이드바에 패널이 빠짐없이 있다",
      ["reports", "metrics", "products", "reservations", "performance", "chat", "support", "audit"]
        .every((key) => d.querySelector(`[data-admin-panel="${key}"]`) !== null));
    T("1:1 문의 관리 패널이 있다",
      d.querySelector('[data-admin-panel="support"]') !== null
        && d.querySelector('[data-admin-section="support"]') !== null);

    /* 실연동이 신고 관리뿐이라, 들어오자마자 쓸 수 있는 것이 먼저 보여야 한다. */
    T("기본으로 신고 관리가 열린다",
      shown().length === 1 && shown()[0] === "reports");
    T("기본 화면 메뉴에 현재 표시가 붙는다",
      d.querySelector('[data-admin-panel="reports"]').getAttribute("aria-current") === "page");

    /*
     * 연동 전 항목을 막지 않는다. 막으면 앞으로 무엇이 붙는지 볼 수 없고,
     * "연동 안 된 것을 숨기지 않는다"는 이 화면의 원칙과도 어긋난다.
     */
    T("연동 전 화면도 누를 수 있다",
      [...d.querySelectorAll("[data-admin-panel]")].every((button) => !button.disabled));

    d.querySelector('[data-admin-panel="chat"]').click();
    T("고른 화면 하나만 보인다", shown().length === 1 && shown()[0] === "chat");
    T("현재 표시가 함께 옮겨간다",
      d.querySelectorAll('[data-admin-panel][aria-current="page"]').length === 1
        && d.querySelector('[data-admin-panel="chat"]').classList.contains("is-current"));

    /* 화면을 옮겨도 신고 목록을 다시 부르지 않는다. 사이드바는 표시만 바꾼다. */
    const before = calls.length;
    d.querySelector('[data-admin-panel="metrics"]').click();
    T("화면 전환만으로 API를 다시 부르지 않는다", calls.length === before);
    T("api 상태에 고른 화면이 남는다", w.__adminDashboard.state.panel === "metrics");

    /* 없는 이름이 들어와도 빈 화면이 되면 안 된다. */
    w.__adminDashboard.openPanel("없는화면");
    T("모르는 화면 이름은 신고 관리로 되돌린다",
      shown().length === 1 && shown()[0] === "reports");
  }

  /* ── 주소로 화면을 바로 연다 (#165 · 스토리보드가 관리자를 여덟 장으로 센다) ── */
  {
    const { w, d } = await boot(() => ok([]), "http://localhost/admin?panel=chat");
    const shown = () => [...d.querySelectorAll("[data-admin-section]")]
      .filter((section) => !section.hidden)
      .map((section) => section.dataset.adminSection);

    T("?panel= 으로 해당 화면을 바로 연다", shown().length === 1 && shown()[0] === "chat");
    T("사이드바 현재 표시도 그 화면으로 간다",
      d.querySelector('[data-admin-panel="chat"]').getAttribute("aria-current") === "page");
    T("맞는 값이면 주소를 그대로 둔다",
      new w.URLSearchParams(w.location.search).get("panel") === "chat");
  }

  {
    const { w, d } = await boot(() => ok([]), "http://localhost/admin?panel=없는패널");
    const shown = () => [...d.querySelectorAll("[data-admin-section]")]
      .filter((section) => !section.hidden)
      .map((section) => section.dataset.adminSection);

    T("주소에 모르는 값이 와도 신고 관리를 연다",
      shown().length === 1 && shown()[0] === "reports");
    /* 주소에 그대로 두면 새로고침할 때마다 같은 일이 반복된다. */
    T("열지 못한 값은 주소에서 지운다",
      new w.URLSearchParams(w.location.search).get("panel") === null);
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
    /* 상담 채팅에도 필터가 있으므로 신고 쪽 그룹으로 좁혀서 본다. */
    T("선택한 필터만 활성 표시된다",
      d.querySelectorAll("[data-report-status].on").length === 1
        && d.querySelector("[data-report-status].on").dataset.reportStatus === "PENDING");
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

  /* ── 우리 응답 규격이 아닌 오류는 서버 내부 문구를 그대로 보여주지 않는다 ── */
  {
    const { d } = await boot(() => ({
      ok: false, status: 404,
      json: async () => ({ timestamp: "...", status: 404, error: "Not Found",
        message: "No static resource api/v1/travel-record-reports." })
    }));

    T("서버 내부 오류 문구를 화면에 그대로 노출하지 않는다",
      !d.getElementById("reportEmpty").textContent.includes("No static resource")
        && d.getElementById("reportEmpty").textContent.includes("불러오지 못했어요"));
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
