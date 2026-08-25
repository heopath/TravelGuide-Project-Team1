const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const read = (p) => fs.readFileSync(path.resolve(__dirname, p), "utf8");

/* =========================================================
   1. 지면 조판 규칙

   사진이 몇 장 올지 미리 알 수 없다. 좌표를 박아두면 3장일 때와 7장일 때
   같은 지면이 나오므로, 장수와 비율로 배치를 고른다.
   ========================================================= */

const sandbox = { window: {}, document: {}, devicePixelRatio: 1 };
vm.createContext(sandbox);
vm.runInContext(read("../../main/resources/static/js/pages/trips/record-book.js"), sandbox);
const book = sandbox.window.AllMyTripsRecordBook;
const pick = book.pickLayout;

assert.equal(pick(0).name, "none", "사진이 없으면 사진 배치가 없다");
assert.equal(pick(0).cells.length, 0);

/* 한 장일 때는 그 사진이 가로인지 세로인지까지 본다. */
assert.equal(pick(1, 1.5).name, "single-landscape", "가로 사진 한 장은 가로 자리에 앉힌다");
assert.equal(pick(1, 0.7).name, "single-portrait", "세로 사진 한 장은 세로 자리에 앉힌다");
assert.equal(pick(1, null).name, "single-landscape", "비율을 모르면 가로로 둔다");

assert.equal(pick(2).name, "stack");
assert.equal(pick(3).name, "hero", "세 장은 큰 것 하나에 작은 것 둘이다");
assert.equal(pick(4).name, "quad");
assert.equal(pick(5).name, "six");
assert.equal(pick(6).name, "six");
assert.equal(pick(9).name, "grid9");

/* 어떤 장수에도 자리가 모자라지 않아야 한다. */
for (let n = 1; n <= 9; n++) {
  assert.ok(pick(n, 1.4).cells.length >= Math.min(n, 9),
    `${n}장일 때 자리가 모자라면 안 된다`);
}

/* 모든 자리는 지면 안에 있어야 한다. 값은 0~1 비율이다. */
for (let n = 1; n <= 9; n++) {
  pick(n, 1.4).cells.forEach(([x, y, w, h]) => {
    assert.ok(x >= 0 && y >= 0, `${n}장: 자리가 지면 왼쪽/위를 벗어났다`);
    assert.ok(x + w <= 1.001, `${n}장: 자리가 지면 오른쪽을 벗어났다`);
    assert.ok(y + h <= 1.001, `${n}장: 자리가 지면 아래를 벗어났다`);
    assert.ok(w > 0 && h > 0, `${n}장: 자리 크기가 0 이하다`);
  });
}

/* 인쇄를 염두에 둔 크기여야 한다. */
assert.ok(book.size.width >= 2000, "지면이 인쇄에 쓸 만큼 커야 한다");
assert.ok(book.size.width > book.size.height, "펼친 지면은 가로가 길다");

/* =========================================================
   2. 내보내기가 막히지 않게 하는 장치

   다른 도메인 사진을 허락 없이 그리면 브라우저가 canvas를 오염으로 표시하고
   저장을 막는다. 허락받은 사진만 그려야 한 장 때문에 전체가 막히지 않는다.
   ========================================================= */

const bookSource = read("../../main/resources/static/js/pages/trips/record-book.js");

assert.match(bookSource, /img\.crossOrigin = "anonymous"/,
  "사진은 CORS 허락을 받아 불러와야 한다");
assert.match(bookSource, /onerror = function \(\) \{ resolve\(null\); \}/,
  "못 불러온 사진은 건너뛰고 지면은 계속 그려야 한다");
assert.match(bookSource, /document\.fonts/,
  "폰트 로딩을 기다려야 한글이 제 글꼴로 그려진다");
assert.match(bookSource, /devicePixelRatio/,
  "화면 배율을 반영해야 흐릿하지 않다");

/* =========================================================
   3. 화면 연결
   ========================================================= */

const recordSource = read("../../main/resources/static/js/pages/trips/record.js");
const recordMarkup = read("../../main/resources/templates/trips/record.html");

assert.match(recordMarkup, /data-record-book-canvas/, "지면을 그릴 canvas가 있어야 한다");
assert.match(recordMarkup, /data-record-book-draw/, "지면 만들기 버튼이 있어야 한다");
assert.match(recordMarkup, /data-record-book-save/, "저장 버튼이 있어야 한다");
assert.match(recordMarkup, /data-record-book-gif/, "공유용 GIF 저장 버튼이 있어야 한다");
assert.match(recordMarkup, /type="file"/, "사진은 URL 입력 대신 파일 업로드로 받아야 한다");
assert.match(recordMarkup, /accept="image\/jpeg,image\/png,image\/webp,image\/gif"/,
  "허용할 사진 형식을 화면에서 안내해야 한다");
assert.match(recordMarkup, /record-book\.js/, "지면 그리는 파일이 실려야 한다");
assert.ok(
  recordMarkup.indexOf("record-book.js") < recordMarkup.indexOf("pages/trips/record.js"),
  "record.js보다 먼저 실려야 호출할 수 있다"
);

assert.match(recordSource, /AllMyTripsRecordBook\.render/, "그리기를 연결해야 한다");
assert.match(recordSource, /AllMyTripsRecordBook\.toBlob/, "저장을 연결해야 한다");
assert.match(recordSource, /new window\.GIF/, "GIF 렌더링을 연결해야 한다");
assert.match(recordSource, /images\/upload/, "사진 파일을 S3 업로드 API로 보내야 한다");
assert.match(recordSource, /titleInput\?\.value/, "저장 전 입력값으로 그려야 미리보기가 쓸모 있다");

console.log("record book acceptance checks passed");
