# AI-12 기존 일정 시간 충돌 방지 테스트

## 정책

- AI가 추천하는 일정은 같은 DAY에 저장된 기존 일정과 시간이 겹치면 추가하지 않는다.
- 종료 시간이 없는 기존 일정은 시작 시각부터 2시간을 사용한 것으로 본다.
- 시간 충돌 판단은 브라우저와 서버에서 모두 수행한다.
- 사용자가 직접 등록하는 일반 일정은 이 정책의 대상이 아니다.

## 수동 테스트

| ID | 상황 | 기대 결과 |
| --- | --- | --- |
| AI-TIME-01 | DAY 1에 10:00 기존 일정이 있고 AI가 11:00 장소를 추천 | `시간 겹침`으로 표시되고 추가 버튼이 비활성화된다. |
| AI-TIME-02 | DAY 1에 10:00 기존 일정이 있고 AI가 12:00 장소를 추천 | 추가할 수 있다. |
| AI-TIME-03 | DAY 1에는 장소 A가 없고 DAY 2에만 장소 A가 있음 | 전체 보기에서도 DAY 1 추천의 A는 `추가됨`으로 표시되지 않는다. |
| AI-TIME-04 | 시간 겹침 항목과 정상 항목을 함께 일괄 추가 | 정상 항목만 저장되고 결과에 시간 겹침 개수가 표시된다. |
| AI-TIME-05 | 오래 열린 화면에서 다른 탭이 먼저 일정을 추가한 뒤 AI 항목 저장 | 서버가 `ITINERARY_TIME_CONFLICT`를 반환하며 중복 저장하지 않는다. |

## 자동 테스트

```powershell
.\gradlew.bat test --tests "org.example.all_my_trip_project.domain.trip.service.ItineraryItemServiceTest"
.\gradlew.bat test --tests "org.example.all_my_trip_project.domain.ai.service.CohereAiModelClientTest"
```
