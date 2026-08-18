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

// 저장된 종료 시각은 다음 렌더링에서도 체류시간 계산에 사용하고, 수동 편집도 같은 경계를 거절한다.
assert.match(scheduleSource, /storedEnd !== null && storedEnd > start \? storedEnd : start \+ 120/);
assert.match(scheduleSource, /endMinutes >= 24 \* 60/);
assert.match(scheduleSource, /endTime,/);

// 자정 초과 추천은 추가 버튼과 DAY 일괄 추가에서 모두 막고 이유를 표시한다.
assert.match(guideSource, /시간 조정 필요/);
assert.match(guideSource, /자정을 넘어 추가할 수 없습니다/);
assert.match(guideSource, /unavailableTimePlaceIds\.has/);

console.log("schedule time acceptance checks passed");
