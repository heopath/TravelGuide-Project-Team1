# 티켓 예약 대기열 부하 테스트

> 이 문서는 GitHub Wiki에 옮겨 쓸 초안입니다. 저장소에도 두는 이유는 스크립트와 함께
> 버전이 움직여야 하기 때문입니다. 스크립트가 바뀌면 이 문서도 같은 PR에서 고칩니다.

## 먼저 — 도구 하나로는 안 됩니다

확인해야 할 것이 성격이 다른 두 가지입니다.

| 질문 | 무엇으로 | 왜 |
| --- | --- | --- |
| 재고 10개인데 11명이 예약되지 않았나 | **JUnit 동시성 테스트** | 부하 도구는 처리량을 재지 불변식을 검사하지 않습니다 |
| 1000명이 몰리면 대기열이 순번을 제대로 주나 | **k6** | 실제 동시 접속과 응답 시간이 필요합니다 |

부하 도구는 "초당 몇 건을 처리했다"를 알려줍니다. **"두 사람이 같은 자리를 받았는가"는 알려주지 않습니다.** 그래서 정확성은 코드 안에서 따로 봅니다.

---

## 1. 도구 비교

| 도구 | 언어 | 설치 | 로그인·CSRF | 이 프로젝트 적합도 |
| --- | --- | --- | --- | --- |
| **k6** | JavaScript | 단일 실행 파일 | 쿠키 자동, 헤더 조작 쉬움 | **채택** |
| JMeter | GUI/XML | JVM + GUI | 쿠키 매니저·정규식 추출기로 가능 | 팀원 배포와 스크립트 리뷰가 번거로움 |
| Gatling | Scala/Java DSL | JVM + 빌드 도구 | 가능 | 리포트는 최고, 학습 비용이 큼 |
| Locust | Python | Python 환경 | 가능 | 파이썬을 안 쓰는 팀이라 환경이 하나 늘어남 |
| Artillery | JavaScript/YAML | Node | 가능 | k6와 겹치는데 시나리오 표현력이 약함 |
| `ab`, `wrk` | — | 단일 실행 파일 | **불가** | 로그인·CSRF를 못 다뤄 401·403만 받습니다 |

### k6를 고른 이유

**로그인과 CSRF가 실질적인 기준이었습니다.** 예약 API는 세션 인증 + CSRF 토큰이 필요합니다. 단순히 URL을 두드리는 도구(`ab`, `wrk`)로는 인증 벽을 넘지 못해 아무것도 재지 못합니다.

남은 후보 중에서는

- **설치가 실행 파일 하나**라 팀원이 따라 하기 쉽습니다. JVM이나 파이썬 환경을 새로 맞출 필요가 없습니다
- **스크립트가 JavaScript**입니다. 이 저장소는 이미 `src/test/js`에 수용 기준 테스트를 JS로 쓰고 있어 읽는 사람이 같습니다
- **가상 사용자(VU) 모델**이 "N명이 동시에"와 그대로 대응됩니다. 대기열 순번 검증에 맞습니다
- 스크립트가 텍스트라 **PR에서 리뷰가 됩니다.** JMeter의 `.jmx`는 XML이라 diff를 읽기 어렵습니다

### 고르지 않은 이유

**JMeter** — 국내 자료가 많고 GUI로 시작하기 쉽습니다. 다만 결과를 팀에 공유하려면 GUI 설정을 통째로 넘겨야 하고, `.jmx` 파일은 변경 이력이 사실상 안 보입니다.

**Gatling** — 리포트가 가장 보기 좋습니다. 하지만 Scala DSL(또는 Java DSL)을 새로 익혀야 하고, 이 프로젝트에서 재려는 것이 그 학습 비용을 정당화할 만큼 복잡하지 않습니다.

---

## 2. 준비물

### 반드시 로컬에서만 돌립니다

**운영 EC2·RDS에 쏘지 않습니다.** 부하로 만든 예약이 `reservations`에 그대로 쌓여 운영 지표(오늘 예약)와 예약 모니터링 숫자가 오염됩니다. 티켓 재고도 실제로 깎입니다.

### 필요한 것

- 로컬 PostgreSQL, Redis (대기열이 Redis를 씁니다. 없으면 대기열이 꺼져 아무도 대기하지 않습니다)
- `local` 프로필로 띄운 애플리케이션

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

- k6 ([설치](https://k6.io/docs/get-started/installation/))
- 부하용 계정·여행·티켓 시간대 → **`load-test/fixtures.sql`**

### 데이터 준비

`load-test/fixtures.sql`을 순서대로 실행합니다. 계정 30개, 계정마다 여행 1개, 재고 10개짜리 전용 시간대를 만듭니다.

**비밀번호 해시를 SQL에 적지 않습니다.** BCrypt라 손으로 만들 수 없고, 자기 계정 해시를 복사하면 그 비밀번호를 k6 스크립트에 적게 됩니다. 대신 이렇게 합니다.

1. 화면에서 `loadtest1@example.com`으로 **회원가입**합니다. 비밀번호는 부하 테스트용으로만 쓸 값을 정합니다
2. SQL을 실행하면 나머지 29개가 **1번 계정의 해시를 복사**해 같은 비밀번호로 만들어집니다

**시드 데이터를 쓰지 않고 전용 상품을 새로 만듭니다.** 시드 상품에 부하를 걸면 재고가 실제로 깎여 다음 사람이 화면을 확인할 때 품절로 보입니다.

파일 끝에 회차 사이 초기화 구문과 전체 정리 구문이 주석으로 들어 있습니다. **한 번 돌리면 재고가 소진되므로 다시 돌리기 전에 초기화가 필요합니다.** Redis에 남은 줄(`all-my-trips:booking-queue:*`)도 함께 비웁니다.

---

## 3. 정확성 — 재고 초과 예약이 없는지

부하를 걸기 **전에** 이것부터 확인합니다. 여기서 깨지면 부하 결과를 볼 이유가 없습니다.

```bash
BOOKING_QUEUE_CONCURRENCY_TEST=true ./gradlew test --tests "*BookingQueueConcurrencyTest*"
```

재고 10개에 30명을 `CountDownLatch`로 한 지점에 모았다가 동시에 풀어 다음을 확인합니다.

- `ticket_inventory.reserved_quantity`가 재고를 넘지 않는다
- 성공한 요청 수와 실제 저장된 예약 수가 같다
- 성공 + 실패 = 전체 요청 수

**환경변수가 없으면 skip됩니다.** 저장소에 testcontainers도 H2도 없어 실제 DB가 필요하고, 매번 도는 테스트로 두면 DB가 없는 사람의 빌드가 깨집니다.

---

## 4. 부하 — 대기열이 순번을 제대로 주는지

```bash
k6 run load-test/booking-queue.js
k6 run -e VUS=50 -e SLOT_ID=31 load-test/booking-queue.js
```

k6를 설치하지 않았다면 도커 이미지로 돌려도 됩니다. 컨테이너에서 호스트를 부르므로 주소만 바꿉니다.

```bash
docker run --rm -i --add-host=host.docker.internal:host-gateway \
  -e BASE_URL=http://host.docker.internal:8090 -e VUS=30 -e SLOT_ID=1 -e PASSWORD='...' \
  grafana/k6 run - < load-test/booking-queue.js
```

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `BASE_URL` | `http://localhost:8080` | 대상 주소 |
| `VUS` | `30` | 동시 사용자 수 |
| `SLOT_ID` | `31` | 부하를 걸 티켓 시간대 |
| `TRIP_ID` | (없음) | **평소에는 넘기지 않습니다.** 예약은 여행 소유자만 할 수 있어 모든 VU가 같은 여행을 쓰면 한 명만 성공합니다. 지정하지 않으면 각 VU가 자기 여행을 찾습니다. VU 하나로 디버깅할 때만 씁니다 |
| `TRIP_DESTINATION` | `부하테스트` | VU가 자기 여행을 고를 때 쓰는 목적지명 |
| `EMAIL_PREFIX` | `loadtest` | 계정 이메일 접두사 |
| `PASSWORD` | `Test1234!` | 계정 공통 비밀번호 |

### 시나리오를 `per-vu-iterations`로 둔 이유

대기열은 **동시에 들어올 때** 동작하는 장치입니다. 천천히 늘리는 `ramping-vus`로는 `booking.queue.capacity-per-second`(기본 5)를 넘기지 못해 **아무도 대기하지 않고 지나갑니다.** 그러면 대기열을 켜 두고도 대기열을 시험하지 못합니다.

### 흐름

```
로그인 → CSRF 발급
  → POST /api/v1/booking-queue/entries        (줄 서기)
  → GET  /api/v1/booking-queue/entries/{token} (3초마다 순번 확인)
  → POST /api/v1/booking-queue/entries/{token}/reservation  (차례가 오면 예약)
```

폴링 주기 3초는 화면(`queue.js`)과 같은 값입니다. 다르게 주면 실제 사용자와 다른 부하를 만듭니다.

### 결과 읽는 법

```
── 대기열 결과 ──
입장(READY) : 30
예약 성공    : 10
재고 소진    : 20
대기표 만료  : 0
```

| 지표 | 정상 | 이상하면 |
| --- | --- | --- |
| 예약 성공 | **재고 수 이하** | 넘으면 대기열이 아니라 재고 처리 문제 → 3번 테스트로 좁힙니다 |
| 재고 소진(409) | 정상입니다 | 실패가 아닙니다. 재고보다 사람이 많으면 당연히 나옵니다 |
| 대기표 만료(410) | 0에 가깝게 | 많으면 `entry-ttl`·`admission-ttl`이 짧거나 처리가 느립니다 |
| `http_req_failed` | 5% 미만 | 5xx가 섞이면 설계가 아니라 버그입니다 |
| `queue_wait_to_ready_ms` | — | `capacity-per-second`를 바꿔가며 비교합니다 |

**409는 실패가 아닙니다.** 재고가 동나면 나오는 정상 응답입니다. 5xx만 실패로 봅니다.

**410(대기표 만료)도 실패가 아닙니다.** 만료는 응답 본문의 `status`가 아니라 **HTTP 410 + `BOOKING_QUEUE_EXPIRED`** 로 옵니다. 서버가 예외를 던지므로 `data`가 없습니다. 만료를 세려면 상태 코드로 판별해야 합니다.

### 만료 경로를 확인하는 법

기본값(`entry-ttl` 10분, `admission-ttl` 2분)으로는 부하 한 회차 안에 만료가 나오지 않습니다. 값을 짧게 주고 따로 확인합니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local --booking.queue.admission-ttl=8s'
```

차례가 온 뒤 8초를 넘기면 조회와 예약이 모두 410으로 바뀝니다. Redis에서 직접 볼 수도 있습니다.

```bash
docker compose exec redis redis-cli TTL "all-my-trips:booking-queue:entry:<토큰>"
```

**READY가 된 순간의 TTL이 `admission-ttl`과 같아야 합니다.** 줄을 서지 않고 바로 입장한 경우에도 마찬가지입니다. (이전에는 이 경우에만 `entry-ttl`이 적용돼 자리를 5배 오래 잡고 있었습니다.)

---

## 5. 조절할 값

`application.properties`의 `booking.queue.*`입니다.

| 값 | 환경변수 | 기본 | 올리면 | 내리면 |
| --- | --- | --- | --- | --- |
| `capacity-per-second` | `BOOKING_QUEUE_CAPACITY_PER_SECOND` | 5 | 대기가 줄지만 DB 경쟁이 늘어남 | 대기가 길어짐 |
| `entry-ttl` | `BOOKING_QUEUE_ENTRY_TTL` | `10m` | 오래 기다려도 순번 유지 | 만료가 늘어남 |
| `admission-ttl` | `BOOKING_QUEUE_ADMISSION_TTL` | `2m` | 차례가 온 뒤 여유가 늘어남 | 자리를 오래 잡고 있지 않음 |

**파일을 고치지 않고 환경변수로 바꿀 수 있습니다.** 값을 바꿔가며 비교할 때 이 편이 안전합니다. 커밋에 실수로 섞이지 않습니다.

```bash
BOOKING_QUEUE_CAPACITY_PER_SECOND=10 ./gradlew bootRun --args='--spring.profiles.active=local'
```

부하 테스트의 목적은 **이 값을 근거를 갖고 정하는 것**입니다. 기본값은 실습 규모에서 대기 화면을 확인할 수 있는 선으로 잡은 것이고, 측정한 값이 아닙니다.

---

## 6. 2차 회차 결과 (2026-08-14)

대상 커밋 `415c71c`(#228 머지 후). 로컬 도커 환경, VU 30명, 재고 10개.
`capacity-per-second`만 바꿔 두 번 돌렸습니다.

| | A회차 | B회차 |
| --- | --- | --- |
| `capacity-per-second` | **5** (기본값) | **10** |
| 입장(READY) | 30 | 30 |
| 예약 성공 | 10 | 10 |
| 재고 소진(409) | 20 | 20 |
| 대기표 만료(410) | 0 | 0 |
| **줄 서서 기다린 시간 p95** | **15.06초** | **6.02초** |
| 〃 평균 | 7.55초 | 3.01초 |
| **개별 응답 시간 p95** | **255ms** | **895ms** |
| 요청 실패율 | 0.0% | 0.0% |
| 체크 통과율 | 100% | 100% |
| 서버 ERROR 로그 | 0건 | 0건 |

### 읽는 법

**초과 예약은 없습니다.** 두 회차 모두 `reserved_quantity=10`, 예약 10건, 서로 다른 사용자 10명입니다. 30명이 몰려도 재고 10개를 정확히 지켰습니다.

**`capacity-per-second`를 올리면 기다리는 시간은 줄지만 서버가 느려집니다.** 5 → 10으로 올리자 대기 시간 p95는 15초에서 6초로 **60% 줄었지만**, 개별 응답 시간 p95는 255ms에서 895ms로 **3.5배 늘었습니다.** 문서에 적어둔 트레이드오프가 숫자로 확인된 것입니다.

한 번에 들여보내는 사람이 많아지면 그만큼 같은 재고를 두고 DB에서 동시에 다투기 때문입니다. **줄이 짧아진 대신 창구가 느려집니다.**

**기본값 5를 유지합니다.** 실습 규모(동시 30명)에서 대기 15초는 대기 화면을 보여주기에 적당하고, 응답 255ms가 훨씬 안정적입니다. 동시 접속이 실제로 늘면 그때 다시 재고 올립니다.

### 이번 회차에서 고친 것

1차 회차의 "대기표 만료 0건"은 **만료가 없었던 게 아니라 셀 수 없었던 것**이었습니다. 만료는 HTTP 410으로 오는데 스크립트가 200만 보고 있었습니다. ([#228](https://github.com/heopath/TravelGuide-Project-Team1/pull/228))

같은 PR에서 서버 쪽 버그도 고쳤습니다. 줄을 서지 않고 바로 입장한 사람에게 `admission-ttl`이 아니라 `entry-ttl`이 걸려 자리를 5배 오래 붙잡고 있었습니다.

이번 회차의 만료 0건은 **정상입니다.** `admission-ttl`이 2분인데 차례가 오자마자 예약하므로 만료될 일이 없습니다. 만료 동작 자체는 위 "만료 경로를 확인하는 법"으로 따로 확인했습니다.

### 아직 못 본 것

- **VU 30명이 한계입니다.** 계정을 30개만 만들어 뒀습니다. 더 큰 규모는 `fixtures.sql`의 `generate_series` 범위를 늘려야 합니다
- **재고보다 사람이 훨씬 많은 경우**(재고 10 / 1000명)는 안 재봤습니다
- **여러 시간대에 동시에 몰리는 경우**는 안 재봤습니다. 지금 스크립트는 `SLOT_ID` 하나만 씁니다

---

## 7. 기록할 것

위키에 회차별로 남깁니다. 값만 적으면 나중에 왜 그렇게 정했는지 알 수 없습니다.

- 실행 일시, 대상 커밋
- VU 수, `capacity-per-second`, 시간대 재고
- 입장·성공·소진·만료 건수
- `queue_wait_to_ready_ms` p50 / p95
- 바꾼 설정과 그 이유

---

## 관련

- 대기열 구현: [PR #221](https://github.com/heopath/TravelGuide-Project-Team1/pull/221)
- 스크립트: `load-test/booking-queue.js`
- 동시성 테스트: `src/test/java/.../booking/service/BookingQueueConcurrencyTest.java`
