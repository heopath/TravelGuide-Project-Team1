# 프론트 화면 연결 안내

## 실행

기본 프로필은 `ui`이며 MySQL, PostgreSQL, Redis, Gemini API 키가 없어도 화면을 확인할 수 있다.

```shell
./gradlew bootRun
```

브라우저에서 `http://localhost:8080`으로 접속하면 `/home`으로 이동한다.

## 화면 URL

| URL | Thymeleaf 템플릿 |
| --- | --- |
| `/home` | `home/home.html` |
| `/auth/login` | `auth/login.html` |
| `/auth/signup` | `auth/signup.html` |
| `/trips/new/basic` | `trips/basic.html` |
| `/trips/new/style` | `trips/style.html` |
| `/trips/recommendations` | `trips/recommendations.html` |
| `/trips/{tripSlug}/schedule` | `trips/schedule.html` |
| `/trips/{tripSlug}/map` | `trips/map.html` |
| `/trips/{tripSlug}/optimize` | `trips/optimize.html` |
| `/trips/{tripSlug}/record` | `trips/record.html` |
| `/guide` | `guide/guide.html` |
| `/guide/themes` | `guide/themes.html` |
| `/guide/places/{placeSlug}` | `guide/place-detail.html` |
| `/ai-guide` | `guide/ai-guide.html` |
| `/booking` | `booking/booking.html` |
| `/booking/tickets/{ticketSlug}` | `booking/ticket.html` |
| `/booking/hotels` | `booking/hotels.html` |
| `/booking/flights` | `booking/flights.html` |
| `/booking/queue` | `booking/queue.html` |
| `/mypage` | `mypage/mypage.html` |
| `/admin` | `admin/admin.html` |

## 리소스 위치

- HTML: `src/main/resources/templates`
- CSS: `src/main/resources/static/css`
- JavaScript: `src/main/resources/static/js`
- 이미지: `src/main/resources/static/images`

`PageController`는 화면 반환만 담당한다. 이후 REST API Controller와 DTO는 기능별 패키지에 별도로 작성한다.
