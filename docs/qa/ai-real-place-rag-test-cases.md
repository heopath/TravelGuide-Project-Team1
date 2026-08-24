# 실제 장소 기반 AI RAG QA

| ID | 시나리오 | 기대 결과 |
| --- | --- | --- |
| AI-RAG-REAL-01 | 실제 장소 후보가 없는 상태에서 장소·카테고리 질문 | 카카오 로컬 검색 결과가 `places`에 `KAKAO` 공급자로 저장되고 RAG 근거로 AI에 전달된다. |
| AI-RAG-REAL-02 | 같은 장소를 다시 질문 | `(external_provider, external_place_id)` 기준 upsert되어 중복 장소가 생성되지 않는다. |
| AI-RAG-REAL-03 | 저장된 카카오 장소가 있는 상태에서 유사 질문 | 최신 카카오 후보를 우선하고 기존 pgvector 후보를 보조로 함께 전달한다. |
| AI-RAG-REAL-04 | `KAKAO_REST_API_KEY` 미설정 | 외부 장소 검색은 생략되고 AI 기본 추천 흐름은 유지된다. |
| AI-RAG-REAL-05 | 카카오 API 오류·시간 초과 | 오류가 사용자에게 노출되지 않으며 기본 추천 흐름이 유지된다. |
| AI-RAG-REAL-06 | 테스트용 `LOCAL_SEED` 문서만 존재 | 테스트 장소명은 사용자 추천 근거로 전달되지 않는다. |
| AI-RAG-REAL-07 | 여러 DAY에 서로 다른 지역·장소 유형 요청 | 각 DAY의 지역별 카카오 후보가 고르게 전달되어, 앞 DAY 결과가 뒤 DAY 후보를 밀어내지 않는다. |
| AI-RAG-REAL-08 | 카카오가 기타/미분류 장소를 반환 | DB 허용 카테고리인 `ATTRACTION`으로 저장되어 `ck_places_category` 오류가 발생하지 않는다. |
| AI-RAG-REAL-09 | 카카오 장소 한 건의 DB 저장이 실패 | 오류는 로그로 남고, 저장된 다른 후보·기존 RAG 후보 또는 기본 AI 추천으로 응답이 계속된다. |
| AI-RAG-REAL-10 | 다수 지역·유형을 포함한 긴 질문 | 카카오 검색어는 최대 8개이며 전체 7초 시간 예산이 끝나면 남은 검색을 생략하고 AI 응답을 계속한다. |
| AI-RAG-REAL-11 | `DAY 2 식당 갔다가 할 수 있는 게 뭐가 있어?`, `점심 먹고 뭐할지 추천해줘`처럼 기준 상호를 다시 쓰지 않은 후속 활동 질문 | 선택 DAY의 마지막 실제 일정 장소 좌표를 기준으로 2km 내 관광·명소 후보를 카카오에서 검색하며, 검증된 실제 장소는 일정에 추가 가능한 카드로 표시된다. |

## 자동화 테스트

```powershell
.\gradlew.bat test --tests "org.example.all_my_trip_project.domain.place.service.KakaoLocalPlaceClientTest" --tests "org.example.all_my_trip_project.domain.place.service.KakaoPlaceDiscoveryServiceTest" --tests "org.example.all_my_trip_project.domain.ai.service.AiGuideServiceTest" --tests "org.example.all_my_trip_project.domain.rag.service.PlaceRagServiceTest"
```
