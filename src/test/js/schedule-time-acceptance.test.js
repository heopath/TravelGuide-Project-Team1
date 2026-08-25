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
assert.match(scheduleSource, /function isTimeConflictCandidate\(other, item, targetDayIdentity, isAllScheduleVisible\)/);
assert.match(scheduleSource, /String\(other\?\.itineraryItemId\) === String\(item\?\.itineraryItemId\)/);
assert.match(scheduleSource, /function attachScheduleDayIdentity\(items, day\)/);
assert.match(scheduleSource, /draft-day:/);
assert.match(scheduleSource, /return !isAllScheduleVisible \|\| isSameScheduleDay\(other, targetDayIdentity\)/);
assert.match(scheduleSource, /openTimeEditor\(item, timeButton, day\)/);

// 전체 보기에서는 저장 일정과 초안 일정 모두 DAY별로만 충돌을 판단한다.
const sameDayFunction = scheduleSource.match(/function isSameScheduleDay\(item, targetDayIdentity\) \{[\s\S]*?\n  \}/)?.[0];
assert.ok(sameDayFunction, "DAY 비교 함수가 있어야 한다");
const isSameScheduleDay = Function(sameDayFunction + "; return isSameScheduleDay;")();
assert.equal(isSameScheduleDay({scheduleDayIdentity: "trip-day:1"}, "trip-day:1"), true);
assert.equal(isSameScheduleDay({scheduleDayIdentity: "trip-day:2"}, "trip-day:1"), false);
assert.equal(isSameScheduleDay({scheduleDayIdentity: "draft-day:2026-09-01"}, "draft-day:2026-09-01"), true);
assert.equal(isSameScheduleDay({scheduleDayIdentity: "draft-day:2026-09-02"}, "draft-day:2026-09-01"), false);

// 자기 일정은 제외하고, 전체 보기에서는 같은 DAY의 다른 일정만 충돌 대상으로 삼는다.
const conflictCandidateFunction = scheduleSource.match(/function isTimeConflictCandidate\(other, item, targetDayIdentity, isAllScheduleVisible\) \{[\s\S]*?\n  \}/)?.[0];
assert.ok(conflictCandidateFunction, "시간 충돌 비교 함수가 있어야 한다");
const isTimeConflictCandidate = Function("isSameScheduleDay", conflictCandidateFunction + "; return isTimeConflictCandidate;")(isSameScheduleDay);
const dayOneItem = {itineraryItemId: 1, scheduleDayIdentity: "trip-day:1"};
assert.equal(isTimeConflictCandidate(dayOneItem, dayOneItem, "trip-day:1", true), false);
assert.equal(isTimeConflictCandidate({itineraryItemId: 2, scheduleDayIdentity: "trip-day:1"}, dayOneItem, "trip-day:1", true), true);
assert.equal(isTimeConflictCandidate({itineraryItemId: 3, scheduleDayIdentity: "trip-day:2"}, dayOneItem, "trip-day:1", true), false);
assert.equal(isTimeConflictCandidate({itineraryItemId: 3, scheduleDayIdentity: "trip-day:2"}, dayOneItem, "trip-day:1", false), true);

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
assert.match(guideSource, /entry\.adjustButton\.hidden = added \|\| \(!timeConflict && !unavailableTime\)/);

// 일정 화면의 AI 요청에는 현재 선택 DAY가 함께 전송되어 다른 DAY 추천이 섞이지 않는다.
assert.match(scheduleSource, /getActiveDayNumber: function \(\)/);
assert.match(guideSource, /selectedDayNumber: window\.AllMyTripsSchedule\?\.getActiveDayNumber\?\.\(\) \|\| null/);

// 일정을 다 짜면 예약으로 넘어갈 수 있어야 한다. tripId를 안 달고 가면 예약 화면이
// 목적지·날짜·인원을 채우지 못하고, 고른 항공편도 그 여행에 붙지 않는다.
const scheduleMarkup = fs.readFileSync(
  path.resolve(__dirname, "../../main/resources/templates/trips/schedule.html"), "utf8"
);
assert.match(scheduleMarkup, /data-schedule-booking/);
assert.match(scheduleSource, /"\/booking\/flights\?tripId=" \+ encodeURIComponent\(activeTripId\)/);
assert.match(scheduleSource, /먼저 여행을 저장해 주세요\./);

// 일치하는 예약 패널은 제목 행만 기본 노출하고, 건수 옆 +/- 버튼으로 상세를 접고 펼친다.
assert.match(scheduleMarkup, /data-booking-match-toggle/);
assert.match(scheduleMarkup, /data-booking-match-content[\s\S]*?hidden/);
assert.match(scheduleSource, /function setBookingMatchExpanded\(expanded\)/);
assert.match(scheduleSource, /bookingMatchToggleIcon\.textContent = expanded \? "−" : "\+";/);
assert.match(scheduleSource, /function alignScheduleMapToFirstItem\(\)/);
assert.match(scheduleSource, /bookingPanelOuterHeight = bookingMatchPanel\.getBoundingClientRect\(\)\.height/);
assert.match(scheduleSource, /itemTop - bodyTop - bookingPanelOuterHeight/);

// 방문 시각은 HTML 문자열에 끼워 넣지 않는다. 저장해 둔 값에 따옴표가 섞이면
// 속성을 빠져나와 태그로 읽히기 때문에, 칸을 만든 뒤 값으로만 채운다.
assert.doesNotMatch(scheduleSource, /value="\$\{currentHour\}"/);
assert.doesNotMatch(scheduleSource, /value="\$\{currentMinute\}"/);
assert.match(scheduleSource, /hourInput\.value = currentHour;/);
assert.match(scheduleSource, /minuteInput\.value = currentMinute;/);

// 예약 숙소는 일정 마지막에 기본 배치하되 기존 드래그 재정렬 흐름을 그대로 사용한다.
// 숙소가 마지막 목적지가 되더라도 체류시간을 만들지 않고 도착시간만 표시한다.
assert.match(scheduleSource, /function isArrivalOnlyAccommodation\(item\)/);
assert.match(scheduleSource, /if \(isArrivalOnlyAccommodation\(item\)\) return 0;/);
assert.match(scheduleSource, /endLabel\.textContent = arrivalOnly \? "도착"/);
assert.match(scheduleSource, /if \(candidate\.type === "flight"\) return 0;/);
assert.match(scheduleSource, /if \(candidate\.type === "accommodation"\) return items\.length;/);
assert.match(scheduleSource, /resolveBookingAccommodation\(candidate\)/);
assert.match(scheduleSource, /const bookingStartTime = isAccommodation \? null : bookingMatchTime\(candidate\);/);
assert.match(scheduleSource, /cursor \+= previousDuration \+ travelMinutes;/);
assert.match(scheduleSource, /\/items\/reorder/);
assert.match(scheduleSource, /추가한 예약 일정 정보를 확인할 수 없습니다\./);

const scheduleStyle = fs.readFileSync(
  path.resolve(__dirname, "../../main/resources/static/css/pages/trips/schedule.css"), "utf8"
);
assert.match(scheduleStyle, /\.schedule-booking-match-content\[hidden\]/);
assert.match(scheduleStyle, /\.schedule-time-editor\.is-arrival-only \.schedule-time-options/);
assert.match(scheduleStyle, /\.schedule-workspace-footer > \.schedule-back-button,[\s\S]*?\.schedule-workspace-footer > \.schedule-save-button,[\s\S]*?\.schedule-workspace-footer > \.schedule-booking-button \{[\s\S]*?height: 48px;[\s\S]*?font-size: 13px;/);

const insertionFunction = scheduleSource.match(
  /function bookingMatchInsertionIndex\(candidate, items\) \{[\s\S]*?\n  \}/
)?.[0];
assert.ok(insertionFunction, "예약 일정 삽입 위치 계산 함수가 있어야 한다");
const bookingMatchInsertionIndex = Function(
  "toMinutes",
  "bookingMatchTime",
  "getItemStartTime",
  insertionFunction + "; return bookingMatchInsertionIndex;"
)(
  function (value) {
    if (!value) return null;
    const [hour, minute] = String(value).split(":").map(Number);
    return hour * 60 + minute;
  },
  function (candidate) { return candidate.time || null; },
  function (item) { return item.startTime || ""; }
);
assert.equal(bookingMatchInsertionIndex({type: "accommodation"}, [{startTime: "09:00"}]), 1);
assert.equal(bookingMatchInsertionIndex(
  {type: "flight", leg: 0, time: "11:00"},
  [{startTime: "09:00"}, {startTime: "12:00"}]
), 0);
assert.equal(bookingMatchInsertionIndex(
  {type: "flight", leg: 1, time: null},
  [{startTime: "09:00"}, {startTime: "12:00"}]
), 0);
assert.match(scheduleSource, /function resetExistingScheduleTimesForFlight/);
assert.match(scheduleSource, /scheduleItemUpdatePayload\(item, targetDay, null, null\)/);
assert.match(scheduleSource, /delete overrides\[getScheduleTimeKey\(item\)\]/);
assert.match(scheduleSource, /delete dayStarts\[getScheduleDayStartKey\(targetDay\)\]/);

// 예약에서 추가한 항공·숙소를 삭제하면 예약 목록을 다시 읽어 추가 버튼을 복구한다.
assert.match(scheduleSource, /const deletedBookingMatch = isBookingScheduleItem\(item\);/);
assert.match(scheduleSource, /if \(deletedBookingMatch\) \{[\s\S]*?await loadBookingMatches\(scheduleDays\);[\s\S]*?\}/);

console.log("schedule time acceptance checks passed");
