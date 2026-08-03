# 장소 캐시 정책

## 적용 대상

- 캐시 이름: `placeDetail`
- 대상 API: `GET /api/places/{placeId}`
- 키: 장소 ID
- 저장 내용: 장소 기본정보, 이미지 목록, 여행 스타일 목록

검색 결과는 조건 조합과 갱신 범위가 넓어 이번 단계에서는 캐시하지 않는다.

## 만료와 갱신

- TTL: 10분
- 장소 수정 성공 시 해당 장소 상세 캐시를 즉시 제거한다.
- 장소 삭제 성공 시 해당 장소 상세 캐시를 즉시 제거한다.
- 캐시가 제거된 뒤 첫 상세 조회에서 DB 결과를 다시 저장한다.
- 존재하지 않는 장소와 오류 응답은 캐시하지 않는다.

## 장애 처리

Redis 조회·저장·제거에 실패하면 경고 로그를 남기고 DB 처리를 계속한다. 따라서 Redis가
중단되어도 장소 상세 조회와 장소 수정·삭제 기능은 계속 사용할 수 있다.

## 로컬 확인

```text
docker compose up -d redis
GET http://localhost:8081/api/places/100
docker compose exec redis redis-cli --scan --pattern "*placeDetail*"
```

같은 상세 API를 캐시 전후로 호출했을 때 JSON 응답이 동일해야 한다.
