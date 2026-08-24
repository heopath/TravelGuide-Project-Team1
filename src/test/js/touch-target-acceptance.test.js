const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const read = (p) => fs.readFileSync(path.resolve(__dirname, p), "utf8");

/*
 * 손끝이 닿는 넓이는 대략 9mm다. 화면에서 44px 안팎은 되어야 한 번에 눌린다.
 *
 * 좁은 화면에서 40px 이하인 버튼들이 있었다. 특히 항공 검색의 닫기(16×15)와
 * 조건 변경(54×20)은 여러 번 눌러야 잡히는 크기였다.
 */

const components = read("../../main/resources/static/css/common/components.css");
const layout = read("../../main/resources/static/css/common/layout.css");
const flights = read("../../main/resources/static/css/pages/booking/flights.css");

/* =========================================================
   1. 좁은 화면에서만 키운다

   마우스로 쓰는 넓은 화면은 40px로도 충분하다. 전 폭에서 키우면 헤더가
   두꺼워지고 촘촘한 배치가 흐트러진다.
   ========================================================= */

const narrowBlocks = components.match(/@media \(max-width: 760px\) \{[\s\S]*?\n\}/g) || [];
const narrow = narrowBlocks.join("\n");

assert.match(narrow, /\.menu-button\s*\{[^}]*width:\s*44px/,
  "좁은 화면에서 메뉴 버튼은 44px여야 한다");
assert.match(narrow, /\.site-footer nav button\s*\{[^}]*min-height:\s*44px/,
  "좁은 화면에서 푸터 버튼은 44px여야 한다");
assert.match(narrow, /\.app-header \.brand\s*\{[^}]*min-height:\s*44px/,
  "로고도 세로 44px는 되어야 한다");

/* 넓은 화면 규칙은 건드리지 않았어야 한다. */
assert.match(layout, /\.menu-button \{[^}]*width: 40px/,
  "넓은 화면의 메뉴 버튼 크기는 그대로 둔다");

/* =========================================================
   2. 보이는 모양은 그대로 두고 누를 면적만 넓힌다

   항공 검색 막대는 촘촘해서 버튼을 실제로 키우면 배치가 흐트러진다.
   가짜 요소로 손이 닿는 넓이만 넓힌다.
   ========================================================= */

assert.match(flights, /\.extbar \.x,\s*\n\.page-flights \.cond-edit \{[\s\S]*?position: relative;/,
  "가짜 요소를 놓으려면 기준이 되는 position이 필요하다");
assert.match(flights, /::after \{[\s\S]*?min-width: 44px;[\s\S]*?min-height: 44px;/,
  "누를 면적이 44px는 되어야 한다");
assert.match(flights, /::after \{[\s\S]*?transform: translate\(-50%, -50%\);/,
  "넓힌 면적은 버튼 가운데를 기준으로 퍼져야 한쪽으로 쏠리지 않는다");

/* 크기를 직접 키우지 않았는지. 키웠다면 검색 막대 배치가 달라진다. */
const condEditRule = flights.match(/\.page-flights \.cond-edit \{[^}]*\}/);
assert.ok(condEditRule, "조건 변경 버튼 규칙이 있어야 한다");
assert.doesNotMatch(condEditRule[0], /min-height|height:/,
  "보이는 크기는 그대로 두어야 한다");

console.log("touch target acceptance checks passed");
