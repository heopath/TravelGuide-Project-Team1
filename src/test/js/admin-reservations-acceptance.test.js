/* 관리자 예약 모니터링 수용 기준
 * 실행: src/test/js 에서 `npm test`
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");
const { readMarkup } = require("./markup");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/admin/admin.html");
const JS = path.join(ROOT, "src/main/resources/static/js/pages/admin/admin-reservations.js");

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

const reservation = (id, overrides) => Object.assign({
  reservationId: id,
  reservationNumber: `R2026081200${id}`,
  status: "CONFIRMED",
  totalAmount: 36000,
  currency: "KRW",
  tripId: 5,
  nickname: "여행자",
  productName: "해변 열차 이용권",
  itemCount: 1,
  createdAt: "2026-08-12T01:00:00Z",
  updatedAt: "2026-08-12T02:00:00Z",
  confirmedAt: "2026-08-12T02:00:00Z",
  cancelledAt: null,
  expiresAt: null,
  expiredPending: false,
}, overrides || {});

const ok = (data) => ({
  ok: true, status: 200, json: async () => ({ success: true, code: "SUCCESS", data })
});
const fail = (status, code, message) => ({
  ok: false, status, json: async () => ({ success: false, code, message })
});
const page = (items, expiredPendingTotal) => ({
  items, page: 0, size: 20, total: items.length, totalPages: 1,
  expiredPendingTotal: expiredPendingTotal || 0,
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

const rows = (d) => [...d.querySelectorAll("#reservationList .admin-monitor-row")];
const alertBox = (d) => d.querySelector("[data-reservation-alert]");

async function run() {
  /* ── 마크업 ── */
  {
    const markup = readMarkup(HTML);
    const section = markup.slice(
      markup.indexOf('data-admin-section="reservations"'),
      markup.indexOf('data-admin-section="performance"')
    );

    T("예약 모니터링 패널은 실연동으로 표시된다",
      /<span class="admin-tag live" data-admin-state="reservations">실연동<\/span>/.test(section));
    T("사이드바의 예약 모니터링 항목도 실연동이다",
      /data-admin-panel="reservations">[\s\S]*?<em class="live">실연동<\/em>/.test(markup));
    T("상태 필터와 만료 방치 필터가 있다",
      ["", "PENDING", "CONFIRMED", "CANCELLED", "USED", "EXPIRED_PENDING"]
        .every((v) => section.includes(`data-reservation-status="${v}"`)));
    T("마크업에 예약 건수가 박혀 있지 않다", !/\d{1,3},\d{3}/.test(section));
    T("상태를 바꾸는 입력은 두지 않는다",
      !section.includes("<select") && !section.includes("data-reservation-cancel"));
    T("페이지 스크립트를 실제로 불러온다", markup.includes("/js/pages/admin/admin-reservations.js"));
  }

  /* ── 목록 ── */
  {
    const { d, calls } = await boot(() => ok(page([reservation(1), reservation(2)])));
    await until(() => rows(d).length === 2);

    T("관리자 예약 API를 호출한다", calls[0].startsWith("/api/v1/admin/reservations?"));
    T("예약마다 한 행을 그린다", rows(d).length === 2);
    T("예약번호를 앞세운다", rows(d)[0].querySelector("strong").textContent === "R20260812001");
    T("상품과 회원을 함께 밝힌다",
      rows(d)[0].querySelector("small").textContent.includes("해변 열차 이용권")
        && rows(d)[0].querySelector("small").textContent.includes("여행자"));
    T("결제 금액을 바로 보여준다",
      rows(d)[0].querySelector("[data-reservation-amount]").textContent === "36,000원");
    T("여행 연결 여부를 바로 보여준다",
      rows(d)[0].querySelector("[data-reservation-trip]").textContent === "여행 일정 연결됨");
    T("상태를 한국어로 보여준다",
      rows(d)[0].querySelector(".admin-status").textContent === "확정");
    T("전체 조회 건수를 보여준다",
      d.querySelector("[data-reservation-count]").textContent === "조회 결과 2건");
  }

  /* ── 여러 건 묶인 예약 ── */
  {
    const { d } = await boot(() => ok(page([reservation(1, { itemCount: 3 })])));
    await until(() => rows(d).length === 1);

    T("상품이 여러 건이면 나머지 건수를 밝힌다",
      rows(d)[0].querySelector("small").textContent.includes("외 2건"));
  }

  /* ── 상태별로 의미 있는 시각을 쓴다 ── */
  {
    const cancelledAt = "2026-08-12T05:30:00Z";
    const updatedAt = "2026-08-12T09:00:00Z";
    const { d } = await boot(() => ok(page([
      reservation(1, { status: "CANCELLED", cancelledAt, updatedAt, confirmedAt: null }),
    ])));
    await until(() => rows(d).length === 1);

    /* 표기는 로케일·타임존에 따라 달라지므로 화면과 같은 방식으로 만들어 비교한다. */
    const format = (value) => new Intl.DateTimeFormat("ko-KR", {
      month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit",
    }).format(new Date(value));

    const shown = rows(d)[0].querySelector("[data-changed-at] strong").textContent;
    T("취소 건은 취소 시각을 보여준다", shown === format(cancelledAt));
    T("취소 건에 updated_at을 쓰지 않는다", shown !== format(updatedAt));
  }

  /* ── 만료 방치 ── */
  {
    const { d, calls } = await boot(() => ok(page([
      reservation(1, {
        status: "PENDING",
        expiresAt: "2026-08-11T00:00:00Z",
        confirmedAt: null,
        expiredPending: true,
      }),
      reservation(2),
    ], 4)));
    await until(() => rows(d).length === 2);

    T("만료 시각이 지난 대기 건을 표시한다",
      rows(d)[0].querySelector("[data-expired-pending]").textContent.includes("결제 기한")
        && rows(d)[0].querySelector("[data-expired-pending]").textContent.includes("지남"));
    T("만료 방치 행을 눈에 띄게 구분한다", rows(d)[0].classList.contains("needs-attention"));
    T("정상 건에는 표시하지 않는다",
      rows(d)[1].querySelector("[data-expired-pending]") === null);
    T("상태는 대기 그대로 둔다",
      rows(d)[0].querySelector(".admin-status").textContent === "대기");
    T("방치된 전체 건수를 따로 알린다",
      alertBox(d).hidden === false && alertBox(d).textContent.includes("4건"));
    d.querySelector("[data-reservation-alert-open]").click();
    await until(() => calls.length === 2);
    T("방치 경고에서 해당 예약 목록을 바로 연다",
      calls[1].includes("expiredPendingOnly=true")
        && d.querySelector('[data-reservation-status="EXPIRED_PENDING"]').classList.contains("on"));
  }

  /* ── 방치 건이 없으면 배너를 띄우지 않는다 ── */
  {
    const { d } = await boot(() => ok(page([reservation(1)], 0)));
    await until(() => rows(d).length === 1);

    T("방치 건이 없으면 경고를 띄우지 않는다", alertBox(d).hidden === true);
  }

  /* ── 만료 방치 필터 ── */
  {
    const { d, calls } = await boot(() => ok(page([reservation(1)], 2)));
    await until(() => rows(d).length === 1);

    d.querySelector('[data-reservation-status="EXPIRED_PENDING"]').click();
    await until(() => calls.length === 2);

    T("만료 방치 필터는 전용 조건으로 조회한다", calls[1].includes("expiredPendingOnly=true"));
    T("만료 방치 필터에는 status를 함께 보내지 않는다", !calls[1].includes("status="));
  }

  /* ── 상태 필터와 검색 ── */
  {
    const { d, calls } = await boot(() => ok(page([reservation(1)])));
    await until(() => rows(d).length === 1);

    d.querySelector('[data-reservation-status="PENDING"]').click();
    await until(() => calls.length === 2);
    T("상태 필터를 질의에 반영한다",
      calls[1].includes("status=PENDING") && !calls[1].includes("expiredPendingOnly"));

    const search = d.querySelector("[data-reservation-search]");
    search.value = "R2026";
    d.querySelector("[data-reservation-search-form]")
      .dispatchEvent(new d.defaultView.Event("submit", { bubbles: true, cancelable: true }));
    await until(() => calls.length === 3);
    T("검색어를 질의에 반영한다", calls[2].includes("keyword=R2026"));
    T("검색 뒤 초기화 버튼을 보여준다",
      d.querySelector("[data-reservation-search-clear]").hidden === false);

    d.querySelector("[data-reservation-search-clear]").click();
    await until(() => calls.length === 4);
    T("검색을 초기화하면 전체 목록을 다시 조회한다",
      !calls[3].includes("keyword=") && search.value === "");

    d.querySelector("[data-reservation-refresh]").click();
    await until(() => calls.length === 5);
    T("새로고침으로 현재 조건을 다시 조회한다", calls[4].includes("status=PENDING"));
  }

  /* ── 실패와 빈 목록 ── */
  {
    const { d } = await boot(() => fail(403, "FORBIDDEN", "권한이 없습니다."));
    await until(() => d.getElementById("reservationEmpty").textContent.includes("관리자"));

    T("403이면 관리자 전용임을 알린다",
      d.getElementById("reservationEmpty").textContent.includes("관리자만 접근할 수 있습니다."));
    T("403이면 행을 그리지 않는다", rows(d).length === 0);
  }
  {
    const { d } = await boot(() => ok(page([])));
    await until(() => d.getElementById("reservationEmpty").textContent.includes("없어요"));

    T("접수된 예약이 없으면 그대로 알린다",
      d.getElementById("reservationEmpty").textContent === "접수된 예약이 없어요.");
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed > 0) process.exit(1);
}

run().catch((error) => { console.error(error); process.exit(1); });
