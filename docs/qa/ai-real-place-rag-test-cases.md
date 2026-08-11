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

## 자동화 테스트

```powershell
.\gradlew.bat test --tests "org.example.all_my_trip_project.domain.place.service.KakaoLocalPlaceClientTest" --tests "org.example.all_my_trip_project.domain.place.service.KakaoPlaceDiscoveryServiceTest" --tests "org.example.all_my_trip_project.domain.ai.service.AiGuideServiceTest" --tests "org.example.all_my_trip_project.domain.rag.service.PlaceRagServiceTest"
```
