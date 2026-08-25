/*
 * 여행 기록을 책의 한 지면으로 조판한다.
 *
 * 왼쪽은 사진, 오른쪽은 글이다. 사진이 몇 장 올지 미리 알 수 없으므로 배치를
 * 좌표로 박아두지 않고 장수와 가로세로 비율을 보고 고른다(pickLayout).
 *
 * 내보내기(toBlob)는 canvas가 "오염"되면 막힌다. 다른 도메인 사진을 그냥 그리면
 * 브라우저가 오염으로 표시하기 때문에, 허락(CORS)을 받은 사진만 그리고 나머지는
 * 빈 자리로 남긴다. 사진 한 장 때문에 저장 전체가 막히는 편보다 낫다.
 */
(function () {
  "use strict";

  var W = 2480;
  var H = 1748;
  var MARGIN = 120;
  var GAP = 24;

  var PAPER = "#ffffff";
  var BOARD = "#eef1ff";
  var INK = "#1b2540";
  var BODY = "#4f5b73";
  var MUTED = "#778198";
  var EDGE = "#e0e6f0";
  var CORNER = "#dfe3ff";
  var GOLD = "#5c68ff";

  function font(weight, size) {
    return weight + " " + size + "px Pretendard, -apple-system, BlinkMacSystemFont, system-ui, sans-serif";
  }

  function roundedPath(ctx, x, y, w, h, radius) {
    var r = Math.min(radius, w / 2, h / 2);
    ctx.beginPath();
    if (typeof ctx.roundRect === "function") {
      ctx.roundRect(x, y, w, h, r);
      return;
    }
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  /* 종이 결. 매번 달라지면 다시 그릴 때 지면이 흔들려 보이므로 씨앗을 고정한다. */
  function seeded(seed) {
    var s = seed >>> 0;
    return function () {
      s = (s * 1664525 + 1013904223) >>> 0;
      return s / 4294967296;
    };
  }

  function grain(ctx, x, y, w, h, seed) {
    var rand = seeded(seed);
    ctx.save();

    /* 낱알. 종이 표면의 오돌토돌한 결이다. */
    ctx.globalAlpha = 0.026;
    ctx.fillStyle = "#6674a5";
    var count = Math.round((w * h) / 1400);
    for (var i = 0; i < count; i++) {
      ctx.fillRect(x + rand() * w, y + rand() * h, 2 + rand() * 2, 2);
    }

    /* 섬유. 낱알만으로는 모래처럼 보여서, 결을 따라 흐르는 실선을 섞는다. */
    ctx.globalAlpha = 0.018;
    ctx.strokeStyle = "#7784b1";
    ctx.lineWidth = 1;
    var fibers = Math.round(w / 12);
    for (var f = 0; f < fibers; f++) {
      var fx = x + rand() * w;
      var fy = y + rand() * h;
      var len = 30 + rand() * 130;
      var tilt = (rand() - 0.5) * 16;
      ctx.beginPath();
      ctx.moveTo(fx, fy);
      ctx.lineTo(fx + len, fy + tilt);
      ctx.stroke();
    }

    /* 가장자리 그늘. 종이가 판에 얹혀 있는 두께감을 만든다. */
    ctx.globalAlpha = 1;
    var edge = 90;
    [[x, y, w, edge, 0, 1], [x, y + h - edge, w, edge, 0, -1],
     [x, y, edge, h, 1, 0], [x + w - edge, y, edge, h, -1, 0]].forEach(function (band) {
      var gx = band[4], gy = band[5];
      var g = ctx.createLinearGradient(
        band[0] + (gx < 0 ? band[2] : 0), band[1] + (gy < 0 ? band[3] : 0),
        band[0] + (gx < 0 ? 0 : gx ? band[2] : 0), band[1] + (gy < 0 ? 0 : gy ? band[3] : 0)
      );
      g.addColorStop(0, "rgba(72,84,138,0.045)");
      g.addColorStop(1, "rgba(72,84,138,0)");
      ctx.fillStyle = g;
      ctx.fillRect(band[0], band[1], band[2], band[3]);
    });

    ctx.restore();
  }

  /* 사진 모서리를 붙잡는 삼각 고정대. 사진첩에서 쓰던 그 방식이다. */
  function corners(ctx, x, y, w, h) {
    var size = Math.min(46, w * 0.12, h * 0.12);
    ctx.fillStyle = CORNER;
    [[x, y, 1, 1], [x + w, y, -1, 1], [x, y + h, 1, -1], [x + w, y + h, -1, -1]].forEach(function (c) {
      ctx.beginPath();
      ctx.moveTo(c[0], c[1]);
      ctx.lineTo(c[0] + size * c[2], c[1]);
      ctx.lineTo(c[0], c[1] + size * c[3]);
      ctx.closePath();
      ctx.fill();
    });
  }

  /* 칸을 꽉 채우되 비율은 지킨다. 넘치는 부분은 잘라낸다. */
  function drawCover(ctx, img, x, y, w, h) {
    var scale = Math.max(w / img.naturalWidth, h / img.naturalHeight);
    var dw = img.naturalWidth * scale;
    var dh = img.naturalHeight * scale;
    ctx.save();
    roundedPath(ctx, x, y, w, h, 22);
    ctx.clip();
    ctx.drawImage(img, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh);
    ctx.restore();
  }

  function emptyFrame(ctx, x, y, w, h, message) {
    roundedPath(ctx, x, y, w, h, 22);
    ctx.fillStyle = "#f3f5ff";
    ctx.fill();
    ctx.strokeStyle = EDGE;
    ctx.lineWidth = 2;
    ctx.stroke();
    if (!message) return;
    ctx.fillStyle = MUTED;
    ctx.font = font(400, 30);
    ctx.textAlign = "center";
    ctx.fillText(message, x + w / 2, y + h / 2 + 10);
    ctx.textAlign = "left";
  }

  /*
   * 사진 배치를 고른다. 좌표를 박아두지 않고 장수로 규칙을 정한 뒤,
   * 한 장뿐일 때는 그 사진이 가로인지 세로인지까지 본다.
   *
   * 비율(firstRatio)은 가로/세로다. 1보다 작으면 세로 사진이다.
   */
  function pickLayout(count, firstRatio) {
    if (count <= 0) return { name: "none", cells: [] };
    if (count === 1) {
      return firstRatio && firstRatio < 0.95
        ? { name: "single-portrait", cells: [[0.12, 0, 0.76, 1]] }
        : { name: "single-landscape", cells: [[0, 0.12, 1, 0.76]] };
    }
    if (count === 2) {
      return { name: "stack", cells: [[0, 0, 1, 0.49], [0, 0.51, 1, 0.49]] };
    }
    if (count === 3) {
      return { name: "hero", cells: [[0, 0, 1, 0.62], [0, 0.64, 0.49, 0.36], [0.51, 0.64, 0.49, 0.36]] };
    }
    if (count === 4) {
      return {
        name: "quad",
        cells: [[0, 0, 0.49, 0.49], [0.51, 0, 0.49, 0.49], [0, 0.51, 0.49, 0.49], [0.51, 0.51, 0.49, 0.49]]
      };
    }
    if (count <= 6) {
      return {
        name: "six",
        cells: [
          [0, 0, 0.66, 0.49], [0.68, 0, 0.32, 0.235], [0.68, 0.255, 0.32, 0.235],
          [0, 0.51, 0.32, 0.49], [0.34, 0.51, 0.32, 0.49], [0.68, 0.51, 0.32, 0.49]
        ]
      };
    }
    var cells = [];
    for (var r = 0; r < 3; r++) {
      for (var c = 0; c < 3; c++) {
        cells.push([c * 0.345, r * 0.345, 0.31, 0.31]);
      }
    }
    return { name: "grid9", cells: cells };
  }

  /*
   * 글을 지면 폭에 맞춰 흘린다. 넘치면 말줄임으로 끊는다.
   *
   * indent(줄번호)로 줄마다 시작점과 폭을 바꿀 수 있다. 드롭캡 옆을 감싸 흐르게
   * 하려고 둔 자리다. 주지 않으면 모든 줄이 같은 폭으로 흐른다.
   */
  function flow(ctx, text, x, y, maxWidth, lineHeight, maxLines, indent) {
    var paragraphs = String(text || "").split(/\n+/);
    var line = 0;

    function box(n) {
      var shift = indent ? indent(n) : null;
      return { x: x + (shift ? shift.dx : 0), width: maxWidth - (shift ? shift.dx : 0) };
    }

    for (var p = 0; p < paragraphs.length && line < maxLines; p++) {
      var words = paragraphs[p].split(/\s+/).filter(Boolean);
      var current = "";
      for (var i = 0; i < words.length; i++) {
        var next = current ? current + " " + words[i] : words[i];
        if (ctx.measureText(next).width > box(line).width && current) {
          ctx.fillText(current, box(line).x, y + line * lineHeight);
          line++;
          current = words[i];
          if (line >= maxLines) {
            ctx.fillText(current.slice(0, 24) + "…", box(line - 1).x, y + (line - 1) * lineHeight);
            return line;
          }
        } else {
          current = next;
        }
      }
      if (current && line < maxLines) {
        ctx.fillText(current, box(line).x, y + line * lineHeight);
        line++;
      }
    }
    return line;
  }

  /*
   * 드롭캡. 첫 글자를 크게 놓고 본문이 그 옆을 감싸 흐르게 한다.
   *
   * 글자 크기는 줄 높이의 배수로 잡는다. 그래야 몇 줄을 차지할지 정해지고,
   * 본문이 그만큼만 들여쓰면 된다. 한글은 글자마다 폭이 달라 실제로 재서 쓴다.
   */
  function dropCap(ctx, text, x, y, lineHeight, lines) {
    var first = String(text || "").trim().charAt(0);
    if (!first) return null;

    var size = Math.round(lineHeight * lines * 0.86);
    ctx.save();
    ctx.font = font(500, size);
    var width = ctx.measureText(first).width;
    ctx.fillStyle = INK;
    /* 큰 글자는 첫 줄 글자와 윗선을 맞춘다. */
    ctx.fillText(first, x, y + size * 0.78);
    ctx.restore();

    return { char: first, width: width, lines: lines, gap: 18 };
  }

  function stars(ctx, x, y, size, gap, filled) {
    for (var i = 0; i < 5; i++) {
      var cx = x + i * (size + gap) + size / 2;
      ctx.fillStyle = i < filled ? GOLD : "#e0d9c8";
      ctx.beginPath();
      for (var j = 0; j < 10; j++) {
        var radius = j % 2 ? size * 0.2 : size * 0.48;
        var angle = (Math.PI / 5) * j - Math.PI / 2;
        var px = cx + Math.cos(angle) * radius;
        var py = y + Math.sin(angle) * radius;
        if (j) ctx.lineTo(px, py); else ctx.moveTo(px, py);
      }
      ctx.closePath();
      ctx.fill();
    }
  }

  /*
   * 미니 지도. 일정에 담은 장소를 순서대로 이어 동선을 그린다.
   *
   * 지도 이미지를 쓰지 않는다. 좌표만으로 그리므로 지도 API 키가 없어도 되고,
   * 지면과 같은 종이 톤을 유지할 수 있다.
   *
   * 위경도를 그대로 쓰면 우리나라 위도에서 가로가 눌린다. 경도 1도가 위도 1도보다
   * 짧기 때문이다. 중간 위도의 코사인을 곱해 보정한다.
   */
  function drawMiniMap(ctx, x, y, w, h, points) {
    /* 종이와 같은 톤이면 묻힌다. 한 단계 짙게 깔고 테두리를 준다. */
    ctx.fillStyle = "#f1f3ff";
    ctx.fillRect(x, y, w, h);

    /* 옅은 모눈. 지도라는 것을 알려주는 최소한의 신호다. */
    ctx.save();
    ctx.beginPath();
    ctx.rect(x, y, w, h);
    ctx.clip();
    ctx.strokeStyle = "rgba(92,104,255,0.14)";
    ctx.lineWidth = 1;
    for (var gx = x + 60; gx < x + w; gx += 60) {
      ctx.beginPath();
      ctx.moveTo(gx, y);
      ctx.lineTo(gx, y + h);
      ctx.stroke();
    }
    for (var gy = y + 60; gy < y + h; gy += 60) {
      ctx.beginPath();
      ctx.moveTo(x, gy);
      ctx.lineTo(x + w, gy);
      ctx.stroke();
    }
    ctx.restore();

    ctx.strokeStyle = "#cfd5ff";
    ctx.lineWidth = 2;
    ctx.strokeRect(x + 1, y + 1, w - 2, h - 2);

    ctx.fillStyle = "#5360c7";
    ctx.font = font(500, 24);
    ctx.fillText("다녀온 길", x + 20, y + 38);

    var usable = (points || []).filter(function (p) {
      return p && isFinite(p.lat) && isFinite(p.lng);
    });
    if (usable.length === 0) {
      ctx.fillStyle = MUTED;
      ctx.font = font(400, 24);
      ctx.textAlign = "center";
      ctx.fillText("일정에 담은 장소가 없어요", x + w / 2, y + h / 2 + 8);
      ctx.textAlign = "left";
      return 0;
    }

    var pad = 46;
    var top = y + 54;
    var innerW = w - pad * 2;
    var innerH = h - (top - y) - pad;

    var lats = usable.map(function (p) { return p.lat; });
    var lngs = usable.map(function (p) { return p.lng; });
    var minLat = Math.min.apply(null, lats), maxLat = Math.max.apply(null, lats);
    var minLng = Math.min.apply(null, lngs), maxLng = Math.max.apply(null, lngs);
    var midLat = (minLat + maxLat) / 2;
    var squeeze = Math.cos((midLat * Math.PI) / 180) || 1;

    var spanX = Math.max((maxLng - minLng) * squeeze, 0.0001);
    var spanY = Math.max(maxLat - minLat, 0.0001);
    /* 가로세로 비율을 지켜야 동선 모양이 찌그러지지 않는다. */
    var scale = Math.min(innerW / spanX, innerH / spanY);
    var drawW = spanX * scale;
    var drawH = spanY * scale;
    var offsetX = x + pad + (innerW - drawW) / 2;
    var offsetY = top + (innerH - drawH) / 2;

    var placed = usable.map(function (p) {
      return {
        label: p.label,
        px: offsetX + (p.lng - minLng) * squeeze * scale,
        /* 위도는 위로 갈수록 커지므로 화면 좌표와 뒤집힌다. */
        py: offsetY + (maxLat - p.lat) * scale
      };
    });

    if (placed.length > 1) {
      ctx.strokeStyle = "#5c68ff";
      ctx.lineWidth = 4;
      ctx.setLineDash([12, 9]);
      ctx.beginPath();
      placed.forEach(function (p, i) {
        if (i) ctx.lineTo(p.px, p.py); else ctx.moveTo(p.px, p.py);
      });
      ctx.stroke();
      ctx.setLineDash([]);
    }

    /*
     * 장소 이름. 번호만 있으면 어디를 다녀온 지면인지 알 수 없다.
     *
     * 여섯 곳이 좁은 칸에 모이면 이름이 서로 겹친다. 이미 놓은 이름과 부딪히면
     * 반대쪽에 붙여 보고, 그래도 부딪히면 그 이름은 포기한다. 번호는 남으므로
     * 순서를 잃지는 않는다.
     */
    var taken = [];
    function collides(box) {
      return taken.some(function (t) {
        return !(box.x + box.w < t.x || box.x > t.x + t.w || box.y + box.h < t.y || box.y > t.y + t.h);
      });
    }

    ctx.font = font(400, 21);
    placed.forEach(function (p) {
      var name = String(p.label || "").trim();
      if (!name) return;
      if (name.length > 11) name = name.slice(0, 10) + "…";
      var textW = ctx.measureText(name).width;

      var options = [
        { x: p.px + 26, y: p.py - 12, align: "left" },
        { x: p.px - 26 - textW, y: p.py - 12, align: "left" },
        { x: p.px + 26, y: p.py + 20, align: "left" },
        { x: p.px - 26 - textW, y: p.py + 20, align: "left" }
      ];

      for (var i = 0; i < options.length; i++) {
        var o = options[i];
        var box = { x: o.x - 6, y: o.y - 20, w: textW + 12, h: 28 };
        if (box.x < x + 8 || box.x + box.w > x + w - 8) continue;
        if (box.y < y + 46 || box.y + box.h > y + h - 8) continue;
        if (collides(box)) continue;

        /* 이름 뒤에 옅은 판을 깔아야 모눈과 동선 위에서도 읽힌다. */
        ctx.fillStyle = "rgba(246,247,255,0.92)";
        ctx.fillRect(box.x, box.y, box.w, box.h);
        ctx.fillStyle = "#4b568e";
        ctx.fillText(name, o.x, o.y);
        taken.push(box);
        return;
      }
    });

    placed.forEach(function (p, i) {
      ctx.fillStyle = "#fdfaf3";
      ctx.beginPath();
      ctx.arc(p.px, p.py, 19, 0, Math.PI * 2);
      ctx.fill();
      ctx.strokeStyle = "#5c68ff";
      ctx.lineWidth = 4;
      ctx.stroke();

      ctx.fillStyle = "#4654e8";
      ctx.font = font(500, 22);
      ctx.textAlign = "center";
      ctx.fillText(String(i + 1), p.px, p.py + 8);
      ctx.textAlign = "left";
    });

    return placed.length;
  }

  function formatPeriod(start, end) {
    var dot = function (value) { return String(value || "").split("-").join("."); };
    if (!start && !end) return "";
    return dot(start) + " – " + dot(end);
  }

  /*
   * 사진을 미리 받아 둔다. 다른 도메인 사진은 허락(CORS)을 받아야 내보내기가 되므로
   * crossOrigin으로만 시도하고, 실패하면 그 자리는 비운다.
   */
  function loadImages(list) {
    return Promise.all((list || []).map(function (item) {
      return new Promise(function (resolve) {
        if (!item || !item.imageUrl) return resolve(null);
        var img = new Image();
        img.crossOrigin = "anonymous";
        img.onload = function () { resolve(img); };
        img.onerror = function () { resolve(null); };
        img.src = item.imageUrl;
      });
    }));
  }

  function drawPhotoPage(ctx, x, y, w, h, loaded, total) {
    var first = null;
    for (var i = 0; i < loaded.length; i++) {
      if (loaded[i]) { first = loaded[i]; break; }
    }
    var ratio = first ? first.naturalWidth / first.naturalHeight : null;
    var layout = pickLayout(loaded.length, ratio);

    /*
     * "외 N장"은 넘치는 사진이 있을 때만 나온다. 자리를 늘 비워 두면 사진이
     * 다 들어간 지면에서도 아래가 허전해진다.
     */
    var hidden = total - layout.cells.length;
    var captionRoom = hidden > 0 ? 56 : 0;
    var boxH = h - captionRoom;

    layout.cells.forEach(function (cell, index) {
      if (index >= loaded.length) return;
      var cx = x + cell[0] * w;
      var cy = y + cell[1] * boxH;
      var cw = cell[2] * w - (cell[2] < 1 ? GAP / 2 : 0);
      var ch = cell[3] * boxH - (cell[3] < 1 ? GAP / 2 : 0);
      var img = loaded[index];
      ctx.save();
      ctx.shadowColor = "rgba(34,53,104,0.16)";
      ctx.shadowBlur = 24;
      ctx.shadowOffsetY = 10;
      roundedPath(ctx, cx - 8, cy - 8, cw + 16, ch + 16, 26);
      ctx.fillStyle = "#ffffff";
      ctx.fill();
      ctx.restore();
      if (img) drawCover(ctx, img, cx, cy, cw, ch);
      else emptyFrame(ctx, cx, cy, cw, ch, "사진을 불러오지 못했어요");
      corners(ctx, cx, cy, cw, ch);
    });

    if (hidden > 0) {
      ctx.fillStyle = MUTED;
      ctx.font = font(400, 30);
      ctx.textAlign = "right";
      ctx.fillText("외 " + hidden + "장", x + w, y + h - 8);
      ctx.textAlign = "left";
    }
    return layout.name;
  }

  function drawTextPage(ctx, x, y, w, h, data) {
    ctx.fillStyle = MUTED;
    ctx.font = font(400, 32);
    ctx.fillText(data.tripTitle || "여행 기록", x, y + 12);

    ctx.strokeStyle = EDGE;
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(x, y + 44);
    ctx.lineTo(x + w, y + 44);
    ctx.stroke();

    ctx.fillStyle = INK;
    ctx.font = font(500, 68);
    var used = flow(ctx, data.title || "제목 없는 기록", x, y + 140, w, 84, 2);

    var cursor = y + 140 + used * 84 + 34;
    if (data.rating > 0) {
      stars(ctx, x, cursor, 40, 12, data.rating);
      cursor += 80;
    } else {
      cursor += 18;
    }

    /*
     * 지도는 지면 아래를 차지한다. 글이 짧으면 오른쪽이 허전한데, 동선이 그
     * 자리를 채우면서 여행의 모양도 함께 보여 준다.
     */
    var mapH = data.route && data.route.length ? 440 : 0;
    var textBottom = y + h - 90 - (mapH ? mapH + 40 : 0);
    var lineHeight = 60;

    /* 서비스의 다른 카드와 같은 본문 위계를 사용해 첫 글자를 과장하지 않는다. */
    ctx.font = font(400, 36);
    var cap = null;
    var rest = data.content || "";

    ctx.fillStyle = BODY;
    ctx.font = font(400, 36);
    var room = Math.max(1, Math.floor((textBottom - cursor) / lineHeight));
    flow(ctx, rest, x, cursor + 34, w, lineHeight, room, cap ? function (n) {
      return n < cap.lines ? { dx: cap.width + cap.gap } : { dx: 0 };
    } : null);

    if (mapH) {
      drawMiniMap(ctx, x, y + h - 90 - mapH, w, mapH, data.route);
    }

    ctx.fillStyle = MUTED;
    ctx.font = font(400, 30);
    ctx.fillText(formatPeriod(data.startDate, data.endDate), x, y + h - 8);
    if (data.destination) {
      ctx.textAlign = "right";
      ctx.fillText(data.destination, x + w, y + h - 8);
      ctx.textAlign = "left";
    }
  }

  function drawSpread(ctx, data, loaded) {
    var backdrop = ctx.createLinearGradient(0, 0, W, H);
    backdrop.addColorStop(0, "#152252");
    backdrop.addColorStop(0.52, "#3349ad");
    backdrop.addColorStop(1, "#6750c6");
    ctx.fillStyle = backdrop;
    ctx.fillRect(0, 0, W, H);

    /* 종이보다 조금 크게 보이는 짙은 표지와 모서리로 한 권의 앨범 두께를 만든다. */
    ctx.save();
    ctx.shadowColor = "rgba(7,13,44,0.45)";
    ctx.shadowBlur = 70;
    ctx.shadowOffsetY = 30;
    roundedPath(ctx, MARGIN - 42, MARGIN - 34, W - MARGIN * 2 + 84, H - MARGIN * 2 + 76, 54);
    var cover = ctx.createLinearGradient(MARGIN, MARGIN, W - MARGIN, H - MARGIN);
    cover.addColorStop(0, "#293779");
    cover.addColorStop(1, "#4f3f9d");
    ctx.fillStyle = cover;
    ctx.fill();
    ctx.restore();

    roundedPath(ctx, MARGIN - 25, MARGIN - 17, W - MARGIN * 2 + 50, H - MARGIN * 2 + 48, 48);
    ctx.strokeStyle = "rgba(220,225,255,0.34)";
    ctx.lineWidth = 3;
    ctx.stroke();

    ctx.save();
    ctx.shadowColor = "rgba(44,55,130,0.18)";
    ctx.shadowBlur = 50;
    ctx.shadowOffsetY = 22;
    roundedPath(ctx, MARGIN, MARGIN, W - MARGIN * 2, H - MARGIN * 2, 42);
    ctx.fillStyle = PAPER;
    ctx.fill();
    ctx.restore();

    roundedPath(ctx, MARGIN, MARGIN, W - MARGIN * 2, H - MARGIN * 2, 42);
    ctx.strokeStyle = "#d9dfff";
    ctx.lineWidth = 2;
    ctx.stroke();
    grain(ctx, MARGIN + 4, MARGIN + 20, W - MARGIN * 2 - 8, H - MARGIN * 2 - 24, 20260825);

    var accent = ctx.createLinearGradient(MARGIN, 0, W - MARGIN, 0);
    accent.addColorStop(0, "#4a73ff");
    accent.addColorStop(1, "#7657ff");
    roundedPath(ctx, MARGIN, MARGIN, W - MARGIN * 2, 18, 9);
    ctx.fillStyle = accent;
    ctx.fill();

    var pad = 90;
    var pageW = (W - MARGIN * 2) / 2;
    var innerW = pageW - pad * 2;
    var innerH = H - MARGIN * 2 - pad * 2;
    var top = MARGIN + pad;

    var layoutName = "none";
    if (loaded.length > 0) {
      layoutName = drawPhotoPage(ctx, MARGIN + pad, top, innerW, innerH, loaded, data.totalImages);
      drawTextPage(ctx, MARGIN + pageW + pad, top, innerW, innerH, data);
    } else {
      /* 사진이 없으면 지면 전체를 글에 내준다. */
      drawTextPage(ctx, MARGIN + pad, top, innerW, innerH, data);
      ctx.fillStyle = MUTED;
      ctx.font = font(400, 32);
      ctx.fillText("사진을 더하면 왼쪽 지면이 채워져요.", MARGIN + pageW + pad, top + 12);
    }

    /*
     * 가운데 접힘. 두 쪽이 한 장에서 이어진다는 느낌을 만든다.
     *
     * 넓게 퍼지는 그늘로 종이가 휘어 들어가는 것을 만들고, 가장 깊은 곳에
     * 접힌 자국 한 줄을 얹는다. 그늘만 있으면 흐릿하고, 선만 있으면 종이를
     * 자른 것처럼 보인다.
     */
    var center = W / 2;
    var top = MARGIN;
    var height = H - MARGIN * 2;

    var wide = ctx.createLinearGradient(center - 150, 0, center + 150, 0);
    wide.addColorStop(0, "rgba(35,45,99,0)");
    wide.addColorStop(0.32, "rgba(35,45,99,0.04)");
    wide.addColorStop(0.5, "rgba(23,31,73,0.16)");
    wide.addColorStop(0.68, "rgba(35,45,99,0.04)");
    wide.addColorStop(1, "rgba(35,45,99,0)");
    ctx.fillStyle = wide;
    ctx.fillRect(center - 150, top, 300, height);

    ctx.strokeStyle = "rgba(37,46,97,0.28)";
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(center, top);
    ctx.lineTo(center, top + height);
    ctx.stroke();

    /* 접힌 자국 바로 옆은 빛을 받아 살짝 밝다. */
    ctx.strokeStyle = "rgba(255,255,255,0.72)";
    ctx.lineWidth = 3;
    [center - 7, center + 7].forEach(function (x) {
      ctx.beginPath();
      ctx.moveTo(x, top);
      ctx.lineTo(x, top + height);
      ctx.stroke();
    });

    return layoutName;
  }

  function dateLabel(value) {
    if (!value) return "";
    var parts = String(value).split("-");
    return parts.length === 3 ? Number(parts[1]) + "월 " + Number(parts[2]) + "일" : String(value);
  }

  function timeLabel(value) {
    return value ? String(value).slice(0, 5) : "";
  }

  function itineraryText(day) {
    var items = (day && day.items) || [];
    if (!items.length) return "이 날은 정해진 일정 없이 천천히 여행했습니다.";
    return items.slice(0, 8).map(function (item) {
      var time = timeLabel(item.startTime);
      var label = item.title || item.placeName || "여행 일정";
      return (time ? time + "  " : "") + label;
    }).join("\n");
  }

  function bookingTypeLabel(type) {
    if (type === "FLIGHT") return "✈ 항공";
    if (type === "ACCOMMODATION") return "⌂ 숙소";
    if (type === "TICKET") return "◇ 티켓";
    return "예약";
  }

  function bookingText(items) {
    return (items || []).slice(0, 9).map(function (item) {
      var meta = [item.detail, item.usageDate, item.statusLabel].filter(Boolean).join(" · ");
      return bookingTypeLabel(item.type) + "  " + (item.title || "예약") + (meta ? "\n   " + meta : "");
    }).join("\n");
  }

  /*
   * 사진을 고른 순서대로 여행 일자에 고르게 나눈다. 촬영 시각을 강제로 읽지
   * 않으므로 메타데이터가 지워진 사진도 같은 결과를 얻는다. 한 일자에 사진이
   * 많으면 여러 지면으로 나눠 모든 사진을 빠짐없이 쓴다.
   */
  function distributePhotos(images, days) {
    var groups = (days || []).map(function () { return []; });
    if (!groups.length) return [images.slice()];
    (images || []).forEach(function (image, index) {
      var dayIndex = Math.min(groups.length - 1, Math.floor(index * groups.length / Math.max(images.length, 1)));
      groups[dayIndex].push(image);
    });
    return groups;
  }

  function chunks(list, size) {
    var result = [];
    for (var i = 0; i < list.length; i += size) result.push(list.slice(i, i + size));
    return result.length ? result : [[]];
  }

  function buildPages(data) {
    var images = (data.images || []).slice();
    var days = (data.days || []).slice().sort(function (a, b) {
      return Number(a.dayNumber || 0) - Number(b.dayNumber || 0);
    });
    var pages = [{
      kind: "cover",
      tripTitle: data.tripTitle,
      title: data.tripTitle || "우리의 여행",
      content: [
        data.destination ? data.destination + "에서 보낸 시간" : "사진으로 다시 만나는 여행",
        days.length ? days.length + "일의 일정" : null,
        images.length + "장의 사진"
      ].filter(Boolean).join("\n"),
      rating: 0,
      startDate: data.startDate,
      endDate: data.endDate,
      destination: data.destination,
      route: data.route || [],
      images: images.slice(0, 3)
    }];

    var groups = distributePhotos(images, days);
    if (days.length) {
      days.forEach(function (day, dayIndex) {
        chunks(groups[dayIndex], 6).forEach(function (pageImages, chunkIndex) {
          pages.push({
            kind: "day",
            tripTitle: data.tripTitle,
            title: "DAY " + (day.dayNumber || dayIndex + 1)
              + (day.tripDate ? " · " + dateLabel(day.tripDate) : "")
              + (chunkIndex ? " · " + (chunkIndex + 1) : ""),
            content: itineraryText(day),
            rating: 0,
            startDate: day.tripDate,
            endDate: day.tripDate,
            destination: day.title || data.destination,
            route: [],
            images: pageImages
          });
        });
      });
    } else {
      chunks(images, 6).forEach(function (pageImages, index) {
        pages.push({
          kind: "gallery",
          tripTitle: data.tripTitle,
          title: "PHOTO STORY " + (index + 1),
          content: "사진으로 남긴 여행의 순간들",
          rating: 0,
          startDate: data.startDate,
          endDate: data.endDate,
          destination: data.destination,
          route: [],
          images: pageImages
        });
      });
    }

    var bookings = (data.bookings && data.bookings.items) || [];
    if (bookings.length) {
      pages.push({
        kind: "booking",
        tripTitle: data.tripTitle,
        title: "여행을 완성한 예약",
        content: bookingText(bookings),
        rating: 0,
        startDate: data.startDate,
        endDate: data.endDate,
        destination: data.destination,
        route: [],
        images: images.slice(0, 2)
      });
    }
    return pages;
  }

  function renderPageData(canvas, page, ratio) {
    var all = page.images || [];
    var shown = all.slice(0, 9);
    return loadImages(shown).then(function (loaded) {
      var drawRatio = ratio || window.devicePixelRatio || 1;
      canvas.width = W * drawRatio;
      canvas.height = H * drawRatio;
      var ctx = canvas.getContext("2d");
      ctx.setTransform(drawRatio, 0, 0, drawRatio, 0, 0);
      ctx.textBaseline = "alphabetic";

      var ready = document.fonts && document.fonts.ready ? document.fonts.ready : Promise.resolve();
      return ready.then(function () {
        var payload = {
          tripTitle: page.tripTitle,
          title: page.title,
          content: page.content,
          rating: page.rating,
          startDate: page.startDate,
          endDate: page.endDate,
          destination: page.destination,
          route: page.route || [],
          totalImages: all.length
        };
        var name = drawSpread(ctx, payload, loaded);
        var missing = 0;
        for (var i = 0; i < loaded.length; i++) if (!loaded[i]) missing++;
        return { layout: name, missing: missing, shown: shown.length, total: all.length };
      });
    });
  }

  /* 이전 단일 지면 API는 테스트와 기존 호출 호환을 위해 유지한다. */
  function render(canvas, data) {
    return renderPageData(canvas, {
      tripTitle: data.tripTitle,
      title: data.title,
      content: data.content,
      rating: data.rating,
      startDate: data.startDate,
      endDate: data.endDate,
      destination: data.destination,
      route: data.route || [],
      images: data.images || []
    });
  }

  function renderAlbum(canvas, data, pageIndex) {
    var pages = buildPages(data);
    var index = Math.max(0, Math.min(Number(pageIndex) || 0, pages.length - 1));
    return renderPageData(canvas, pages[index]).then(function (result) {
      return Object.assign(result, { index: index, pageCount: pages.length, kind: pages[index].kind });
    });
  }

  /* GIF는 실제 표지·날짜별 일정·예약 페이지를 각각 프레임으로 사용한다. */
  function renderAll(data) {
    var pages = buildPages(data);
    return Promise.all(pages.map(function (page) {
      var canvas = document.createElement("canvas");
      return renderPageData(canvas, page, 0.5).then(function () { return canvas; });
    }));
  }

  function toBlob(canvas) {
    return new Promise(function (resolve, reject) {
      try {
        canvas.toBlob(function (blob) {
          if (blob) resolve(blob);
          else reject(new Error("이미지를 만들지 못했습니다."));
        }, "image/png");
      } catch (error) {
        reject(error);
      }
    });
  }

  window.AllMyTripsRecordBook = {
    render: render,
    renderAlbum: renderAlbum,
    renderAll: renderAll,
    buildPages: buildPages,
    toBlob: toBlob,
    pickLayout: pickLayout,
    size: { width: W, height: H }
  };
})();
