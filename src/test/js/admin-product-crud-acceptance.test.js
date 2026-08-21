/* 관리자 티켓 상품 등록·수정 + 시간대 재고 조정 수용 기준
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
  ticketProductId: id, placeId: 3, placeName: "해운대 블루라인파크",
  region: "부산광역시", city: "해운대구",
  name: `해변 열차 이용권 ${id}`, status: "ON_SALE",
  saleStartAt: "2026-08-01T00:00:00Z", saleEndAt: "2026-09-30T00:00:00Z",
  usageStartDate: "2026-09-01", usageEndDate: "2026-09-30",
  optionCount: 2, slotCount: 4,
  totalQuantity: 120, reservedQuantity: 45, remainingQuantity: 75,
  minUnitPrice: 12000, currency: "KRW",
}, overrides || {});

const option = (id, overrides) => Object.assign({
  ticketProductOptionId: id, ticketProductId: 1, name: "성인",
  unitPrice: 12000, currency: "KRW", maxQuantityPerUser: 4,
  sortOrder: 1, isActive: true, slotCount: 2,
}, overrides || {});

const slot = (id, overrides) => Object.assign({
  ticketTimeSlotId: id, ticketProductOptionId: 100 + id,
  optionName: "성인", unitPrice: 12000, currency: "KRW",
  usageDate: "2026-09-15", startTime: "10:00:00", endTime: "11:00:00",
  status: "OPEN", totalQuantity: 30, reservedQuantity: 12, remainingQuantity: 18,
  optionActive: true,
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
    url: "http://localhost/admin", runScripts: "outside-only"
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

/** 목록 + 장소 + 시간대를 한 번에 받아주는 기본 응답기. */
function responder(extra) {
  return (url, options) => {
    if (url.includes("/admin/places")) {
      return ok(page([{ placeId: 3, name: "해운대 블루라인파크", city: "해운대구" }]));
    }
    /* 옵션은 시간대보다 먼저 조회된다. 시간대가 옵션에 달리기 때문이다. (#254) */
    if (url.includes("/options")) return ok([option(101), option(102, { name: "청소년", sortOrder: 2 })]);
    if (url.includes("/slots")) return ok([slot(9), slot(10, { status: "CLOSED" })]);
    if (extra) {
      const handled = extra(url, options);
      if (handled) return handled;
    }
    return ok(page([product(1)]));
  };
}

const rows = (d) => [...d.querySelectorAll("#productList .admin-product-row")];
const form = (d) => d.querySelector("[data-product-form]");
const field = (d, name) => d.querySelector(`[data-product-form] [data-field="${name}"]`);
const slotRows = (d) => [...d.querySelectorAll("[data-slot-list] .admin-slot-row")];

async function run() {
  /* ── 마크업 ── */
  {
    const markup = readMarkup(HTML);
    T("새 상품 버튼이 있다", markup.includes("data-product-new"));
    T("등록·수정 폼이 기본으로 감춰져 있다", /data-product-form hidden/.test(markup));
    T("시간대 패널이 기본으로 감춰져 있다", /data-slot-panel hidden/.test(markup));
    T("폼에 필요한 입력이 모두 있다",
      ["placeId", "name", "saleStartAt", "saleEndAt", "usageStartDate", "usageEndDate"]
        .every((f) => markup.includes(`data-field="${f}"`)));
    T("마크업에 수량이 박혀 있지 않다",
      !/value="\d{2,}"/.test(markup.slice(markup.indexOf('data-admin-section="products"'),
        markup.indexOf('data-admin-section="reservations"'))));
  }

  /* ── 등록 ── */
  {
    let posted = null;
    const { d, calls } = await boot(responder((url, options) => {
      if (options.method === "POST") { posted = JSON.parse(options.body); return ok(product(2)); }
      return null;
    }));
    await until(() => rows(d).length === 1);

    d.querySelector("[data-product-new]").click();
    await until(() => form(d).hidden === false && field(d, "placeId").options.length > 0);

    T("새 상품을 누르면 폼이 열린다", form(d).hidden === false);
    T("장소 목록을 받아 선택지로 채운다",
      calls.some((c) => c.url.includes("/admin/places"))
        && field(d, "placeId").options.length === 1);
    T("등록 폼은 빈 값으로 시작한다", field(d, "name").value === "");

    field(d, "placeId").value = "3";
    field(d, "name").value = "새 이용권";
    field(d, "saleStartAt").value = "2026-09-01T09:00";
    field(d, "saleEndAt").value = "2026-09-30T18:00";
    field(d, "usageStartDate").value = "2026-09-01";
    field(d, "usageEndDate").value = "2026-09-30";
    form(d).dispatchEvent(new d.defaultView.Event("submit", { cancelable: true }));
    await until(() => posted !== null && form(d).hidden === true);

    T("등록은 POST로 보낸다",
      calls.some((c) => c.options.method === "POST" && c.url === "/api/v1/admin/ticket-products"));
    T("장소와 상품명을 담는다", posted.placeId === 3 && posted.name === "새 이용권");
    T("판매 일시는 오프셋이 붙은 형식으로 보낸다", /Z$|[+-]\d{2}:\d{2}$/.test(posted.saleStartAt));
    T("이용일은 날짜만 보낸다", posted.usageStartDate === "2026-09-01");
    T("등록 후 폼을 닫는다", form(d).hidden === true);
  }

  /* ── 수정 ── */
  {
    let put = null;
    const { d, calls } = await boot(responder((url, options) => {
      if (options.method === "PUT") { put = JSON.parse(options.body); return ok(product(1)); }
      return null;
    }));
    await until(() => rows(d).length === 1);

    d.querySelector('[data-product-edit="1"]').click();
    await until(() => form(d).hidden === false);

    T("수정을 누르면 기존 값이 채워진다",
      field(d, "name").value === "해변 열차 이용권 1"
        && field(d, "usageStartDate").value === "2026-09-01");

    form(d).dispatchEvent(new d.defaultView.Event("submit", { cancelable: true }));
    await until(() => put !== null);

    T("수정은 PUT으로 상품 주소에 보낸다",
      calls.some((c) => c.options.method === "PUT" && c.url === "/api/v1/admin/ticket-products/1"));
    T("수정 요청에 판매 상태를 담지 않는다", put.status === undefined);
  }

  /* ── 등록 실패 ── */
  {
    const { d } = await boot(responder((url, options) => {
      if (options.method === "POST") {
        return fail(400, "INVALID_TICKET_REQUEST", "티켓 사용일, 수량 또는 여행 정보가 올바르지 않습니다.");
      }
      return null;
    }));
    await until(() => rows(d).length === 1);
    d.querySelector("[data-product-new]").click();
    await until(() => form(d).hidden === false);

    form(d).dispatchEvent(new d.defaultView.Event("submit", { cancelable: true }));
    await until(() => d.querySelector("[data-form-message]").textContent.length > 0);

    T("실패하면 사유를 폼에 남긴다",
      d.querySelector("[data-form-message]").textContent.includes("올바르지 않습니다"));
    T("실패하면 폼을 닫지 않는다", form(d).hidden === false);
  }

  /* ── 시간대 목록 ── */
  {
    const { d, calls } = await boot(responder());
    await until(() => rows(d).length === 1);

    d.querySelector('[data-product-slots="1"]').click();
    await until(() => slotRows(d).length === 2);

    T("시간대 목록을 상품 주소로 조회한다",
      calls.some((c) => c.url === "/api/v1/admin/ticket-products/1/slots"));
    T("옵션명과 이용일을 함께 보여준다",
      slotRows(d)[0].querySelector("strong").textContent === "성인"
        && slotRows(d)[0].querySelector("small").textContent.includes("2026-09-15"));
    T("예약 수량을 따로 보여준다",
      slotRows(d)[0].querySelector("[data-slot-reserved]").textContent === "12");
    T("전체 수량은 예약 수 미만으로 못 내리게 최솟값을 건다",
      slotRows(d)[0].querySelector('[data-slot-total="9"]').min === "12");
    T("닫힌 시간대는 이유를 표시한다",
      slotRows(d)[1].querySelector("[data-slot-closed]").textContent === "시간대 닫힘");
  }

  /* ── 재고 조정 ── */
  {
    let patched = null;
    const { d, calls } = await boot(responder((url, options) => {
      if (options.method === "PATCH" && url.includes("/inventory")) {
        patched = JSON.parse(options.body);
        return ok(slot(9, { totalQuantity: patched.totalQuantity, remainingQuantity: patched.totalQuantity - 12 }));
      }
      return null;
    }));
    await until(() => rows(d).length === 1);
    d.querySelector('[data-product-slots="1"]').click();
    await until(() => slotRows(d).length === 2);

    const input = d.querySelector('[data-slot-total="9"]');
    input.value = "50";
    slotRows(d)[0].querySelector(".admin-chip").click();
    await until(() => patched !== null);

    T("재고는 시간대 주소로 PATCH한다",
      calls.some((c) => c.url === "/api/v1/admin/ticket-slots/9/inventory"));
    T("전체 수량만 보낸다",
      patched.totalQuantity === 50 && patched.reservedQuantity === undefined);
    T("저장 후 서버가 준 값으로 맞춘다", input.value === "50");
  }

  /* ── 예약 수 미만으로 줄이면 되돌린다 ── */
  {
    const { d } = await boot(responder((url, options) => {
      if (options.method === "PATCH" && url.includes("/inventory")) {
        return fail(409, "TICKET_INVENTORY_BELOW_RESERVED", "이미 예약된 수량보다 적게 줄일 수 없습니다.");
      }
      return null;
    }));
    await until(() => rows(d).length === 1);
    d.querySelector('[data-product-slots="1"]').click();
    await until(() => slotRows(d).length === 2);

    const input = d.querySelector('[data-slot-total="9"]');
    input.value = "5";
    slotRows(d)[0].querySelector(".admin-chip").click();
    /* 메시지 자리는 요청 전에 만들어진다. 내용이 채워질 때까지 기다려야 결과를 본다. */
    await until(() => (slotRows(d)[0].querySelector("[data-slot-message]")?.textContent || "").length > 0);

    T("거부되면 입력값을 원래대로 되돌린다", input.value === "30");
    T("거부 사유를 그 줄에 표시한다",
      slotRows(d)[0].querySelector("[data-slot-message]").textContent.includes("적게 줄일 수 없습니다"));
  }

  /* ── 옵션은 있는데 시간대가 없는 상품 ── */
  {
    const { d } = await boot((url) => {
      if (url.includes("/admin/places")) return ok(page([]));
      if (url.includes("/options")) return ok([option(101)]);
      if (url.includes("/slots")) return ok([]);
      return ok(page([product(1, { slotCount: 0 })]));
    });
    await until(() => rows(d).length === 1);
    d.querySelector('[data-product-slots="1"]').click();
    await until(() => d.querySelector("[data-slot-empty]").textContent.includes("시간대가 없어요"));

    T("시간대가 없으면 예약을 못 받는다는 것을 알린다",
      d.querySelector("[data-slot-empty]").textContent.includes("예약을 받을 수 있어요"));
  }

  /*
   * 옵션조차 없는 상품. 시간대는 옵션에 달리므로(#254) 이때는 "시간대를 추가하라"가 아니라
   * "옵션을 먼저 만들라"고 해야 한다. 안 그러면 만들 수 없는 것을 만들라고 시키는 셈이다.
   */
  {
    const { d } = await boot((url) => {
      if (url.includes("/admin/places")) return ok(page([]));
      if (url.includes("/options")) return ok([]);
      if (url.includes("/slots")) return ok([]);
      return ok(page([product(1, { optionCount: 0, slotCount: 0 })]));
    });
    await until(() => rows(d).length === 1);
    d.querySelector('[data-product-slots="1"]').click();
    /* hidden은 처음부터 false라 대기 조건이 못 된다. 실제로 채워진 문구를 기다린다. */
    await until(() => d.querySelector("[data-option-empty]").textContent.trim() !== "");

    T("옵션이 없으면 옵션부터 만들라고 안내한다",
      d.querySelector("[data-slot-empty]").textContent.includes("옵션을 먼저 등록"));
    T("옵션이 없다는 것도 따로 알린다",
      d.querySelector("[data-option-empty]").textContent.includes("등록된 옵션이 없어요"));
    T("목록에서도 옵션 없음을 표시한다",
      rows(d)[0].querySelector("[data-product-note]").textContent === "옵션 없음");
  }

  /* ── 시간대 모달 ── */
  {
    const { d } = await boot(responder());
    await until(() => rows(d).length === 1);

    const backdrop = d.querySelector("[data-slot-panel]");
    d.querySelector('[data-product-slots="1"]').click();
    await until(() => slotRows(d).length === 2);

    /*
     * 예전에는 목록 아래에 펼쳤다. 상품 스무 줄만큼 내려간 자리에 열려, 누른 사람 눈에는
     * 아무 일도 안 일어난 것처럼 보였다.
     */
    T("시간대는 모달로 뜬다",
      backdrop.classList.contains("modal-backdrop")
        && Boolean(d.querySelector("[data-slot-card]")));
    T("모달인 것을 보조기기에도 알린다",
      d.querySelector("[data-slot-card]").getAttribute("aria-modal") === "true");
    /* 뒤 목록이 같이 밀리면 모달 안에서 길을 잃는다. */
    T("열려 있는 동안 뒤 화면 스크롤을 잠근다",
      d.body.dataset.slotModalOpen === "1");

    /* 카드 안을 누르다 닫히면 쓰던 값이 날아간다. */
    d.querySelector("[data-slot-card]").click();
    T("카드 안을 눌러도 닫히지 않는다", backdrop.hidden === false);

    backdrop.dispatchEvent(new d.defaultView.MouseEvent("click", { bubbles: true }));
    T("배경을 누르면 닫힌다", backdrop.hidden === true);
    T("닫으면 스크롤 잠금도 푼다", d.body.dataset.slotModalOpen === undefined);
  }
  {
    const { d } = await boot(responder());
    await until(() => rows(d).length === 1);
    const backdrop = d.querySelector("[data-slot-panel]");
    d.querySelector('[data-product-slots="1"]').click();
    await until(() => slotRows(d).length === 2);

    d.dispatchEvent(new d.defaultView.KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    T("Esc로도 닫힌다", backdrop.hidden === true);
  }

  /* ── 보기 기간 ── */
  {
    const { d } = await boot(responder());
    await until(() => rows(d).length === 1);
    d.querySelector('[data-product-slots="1"]').click();
    await until(() => slotRows(d).length === 2);

    /*
     * 픽스처의 이용일은 기본 기간(오늘부터 두 주) 밖이다. 그런데도 보여야 한다 —
     * 시즌이 몇 달 뒤인 상품은 열자마자 늘 0개가 떠서, 시간대가 없는 줄로 읽게 된다.
     */
    T("기본 기간에 하나도 없으면 전체를 보여준다", slotRows(d).length === 2);
    T("전체 중 몇 개를 보고 있는지 밝힌다",
      /전체 2개 중 2개/.test(d.querySelector("[data-slot-count]").textContent),
      d.querySelector("[data-slot-count]").textContent);

    /* 사람이 직접 좁힌 경우에는 넓히지 않는다. 고른 조건을 무시하면 더 헷갈린다. */
    const from = d.querySelector('[data-slot-view="from"]');
    const to = d.querySelector('[data-slot-view="to"]');
    from.value = "2030-01-01";
    to.value = "2030-01-31";
    from.dispatchEvent(new d.defaultView.Event("change", { bubbles: true }));
    T("직접 좁혀서 0개가 되면 그대로 둔다", slotRows(d).length === 0);
    T("기간을 넓히라고 알려준다",
      d.querySelector("[data-slot-empty]").textContent.includes("기간을 넓히"));

    d.querySelector("[data-slot-view-all]").click();
    T("전체 보기로 되돌릴 수 있다", slotRows(d).length === 2);
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed > 0) process.exit(1);
}

run().catch((error) => { console.error(error); process.exit(1); });
