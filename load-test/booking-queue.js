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
const TRIP_ID = Number(__ENV.TRIP_ID || 10);
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

function api(path) {
  return `${BASE}${path}`;
}

/** 로그인 → 세션 쿠키 확보 → CSRF 토큰 발급. k6는 VU마다 쿠키 항아리를 따로 갖는다. */
function login(vu) {
  const email = `${EMAIL_PREFIX}${vu}@example.com`;
  const res = http.post(api("/api/v1/auth/login"),
    JSON.stringify({ email, password: PASSWORD }),
    { headers: { "Content-Type": "application/json" }, tags: { step: "login" } });

  if (res.status !== 200) {
    fail(`로그인 실패 (${email}): ${res.status} ${res.body}`);
  }

  const csrf = http.get(api("/api/v1/csrf"), { tags: { step: "csrf" } });
  if (csrf.status !== 200) fail(`CSRF 발급 실패: ${csrf.status}`);
  const body = csrf.json();
  return { [body.headerName]: body.token, "Content-Type": "application/json" };
}

export default function () {
  const headers = login(__VU);

  /* 요청 키는 VU마다 달라야 한다. 같으면 서버가 같은 요청으로 보고 기존 예약을 돌려준다. */
  const requestKey = `k6-${__VU}-${Date.now()}`;

  const enqueue = http.post(api("/api/v1/booking-queue/entries"),
    JSON.stringify({ tripId: TRIP_ID, slotId: SLOT_ID, quantity: 1, requestKey }),
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
      { headers, tags: { step: "status" } });
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
    null, { headers, tags: { step: "complete" } });

  /*
   * 재고가 동나면 TICKET_NOT_AVAILABLE(409)가 돌아온다. 이건 정상 동작이다.
   * 부하 테스트에서 확인할 것은 "500이 나지 않는 것"과 "성공 수가 재고를 넘지 않는 것"이다.
   */
  const code = complete.json("code");
  if (complete.status === 200) {
    reserved.add(1);
  } else if (code === "TICKET_NOT_AVAILABLE") {
    soldOut.add(1);
  }

  check(complete, {
    "예약 완료가 5xx로 실패하지 않는다": (r) => r.status < 500,
    "성공이거나 재고 소진이다": (r) => r.status === 200 || code === "TICKET_NOT_AVAILABLE",
  });
}

export function handleSummary(data) {
  const line = (name) => data.metrics[name]?.values?.count ?? 0;
  const summary = [
    "",
    "── 대기열 결과 ──",
    `입장(READY) : ${line("queue_admitted")}`,
    `예약 성공    : ${line("queue_reserved")}`,
    `재고 소진    : ${line("queue_sold_out")}`,
    `대기표 만료  : ${line("queue_expired")}`,
    "",
    "예약 성공 수가 시간대 재고를 넘으면 대기열이 아니라 재고 처리에 문제가 있는 것이다.",
    "그 경우 BookingQueueConcurrencyTest로 좁혀서 확인한다.",
    "",
  ].join("\n");
  return { stdout: summary };
}
