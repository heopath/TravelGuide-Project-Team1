/* 티켓 상품 상세 수용 기준 (#281)
 *
 * 상품 하나를 통째로 보여주고 그 자리에서 예매까지 하는 화면이다. 예전에는 예약 화면의
 * 목록 안에서 시간대만 펼쳐 봤는데, 무엇을 사는지를 알 수 없었다.
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");
const { readMarkup } = require("./markup");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/booking/ticket.html");
const SCRIPT = path.join(ROOT, "src/main/resources/static/js/pages/booking/ticket-detail.js");

let passed = 0;
let failed = 0;
function T(name, condition, detail) {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name + (detail ? " — " + detail : "")); }
}
function until(predicate, timeoutMs = 3000) {
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

const QUEUE_TOKEN = "queue-token-1";

const slot = (id, date, start, price, option = "성인 입장권") => ({
  productId: 7, placeId: 3, productName: "제주 아쿠아리움 입장권", placeName: "아쿠아플라넷 제주",
  region: "제주특별자치도", city: "서귀포시", optionId: 1, optionName: option,
  slotId: id, usageDate: date, startTime: start, endTime: "18:00:00",
  unitPrice: price, currency: "KRW", maxQuantityPerUser: 4, remainingQuantity: 10,
  saleType: "NORMAL", saleState: "ON_SALE", opensAt: null
});

function product(overrides) {
  return Object.assign({
    productId: 7, productName: "제주 아쿠아리움 입장권", description: "돌고래를 볼 수 있어요.",
    placeId: 3, placeName: "아쿠아플라넷 제주", region: "제주특별자치도", city: "서귀포시",
    address: "제주특별자치도 서귀포시 성산읍 섭지코지로 95", category: "ATTRACTION",
    minUnitPrice: 20000, currency: "KRW",
    firstUsageDate: "2026-09-15", lastUsageDate: "2026-09-16",
    availableSlotCount: 3, remainingQuantity: 30,
    saleType: "NORMAL", saleState: "ON_SALE", opensAt: null
  }, overrides || {});
}

function json(body) {
  return { ok: true, status: 200, json: async () => body };
}

/**
 * 상세 화면을 띄운다.
 *
 * 주소의 마지막 칸이 상품 번호다 — 화면 스크립트가 거기서 꺼내 조회한다.
 */
function boot(options = {}) {
  const calls = [];
  const bodies = [];
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: `http://localhost/booking/tickets/${options.id ?? 7}${options.query || ""}`,
    runScripts: "outside-only"
  });
  const w = dom.window;
  const d = w.document;

  w.fetch = async (url, request = {}) => {
    calls.push(`${request.method || "GET"} ${url}`);
    if (request.body) bodies.push(JSON.parse(request.body));

    if (String(url).startsWith("/api/v1/tickets/products/")) {
      if (options.notFound) {
        return { ok: false, status: 404, json: async () => ({ success: false, message: "티켓을 찾을 수 없습니다." }) };
      }
      return json({ success: true, data: {
        product: options.product || product(),
        slots: options.slots || [
          slot(31, "2026-09-15", "10:00:00", 20000),
          slot(32, "2026-09-15", "14:00:00", 20000),
          slot(33, "2026-09-16", "10:00:00", 25000, "성인 야간권")
        ],
        serverTime: options.serverTime || new Date().toISOString()
      } });
    }
    if (String(url) === "/api/v1/booking-queue/entries") {
      return json({ success: true, data: { token: QUEUE_TOKEN, status: options.queueStatus || "READY" } });
    }
    if (String(url).endsWith("/reservation")) {
      return json({ success: true, data: {
        reservationId: 9, productName: "제주 아쿠아리움 입장권", totalAmount: 40000, status: "PENDING"
      } });
    }
    return json({ success: true, data: null });
  };

  w.eval(fs.readFileSync(SCRIPT, "utf8"));
  if (d.readyState !== "loading") d.dispatchEvent(new w.Event("DOMContentLoaded"));
  return { w, d, calls, bodies };
}

const text = (d, selector) => d.querySelector(selector)?.textContent?.replace(/\s+/g, " ").trim() || "";

async function main() {
  /* ── 마크업: 값이 박혀 있으면 연동이 빠진 것을 아무도 모른다 ── */
  {
    const markup = readMarkup(HTML);
    T("상품 값을 마크업에 박아두지 않았다",
      !markup.includes("해운대") && !markup.includes("16,000원") && !markup.includes("블루라인"));
    T("상품 정보 자리가 있다", markup.includes("data-ticket-facts"));
  }

  /* ── 상품 내용 ── */
  {
    const { d, calls } = boot();
    await until(() => !d.querySelector("[data-ticket-body]").hidden);

    T("주소의 상품 번호로 조회한다", calls.some((c) => c.includes("/api/v1/tickets/products/7")));
    T("상품명을 보여준다", text(d, "[data-ticket-name]") === "제주 아쿠아리움 입장권");
    /* 분류 코드를 그대로 노출하면 손님에게는 뜻 없는 영어다. */
    T("분류를 사람이 쓰는 말로 보여준다", text(d, "[data-ticket-category]") === "관광지");
    T("지역을 보여준다", text(d, "[data-ticket-region]").includes("제주"));
    T("이용 기간을 보여준다", text(d, "[data-ticket-facts]").includes("2026-09-15 ~ 2026-09-16"));
    T("옵션별 가격을 보여준다",
      text(d, "[data-ticket-facts]").includes("성인 입장권 20,000원")
        && text(d, "[data-ticket-facts]").includes("성인 야간권 25,000원"));
    T("1인 최대 매수를 보여준다", text(d, "[data-ticket-facts]").includes("4매"));
    T("상품 소개를 보여준다", text(d, "[data-ticket-description]").includes("돌고래"));
    /* 어디로 가야 하는지가 상세에 없으면 손님이 지도를 따로 찾아야 한다. */
    T("장소와 주소를 보여준다",
      text(d, "[data-ticket-venue-address]").includes("섭지코지로"));
  }

  /* ── 예매: 날짜 → 회차 → 매수 ── */
  {
    const { d, calls, bodies } = boot({ query: "?tripId=10" });
    await until(() => !d.querySelector("[data-ticket-body]").hidden);

    T("날짜를 고를 수 있다", d.querySelectorAll("[data-ticket-date]").length === 2);
    /* 빈 화면에서 시작하지 않도록 첫 날짜는 미리 골라 둔다. */
    T("첫 날짜를 미리 골라 둔다", d.querySelector("[data-ticket-date].on") !== null);
    T("고른 날짜의 회차만 보여준다", d.querySelectorAll("[data-ticket-slot]").length === 2);
    T("회차를 고르기 전에는 담을 수 없다",
      d.querySelector("[data-ticket-reserve]").disabled === true);

    d.querySelectorAll("[data-ticket-slot]")[1].click();
    T("회차를 고르면 매수를 정할 수 있다",
      d.querySelector("[data-ticket-quantity-field]").hidden === false);
    T("회차를 고르면 담을 수 있다", d.querySelector("[data-ticket-reserve]").disabled === false);
    T("고른 회차 금액이 합계에 반영된다", text(d, "[data-ticket-total]") === "20,000원");

    d.querySelector("[data-ticket-qty-up]").click();
    T("매수를 늘리면 합계가 따라 오른다", text(d, "[data-ticket-total]") === "40,000원");

    /* 남은 수량과 1인 최대를 넘겨 담지 못하게 한다. */
    for (let i = 0; i < 8; i += 1) d.querySelector("[data-ticket-qty-up]").click();
    T("1인 최대 매수를 넘기지 않는다", d.querySelector("[data-ticket-quantity]").value === "4");

    d.querySelector("[data-ticket-reserve]").click();
    await until(() => calls.includes("POST /api/v1/booking-queue/entries"));
    await until(() => calls.some((c) => c.includes(`/entries/${QUEUE_TOKEN}/reservation`)));

    T("예매는 대기열을 거친다", calls.includes("POST /api/v1/booking-queue/entries"));
    T("고른 회차와 매수를 그대로 보낸다",
      bodies.some((b) => b.slotId === 32 && b.quantity === 4));
    /* 여행을 고른 채로 들어왔으면 그 여행에 붙인다. (#255) */
    T("여행을 이어받으면 tripId를 함께 보낸다", bodies.some((b) => b.tripId === 10));
    T("담은 뒤 결제할 곳을 알려준다",
      text(d, "[data-ticket-state]").includes("마이페이지"));
  }

  /* ── 여행 없이 ── */
  {
    const { d, bodies } = boot();
    await until(() => !d.querySelector("[data-ticket-body]").hidden);
    d.querySelector("[data-ticket-slot]").click();
    d.querySelector("[data-ticket-reserve]").click();
    await until(() => bodies.some((b) => b.slotId === 31));

    /* 티켓은 여행 계획과 무관하게 팔린다. tripId를 억지로 채우지 않는다. (#255) */
    T("여행이 없으면 tripId를 아예 싣지 않는다",
      bodies.some((b) => b.slotId === 31 && !("tripId" in b)));
  }

  /* ── 오픈 예정 (#256) ── */
  {
    const opensAt = new Date(Date.now() + 90 * 1000).toISOString();
    const { d } = boot({
      product: product({ saleType: "SCHEDULED", saleState: "SCHEDULED", opensAt }),
      slots: [slot(41, "2026-09-20", "19:00:00", 30000)]
    });
    await until(() => !d.querySelector("[data-ticket-body]").hidden);

    T("오픈 예정이면 배지로 알린다", text(d, "[data-ticket-sale-badge]") === "오픈 예정");
    T("오픈까지 남은 시간을 보여준다", /분 \d+초 뒤에 열려요/.test(text(d, "[data-ticket-book-lead]")),
      text(d, "[data-ticket-book-lead]"));
    /* 조회에서만 막으면 눌러 보고서야 안 된다는 걸 안다. 버튼부터 잠근다. */
    T("오픈 전에는 담을 수 없다",
      d.querySelector("[data-ticket-reserve]").disabled === true
        && text(d, "[data-ticket-reserve]").includes("오픈 전"));
  }

  /* ── 판매 종료 ── */
  {
    const { d } = boot({ product: product({ saleState: "ENDED" }) });
    await until(() => !d.querySelector("[data-ticket-body]").hidden);

    T("판매가 끝났으면 그렇게 알린다", text(d, "[data-ticket-sale-badge]") === "판매 종료");
    T("판매가 끝나면 담을 수 없다", d.querySelector("[data-ticket-reserve]").disabled === true);
  }

  /* ── 없는 상품 ── */
  {
    const { d } = boot({ notFound: true });
    await until(() => text(d, "[data-ticket-state]").includes("티켓"));

    T("없는 상품은 이유를 알린다", d.querySelector("[data-ticket-state]").hidden === false);
    T("없는 상품에는 예매 자리를 두지 않는다",
      d.querySelector("[data-ticket-body]").hidden === true);
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  process.exit(failed ? 1 : 0);
}

main().catch((error) => { console.error(error); process.exit(1); });
