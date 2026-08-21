/* 전역 로딩 화면 수용 기준
 *
 * 로딩 화면은 화면 전체를 덮는다. 그래서 "안 뜨는 것"보다 "안 꺼지는 것"이 훨씬 나쁘다.
 * 꺼지지 않으면 손님은 아무것도 못 하고, 새로고침 말고는 빠져나갈 길이 없다.
 *
 * 실행: src/test/js 에서 `npm test`
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const JS = path.join(ROOT, "src/main/resources/static/js/core/loading.js");

let passed = 0;
let failed = 0;
const T = (name, condition, detail) => {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name + (detail ? " — " + detail : "")); }
};

const tick = () => new Promise((resolve) => setTimeout(resolve, 5));

/**
 * 로더를 올린 빈 문서를 만든다. fetch는 시험용으로 갈아 끼운다.
 *
 * 문서를 만든 직후에는 손대지 않는다. jsdom이 로드 직후 pageshow를 쏘는데, 로더는
 * 거기서 스스로를 끈다. 그 사이에 제출하면 무엇 때문에 꺼졌는지 알 수 없어 검사가
 * 흔들린다. 실제로도 손님은 화면이 다 뜬 뒤에 폼을 누른다.
 */
async function boot(fetchImpl) {
  const dom = new JSDOM("<!doctype html><html><body></body></html>",
    { url: "http://localhost/admin", runScripts: "outside-only" });
  const w = dom.window;
  w.fetch = fetchImpl || (async () => ({ ok: true, status: 200, json: async () => ({}) }));
  w.eval(fs.readFileSync(JS, "utf8"));

  const loader = w.document.querySelector(".travel-loader");
  await tick();
  return { w, d: w.document, loader, active: () => loader.classList.contains("is-active") };
}

/** 폼 하나를 만들어 제출한다. handler를 주면 그 폼의 제출 처리로 붙인다. */
function submitForm(w, options) {
  const form = w.document.createElement("form");
  if (options && options.optOut) form.setAttribute("data-no-global-loading", "");
  if (options && options.handler) form.addEventListener("submit", options.handler);
  w.document.body.appendChild(form);
  form.dispatchEvent(new w.Event("submit", { bubbles: true, cancelable: true }));
  return form;
}

async function run() {
  /* ── 이번에 고친 것 ── */
  {
    /*
     * 관리자 상품·옵션·시간대 등록에서 로딩 화면이 뜬 채로 멈췄다. 폼이 preventDefault를
     * 부르고 allMyTripsLoading:false로 요청하면, fetch 감싸기를 건너뛰어 끌 사람이
     * 아무도 없었다.
     */
    const { w, active } = await boot();
    submitForm(w, { handler: (event) => event.preventDefault() });
    T("JS가 처리하는 폼은 제출 순간 로딩이 뜬다", active());

    await tick();
    T("그 제출이 끝나면 로딩이 꺼진다", !active());
  }
  {
    /* 같은 상황에서 요청까지 태워 본다. 이게 관리자 화면의 실제 흐름이다. */
    const seen = [];
    const { w, active } = await boot(async (url, request) => {
      seen.push(request && request.allMyTripsLoading);
      return { ok: true, status: 200, json: async () => ({ success: true }) };
    });
    submitForm(w, {
      handler: (event) => {
        event.preventDefault();
        w.fetch("/api/v1/admin/products", { method: "POST", allMyTripsLoading: false });
      },
    });
    await tick();

    T("로딩을 끄지 않는 요청을 태워도 로딩이 꺼진다", !active());
    T("그 요청은 로딩 추적에서 빠져 있다", seen[0] === false);
  }

  /* ── 원래 동작은 그대로여야 한다 ── */
  {
    /* 화면이 진짜 넘어가는 제출은 로딩이 남아야 한다. 여기서 끄면 흰 화면만 보인다. */
    const { w, active } = await boot();
    submitForm(w, {});
    await tick();
    T("화면이 넘어가는 제출은 로딩을 남긴다", active());
  }
  {
    const { w, active } = await boot();
    submitForm(w, { optOut: true, handler: (event) => event.preventDefault() });
    T("data-no-global-loading 폼은 아예 안 띄운다", !active());
  }
  {
    /*
     * 추적 중인 요청이 도는 사이에 끄면, 답이 오기 전에 로딩이 사라져 눌렸는지 알 수 없다.
     * 그쪽은 끝날 때 알아서 끄므로 손대지 않는다.
     */
    let settle;
    const pending = new Promise((resolve) => { settle = resolve; });
    const { w, active } = await boot(() => pending);

    submitForm(w, {
      handler: (event) => {
        event.preventDefault();
        w.fetch("/api/v1/admin/products", { method: "POST" });
      },
    });
    await tick();
    T("답을 기다리는 중에는 로딩을 끄지 않는다", active());

    settle({ ok: true, status: 200, json: async () => ({}) });
  }

  console.log("\n" + passed + " passed, " + failed + " failed");
  if (failed > 0) process.exit(1);
}

run().catch((error) => { console.error(error); process.exit(1); });
