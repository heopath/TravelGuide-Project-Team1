# 인계 문서 — 대기열 부하 테스트

작성 2026-08-13 · 기준 커밋 `580a7dd` (`develop`)

이 문서만 읽고 이어서 작업할 수 있게 썼습니다. 앞 대화를 몰라도 됩니다.

---

## 1. 지금 어디까지 왔나

**v0.5.0을 배포했고, 그 뒤 `develop`에 4건이 더 쌓였습니다.**

| 커밋 | 내용 |
| --- | --- |
| `580a7dd` | AI 일정 장소 기준 주변 추천 정확도 개선 (#220) |
| `7d48f50` | **혼잡 시 티켓 예약 대기열 구현** (#221) ← 이번 작업의 대상 |
| `fda92e3` | 관리자 조작 이력 조회 화면 (#219) |
| `9070b3d` | 관리자 티켓 상품 등록·수정과 시간대 재고 조정 (#216) |

`main`은 v0.5.0(`b6c528c`)에 있고 아직 위 4건이 안 올라갔습니다.

### 열린 PR

**[PR #223](https://github.com/heopath/TravelGuide-Project-Team1/pull/223) — 이 작업의 준비물.** 아직 머지 전입니다.

브랜치 `chore/booking-queue-load-test`에 세 가지가 들어 있습니다.

- `load-test/booking-queue.js` — k6 스크립트
- `load-test/fixtures.sql` — 계정·여행·티켓 준비 SQL
- `src/test/java/.../booking/service/BookingQueueConcurrencyTest.java` — 재고 초과 방지 검증
- `docs/qa/booking-queue-load-test.md` — 도구 비교와 실행 절차

---

## 2. 다음에 할 일

**아직 부하를 한 번도 돌리지 않았습니다.** 준비물만 만든 상태입니다.

### 순서

```
① loadtest1 계정을 화면에서 회원가입
② load-test/fixtures.sql 실행 → slot_id, trip_id 확보
③ 동시성 테스트로 재고 초과가 없는지 확인     ← 여기서 깨지면 ④를 볼 이유가 없음
④ k6로 부하를 걸고 대기 시간 측정
⑤ capacity-per-second를 바꿔가며 비교
⑥ 결과를 위키에 회차별로 기록
```

### 왜 ③을 먼저 하나

**부하 도구는 "두 사람이 같은 자리를 받았는가"를 알려주지 않습니다.** 초당 몇 건을 처리했는지만 알려줍니다. 재고가 새는지는 코드 안에서 봐야 하고, 그게 깨진 상태라면 처리량을 재는 의미가 없습니다.

### 명령

```bash
# ③ 정확성 — 실제 DB·Redis 필요
BOOKING_QUEUE_CONCURRENCY_TEST=true ./gradlew test --tests "*BookingQueueConcurrencyTest*"

# ④ 부하
k6 run -e VUS=30 -e SLOT_ID=<slot_id> -e TRIP_ID=<trip_id> load-test/booking-queue.js
```

---

## 3. 반드시 지킬 것

### 운영에 쏘지 않습니다

**터널로 붙는 PostgreSQL·Redis가 운영 인스턴스입니다.** 부하로 만든 예약이 `reservations`에 그대로 쌓여

- 관리자 `운영 지표`의 `오늘 예약` 숫자가 오염됩니다
- `예약 모니터링` 목록에 가짜 예약이 섞입니다
- 티켓 재고가 실제로 깎여 다음 사람이 화면을 확인할 때 품절로 보입니다

실행 전에 접속한 DB를 확인하세요.

```sql
SELECT current_database(), inet_server_addr();
```

### 로컬 실행 시 프로필

`application.properties`의 기본 프로필이 `ui`이고, **`ui`로 띄우면 API가 아예 등록되지 않아 404가 납니다.** 반드시 `local`로 띄웁니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

`local` 프로필은 `spring.flyway.enabled=true`입니다. 운영 DB에 붙을 일이 있으면 반드시 끄세요.

---

## 4. 대기열 구조 (#221)

### API

| 메서드 | 주소 | |
| --- | --- | --- |
| `POST` | `/api/v1/booking-queue/entries` | 줄 서기. 본문은 `CreateTicketReservationRequest` |
| `GET` | `/api/v1/booking-queue/entries/{token}` | 순번 조회 |
| `POST` | `/api/v1/booking-queue/entries/{token}/reservation` | 차례가 오면 예약 완료 |
| `DELETE` | `/api/v1/booking-queue/entries/{token}` | 대기 취소 |

상태값: `WAITING` · `READY` · `PROCESSING` · `COMPLETED` · `EXPIRED`

### 설정 — 전부 환경변수로 덮어쓸 수 있습니다

| 프로퍼티 | 환경변수 | 기본 |
| --- | --- | --- |
| `booking.queue.capacity-per-second` | `BOOKING_QUEUE_CAPACITY_PER_SECOND` | `5` |
| `booking.queue.entry-ttl` | `BOOKING_QUEUE_ENTRY_TTL` | `10m` |
| `booking.queue.admission-ttl` | `BOOKING_QUEUE_ADMISSION_TTL` | `2m` |
| `booking.queue.processing-ttl` | `BOOKING_QUEUE_PROCESSING_TTL` | `30s` |
| `booking.queue.completed-ttl` | `BOOKING_QUEUE_COMPLETED_TTL` | `2m` |

파일을 안 고치고 값을 바꿀 수 있어 비교 실험에 편하고, 커밋에 실수로 섞이지 않습니다.

```bash
BOOKING_QUEUE_CAPACITY_PER_SECOND=10 ./gradlew bootRun --args='--spring.profiles.active=local'
```

**기본값 5는 측정한 값이 아닙니다.** 실습 규모에서 대기 화면을 확인할 수 있는 선으로 잡은 것이고, **이 값을 근거를 갖고 정하는 것이 부하 테스트의 목적입니다.**

### Redis 키

`all-my-trips:booking-queue:` 접두사를 씁니다. 회차 사이에 비우지 않으면 이전 줄이 이어집니다.

```bash
redis-cli --scan --pattern 'all-my-trips:booking-queue:*' | xargs -r redis-cli del
```

---

## 5. 알아둘 함정

이 프로젝트에서 실제로 걸렸던 것들입니다.

### 새 매퍼 XML은 테스트가 잡아주지 않습니다

서비스 테스트는 DAO를 mock으로 대체하고, 프런트 테스트는 `fetch`를 가짜로 바꿉니다. **저장소에 testcontainers도 H2도 없어 SQL이 실제로 도는지 확인된 적이 없습니다.** 새 질의를 쓰면 로컬 DB로 한 번 띄워 눌러봐야 합니다.

### `@Profile("!ui")`를 빠뜨리면 컨텍스트가 무너집니다

DAO·서비스·컨트롤러 모두 `@Profile("!ui")`입니다. 새로 만들 때 빠뜨리면 기본 프로필(`ui`)에서 빈이 로드되며 매퍼를 못 찾아 **모든 통합 테스트가 함께 실패합니다.**

### Jackson 2 / 3

Spring Boot 4는 Jackson 3이 기본입니다. `com.fasterxml.jackson.databind.ObjectMapper`를 **빈으로 주입받으면** 컨텍스트가 안 뜹니다. 이 저장소는 전부 `new ObjectMapper()`로 직접 만들어 씁니다.

### `Map.of`에 null을 넣으면 호출부에서 터집니다

값이 하나라도 `null`이면 즉시 `NullPointerException`이고, **그 예외는 호출한 쪽에서 나므로 서비스 내부 try/catch로 못 막습니다.** 감사 로그를 붙이다 실제로 관리 기능을 깨뜨릴 뻔했습니다. `AdminAuditService.payload(...)`가 그래서 있습니다.

### `String.valueOf(invocation.getArgument(n))`

Mockito 답변에서 이렇게 쓰면 자바가 **`String.valueOf(char[])` 오버로드를 골라** `ClassCastException`이 납니다. `invocation.getArgument(n, String.class)`로 타입을 명시하세요.

### `dataset`은 읽기 전용입니다

`Object.assign(el, { dataset: {...} })`는 `TypeError`입니다. 브라우저에서도 같습니다.

### `git diff A...B`(점 3개)로 병합 여부를 판단하지 마세요

갈라진 지점부터의 변경을 보여줄 뿐이라, **이미 대상 브랜치에 들어간 변경도 그대로 출력됩니다.** 병합 여부는 두 트리를 직접 비교(`git diff A B`)해야 합니다.

### 새 기능 시작 전에 `git fetch`부터

같은 대기열을 두 사람이 동시에 만든 적이 있습니다. 오전 상태만 보고 시작해서 생긴 일입니다. **작업 시작 전 최신 `develop`과 열린 PR을 반드시 확인하세요.**

### 화면 목록은 `app.js` 한 곳입니다

`ALL_MY_TRIPS_SCREENS` 배열이 `src/main/resources/static/js/app.js`에만 있습니다. 화면을 추가·삭제하면 여기와 [#191](https://github.com/heopath/TravelGuide-Project-Team1/issues/191)을 함께 고쳐야 합니다. 현재 **36장**(관리자 9장)입니다.

---

## 6. 검증 명령

```bash
./gradlew clean build                    # 전체
cd src/test/js && npm test               # 프런트 수용 테스트 (현재 361건)
node --check <파일>                       # JS 문법
```

테스트 실패 원인은 요약만 보지 말고 `build/test-results/test/TEST-*.xml`의 assertion 메시지까지 확인하세요.

**실패가 내 변경 때문인지 먼저 확인합니다.** `develop`으로 체크아웃해 같은 테스트를 돌리면 기존 문제인지 바로 갈립니다.

### 커밋 전 줄바꿈 확인

외부 도구가 파일을 건드리면 작업트리 전체가 CRLF로 바뀌어 수백 개 파일이 변경으로 잡힙니다.

```bash
git status --short | wc -l           # 수정한 파일 수와 맞는지
git diff --ignore-cr-at-eol --stat   # 실제 내용 변경만
```

---

## 7. 이 작업 밖의 열린 일

| 이슈 | 내용 | 담당 |
| --- | --- | --- |
| [#218](https://github.com/heopath/TravelGuide-Project-Team1/issues/218) | 감사 로그 IP가 전부 `::1` — 프록시가 `X-Forwarded-For` 미전달 | 없음 |
| [#212](https://github.com/heopath/TravelGuide-Project-Team1/issues/212) | 관리자 회원 관리 화면 | 없음 |
| [#124](https://github.com/heopath/TravelGuide-Project-Team1/issues/124) | 여행 기간 변경 충돌 화면 경고 | jeomseon0516 |
| [#197](https://github.com/heopath/TravelGuide-Project-Team1/issues/197) · [#199](https://github.com/heopath/TravelGuide-Project-Team1/issues/199) · [#211](https://github.com/heopath/TravelGuide-Project-Team1/issues/211) | 경로·AI 일정 후속 | 없음 |
| [#193](https://github.com/heopath/TravelGuide-Project-Team1/issues/193) | 8/14 12:00 산출물 제출 — 유스케이스만 남음 | 홍유원 |

### v0.5.0에 남긴 제한사항 중 아직 안 고친 것

- **만료된 예약을 자동 정리하지 않습니다.** `expires_at`이 지나도 `EXPIRED`로 바꾸는 처리가 없어 `PENDING`으로 남아 재고를 잡습니다. 예약 모니터링에서 `만료 방치`로 **보이게만** 해뒀습니다
- **AI 장소 매칭에 최소 점수 기준이 없습니다.** #220이 반경으로 상당 부분 줄였지만 임계값 자체는 없습니다
- **기존 `place_id`가 빈 AI 일정은 복구되지 않습니다.** 신규 일정부터 적용됩니다
- **상담 채팅**만 관리자 화면 중 연동 전입니다

---

## 8. 작업 규칙

- 기본 브랜치 `develop`, 배포는 `main`, PR은 `develop` 대상 **squash merge**
- 작업 브랜치 `feature/*`, `fix/*`, `chore/*`
- 주석과 커밋 메시지는 **한국어**, 접두사 `feat:` `fix:` `chore:` `docs:`
- 주석은 "무엇을"이 아니라 **"왜"** 를 적습니다
- **머지는 작성자가 직접 합니다**
- `develop`은 기본 브랜치가 아니라 PR 본문의 `Closes #N`으로 이슈가 자동으로 닫히지 않습니다. 직접 닫으세요

---

## 9. 참고 문서

| 문서 | 내용 |
| --- | --- |
| `CLAUDE.md` | 프로젝트 전반 규칙과 함정 |
| `docs/qa/booking-queue-load-test.md` | 도구 비교, 실행 절차, 결과 읽는 법 |
| `load-test/fixtures.sql` | 데이터 준비·초기화·정리 SQL |
| [PR #221](https://github.com/heopath/TravelGuide-Project-Team1/pull/221) | 대기열 구현 배경과 판단 |
| [v0.5.0 릴리스](https://github.com/heopath/TravelGuide-Project-Team1/releases/tag/v0.5.0) | 현재 배포된 범위와 알려진 제한사항 |
