/* 비밀번호 찾기 수용 기준
 *
 * 지켜야 할 것이 넷이다.
 *
 *   1. 로그인 화면에서 비밀번호 찾기로 갈 길이 있어야 한다. 없으면 비밀번호를 잊은
 *      손님은 새 계정을 파는 수밖에 없다.
 *   2. 재설정 화면은 열자마자 링크가 아직 쓸 수 있는지 확인한다. 새 비밀번호를 두 번
 *      입력한 뒤에 "만료된 링크입니다"를 보면 헛수고다.
 *   3. 두 비밀번호가 다르거나 8자가 안 되면 서버를 부르기 전에 화면에서 막는다.
 *   4. 없는 이메일로 요청해도 화면은 똑같이 "보냈습니다"라고 답한다. 여기서 답이
 *      갈리면 이메일만 넣어 보며 누가 가입했는지 알아낼 수 있다.
 *
 * 오류 자리가 늘 보이던 문제(reset.css의 [hidden])도 여기서 함께 잡아 둔다.
 *
 * 실행: src/test/js 에서 `npm test`
 */
const { JSDOM, VirtualConsole } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const TEMPLATES = path.join(ROOT, "src/main/resources/templates/auth");
const SCRIPTS = path.join(ROOT, "src/main/resources/static/js/pages/auth");
const RESET_CSS = path.join(ROOT, "src/main/resources/static/css/common/reset.css");

let passed = 0;
let failed = 0;
const T = (name, condition, detail) => {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name + (detail ? " — " + detail : "")); }
};

const read = (file) => fs.readFileSync(file, "utf8");

function quietConsole() {
  const virtualConsole = new VirtualConsole();
  virtualConsole.on("jsdomError", () => {});
  return virtualConsole;
}

/* 서버가 내려줄 JSON을 흉내 내는 fetch. 부른 주소와 본문을 기록해 둔다. */
function fakeFetch(routes, calls) {
  return async function (url, options) {
    const path = String(url).split("?")[0];
    calls.push({ url: String(url), method: (options && options.method) || "GET",
                 body: options && options.body ? JSON.parse(options.body) : null });
    const reply = routes[path] || { ok: true, payload: { success: true, message: "ok" } };
    return {
      ok: reply.ok,
      headers: { get: () => "application/json" },
      json: async () => reply.payload
    };
  };
}

/* 템플릿의 <body> 안쪽만 떼어 온다. Thymeleaf 조각(th:replace)은 브라우저에서
   서버가 채우므로 여기서는 빈 껍데기로 남는다 — 검사에는 지장이 없다. */
function bodyOf(html) {
  return html.slice(html.indexOf("<body"), html.indexOf("</body>") + 7);
}

async function openPage(template, script, options) {
  const opts = options || {};
  const dom = new JSDOM(
    "<!doctype html><html>" + bodyOf(read(path.join(TEMPLATES, template))) + "</html>",
    {
      url: "http://localhost:8080" + (opts.search || ""),
      runScripts: "outside-only",
      /* 성공하면 화면이 /auth/login으로 옮겨 간다. jsdom은 이동을 못 하고 오류를 찍는데,
         검사와 무관한 잡음이라 삼킨다. */
      virtualConsole: quietConsole()
    }
  );
  const calls = [];
  dom.window.fetch = fakeFetch(opts.routes || {}, calls);
  dom.window.eval(read(path.join(SCRIPTS, script)));
  /* jsdom이 자기 DOMContentLoaded를 곧 쏜다. 여기서 한 번 더 쏘면 화면 스크립트가
     두 번 붙어 서버를 두 번 부른다 — 기다리기만 한다. */
  await new Promise((resolve) => setTimeout(resolve, 0));
  return { window: dom.window, document: dom.window.document, calls: calls };
}

const submit = async (page, formId) => {
  const form = page.document.querySelector(formId);
  form.dispatchEvent(new page.window.Event("submit", { bubbles: true, cancelable: true }));
  await new Promise((resolve) => setTimeout(resolve, 0));
};

const csrfRoute = {
  "/api/v1/csrf": { ok: true, payload: { headerName: "X-CSRF-TOKEN", token: "t" } }
};

(async function run() {

  /* =========================================================
     1. 로그인 화면에서 갈 길이 있다
     ========================================================= */

  const loginHtml = read(path.join(TEMPLATES, "login.html"));
  T("로그인 화면에 비밀번호 찾기 버튼이 있다",
    loginHtml.includes('data-route="/auth/forgot-password"'));
  T("회원가입 길도 그대로 남아 있다",
    loginHtml.includes('data-route="/auth/signup"'));

  /* =========================================================
     2. 오류 자리는 처음에 보이지 않는다

     .notice의 display:flex가 브라우저 기본 [hidden]{display:none}을 이겨서
     빈 분홍 띠가 늘 떠 있었다. reset.css에서 !important로 뒤집었다.
     ========================================================= */

  T("reset.css가 hidden을 스타일보다 앞세운다",
    /\[hidden\]\s*\{[^}]*display:\s*none\s*!important/.test(read(RESET_CSS)));

  /* =========================================================
     3. 재설정 화면은 열자마자 링크를 확인한다
     ========================================================= */

  const noToken = await openPage("reset-password.html", "reset-password.js", {});
  T("토큰이 없으면 바로 안내하고 입력을 막는다",
    !noToken.document.querySelector("#reset-error").hidden
      && noToken.document.querySelector("#reset-submit").disabled);
  T("토큰이 없으면 서버를 부르지 않는다", noToken.calls.length === 0,
    JSON.stringify(noToken.calls));

  const deadLink = await openPage("reset-password.html", "reset-password.js", {
    search: "?token=dead",
    routes: {
      "/api/v1/auth/password-reset": {
        ok: false,
        payload: { success: false, message: "링크가 만료되었거나 이미 사용되었습니다." }
      }
    }
  });
  T("만료된 링크는 열자마자 알려 준다",
    deadLink.document.querySelector("#reset-error").textContent.includes("만료"),
    deadLink.document.querySelector("#reset-error").textContent);
  T("만료된 링크에서는 입력이 잠긴다",
    deadLink.document.querySelector("#reset-password").disabled);

  /* 스프링이 낸 기본 404는 {timestamp,status,error,message,path} 꼴이라 success가 없다.
     그 message("No static resource ...")를 그대로 띄우면 손님이 볼 말이 아니다. */
  const framework404 = await openPage("reset-password.html", "reset-password.js", {
    search: "?token=alive",
    routes: {
      "/api/v1/auth/password-reset": {
        ok: false,
        payload: { status: 404, error: "Not Found", message: "No static resource api/v1/auth/password-reset." }
      }
    }
  });
  T("서버 속사정이 화면에 그대로 뜨지 않는다",
    !framework404.document.querySelector("#reset-error").textContent.includes("No static resource"),
    framework404.document.querySelector("#reset-error").textContent);

  const liveLink = await openPage("reset-password.html", "reset-password.js", {
    search: "?token=alive",
    routes: { "/api/v1/auth/password-reset": { ok: true, payload: { success: true, message: "ok" } } }
  });
  T("살아 있는 링크는 조용히 통과한다",
    liveLink.document.querySelector("#reset-error").hidden
      && !liveLink.document.querySelector("#reset-submit").disabled);
  T("확인은 GET 한 번뿐이다",
    liveLink.calls.length === 1 && liveLink.calls[0].method === "GET",
    JSON.stringify(liveLink.calls));

  /* =========================================================
     4. 화면에서 먼저 걸러 낸다
     ========================================================= */

  const short = await openPage("reset-password.html", "reset-password.js", {
    search: "?token=alive",
    routes: Object.assign({}, csrfRoute, {
      "/api/v1/auth/password-reset": { ok: true, payload: { success: true, message: "ok" } }
    })
  });
  short.document.querySelector("#reset-password").value = "1234567";
  short.document.querySelector("#reset-password-confirm").value = "1234567";
  await submit(short, "#reset-form");
  T("8자 미만은 서버까지 가지 않는다",
    short.calls.length === 1 && !short.document.querySelector("#reset-error").hidden,
    short.document.querySelector("#reset-error").textContent);

  const mismatch = await openPage("reset-password.html", "reset-password.js", {
    search: "?token=alive",
    routes: Object.assign({}, csrfRoute, {
      "/api/v1/auth/password-reset": { ok: true, payload: { success: true, message: "ok" } }
    })
  });
  mismatch.document.querySelector("#reset-password").value = "newpassword1";
  mismatch.document.querySelector("#reset-password-confirm").value = "newpassword2";
  await submit(mismatch, "#reset-form");
  T("두 비밀번호가 다르면 서버까지 가지 않는다",
    mismatch.calls.length === 1
      && mismatch.document.querySelector("#reset-error").textContent.includes("다릅니다"),
    mismatch.document.querySelector("#reset-error").textContent);

  const ok = await openPage("reset-password.html", "reset-password.js", {
    search: "?token=alive",
    routes: Object.assign({}, csrfRoute, {
      "/api/v1/auth/password-reset": { ok: true, payload: { success: true, message: "ok" } },
      "/api/v1/auth/password-reset/confirm": {
        ok: true, payload: { success: true, message: "비밀번호를 변경했습니다." }
      }
    })
  });
  ok.document.querySelector("#reset-password").value = "newpassword1";
  ok.document.querySelector("#reset-password-confirm").value = "newpassword1";
  await submit(ok, "#reset-form");
  const confirmCall = ok.calls.find((c) => c.url.endsWith("/confirm"));
  T("변경 요청에 링크의 토큰이 실린다",
    confirmCall && confirmCall.body.token === "alive" && confirmCall.body.newPassword === "newpassword1",
    JSON.stringify(confirmCall));
  T("성공하면 안내가 뜨고 입력이 잠긴다",
    !ok.document.querySelector("#reset-done").hidden
      && ok.document.querySelector("#reset-submit").disabled);

  /* =========================================================
     5. 없는 이메일도 같은 답을 받는다
     ========================================================= */

  const sent = await openPage("forgot-password.html", "forgot-password.js", {
    routes: Object.assign({}, csrfRoute, {
      "/api/v1/auth/password-reset": {
        ok: true,
        payload: { success: true, message: "입력하신 이메일로 재설정 링크를 보냈습니다." }
      }
    })
  });
  sent.document.querySelector("#forgot-email").value = "nobody@example.com";
  await submit(sent, "#forgot-form");
  T("요청하면 안내가 뜬다",
    !sent.document.querySelector("#forgot-done").hidden
      && sent.document.querySelector("#forgot-error").hidden,
    sent.document.querySelector("#forgot-done").textContent);
  T("입력한 이메일은 화면에서 지운다",
    sent.document.querySelector("#forgot-email").value === "");
  const requestCall = sent.calls.find((c) => c.method === "POST");
  T("이메일은 앞뒤 공백 없이 보낸다",
    requestCall && requestCall.body.email === "nobody@example.com",
    JSON.stringify(requestCall));

  console.log("");
  console.log(passed + " passed, " + failed + " failed");
  if (failed > 0) process.exit(1);
})();
