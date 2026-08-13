const fs = require("fs");
const path = require("path");
const { JSDOM } = require("jsdom");

const ROOT = path.resolve(__dirname, "../..");
const HTML = path.join(ROOT, "main/resources/templates/booking/queue.html");
const SCRIPT = path.join(ROOT, "main/resources/static/js/pages/booking/queue.js");
const TOKEN = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
let passed = 0;
let failed = 0;
function T(name, ok) { if (ok) { passed++; console.log(`PASS ${name}`); } else { failed++; console.error(`FAIL ${name}`); } }
function json(data, ok = true) { return { ok, json: async () => data }; }
function until(predicate, timeout = 3000) { return new Promise((resolve, reject) => {
  const started = Date.now();
  const tick = () => predicate() ? resolve() : Date.now() - started > timeout ? reject(new Error("timeout")) : setTimeout(tick, 10);
  tick();
}); }

async function main() {
  const calls = [];
  let statusCalls = 0;
  let destination = null;
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: `http://localhost/booking/queue?token=${TOKEN}`, runScripts: "outside-only"
  });
  const w = dom.window;
  const d = w.document;
  w.__bookingQueueNavigate = (url) => { destination = url; };
  w.fetch = async (url, options = {}) => {
    calls.push(`${options.method || "GET"} ${url}`);
    if (url.endsWith("/reservation")) return json({ success: true, data: {
      reservationId: 91, tripId: 10, status: "PENDING"
    } });
    if (url.includes("/booking-queue/entries/")) {
      statusCalls++;
      return json({ success: true, data: statusCalls === 1 ? {
        token: TOKEN, status: "WAITING", slotId: 31, tripId: 10,
        position: 8, ahead: 7, estimatedWaitSeconds: 4, progressPercent: 35,
        expiresAt: new Date(Date.now() + 600000).toISOString()
      } : {
        token: TOKEN, status: "READY", slotId: 31, tripId: 10,
        position: 0, ahead: 0, estimatedWaitSeconds: 0, progressPercent: 100,
        expiresAt: new Date(Date.now() + 120000).toISOString()
      } });
    }
    return json({ success: false, message: "unexpected" }, false);
  };

  w.eval(fs.readFileSync(SCRIPT, "utf8"));
  d.dispatchEvent(new w.Event("DOMContentLoaded"));
  await until(() => statusCalls === 1 && d.getElementById("queuePosition").textContent === "8");

  T("서버가 준 실제 대기 순번을 표시한다", d.getElementById("queuePosition").textContent === "8");
  T("예상 대기 시간과 진행률을 표시한다",
    d.getElementById("queueEstimate").textContent.includes("4초")
      && d.getElementById("queueProgress").style.width === "35%");
  T("순번 만료까지 남은 시간을 표시한다", d.getElementById("queueExpiry").textContent.includes("순번 유지 시간"));

  await w.__bookingQueue.refresh();
  await until(() => destination !== null);
  T("차례가 되면 저장된 요청을 서버에서 예약 완료한다",
    calls.includes(`POST /api/v1/booking-queue/entries/${TOKEN}/reservation`));
  T("완료된 예약의 여행 티켓 탭으로 돌아간다",
    destination === "/booking/flights?tab=ticket&tripId=10");

  console.log(`\n${passed} passed, ${failed} failed`);
  process.exit(failed ? 1 : 0);
}
main().catch((error) => { console.error(error); process.exit(1); });
