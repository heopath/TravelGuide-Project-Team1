/* 관리자 회원 관리 수용 기준
 * 실행: src/test/js 에서 `npm test`
 *
 * 무게중심은 두 보호장치가 화면에서 실제로 잠기는지다. 서버도 같은 검사를 하지만,
 * 화면이 잠그지 않으면 관리자는 누르고 나서야 거부당한다.
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/admin/admin.html");
const JS = path.join(ROOT, "src/main/resources/static/js/pages/admin/admin-members.js");

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

const CURRENT_ADMIN = 7;

const member = (id, overrides) => Object.assign({
  userId: id,
  email: `member${id}@example.com`,
  nickname: `회원${id}`,
  role: "USER",
  status: "ACTIVE",
  lastLoginAt: "2026-08-15T02:10:00Z",
  createdAt: "2026-08-01T00:00:00Z",
  deletedAt: null,
}, overrides || {});

const ok = (data) => ({
  ok: true, status: 200, json: async () => ({ success: true, code: "SUCCESS", data })
});
const fail = (status, code, message) => ({
  ok: false, status, json: async () => ({ success: false, code, message })
});
const page = (items, adminCount) => ({
  items, page: 0, size: 20, total: items.length, totalPages: 1,
  activeAdminCount: adminCount == null ? 2 : adminCount,
  currentAdminUserId: CURRENT_ADMIN,
});

async function boot(responder) {
  const calls = [];
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/admin", runScripts: "outside-only"
  });
  const w = dom.window;
  const d = w.document;
  w.confirm = () => true;
  w.fetch = async (url, options) => {
    calls.push({ url: String(url), options: options || {} });
    return responder(String(url), options || {});
  };
  w.eval(fs.readFileSync(JS, "utf8"));
  if (d.readyState !== "loading") d.dispatchEvent(new w.Event("DOMContentLoaded"));
  await until(() => calls.length > 0);
  return { w, d, calls };
}

const rows = (d) => [...d.querySelectorAll("[data-member-list] .admin-member-row")];
const button = (row, action) => row.querySelector(`[data-member-action="${action}"]`);
const notice = (d) => d.querySelector("[data-member-notice]");
const empty = (d) => d.querySelector("[data-member-empty]");

async function run() {
  /* ── 마크업 ── */
  {
    const markup = fs.readFileSync(HTML, "utf8").replace(/<!--[\s\S]*?-->/g, "");
    const section = markup.slice(
      markup.indexOf('data-admin-section="members"'),
      markup.indexOf('data-admin-section="audit"')
    );

    T("회원 관리 패널이 실연동으로 표시된다",
      /<span class="admin-tag live" data-admin-state="members">실연동<\/span>/.test(section));
    T("사이드바에 회원 관리 항목이 있다",
      /data-admin-panel="members">[\s\S]*?<em class="live">실연동<\/em>/.test(markup));
    T("상태 필터에 활동·정지·탈퇴가 모두 있다",
      section.includes('data-member-status="ACTIVE"')
      && section.includes('data-member-status="SUSPENDED"')
      && section.includes('data-member-status="WITHDRAWN"'));
    T("권한 필터에 관리자·일반이 있다",
      section.includes('data-member-role="ADMIN"') && section.includes('data-member-role="USER"'));
    T("닉네임과 이메일로 검색할 수 있다",
      /data-member-search[\s\S]*?닉네임 또는 이메일/.test(section));
    T("페이지 스크립트를 실제로 불러온다", markup.includes("/js/pages/admin/admin-members.js"));
  }

  /* ── 목록 ── */
  {
    const { d, calls } = await boot(() => ok(page([
      member(11), member(12, { role: "ADMIN" }),
    ])));
    await until(() => rows(d).length === 2);

    T("회원 목록 API를 호출한다", calls[0].url.startsWith("/api/v1/admin/members?"));
    T("회원마다 한 행을 그린다", rows(d).length === 2);
    T("닉네임과 이메일을 함께 보여준다",
      rows(d)[0].querySelector("strong").textContent === "회원11"
      && rows(d)[0].querySelector("small").textContent === "member11@example.com");
    T("권한을 한국어로 보여준다",
      rows(d)[1].querySelector("[data-member-role-cell]").textContent === "관리자");
    T("상태를 한국어로 보여준다",
      rows(d)[0].querySelector("[data-member-status-cell]").textContent === "활동");
  }

  /* ── 자기 자신 보호 ── */
  {
    const { d } = await boot(() => ok(page([
      member(CURRENT_ADMIN, { role: "ADMIN", nickname: "나" }), member(12),
    ], 3)));
    await until(() => rows(d).length === 2);

    const mine = rows(d)[0];
    T("자기 자신은 정지 버튼이 잠긴다", button(mine, "suspend").disabled === true);
    T("자기 자신은 관리자 해제 버튼이 잠긴다", button(mine, "demote").disabled === true);
    T("잠근 이유를 버튼에 붙인다",
      button(mine, "suspend").title.includes("자기 자신"));
    T("다른 회원의 버튼은 잠기지 않는다", button(rows(d)[1], "suspend").disabled === false);
  }

  /* ── 마지막 관리자 보호 ── */
  {
    const { d } = await boot(() => ok(page([
      member(12, { role: "ADMIN" }),
    ], 1)));
    await until(() => rows(d).length === 1);

    const only = rows(d)[0];
    T("마지막 관리자는 관리자 해제가 잠긴다", button(only, "demote").disabled === true);
    T("마지막 관리자는 정지도 잠긴다", button(only, "suspend").disabled === true);
    T("마지막 관리자라는 이유를 붙인다",
      button(only, "demote").title.includes("마지막 관리자"));
    T("관리자가 한 명뿐이면 미리 알린다",
      notice(d).hidden === false && notice(d).textContent.includes("한 명뿐"));
  }
  {
    const { d } = await boot(() => ok(page([member(12, { role: "ADMIN" })], 2)));
    await until(() => rows(d).length === 1);

    T("관리자가 둘 이상이면 해제할 수 있다", button(rows(d)[0], "demote").disabled === false);
    T("관리자가 둘 이상이면 안내를 띄우지 않는다", notice(d).hidden === true);
  }

  /* ── 탈퇴 회원 ── */
  {
    const { d } = await boot(() => ok(page([
      member(13, { status: "WITHDRAWN", deletedAt: "2026-08-10T00:00:00Z" }),
    ])));
    await until(() => rows(d).length === 1);

    const gone = rows(d)[0];
    T("탈퇴 회원은 상태를 탈퇴로 보여준다",
      gone.querySelector("[data-member-status-cell]").textContent === "탈퇴");
    T("탈퇴 회원은 정지 버튼이 잠긴다", button(gone, "suspend").disabled === true);
    T("탈퇴 회원은 승격 버튼이 잠긴다", button(gone, "promote").disabled === true);
  }

  /* ── 정지된 회원 ── */
  {
    const { d } = await boot(() => ok(page([member(14, { status: "SUSPENDED" })])));
    await until(() => rows(d).length === 1);

    const held = rows(d)[0];
    T("정지된 회원에게는 정지 해제 버튼을 준다", button(held, "activate") !== null);
    T("정지된 회원은 관리자 승격이 잠긴다", button(held, "promote").disabled === true);
    T("승격이 잠긴 이유를 붙인다", button(held, "promote").title.includes("정지된 회원"));
  }

  /*
   * 로컬 DB로 실제 호출해 보고 찾은 것이다. 이미 정지된 관리자는 로그인을 못 하므로
   * 강등해도 /admin에 들어갈 수 있는 사람이 줄지 않는다. 활동 중인 관리자가 하나뿐이어도
   * 잠그면 안 된다.
   */
  {
    const { d } = await boot(() => ok(page([
      member(15, { role: "ADMIN", status: "SUSPENDED" }),
    ], 1)));
    await until(() => rows(d).length === 1);

    T("이미 정지된 관리자는 관리자 수와 무관하게 해제할 수 있다",
      button(rows(d)[0], "demote").disabled === false);
  }

  /* ── 정지 실행 ── */
  {
    let listed = 0;
    const { d, calls } = await boot((url, options) => {
      if (options.method === "PATCH") return ok(member(12, { status: "SUSPENDED" }));
      listed += 1;
      return ok(page([member(12, { status: listed > 1 ? "SUSPENDED" : "ACTIVE" })]));
    });
    await until(() => rows(d).length === 1);

    button(rows(d)[0], "suspend").dispatchEvent(new d.defaultView.Event("click", { bubbles: true }));
    await until(() => calls.some((call) => call.options.method === "PATCH"));

    const patch = calls.find((call) => call.options.method === "PATCH");
    T("정지는 status 엔드포인트로 보낸다", patch.url === "/api/v1/admin/members/12/status");
    T("정지 요청 본문에 SUSPENDED를 담는다",
      JSON.parse(patch.options.body).status === "SUSPENDED");

    await until(() => listed > 1);
    T("바꾼 뒤 목록을 다시 받는다", listed > 1);
  }

  /* ── 승격 실행 ── */
  {
    const { d, calls } = await boot((url, options) => {
      if (options.method === "PATCH") return ok(member(12, { role: "ADMIN" }));
      return ok(page([member(12)]));
    });
    await until(() => rows(d).length === 1);

    button(rows(d)[0], "promote").dispatchEvent(new d.defaultView.Event("click", { bubbles: true }));
    await until(() => calls.some((call) => call.options.method === "PATCH"));

    const patch = calls.find((call) => call.options.method === "PATCH");
    T("승격은 role 엔드포인트로 보낸다", patch.url === "/api/v1/admin/members/12/role");
    T("승격 요청 본문에 ADMIN을 담는다", JSON.parse(patch.options.body).role === "ADMIN");
  }

  /* ── 확인 창을 취소하면 아무것도 보내지 않는다 ── */
  {
    const { w, d, calls } = await boot(() => ok(page([member(12)])));
    await until(() => rows(d).length === 1);
    w.confirm = () => false;

    button(rows(d)[0], "suspend").dispatchEvent(new d.defaultView.Event("click", { bubbles: true }));
    await new Promise((resolve) => setTimeout(resolve, 50));

    T("확인을 취소하면 요청을 보내지 않는다",
      !calls.some((call) => call.options.method === "PATCH"));
  }

  /* ── 필터 ── */
  {
    const { d, calls } = await boot(() => ok(page([member(12)])));
    await until(() => rows(d).length === 1);

    d.querySelector('[data-member-status="SUSPENDED"]')
      .dispatchEvent(new d.defaultView.Event("click", { bubbles: true }));
    await until(() => calls.length > 1);
    T("상태 필터를 요청에 담는다", calls[calls.length - 1].url.includes("status=SUSPENDED"));

    d.querySelector('[data-member-role="ADMIN"]')
      .dispatchEvent(new d.defaultView.Event("click", { bubbles: true }));
    await until(() => calls.length > 2);
    T("권한 필터를 요청에 담는다", calls[calls.length - 1].url.includes("role=ADMIN"));
  }

  /* ── 실패와 빈 목록 ── */
  {
    const { d } = await boot(() => ok(page([])));
    await until(() => empty(d).textContent.includes("없어요"));

    T("회원이 없으면 그대로 알린다", empty(d).textContent === "표시할 회원이 없어요.");
  }
  {
    const { d } = await boot(() => fail(403, "FORBIDDEN", "권한이 없습니다."));
    await until(() => empty(d).textContent.includes("관리자"));

    T("403이면 관리자 전용임을 알린다",
      empty(d).textContent.includes("관리자만 접근할 수 있습니다."));
    T("403이면 행을 그리지 않는다", rows(d).length === 0);
  }
  {
    const { d, calls } = await boot((url, options) => {
      if (options.method === "PATCH") {
        return fail(400, "LAST_ADMIN_PROTECTED", "마지막 관리자입니다. 다른 관리자를 먼저 지정해 주세요.");
      }
      return ok(page([member(12, { role: "ADMIN" })], 2));
    });
    await until(() => rows(d).length === 1);

    button(rows(d)[0], "demote").dispatchEvent(new d.defaultView.Event("click", { bubbles: true }));
    await until(() => calls.some((call) => call.options.method === "PATCH"));
    await until(() => empty(d).hidden === false);

    T("서버가 거부하면 그 이유를 그대로 보여준다",
      empty(d).textContent.includes("마지막 관리자입니다."));
    T("거부되면 버튼을 다시 누를 수 있게 푼다",
      button(rows(d)[0], "demote").disabled === false);
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed > 0) process.exit(1);
}

run().catch((error) => { console.error(error); process.exit(1); });
