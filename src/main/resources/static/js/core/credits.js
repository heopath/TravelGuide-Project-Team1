/* 푸터 버전 번호를 다섯 번 누르면 흐르는 팀 크레딧
 *
 * 다섯 명이 넉 달 붙어 만든 서비스인데, 화면 어디에도 만든 사람 이름이 없었다.
 * 소개 페이지를 따로 두면 손님에게는 쓸데없는 메뉴가 하나 느는 셈이라, 찾는 사람만
 * 찾도록 푸터의 저작권 줄 뒤에 숨긴다.
 *
 * 실수로 열릴 일은 없다. 1.5초 안에 다섯 번을 이어 눌러야 하고, 그렇게 누를 이유가
 * 있는 사람은 이미 무언가를 기대하고 누르는 사람이다.
 *
 * ── 왜 canvas인가
 *
 * 영화 엔딩처럼 이름이 올라가고, 다 올라가면 종이비행기가 밤하늘을 가로질러 떠나며
 * 끝난다. 별이 깜빡이고 비행기가 곡선을 그리며 꼬리를 남기는 그림은 DOM 요소를
 * 수백 개 만들어 옮기는 것보다 canvas에 매 프레임 그리는 편이 간단하고 가볍다.
 * 기록 지면(pages/trips/record-book.js)에서 이미 쓰던 방식이다.
 *
 * 대신 canvas에 그린 글자는 읽어주는 프로그램에 잡히지 않는다. 그래서 같은 내용을
 * 글자로도 두고(.credits-track), canvas가 도는 동안에는 눈에만 안 보이게 감춘다.
 *
 * ── 움직이지 않는 길
 *
 * 움직임을 줄이는 설정을 쓰거나 canvas를 쓸 수 없는 환경이면 흐르지 않고 한 화면에
 * 펼쳐 둔다. 그때는 스스로 닫지도 않는다 — 흐름이 없으니 끝나는 시점도 없다.
 */
(function footerCredits() {
  "use strict";

  /* 이어 눌러야 하는 횟수와, 세던 것을 잊는 간격. */
  var TAPS_NEEDED = 5;
  var TAP_GAP_MS = 1500;

  /* 이름이 다 올라가는 데 걸리는 시간과, 그 뒤 비행기가 떠나는 데 걸리는 시간. */
  var ROLL_MS = 15000;
  var FINALE_MS = 3200;
  /* 마지막 600ms는 어둠에 덮으며 닫는다. 갑자기 사라지면 끊긴 것처럼 보인다. */
  var FADE_MS = 600;

  var SKY_TOP = "#16224d";
  var SKY_BOTTOM = "#070d20";

  /* 크레딧에 오르는 줄. 종류마다 크기·색·앞 여백이 다르다. */
  var LINES = [
    { text: "All My Trips", kind: "brand" },
    { text: "여행의 모든 것, 마이티와 함께", kind: "tagline" },
    { text: "팀장", kind: "role" },
    { text: "허민재", kind: "name" },
    { text: "만든 사람들", kind: "role" },
    { text: "정인길", kind: "name" },
    { text: "홍유원", kind: "name" },
    { text: "남현호", kind: "name" },
    { text: "한성주", kind: "name" },
    /* 마지막 인사. 떠나는 종이비행기에 뜻을 붙여 주는 줄이라 저작권 줄보다 앞에 온다. */
    { text: "다음 여행에서 또 만나요", kind: "farewell" },
    { text: "© 2026 All My Trips", kind: "tail" },
  ];

  var STYLES = {
    brand: { size: 40, weight: 800, color: "#ffffff", gapBefore: 0 },
    tagline: { size: 16, weight: 400, color: "#aeb7ff", gapBefore: 18 },
    role: { size: 13, weight: 500, color: "#8891a8", gapBefore: 74 },
    name: { size: 26, weight: 700, color: "#ffffff", gapBefore: 18 },
    farewell: { size: 18, weight: 500, color: "#aeb7ff", gapBefore: 84 },
    tail: { size: 13, weight: 400, color: "#68728a", gapBefore: 62 },
    /* 버전은 저작권 줄과 같은 모양이되 바로 아래 붙는다. */
    version: { size: 13, weight: 400, color: "#68728a", gapBefore: 10 },
  };

  function font(weight, size) {
    return weight + " " + size + "px Pretendard, -apple-system, BlinkMacSystemFont, system-ui, sans-serif";
  }

  /* ────────────────────────────────────────────────────────────
   * 계산. 그리기와 떼어 두어 canvas 없이도 검사할 수 있게 한다.
   * ──────────────────────────────────────────────────────────── */

  /**
   * 줄 목록을 세로로 쌓아 y 좌표를 매긴다.
   *
   * 좌표는 글 뭉치의 맨 위를 0으로 잡은 값이다. 화면에 올릴 때 흐른 만큼 빼면 된다.
   */
  function measure(lines) {
    var y = 0;
    var placed = lines.map(function (line) {
      var style = STYLES[line.kind] || STYLES.name;
      y += style.gapBefore + style.size;
      return { text: line.text, kind: line.kind, y: y };
    });
    return { lines: placed, height: y + 40 };
  }

  /**
   * 흐른 시간이 어느 대목인지 알려준다.
   *
   * roll — 이름이 올라가는 중. finale — 비행기가 떠나는 중. done — 닫을 때.
   * t는 그 대목 안에서의 진행도(0~1)다.
   */
  function phaseAt(elapsed, rollMs, finaleMs) {
    if (elapsed < rollMs) return { name: "roll", t: elapsed / rollMs };
    if (elapsed < rollMs + finaleMs) return { name: "finale", t: (elapsed - rollMs) / finaleMs };
    return { name: "done", t: 1 };
  }

  /**
   * 종이비행기의 자리와 기울기. t는 0(왼쪽 아래)에서 1(오른쪽 위 바깥)까지다.
   *
   * 곧게 가면 종이비행기로 보이지 않는다. 이차 베지에로 한 번 솟았다 빠지는 길을
   * 그리고, 기울기는 그 길의 접선에서 얻는다 — 그래야 기수가 가는 쪽을 향한다.
   */
  function planeAt(t, width, height) {
    var from = { x: -90, y: height * 0.82 };
    var control = { x: width * 0.45, y: height * 0.08 };
    var to = { x: width + 120, y: height * 0.16 };

    var inverse = 1 - t;
    var x = inverse * inverse * from.x + 2 * inverse * t * control.x + t * t * to.x;
    var y = inverse * inverse * from.y + 2 * inverse * t * control.y + t * t * to.y;

    /* 접선 = 미분. 두 점을 다시 재지 않고 기울기를 바로 얻는다. */
    var dx = 2 * inverse * (control.x - from.x) + 2 * t * (to.x - control.x);
    var dy = 2 * inverse * (control.y - from.y) + 2 * t * (to.y - control.y);

    return { x: x, y: y, angle: Math.atan2(dy, dx) };
  }

  /* 별자리가 매 프레임 달라지면 하늘이 끓는다. 씨앗을 고정해 같은 자리에 둔다. */
  function seeded(seed) {
    var s = seed >>> 0;
    return function () {
      s = (s * 1664525 + 1013904223) >>> 0;
      return s / 4294967296;
    };
  }

  function makeStars(width, height) {
    var rand = seeded(20260824);
    var count = Math.round((width * height) / 9000);
    var stars = [];
    for (var i = 0; i < count; i++) {
      stars.push({
        x: rand() * width,
        y: rand() * height,
        radius: 0.4 + rand() * 1.3,
        phase: rand() * Math.PI * 2,
      });
    }
    return stars;
  }

  /* 검사에서 쓰라고 계산만 내놓는다. 그리기는 화면에서만 뜻이 있다. */
  window.AllMyTripsCredits = {
    lines: LINES,
    measure: measure,
    phaseAt: phaseAt,
    planeAt: planeAt,
    timing: { rollMs: ROLL_MS, finaleMs: FINALE_MS, fadeMs: FADE_MS },
  };

  /* ────────────────────────────────────────────────────────────
   * 화면
   * ──────────────────────────────────────────────────────────── */

  var badge = document.querySelector(".footer-version");
  if (!badge) return;

  var taps = 0;
  var lastTapAt = 0;
  var overlay = null;
  var restoreFocusTo = null;
  var animation = null;

  badge.addEventListener("click", function countTap() {
    var now = Date.now();
    taps = now - lastTapAt > TAP_GAP_MS ? 1 : taps + 1;
    lastTapAt = now;
    if (taps < TAPS_NEEDED) return;

    taps = 0;
    open();
  });

  /*
   * 움직임을 줄이는 설정은 열 때마다 다시 본다. 도중에 바꿀 수 있어서다.
   */
  function prefersReducedMotion() {
    return typeof window.matchMedia === "function"
      && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  }

  function build() {
    var root = document.createElement("div");
    root.className = "credits-roll";
    root.setAttribute("role", "dialog");
    root.setAttribute("aria-modal", "true");
    root.setAttribute("aria-label", "만든 사람들");
    root.innerHTML = ""
      + '<canvas class="credits-canvas" data-credits-canvas aria-hidden="true"></canvas>'
      + '<div class="credits-track" data-credits-track>'
      + '<p class="credits-brand">All My Trips</p>'
      + '<p class="credits-tagline">여행의 모든 것, 마이티와 함께</p>'
      + '<p class="credits-role">팀장</p>'
      + '<p class="credits-name">허민재</p>'
      + '<p class="credits-role">만든 사람들</p>'
      + '<p class="credits-name">정인길</p>'
      + '<p class="credits-name">홍유원</p>'
      + '<p class="credits-name">남현호</p>'
      + '<p class="credits-name">한성주</p>'
      + '<p class="credits-farewell">다음 여행에서 또 만나요</p>'
      + '<p class="credits-tail">© 2026 All My Trips</p>'
      + '<p class="credits-tail" data-credits-version></p>'
      + "</div>"
      + '<button type="button" class="credits-close" data-credits-close>닫기</button>';

    /*
     * 버전은 관리자 화면에서 고칠 수 있는 값이라 서버를 거쳐 들어온다. 마크업에
     * 끼워 넣지 않고 글자로만 넣는다.
     */
    root.querySelector("[data-credits-version]").textContent = version();

    return root;
  }

  function version() {
    return badge.textContent ? badge.textContent.trim() : "";
  }

  /**
   * canvas에 크레딧을 흘린다. 그릴 수 없는 환경이면 null을 돌려준다.
   *
   * 돌려주는 것은 멈추는 함수 하나다. 닫을 때 이것만 부르면 다음 프레임이 예약되지
   * 않는다 — 닫힌 뒤에도 계속 도는 그림은 배터리만 먹는다.
   */
  function animate(canvas, onFinish) {
    var context = typeof canvas.getContext === "function" ? canvas.getContext("2d") : null;
    if (!context) return null;
    if (typeof window.requestAnimationFrame !== "function") return null;

    var width = 0;
    var height = 0;
    var stars = [];
    var content = measure(LINES.concat([{ text: version(), kind: "version" }]));
    var startedAt = null;
    var frameId = 0;

    function resize() {
      var ratio = window.devicePixelRatio || 1;
      width = canvas.clientWidth || window.innerWidth;
      height = canvas.clientHeight || window.innerHeight;
      canvas.width = Math.round(width * ratio);
      canvas.height = Math.round(height * ratio);
      context.setTransform(ratio, 0, 0, ratio, 0, 0);
      stars = makeStars(width, height);
    }

    function drawSky(elapsed) {
      var sky = context.createLinearGradient(0, 0, 0, height);
      sky.addColorStop(0, SKY_TOP);
      sky.addColorStop(1, SKY_BOTTOM);
      context.fillStyle = sky;
      context.fillRect(0, 0, width, height);

      context.fillStyle = "#ffffff";
      for (var i = 0; i < stars.length; i++) {
        var star = stars[i];
        /* 다 같이 깜빡이면 신호등처럼 보인다. 별마다 시작을 어긋나게 둔다. */
        context.globalAlpha = 0.25 + 0.45 * (0.5 + 0.5 * Math.sin(elapsed / 900 + star.phase));
        context.beginPath();
        context.arc(star.x, star.y, star.radius, 0, Math.PI * 2);
        context.fill();
      }
      context.globalAlpha = 1;
    }

    /** 글 뭉치를 offset만큼 끌어올려 그린다. */
    function drawLines(offset) {
      context.textAlign = "center";
      context.textBaseline = "alphabetic";

      for (var i = 0; i < content.lines.length; i++) {
        var line = content.lines[i];
        var y = height + line.y - offset;
        /* 화면 밖 글자는 그리지 않는다. */
        if (y < -60 || y > height + 60) continue;

        var style = STYLES[line.kind] || STYLES.name;
        context.font = font(style.weight, style.size);
        context.fillStyle = style.color;
        context.fillText(line.text, width / 2, y);
      }
    }

    /** 떠나는 종이비행기와 그 꼬리. */
    function drawPlane(t) {
      var spot = planeAt(t, width, height);

      /* 꼬리. 지나온 길을 점선으로 남긴다. */
      context.save();
      context.strokeStyle = "rgba(174,183,255,.45)";
      context.lineWidth = 1.5;
      context.setLineDash([5, 7]);
      context.beginPath();
      for (var step = 0; step <= 40; step++) {
        var past = planeAt((step / 40) * t, width, height);
        if (step === 0) context.moveTo(past.x, past.y);
        else context.lineTo(past.x, past.y);
      }
      context.stroke();
      context.restore();

      /* 몸체. 마스코트 머리 위를 도는 그 종이비행기와 같은 모양이다. */
      context.save();
      context.translate(spot.x, spot.y);
      context.rotate(spot.angle);
      context.fillStyle = "#ffffff";
      context.beginPath();
      context.moveTo(18, 0);
      context.lineTo(-14, -11);
      context.lineTo(-8, 0);
      context.lineTo(-14, 11);
      context.closePath();
      context.fill();

      context.fillStyle = "#aeb7ff";
      context.beginPath();
      context.moveTo(18, 0);
      context.lineTo(-8, 0);
      context.lineTo(-14, 11);
      context.closePath();
      context.fill();
      context.restore();
    }

    function draw(elapsed) {
      var phase = phaseAt(elapsed, ROLL_MS, FINALE_MS);
      /* 이름이 다 지나가려면 화면 높이만큼 더 올라가야 한다. */
      var distance = height + content.height;

      drawSky(elapsed);
      drawLines(distance * Math.min(phase.name === "roll" ? phase.t : 1, 1));

      if (phase.name === "finale") {
        drawPlane(Math.min(phase.t * 1.15, 1));

        /* 끝머리는 어둠에 덮어 닫는다. 뚝 끊기면 오류처럼 보인다. */
        var remaining = FINALE_MS * (1 - phase.t);
        if (remaining < FADE_MS) {
          context.globalAlpha = 1 - remaining / FADE_MS;
          context.fillStyle = SKY_BOTTOM;
          context.fillRect(0, 0, width, height);
          context.globalAlpha = 1;
        }
      }

      return phase;
    }

    function frame(now) {
      /* 0도 정당한 시각이다. !startedAt으로 보면 그때마다 시작을 다시 잡아 끝나지 않는다. */
      if (startedAt === null) startedAt = now;
      var phase = draw(now - startedAt);

      if (phase.name === "done") {
        onFinish();
        return;
      }
      frameId = window.requestAnimationFrame(frame);
    }

    resize();
    window.addEventListener("resize", resize);
    frameId = window.requestAnimationFrame(frame);

    return function stop() {
      window.removeEventListener("resize", resize);
      if (frameId && typeof window.cancelAnimationFrame === "function") {
        window.cancelAnimationFrame(frameId);
      }
    };
  }

  function open() {
    if (overlay) return;

    restoreFocusTo = document.activeElement;
    overlay = build();
    document.body.appendChild(overlay);
    document.body.classList.add("credits-open");

    overlay.addEventListener("click", function (event) {
      var onCanvas = event.target.hasAttribute && event.target.hasAttribute("data-credits-canvas");
      var onBackdrop = event.target === overlay || onCanvas;
      var onCloseButton = event.target.closest && event.target.closest("[data-credits-close]");
      if (onBackdrop || onCloseButton) close();
    });

    if (!prefersReducedMotion()) {
      animation = animate(overlay.querySelector("[data-credits-canvas]"), close);
    }

    /*
     * 흐르지 않기로 했거나 canvas를 쓸 수 없으면 글자를 한 화면에 펼쳐 두고,
     * 닫는 것도 사람에게 맡긴다.
     */
    overlay.classList.add(animation ? "is-canvas" : "is-static");

    var closeButton = overlay.querySelector("[data-credits-close]");
    if (closeButton) closeButton.focus();
    document.addEventListener("keydown", onKeydown, true);
  }

  function close() {
    if (!overlay) return;

    if (animation) animation();
    animation = null;

    document.removeEventListener("keydown", onKeydown, true);
    overlay.remove();
    overlay = null;
    document.body.classList.remove("credits-open");

    if (restoreFocusTo && typeof restoreFocusTo.focus === "function") restoreFocusTo.focus();
    restoreFocusTo = null;
  }

  function onKeydown(event) {
    if (event.key === "Escape") {
      /* app.js도 Escape를 듣고 모달을 닫는다. 크레딧을 닫으려고 누른 키가 뒤쪽
       * 화면의 모달까지 건드리지 않게 여기서 멈춘다. */
      event.stopPropagation();
      close();
      return;
    }

    /* 초점이 크레딧 뒤 화면으로 새지 않게 붙잡는다. 누를 것은 닫기 하나뿐이다. */
    if (event.key === "Tab") {
      event.preventDefault();
      var closeButton = overlay.querySelector("[data-credits-close]");
      if (closeButton) closeButton.focus();
    }
  }
})();
