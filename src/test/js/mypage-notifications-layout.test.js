/* 마이페이지 알림 화면 정렬 수용 기준
 *
 * 알림만 전체 화면 뷰 목록에서 빠져 있어서 패딩이 0이었다. 제목은 뒤로 가기 버튼에
 * 밀려 들어가 있는데 목록은 패널 가장자리에 붙어, 좌측 정렬선이 서로 어긋나 보였다.
 * 항목 안에서도 글이 왼쪽 3분의 1에 몰리고 오른쪽이 통째로 비었다.
 *
 * 실행: src/test/js 에서 `npm test`
 */
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const CSS = fs.readFileSync(path.join(ROOT, "src/main/resources/static/css/pages/mypage/mypage.css"), "utf8");

let passed = 0;
let failed = 0;
const T = (name, condition, detail) => {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name + (detail ? " — " + detail : "")); }
};

/* =========================================================
   1. 알림도 다른 전체 화면 뷰와 같은 패딩을 쓴다

   여기서 빠지면 알림만 내용이 패널 가장자리에 붙는다.
   ========================================================= */

const fullViewRule = CSS.match(/((?:\.page-mypage \.mypage-[a-z-]+-view,\s*)+\.page-mypage \.mypage-[a-z-]+-view\s*\{[^}]*\})/);
T("전체 화면 뷰 묶음 규칙이 있다", !!fullViewRule);

const ruleText = fullViewRule ? fullViewRule[1] : "";
["trips", "favorites", "reviews", "support", "notifications", "settings"].forEach((view) => {
  T(view + " 뷰가 그 묶음에 들어 있다",
    ruleText.includes(".mypage-" + view + "-view"),
    view + "만 빠지면 그 화면 정렬이 혼자 어긋난다");
});
T("묶음에 padding이 걸려 있다", /padding:\s*28px/.test(ruleText));

/* =========================================================
   2. 항목 안에서 글이 남은 폭을 다 쓴다
   ========================================================= */

T("본문이 남은 폭을 채운다",
  /\.notification-text\s*\{[^}]*flex:\s*1/.test(CSS),
  "안 늘리면 글이 왼쪽에 몰리고 오른쪽이 빈다");
T("긴 글이 칸을 밀어내지 않는다",
  /\.notification-text\s*\{[^}]*min-width:\s*0/.test(CSS));

/* =========================================================
   3. 시각은 제목 줄 오른쪽 끝에 둔다

   본문과 링크 사이에 끼어 있으면 읽는 흐름이 끊긴다.
   ========================================================= */

const smallRule = CSS.match(/\.page-mypage \.notification-text small\s*\{[^}]*\}/);
T("시각 규칙이 있다", !!smallRule);
const small = smallRule ? smallRule[0] : "";
T("시각이 제목과 같은 줄이다", /grid-row:\s*1/.test(small), small);
T("시각이 오른쪽 끝에 붙는다", /justify-self:\s*end/.test(small));
T("시각이 줄바꿈되지 않는다", /white-space:\s*nowrap/.test(small),
  "줄바꿈되면 항목 높이가 들쭉날쭉해진다");

const strongRule = CSS.match(/\.page-mypage \.notification-text strong\s*\{[^}]*\}/);
T("제목이 첫 줄 왼쪽이다",
  strongRule && /grid-row:\s*1/.test(strongRule[0]) && /grid-column:\s*1/.test(strongRule[0]));

/* =========================================================
   4. 본문과 링크는 한 줄을 통째로 쓴다
   ========================================================= */

T("본문이 두 칸을 다 쓴다",
  /\.page-mypage \.notification-text p\s*\{[^}]*grid-column:\s*1 \/ -1/.test(CSS));
T("링크가 두 칸을 다 쓴다",
  /\.page-mypage \.notification-go\s*\{[^}]*grid-column:\s*1 \/ -1/.test(CSS));
T("링크에 밑줄이 없다",
  /\.page-mypage \.notification-go\s*\{[^}]*text-decoration:\s*none/.test(CSS),
  "다른 버튼들과 이질적으로 보인다");

/* 아이콘이 세로로 늘어나지 않게 첫 줄에 맞춘다. */
T("아이콘이 제목 줄에 맞춰진다",
  /\.page-mypage \.notification-item\s*\{[^}]*align-items:\s*flex-start/.test(CSS));

console.log("");
console.log(passed + " passed, " + failed + " failed");
if (failed > 0) process.exit(1);
