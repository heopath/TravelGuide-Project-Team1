/*
 * 여행이 끝났는지 판단하는 규칙 한 곳.
 *
 * 서버의 TripStatus.isFinished와 같은 규칙이다. 두 곳이 어긋나면 화면에는 기록 버튼이
 * 보이는데 눌러도 거절당하는 상태가 된다. 규칙을 바꿀 때는 양쪽을 같이 바꾼다.
 *
 * COMPLETED로 상태를 바꾸는 코드가 없어 날짜가 지나도 CONFIRMED에 머문다. 그래서
 * 종료일로도 판단한다. 확정한 여행만 세고(초안은 아직 여행이 아니고 취소는 다녀오지
 * 않은 여행이다), 종료일 당일은 아직 여행 중으로 본다.
 */
(function () {
  "use strict";

  function isTripFinished(trip, today) {
    if (!trip) return false;
    if (trip.status === "COMPLETED") return true;
    if (trip.status !== "CONFIRMED") return false;
    if (!trip.endDate) return false;

    const end = new Date(trip.endDate + "T00:00:00");
    if (Number.isNaN(end.getTime())) return false;

    const now = today ? new Date(today) : new Date();
    const todayMidnight = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    return end < todayMidnight;
  }

  window.AllMyTripsTripStatus = { isTripFinished: isTripFinished };
})();
