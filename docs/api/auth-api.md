# 회원·인증 API 명세

## 1. 기본 규칙

- API 버전은 `/api/v1`로 통일한다.
- 인증 방식은 Spring Security 세션·쿠키 방식을 사용한다.
- 로그인 성공 시 `JSESSIONID` 쿠키를 발급한다.
- 비밀번호는 BCrypt로 암호화해 저장한다.
- 성공 응답은 `ApiResponse<T>`를 사용한다.
- 오류 응답은 `ErrorResponse`를 사용한다.
- 비밀번호와 비밀번호 해시는 API 응답에 포함하지 않는다.
- 로그인 사용자 ID는 요청값으로 받지 않고 인증 정보에서 조회한다.
- 회원가입 후 자동 로그인하지 않는다.
- 로그인 실패 시 이메일 존재 여부를 알려주지 않는다.

## 2. API 목록

| 기능 | Method | URL | 인증 |
|---|---|---|---|
| 회원가입 | POST | `/api/v1/auth/signup` | 불필요 |
| 로그인 | POST | `/api/v1/auth/login` | 불필요 |
| 로그아웃 | POST | `/api/v1/auth/logout` | 필요 |
| 내 정보 조회 | GET | `/api/v1/members/me` | 필요 |

---

## 3. 회원가입

### 요청

```http
POST /api/v1/auth/signup
Content-Type: application/json
```

```json
{
  "email": "user@example.com",
  "password": "Password123!",
  "nickname": "여행자"
}
```

### 요청 DTO

`SignupRequest`

| 필드 | 타입 | 필수 | 검증 규칙 |
|---|---|---:|---|
| email | String | O | 이메일 형식, 중복 불가 |
| password | String | O | 8자 이상 |
| nickname | String | O | 2자 이상 20자 이하, 중복 불가 |

비밀번호 확인값은 프론트엔드에서 검사하고 API에는 전달하지 않는다.

### 성공 응답

- HTTP 상태: `201 Created`

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "회원가입이 완료되었습니다.",
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "nickname": "여행자",
    "role": "USER",
    "status": "ACTIVE"
  }
}
```

### 발생 가능한 오류

| 오류 코드 | HTTP 상태 | 의미 |
|---|---:|---|
| VALIDATION_ERROR | 400 | 입력값 검증 실패 |
| EMAIL_DUPLICATED | 409 | 이미 사용 중인 이메일 |
| NICKNAME_DUPLICATED | 409 | 이미 사용 중인 닉네임 |

---

## 4. 로그인

### 요청

```http
POST /api/v1/auth/login
Content-Type: application/json
```

```json
{
  "email": "user@example.com",
  "password": "Password123!"
}
```

### 요청 DTO

`LoginRequest`

| 필드 | 타입 | 필수 | 검증 규칙 |
|---|---|---:|---|
| email | String | O | 이메일 형식 |
| password | String | O | 빈 값 불가 |

### 성공 처리

- 이메일과 비밀번호를 확인한다.
- 비밀번호는 BCrypt로 비교한다.
- 로그인 성공 시 세션을 생성한다.
- 응답에 `JSESSIONID` 쿠키가 포함된다.
- `last_login_at`을 갱신한다.

### 성공 응답

- HTTP 상태: `200 OK`

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "로그인되었습니다.",
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "nickname": "여행자",
    "role": "USER",
    "status": "ACTIVE"
  }
}
```

### 발생 가능한 오류

| 오류 코드 | HTTP 상태 | 의미 |
|---|---:|---|
| VALIDATION_ERROR | 400 | 입력값 검증 실패 |
| INVALID_CREDENTIALS | 401 | 이메일 또는 비밀번호 불일치 |
| ACCOUNT_SUSPENDED | 403 | 정지된 계정 |
| ACCOUNT_WITHDRAWN | 403 | 탈퇴한 계정 |

이메일이 존재하지 않는 경우와 비밀번호가 틀린 경우 모두 `INVALID_CREDENTIALS`를 반환한다.

---

## 5. 로그아웃

### 요청

```http
POST /api/v1/auth/logout
Cookie: JSESSIONID=세션값
```

요청 본문은 없다.

### 처리 내용

- 현재 로그인 세션을 종료한다.
- `JSESSIONID` 쿠키를 만료시킨다.

### 성공 응답

- HTTP 상태: `200 OK`

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "로그아웃되었습니다.",
  "data": null
}
```

### 발생 가능한 오류

| 오류 코드 | HTTP 상태 | 의미 |
|---|---:|---|
| UNAUTHORIZED | 401 | 로그인하지 않은 사용자 |

---

## 6. 내 정보 조회

### 요청

```http
GET /api/v1/members/me
Cookie: JSESSIONID=세션값
```

요청 파라미터로 `userId`를 받지 않는다. 로그인 세션의 사용자 정보를 사용한다.

### 성공 응답

- HTTP 상태: `200 OK`

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 완료되었습니다.",
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "nickname": "여행자",
    "role": "USER",
    "status": "ACTIVE"
  }
}
```

### 발생 가능한 오류

| 오류 코드 | HTTP 상태 | 의미 |
|---|---:|---|
| UNAUTHORIZED | 401 | 로그인이 필요함 |
| ACCOUNT_SUSPENDED | 403 | 정지된 계정 |
| ACCOUNT_WITHDRAWN | 403 | 탈퇴한 계정 |

---

## 7. DTO 목록

| DTO | 용도 |
|---|---|
| SignupRequest | 회원가입 요청 |
| LoginRequest | 로그인 요청 |
| LoginResponse | 로그인 성공 응답 |
| MemberResponse | 회원 정보 응답 |

`UserDTO`의 `passwordHash`는 API 응답에 직접 사용하지 않는다.

---

## 8. 공통 오류 응답 예시

```json
{
  "success": false,
  "code": "INVALID_CREDENTIALS",
  "message": "이메일 또는 비밀번호가 올바르지 않습니다.",
  "data": null,
  "errors": []
}
```

## 9. 오류 코드 목록

| 오류 코드 | HTTP 상태 | 의미 |
|---|---:|---|
| VALIDATION_ERROR | 400 | 입력값 검증 실패 |
| INVALID_CREDENTIALS | 401 | 이메일 또는 비밀번호 불일치 |
| UNAUTHORIZED | 401 | 로그인이 필요함 |
| ACCOUNT_SUSPENDED | 403 | 정지된 계정 |
| ACCOUNT_WITHDRAWN | 403 | 탈퇴한 계정 |
| EMAIL_DUPLICATED | 409 | 이메일 중복 |
| NICKNAME_DUPLICATED | 409 | 닉네임 중복 |

## 10. 보안 규칙

- 원본 비밀번호를 DB와 로그에 저장하지 않는다.
- 비밀번호는 BCrypt로 암호화한다.
- `password`와 `passwordHash`를 응답에 포함하지 않는다.
- 로그인 실패 메시지로 이메일 가입 여부를 노출하지 않는다.
- 로그아웃 시 세션을 무효화한다.
- 운영 환경의 세션 쿠키에는 `HttpOnly`, `Secure`, `SameSite` 설정을 적용한다.
- 세션·쿠키 인증을 사용하므로 상태 변경 요청에 대한 CSRF 보호를 적용한다.

## 11. 완료 기준

- [ ] 회원가입 명세 작성
- [ ] 로그인 명세 작성
- [ ] 로그아웃 명세 작성
- [ ] 내 정보 조회 명세 작성
- [ ] 요청·응답 DTO 이름 확정
- [ ] 오류 코드와 HTTP 상태 확정
- [ ] 비밀번호 정보가 응답에서 제외되는지 확인
- [ ] 팀원 검수 완료