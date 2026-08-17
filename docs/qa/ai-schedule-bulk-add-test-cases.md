# AI 추천 일정 추가 QA 체크리스트

## 사전 조건

- 로그인한 사용자가 본인 여행의 일정 화면(`/trips/{tripId}/schedule`)에 접속한다.
- DAY 1, DAY 2가 존재하고 각 DAY에는 서로 다른 실제 장소가 하나 이상 저장되어 있다.
- AI 추천 결과에는 카카오 또는 RAG로 검증된 실제 장소와 일반 안내 항목이 함께 있을 수 있다.

| ID | 시나리오 | 기대 결과 |
| --- | --- | --- |
| AI-BULK-01 | 검증된 실제 장소가 있는 DAY 1 추천 카드에서 `DAY 1 일정 모두 추가` 선택 | 실제 장소 항목만 DAY 1에 저장되고, DAY 1 일정 목록과 추가 상태가 즉시 갱신된다. |
| AI-BULK-02 | 검증되지 않은 일반 안내 항목 확인 | `지도 보기`, `일정에 추가`, `DAY 일정 모두 추가` 기능이 노출되지 않는다. |
| AI-BULK-03 | 이미 저장된 장소가 포함된 추천을 다시 확인 | 저장된 장소는 `추가됨`으로 표시되고 중복 요청을 보내지 않는다. |
| AI-BULK-04 | DAY 1과 DAY 2에 서로 다른 장소를 저장한 뒤 전체 보기에서 추천 카드 확인 | 추천 DAY별 일정 목록을 따로 조회하며, 다른 DAY의 장소 때문에 잘못 `추가됨`으로 표시되지 않는다. |
| AI-BULK-05 | 중간 순서의 일정(예: sort order 1)을 삭제한 뒤 새 실제 장소 추가 | 새 장소가 마지막 순서로 저장되며 sort order 유일 제약 충돌이 발생하지 않는다. |
| AI-BULK-06 | 같은 DAY에 같은 실제 장소를 다른 탭 또는 연속 요청으로 두 번 추가 | 서버와 DB가 중복 저장을 막고 화면에는 `이미 추가됨` 상태가 반영된다. |
| AI-BULK-07 | AI로 추가한 실제 장소를 일정 화면에서 삭제 | `allmytrips:schedule-changed` 이벤트로 추천 카드가 갱신되고 버튼이 다시 `일정에 추가`로 돌아온다. |
| AI-BULK-08 | 여러 실제 장소를 DAY 일괄 추가하는 중 일부 요청 실패 | 추가됨·이미 추가됨·실패 개수를 구분해 안내하고, 성공한 장소는 그대로 유지한다. |
| AI-BULK-09 | `다른 곳 추천해줘`처럼 재추천 질문 | 이전 응답에서 제안된 실제 장소는 후보에서 제외하고, 새 검증 장소가 있을 때만 지도·일정 추가 기능을 제공한다. |
| AI-BULK-10 | 부산 여행과 서울 여행에서 각각 특정 장소 근처 추천 | 현재 여행의 목적지와 일정 장소 좌표를 기준으로 검색하며 다른 지역의 동명 지점이 일정에 연결되지 않는다. |

## 자동 검증

- `ItineraryItemServiceTest`
  - 삭제 후 `MAX(sort_order) + 1`로 저장하는지 확인
  - 같은 DAY·같은 장소의 중복 요청과 동시 중복 요청을 차단하는지 확인
- `AiGuideServiceTest`
  - 검증된 장소에만 카드 메타데이터를 붙이는지 확인
  - 재추천 시 기존 후보를 제외하는지 확인
  - 일정 장소 좌표를 주변 추천 검색 기준으로 사용하는지 확인
- `KakaoPlaceDiscoveryServiceTest`
  - 목적지에 맞는 동명 지점을 선택하는지 확인
  - 도보·일반 근처 추천 반경이 2km를 넘지 않는지 확인
  - 쇼핑·카페·식당 등 질문별 업종 검색어를 구성하는지 확인

## 실행 명령

```powershell
.\gradlew.bat test --tests "org.example.all_my_trip_project.domain.trip.service.ItineraryItemServiceTest"
.\gradlew.bat test --tests "org.example.all_my_trip_project.domain.ai.service.AiGuideServiceTest"
.\gradlew.bat test --tests "org.example.all_my_trip_project.domain.place.service.KakaoPlaceDiscoveryServiceTest"
```
