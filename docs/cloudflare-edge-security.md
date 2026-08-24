# Cloudflare 운영 적용

## 현재 적용 상태

| 기능 | 설정 | 상태 |
| --- | --- | --- |
| Cache Rule | `/css`, `/js`, `/images`, `/media`의 GET/HEAD만 캐시 적합 처리 | 배포 완료 |
| Rate Limiting | 인증·AI·고객센터 POST API, IP별 10초 5회 초과 시 10초 차단 | 배포 완료 |
| Turnstile | `All My Trips 인증`, 관리형, `allmytrip.click`과 하위 도메인 | 위젯 생성 완료 |
| Tunnel | EC2 `cloudflared` 설치와 DNS 전환 필요 | 미적용 |
| Access | 관리자 허용 이메일/IdP 결정 필요 | 미적용 |

Cache Rule은 원본의 `Cache-Control`을 존중한다. 운영 프로필은 정적 파일에 브라우저
1시간, 공유 캐시 1일을 지정한다. 따라서 HTML과 API 응답은 캐시되지 않으며, 새 버전
배포 뒤 오래된 정적 파일이 장기간 고정되지 않는다.

## Turnstile 운영 활성화

Cloudflare 대시보드의 Turnstile 위젯에서 사이트 키와 비밀 키를 각각 복사한다. 비밀 키는
Git, 이슈, PR, 채팅에 붙이지 않는다. EC2에서 다음 파일을 연다.

```bash
sudoedit /opt/all-my-trips/shared/app.env
```

아래 네 줄을 추가한다.

```dotenv
CLOUDFLARE_TURNSTILE_ENABLED=true
CLOUDFLARE_TURNSTILE_SITE_KEY=<사이트 키>
CLOUDFLARE_TURNSTILE_SECRET_KEY=<비밀 키>
CLOUDFLARE_TURNSTILE_EXPECTED_HOSTNAME=www.allmytrip.click
```

코드가 배포된 뒤 서비스를 재시작하고 로그를 확인한다.

```bash
sudo systemctl restart all-my-trips
sudo systemctl status all-my-trips --no-pager -l
sudo journalctl -u all-my-trips --since "3 minutes ago" --no-pager \
  | grep -iE 'error|failed|exception|turnstile'
```

로그인과 회원가입을 각각 한 번 성공시키고, 브라우저 개발자 도구의 요청 본문이나 서버
로그에 Turnstile 토큰·비밀 키가 출력되지 않는지 확인한다. 문제가 있으면
`CLOUDFLARE_TURNSTILE_ENABLED=false`로 바꾸고 재시작하면 기존 인증 흐름으로 즉시 돌아간다.

## Tunnel 적용 전 체크리스트

Tunnel은 Cloudflare에서 터널만 만든다고 끝나지 않는다. EC2에 `cloudflared`를 설치하고
서비스로 등록한 뒤, `www` DNS를 터널로 전환하고 보안 그룹에서 기존 공개 원본 포트를
닫아야 실제로 원본 IP가 보호된다. DNS 전환은 서비스 중단 위험이 있으므로 다음 조건을
확인한 별도 배포로 진행한다.

- 현재 Nginx가 받는 내부 포트와 헬스 체크 URL
- EC2에서 Cloudflare로 나가는 443 연결 허용 여부
- 롤백할 기존 DNS 레코드 값과 TTL
- 터널 연결이 2개 이상 `HEALTHY`인 상태
- 전환 뒤 로그인, WebSocket 고객센터, 결제 콜백 점검

## Access 적용 전 체크리스트

`/admin`과 `/api/v1/admin/*`를 Access로 보호하려면 허용할 관리자 이메일과 로그인 방식
(Google 또는 이메일 OTP)을 먼저 정해야 한다. Access 정책을 만들기 전 기존 Spring
Security 관리자 로그인을 유지하고, 정책 적용 뒤 일반 사용자·관리자·로그아웃 상태를 각각
검증한다.
