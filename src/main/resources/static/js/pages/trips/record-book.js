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

  var PAPER = "#faf7f0";
  var BOARD = "#efe9dc";
  var INK = "#1c1917";
  var BODY = "#44403c";
  var MUTED = "#78716c";
  var EDGE = "#e7e2d6";
  var CORNER = "#d9cfb8";
  var GOLD = "#c08a2e";

  function font(weight, size) {
    return weight + " " + size + "px Pretendard, -apple-system, BlinkMacSystemFont, system-ui, sans-serif";
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
    ctx.globalAlpha = 0.085;
    ctx.fillStyle = "#8b7d63";
    var count = Math.round((w * h) / 1400);
    for (var i = 0; i < count; i++) {
      ctx.fillRect(x + rand() * w, y + rand() * h, 2 + rand() * 2, 2);
    }

    /* 섬유. 낱알만으로는 모래처럼 보여서, 결을 따라 흐르는 실선을 섞는다. */
    ctx.globalAlpha = 0.05;
    ctx.strokeStyle = "#9c8d70";
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
      g.addColorStop(0, "rgba(120,105,80,0.09)");
      g.addColorStop(1, "rgba(120,105,80,0)");
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
    ctx.beginPath();
    ctx.rect(x, y, w, h);
    ctx.clip();
    ctx.drawImage(img, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh);
    ctx.restore();
  }

  function emptyFrame(ctx, x, y, w, h, message) {
    ctx.fillStyle = "#f1ece0";
    ctx.fillRect(x, y, w, h);
    ctx.strokeStyle = EDGE;
    ctx.lineWidth = 2;
    ctx.strokeRect(x + 1, y + 1, w - 2, h - 2);
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

  /* 글을 지면 폭에 맞춰 흘린다. 넘치면 말줄임으로 끊는다. */
  function flow(ctx, text, x, y, maxWidth, lineHeight, maxLines) {
    var paragraphs = String(text || "").split(/\n+/);
    var line = 0;
    for (var p = 0; p < paragraphs.length && line < maxLines; p++) {
      var words = paragraphs[p].split(/\s+/).filter(Boolean);
      var current = "";
      for (var i = 0; i < words.length; i++) {
        var next = current ? current + " " + words[i] : words[i];
        if (ctx.measureText(next).width > maxWidth && current) {
          ctx.fillText(current, x, y + line * lineHeight);
          line++;
          current = words[i];
          if (line >= maxLines) {
            ctx.fillText(current.slice(0, 24) + "…", x, y + line * lineHeight);
            return line + 1;
          }
        } else {
          current = next;
        }
      }
      if (current && line < maxLines) {
        ctx.fillText(current, x, y + line * lineHeight);
        line++;
      }
    }
    return line;
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

    var cursor = y + 140 + used * 84 + 40;
    stars(ctx, x, cursor, 40, 12, data.rating || 0);

    cursor += 80;
    ctx.fillStyle = BODY;
    ctx.font = font(400, 36);
    var room = Math.max(1, Math.floor((y + h - cursor - 90) / 60));
    flow(ctx, data.content || "", x, cursor, w, 60, room);

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
    ctx.fillStyle = BOARD;
    ctx.fillRect(0, 0, W, H);

    ctx.fillStyle = PAPER;
    ctx.fillRect(MARGIN, MARGIN, W - MARGIN * 2, H - MARGIN * 2);
    ctx.strokeStyle = EDGE;
    ctx.lineWidth = 2;
    ctx.strokeRect(MARGIN + 1, MARGIN + 1, W - MARGIN * 2 - 2, H - MARGIN * 2 - 2);

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

    grain(ctx, MARGIN, MARGIN, W - MARGIN * 2, H - MARGIN * 2, 20260822);

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
    wide.addColorStop(0, "rgba(120,105,80,0)");
    wide.addColorStop(0.32, "rgba(120,105,80,0.12)");
    wide.addColorStop(0.5, "rgba(105,90,68,0.4)");
    wide.addColorStop(0.68, "rgba(120,105,80,0.12)");
    wide.addColorStop(1, "rgba(120,105,80,0)");
    ctx.fillStyle = wide;
    ctx.fillRect(center - 150, top, 300, height);

    ctx.strokeStyle = "rgba(92,78,58,0.55)";
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(center, top);
    ctx.lineTo(center, top + height);
    ctx.stroke();

    /* 접힌 자국 바로 옆은 빛을 받아 살짝 밝다. */
    ctx.strokeStyle = "rgba(255,252,244,0.5)";
    ctx.lineWidth = 3;
    [center - 7, center + 7].forEach(function (x) {
      ctx.beginPath();
      ctx.moveTo(x, top);
      ctx.lineTo(x, top + height);
      ctx.stroke();
    });

    return layoutName;
  }

  function render(canvas, data) {
    var all = data.images || [];
    var shown = all.slice(0, 9);
    return loadImages(shown).then(function (loaded) {
      var ratio = window.devicePixelRatio || 1;
      canvas.width = W * ratio;
      canvas.height = H * ratio;
      var ctx = canvas.getContext("2d");
      ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
      ctx.textBaseline = "alphabetic";

      var ready = document.fonts && document.fonts.ready ? document.fonts.ready : Promise.resolve();
      return ready.then(function () {
        var payload = {
          tripTitle: data.tripTitle,
          title: data.title,
          content: data.content,
          rating: data.rating,
          startDate: data.startDate,
          endDate: data.endDate,
          destination: data.destination,
          totalImages: all.length
        };
        var name = drawSpread(ctx, payload, loaded);
        var missing = 0;
        for (var i = 0; i < loaded.length; i++) if (!loaded[i]) missing++;
        return { layout: name, missing: missing, shown: shown.length, total: all.length };
      });
    });
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
    toBlob: toBlob,
    pickLayout: pickLayout,
    size: { width: W, height: H }
  };
})();
