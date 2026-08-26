/* 관리자 API 키 관리 수용 기준 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/admin/admin.html");
const JS = path.join(ROOT, "src/main/resources/static/js/pages/admin/admin-apikeys.js");

let passed = 0;
let failed = 0;
const T = (name, condition, detail) => {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name + (detail ? " — " + detail : "")); }
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

const json = (data) => ({ ok: true, status: 200, json: async () => ({ success: true, data }) });

const listPayload = (encryptionReady) => ({
  encryptionReady: encryptionReady !== false,
  keys: [
    {
      name: "OPENAI", label: "OpenAI", description: "AI 여행 추천에 사용합니다.",
      maskedValue: "sk-••••••••cdef", source: "STORED",
      updatedAt: "2026-08-20T09:10:00+09:00", updatedBy: 1
    },
    {
      name: "KAKAO_REST", label: "카카오 REST", description: "장소 검색에 사용합니다.",
      maskedValue: "a1b••••••••9f31", source: "ENV", updatedAt: null, updatedBy: null
    }
  ]
});

async function boot(responder) {
  const calls = [];
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/admin?panel=apikeys", runScripts: "outside-only",
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
  await until(() => d.querySelectorAll("[data-apikey-card]").length > 0);
  return { w, d, calls };
}

const card = (d, name) => d.querySelector(`[data-apikey-card="${name}"]`);
const fire = (d, element, type) =>
  element.dispatchEvent(new d.defaultView.Event(type, { bubbles: true, cancelable: true }));

async function run() {
  {
    const markup = fs.readFileSync(HTML, "utf8");
    const script = fs.readFileSync(JS, "utf8");
    T("사이드바에 API 키 관리 메뉴가 있다", markup.includes('data-admin-panel="apikeys"'));
    /* 메뉴와 패널의 이름이 같아야 admin.js의 openPanel이 화면을 찾아 연다. */
    T("메뉴가 여는 패널이 실제로 있다", markup.includes('data-admin-section="apikeys"'));
    T("API 키 패널이 실연동이다",
      /data-admin-section="apikeys"[\s\S]*?data-admin-state="apikeys">실연동/.test(markup));
    T("페이지 스크립트를 실제로 불러온다", markup.includes("/js/pages/admin/admin-apikeys.js"));
    T("401 로그인 복귀 주소에 키 패널 쿼리까지 인코딩한다",
      script.includes('encodeURIComponent("/admin?panel=apikeys")'));
    /* 관리 대상 목록은 서버(ManagedApiKey) 한 곳에서만 정한다. 화면에도 두면 둘이 어긋난다. */
    T("화면이 키 이름을 하드코딩하지 않는다",
      !script.includes('"OPENAI"') && !script.includes('"KAKAO_REST"'));
  }

  {
    const { d, calls } = await boot(() => json(listPayload(true)));

    T("진입 시 키 목록을 조회한다", calls[0].url === "/api/v1/admin/api-keys");
    T("관리 대상 키를 모두 카드로 보여준다",
      d.querySelectorAll("[data-apikey-card]").length === 2);
    T("현재 키는 마스킹된 값만 보여준다",
      card(d, "OPENAI").querySelector(".admin-apikey-current strong").textContent
        === "sk-••••••••cdef");
    T("입력란은 화면에 값이 드러나지 않는 형식이다",
      card(d, "OPENAI").querySelector("[data-apikey-input]").type === "password");
    T("저장값과 환경변수 사용을 구분해 표시한다",
      card(d, "OPENAI").querySelector(".admin-apikey-source").classList.contains("stored")
        && card(d, "KAKAO_REST").querySelector(".admin-apikey-source").classList.contains("env"));
    T("되돌리기는 저장값이 있는 키에만 둔다",
      card(d, "OPENAI").querySelector("[data-apikey-reset]") !== null
        && card(d, "KAKAO_REST").querySelector("[data-apikey-reset]") === null);
    T("암호화가 준비되면 경고를 감춘다",
      d.querySelector("[data-apikey-warning]").hidden === true);
  }

  {
    /* 이 기능의 핵심. 확인하지 않은 키는 저장할 수 없어야 한다. */
    const { d, calls } = await boot((url, options) => {
      if (url.endsWith("/test")) {
        return json({ valid: true, statusCode: 200, message: "정상 응답을 받았습니다." });
      }
      return json(listPayload(true));
    });

    const target = card(d, "OPENAI");
    const input = target.querySelector("[data-apikey-input]");
    const save = target.querySelector("[data-apikey-save]");

    T("연결 테스트 전에는 저장할 수 없다", save.disabled === true);

    input.value = "sk-new-key-value";
    fire(d, input, "input");
    target.querySelector("[data-apikey-test]").click();
    await until(() => save.disabled === false);

    const tested = calls.find((call) => call.url.endsWith("/test"));
    T("연결 테스트는 입력한 키를 본문에 담아 POST한다",
      tested.options.method === "POST"
        && JSON.parse(tested.options.body).apiKey === "sk-new-key-value");
    T("확인에 성공하면 저장 버튼이 열린다", save.disabled === false);

    input.value = "sk-typo";
    fire(d, input, "input");
    T("입력이 바뀌면 저장 버튼을 다시 잠근다", save.disabled === true);
  }

  {
    const { d, calls } = await boot((url, options) => {
      if (url.endsWith("/test")) {
        return json({ valid: false, statusCode: 401, message: "키가 거부되었습니다." });
      }
      return json(listPayload(true));
    });

    const target = card(d, "OPENAI");
    target.querySelector("[data-apikey-input]").value = "sk-wrong";
    fire(d, target.querySelector("[data-apikey-input]"), "input");
    target.querySelector("[data-apikey-test]").click();
    await until(() => target.querySelector("[data-apikey-message]").textContent.length > 0
      && !target.querySelector("[data-apikey-message]").textContent.includes("확인하는 중"));

    T("확인에 실패하면 저장 버튼이 잠긴 채로 둔다",
      target.querySelector("[data-apikey-save]").disabled === true);
    T("실패 사유를 그대로 관리자에게 보여준다",
      target.querySelector("[data-apikey-message]").textContent.includes("거부"));
  }

  {
    const { d, calls } = await boot((url, options) => {
      if (url.endsWith("/test")) return json({ valid: true, statusCode: 200, message: "정상" });
      if (options.method === "PUT") return json(listPayload(true).keys[0]);
      return json(listPayload(true));
    });

    const target = card(d, "OPENAI");
    const input = target.querySelector("[data-apikey-input]");
    input.value = "sk-new-key-value";
    fire(d, input, "input");
    target.querySelector("[data-apikey-test]").click();
    await until(() => target.querySelector("[data-apikey-save]").disabled === false);

    fire(d, target.querySelector("[data-apikey-form]"), "submit");
    await until(() => calls.some((call) => call.options.method === "PUT"));

    const put = calls.find((call) => call.options.method === "PUT");
    T("저장은 키 이름을 경로에 담아 PUT한다", put.url === "/api/v1/admin/api-keys/OPENAI");
    T("확인을 통과한 값을 그대로 저장한다",
      JSON.parse(put.options.body).apiKey === "sk-new-key-value");

    await until(() => calls.filter((call) => call.url === "/api/v1/admin/api-keys").length === 2);
    T("저장 뒤 목록을 다시 읽어 화면을 맞춘다",
      calls.filter((call) => call.url === "/api/v1/admin/api-keys").length === 2);
  }

  {
    const { d } = await boot((url) => {
      if (url.endsWith("/test")) return json({ valid: true, statusCode: 200, message: "정상" });
      return json(listPayload(false));
    });

    T("암호화 설정이 없으면 이유를 화면에 띄운다",
      d.querySelector("[data-apikey-warning]").hidden === false);

    const target = card(d, "OPENAI");
    target.querySelector("[data-apikey-input]").value = "sk-new-key-value";
    fire(d, target.querySelector("[data-apikey-input]"), "input");
    target.querySelector("[data-apikey-test]").click();
    await until(() => target.querySelector("[data-apikey-message]").textContent.includes("저장할 수 있어요"));

    /* 눌러 본 뒤 서버가 거절하면 관리자는 자기 입력이 잘못된 줄 안다. 미리 잠근다. */
    T("암호화 설정이 없으면 확인에 성공해도 저장을 막는다",
      target.querySelector("[data-apikey-save]").disabled === true);
  }

  {
    const { d, calls } = await boot((url, options) => {
      if (options.method === "DELETE") return json(listPayload(true).keys[1]);
      return json(listPayload(true));
    });

    d.defaultView.confirm = () => true;
    card(d, "OPENAI").querySelector("[data-apikey-reset]").click();
    await until(() => calls.some((call) => call.options.method === "DELETE"));

    const removed = calls.find((call) => call.options.method === "DELETE");
    T("되돌리기는 저장값 삭제 요청을 보낸다", removed.url === "/api/v1/admin/api-keys/OPENAI");
  }

  {
    const { d, calls } = await boot((url, options) => {
      if (url.endsWith("/test")) return json({ valid: true, statusCode: 200, message: "정상" });
      return json(listPayload(true));
    });

    card(d, "OPENAI").querySelector("[data-apikey-check]").click();
    await until(() => calls.some((call) => call.url.endsWith("/test")));

    /* 값을 비워 보내면 서버가 지금 쓰이는 키로 확인한다. */
    const checked = calls.find((call) => call.url.endsWith("/test"));
    T("지금 키 상태 확인은 입력값 없이 요청한다",
      JSON.parse(checked.options.body).apiKey === "");
    T("지금 키 확인만으로는 저장 버튼이 열리지 않는다",
      card(d, "OPENAI").querySelector("[data-apikey-save]").disabled === true);
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed > 0) process.exit(1);
}

run().catch((error) => { console.error(error); process.exit(1); });
