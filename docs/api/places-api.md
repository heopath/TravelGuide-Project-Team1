# 장소 목록 API

## 요청

```http
GET /api/v1/places?page=0&size=20
Accept: application/json
```

- `page`: 0부터 시작하는 페이지 번호, 기본값 `0`
- `size`: 페이지 크기, 기본값 `20`, 최댓값 `100`
- `keyword`, `category`, `region`, `styleId`: 선택 검색 조건

## 성공 응답 예시

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 완료되었습니다.",
  "data": [
    {
      "placeId": 1,
      "name": "해운대",
      "category": "ATTRACTION",
      "region": "부산",
      "city": "해운대구"
    }
  ]
}
```

## 빈 목록 응답 예시

조회 결과가 없으면 오류 대신 빈 배열을 반환한다.

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 완료되었습니다.",
  "data": []
}
```
