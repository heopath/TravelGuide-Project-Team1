/*
 * 티켓 예약 대기열 부하 테스트 (k6)
 *
 * 실행
 *   k6 run load-test/booking-queue.js
 *   k6 run -e VUS=50 -e SLOT_ID=31 -e TRIP_ID=10 load-test/booking-queue.js
 *
 * 반드시 로컬에서만 돌린다. 운영 EC2·RDS에 쏘면 부하로 만든 예약이 그대로 남아
 * 운영 지표와 예약 모니터링 숫자가 오염된다.
 *
 * 이 스크립트가 재는 것
 *   - 몰릴 때 대기열이 순번을 제대로 주는지
 *   - READY까지 걸리는 시간
 *   - 예약 완료가 재고 안에서만 성공하는지
 *
 * 재지 못하는 것
 *   - "두 사람이 같은 자리를 받았는가" 같은 정확성. 그건 JUnit 동시성 테스트가 본다
 *     (BookingQueueConcurrencyTest). 부하 도구는 처리량을 보여줄 뿐 불변식을 검사하지 않는다.
 */
import http from "k6/http";
import { check, sleep, fail } from "k6";
import { Counter, Trend } from "k6/metrics";

const BASE = __ENV.BASE_URL || "http://localhost:8080";
const VUS = Number(__ENV.VUS || 30);
const SLOT_ID = Number(__ENV.SLOT_ID || 31);
/*
 * 예약은 여행 소유자만 할 수 있다. 모든 VU가 같은 TRIP_ID를 쓰면 소유자 한 명만 성공하고
 * 나머지는 전부 거부되어 측정값이 무의미해진다. 기본값은 로그인한 계정의 여행을 직접 찾는 것이고,
 * TRIP_ID는 VU 한 개로 디버깅할 때만 쓴다.
 */
const TRIP_ID = Number(__ENV.TRIP_ID || 0);
const TRIP_DESTINATION = __ENV.TRIP_DESTINATION || "부하테스트";
const PASSWORD = __ENV.PASSWORD || "Test1234!";
/* 계정은 부하용으로 미리 만들어 둔다. loadtest1@example.com ~ loadtestN@example.com */
const EMAIL_PREFIX = __ENV.EMAIL_PREFIX || "loadtest";

const admitted = new Counter("queue_admitted");
const reserved = new Counter("queue_reserved");
const soldOut = new Counter("queue_sold_out");
const expired = new Counter("queue_expired");
const waitToReady = new Trend("queue_wait_to_ready_ms", true);

export const options = {
  scenarios: {
    /*
     * 한 번에 몰아넣는다. 대기열은 "동시에 들어올 때" 동작하는 장치라,
     * 천천히 늘리는 ramping으로는 capacityPerSecond(5)를 넘기지 못해 아무도 대기하지 않는다.
     */
    burst: {
      executor: "per-vu-iterations",
      vus: VUS,
      iterations: 1,
      maxDuration: "3m",
    },
  },
  thresholds: {
    /* 대기열이 있는데도 500이 나오면 설계가 아니라 버그다. */
    http_req_failed: ["rate<0.05"],
    checks: ["rate>0.95"],
  },
};

/*
 * 재고 소진(409)은 대기열이 제대로 도는 증거이지 실패가 아니다.
 * k6는 4xx를 기본으로 실패로 세므로, 예약 완료 호출에서만 409를 정상으로 인정한다.
 * 전역 기본값을 좁게 두어 다른 요청의 409는 그대로 실패로 잡히게 한다.
 */
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 399 }));
/* 대기표 만료(410)도 설계된 동작이다. 만료를 재려면 실패로 세지 않아야 한다. */
const COMPLETE_EXPECTED_ALL = http.expectedStatuses(200, 409, 410);
const STATUS_EXPECTED = http.expectedStatuses(200, 410);

function api(path) {
  return `${BASE}${path}`;
}

/** CSRF 발급 → 로그인 → CSRF 재발급. k6는 VU마다 쿠키 항아리를 따로 갖는다. */
function login(vu) {
  const email = `${EMAIL_PREFIX}${vu}@example.com`;

  /* 로그인도 CSRF 보호 대상이라 토큰을 먼저 받아야 한다. 없이 보내면 403이 돌아온다. */
  const pre = http.get(api("/api/v1/csrf"), { tags: { step: "csrf" } });
  if (pre.status !== 200) fail(`CSRF 발급 실패: ${pre.status}`);
  const preBody = pre.json();

  const res = http.post(api("/api/v1/auth/login"),
    JSON.stringify({ email, password: PASSWORD }),
    {
      headers: { "Content-Type": "application/json", [preBody.headerName]: preBody.token },
      tags: { step: "login" },
    });

  if (res.status !== 200) {
    fail(`로그인 실패 (${email}): ${res.status} ${res.body}`);
  }

  /* 인증되면 세션이 바뀌므로 토큰을 다시 받는다. */
  const csrf = http.get(api("/api/v1/csrf"), { tags: { step: "csrf" } });
  if (csrf.status !== 200) fail(`CSRF 발급 실패: ${csrf.status}`);
  const body = csrf.json();
  return { [body.headerName]: body.token, "Content-Type": "application/json" };
}

/** 로그인한 계정이 소유한 부하 테스트용 여행을 찾는다. */
function resolveTripId(headers) {
  if (TRIP_ID) return TRIP_ID;

  const res = http.get(api("/api/v1/trips"), { headers, tags: { step: "trips" } });
  if (res.status !== 200) fail(`여행 목록 조회 실패: ${res.status} ${res.body}`);

  const trips = res.json("data") || [];
  const target = trips.find((trip) => trip.destinationName === TRIP_DESTINATION) || trips[0];
  if (!target) fail(`VU ${__VU}의 여행이 없습니다. fixtures.sql을 먼저 실행하세요.`);
  return target.tripId;
}

export default function () {
  const headers = login(__VU);
  const tripId = resolveTripId(headers);

  /* 요청 키는 VU마다 달라야 한다. 같으면 서버가 같은 요청으로 보고 기존 예약을 돌려준다. */
  const requestKey = `k6-${__VU}-${Date.now()}`;

  const enqueue = http.post(api("/api/v1/booking-queue/entries"),
    JSON.stringify({ tripId, slotId: SLOT_ID, quantity: 1, requestKey }),
    { headers, tags: { step: "enqueue" } });

  const ok = check(enqueue, { "대기열 진입 200": (r) => r.status === 200 });
  if (!ok) return;

  const entry = enqueue.json("data");
  const token = entry.token;
  const startedAt = Date.now();

  let state = entry.status;
  /* READY가 될 때까지 순번을 확인한다. 화면 폴링 주기와 같은 3초를 쓴다. */
  let attempts = 0;
  while (state === "WAITING" && attempts < 40) {
    sleep(3);
    attempts += 1;
    const status = http.get(api(`/api/v1/booking-queue/entries/${token}`),
      { headers, tags: { step: "status" }, responseCallback: STATUS_EXPECTED });
    /*
     * 만료는 본문의 status가 아니라 HTTP 410 + BOOKING_QUEUE_EXPIRED로 온다.
     * (BookingQueueService가 예외를 던지므로 data가 없다.)
     * 200만 보고 끊으면 만료가 "차례가 오지 않음"으로 잡혀 영영 0건이 된다.
     */
    if (status.status === 410) {
      state = "EXPIRED";
      break;
    }
    if (status.status !== 200) break;
    state = status.json("data.status");
  }

  if (state === "EXPIRED") {
    expired.add(1);
    return;
  }
  if (state !== "READY") {
    /* 40번을 기다려도 차례가 안 왔다. 대기열이 막힌 것이므로 실패로 본다. */
    check(null, { "제한 시간 안에 차례가 왔다": () => false });
    return;
  }

  admitted.add(1);
  waitToReady.add(Date.now() - startedAt);

  const complete = http.post(api(`/api/v1/booking-queue/entries/${token}/reservation`),
    null, { headers, tags: { step: "complete" }, responseCallback: COMPLETE_EXPECTED_ALL });

  /*
   * 재고가 동나면 TICKET_NOT_AVAILABLE(409)가 돌아온다. 이건 정상 동작이다.
   * 부하 테스트에서 확인할 것은 "500이 나지 않는 것"과 "성공 수가 재고를 넘지 않는 것"이다.
   */
  const code = complete.json("code");
  if (complete.status === 200) {
    reserved.add(1);
  } else if (code === "TICKET_NOT_AVAILABLE") {
    soldOut.add(1);
  } else if (code === "BOOKING_QUEUE_EXPIRED") {
    /* 차례를 받고도 admission-ttl 안에 예약을 못 마친 경우다. 이것도 정상 동작이다. */
    expired.add(1);
  }

  check(complete, {
    "예약 완료가 5xx로 실패하지 않는다": (r) => r.status < 500,
    "성공·재고 소진·대기표 만료 중 하나다": (r) =>
      r.status === 200 || code === "TICKET_NOT_AVAILABLE" || code === "BOOKING_QUEUE_EXPIRED",
  });
}

export function handleSummary(data) {
  const line = (name) => data.metrics[name]?.values?.count ?? 0;
  /* 이 값을 보고 capacity-per-second를 정하므로 대기 시간은 반드시 함께 출력한다. */
  const wait = data.metrics["queue_wait_to_ready_ms"]?.values;
  const ms = (value) => (value == null ? "-" : `${Math.round(value)}ms`);
  const pct = (value) => (value == null ? "-" : `${(value * 100).toFixed(1)}%`);

  /*
   * 이 요약이 k6 기본 출력을 대체하므로, 임계값에 쓰는 지표를 여기서 직접 찍는다.
   * 안 찍으면 통과했는지 여부를 종료 코드로만 알 수 있어 회차 기록에 남길 숫자가 없다.
   */
  const failed = data.metrics["http_req_failed"]?.values;
  const checks = data.metrics["checks"]?.values;
  const duration = data.metrics["http_req_duration"]?.values;
  const mark = (ok) => (ok ? "OK" : "확인 필요");

  const summary = [
    "",
    "── 대기열 결과 ──",
    `입장(READY) : ${line("queue_admitted")}`,
    `예약 성공    : ${line("queue_reserved")}`,
    `재고 소진    : ${line("queue_sold_out")}`,
    `대기표 만료  : ${line("queue_expired")}`,
    "",
    "── READY까지 걸린 시간 ──",
    `평균 ${ms(wait?.avg)} · 중앙 ${ms(wait?.med)} · p95 ${ms(wait?.["p(95)"])} · 최대 ${ms(wait?.max)}`,
    "",
    "── 임계값 ──",
    `요청 실패율   : ${pct(failed?.rate)}  (기준 5% 미만, ${mark((failed?.rate ?? 0) < 0.05)})`,
    `체크 통과율   : ${pct(checks?.rate)}  (기준 95% 초과, ${mark((checks?.rate ?? 0) > 0.95)})`,
    `응답 시간 p95 : ${ms(duration?.["p(95)"])}`,
    "",
    "예약 성공 수가 시간대 재고를 넘으면 대기열이 아니라 재고 처리에 문제가 있는 것이다.",
    "그 경우 BookingQueueConcurrencyTest로 좁혀서 확인한다.",
    "",
  ].join("\n");
  return { stdout: summary };
}
