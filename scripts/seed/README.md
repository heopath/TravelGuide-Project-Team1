# 시드 스크립트

관리자가 화면에서 하는 일을 같은 API로 대신 한다. SQL로 밀어 넣지 않으므로
화면 뒤의 검증과 상태 규칙을 똑같이 거친다.

## ticket-catalog.js — 체험·축제 예약 상품

장소(ACTIVITY·FESTIVAL) 7곳과 각 장소의 예약 상품·옵션·회차·재고를 올리고
판매중으로 바꾼다.

```
장소  ->  예약 상품  ->  옵션(등급·가격)  ->  회차(이용일·시각)  ->  재고
                                                    |
                                    그 뒤는 예매 -> 결제 -> 발급 -> 검표
```

```bash
ADMIN_EMAIL=관리자@example.com ADMIN_PASSWORD=비밀번호 \
  node scripts/seed/ticket-catalog.js

# 다른 포트에 붙일 때
BASE_URL=http://localhost:8099 ADMIN_EMAIL=... ADMIN_PASSWORD=... \
  node scripts/seed/ticket-catalog.js
```

- 관리자(`ROLE_ADMIN`) 계정이 있어야 한다
- 이용 기간은 `2026-08-24 ~ 2026-09-20`. 여행이 이 안에 걸쳐 있어야 예매가 막히지 않는다
- 다시 돌려도 안전하다. 같은 이름의 장소·상품이 있으면 건너뛴다

가격과 회차는 화면을 채우기 위해 정한 값이다. 장소와 행사의 실제 요금표가 아니다.
