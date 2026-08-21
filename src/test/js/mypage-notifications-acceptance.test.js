/* 마이페이지 알림 수용 기준 (#191)
 *
 * 알림은 곁다리다. 못 받아도 마이페이지의 다른 것이 막혀서는 안 되고, 안 읽은 표시가
 * 사실과 달라서도 안 된다 — 읽을 것이 없는데 배지가 붙어 있으면 눌러 보게 되고,
 * 있는데 안 붙어 있으면 놓친다.
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");
const { readMarkup } = require("./markup");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/mypage/mypage.html");
const JS = path.join(ROOT, "src/main/resources/static/js/pages/mypage/mypage-notifications.js");

let passed = 0;
let failed = 0;
function test(name, condition, detail) {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name + (detail ? " — " + detail : "")); }
}
function until(predicate, timeoutMs = 3000) {
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

const text = (d, s) => d.querySelector(s)?.textContent?.trim() || "";

/*
 * mypage-common.js의 request는 이미 파싱된 data를 돌려준다. fetch 응답 모양이 아니다.
 * 응답 껍데기를 흉내 내면 화면이 data.items를 못 찾는다.
 */
function ok(data) {
  return data;
}

const sample = (overrides) => Object.assign({
  notificationId: 1,
  type: "PAYMENT_COMPLETED",
  title: "결제가 완료됐어요",
  body: "티켓 2장이 발급됐습니다. 입장 QR을 확인해 주세요.",
  link: "/mypage?view=tickets",
  readAt: null,
  createdAt: new Date().toISOString(),
}, overrides || {});

/**
 * 알림 화면을 띄운다.
 *
 * 모듈이 mypage-common.js의 request를 쓰므로 그 자리를 대신 채워 넣는다. jsdom에서는
 * import를 가로챌 수 없어, 모듈 소스의 import 줄을 걷어내고 같은 이름의 함수를 준다.
 */
async function boot(responder) {
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/mypage?view=notifications",
    runScripts: "outside-only",
  });
  const w = dom.window;
  const calls = [];

  const source = fs.readFileSync(JS, "utf8")
    .replace(/^import[\s\S]*?;\s*$/m, "")
    .replace(/^export /gm, "");

  w.eval(`
    async function request(url, options) {
      globalThis.__calls.push({ url, method: (options && options.method) || "GET" });
      return globalThis.__responder(url, options);
    }
    ${source}
    globalThis.__api = { initNotifications, initNotificationBadge };
  `);
  w.__calls = calls;
  w.__responder = responder;

  return { w, d: w.document, calls };
}

async function run() {
  /* ── 화면 자체 ── */
  {
    const markup = readMarkup(HTML);
    /* 예전에는 눌리지 않는 `준비 중` 버튼만 있었다. */
    test("알림 버튼이 눌린다",
      markup.includes("data-open-notifications") && !/data-open-notifications[^>]*disabled/.test(markup));
    test("준비 중 표시를 지웠다", !markup.includes("<em>준비 중</em>"));
    test("알림 화면이 있다", markup.includes("data-notifications-view"));
    test("안 읽은 개수 자리가 있다", markup.includes("data-notification-badge"));
  }

  /* ── 목록 ── */
  {
    const { d, w, calls } = await boot(() => ok({
      items: [sample(), sample({ notificationId: 2, type: "SUPPORT_REPLIED",
        title: "문의에 답변이 달렸어요", link: "/mypage?view=support", readAt: new Date().toISOString() })],
      unread: 1, total: 2,
    }));
    w.__api.initNotifications();
    await until(() => !d.querySelector("[data-notifications-list]").hidden);

    test("목록을 부른다", calls.some((c) => c.url.startsWith("/api/v1/notifications")));
    test("받은 알림을 모두 그린다",
      d.querySelectorAll("[data-notification-id]").length === 2);
    test("제목과 본문을 보여준다",
      text(d, "[data-notification-id='1'] strong").includes("결제가 완료")
      && text(d, "[data-notification-id='1'] p").includes("2장"));

    /* 안 읽은 것과 읽은 것이 한눈에 갈려야 훑어보기 쉽다. */
    test("안 읽은 것만 표시가 남는다",
      d.querySelector("[data-notification-id='1']").dataset.unread === "1"
      && d.querySelector("[data-notification-id='2']").dataset.unread === undefined);

    const badge = d.querySelector("[data-notification-badge]");
    test("안 읽은 개수를 배지에 쓴다", badge.textContent === "1" && badge.hidden === false);
    test("갈 곳이 있으면 링크를 준다",
      d.querySelector("[data-notification-id='1'] .notification-go").getAttribute("href")
        === "/mypage?view=tickets");
  }

  /* ── 읽을 것이 없을 때 ── */
  {
    const { d, w } = await boot(() => ok({ items: [], unread: 0, total: 0 }));
    w.__api.initNotifications();
    await until(() => text(d, "[data-notifications-state]").includes("없어요"));

    test("빈 목록을 그렇다고 말한다", text(d, "[data-notifications-state]").includes("아직 받은 알림이 없어요"));
    /* 0이 붙어 있으면 읽을 것이 있는 줄 알고 눌러 보게 된다. */
    test("0이면 배지를 감춘다", d.querySelector("[data-notification-badge]").hidden === true);
    test("읽을 것이 없으면 모두 읽음 버튼도 감춘다",
      d.querySelector("[data-notifications-read-all]").hidden === true);
  }

  /* ── 읽음 처리 ── */
  {
    const { d, w, calls } = await boot((url, options) => {
      if (url.includes("/read")) return ok(null);
      if (url.includes("unread-count")) return ok({ unread: 0 });
      return ok({ items: [sample()], unread: 1, total: 1 });
    });
    w.__api.initNotifications();
    await until(() => !d.querySelector("[data-notifications-list]").hidden);

    d.querySelector("[data-notification-id='1']").click();
    await until(() => calls.some((c) => c.url.includes("/1/read")));

    const read = calls.find((c) => c.url.includes("/1/read"));
    test("누르면 읽음으로 보낸다", read.method === "PATCH", JSON.stringify(read));
    test("누른 줄의 안 읽음 표시를 지운다",
      d.querySelector("[data-notification-id='1']").dataset.unread === undefined);
  }
  {
    /* 이미 읽은 것을 다시 눌러도 서버를 부르지 않는다. 부르면 읽은 시각만 밀린다. */
    const { d, w, calls } = await boot(() => ok({
      items: [sample({ readAt: new Date().toISOString() })], unread: 0, total: 1,
    }));
    w.__api.initNotifications();
    await until(() => !d.querySelector("[data-notifications-list]").hidden);

    const before = calls.length;
    d.querySelector("[data-notification-id='1']").click();
    await new Promise((r) => setTimeout(r, 60));
    test("읽은 것을 다시 눌러도 서버를 부르지 않는다", calls.length === before);
  }

  /* ── 바깥 주소 ── */
  {
    /* 알림이 손님을 사이트 밖으로 보내는 발판이 되면 안 된다. */
    const { d, w } = await boot(() => ok({
      items: [sample({ link: "https://evil.example/steal" })], unread: 1, total: 1,
    }));
    w.__api.initNotifications();
    await until(() => !d.querySelector("[data-notifications-list]").hidden);
    test("바깥 주소는 링크로 만들지 않는다",
      d.querySelector(".notification-go") === null);
  }
  {
    const { d, w } = await boot(() => ok({
      items: [sample({ link: "//evil.example/steal" })], unread: 1, total: 1,
    }));
    w.__api.initNotifications();
    await until(() => !d.querySelector("[data-notifications-list]").hidden);
    test("//로 시작하는 주소도 막는다", d.querySelector(".notification-go") === null);
  }

  /* ── 배지만 따로 ── */
  {
    const { d, w, calls } = await boot(() => ok({ unread: 3 }));
    await w.__api.initNotificationBadge();
    test("개수만 필요할 때는 목록을 받지 않는다",
      calls.every((c) => c.url.includes("unread-count")), calls.map((c) => c.url).join(" | "));
    test("배지에 개수를 쓴다", d.querySelector("[data-notification-badge]").textContent === "3");
  }
  {
    /* 배지 하나 때문에 마이페이지에 오류를 띄울 이유가 없다. */
    const { d, w } = await boot(() => { throw new Error("서버 오류"); });
    await w.__api.initNotificationBadge();
    test("개수를 못 받아도 조용히 넘어간다",
      d.querySelector("[data-notification-badge]").hidden === true);
  }

  console.log("\n" + passed + " passed, " + failed + " failed");
  if (failed > 0) process.exitCode = 1;
}

run().catch((error) => { console.error(error); process.exitCode = 1; });
