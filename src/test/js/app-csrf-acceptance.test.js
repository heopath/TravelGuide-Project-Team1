/* 전 화면 공용 app.js의 CSRF 재시도 래퍼(installCsrfAwareFetch) 수용 기준.
 *
 * 이슈 #158에서 지적된 두 재시도 사각지대(Request 입력 body 소모, 토큰 발급
 * 실패 고착)와 그 후속 리뷰에서 지적된 Request 헤더 유실 문제의 회귀 테스트다.
 * 실행: src/test/js 에서 `npm install` 후 `npm test`
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const APP_JS = path.join(ROOT, "src/main/resources/static/js/app.js");

let passed = 0;
let failed = 0;
function test(name, condition) {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name); }
}

function jsonResponse(body, init) {
  return new Response(JSON.stringify(body), Object.assign({ headers: { "Content-Type": "application/json" } }, init || {}));
}

/**
 * @param handler (url, input, init) => Response|Promise<Response> — "/api/v1/csrf"를 뺀 나머지 호출을 처리한다.
 * @param csrfHandler (callCount) => Response|Promise<Response>|Error — 없으면 항상 토큰 "test-token" 발급.
 */
function boot({ handler, csrfHandler } = {}) {
  const dom = new JSDOM("<!doctype html><html><body></body></html>", {
    url: "http://localhost/trips/1/record",
    runScripts: "outside-only",
  });
  const w = dom.window;
  w.Request = global.Request;
  w.Response = global.Response;
  w.Headers = global.Headers;

  let csrfCalls = 0;
  const calls = [];
  w.fetch = async function (input, init) {
    const url = typeof input === "string" ? input : input.url;
    if (url.includes("/api/v1/csrf")) {
      csrfCalls++;
      if (csrfHandler) {
        const result = csrfHandler(csrfCalls);
        if (result instanceof Error) throw result;
        return result;
      }
      return jsonResponse({ headerName: "X-CSRF-TOKEN", token: "test-token" });
    }
    calls.push({ input, init });
    return handler ? handler(url, input, init) : jsonResponse({ ok: true }, { status: 200 });
  };

  w.eval(fs.readFileSync(APP_JS, "utf8"));
  return { w, calls, csrfCallCount: () => csrfCalls };
}

async function testGetBypassesCsrf() {
  const { w, csrfCallCount } = boot();
  const response = await w.fetch("/api/v1/trips/1");
  test("GET 요청은 CSRF 래퍼를 건너뛴다", response.status === 200 && csrfCallCount() === 0);
}

async function testPostAttachesCsrfHeader() {
  const { w, calls } = boot();
  await w.fetch("/api/v1/travel-records", { method: "POST", body: "{}" });
  const sent = calls[0];
  test("동일 출처 POST는 X-CSRF-TOKEN 헤더를 붙인다", sent.init.headers.get("X-CSRF-TOKEN") === "test-token");
}

async function testAccessDeniedRetriesOnceThenGivesUp() {
  let targetCalls = 0;
  const { w, csrfCallCount } = boot({
    handler: function () {
      targetCalls++;
      return jsonResponse({ code: "ACCESS_DENIED" }, { status: 403 });
    },
  });
  const response = await w.fetch("/api/v1/travel-records", { method: "POST", body: "{}" });
  test("ACCESS_DENIED면 새 토큰으로 딱 한 번만 재시도한다", targetCalls === 2 && csrfCallCount() === 2);
  test("재시도 후에도 403이면 그대로 호출자에게 돌려준다(무한 재시도 방지)", response.status === 403);
}

async function testForbiddenIsNotRetried() {
  let targetCalls = 0;
  const { w, csrfCallCount } = boot({
    handler: function () {
      targetCalls++;
      return jsonResponse({ code: "FORBIDDEN" }, { status: 403 });
    },
  });
  const response = await w.fetch("/api/v1/travel-records", { method: "POST", body: "{}" });
  test("권한 부족(FORBIDDEN)은 CSRF 문제가 아니므로 재시도하지 않는다", targetCalls === 1 && csrfCallCount() === 1);
  test("FORBIDDEN 응답을 그대로 호출자에게 돌려준다", response.status === 403);
}

/* 이슈 #158-1: 토큰 발급 자체가 한 번 실패하면 그 뒤로 계속 막히던 문제 */
async function testCsrfFetchFailureDoesNotStickCache() {
  const { w, csrfCallCount } = boot({
    csrfHandler: function (callCount) {
      if (callCount === 1) throw new TypeError("network error (simulated)");
      return jsonResponse({ headerName: "X-CSRF-TOKEN", token: "tok-" + callCount });
    },
  });

  let firstError = null;
  try {
    await w.fetch("/api/v1/travel-records", { method: "POST", body: "{}" });
  } catch (error) {
    firstError = error;
  }
  test("토큰 발급 실패는 호출자에게 에러로 전달된다", firstError !== null);

  let secondResponse = null;
  try {
    secondResponse = await w.fetch("/api/v1/travel-records", { method: "POST", body: "{}" });
  } catch (error) {
    secondResponse = null;
  }
  test("실패 후 다음 요청은 캐시가 비워져 새로 토큰을 받아 성공한다(수정 전이면 계속 실패)", !!secondResponse && secondResponse.status === 200);
  test("두 번째 요청에서 /api/v1/csrf를 다시 호출했다(재시도 증거)", csrfCallCount() === 2);
}

/* 이슈 #158-2: Request 객체 입력이면 첫 전송에서 body가 소모돼 재시도가 예외로 깨지던 문제 */
async function testRequestInputRetrySurvivesBodyConsumption() {
  let targetCalls = 0;
  const { w } = boot({
    handler: async function (url, input) {
      // 실제 브라우저 fetch처럼 Request 입력의 body를 실제로 소모한다(clone 없이 재사용하면 여기서 예외).
      if (input instanceof Request) await input.text();
      targetCalls++;
      if (targetCalls === 1) return jsonResponse({ code: "ACCESS_DENIED" }, { status: 403 });
      return jsonResponse({ ok: true }, { status: 200 });
    },
  });

  const request = new Request("http://localhost/api/v1/travel-records", {
    method: "POST",
    body: JSON.stringify({ title: "t" }),
  });

  let threw = null;
  let response = null;
  try {
    response = await w.fetch(request);
  } catch (error) {
    threw = error;
  }

  test("Request 객체 입력은 재시도 후에도 'body already consumed' 없이 성공한다(수정 전이면 예외)", threw === null);
  test("재시도가 실제로 두 번째 응답(200)을 받았다", !!response && response.status === 200);
}

/* 후속 리뷰 지적: Request 객체의 기존 헤더(Content-Type 등)가 재시도 과정에서 사라지면 안 된다 */
async function testRequestInputHeadersArePreserved() {
  let capturedHeaders = null;
  const { w } = boot({
    handler: async function (url, input, init) {
      if (input instanceof Request) await input.text();
      capturedHeaders = init.headers;
      return jsonResponse({ ok: true }, { status: 200 });
    },
  });

  const request = new Request("http://localhost/api/v1/travel-records", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Custom-Header": "abc" },
    body: JSON.stringify({ title: "t" }),
  });

  await w.fetch(request);

  test("Request 자체에 있던 Content-Type이 options 없이도 유지된다", capturedHeaders.get("Content-Type") === "application/json");
  test("Request 자체에 있던 커스텀 헤더도 유지된다", capturedHeaders.get("X-Custom-Header") === "abc");
  test("CSRF 헤더는 그 위에 추가된다", capturedHeaders.get("X-CSRF-TOKEN") === "test-token");
}

async function testOptionsHeadersOverrideRequestHeaders() {
  let capturedHeaders = null;
  const { w } = boot({
    handler: async function (url, input, init) {
      if (input instanceof Request) await input.text();
      capturedHeaders = init.headers;
      return jsonResponse({ ok: true }, { status: 200 });
    },
  });

  const request = new Request("http://localhost/api/v1/travel-records", {
    method: "POST",
    headers: { "X-Custom-Header": "from-request" },
    body: "{}",
  });

  await w.fetch(request, { headers: { "X-Custom-Header": "from-options" } });

  test("options.headers가 있으면 Request 자체 헤더보다 우선한다", capturedHeaders.get("X-Custom-Header") === "from-options");
}

(async () => {
  await testGetBypassesCsrf();
  await testPostAttachesCsrfHeader();
  await testAccessDeniedRetriesOnceThenGivesUp();
  await testForbiddenIsNotRetried();
  await testCsrfFetchFailureDoesNotStickCache();
  await testRequestInputRetrySurvivesBodyConsumption();
  await testRequestInputHeadersArePreserved();
  await testOptionsHeadersOverrideRequestHeaders();

  console.log("\n" + passed + " passed, " + failed + " failed");
  process.exit(failed === 0 ? 0 : 1);
})();
