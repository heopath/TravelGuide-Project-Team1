# AI-07 장기 대화 이력 DB 테스트 체크리스트

| ID | 시나리오 | 기대 결과 |
| --- | --- | --- |
| AI-HISTORY-01 | 로그인 사용자가 `tripId`와 질문으로 AI 요청 성공 | 질문과 AI 응답이 Redis와 DB 세션/메시지에 저장된다. |
| AI-HISTORY-02 | 같은 사용자·같은 여행에서 연속 질문 | Redis에는 최근 3턴만 남고 DB에는 순서대로 질문·응답이 저장된다. |
| AI-HISTORY-03 | Redis 키 만료 또는 삭제 후 같은 여행으로 요청 | DB의 최근 최대 3턴이 Cohere 요청 프롬프트 맥락으로 사용된다. |
| AI-HISTORY-04 | 다른 사용자 또는 다른 `tripId` 요청 | 다른 사용자의 DB 대화 이력이 조회되거나 프롬프트에 포함되지 않는다. |
| AI-HISTORY-05 | Redis 연결 실패 상태에서 AI 요청 성공 | DB 저장은 시도되며 Cohere 기본 추천은 유지된다. |
| AI-HISTORY-06 | DB 저장/조회 실패 상태에서 AI 요청 | Redis 이력이 있으면 이를 사용하고, 없어도 Cohere 기본 추천은 유지된다. |
| AI-HISTORY-07 | 사용자·여행 대화 삭제 Service 실행 | Redis 키와 해당 활성 DB 세션이 함께 삭제된다. |

## 자동 테스트

```powershell
.\gradlew.bat test --tests "org.example.all_my_trip_project.domain.ai.service.AiConversationHistoryServiceTest" --tests "org.example.all_my_trip_project.domain.ai.service.AiConversationPersistenceServiceTest" --tests "org.example.all_my_trip_project.domain.ai.service.AiGuideServiceTest"
```

## PostgreSQL·Redis integration test

Set `AI_HISTORY_DB_INTEGRATION_TEST=true` together with the existing datasource and Redis environment variables in the IntelliJ Gradle test configuration.

```powershell
.\gradlew.bat test --tests "org.example.all_my_trip_project.domain.ai.service.AiConversationHistoryDatabaseIntegrationTest"
```

The test creates a temporary user and trip, verifies that one question/answer pair is stored in PostgreSQL, deletes the matching Redis key, then verifies that the same turn is loaded from PostgreSQL. The temporary session, trip, and user are removed during cleanup.
