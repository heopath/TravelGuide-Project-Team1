const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const scheduleSource = fs.readFileSync(
  path.resolve(__dirname, "../../main/resources/static/js/pages/trips/schedule.js"), "utf8"
);
const guideSource = fs.readFileSync(
  path.resolve(__dirname, "../../main/resources/static/js/pages/trips/schedule-ai-guide.js"), "utf8"
);

// AI 일정은 기본 2시간을 점유하며, 24:00에 닿는 일정도 다음 날로 넘어가는 것으로 처리한다.
assert.match(scheduleSource, /const aiRecommendationDurationMinutes = 120;/);
assert.match(scheduleSource, /recommendationStart \+ aiRecommendationDurationMinutes >= dayMinutes/);
assert.match(scheduleSource, /getAiRecommendationUnavailableTimePlaceIds/);
assert.match(scheduleSource, /match\(\/\^\(\[01\]\\d\|2\[0-3\]\):\(\[0-5\]\\d\)/);
assert.doesNotMatch(scheduleSource, /\[01\]\\\\d\|2\[0-3\]/);
assert.match(scheduleSource, /isAiRecommendationOutsideDay,/);

// 저장된 종료 시각은 다음 렌더링에서도 체류시간 계산에 사용하고, 수동 편집도 같은 경계를 거절한다.
assert.match(scheduleSource, /storedEnd !== null && storedEnd > start \? storedEnd : start \+ 120/);
assert.match(scheduleSource, /endMinutes >= 24 \* 60/);
assert.match(scheduleSource, /endTime,/);
assert.match(scheduleSource, /기존 일정과 시간이 겹칩니다\. 다른 시간을 선택해 주세요\./);
assert.match(scheduleSource, /String\(other\?\.itineraryItemId\) !== String\(item\?\.itineraryItemId\)/);

// 자정 초과 추천은 추가 버튼과 DAY 일괄 추가에서 모두 막고 이유를 표시한다.
assert.match(guideSource, /시간 조정 필요/);
assert.match(guideSource, /자정을 넘어 추가할 수 없습니다/);
assert.match(guideSource, /unavailableTimeKeys\.has\(recommendationKey\(item\)\)/);

// 사용자는 추천 항목을 골라 일괄 추가할 수 있고, 충돌 항목은 다른 시간대로 다시 추천할 수 있다.
assert.match(guideSource, /selectedPlaceIds/);
assert.match(guideSource, /개 선택 추가/);
assert.match(guideSource, /다른 시간 추천/);
assert.match(guideSource, /현재 일정과 겹치지 않는 다른 시간대로 추천해줘/);
assert.match(guideSource, /시작 시간은 21:30 이하로 추천해줘/);
assert.match(guideSource, /예상 체류 2시간/);
assert.match(guideSource, /recommendationKey\(item\)/);
assert.match(guideSource, /filter\(isOutsideDay\)/);

console.log("schedule time acceptance checks passed");
