const fs = require("fs");
const path = require("path");
const { JSDOM } = require("jsdom");

const ROOT = path.resolve(__dirname, "../..");
const HTML = path.join(ROOT, "main/resources/templates/admin/places.html");
const DASHBOARD = path.join(ROOT, "main/resources/templates/admin/admin.html");
const SCRIPT = path.join(ROOT, "main/resources/static/js/pages/admin/admin-places.js");
let passed = 0;
let failed = 0;
function T(name, ok) { if (ok) { passed++; console.log(`PASS ${name}`); } else { failed++; console.error(`FAIL ${name}`); } }
function response(data, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => data };
}
function until(predicate, timeout = 3000) { return new Promise((resolve, reject) => {
  const started = Date.now();
  const tick = () => predicate() ? resolve() : Date.now() - started > timeout
    ? reject(new Error("timeout")) : setTimeout(tick, 10);
  tick();
}); }

async function main() {
  const calls = [];
  let items = [
    { placeId: 1, category: "ATTRACTION", name: "성산일출봉", countryCode: "KR",
      region: "제주특별자치도", city: "서귀포시", address: "성산읍", latitude: 33.4587,
      longitude: 126.9425, description: "제주 대표 관광지", primaryImageUrl: "https://img.example/1.jpg", active: true },
    { placeId: 2, category: "CAFE", name: "숨긴 카페", countryCode: "KR",
      region: "제주특별자치도", city: "제주시", active: false }
  ];
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/admin/places", runScripts: "outside-only"
  });
  const w = dom.window;
  const d = w.document;
  w.fetch = async (url, options = {}) => {
    const method = options.method || "GET";
    calls.push({ method, url, body: options.body ? JSON.parse(options.body) : null });
    if (method === "GET") {
      return response({ success: true, data: { items, total: items.length, page: 0, size: 100, totalPages: 1 } });
    }
    if (method === "POST") {
      const created = { placeId: 3, ...JSON.parse(options.body) };
      items = [created, ...items];
      return response({ success: true, data: created });
    }
    if (method === "PUT") {
      const id = Number(url.split("/").pop());
      items = items.map((item) => item.placeId === id ? { ...item, ...JSON.parse(options.body) } : item);
      return response({ success: true, data: items.find((item) => item.placeId === id) });
    }
    if (method === "PATCH") {
      const id = Number(url.split("/").at(-2));
      items = items.map((item) => item.placeId === id ? { ...item, ...JSON.parse(options.body) } : item);
      return response({ success: true, data: items.find((item) => item.placeId === id) });
    }
    return response({ success: false }, 500);
  };

  w.eval(fs.readFileSync(SCRIPT, "utf8"));
  if (d.readyState !== "loading") d.dispatchEvent(new w.Event("DOMContentLoaded"));
  await until(() => d.querySelectorAll("[data-place-row]").length === 2);

  T("관리자 대시보드에서 추천 장소 관리 화면으로 이동할 수 있다",
    fs.readFileSync(DASHBOARD, "utf8").includes('data-route="/admin/places"'));
  T("공개 장소와 숨긴 장소를 모두 관리 목록에 표시한다",
    d.getElementById("placeList").textContent.includes("성산일출봉")
      && d.getElementById("placeList").textContent.includes("숨긴 카페")
      && d.getElementById("placeList").textContent.includes("숨김"));
  T("대표 이미지가 있으면 목록에 표시한다", d.querySelectorAll(".admin-place-thumb img").length === 1);

  d.querySelector('[data-place-edit="1"]').click();
  T("수정 버튼을 누르면 기존 장소 정보를 입력 폼에 채운다",
    d.getElementById("placeName").value === "성산일출봉"
      && d.getElementById("placeEditorTitle").textContent === "추천 장소 수정");
  d.getElementById("placeName").value = "성산일출봉 수정";
  d.getElementById("placeEditorForm").dispatchEvent(new w.Event("submit", { bubbles: true, cancelable: true }));
  await until(() => calls.some((call) => call.method === "PUT"));
  T("수정 저장은 관리자 장소 수정 API를 호출한다",
    calls.some((call) => call.method === "PUT" && call.url === "/api/v1/admin/places/1"
      && call.body.name === "성산일출봉 수정"));

  d.getElementById("newPlaceButton").click();
  d.getElementById("placeName").value = "새 추천 장소";
  d.getElementById("placeCategory").value = "ACTIVITY";
  d.getElementById("placeEditorForm").dispatchEvent(new w.Event("submit", { bubbles: true, cancelable: true }));
  await until(() => calls.some((call) => call.method === "POST"));
  T("새 장소 등록은 관리자 전용 등록 API를 호출한다",
    calls.some((call) => call.method === "POST" && call.url === "/api/v1/admin/places"
      && call.body.category === "ACTIVITY"));

  await until(() => d.querySelector('[data-place-visible="2"]'));
  d.querySelector('[data-place-visible="2"]').click();
  await until(() => calls.some((call) => call.method === "PATCH"));
  T("숨긴 장소를 삭제하지 않고 다시 공개할 수 있다",
    calls.some((call) => call.method === "PATCH"
      && call.url === "/api/v1/admin/places/2/visibility" && call.body.active === true));

  console.log(`\n${passed} passed, ${failed} failed`);
  process.exit(failed ? 1 : 0);
}
main().catch((error) => { console.error(error); process.exit(1); });
