/* AI 여행계획 진행률 수용 기준
 *
 * 초안 만들기는 5초 남짓 걸린다. 그동안 "만들고 있어요" 한 줄만 보여주면 얼마나
 * 남았는지 알 수 없다. 진행률을 붙이되, 두 구간의 성격이 다르다.
 *
 *   - 초안 만들기: 서버가 외부 AI를 부르는 시간이 거의 전부라 중간 단계가 없다.
 *     지나간 시간으로 가늠하는 예상치다. 그래서 화면에 예상이라고 적고, 응답 전에는
 *     100%에 닿지 않아야 한다. 다 됐다고 해 놓고 기다리게 하면 멈춘 것처럼 보인다.
 *   - 저장 중 장소 확인: 전체 개수를 알고 하나씩 끝난다. 실제 값이다.
 *
 * 실행: src/test/js 에서 `npm test`
 */
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const JS = fs.readFileSync(path.join(ROOT, "src/main/resources/static/js/pages/guide/ai-trip-plan.js"), "utf8");
const HTML = fs.readFileSync(path.join(ROOT, "src/main/resources/templates/guide/ai-trip-plan.html"), "utf8");
const CSS = fs.readFileSync(path.join(ROOT, "src/main/resources/static/css/pages/guide/ai-trip-plan.css"), "utf8");

let passed = 0;
let failed = 0;
const T = (name, condition, detail) => {
  if (condition) { passed++; console.log("PASS " + name); }
  else { failed++; console.log("FAIL " + name + (detail ? " — " + detail : "")); }
};

/* =========================================================
   1. 초안 만들기 — 예상 진행률

   숫자를 지어내는 것이므로 예상이라고 밝혀야 한다.
   ========================================================= */

T("로딩 화면에 진행률 막대가 있다", /data-plan-progress-bar/.test(HTML));
T("퍼센트 숫자를 보여준다", /data-plan-progress-value/.test(HTML));
T("예상이라고 밝힌다", /예상 진행률/.test(HTML), "화면에 '예상' 표시 없음");
T("보조기기에 진행률로 알린다", /role="progressbar"/.test(HTML));
T("단계 문구 자리가 있다", /data-plan-loading-step/.test(HTML));

/* 응답 전에 100%가 되면 안 된다. 상한이 100 미만이어야 한다. */
const capMatch = JS.match(/PLAN_PROGRESS_CAP\s*=\s*(\d+)/);
T("응답 전에는 100%에 닿지 않는다",
  capMatch && Number(capMatch[1]) < 100, capMatch ? "상한 " + capMatch[1] : "상한 상수 없음");

/* 실제 응답이 오면 100%를 채운다. */
T("응답이 오면 100%로 채운다", /function finishPlanProgress\(\)[\s\S]*?paintPlanProgress\(100\)/.test(JS));
T("초안이 도착하면 finishPlanProgress를 부른다",
  /finishPlanProgress\(\);[\s\S]{0,400}renderPlan\(payload\.data\)/.test(JS));

/* 예상 소요는 실제로 잰 값(5초 안팎)과 같은 자릿수여야 한다. */
const expectedMatch = JS.match(/PLAN_PROGRESS_EXPECTED_MS\s*=\s*(\d+)/);
const expectedMs = expectedMatch ? Number(expectedMatch[1]) : 0;
T("예상 소요가 실제 측정치와 같은 범위다(2~15초)",
  expectedMs >= 2000 && expectedMs <= 15000, expectedMs + "ms");

/* 로딩이 끝나면 타이머를 멈춘다. 안 멈추면 화면을 떠난 뒤에도 계속 돈다. */
T("로딩이 아니면 타이머를 멈춘다",
  /if \(state === "loading"\) startPlanProgress\(\);\s*[\r\n]\s*else stopPlanProgress\(\);/.test(JS));

/* =========================================================
   2. 저장 중 장소 확인 — 실제 진행률

   가늠이 아니라 끝난 개수다. 전체 개수를 세고 하나씩 올린다.
   ========================================================= */

T("전체 장소 개수를 센다", /totalPlaces[\s\S]{0,200}reduce\(/.test(JS));
T("하나 끝날 때마다 올린다", /donePlaces \+= 1;/.test(JS));
T("몇 개 중 몇 개인지 보여준다", /data-plan-save-value/.test(HTML));
T("저장 진행률 막대가 있다", /data-plan-save-bar/.test(HTML));

/* 장소 확인이 끝나면 막대를 치운다. 남기면 다음 단계에서 멈춘 것처럼 보인다. */
T("장소 확인이 끝나면 막대를 치운다",
  /hideSaveProgress\(\);[\s\S]{0,120}일정 준비 중/.test(JS));
T("저장이 실패해도 막대가 남지 않는다",
  /finally \{[\s\S]{0,200}hideSaveProgress\(\);/.test(JS));

/* 누르기 전에는 자리를 차지하지 않는다. */
T("누르기 전에는 감춰져 있다", /data-plan-save-progress hidden/.test(HTML));
T("감춰졌을 때 자리를 차지하지 않는다",
  /\.plan-save-progress\[hidden\][^}]*display:\s*none/.test(CSS));

/* =========================================================
   3. 되돌릴 때 뒤로 미끄러지지 않는다

   폭에 0.25초 전환이 걸려 있어 앞으로는 부드럽게 늘어난다. 그런데 0으로 되돌릴 때도
   같은 전환이 걸리면, 실패 후 다시 시도할 때 막대가 뒤로 미끄러진 뒤 다시 앞으로
   간다. 값은 줄지 않는데 화면만 되감기는 것처럼 보였다.
   ========================================================= */

T("전환을 끄는 클래스가 있다",
  /\.plan-progress-bar > span\.plan-progress-instant[^}]*transition:\s*none/.test(CSS));
T("되돌릴 때 그 클래스를 쓴다",
  /classList\.add\("plan-progress-instant"\)/.test(JS));
T("폭을 확정한 뒤 전환을 되살린다",
  /void fill\.offsetWidth;[\s\S]{0,120}classList\.remove\("plan-progress-instant"\)/.test(JS),
  "reflow 없이 클래스를 빼면 다음 증가분까지 끊겨 보인다");
T("0으로 되돌릴 때 즉시 옮긴다", /paintPlanProgress\(0, true\)/.test(JS));

/* 화면이 뜰 때 showPlanState("loading")이 두 번 불린다. 두 번째가 진행률을 되돌리면 안 된다. */
T("이미 돌고 있으면 다시 시작하지 않는다",
  /function startPlanProgress\(\)[\s\S]{0,400}if \(planProgressTimer\) return;/.test(JS));

/* =========================================================
   4. 두 구간이 같은 모양을 쓴다
   ========================================================= */

T("공용 막대 스타일이 있다", /\.plan-progress-bar\s*\{/.test(CSS));
T("막대가 넘치지 않게 잘린다", /\.plan-progress-bar[^}]*overflow:\s*hidden/.test(CSS));
T("숫자 폭이 흔들리지 않는다",
  /\.plan-progress > strong[^}]*font-variant-numeric:\s*tabular-nums/.test(CSS),
  "퍼센트가 바뀔 때마다 폭이 변하면 옆 요소가 흔들린다");

console.log("");
console.log(passed + " passed, " + failed + " failed");
if (failed > 0) process.exit(1);
