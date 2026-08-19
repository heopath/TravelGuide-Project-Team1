# 상담 채팅 — AI 봇 응대와 WebSocket 전환 (초안)

> 이 문서는 아직 팀 확정 전 초안입니다. "정할 것" 항목은 논의 후 이 문서에 반영합니다.

## 배경

관리자 상담 채팅([이슈 #247](https://github.com/heopath/TravelGuide-Project-Team1/issues/247) / [PR #248](https://github.com/heopath/TravelGuide-Project-Team1/pull/248))이 v0.7.0에 이미 배포돼 있습니다. 손님 한 명당 방 하나, 방 상태(`BOT`/`WAITING`/`ASSIGNED`/`CLOSED`), 관리자 "내가 응대하기"까지 전부 동작합니다.

다만 이슈 #247 본문이 처음부터 이렇게 선을 그어뒀습니다.

> **범위 밖** — 챗봇 본체는 다른 팀원 담당

그래서 지금은 방을 열면 DB 기본값(`WAITING`)으로 곧장 "관리자 대기" 상태가 되고, `BOT` 상태의 방은 실제로 생기지 않습니다. 봇이 쓸 자리(상태값 `BOT`, 발신자 타입 `BOT`)만 스키마에 미리 마련돼 있을 뿐입니다. 이 "챗봇 본체" 작업은 아직 별도 이슈로 만들어지지 않았고, 이 문서가 그 작업을 계획합니다.

## 구분해야 할 것 — AI 여행 가이드와는 다른 기능

이 저장소에는 이미 AI를 부르는 화면이 있어 헷갈리기 쉽습니다. 서로 다른 도메인 패키지, 다른 테이블, 다른 성격입니다.

| | AI 여행 가이드 (기존) | 상담 채팅 (이 문서) |
| --- | --- | --- |
| 위치 | `domain.ai`, `/ai-guide` | `domain.support`, 마이페이지 고객센터 탭 / 관리자 화면 |
| 테이블 | `ai_chat_sessions`, `ai_chat_messages` | `support_chat_rooms`, `support_chat_messages` |
| 통신 방식 | 단방향 — `POST /api/v1/ai-guides/generate` 요청 1번에 응답 1번 | 왕복 — 방 하나를 열어두고 여러 번 주고받음 |
| 관리자 개입 | 없음 | 있음 — 관리자가 방을 넘겨받아 봇을 대신함 |
| 호출 위치 | 여행 일정 짜는 화면 안 | 사이트 어디서든 열 수 있는 고객 상담 |

**결론: 이 문서는 AI 여행 가이드를 건드리지 않습니다.** 상담 채팅에 새 AI 연동을 추가하고, 그 갱신 방식을 WebSocket으로 바꾸는 작업만 다룹니다.

## 왜 WebSocket인가 (확정된 판단)

AI 여행 가이드는 "질문 1번 → 답변 1번"으로 끝나는 구조라 폴링이든 동기 호출이든 상관이 없습니다. **상담 채팅은 다릅니다.** 손님·봇·관리자 세 참여자가 같은 방 하나에 실시간으로 얽히고, 그중에서도 **관리자가 대화 도중에 끼어드는(takeover) 구조**라는 점이 핵심입니다.

- 손님은 봇과 실시간으로 주고받는 중입니다 — "글 남기고 기다리는" 1:1 문의가 아니라 지금 붙어서 대화하는 채팅입니다.
- 관리자가 `takeover()`를 누르는 순간, 손님 쪽 화면은 "봇이 아니라 사람이 응대 중"이라는 상태 전환을 **지연 없이** 알아야 합니다. 폴링 주기(현재 3초)만큼 손님은 자신이 사람과 이야기하는지 봇과 이야기하는지 모르는 구간이 생깁니다.
- 관리자가 넘겨받은 뒤에도 손님이 타이핑하는 메시지를 관리자가 실시간으로 받아야 응대가 자연스럽습니다. 대화가 길어지거나 여러 방이 동시에 열릴수록 폴링의 지연·낭비(빈 응답 반복 조회)가 누적됩니다.
- 현재 폴링 구현 자체도 실시간 응대에 맞지 않는 특성이 있습니다(코드로 확인, 위 "갱신 방식" 참고). 매 3초마다 전체 메시지를 다시 받는 방식이라 대화가 길어질수록 낭비가 커지고, 오류가 나면 재시도 없이 그냥 멈춰서 손님·관리자가 알아채지 못하는 사이 갱신이 끊길 수 있습니다. WebSocket 전환 시 이 두 문제(전체 재조회, 무재시도 중단)도 함께 없앨 수 있습니다.

이슈 #247의 "폴링으로 충분하다"는 결정은 **그 시점(챗봇 없는 관리자 화면, 관리자 몇 명만 여는 자리)** 에서는 합리적이었지만, 봇이 실시간으로 응답하고 관리자가 그 대화 중간에 끼어드는 지금 구조에는 더 이상 맞지 않습니다. 그래서 **이번 작업은 WebSocket 전환을 전제로 진행**하며, 아래 "정할 것"은 "WebSocket을 쓸지 말지"가 아니라 **무엇을 WebSocket으로 옮기고 무엇을 REST로 남길지**에 대한 프로토콜 세부사항만 다룹니다.

## 현재 구현 상태 (v0.7.0 기준)

### API

| 기능 | Method | URL | 사용자 |
| --- | --- | --- | --- |
| 상담 시작/이어가기 | POST | `/api/v1/support/chat` | 손님 |
| 폴링 조회 | GET | `/api/v1/support/chat` | 손님 |
| 메시지 전송 | POST | `/api/v1/support/chat/messages` | 손님 |
| 방 목록 | GET | `/api/v1/admin/support-chats` | 관리자 |
| 방 상세 | GET | `/api/v1/admin/support-chats/{roomId}` | 관리자 |
| 내가 응대하기 | POST | `/api/v1/admin/support-chats/{roomId}/takeover` | 관리자 |
| 메시지 전송 | POST | `/api/v1/admin/support-chats/{roomId}/messages` | 관리자 |
| 상담 종료 | POST | `/api/v1/admin/support-chats/{roomId}/close` | 관리자 |

`SupportChatService` 한 곳이 손님 쪽과 관리자 쪽을 모두 처리합니다(`src/main/java/.../domain/support/service/SupportChatService.java`).

### 갱신 방식

`admin-chat.js`, `mypage-support-chat.js` 둘 다 **3초 폴링**입니다. 이슈 #247 본문에 이미 "정할 것"으로 결정돼 있었습니다.

> 갱신 방식 — 폴링으로 둡니다. WebSocket은 서버 구성이 늘고, 대기열에서 폴링 주기를 재본 경험이 있어 감이 있습니다

구체적인 동작(코드로 확인):

```js
timer = window.setInterval(async function () {
  if (panel.hidden || !opened) return stopPolling();
  render(await request("/api/v1/support/chat"));
}, POLL_INTERVAL_MS); // 3000
```

- **매번 전체를 다시 받습니다.** 증분(새 메시지만) 요청이 아니라, 매 3초마다 방 정보 + 메시지 최대 200개(`SupportChatViewResponse`)를 통째로 다시 내려받아 화면을 다시 그립니다. 대화가 길어질수록 매 tick의 응답 크기가 커집니다.
- **패널을 닫으면 멈춥니다.** `panel.hidden`을 매 tick 체크해 `clearInterval` — 백그라운드에서 계속 도는 구조는 아닙니다.
- **오류가 나면 재시도 없이 그냥 멈춥니다.** `catch (error) { stopPolling(); }` — 일시적 네트워크 오류든 뭐든 한 번 실패하면 폴링 자체가 끊기고, 사용자가 다시 열어야 재개됩니다.

`build.gradle`에도 WebSocket 관련 의존성은 아직 없습니다. **이 결정은 이번 작업으로 뒤집힙니다** — 이유는 아래 "왜 WebSocket인가" 참고.

### DB — 이미 봇을 염두에 두고 설계됨

`database/migration/V19__support_chat.sql`이 지금 하려는 작업을 미리 상정하고 만들어졌습니다. **새 테이블이나 로그용 컬럼을 추가할 필요가 없습니다.**

```sql
support_chat_rooms.status       -- BOT · WAITING · ASSIGNED · CLOSED
support_chat_messages.sender_type -- USER · BOT · ADMIN (BOT 메시지는 sender_user_id가 NULL)
```

`support_chat_messages`는 append-only라 대화 기록이 곧 로그입니다. 봇이 쓴 메시지도 `sender_type = 'BOT'`으로 같은 테이블에 쌓이므로, "채팅 내역을 로그로 저장"이라는 요구사항은 스키마상 이미 충족돼 있습니다.

## 이번 작업 목표

1. 방을 열면 `BOT` 상태로 시작하고, AI가 첫 응답부터 자동으로 작성한다.
2. 관리자가 "내가 응대하기"(`takeover`)를 누르면 봇이 멈추고, 이후 메시지는 관리자만 쓴다.
3. 갱신 방식을 폴링에서 WebSocket으로 바꿔 실시간으로 주고받는다 — 관리자가 대화 도중 끼어드는 구조이므로 폴링 지연은 허용하지 않는다(근거는 "왜 WebSocket인가" 참고).
4. 대화 기록은 기존 `support_chat_messages`에 그대로 쌓는다(신규 스키마 불필요, 위 항목 참고).

## 서버 구성 — 별도 서버가 필요한가

**결론: 별도 서버는 필요 없습니다. 지금과 같은 Spring Boot 프로세스 안에서 처리합니다.**

### 근거

1. **배포 토폴로지가 이미 단일 인스턴스입니다.** `.github/workflows/deploy.yml`이 `AWS_EC2_INSTANCE_ID` 하나에 SSM으로 배포하고, 그 인스턴스의 systemd 서비스(`all-my-trips`) 하나가 `app.jar`를 직접 구동합니다. 로드밸런서나 오토스케일링 그룹, 다중 인스턴스 배포가 지금은 없습니다. "여러 인스턴스에 걸친 WebSocket 세션을 어떻게 공유할까"라는, 별도 서버·메시지 브로커를 필요로 하게 만드는 전형적인 이유 자체가 지금 인프라에는 해당하지 않습니다.
2. **`spring-boot-starter-websocket`은 내장 Tomcat 위에서 그대로 동작합니다.** WebSocket 핸드셰이크(HTTP Upgrade)를 처리하는 서블릿 핸들러일 뿐이라 별도 프로세스나 포트가 필요 없습니다. REST API와 같은 포트(8080), 같은 JVM, 같은 `app.jar`에서 함께 뜹니다 — 배포 파이프라인도 손댈 필요가 없습니다.
3. **연결 규모가 작습니다.** 이슈 #247이 이미 "관리자 몇 명만 여는 자리"라고 못박았고, 손님 쪽 동시 접속도 예약 대기열(#221, 3차 부하 테스트까지 거친 대량 트래픽 대상)과는 성격이 다릅니다. WebSocket 연결이 스레드를 오래 점유하는 특성이 있긴 하지만, 이 규모에서 메인 서버와 자원 경쟁을 걱정할 단계는 아닙니다.

### 그래도 손봐야 하는 것 — "서버 분리"는 아니지만 인프라 설정은 별도로 필요

1. **nginx 리버스 프록시 설정.** EC2 앞단에 nginx가 있다는 것은 이슈 #250에서 이미 확인된 사실입니다(*"앱은 이미 맞고 nginx만 남아 있었는데..."*, 감사 로그 IP 이슈 #218 관련 코멘트). nginx는 기본적으로 HTTP/1.0으로 업스트림에 프록시하기 때문에, `Upgrade: websocket` / `Connection: Upgrade` 핸드셰이크 헤더를 그대로 전달하려면 아래 설정을 명시적으로 추가해야 합니다.

   ```nginx
   proxy_http_version 1.1;
   proxy_set_header Upgrade $http_upgrade;
   proxy_set_header Connection "upgrade";
   ```

   **이 설정이 빠지면 WebSocket 핸드셰이크가 로컬에서는 되고 운영에서는 실패하는(502/400) 상황이 됩니다.** 이 저장소에는 nginx 설정 파일이 없어(위키에서 관리) 직접 확인하지 못했으므로, 배포 전에 위키/EC2에서 실제 설정을 확인해야 합니다.
2. **다중 인스턴스로 바뀌면 이 결론을 다시 봐야 합니다.** 지금은 없지만, 나중에 트래픽이 늘어 로드밸런서 + 다중 인스턴스 구조로 바뀌면 그때는 (a) 같은 클라이언트를 같은 인스턴스로 고정하는 스티키 세션, 또는 (b) 인스턴스 간 메시지를 전달할 브로커(예: Redis Pub/Sub — 이미 예약 대기열(`RedisBookingQueueStore`)에서 Redis를 쓰고 있어 재사용 후보)가 필요해집니다. **지금 시점에 미리 만들면 오버엔지니어링이라 범위 밖으로 둡니다.**

**브로커(확정, heopath 리뷰)**: 단일 인스턴스인 지금은 Spring이 기본 제공하는 **내장(in-memory) 심플 브로커**로 시작합니다. RabbitMQ 같은 외부 STOMP 브로커는 다중 인스턴스로 바뀔 때(위 2번) 다시 검토합니다.

## 설계

### 1. 봇 연동 지점

**확정(heopath 리뷰, [PR #263](https://github.com/heopath/TravelGuide-Project-Team1/pull/263)): Gemini를 사용하되, 기존 AI 여행 가이드 프롬프트는 재사용하지 않습니다.** `domain.ai`의 `AiModelClient`/`GeminiAiModelClient`는 여행 일정 프롬프트 전용으로 짜여 있어 그대로 쓰기 어렵고, 같은 `ChatModel` 빈 설정은 공유하되 **상담 전용 `SupportChatBotClient`와 별도 시스템 프롬프트**로 분리합니다(`domain.support` 아래).

흐름 제안:

```text
SupportChatService.openMyRoom()
  → 방을 BOT 상태로 생성
  → SupportChatBotClient 호출 → 첫 인사 메시지를 sender_type=BOT으로 저장

SupportChatService.sendAsUser()
  → 방 상태가 BOT이면: 사용자 메시지 저장 → 봇 응답 생성 → sender_type=BOT으로 저장
  → 방 상태가 WAITING/ASSIGNED면: 지금처럼 사용자 메시지만 저장 (관리자가 볼 때까지 대기)
```

관리자가 `takeover()`를 부르면 상태가 `ASSIGNED`로 바뀝니다. "방 상태가 `BOT`일 때만 봇이 응답한다"는 조건 하나로 자연스럽게 봇이 멈춥니다 — `SupportChatService` 코드 주석에 이미 이렇게 쓰여 있습니다.

> 봇이 붙으면 이 화면은 고칠 것이 없다.

즉 관리자 쪽 코드는 그대로 두고, `SupportChatService`의 손님 쪽 메서드 몇 개만 손대면 됩니다. **다만 위 흐름은 단순화된 그림이고, 실제로는 아래 "Gemini 응답과 관리자 takeover 경쟁 조건"의 재확인 절차가 반드시 필요합니다.**

**봇 → `WAITING` 전환 기준(heopath 리뷰로 확정)**: 아래 네 가지 중 하나면 봇이 멈추고 사람 대기로 넘어갑니다.

- 손님이 명시적으로 상담원 연결을 요청
- Gemini 호출 실패 또는 시간 초과
- 상담 정책 범위 밖의 질문
- 같은 문제가 반복돼 해결되지 않음

### 2. WebSocket 설계

- `build.gradle`에 `spring-boot-starter-websocket` 추가 필요.
- STOMP over SockJS 제안 — 예: 핸드셰이크 `/ws/support-chat`, 구독 `/topic/support-chat/rooms/{roomId}`, 발행 `/app/support-chat/{roomId}/send`. **구체적인 경로 이름은 팀 논의 후 확정합니다.**
- **인증(확정)**: 별도 JWT를 추가하지 않고 기존 `JSESSIONID` 세션 인증을 WebSocket 핸드셰이크에서 그대로 재사용합니다. STOMP `CONNECT` 프레임에는 기존 CSRF 토큰을 포함하고, 동일 출처(same-origin) 요청만 허용합니다.
- **발신 경로(확정)**: 1차 구현은 기존 REST POST(`open`/`messages`/`takeover`/`close`)를 그대로 유지합니다. **새 메시지·방 상태 변경 "수신"만 WebSocket으로 전환**합니다. 기존 인증·CSRF·검증·공통 오류 응답을 그대로 활용할 수 있어 구현 범위와 회귀 위험이 작습니다. 발신도 WebSocket으로 옮기는 건 추후 필요해지면 검토합니다.
- **구독 권한 검사(필수, 구현 시 반드시 반영)**: `/topic/support-chat/rooms/{roomId}` 구독을 아무나 허용하면 안 됩니다. 일반 사용자는 자신의 방만, 관리자는 `ROLE_ADMIN`만 구독할 수 있어야 하고, **이 판단은 클라이언트가 보내는 `roomId`/사용자 ID/`senderType`을 신뢰하지 않고 서버가 세션에서 얻은 `Principal`로만 해야 합니다.**

### 3. 프론트-백엔드 데이터 형식 (제안)

**새 DTO를 따로 만들지 않고, 기존 REST 응답에 이미 쓰는 `SupportChatMessageDTO`/`SupportChatRoomDTO`를 그대로 재사용합니다.** 필드명이 이미 있으니 프론트가 REST 응답을 파싱하던 코드와 WebSocket 수신 코드가 같은 모양의 JSON을 다루게 됩니다 — 스키마를 두 벌 유지할 이유가 없습니다.

**클라이언트 → 서버 (발행, SEND)**

`/app/support-chat/{roomId}/send` — 지금의 `SupportChatMessageRequest`와 동일한 바디입니다.

```json
{ "content": "환불 문의드립니다" }
```

**서버 → 클라이언트 (구독, SUBSCRIBE)**

`/topic/support-chat/rooms/{roomId}` — 한 방에 "새 메시지"와 "방 상태 변경(예: 관리자가 넘겨받음)" 두 종류의 이벤트가 있으므로, `type`으로 구분하는 얇은 envelope 하나를 씌웁니다.

```json
// 새 메시지 도착 (SupportChatMessageDTO 그대로)
{
  "type": "MESSAGE",
  "message": {
    "supportChatMessageId": 123,
    "supportChatRoomId": 45,
    "senderType": "BOT",
    "senderUserId": null,
    "senderNickname": null,
    "content": "무엇을 도와드릴까요?",
    "createdAt": "2026-08-18T10:00:00+09:00"
  }
}
```

```json
// 방 상태 변경 (SupportChatRoomDTO 그대로) — 예: 관리자가 "내가 응대하기"를 누른 순간
{
  "type": "ROOM_STATUS",
  "room": {
    "supportChatRoomId": 45,
    "status": "ASSIGNED",
    "assignedAdminId": 12,
    "assignedAdminNickname": "허민재",
    "lastMessageAt": "2026-08-18T10:00:12+09:00"
  }
}
```

`/topic/support-chat/admin/rooms` — 관리자 대기열 전용 토픽입니다. 관리자 화면은 **열어 둔 방의 토픽만 구독**하므로 그것만으로는 새 상담이 들어오거나 다른 방이 `WAITING`으로 넘어간 것을 알 수 없고, 폴링을 걷어낸 뒤에는 목록이 새로고침 전까지 멈춰 있게 됩니다. 목록에 영향을 주는 변화(방 생성, 상태 전환, 새 메시지)는 방 토픽과 함께 이 토픽에도 같은 envelope로 내보내고, 관리자 화면은 이 이벤트를 받으면 기존 `GET /api/v1/admin/support-chats` 목록을 다시 읽습니다. `ROLE_ADMIN`만 구독할 수 있습니다.

`/user/queue/support-chat/errors` — 아래 "오류 전달"의 2번 계층을 받는 자리입니다. **구독 인가 검사가 방 토픽만 다루면 이 큐 구독까지 "roomId가 없다"는 이유로 거부되어, 서버가 오류를 보내도 받을 곳이 없습니다.** 목적지별로 판단을 나눠, 이 큐는 로그인한 본인 세션이면 허용합니다(스프링이 사용자별로 목적지를 갈라 주므로 남의 큐를 받아 갈 수 없습니다).

`status`/`senderType`은 REST와 동일한 값 집합(`BOT`/`WAITING`/`ASSIGNED`/`CLOSED`, `USER`/`BOT`/`ADMIN`)을 그대로 씁니다. 서버 구현은 `SimpMessagingTemplate.convertAndSend()`로 기존 DTO 인스턴스를 그대로 넘기면 되므로, 직렬화 로직을 새로 짤 필요가 없습니다.

**오류 전달(확정, heopath 리뷰)** — 오류를 세 층으로 나눕니다.

1. **REST 오류**: 기존 `ErrorResponse(success/code/message/data/errors)`를 그대로 유지합니다. WebSocket 도입과 무관합니다.
2. **WebSocket의 복구 가능한 오류**(권한 없는 방 구독 시도, 메시지 검증 실패 등): 사용자 전용 큐 `/user/queue/support-chat/errors`로 아래 형식을 보냅니다.

   ```json
   { "type": "VALIDATION_ERROR", "code": "INVALID_SUPPORT_CHAT_REQUEST", "message": "메시지는 2000자 이하여야 합니다.", "retryable": false }
   ```

3. **연결·프로토콜 자체 오류**(핸드셰이크 실패 등): STOMP `ERROR` 프레임을 그대로 사용합니다.

### 4. 보안 — 신뢰 경계

WebSocket 메시지는 REST 요청과 달리 클라이언트가 페이로드에 `roomId`, `senderType`, 심지어 다른 사용자의 ID까지 마음대로 채워 보낼 수 있습니다. **서버는 이 값을 절대 그대로 믿지 않습니다.**

- 구독(`/topic/support-chat/rooms/{roomId}`)과 발행 양쪽 모두, 누구인지는 STOMP 세션에 연결된 `Principal`(로그인 세션)에서만 가져옵니다.
- 일반 사용자는 `SupportChatDAO.findOpenRoomByUser()`로 확인되는 **자기 방만** 구독할 수 있습니다.
- 관리자는 `ROLE_ADMIN`이어야 방 목록에 있는 어떤 방이든 구독할 수 있습니다.
- 관리자 대기열 토픽(`/topic/support-chat/admin/rooms`)도 `ROLE_ADMIN`만 구독할 수 있습니다. 손님이 구독하면 다른 손님의 상담 흐름이 보입니다.
- 본인 오류 큐(`/user/queue/support-chat/errors`)는 로그인 여부만 확인합니다 — 목적지를 세션별로 갈라 주므로 남의 큐를 지정할 방법이 없습니다.
- 클라이언트가 보낸 `senderType: "ADMIN"` 같은 값은 절대 그대로 저장하지 않고, 서버가 `Principal`의 역할을 보고 직접 결정합니다.

### 5. Gemini 응답과 관리자 takeover 경쟁 조건

봇 응답은 비동기이므로, **손님 메시지 저장과 봇 응답 저장 사이에 관리자가 `takeover()`를 부를 수 있습니다.** 그대로 두면 관리자가 이미 응대를 시작한 방에 봇 답변이 뒤늦게 끼어드는 사고가 납니다.

- `Gemini` 호출은 **DB 트랜잭션 안에서 기다리지 않습니다** — 외부 API 호출을 트랜잭션으로 묶으면 커넥션을 오래 붙잡습니다.
- 봇 응답을 저장하기 **직전에 방 상태를 다시 조회**해, 여전히 `BOT`일 때만 메시지를 저장합니다. 그 사이 `ASSIGNED`로 바뀌었다면 봇 응답은 버립니다(저장하지 않음).

```text
1. 손님 메시지 저장 (status=BOT 확인)
2. Gemini 호출 (트랜잭션 밖, 비동기)
3. 응답 도착 → 방 상태 재조회
4. 여전히 BOT이면 → 저장 + WebSocket 브로드캐스트
   ASSIGNED로 바뀌었으면 → 버림 (관리자가 이미 응대 중)
```

### 6. 재연결 동기화

WebSocket 연결이 끊겼다가 재연결되면 그 사이의 이벤트(메시지, 상태 변경)를 놓칠 수 있습니다. 재연결 직후 기존 REST(`GET /api/v1/support/chat` 또는 `GET /api/v1/admin/support-chats/{roomId}`)로 방 전체를 한 번 동기화해 화면을 최신 상태로 맞춥니다. 이 흐름은 "완료 기준"에 포함합니다.

**순서는 "구독 먼저, 동기화 나중"입니다.** REST로 먼저 맞추고 그다음에 구독하면, REST 응답이 만들어진 시점과 SUBSCRIBE가 등록되는 시점 사이에 저장된 이벤트를 어느 쪽으로도 받지 못합니다. 하필 그 이벤트가 봇 답변이면 손님 화면은 "답변을 준비하고 있습니다..."에서 입력창이 잠긴 채 멈춥니다. 그래서 구독을 먼저 걸어 둡니다.

**구독 순서만으로는 부족합니다(heopath 2차 리뷰).** 이벤트가 REST 조회를 여러 번 겹쳐 트리거할 수 있고, 네트워크 지연 때문에 응답이 요청 순서대로 온다는 보장이 없습니다. 느리게 온 옛 조회 응답이 빠르게 온 새 조회 응답을 뒤늦게 덮어쓰면, 화면은 다시 낡은 상태로 되돌아가고(예: 방금 반영된 봇 답변이 사라짐) 이후 이벤트가 없으면 그 상태로 멈춥니다. 그래서 조회를 부를 때마다 요청 세대 번호를 하나씩 늘리고, 응답이 도착했을 때 그 번호가 더 이상 최신이 아니면(그 사이 더 최신 조회가 시작됐다는 뜻) 반영하지 않고 버립니다 — 마지막에 *시작된* 조회가 결국 이깁니다.

### 6-1. WebSocket 연결 실패 시 REST 폴백 폴링(heopath 2차 리뷰)

`/webjars/sockjs-client`·`/webjars/stomp-websocket` 스크립트를 못 불러오거나, 핸드셰이크·운영 nginx의 WebSocket Upgrade 설정이 실패해 연결이 계속 안 되면, 재연결 시도만 반복할 뿐 REST로 대신 갱신하는 경로가 없었습니다. 새 방은 `BOT` 상태로 시작하고 첫 인사·후속 답변은 비동기로 저장되므로, 연결이 안 되는 손님 화면은 봇 답변을 받을 방법이 아예 없어져 입력창이 잠긴 채 멈추고, 관리자 화면은 새 상담이 대기열에 뜨지 않습니다.

**연결돼 있지 않은 동안에만** 일정 주기(5초)로 REST를 대신 부릅니다 — 손님 화면은 열어 둔 방을, 관리자 화면은 상담 목록과 열어 둔 방을 함께 다시 읽습니다. 매 틱마다 연결 상태를 다시 확인하므로 연결되면 스스로 멈춥니다. WebSocket이 정상인 동안에는 이 폴링이 전혀 개입하지 않습니다.

### 7. 봇 응답 대기 중 UX

**확정(heopath 리뷰, [PR #263](https://github.com/heopath/TravelGuide-Project-Team1/pull/263)):** 1차 구현은 별도 서버 이벤트 없이 프론트 로컬 상태만으로 처리합니다.

- 손님 메시지가 REST 전송에 성공하면 즉시 "답변을 준비하고 있습니다..." 표시를 띄웁니다.
- `BOT` 메시지를 WebSocket으로 수신하면 대기 표시를 제거합니다.
- 방 상태가 `WAITING`으로 바뀌거나 오류가 발생하면 대기 표시를 상담원 대기 안내로 전환합니다.
- 봇 응답을 기다리는 동안에는 손님의 추가 전송을 막아 응답 순서가 뒤섞이지 않게 합니다.
- **1차 범위 밖**: 별도 `BOT_TYPING` WebSocket 이벤트나 DB 저장은 하지 않습니다. 대기 상태는 프론트가 로컬로만 관리합니다.
- 다만 **로컬 플래그를 들고 다니지는 않습니다.** 대기 여부는 화면을 그릴 때마다 서버가 준 방 상태에서 다시 판단합니다 — 방이 `BOT`이고 마지막 메시지가 손님(또는 아직 메시지가 없음)이면 대기입니다. 플래그를 들고 다니면 질문을 보낸 뒤 새로고침하거나 다른 탭에서 열었을 때 입력창이 열려 있어, 같은 방에 봇 호출이 겹치고 답변이 중복되거나 순서가 뒤바뀝니다.
- 서버도 같은 것을 한 겹 더 막습니다. 한 방에 봇 응답 생성이 이미 돌고 있으면 새 요청은 겹쳐 부르지 않고 "끝나고 한 번 더" 표시만 남기며, 이어지는 실행이 그 사이 쌓인 메시지까지 포함한 대화 내역을 다시 읽습니다(단일 인스턴스 전제 — 내장 심플 브로커와 같은 전제입니다).

### 8. 상태 전이

```text
방 생성 → BOT (봇이 자동 응답)
            │
            ├─ 관리자가 "내가 응대하기" ──▶ ASSIGNED (사람이 응대)
            │
            └─ 봇 → WAITING 전환 기준(위 "1. 봇 연동 지점" 참고: 상담원 요청 /
               Gemini 실패·시간 초과 / 정책 범위 밖 / 반복 미해결) ──▶ WAITING (사람 대기)

ASSIGNED / WAITING → 관리자가 "상담 종료" ──▶ CLOSED
```

## 로그·기록

- **이미 해결됨** — `support_chat_messages`가 append-only 로그이고, 봇 메시지도 같은 테이블에 `sender_type='BOT'`으로 쌓입니다. 새 테이블·컬럼이 필요 없습니다.
- `takeover()`(응대 이관)를 `admin_audit_logs`에 남길지는 별도 논의 대상입니다 — 현재 `SupportChatService.takeover()`는 감사 로그를 남기지 않습니다.

## 정할 것

**heopath 리뷰([PR #263](https://github.com/heopath/TravelGuide-Project-Team1/pull/263))로 6개 항목 전부 확정됐습니다.**

| # | 항목 | 상태 |
| --- | --- | --- |
| 1 | 봇 프롬프트/모델 | ✅ 확정 — "설계 &gt; 1. 봇 연동 지점" |
| 2 | 봇이 답을 못 찾을 때 | ✅ 확정 — "설계 &gt; 1. 봇 연동 지점" |
| 3 | WebSocket 인증 방식 | ✅ 확정 — "설계 &gt; 2. WebSocket 설계" |
| 4 | 발신 경로 | ✅ 확정 — "설계 &gt; 2. WebSocket 설계" |
| 5 | 봇 응답 대기 중 UX | ✅ 확정 — "설계 &gt; 7. 봇 응답 대기 중 UX" |
| 6 | 오류 페이로드 형식 | ✅ 확정 — "설계 &gt; 3. 프론트-백엔드 데이터 형식" |

(WebSocket 채택 여부 자체는 "왜 WebSocket인가"에서 이미 확정됐으므로 여기서 다시 논의하지 않습니다.)

## 범위 밖

- 파일·이미지 전송 (이슈 #247에서 이미 범위 밖으로 명시, 이번에도 유지)
- 상담 만족도 평가
- AI 여행 가이드(`domain.ai`)와의 통합·공용 UI

## 완료 기준

- [ ] 방 생성 시 `BOT` 상태로 시작하고 첫 메시지가 자동으로 달린다
- [ ] 관리자가 응대를 넘겨받으면(`ASSIGNED`) 봇 응답이 멈춘다
- [ ] Gemini 응답 저장 직전 방 상태를 재확인해, `takeover` 이후 봇 응답이 뒤늦게 저장되지 않는다(경쟁 조건 검증)
- [ ] `/topic/support-chat/rooms/{roomId}` 구독이 서버의 `Principal` 기준으로만 허용된다(자기 방 아닌 손님, `ROLE_ADMIN` 아닌 사용자의 구독·발행 거부 검증)
- [ ] `/topic/support-chat/admin/rooms`는 `ROLE_ADMIN`만, `/user/queue/support-chat/errors`는 로그인한 본인만 구독할 수 있다
- [ ] 관리자 대기열이 새로고침 없이 갱신된다(새 상담 도착, 다른 방의 `WAITING` 전환)
- [ ] WebSocket 재연결 시 구독을 먼저 걸고 REST로 전체 동기화하는 흐름이 동작한다(이벤트 유실 방지)
- [ ] 여러 조회가 겹쳐도 요청 세대 번호로 최신 응답만 반영된다 — 느리게 온 옛 응답이 화면을 되돌리지 않는다(경쟁 조건 검증)
- [ ] WebSocket 연결이 계속 안 되는 동안(스크립트 없음, 핸드셰이크·nginx 실패) REST 폴백 폴링이 관리자 대기열·열어 둔 방을 계속 갱신하고, 연결되면 스스로 멈춘다
- [ ] 질문을 보낸 뒤 새로고침하거나 다른 탭에서 열어도 봇 응답 대기 상태가 복원되고, 같은 방에 봇 호출이 겹치지 않는다
- [x] "봇 응답 대기 중 UX"(정할 것 남은 항목)가 팀 논의를 거쳐 확정되고 이 문서에 반영된다 — "설계 > 7. 봇 응답 대기 중 UX" 참고
- [ ] WebSocket 실시간 갱신(수신)이 동작한다 — 발신은 1차에서 기존 REST POST 유지
- [ ] 운영 nginx에 WebSocket 업그레이드 헤더 설정을 확인·추가한다(로컬에서만 되고 운영에서 실패하는 상황 방지)
- [ ] `src/test/js`의 기존 상담 채팅 수용 테스트(`admin-chat-acceptance.test.js` 등)가 회귀 없이 통과한다
- [ ] 팀 리뷰
