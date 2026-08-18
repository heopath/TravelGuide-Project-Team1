/* 마이페이지 진입점(mypage.js)이 부르는 초기화 함수가 전부 import돼 있는지 본다.
 *
 * 빠뜨리면 브라우저에서만 드러나고 빌드는 통과한다. 실제로 v0.7.0이 initSupportChat을
 * import 없이 부른 채 나갔고, 상담 채팅(#247)이 운영에서 통째로 동작하지 않았다.
 * ReferenceError가 Promise.allSettled 인자를 만드는 도중에 터져 pageReady도 안 붙었다.
 */
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const MYPAGE_DIR = path.join(ROOT, "src/main/resources/static/js/pages/mypage");
const ENTRY = path.join(MYPAGE_DIR, "mypage.js");

let passed = 0;
let failed = 0;
function test(name, condition, detail) {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name + (detail ? " — " + detail : "")); }
}

const source = fs.readFileSync(ENTRY, "utf8");

/* import { a, b } from "./x.js" 에서 이름과 경로를 뽑는다. */
function importedNames(code) {
  const names = new Map();
  const pattern = /import\s*\{([^}]*)\}\s*from\s*["']([^"']+)["']/g;
  let match;
  while ((match = pattern.exec(code)) !== null) {
    match[1].split(",")
      .map((raw) => raw.trim().split(/\s+as\s+/).pop().trim())
      .filter(Boolean)
      .forEach((name) => names.set(name, match[2]));
  }
  return names;
}

/* 호출되는 initXxx() 이름. 선언부(function initXxx)는 제외한다. */
function calledInitFunctions(code) {
  const called = new Set();
  const pattern = /(^|[^.\w])(init[A-Z]\w*)\s*\(/g;
  let match;
  while ((match = pattern.exec(code)) !== null) {
    const before = code.slice(Math.max(0, match.index - 12), match.index + match[0].length);
    if (/function\s+init[A-Z]\w*\s*\($/.test(before)) continue;
    called.add(match[2]);
  }
  return called;
}

const imports = importedNames(source);
const called = calledInitFunctions(source);
const declared = new Set(
  [...source.matchAll(/function\s+(init[A-Z]\w*)\s*\(/g)].map((m) => m[1])
);

const missing = [...called].filter((name) => !imports.has(name) && !declared.has(name));

test("초기화 함수를 하나 이상 부른다", called.size > 0, `찾은 호출: ${called.size}`);
test("부르는 초기화 함수가 전부 import돼 있다", missing.length === 0,
  missing.length ? `import 없음: ${missing.join(", ")}` : "");

/* import한 파일이 실제로 그 이름을 export하는지까지 본다. 경로 오타를 잡는다. */
const badExports = [];
imports.forEach((from, name) => {
  if (!from.startsWith(".")) return;
  const file = path.resolve(MYPAGE_DIR, from);
  if (!fs.existsSync(file)) {
    badExports.push(`${name}: 파일 없음 (${from})`);
    return;
  }
  const target = fs.readFileSync(file, "utf8");
  const exported = new RegExp(`export\\s+(async\\s+)?function\\s+${name}\\b`).test(target)
    || new RegExp(`export\\s*\\{[^}]*\\b${name}\\b`).test(target)
    || new RegExp(`export\\s+(const|let|var)\\s+${name}\\b`).test(target);
  if (!exported) badExports.push(`${name}: ${from}에 export 없음`);
});

test("import한 이름을 대상 파일이 실제로 export한다", badExports.length === 0,
  badExports.join(" / "));

/* 상담 채팅은 v0.7.0에서 실제로 빠졌던 항목이라 이름으로 한 번 더 못 박는다. */
test("상담 채팅 초기화가 걸려 있다",
  called.has("initSupportChat") && imports.has("initSupportChat"));

console.log(`\n${passed} passed, ${failed} failed`);
if (failed > 0) process.exit(1);
