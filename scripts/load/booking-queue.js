import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";

const baseUrl = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/$/, "");
const slotId = Number(__ENV.SLOT_ID || 31);
const rate = Number(__ENV.RATE || 20);
const accounts = JSON.parse(__ENV.QUEUE_TEST_ACCOUNTS || "[]");
const queuedRate = new Rate("booking_queue_waiting");
const entryDuration = new Trend("booking_queue_entry_duration", true);
let authenticated = false;
let csrfToken = "";

export const options = {
  scenarios: {
    simultaneous_ticket_requests: {
      executor: "constant-arrival-rate",
      rate,
      timeUnit: "1s",
      duration: __ENV.DURATION || "30s",
      preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || Math.max(rate, 10)),
      maxVUs: Number(__ENV.MAX_VUS || Math.max(rate * 3, 30))
    }
  },
  thresholds: {
    checks: ["rate>0.99"],
    http_req_failed: ["rate<0.01"],
    booking_queue_entry_duration: ["p(95)<1000"]
  }
};

function accountForVu() {
  if (!accounts.length) {
    throw new Error("QUEUE_TEST_ACCOUNTS에 테스트 계정 JSON 배열을 지정해야 합니다.");
  }
  return accounts[(__VU - 1) % accounts.length];
}

function authenticate(account) {
  const csrf = http.get(`${baseUrl}/api/v1/csrf`, {
    headers: { Accept: "application/json" }, tags: { name: "csrf" }
  });
  check(csrf, { "CSRF 토큰 발급": (response) => response.status === 200 });
  csrfToken = csrf.json("token");

  const login = http.post(`${baseUrl}/api/v1/auth/login`, JSON.stringify({
    email: account.email,
    password: account.password
  }), {
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      "X-CSRF-TOKEN": csrfToken
    },
    tags: { name: "login" }
  });
  check(login, { "테스트 계정 로그인": (response) => response.status === 200 && response.json("success") === true });
  authenticated = login.status === 200;
}

function writeHeaders() {
  return {
    "Content-Type": "application/json",
    Accept: "application/json",
    "X-CSRF-TOKEN": csrfToken
  };
}

export default function () {
  const account = accountForVu();
  if (!authenticated) authenticate(account);
  if (!authenticated) return;

  const request = {
    tripId: Number(account.tripId),
    slotId,
    quantity: 1,
    requestKey: `k6-${__VU}-${__ITER}-${Date.now()}`
  };
  const entry = http.post(`${baseUrl}/api/v1/booking-queue/entries`, JSON.stringify(request), {
    headers: writeHeaders(), tags: { name: "queue-entry" }
  });
  entryDuration.add(entry.timings.duration);
  const accepted = check(entry, {
    "대기열 진입 성공": (response) => response.status === 200 && response.json("success") === true,
    "READY 또는 WAITING 반환": (response) => ["READY", "WAITING"].includes(response.json("data.status"))
  });
  if (!accepted) return;

  const status = entry.json("data.status");
  const token = entry.json("data.token");
  queuedRate.add(status === "WAITING");

  if (status === "WAITING") {
    const poll = http.get(`${baseUrl}/api/v1/booking-queue/entries/${token}`, {
      headers: { Accept: "application/json" }, tags: { name: "queue-status" }
    });
    check(poll, { "대기 순번 조회 성공": (response) => response.status === 200 });
  }

  sleep(Number(__ENV.HOLD_SECONDS || 0.2));
  const leave = http.del(`${baseUrl}/api/v1/booking-queue/entries/${token}`, null, {
    headers: writeHeaders(), tags: { name: "queue-cancel" }
  });
  check(leave, { "테스트 순번 정리": (response) => response.status === 200 || response.status === 410 });
}
