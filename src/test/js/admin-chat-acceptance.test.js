/* 관리자 상담 채팅 수용 기준
 * 실행: src/test/js 에서 `npm test`
 *
 * 무게중심은 "맡지 않은 상담에는 답할 수 없다"이다. 아무나 끼어들면 손님은 여러 사람이
 * 번갈아 답하는 것을 보게 되고, 누가 맡았는지도 흐려진다.
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");
const { readMarkup } = require("./markup");

const ROOT = path.resolve(__dirname, "../../..");
const HTML = path.join(ROOT, "src/main/resources/templates/admin/admin.html");
const JS = path.join(ROOT, "src/main/resources/static/js/pages/admin/admin-chat.js");

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

const room = (id, overrides) => Object.assign({
  supportChatRoomId: id,
  userId: 7,
  userNickname: "민재",
  userEmail: "user@example.com",
  assignedAdminId: null,
  assignedAdminNickname: null,
  assignedToMe: false,
  status: "WAITING",
  lastMessageAt: "2026-08-17T02:00:00Z",
  createdAt: "2026-08-17T01:00:00Z",
  lastMessagePreview: "예약이 안 돼요",
  messageCount: 1,
}, overrides || {});

const message = (id, senderType, content) => ({
  supportChatMessageId: id, supportChatRoomId: 5,
  senderType, senderUserId: senderType === "BOT" ? null : 7,
  senderNickname: senderType === "BOT" ? null : "민재",
  content, createdAt: "2026-08-17T02:00:00Z",
});

/*
 * admin-chat.js는 WebSocket이 연결돼 있지 않은 동안 REST 폴백 폴링을 돈다(설계 문서
 * "봇 응답 대기 중 UX" 절 참고, PR #282 리뷰 반영). jsdom의 setInterval은 실제 Node
 * 타이머로 뒷받침되면서도 unref 가능한 핸들을 내주지 않으므로, 열어 둔 창을 그대로 두면
 * 프로세스가 안 끝난다 — 각 테스트가 쓴 창을 전부 모아 뒀다가 마지막에 닫는다.
 */
const openedWindows = [];

async function boot(responder) {
  const calls = [];
  const dom = new JSDOM(fs.readFileSync(HTML, "utf8"), {
    url: "http://localhost/admin", runScripts: "outside-only"
  });
  const w = dom.window;
  openedWindows.push(w);
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

function closeAllWindows() {
  openedWindows.forEach(function (w) {
    try { w.close(); } catch (error) { /* 이미 닫혔거나 정리할 것이 없다. */ }
  });
  openedWindows.length = 0;
}

const rows = (d) => [...d.querySelectorAll("#chatRoomList .admin-chat-room")];
const bubbles = (d) => [...d.querySelectorAll("#chatMessages .admin-chat-message")];
const input = (d) => d.getElementById("chatInput");
const takeover = (d) => d.getElementById("chatTakeover");

async function openFirstRoom(d, w, calls) {
  await until(() => rows(d).length > 0);
  rows(d)[0].dispatchEvent(new w.Event("click", { bubbles: true }));
  await until(() => calls.some((c) => /support-chats\/\d+$/.test(c.url)));
}

async function run() {
  /* ── 마크업 ── */
  {
    const markup = readMarkup(HTML);

    T("상담 채팅이 실연동으로 표시된다",
      /<span class="admin-tag live" data-admin-state="chat">실연동<\/span>/.test(markup));
    T("사이드바 배지도 실연동이다",
      /data-admin-panel="chat">[\s\S]*?<em class="live">실연동<\/em>/.test(markup));
    T("종료 버튼이 있다", markup.includes('id="chatClose"'));
    T("페이지 스크립트를 실제로 불러온다", markup.includes("/js/pages/admin/admin-chat.js"));
  }

  /* ── 방 목록 ── */
  {
    const { d, calls } = await boot(() => ok([room(5), room(6, { status: "ASSIGNED", assignedAdminId: 90 })]));
    await until(() => rows(d).length === 2);

    T("상담 목록 API를 호출한다", calls[0].url.startsWith("/api/v1/admin/support-chats?"));
    T("상담마다 한 줄을 그린다", rows(d).length === 2);
    T("손님 이름을 보여준다", rows(d)[0].querySelector("strong").textContent === "민재");
    T("상태를 한국어로 보여준다",
      rows(d)[0].querySelector("[data-chat-room-status]").textContent === "대기");
    T("마지막 한 줄을 미리 보여준다", rows(d)[0].textContent.includes("예약이 안 돼요"));
  }
  {
    const { d } = await boot(() => ok([room(5, { lastMessagePreview: null })]));
    await until(() => rows(d).length === 1);

    T("아직 대화가 없는 상담도 빈칸으로 두지 않는다",
      rows(d)[0].textContent.includes("아직 대화가 없어요"));
  }

  /* ── 맡기 전에는 답할 수 없다 ── */
  {
    const { d, w, calls } = await boot((url) => {
      if (/support-chats\/\d+$/.test(url)) {
        return ok({ room: room(5), messages: [message(1, "USER", "예약이 안 돼요")] });
      }
      return ok([room(5)]);
    });
    await openFirstRoom(d, w, calls);
    await until(() => bubbles(d).length === 1);

    T("고른 상담의 대화를 보여준다", bubbles(d).length === 1);
    T("보낸이를 한국어로 보여준다", bubbles(d)[0].querySelector("em").textContent.includes("손님"));
    T("맡기 전에는 답장 입력이 막혀 있다", input(d).disabled === true);
    T("맡으라는 안내를 입력창에 둔다", input(d).placeholder.includes("내가 응대하기"));
    T("맡을 수 있는 상담은 응대하기 버튼이 열려 있다", takeover(d).disabled === false);
  }

  /* ── 내가 맡으면 답할 수 있다 ── */
  {
    let assigned = false;
    const { d, w, calls } = await boot((url, options) => {
      if (/takeover$/.test(url)) {
        assigned = true;
        return ok({ room: room(5, { status: "ASSIGNED", assignedAdminId: 90, assignedToMe: true }), messages: [] });
      }
      if (/support-chats\/\d+$/.test(url)) {
        return ok({
          room: assigned
            ? room(5, { status: "ASSIGNED", assignedAdminId: 90, assignedToMe: true })
            : room(5),
          messages: []
        });
      }
      return ok([room(5)]);
    });
    await openFirstRoom(d, w, calls);
    await until(() => input(d).disabled === true);

    takeover(d).dispatchEvent(new w.Event("click", { bubbles: true }));
    await until(() => calls.some((c) => /takeover$/.test(c.url)));
    await until(() => input(d).disabled === false);

    T("내가 응대하기를 누르면 배정 API를 호출한다",
      calls.some((c) => c.url === "/api/v1/admin/support-chats/5/takeover" && c.options.method === "POST"));
    T("맡은 뒤에는 답장 입력이 열린다", input(d).disabled === false);
    T("맡은 뒤에는 응대하기 버튼이 잠긴다", takeover(d).disabled === true);
    T("내가 응대 중임을 버튼이 알린다", takeover(d).textContent.trim() === "내가 응대 중");
  }

  /*
   * 다른 관리자가 맡은 상담은 열어 볼 수는 있어도 답할 수 없다.
   * 서버도 막지만, 눌러 보고 나서 거부당하면 이유를 알기 어렵다.
   */
  {
    const { d, w, calls } = await boot((url) => {
      if (/support-chats\/\d+$/.test(url)) {
        return ok({
          room: room(5, {
            status: "ASSIGNED", assignedAdminId: 91,
            assignedAdminNickname: "수진", assignedToMe: false
          }),
          messages: []
        });
      }
      return ok([room(5)]);
    });
    await openFirstRoom(d, w, calls);
    await until(() => d.getElementById("chatPeerMeta").textContent.includes("수진"));

    T("다른 관리자가 맡은 상담에는 답할 수 없다", input(d).disabled === true);
    T("맡을 수도 없다", takeover(d).disabled === true);
    T("누가 맡았는지 보여준다", d.getElementById("chatPeerMeta").textContent.includes("담당 수진"));
  }

  /* ── 답장 ── */
  {
    const { d, w, calls } = await boot((url, options) => {
      if (/messages$/.test(url) && options.method === "POST") {
        return ok({
          room: room(5, { status: "ASSIGNED", assignedAdminId: 90, assignedToMe: true }),
          messages: [message(1, "USER", "예약이 안 돼요"), message(2, "ADMIN", "확인해 드릴게요")]
        });
      }
      if (/support-chats\/\d+$/.test(url)) {
        return ok({
          room: room(5, { status: "ASSIGNED", assignedAdminId: 90, assignedToMe: true }),
          messages: [message(1, "USER", "예약이 안 돼요")]
        });
      }
      return ok([room(5)]);
    });
    await openFirstRoom(d, w, calls);
    await until(() => input(d).disabled === false);

    input(d).value = "확인해 드릴게요";
    d.getElementById("chatComposer").dispatchEvent(
      new w.Event("submit", { bubbles: true, cancelable: true }));
    await until(() => calls.some((c) => /messages$/.test(c.url) && c.options.method === "POST"));
    await until(() => bubbles(d).length === 2);

    const post = calls.find((c) => /messages$/.test(c.url) && c.options.method === "POST");
    T("답장은 메시지 API로 보낸다", post.url === "/api/v1/admin/support-chats/5/messages");
    T("입력한 내용을 본문에 담는다", JSON.parse(post.options.body).content === "확인해 드릴게요");
    T("보낸 뒤 대화에 반영된다", bubbles(d).length === 2);
    T("관리자 말은 관리자로 구분한다", bubbles(d)[1].className.includes("admin"));
    T("보낸 뒤 입력창을 비운다", input(d).value === "");
  }

  /* ── 종료 ── */
  {
    const { d, w, calls } = await boot((url) => {
      if (/close$/.test(url)) {
        return ok({ room: room(5, { status: "CLOSED", assignedToMe: false }), messages: [] });
      }
      if (/support-chats\/\d+$/.test(url)) {
        return ok({ room: room(5, { status: "ASSIGNED", assignedAdminId: 90, assignedToMe: true }), messages: [] });
      }
      return ok([room(5)]);
    });
    await openFirstRoom(d, w, calls);
    await until(() => input(d).disabled === false);

    d.getElementById("chatClose").dispatchEvent(new w.Event("click", { bubbles: true }));
    await until(() => calls.some((c) => /close$/.test(c.url)));
    await until(() => input(d).disabled === true);

    T("종료하면 종료 API를 호출한다",
      calls.some((c) => c.url === "/api/v1/admin/support-chats/5/close" && c.options.method === "POST"));
    T("종료된 상담에는 답할 수 없다", input(d).disabled === true);
    T("종료됐음을 입력창이 알린다", input(d).placeholder.includes("종료"));
  }

  /* ── 필터와 실패 ── */
  {
    const { d, w, calls } = await boot(() => ok([]));
    await until(() => calls.length > 0);

    d.querySelector('[data-chat-filter="WAITING"]').dispatchEvent(new w.Event("click", { bubbles: true }));
    await until(() => calls.length > 1);

    T("상태 필터를 요청에 담는다", calls[calls.length - 1].url.includes("status=WAITING"));
    T("상담이 없으면 그대로 알린다",
      d.getElementById("chatRoomEmpty").textContent.includes("없어요"));
  }
  {
    const { d } = await boot(() => fail(403, "FORBIDDEN", "권한이 없습니다."));
    await until(() => d.getElementById("chatRoomEmpty").textContent.includes("관리자"));

    T("403이면 관리자 전용임을 알린다",
      d.getElementById("chatRoomEmpty").textContent.includes("관리자만 접근할 수 있습니다."));
  }

  closeAllWindows();
  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed > 0) process.exit(1);
}

run().catch((error) => { closeAllWindows(); console.error(error); process.exit(1); });
