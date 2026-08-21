/* 관리자 티켓 검표 수용 기준
 * 실행: src/test/js 에서 `npm test`
 *
 * 무게중심은 "실패도 결과로 보여주는가"다. 없는 코드나 이미 쓴 티켓은 오류가 아니라
 * 검표가 답해야 할 상황이고, 현장에서는 그 이유가 가장 중요한 정보다.
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");
const { readMarkup } = require("./markup");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/admin/admin.html");
const JS = path.join(ROOT, "src/main/resources/static/js/pages/admin/admin-validation.js");

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

const ok = (data) => ({
  ok: true, status: 200, json: async () => ({ success: true, code: "SUCCESS", data })
});
const fail = (status, code, message) => ({
  ok: false, status, json: async () => ({ success: false, code, message })
});

const logEntry = (id, overrides) => Object.assign({
  ticketValidationLogId: id,
  issuedTicketId: 5,
  ticketNumber: "AMT-TKN-AAA",
  productName: "아쿠아리움",
  validatorUserId: 7,
  validatorNickname: "민재",
  validationResult: "SUCCESS",
  validationChannel: "ADMIN_WEB",
  failureReason: null,
  validatedAt: "2026-08-16T09:00:00Z",
}, overrides || {});

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

const box = (d) => d.querySelector("[data-validation-result-box]");
const headline = (d) => d.querySelector("[data-validation-headline]")?.textContent || "";
const message = (d) => d.querySelector("[data-validation-message]")?.textContent || "";
const rows = (d) => [...d.querySelectorAll("[data-validation-list] .admin-validation-row")];

/** 코드를 입력하고 확인을 누른다. */
async function scan(d, w, value) {
  d.querySelector("[data-validation-token]").value = value;
  d.querySelector("[data-validation-form]")
    .dispatchEvent(new w.Event("submit", { bubbles: true, cancelable: true }));
}

async function run() {
  /* ── 마크업 ── */
  {
    const markup = readMarkup(HTML);
    const section = markup.slice(
      markup.indexOf('data-admin-section="validation"'),
      markup.indexOf('data-admin-section="members"')
    );

    T("검표 패널이 실연동으로 표시된다",
      /<span class="admin-tag live" data-admin-state="validation">실연동<\/span>/.test(section));
    T("사이드바에 검표 항목이 있다",
      /data-admin-panel="validation">[\s\S]*?<em class="live">실연동<\/em>/.test(markup));
    T("입장 코드 입력창이 있다", section.includes("data-validation-token"));
    T("QR 스캔을 우선 사용하도록 안내한다",
      section.includes("QR 스캔 화면 열기") && section.includes('href="/admin/scan"'));
    T("결과 필터에 입장·사용됨·취소·기간 밖·없는 코드가 있다",
      section.includes('data-validation-result="SUCCESS"')
      && section.includes('data-validation-result="ALREADY_USED"')
      && section.includes('data-validation-result="CANCELLED"')
      && section.includes('data-validation-result="EXPIRED"')
      && section.includes('data-validation-result="NOT_FOUND"'));
    T("페이지 스크립트를 실제로 불러온다", markup.includes("/js/pages/admin/admin-validation.js"));
  }

  /* ── 기록 목록 ── */
  {
    const { d, calls } = await boot(() => ok([logEntry(1), logEntry(2, { validationResult: "EXPIRED" })]));
    await until(() => rows(d).length === 2);

    T("검표 기록 API를 호출한다", calls[0].url.startsWith("/api/v1/admin/ticket-validations?"));
    T("기록마다 한 줄을 그린다", rows(d).length === 2);
    T("결과를 한국어로 보여준다",
      rows(d)[0].querySelector("[data-validation-result-cell]").textContent === "입장");
    T("검표자를 보여준다", rows(d)[0].textContent.includes("민재"));
    T("수동 입력과 QR 스캔 경로를 구분한다",
      rows(d)[0].querySelector("[data-validation-channel]").textContent === "수동 입력");
    T("불러온 기록 수를 보여준다",
      d.querySelector("[data-validation-count]").textContent === "최근 기록 2건");
  }
  {
    const { d } = await boot(() => ok([
      logEntry(1, {
        validationResult: "EXPIRED",
        validationChannel: "MOCK_SCANNER",
        failureReason: "이용 가능 기간이 지났습니다.",
      })
    ]));
    await until(() => rows(d).length === 1);

    T("실패한 기록에 이유를 함께 보여준다",
      rows(d)[0].querySelector("[data-validation-failure-reason]").textContent.includes("기간"));
    T("카메라 검표 기록은 QR 스캔으로 표시한다",
      rows(d)[0].querySelector("[data-validation-channel]").textContent === "QR 스캔");
  }
  {
    // 없는 코드로 시도한 기록에는 티켓이 없다. 빈칸으로 두지 않는다.
    const { d } = await boot(() => ok([
      logEntry(1, { validationResult: "NOT_FOUND", issuedTicketId: null, ticketNumber: null, productName: null })
    ]));
    await until(() => rows(d).length === 1);

    T("티켓이 없는 기록도 빈칸으로 두지 않는다",
      rows(d)[0].textContent.includes("확인되지 않은 코드"));
  }

  /* ── 검표 성공 ── */
  {
    const { d, w, calls } = await boot((url, options) => {
      if (options.method === "POST") {
        return ok({
          result: "SUCCESS", admitted: true, message: "입장하실 수 있어요.",
          ticketNumber: "AMT-TKN-AAA", productName: "아쿠아리움", optionName: "성인",
          usageDate: "2026-08-18", usedAt: "2026-08-16T09:00:00Z",
          validFrom: "2026-08-18T09:00:00Z", validUntil: "2026-08-18T18:00:00Z"
        });
      }
      return ok([]);
    });
    await until(() => calls.length > 0);

    await scan(d, w, "entry-code-1");
    await until(() => calls.some((c) => c.options.method === "POST"));
    await until(() => box(d).hidden === false);

    const post = calls.find((c) => c.options.method === "POST");
    T("검표는 검표 API로 보낸다", post.url === "/api/v1/admin/ticket-validations");
    T("입력한 코드를 본문에 담는다", JSON.parse(post.options.body).token === "entry-code-1");
    T("입장 가능임을 문구로 밝힌다", headline(d).includes("입장 가능"));
    T("색만이 아니라 표시로도 구분한다", box(d).className.includes("ok"));
    T("손님에게 읽어 줄 문장을 보여준다", message(d) === "입장하실 수 있어요.");
    T("어떤 티켓인지 함께 보여준다",
      d.querySelector("[data-validation-ticket]")?.textContent.includes("AMT-TKN-AAA"));
    T("입장 가능한 시간을 함께 보여준다",
      d.querySelector("[data-validation-validity]")?.textContent.includes("입장 가능 시간"));
    /* 다음 손님을 바로 받아야 한다. */
    T("확인 뒤 입력창을 비운다", d.querySelector("[data-validation-token]").value === "");
    d.querySelector("[data-validation-next]").click();
    T("다음 손님 검표를 누르면 이전 결과를 닫는다", box(d).hidden === true);
    T("다음 검표를 위해 입력창에 초점을 돌린다",
      d.activeElement === d.querySelector("[data-validation-token]"));
  }

  /* ── 검표 실패도 결과로 보여준다 ── */
  {
    const { d, w, calls } = await boot((url, options) => {
      if (options.method === "POST") {
        return ok({
          result: "ALREADY_USED", admitted: false,
          message: "이미 8월 16일 09:00에 사용된 티켓이에요.",
          ticketNumber: "AMT-TKN-AAA", productName: "아쿠아리움",
          usedAt: "2026-08-16T09:00:00Z"
        });
      }
      return ok([]);
    });
    await until(() => calls.length > 0);

    await scan(d, w, "used-code");
    await until(() => box(d).hidden === false);

    T("이미 사용된 티켓은 입장 불가로 보여준다", headline(d).includes("입장 불가"));
    T("사용됨을 결과 이름으로 밝힌다", headline(d).includes("사용됨"));
    T("언제 썼는지 알려준다", message(d).includes("이미") && message(d).includes("사용"));
  }
  {
    const { d, w, calls } = await boot((url, options) => {
      if (options.method === "POST") {
        return ok({
          result: "NOT_FOUND", admitted: false,
          message: "확인되지 않는 입장 코드예요. 코드를 다시 확인해 주세요.",
          ticketNumber: null
        });
      }
      return ok([]);
    });
    await until(() => calls.length > 0);

    await scan(d, w, "nope");
    await until(() => box(d).hidden === false);

    T("없는 코드도 오류가 아니라 결과로 보여준다", headline(d).includes("없는 코드"));
    T("티켓이 없으면 티켓 줄을 그리지 않는다", !d.querySelector("[data-validation-ticket]"));
  }

  /* ── 검표 뒤 기록을 다시 읽는다 ── */
  {
    let listCalls = 0;
    const { d, w, calls } = await boot((url, options) => {
      if (options.method === "POST") {
        return ok({ result: "SUCCESS", admitted: true, message: "입장하실 수 있어요.", ticketNumber: "AMT-TKN-AAA" });
      }
      listCalls += 1;
      return ok([]);
    });
    await until(() => listCalls === 1);

    await scan(d, w, "entry-code-1");
    await until(() => listCalls > 1);

    T("검표한 뒤 기록을 다시 읽는다", listCalls > 1);
  }

  /* ── 필터 ── */
  {
    const { d, w, calls } = await boot(() => ok([]));
    await until(() => calls.length > 0);

    d.querySelector('[data-validation-result="EXPIRED"]')
      .dispatchEvent(new w.Event("click", { bubbles: true }));
    await until(() => calls.length > 1);

    T("결과 필터를 요청에 담는다", calls[calls.length - 1].url.includes("result=EXPIRED"));
    T("선택한 결과 필터를 보조기기에도 알린다",
      d.querySelector('[data-validation-result="EXPIRED"]').getAttribute("aria-pressed") === "true");

    d.querySelector("[data-validation-refresh]").click();
    await until(() => calls.length > 2);
    T("새로고침은 현재 결과 필터를 유지한다",
      calls[calls.length - 1].url.includes("result=EXPIRED"));
  }

  /* ── 요청 자체가 실패한 경우 ── */
  {
    const { d, w, calls } = await boot((url, options) => {
      if (options.method === "POST") return fail(403, "FORBIDDEN", "권한이 없습니다.");
      return ok([]);
    });
    await until(() => calls.length > 0);

    await scan(d, w, "entry-code-1");
    await until(() => box(d).hidden === false);

    T("권한이 없으면 그 사실을 결과 자리에 보여준다",
      headline(d).includes("확인하지 못했") && message(d).includes("관리자"));
    T("요청 실패는 입장 가능으로 보이지 않는다", box(d).className.includes("no"));
  }
  {
    const { d } = await boot(() => fail(403, "FORBIDDEN", "권한이 없습니다."));
    await until(() => d.querySelector("[data-validation-empty]").textContent.includes("관리자"));

    T("403이면 기록 자리에 관리자 전용임을 알린다",
      d.querySelector("[data-validation-empty]").textContent.includes("관리자만 접근할 수 있습니다."));
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed > 0) process.exit(1);
}

run().catch((error) => { console.error(error); process.exit(1); });
