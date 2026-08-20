/* 관리자 예약 상품·재고 수용 기준
 * 실행: src/test/js 에서 `npm test`
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");
const { readMarkup } = require("./markup");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/admin/admin.html");
const JS = path.join(ROOT, "src/main/resources/static/js/pages/admin/admin-products.js");

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

const product = (id, overrides) => Object.assign({
  ticketProductId: id,
  placeId: 10 + id,
  placeName: "해운대 블루라인파크",
  region: "부산광역시",
  city: "해운대구",
  name: `해변 열차 이용권 ${id}`,
  status: "ON_SALE",
  saleStartAt: "2026-08-01T00:00:00Z",
  saleEndAt: "2026-09-30T00:00:00Z",
  usageStartDate: "2026-09-01",
  usageEndDate: "2026-09-30",
  optionCount: 2,
  slotCount: 4,
  totalQuantity: 120,
  reservedQuantity: 45,
  remainingQuantity: 75,
  minUnitPrice: 12000,
  currency: "KRW",
}, overrides || {});

const ok = (data) => ({
  ok: true, status: 200, json: async () => ({ success: true, code: "SUCCESS", data })
});
const fail = (status, code, message) => ({
  ok: false, status, json: async () => ({ success: false, code, message })
});
const page = (items) => ({ items, page: 0, size: 20, total: items.length, totalPages: 1 });

async function boot(responder) {
  const calls = [];
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/admin",
    runScripts: "outside-only"
  });
  const w = dom.window;
  const d = w.document;

  w.fetch = async (url, options) => {
    calls.push({ url: String(url), options: options || {} });
    return responder(String(url), options || {});
  };

  w.eval(fs.readFileSync(JS, "utf8"));
  if (d.readyState !== "loading") d.dispatchEvent(new w.Event("DOMContentLoaded"));
  await until(() => calls.length > 0);
  return { w, d, calls };
}

const rows = (d) => [...d.querySelectorAll("#productList .admin-product-row")];

async function run() {
  /* ── 마크업: 연동 전 흔적과 가짜 수치가 남아 있으면 안 된다 ── */
  {
    const markup = readMarkup(HTML);
    const section = markup.slice(
      markup.indexOf('data-admin-section="products"'),
      markup.indexOf('data-admin-section="reservations"')
    );

    T("예약 상품 패널은 실연동으로 표시된다",
      /<span class="admin-tag live" data-admin-state="products">실연동<\/span>/.test(section));
    T("사이드바의 예약 상품 항목도 실연동이다",
      /data-admin-panel="products">[\s\S]*?<em class="live">실연동<\/em>/.test(markup));
    T("판매 상태 필터 5개와 전체 버튼이 있다",
      ["", "DRAFT", "ON_SALE", "SOLD_OUT", "ENDED", "CANCELLED"]
        .every((value) => section.includes(`data-product-status="${value}"`)));
    T("마크업에 재고 숫자가 박혀 있지 않다", !/\d{1,3},\d{3}/.test(section));
    T("상품 목록과 빈 상태 자리는 그대로 쓴다",
      section.includes('id="productList"') && section.includes('id="productEmpty"'));
  }

  /* ── 목록 조회 ── */
  {
    const { d, calls } = await boot(() => ok(page([product(1), product(2)])));
    await until(() => rows(d).length === 2);

    T("관리자 상품 API를 호출한다",
      calls[0].url.startsWith("/api/v1/admin/ticket-products?"));
    T("기본 페이지와 크기를 함께 보낸다",
      calls[0].url.includes("page=0") && calls[0].url.includes("size=20"));
    T("상품마다 한 행을 그린다", rows(d).length === 2);
    T("상품명과 장소를 한 칸에 담는다",
      rows(d)[0].querySelector("strong").textContent === "해변 열차 이용권 1"
        && rows(d)[0].querySelector("small").textContent.includes("해운대"));
    T("재고를 남은 수량과 전체 수량으로 보여준다",
      rows(d)[0].querySelector("[data-product-stock]").textContent === "75 / 120");
    T("목록이 채워지면 안내 문구를 감춘다", d.getElementById("productEmpty").hidden === true);
  }

  /* ── 시간대가 없는 상품은 재고 0과 구분한다 ── */
  {
    const { d } = await boot(() => ok(page([
      product(1, { slotCount: 0, totalQuantity: 0, reservedQuantity: 0, remainingQuantity: 0 }),
      product(2, { slotCount: 3, totalQuantity: 30, reservedQuantity: 30, remainingQuantity: 0 }),
    ])));
    await until(() => rows(d).length === 2);

    T("시간대가 없으면 시간대 없음으로 표시한다",
      rows(d)[0].querySelector("[data-product-stock]").textContent === "시간대 없음");
    T("시간대가 있고 재고가 0이면 수량으로 표시한다",
      rows(d)[1].querySelector("[data-product-stock]").textContent === "0 / 30");
  }

  /* ── 옵션이 없으면 판매 중이어도 노출되지 않는다는 것을 알린다 ── */
  {
    const { d } = await boot(() => ok(page([product(1, { optionCount: 0 })])));
    await until(() => rows(d).length === 1);

    T("옵션이 없는 상품에는 옵션 없음을 표시한다",
      rows(d)[0].querySelector("[data-product-note]").textContent === "옵션 없음");
  }

  /* ── 판매 상태 변경 ── */
  {
    let patched = null;
    const { d, calls } = await boot((url, options) => {
      if (options.method === "PATCH") {
        patched = JSON.parse(options.body);
        return ok(product(1, { status: patched.status, remainingQuantity: 75 }));
      }
      return ok(page([product(1)]));
    });
    await until(() => rows(d).length === 1);

    const select = d.querySelector('[data-product-status="1"]');
    select.value = "SOLD_OUT";
    select.dispatchEvent(new d.defaultView.Event("change"));
    await until(() => patched !== null);

    T("상태 변경은 PATCH로 보낸다",
      calls.some((call) => call.options.method === "PATCH"
        && call.url === "/api/v1/admin/ticket-products/1/status"));
    T("선택한 상태를 본문에 담는다", patched.status === "SOLD_OUT");
    T("품절로 내려도 재고 수량은 그대로 둔다",
      rows(d)[0].querySelector("[data-product-stock]").textContent === "75 / 120");
  }

  /* ── 상태 변경 실패 시 화면만 바뀐 채로 두지 않는다 ── */
  {
    const { d } = await boot((url, options) => {
      if (options.method === "PATCH") return fail(400, "INVALID_TICKET_REQUEST", "올바른 판매 상태가 아닙니다.");
      return ok(page([product(1, { status: "ON_SALE" })]));
    });
    await until(() => rows(d).length === 1);

    const select = d.querySelector('[data-product-status="1"]');
    select.value = "ENDED";
    select.dispatchEvent(new d.defaultView.Event("change"));
    await until(() => d.getElementById("productEmpty").hidden === false);

    T("실패하면 이전 상태로 되돌린다", select.value === "ON_SALE");
    T("실패 사유를 화면에 남긴다",
      d.getElementById("productEmpty").textContent.includes("올바른 판매 상태가 아닙니다."));
  }

  /* ── 권한 없음 ── */
  {
    const { d } = await boot(() => fail(403, "FORBIDDEN", "권한이 없습니다."));
    await until(() => d.getElementById("productEmpty").textContent.includes("관리자"));

    T("403이면 관리자 전용임을 알린다",
      d.getElementById("productEmpty").textContent.includes("관리자만 접근할 수 있습니다."));
    T("403이면 행을 그리지 않는다", rows(d).length === 0);
  }

  /* ── 비어 있는 목록 ── */
  {
    const { d } = await boot(() => ok(page([])));
    await until(() => d.getElementById("productEmpty").hidden === false
      && !d.getElementById("productEmpty").textContent.includes("불러오는 중"));

    T("등록된 상품이 없으면 그대로 알린다",
      d.getElementById("productEmpty").textContent === "등록된 예약 상품이 없어요.");
  }

  /* ── 상태 필터 ── */
  {
    const { d, calls } = await boot(() => ok(page([product(1)])));
    await until(() => rows(d).length === 1);

    d.querySelector('[data-product-status="ON_SALE"]').click();
    await until(() => calls.length === 2);

    T("필터를 고르면 status를 붙여 다시 조회한다", calls[1].url.includes("status=ON_SALE"));
    T("고른 필터 하나만 활성 표시된다",
      d.querySelectorAll("[data-product-status].on").length === 1
        && d.querySelector('[data-product-status="ON_SALE"]').classList.contains("on"));
  }

  /* ── 키워드 검색 ── */
  {
    const { d, calls } = await boot(() => ok(page([product(1)])));
    await until(() => rows(d).length === 1);

    const search = d.querySelector("[data-product-search]");
    search.value = "해운대";
    const enter = new d.defaultView.KeyboardEvent("keydown", { key: "Enter", cancelable: true });
    search.dispatchEvent(enter);
    await until(() => calls.length === 2);

    T("Enter로 키워드를 검색한다", decodeURIComponent(calls[1].url).includes("keyword=해운대"));
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed > 0) process.exit(1);
}

run().catch((error) => { console.error(error); process.exit(1); });
