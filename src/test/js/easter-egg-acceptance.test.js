/* 이스터에그 수용 기준 — 콘솔 인사와 팀 크레딧
 *
 * 이스터에그는 재미로 넣지만, 재미 때문에 서비스가 상하면 안 된다. 그래서 여기서
 * 보는 것은 재미가 아니라 경계다.
 *
 *   1. 실수로 열리지 않는다. 저작권 줄을 몇 번 눌렀다고 화면이 덮이면 사고다.
 *   2. 열렸으면 반드시 닫힌다. 닫는 길이 막히면 손님에게는 새로고침 말고 길이 없다.
 *   3. 닫은 뒤에는 그리기를 멈춘다. 보이지 않는 화면을 계속 그리면 배터리만 먹는다.
 *   4. 버전 값은 관리자가 고치는 값이라 서버를 거쳐 온다. 글자로만 남아야 한다.
 *   5. canvas를 쓸 수 없거나 움직임을 줄이는 설정이면 글자로 펼쳐 두고, 그때는
 *      스스로 닫지 않는다 — 흐름이 없으니 끝나는 시점도 없다.
 *
 * 그림 자체는 검사하지 않는다. 대신 자리와 시점을 정하는 계산(measure·phaseAt·
 * planeAt)을 따로 내놓게 해서 그쪽을 본다. 기록 지면 검사와 같은 방식이다.
 *
 * 실행: src/test/js 에서 `npm test`
 */
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const CREDITS_JS = path.join(ROOT, "src/main/resources/static/js/core/credits.js");
const GREETING_JS = path.join(ROOT, "src/main/resources/static/js/core/console-greeting.js");

/* credits.js와 맞춰 둔다. 여기 값이 어긋나면 검사가 엉뚱한 시점을 본다. */
const TAPS_NEEDED = 5;
const TAP_GAP_MS = 1500;

let passed = 0;
let failed = 0;
const T = (name, condition, detail) => {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name + (detail ? " — " + detail : "")); }
};

const FOOTER = [
  '<footer class="site-footer">',
  "<small>© 2026 All My Trips. ",
  '<span class="footer-version">v0.0.5</span>',
  "</small></footer>",
].join("");

/**
 * 아무것도 그리지 않는 2D 도구. 무엇을 그렸는지만 적어 둔다.
 *
 * jsdom에는 canvas가 없다. 그림을 확인하려는 것이 아니라 "흐르는 길로 갔는지",
 * "글자가 canvas에 올라갔는지"를 보려는 것이라 이 정도면 충분하다.
 */
function fakeContext() {
  const drawn = [];
  const context = {
    drawn,
    createLinearGradient: () => ({ addColorStop() {} }),
    fillText: (text) => drawn.push(text),
  };
  [
    "setTransform", "fillRect", "beginPath", "arc", "fill", "moveTo", "lineTo",
    "closePath", "stroke", "save", "restore", "translate", "rotate", "setLineDash",
  ].forEach((name) => { context[name] = () => {}; });
  return context;
}

/**
 * 푸터가 있는 문서를 만들고 크레딧 스크립트를 올린다.
 *
 * options.version — 푸터에 찍힌 버전 문자열을 갈아 끼운다.
 * options.reducedMotion — 움직임을 줄이는 설정을 켠 사람으로 가정한다.
 * options.canvas — true면 canvas를 쓸 수 있는 환경으로 꾸민다.
 */
function boot(options) {
  const settings = options || {};
  const dom = new JSDOM("<!doctype html><html><body>" + FOOTER + "</body></html>",
    { url: "http://localhost/", runScripts: "outside-only" });
  const w = dom.window;

  const badge = w.document.querySelector(".footer-version");
  if (settings.version !== undefined) badge.textContent = settings.version;

  /* jsdom에는 matchMedia가 없다. 보려는 설정만 흉내 낸다. */
  if (settings.reducedMotion !== undefined) {
    w.matchMedia = (query) => ({
      matches: settings.reducedMotion && query.includes("prefers-reduced-motion"),
      media: query,
    });
  }

  const context = fakeContext();
  /* 예약된 프레임. 취소하면 실제 브라우저처럼 목록에서 빠져야 뜻이 있다. */
  const frames = new Map();
  const cancelled = [];
  let nextFrameId = 0;

  if (settings.canvas) {
    w.HTMLCanvasElement.prototype.getContext = () => context;
    w.requestAnimationFrame = (callback) => {
      nextFrameId += 1;
      frames.set(nextFrameId, callback);
      return nextFrameId;
    };
    w.cancelAnimationFrame = (id) => {
      cancelled.push(id);
      frames.delete(id);
    };
  }

  w.eval(fs.readFileSync(CREDITS_JS, "utf8"));

  const tap = (times) => {
    for (let i = 0; i < (times || 1); i += 1) {
      badge.dispatchEvent(new w.Event("click", { bubbles: true }));
    }
  };
  const overlay = () => w.document.querySelector(".credits-roll");
  const press = (key) => {
    const event = new w.KeyboardEvent("keydown", { key, bubbles: true, cancelable: true });
    w.document.dispatchEvent(event);
    return event;
  };
  /** 예약된 다음 프레임을 주어진 시각으로 실행한다. */
  const runFrame = (at) => {
    const id = Array.from(frames.keys()).pop();
    if (id === undefined) return false;
    const callback = frames.get(id);
    frames.delete(id);
    callback(at);
    return true;
  };

  return { w, d: w.document, badge, tap, overlay, press, context, runFrame, frames, cancelled };
}

/* 세던 것을 잊는 간격을 실제로 기다리지 않고, 시계를 앞으로 돌린 척한다.
 * 창 안쪽(w.Date)을 고쳐야 한다. 스크립트는 jsdom 창 안에서 도는 터라 이 시험
 * 프로세스의 Date를 아무리 고쳐도 그쪽에는 닿지 않는다. */
function advanceClock(w, ms) {
  const realNow = w.Date.now;
  w.Date.now = () => realNow() + ms;
  return () => { w.Date.now = realNow; };
}

function run() {
  /* ── 실수로 열리지 않는다 ── */
  {
    const { tap, overlay } = boot();
    tap(TAPS_NEEDED - 1);
    T("네 번까지는 아무 일도 없다", !overlay());
  }
  {
    const { tap, overlay } = boot();
    tap(TAPS_NEEDED);
    T("다섯 번 이어 누르면 크레딧이 열린다", !!overlay());
  }
  {
    const { w, tap, overlay } = boot();
    tap(TAPS_NEEDED - 1);
    const restore = advanceClock(w, TAP_GAP_MS + 100);
    tap(1);
    restore();
    T("사이가 뜨면 세던 것을 잊는다", !overlay());
  }
  {
    /* 두 겹으로 열리면 한 번 닫아도 하나가 남는다. */
    const { d, tap } = boot({ canvas: true });
    tap(TAPS_NEEDED);
    tap(TAPS_NEEDED);
    T("이미 열려 있으면 겹쳐 열지 않는다", d.querySelectorAll(".credits-roll").length === 1);
  }

  /* ── 열렸으면 반드시 닫힌다 ── */
  {
    const { tap, overlay, press } = boot({ canvas: true });
    tap(TAPS_NEEDED);
    press("Escape");
    T("Escape로 닫힌다", !overlay());
  }
  {
    const { w, tap, overlay } = boot({ canvas: true });
    tap(TAPS_NEEDED);
    overlay().querySelector("[data-credits-canvas]")
      .dispatchEvent(new w.Event("click", { bubbles: true }));
    T("흐르는 화면을 눌러 닫힌다", !overlay());
  }
  {
    const { w, tap, overlay } = boot();
    tap(TAPS_NEEDED);
    overlay().querySelector("[data-credits-close]")
      .dispatchEvent(new w.Event("click", { bubbles: true }));
    T("닫기 버튼으로 닫힌다", !overlay());
  }
  {
    /* 닫힌 뒤에도 화면이 잠긴 채 남으면 아무것도 못 하게 된다. */
    const { d, tap, press } = boot();
    tap(TAPS_NEEDED);
    const lockedWhileOpen = d.body.classList.contains("credits-open");
    press("Escape");
    T("열려 있는 동안만 뒤쪽 스크롤을 잠근다",
      lockedWhileOpen && !d.body.classList.contains("credits-open"));
  }

  /* ── 흐르고, 끝나면 스스로 닫힌다 ── */
  {
    const { w, tap, overlay, context, runFrame } = boot({ canvas: true, reducedMotion: false });
    const timing = w.AllMyTripsCredits.timing;

    tap(TAPS_NEEDED);
    T("canvas를 쓸 수 있으면 흐르는 길로 간다", overlay().classList.contains("is-canvas"));

    /* 첫 프레임은 시각 0으로 온다. 이때를 시작으로 잡는다. */
    runFrame(0);
    T("시작하는 순간에는 이름이 아직 화면 아래에 있다", !context.drawn.includes("허민재"));

    context.drawn.length = 0;
    runFrame(timing.rollMs * 0.5);
    T("흐르는 중간에는 이름이 canvas에 올라간다",
      context.drawn.includes("허민재") && context.drawn.includes("한성주"));

    context.drawn.length = 0;
    runFrame(timing.rollMs * 0.8);
    T("마지막 인사와 버전이 뒤따라 올라간다",
      context.drawn.includes("다음 여행에서 또 만나요") && context.drawn.includes("v0.0.5"));
    T("인사는 이름 뒤, 저작권 줄 앞에 온다",
      context.drawn.indexOf("다음 여행에서 또 만나요") > context.drawn.indexOf("한성주")
        && context.drawn.indexOf("다음 여행에서 또 만나요")
          < context.drawn.indexOf("© 2026 All My Trips"));
  }
  {
    const { w, tap, overlay, runFrame } = boot({ canvas: true, reducedMotion: false });
    const timing = w.AllMyTripsCredits.timing;

    tap(TAPS_NEEDED);
    runFrame(0);
    T("끝나기 전에는 닫히지 않는다", !!overlay());

    runFrame(timing.rollMs + timing.finaleMs + 1);
    T("이름이 다 올라가고 비행기가 떠나면 스스로 닫힌다", !overlay());
  }
  {
    const { tap, press, runFrame, frames, cancelled } = boot({ canvas: true, reducedMotion: false });
    tap(TAPS_NEEDED);
    runFrame(0);
    runFrame(200);
    press("Escape");
    T("닫고 나면 그리기를 멈춘다", cancelled.length === 1 && frames.size === 0);
  }

  /* ── 초점 ── */
  {
    const { d, tap, overlay, press } = boot({ canvas: true });
    const button = d.createElement("button");
    d.body.appendChild(button);
    button.focus();

    tap(TAPS_NEEDED);
    const movedIn = d.activeElement === overlay().querySelector("[data-credits-close]");
    press("Escape");
    T("열면 닫기 버튼으로, 닫으면 누르던 자리로 초점이 돌아온다",
      movedIn && d.activeElement === button);
  }
  {
    const { w, d, tap, overlay } = boot({ canvas: true });
    tap(TAPS_NEEDED);

    const event = new w.KeyboardEvent("keydown", { key: "Tab", bubbles: true, cancelable: true });
    d.dispatchEvent(event);
    T("Tab을 눌러도 초점이 뒤쪽 화면으로 새지 않는다",
      event.defaultPrevented
        && d.activeElement === overlay().querySelector("[data-credits-close]"));
  }
  {
    /* canvas 글자는 읽어주는 프로그램에 잡히지 않는다. 같은 내용이 글자로도 있어야 한다. */
    const { tap, overlay } = boot({ canvas: true });
    tap(TAPS_NEEDED);
    const track = overlay().querySelector("[data-credits-track]");
    T("흐르는 동안에도 이름이 글자로 남아 있다",
      !!track && ["허민재", "정인길", "홍유원", "남현호", "한성주"]
        .every((name) => track.textContent.includes(name)));
    T("그림은 읽어주는 프로그램에서 빼 둔다",
      overlay().querySelector("[data-credits-canvas]").getAttribute("aria-hidden") === "true");
  }

  /* ── 버전은 글자로만 남는다 ── */
  {
    const version = '<img src=x onerror="window.__pwned=1">';
    const { w, tap, overlay } = boot({ version });
    tap(TAPS_NEEDED);

    T("관리자가 넣은 버전 값이 마크업으로 살아나지 않는다",
      overlay().querySelectorAll("img").length === 0 && w.__pwned === undefined);
    T("버전은 글자 그대로 보인다",
      overlay().querySelector("[data-credits-version]").textContent === version);
  }

  /* ── 흐르지 않는 길 ── */
  {
    const { tap, overlay } = boot({ reducedMotion: true, canvas: true });
    tap(TAPS_NEEDED);
    T("움직임을 줄이면 흐르지 않고 펼쳐 둔다",
      overlay().classList.contains("is-static") && !overlay().classList.contains("is-canvas"));
  }
  {
    /* canvas가 없는 낡은 환경에서도 이름은 보여야 한다. */
    const { tap, overlay } = boot({ canvas: false });
    tap(TAPS_NEEDED);
    T("canvas를 쓸 수 없으면 글자로 펼쳐 둔다", overlay().classList.contains("is-static"));
    T("그때도 이름과 인사는 다 보인다",
      ["허민재", "정인길", "홍유원", "남현호", "한성주", "다음 여행에서 또 만나요"]
        .every((text) => overlay().textContent.includes(text)));
  }
  {
    const { tap, overlay } = boot({ reducedMotion: true, canvas: true });
    tap(TAPS_NEEDED);
    T("흐르지 않을 때는 사람이 닫을 때까지 기다린다", !!overlay());
  }

  /* ── 자리와 시점을 정하는 계산 ── */
  {
    const { w } = boot();
    const credits = w.AllMyTripsCredits;
    const laid = credits.measure(credits.lines);

    T("줄은 위에서 아래로 겹치지 않게 쌓인다",
      laid.lines.every((line, i) => i === 0 || line.y > laid.lines[i - 1].y));
    T("글 뭉치 높이는 마지막 줄보다 아래다",
      laid.height > laid.lines[laid.lines.length - 1].y);
    T("이름 사이보다 항목 사이가 더 벌어진다",
      laid.lines[3].y - laid.lines[2].y < laid.lines[4].y - laid.lines[3].y);
  }
  {
    const { w } = boot();
    const { phaseAt } = w.AllMyTripsCredits;

    T("처음에는 이름이 올라간다", phaseAt(0, 1000, 200).name === "roll");
    T("이름이 다 올라가면 비행기가 떠난다", phaseAt(1000, 1000, 200).name === "finale");
    T("비행기까지 떠나면 끝이다", phaseAt(1201, 1000, 200).name === "done");
    T("대목 안에서 진행도는 0에서 1로 간다",
      phaseAt(500, 1000, 200).t === 0.5 && phaseAt(1100, 1000, 200).t === 0.5);
  }
  {
    const { w } = boot();
    const { planeAt } = w.AllMyTripsCredits;
    const start = planeAt(0, 1000, 800);
    const end = planeAt(1, 1000, 800);

    T("비행기는 왼쪽 화면 밖에서 들어온다", start.x < 0);
    T("비행기는 오른쪽 화면 밖으로 나간다", end.x > 1000);
    T("비행기는 아래에서 위로 떠난다", end.y < start.y);
    T("기수는 가는 쪽을 향한다", Math.abs(planeAt(0.5, 1000, 800).angle) < Math.PI / 2);
  }

  /* ── 푸터가 없는 화면 ── */
  {
    const dom = new JSDOM("<!doctype html><html><body></body></html>",
      { url: "http://localhost/", runScripts: "outside-only" });
    let broke = false;
    try { dom.window.eval(fs.readFileSync(CREDITS_JS, "utf8")); } catch (error) { broke = true; }
    T("푸터가 없는 화면에서도 조용히 지나간다", !broke);
  }

  /* ── 콘솔 인사 ── */
  {
    const dom = new JSDOM("<!doctype html><html><body>" + FOOTER + "</body></html>",
      { url: "http://localhost/", runScripts: "outside-only" });
    const w = dom.window;
    const lines = [];
    w.console = { log: (...args) => lines.push(args.join(" ")) };
    w.eval(fs.readFileSync(GREETING_JS, "utf8"));
    const printed = lines.join("\n");

    T("저장소 주소를 알려준다",
      printed.includes("github.com/heopath/TravelGuide-Project-Team1"));
    T("푸터에 찍힌 버전을 함께 보여준다", printed.includes("v0.0.5"));
    T("콘솔에 코드를 붙여넣지 말라고 경고한다",
      printed.includes("붙여넣") && printed.includes("자격"));
    T("인사는 화면에 아무것도 그리지 않는다", w.document.body.children.length === 1);
  }
  {
    /* 콘솔이 없는 환경에서 인사하다 스크립트가 멈추면 본말이 전도된다. */
    const dom = new JSDOM("<!doctype html><html><body>" + FOOTER + "</body></html>",
      { url: "http://localhost/", runScripts: "outside-only" });
    dom.window.console = undefined;
    let broke = false;
    try { dom.window.eval(fs.readFileSync(GREETING_JS, "utf8")); } catch (error) { broke = true; }
    T("콘솔이 없으면 조용히 지나간다", !broke);
  }

  console.log("\n" + passed + " passed, " + failed + " failed");
  if (failed > 0) process.exit(1);
}

run();
