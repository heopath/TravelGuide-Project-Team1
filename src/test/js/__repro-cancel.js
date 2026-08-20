/* 여행을 안 고른 상태에서 `내 예약`의 취소 버튼이 도는지 재현한다. */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = "C:/Users/GGG/Desktop/Workspace/TravelGuide-Project-Team1";
const HTML = path.join(ROOT, "src/main/resources/templates/booking/flights.html");
const SCRIPT = path.join(ROOT, "src/main/resources/static/js/pages/booking/flights.js");
const PAYMENT = path.join(ROOT, "src/main/resources/static/js/core/payment-methods.js");

const reservation = {
  reservationId: 30, reservationNumber: "AMT-TKT-A", tripId: null, status: "PENDING",
  productName: "부하테스트 입장권", optionName: "성인", usageDate: "2026-09-15",
  usageStartTime: "10:00:00", quantity: 1, totalAmount: 10000, currency: "KRW",
  expiresAt: new Date(Date.now() + 9 * 60 * 1000).toISOString()
};

const calls = [];

function json(body) {
  return { ok: true, status: 200, json: async () => body };
}

const wait = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/booking/flights?tab=mine",
    runScripts: "outside-only"
  });
  const w = dom.window;
  const d = w.document;
  Object.defineProperty(d, "visibilityState", { value: "visible", configurable: true });
  w.open = () => null;
  w.confirm = () => true;
  w.fetch = async (url, request = {}) => {
    calls.push(`${request.method || "GET"} ${url}`);
    if (String(url).startsWith("/api/v1/csrf")) {
      return json({ headerName: "X-CSRF-TOKEN", token: "t" });
    }
    if (String(url).startsWith("/api/v1/flights/search")) {
      return json({ success: true, data: { offers: [], meta: {} } });
    }
    if (String(url) === "/api/v1/ticket-reservations") {
      return json({ success: true, data: [reservation] });
    }
    if (/^\/api\/v1\/ticket-reservations\/\d+$/.test(String(url)) && request.method === "DELETE") {
      return json({ success: true, data: { reservationId: 30, status: "CANCELLED" } });
    }
    return json({ success: true, data: null });
  };

  w.eval(fs.readFileSync(PAYMENT, "utf8"));
  w.eval(fs.readFileSync(SCRIPT, "utf8"));
  if (d.readyState !== "loading") d.dispatchEvent(new w.Event("DOMContentLoaded"));

  for (let i = 0; i < 60 && d.body.dataset.pageReady !== "true"; i += 1) await wait(50);
  await wait(400);

  const cancel = d.querySelector("[data-mine-ticket-cancel]");
  const pay = d.querySelector("[data-mine-ticket-pay]");
  console.log("취소 버튼:", Boolean(cancel), "| 결제 버튼:", Boolean(pay));
  console.log("내 예약 내용:", d.getElementById("mineList").textContent.replace(/\s+/g, " ").trim().slice(0, 160));

  if (!cancel) {
    console.log("버튼 자체가 없음 — 호출 목록:", calls.join(" | "));
    process.exit(0);
  }

  cancel.click();
  await wait(600);
  console.log("DELETE 호출:", calls.some((c) => c.startsWith("DELETE")));
  console.log("전체 호출:", calls.join(" | "));
  process.exit(0);
})();
