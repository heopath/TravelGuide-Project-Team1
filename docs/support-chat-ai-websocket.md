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

## 설계

### 1. 봇 연동 지점

`domain.ai`에 이미 `AiModelClient` 인터페이스와 두 구현체(`GeminiAiModelClient`, `CohereAiModelClient`)가 있습니다. 여행 일정 프롬프트 전용으로 짜여 있어 그대로 재사용하기는 어렵고, 같은 `ChatModel`/Cohere 클라이언트 설정을 공유하되 **상담용 프롬프트를 별도로 갖는 구현체**가 필요해 보입니다(가칭 `SupportChatBotClient`, `domain.support` 아래).

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

즉 관리자 쪽 코드는 그대로 두고, `SupportChatService`의 손님 쪽 메서드 몇 개만 손대면 됩니다.

### 2. WebSocket 설계 (제안, 미확정)

- `build.gradle`에 `spring-boot-starter-websocket` 추가 필요.
- STOMP over SockJS 제안 — 예: 핸드셰이크 `/ws/support-chat`, 구독 `/topic/support-chat/rooms/{roomId}`, 발행 `/app/support-chat/{roomId}/send`. **구체적인 경로 이름은 팀 논의 후 확정합니다.**
- 인증: 이 프로젝트는 세션·쿠키 인증을 씁니다. STOMP 핸드셰이크가 기존 `JSESSIONID` 쿠키를 그대로 탈 수 있는지, 아니면 별도 처리가 필요한지 확인이 필요합니다(미정).
- 기존 REST 엔드포인트(`open`/`messages`/`takeover`/`close`)는 유지하고, **"새 메시지 도착을 실시간으로 알리는 부분"만 WebSocket으로 바꾸는 방향을 우선 검토**합니다. 메시지 발신 자체를 WebSocket으로 옮길지는 아래 "정할 것" 참고.

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

`status`/`senderType`은 REST와 동일한 값 집합(`BOT`/`WAITING`/`ASSIGNED`/`CLOSED`, `USER`/`BOT`/`ADMIN`)을 그대로 씁니다. 서버 구현은 `SimpMessagingTemplate.convertAndSend()`로 기존 DTO 인스턴스를 그대로 넘기면 되므로, 직렬화 로직을 새로 짤 필요가 없습니다.

**오류 전달(제안, 미정)** — 권한 없는 방 구독 시도, 메시지 검증 실패(2000자 초과 등) 같은 개인별 오류는 Spring STOMP의 사용자 전용 큐(`/user/queue/support-chat/errors`)로 보내는 방향을 검토합니다. 구체적인 오류 코드 형식은 "정할 것"에 남겨둡니다.

### 4. 상태 전이

```text
방 생성 → BOT (봇이 자동 응답)
            │
            ├─ 관리자가 "내가 응대하기" ──▶ ASSIGNED (사람이 응대)
            │
            └─ (제안, 미정) 봇이 답을 못 찾음 ──▶ WAITING (사람 대기)

ASSIGNED / WAITING → 관리자가 "상담 종료" ──▶ CLOSED
```

## 로그·기록

- **이미 해결됨** — `support_chat_messages`가 append-only 로그이고, 봇 메시지도 같은 테이블에 `sender_type='BOT'`으로 쌓입니다. 새 테이블·컬럼이 필요 없습니다.
- `takeover()`(응대 이관)를 `admin_audit_logs`에 남길지는 별도 논의 대상입니다 — 현재 `SupportChatService.takeover()`는 감사 로그를 남기지 않습니다.

## 정할 것 (팀 논의 필요)

1. **봇 프롬프트/모델** — AI 여행 가이드와 같은 Gemini/Cohere 키·설정을 재사용할지, 상담 전용 시스템 프롬프트를 어떻게 짤지.
2. **봇이 답을 못 찾을 때** — 자동으로 `WAITING`(사람 대기)으로 넘길지, 계속 봇이 응대하며 손님이 직접 "상담원 연결"을 요청하게 할지.
3. **WebSocket 인증 방식** — 세션 쿠키 재사용 가능 여부 확인.
4. **발신 경로** — 메시지 전송도 WebSocket으로 옮길지, 지금처럼 REST POST로 보내고 수신(push)만 WebSocket으로 할지.
5. **봇 응답 대기 중 UX** — 타이핑 표시 등이 필요한지.
6. **오류 페이로드 형식** — 사용자 전용 오류 큐(`/user/queue/support-chat/errors`)를 쓸지, 어떤 필드(코드/메시지)를 담을지.

(WebSocket 채택 여부 자체는 "왜 WebSocket인가"에서 이미 확정됐으므로 여기서 다시 논의하지 않습니다.)

## 범위 밖

- 파일·이미지 전송 (이슈 #247에서 이미 범위 밖으로 명시, 이번에도 유지)
- 상담 만족도 평가
- AI 여행 가이드(`domain.ai`)와의 통합·공용 UI

## 완료 기준

- [ ] 방 생성 시 `BOT` 상태로 시작하고 첫 메시지가 자동으로 달린다
- [ ] 관리자가 응대를 넘겨받으면(`ASSIGNED`) 봇 응답이 멈춘다
- [ ] "정할 것" 항목이 팀 논의를 거쳐 확정되고 이 문서에 반영된다
- [ ] WebSocket 실시간 갱신이 동작한다(발신 경로 정책 확정 후)
- [ ] 운영 nginx에 WebSocket 업그레이드 헤더 설정을 확인·추가한다(로컬에서만 되고 운영에서 실패하는 상황 방지)
- [ ] 기존 REST API·폴링 화면과의 하위 호환 또는 교체 범위가 확정된다
- [ ] `src/test/js`의 기존 상담 채팅 수용 테스트(`admin-chat-acceptance.test.js` 등)가 회귀 없이 통과한다
- [ ] 팀 리뷰
