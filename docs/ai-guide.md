# AI-01 가짜 일정 응답 화면

## 담당 범위

한성주 담당 범위인 AI 질문 입력, 로딩, 성공, 실패, 다시 시도, DTO 샘플 출력, 항공·숙소 외부 검색 링크, 테스트와 사용 설명을 정리한 문서입니다. 실제 AI·RAG 호출은 정인길 담당이며 이 PR에서는 연결하지 않습니다.

## 실행

```powershell
.\gradlew.bat bootRun
```

브라우저에서 `http://localhost:8080/ai-guide`를 엽니다. DB와 AI 키 없이 UI 프로필로 실행할 수 있습니다.

## 화면 시나리오

1. 추천 질문 칩을 누르거나 입력창에 질문을 입력하고 전송합니다.
2. 약 0.9초 동안 로딩 상태가 나타납니다.
3. `모의 응답 상태`가 `성공 응답`이면 `src/main/resources/static/js/pages/guide/ai-guide-mock-data.js`의 모의 DTO와 같은 `days → items` 구조로 날짜별 일정과 출처가 표시됩니다. 팀 공유용 동일 JSON은 `docs/examples/ai-guide-response.json`에서 확인합니다.
4. `실패 응답`으로 바꾸면 오류 안내와 `다시 시도` 버튼이 표시됩니다. 성공 응답으로 바꾼 뒤 다시 시도하면 정상 결과를 확인할 수 있습니다.
5. 항공권·숙소 검색은 새 탭으로 Google Travel 검색 화면을 엽니다. 현재는 외부 검색 이동만 제공하며 예약을 처리하지 않습니다.

## API 계약 제안 (팀장 승인 필요)

화면이 기대하는 요청은 `POST /api/v1/ai/recommendations`입니다.

```json
{ "tripId": 1, "question": "근처 저녁 맛집을 추천해줘", "day": 1 }
```

응답 JSON은 `docs/examples/ai-guide-response.json`을 기준으로 합니다. `success`, `data`, `message` 공통 응답 형식과 `days → items` DTO 필드는 팀장과 실제 AI 담당자가 최종 확정합니다. 이후 `ai-guide.js`의 `requestMock`만 실제 `fetch` 호출로 교체합니다.

## Postman 문서

`docs/postman/All-My-Trips-AI.postman_collection.json`은 향후 실제 API가 구현된 뒤 사용할 계약 예시입니다. 현재 `POST /api/v1/ai/recommendations`는 구현하지 않았으므로 성공 테스트로 실행하지 않습니다.

## 오류 재현

화면의 `모의 응답 상태`를 `실패 응답`으로 선택하고 질문을 전송합니다. 실패 상태에서도 입력창은 복구되며 이전 메시지는 유지됩니다.

## 변경 파일

- `src/main/resources/templates/guide/ai-guide.html`: AI 상태 화면 및 외부 링크 카드
- `src/main/resources/static/js/pages/guide/ai-guide.js`: DTO 기반 모의 응답·로딩·실패·재시도
- `src/main/resources/static/js/pages/guide/ai-guide-mock-data.js`: 화면에서 실제 사용하는 모의 DTO
- `src/main/resources/static/css/pages/guide/ai-guide.css`: 화면 스타일과 모바일 대응
- `docs/examples/ai-guide-response.json`: 팀 공유용 응답 샘플
