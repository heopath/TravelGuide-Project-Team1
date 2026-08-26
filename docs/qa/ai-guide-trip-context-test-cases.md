# AI-06 여행 컨텍스트 테스트 체크리스트

| ID | 시나리오 | 기대 결과 |
| --- | --- | --- |
| AI-CONTEXT-01 | 일정 화면의 AI 가이드 이동 버튼 클릭 | 현재 `activeTripId`로 `/ai-guide?tripId={tripId}` 이동 |
| AI-CONTEXT-02 | `/ai-guide?tripId=12`에서 질문 전송 | 요청 본문에 숫자 `tripId: 12`와 `question` 포함 |
| AI-CONTEXT-03 | 같은 URL 새로고침 | URL의 같은 `tripId`를 유지하고 다음 요청에도 사용 |
| AI-CONTEXT-04 | `/ai-guide`로 직접 접근 | 여행 선택 안내를 표시하고 질문 입력·추천 질문 버튼 비활성화 |
| AI-CONTEXT-05 | `/ai-guide?tripId=abc` 접근 | 잘못된 여행 정보 안내를 표시하고 API 요청하지 않음 |
| AI-CONTEXT-06 | 본인 소유 `tripId`로 API 요청 | 기존 여행·DAY·사용자 선호가 OpenAI 요청 프롬프트에 포함되고 추천 응답 반환 |
| AI-CONTEXT-07 | 존재하지 않거나 타인 소유 `tripId`로 API 요청 | `404 TRIP_NOT_FOUND`, 타인 여행·DAY 정보 미노출 |
| AI-CONTEXT-08 | AI 요청 전후 여행·DAY 조회 | 여행, DAY, 일정 항목이 생성·수정·삭제되지 않음 |

## 자동 테스트

```powershell
.\gradlew.bat test --tests "org.example.all_my_trip_project.domain.ai.controller.AiGuideControllerTest" --tests "org.example.all_my_trip_project.domain.ai.service.AiGuideContextServiceTest" --tests "org.example.all_my_trip_project.domain.ai.service.AiGuideServiceTest" --tests "org.example.all_my_trip_project.domain.ai.service.OpenAiAiModelClientTest"
```

자동 테스트는 `tripId` 양의 정수 검증, 본인 여행·DAY·선호 컨텍스트 구성, 타인 여행 조회 실패 전파, OpenAI 요청 프롬프트 반영을 확인한다. 화면 이동 버튼과 실제 OpenAI 호출은 일정 화면 연결 후 수동으로 확인한다.
