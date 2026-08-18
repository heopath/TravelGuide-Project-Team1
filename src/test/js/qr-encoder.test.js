/* QR 인코더 왕복 시험 (#265)
 *
 * 인코더는 틀려도 화면에 그럴듯한 그림이 나온다. 눈으로는 못 잡는다. 실제로 디코딩해서
 * 원래 값이 나오는지 봐야 한다.
 *
 * 스캔에 쓸 BarcodeDetector는 안드로이드 크롬에만 있어 시험에 쓸 수 없다. 대신 디코더를
 * 시험 의존성으로 두고 픽셀에서 직접 읽는다. 화면 코드와는 다른 구현이라 인코더가 스스로를
 * 채점하는 일도 없다.
 */
const fs = require("fs");
const path = require("path");
const jsQR = require("jsqr");

const ROOT = path.resolve(__dirname, "../../..");
const SOURCE = path.join(ROOT, "src/main/resources/static/js/core/qr-encoder.js");

let passed = 0;
let failed = 0;
function test(name, condition, detail) {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name + (detail ? " — " + detail : "")); }
}

/* ES 모듈이라 require로 못 읽는다. export만 떼고 함수 본문을 평가한다. */
function loadEncoder() {
  const source = fs.readFileSync(SOURCE, "utf8")
    .replace(/^export function/gm, "function");
  const factory = new Function(`${source}\n return { encodeQr };`);
  return factory();
}

const { encodeQr } = loadEncoder();

/** 모듈 배열을 디코더가 읽을 수 있는 RGBA 픽셀로 바꾼다. */
function toPixels(modules, size, scale, quiet) {
  const width = (size + quiet * 2) * scale;
  const data = new Uint8ClampedArray(width * width * 4).fill(255);
  for (let row = 0; row < size; row += 1) {
    for (let column = 0; column < size; column += 1) {
      if (!modules[row][column]) continue;
      for (let y = 0; y < scale; y += 1) {
        for (let x = 0; x < scale; x += 1) {
          const py = (row + quiet) * scale + y;
          const px = (column + quiet) * scale + x;
          const offset = (py * width + px) * 4;
          data[offset] = 0;
          data[offset + 1] = 0;
          data[offset + 2] = 0;
        }
      }
    }
  }
  return { data, width };
}

function roundTrip(text) {
  const { size, modules } = encodeQr(text);
  const { data, width } = toPixels(modules, size, 6, 4);
  const decoded = jsQR(data, width, width);
  return { size, value: decoded ? decoded.data : null };
}

/* 실제로 쓸 값들. 입장 코드는 32바이트를 base64url로 담아 43자다. */
const samples = [
  ["입장 코드 길이(43자)", "ysHGI9JlEDuEwUi8YcVlNLugN-A_UInKaAGZ-SDOaw0"],
  ["티켓 번호", "AMT-TKN-3D06FFABCC13"],
  ["한 글자", "A"],
  ["주소", "https://allmytrip.click/admin/scan"],
  ["기호 섞임", "a-b_c~d.e/f?g=h&i+j"],
];

samples.forEach(([label, text]) => {
  let result;
  try {
    result = roundTrip(text);
  } catch (error) {
    test(`${label} 왕복`, false, error.message);
    return;
  }
  test(`${label} 왕복`, result.value === text,
    `기대 "${text}" / 실제 ${result.value === null ? "검출 실패" : `"${result.value}"`}`);
});

/* 버전이 길이에 따라 올라가는지. 짧은 값에 큰 버전을 쓰면 모듈이 촘촘해져 스캔이 나빠진다. */
{
  const small = encodeQr("A").size;
  const large = encodeQr("x".repeat(100)).size;
  test("길이에 따라 버전이 올라간다", small < large, `${small} vs ${large}`);
  test("짧은 값은 버전 1(21칸)", small === 21, String(small));
}

/* 담을 수 없는 길이는 조용히 잘라내지 않고 알린다. 잘라내면 통하지 않는 QR이 만들어진다. */
{
  let message = null;
  try { encodeQr("x".repeat(200)); } catch (error) { message = error.message; }
  test("너무 길면 예외로 알린다", message !== null && message.includes("깁니다"), String(message));
}

/* 같은 값은 항상 같은 그림이어야 한다. 마스크 선택이 흔들리면 재현이 안 된다. */
{
  const first = encodeQr("AMT-TKN-3D06FFABCC13").modules.flat().join("");
  const second = encodeQr("AMT-TKN-3D06FFABCC13").modules.flat().join("");
  test("같은 값은 같은 결과", first === second);
}

console.log(`\n${passed} passed, ${failed} failed`);
if (failed > 0) process.exit(1);
