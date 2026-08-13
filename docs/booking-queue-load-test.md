# 티켓 예약 대기열 동시 접속 테스트

## 도구 선택

k6를 사용한다. 단일 JavaScript 파일로 동시 도착률을 고정할 수 있고, 응답 시간·실패율·대기열 진입 비율을 한 번에 남긴다. 브라우저 클릭 반복과 달리 실제 로그인 세션, CSRF 토큰, Redis 대기열 API를 모두 통과한다.

## 사전 준비

1. PostgreSQL과 Redis를 실행한다.
2. 애플리케이션을 `local` 프로필로 실행한다.
3. 테스트할 티켓 `slotId`와 로그인 가능한 일반 회원 계정을 준비한다.
4. 각 계정이 소유한 `tripId`를 확인한다. 대기열 진입만 측정하므로 티켓 사용일이 여행 기간 안일 필요는 없지만, 예약 완료까지 시험할 때는 기간이 겹쳐야 한다.

실제 순번 증가와 사용자별 공정성을 보려면 가상 사용자 수만큼 서로 다른 계정을 쓴다. 계정이 부족하면 같은 계정을 여러 VU가 재사용하며, 동일 사용자·동일 시간대 요청은 하나의 순번으로 합쳐진다.

## 실행

PowerShell에서 실제 테스트 계정만 환경 변수로 넣는다. 비밀번호나 운영 세션 쿠키는 파일에 저장하지 않는다.

```powershell
$env:BASE_URL='http://localhost:8080'
$env:SLOT_ID='31'
$env:RATE='20'
$env:DURATION='30s'
$env:QUEUE_TEST_ACCOUNTS='[{"email":"load-user-1@example.com","password":"test-password","tripId":10},{"email":"load-user-2@example.com","password":"test-password","tripId":11}]'
k6 run scripts/load/booking-queue.js
```

대기열을 빠르게 확인하려면 서버를 실행하기 전에 직접 통과량을 낮춘다.

```powershell
$env:BOOKING_QUEUE_CAPACITY_PER_SECOND='3'
./gradlew.bat bootRun --args='--spring.profiles.active=local'
```

## 합격 기준

- `checks`: 99% 초과
- `http_req_failed`: 1% 미만
- `booking_queue_entry_duration` p95: 1초 미만
- `booking_queue_waiting`: 요청률이 서버 허용량을 넘었을 때 0보다 커야 함
- Redis에서 같은 사용자·같은 시간대가 중복 순번으로 늘어나지 않아야 함

스크립트는 각 반복이 끝날 때 만든 순번을 취소한다. 실제 예약 완료 부하와 재고 잠금까지 측정할 때는 취소 부분을 제거하고, 테스트 전용 상품·여행·계정만 사용한다.
