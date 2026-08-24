# 부하 테스트

티켓 예약 대기열이 몰릴 때 재고를 정확히 지키는지 확인합니다.

> ⚠️ **로컬에서만 돌립니다.** 운영 EC2·RDS에 쏘면 가짜 예약이 남아 운영 지표가 오염됩니다.
> t3.micro에서는 부하를 쏘는 쪽과 받는 쪽이 CPU를 나눠 써서 측정값도 의미가 없습니다.

| 파일 | 역할 |
|---|---|
| `booking-queue.js` | k6 스크립트 |
| `fixtures.sql` | 계정 30개·상품·시간대(재고 10) 준비 |
| `reset.sql` | **회차 사이 초기화** — 다시 돌리기 전에 실행 |
| `result-baseline.json` | 기준 측정값 (아래 참고) |

## 준비

k6를 설치합니다.

```bash
winget install --id GrafanaLabs.k6 -e
```

앱과 DB를 띄웁니다.

```bash
docker compose up -d
```

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

**화면에서 `loadtest1@example.com`으로 회원가입합니다.** 비밀번호 해시는 스크립트가 만들지 않고, 이 계정의 것을 나머지 29개가 복사해 씁니다. 본인 계정 비밀번호는 쓰지 마세요 — 나중에 명령줄에 그 값을 적게 됩니다.

```bash
docker compose exec -T postgres psql -U allmytrips -d all_my_trips -f - < load-test/fixtures.sql
```

끝에 나오는 표의 `slot_id`를 적어둡니다.

## 실행

```bash
k6 run -e VUS=30 -e SLOT_ID=<slot_id> -e PASSWORD=<정한 비밀번호> load-test/booking-queue.js
```

**재고 10개에 30명이 한꺼번에 몰립니다. 10명만 성공하고 20명은 거절돼야 정상입니다.**

`VUS`는 계정 수(기본 30)를 넘기면 안 됩니다. VU 번호로 계정을 고르므로 넘기면 없는 계정으로 로그인해 회차 전체가 실패합니다. 더 크게 돌리려면 `psql`에 `-v accounts=60`을 넘겨 계정을 먼저 늘리세요.

## 다시 돌리기 전에

```bash
docker compose exec -T postgres psql -U allmytrips -d all_my_trips -f - < load-test/reset.sql
```

**빼먹으면 안 됩니다.** 예약을 지워도 `reserved_quantity`는 따라 내려가지 않습니다. 그대로 두면 다음 회차가 줄어든 재고로 시작해, 재고 때문에 거절된 것을 대기열 문제로 오해하게 됩니다.

## 기준 측정값

`result-baseline.json`은 아래 조건으로 잰 값입니다. 개선 전후를 비교할 때 기준으로 씁니다.

| 조건 | 값 |
|---|---|
| 측정일 | 2026-08-24 |
| 기준 커밋 | `5413025` (v0.12.0) |
| VUS | 30 |
| 재고 | 10 |
| `POLL_SECONDS` | 1.5 (기본값) |
| 환경 | 로컬 (Docker PostgreSQL + Redis) |

| 지표 | 결과 |
|---|---|
| 입장(READY) | 30 |
| **예약 성공** | **10** — 재고와 일치 |
| 재고 소진 거절 | 20 |
| 대기표 만료 | 0 |
| READY까지 평균 | 3,784ms |
| READY까지 p95 | 7,566ms |
| 응답 시간 p95 | 887ms |
| 요청 실패율 | 0.0% |

비교하려면 **같은 조건으로** 돌려야 합니다. 특히 `POLL_SECONDS`를 바꾸면 체감 대기가 통째로 달라져 회차 간 비교가 성립하지 않습니다.

```bash
k6 run --summary-export=load-test/result-after.json -e VUS=30 -e SLOT_ID=<slot_id> -e PASSWORD=<값> load-test/booking-queue.js
```

## 이 테스트가 재지 못하는 것

**"두 사람이 같은 자리를 받았는가"** 같은 정확성은 재지 못합니다. 부하 도구는 처리량을 보여줄 뿐 불변식을 검사하지 않습니다. 그건 `BookingQueueConcurrencyTest`가 봅니다.
