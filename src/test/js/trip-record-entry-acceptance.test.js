const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const read = (p) => fs.readFileSync(path.resolve(__dirname, p), "utf8");

/* =========================================================
   1. 끝난 여행 판단 규칙 (core/trip-status.js)

   서버의 TripStatus.isFinished와 같은 규칙이어야 한다. 어긋나면 화면에는
   기록 버튼이 보이는데 눌러도 서버가 거절하는 상태가 된다.
   ========================================================= */

const sandbox = { window: {} };
vm.createContext(sandbox);
vm.runInContext(read("../../main/resources/static/js/core/trip-status.js"), sandbox);
const isTripFinished = sandbox.window.AllMyTripsTripStatus.isTripFinished;

const TODAY = new Date(2026, 7, 22); // 2026-08-22

assert.equal(isTripFinished({ status: "COMPLETED", endDate: "2030-01-01" }, TODAY), true,
  "COMPLETED면 날짜와 무관하게 끝난 여행이다");
assert.equal(isTripFinished({ status: "CONFIRMED", endDate: "2026-08-21" }, TODAY), true,
  "확정한 여행은 종료일 다음날부터 끝난 여행이다");
assert.equal(isTripFinished({ status: "CONFIRMED", endDate: "2026-08-22" }, TODAY), false,
  "종료일 당일은 아직 여행 중이다");
assert.equal(isTripFinished({ status: "CONFIRMED", endDate: "2026-09-10" }, TODAY), false,
  "아직 다녀오지 않은 여행은 끝나지 않았다");
assert.equal(isTripFinished({ status: "DRAFT", endDate: "2026-08-01" }, TODAY), false,
  "초안은 날짜가 지나도 끝난 여행이 아니다");
assert.equal(isTripFinished({ status: "CANCELLED", endDate: "2026-08-01" }, TODAY), false,
  "취소한 여행은 날짜가 지나도 끝난 여행이 아니다");
assert.equal(isTripFinished({ status: "CONFIRMED", endDate: null }, TODAY), false,
  "종료일이 없으면 끝난 여행으로 보지 않는다");
assert.equal(isTripFinished(null, TODAY), false, "여행이 없으면 끝난 여행이 아니다");

/* =========================================================
   2. 여행 기록으로 가는 입구 비노출

   화면과 API는 보존하되 기능을 보완할 때까지 일반 사용자에게 진입점을 노출하지 않는다.
   ========================================================= */

const tripsSource = read("../../main/resources/static/js/pages/mypage/mypage-trips.js");

assert.match(tripsSource, /const SHOW_TRAVEL_RECORD_ENTRY = false/,
  "여행 기록 진입점은 비노출 상태여야 한다");
assert.match(tripsSource, /trip-full-card-record/,
  "다시 공개할 수 있도록 기록 버튼 구현은 보존해야 한다");
assert.match(tripsSource, /\/trips\/\$\{trip\.tripId\}\/record/,
  "기록 버튼은 그 여행의 기록 화면으로 가야 한다");
assert.match(tripsSource, /if \(SHOW_TRAVEL_RECORD_ENTRY && finished\) \{/,
  "공개 플래그를 켜더라도 다녀온 여행에서만 기록 버튼을 보여야 한다");
assert.match(tripsSource, /event\.stopPropagation\(\);\s*\n\s*window\.location\.href/,
  "카드 전체가 일정 링크이므로 기록 버튼은 전파를 막아야 한다");
assert.match(tripsSource, /isTripFinished/,
  "상태 표시도 공용 규칙을 써야 한다");

/* 판단 규칙을 화면마다 새로 쓰지 않는다. */
const recordSource = read("../../main/resources/static/js/pages/trips/record.js");
assert.match(recordSource, /AllMyTripsTripStatus\.isTripFinished/,
  "기록 화면도 공용 규칙을 써야 한다");
assert.doesNotMatch(recordSource, /status !== "COMPLETED"/,
  "기록 화면이 상태만으로 판단하면 안 된다");

/* 마이페이지 사진첩 탭은 완료된 여행에서 생성된 기록만 모아 편집 화면으로 보낸다. */
const mypageMarkup = read("../../main/resources/templates/mypage/mypage.html");
const mypageSource = read("../../main/resources/static/js/pages/mypage/mypage.js");
const albumSource = read("../../main/resources/static/js/pages/mypage/mypage-records.js");
assert.match(mypageMarkup, /data-open-records/, "마이페이지에 여행 기록 탭이 있어야 한다");
assert.match(mypageMarkup, /data-records-view/, "여행 기록 목록 화면이 있어야 한다");
assert.match(mypageSource, /initTravelRecords/, "여행 기록 탭을 열 때 목록을 불러와야 한다");
assert.match(albumSource, /\/api\/v1\/travel-records\/me/, "내 기록만 조회해야 한다");
assert.match(albumSource, /\/trips\/\$\{record\.tripId\}\/record/, "사진첩 카드는 해당 여행 기록 화면으로 이동해야 한다");

/* 공용 규칙 파일이 모든 화면에 실려야 한다. */
const scriptsFragment = read("../../main/resources/templates/fragments/scripts.html");
assert.match(scriptsFragment, /core\/trip-status\.js/,
  "공용 규칙 파일이 공통 스크립트에 실려야 한다");

console.log("trip record entry acceptance checks passed");
