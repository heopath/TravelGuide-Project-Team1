# AI-07 Postman 확인 절차

1. 로그인 쿠키가 유지된 상태에서 기존 `All My Trips - AI Guide` 컬렉션의 `Get CSRF token`을 실행한다.
2. `Generate guide with owned trip` 요청을 같은 `tripId`로 두 번 실행한다.
3. 두 번째 요청도 200 응답이며, Redis가 동작 중이면 최근 대화가 프롬프트 맥락에 포함된다.
4. Redis 키를 만료 또는 삭제한 뒤 같은 요청을 다시 실행한다.
5. 200 응답을 유지하고 DB 최근 대화 이력이 사용되는지 서버 디버그 로그 또는 DB 조회로 확인한다.

Postman 컬렉션에는 비밀번호, API 키, DB 접속 정보, Redis 접속 정보를 넣지 않는다.
