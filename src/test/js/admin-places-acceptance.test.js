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

  /* ── 좌표: 필수가 아니지만, 비면 지도에서 조용히 빠진다 ── */
  {
    const markup = fs.readFileSync(HTML, "utf8");

    T("위도·경도가 선택 항목임을 화면이 밝힌다",
      /위도\s*<em>선택<\/em>/.test(markup) && /경도\s*<em>선택<\/em>/.test(markup));
    /* 등록한 사람이 "왜 지도에 안 나오지"로 헤매지 않게 결과를 미리 알린다. */
    T("좌표를 비웠을 때 무엇이 빠지는지 알려준다",
      markup.includes("지도에 표시되지 않고"));
    T("주소로 좌표를 찾는 버튼이 있다", markup.includes('id="placeGeocodeButton"'));
    /*
     * 키가 없으면 SDK를 부르지 않는다. 없는 키로 부르면 콘솔만 더러워진다.
     * th:if에 문자열을 그대로 쓰면 안 된다 — Thymeleaf는 "false"/"off"/"no"만 거짓으로 보므로
     * 빈 문자열이 참이 되어 조건이 무력화된다. 실제로 그렇게 새어 나간 적이 있다.
     */
    T("카카오 키가 없으면 지오코딩 스크립트를 부르지 않는다",
      markup.includes('th:if="${not #strings.isEmpty(kakaoJavascriptKey)}"'));
  }

  /* SDK가 없는 상태(키 미설정)를 흉내낸다. 눌러도 아무 일 없는 버튼은 두지 않는다. */
  T("카카오 SDK가 없으면 좌표 찾기 버튼을 숨긴다",
    d.getElementById("placeGeocodeButton").hidden === true);

  /* SDK가 있을 때 주소로 좌표를 채우는지. 카카오는 x가 경도, y가 위도다. */
  {
    let searched = null;
    w.kakao = {
      maps: {
        load: (callback) => callback(),
        services: {
          Status: { OK: "OK" },
          Geocoder: function () {
            this.addressSearch = (address, callback) => {
              searched = address;
              callback([{ x: "126.9425000", y: "33.4587000", address_name: "제주 서귀포시 성산읍" }], "OK");
            };
          }
        }
      }
    };

    d.getElementById("placeAddress").value = "제주 서귀포시 성산읍";
    /* bind()는 이미 지났으므로 동작 자체를 직접 부른다. */
    w.__adminPlaces.geocodeAddress();
    await until(() => d.getElementById("placeLatitude").value !== "");

    T("주소로 좌표를 찾아 위도·경도를 채운다",
      searched === "제주 서귀포시 성산읍"
        && d.getElementById("placeLatitude").value === "33.4587000"
        && d.getElementById("placeLongitude").value === "126.9425000");
    T("찾은 주소를 함께 알려준다",
      d.getElementById("placeGeocodeStatus").textContent.includes("성산읍"));
  }

  {
    /* 못 찾아도 등록을 막지 않는다. 좌표는 선택 항목이다. */
    w.kakao.maps.services.Geocoder = function () {
      this.addressSearch = (address, callback) => callback([], "ZERO_RESULT");
    };
    d.getElementById("placeAddress").value = "없는 주소";
    d.getElementById("placeLatitude").value = "";
    /* 좌표 조회는 저장과 무관하다. 저장 버튼 상태를 건드리지 않는지 앞뒤로 비교한다. */
    const saveDisabledBefore = d.getElementById("placeSaveButton").disabled;
    w.__adminPlaces.geocodeAddress();
    await until(() => d.getElementById("placeGeocodeStatus").classList.contains("error"));

    T("좌표를 못 찾으면 직접 입력하도록 안내한다",
      d.getElementById("placeGeocodeStatus").textContent.includes("직접 입력")
        && d.getElementById("placeLatitude").value === "");
    T("좌표 조회 실패가 저장 버튼 상태를 건드리지 않는다",
      d.getElementById("placeSaveButton").disabled === saveDisabledBefore);
    /* 실패해도 다시 시도할 수 있어야 한다. */
    T("좌표 조회 실패 후 다시 찾기를 누를 수 있다",
      d.getElementById("placeGeocodeButton").disabled === false);
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  process.exit(failed ? 1 : 0);
}
main().catch((error) => { console.error(error); process.exit(1); });
