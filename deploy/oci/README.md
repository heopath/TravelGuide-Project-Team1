# Oracle Cloud 배포

`main` 브랜치에 변경사항이 병합되면 GitHub Actions가 애플리케이션을 빌드한 뒤
Oracle Cloud 앱 서버로 JAR 파일만 전송합니다. 서버에서는 새 릴리스를 별도
디렉터리에 설치하고 상태 확인이 끝난 뒤 성공으로 처리합니다. 상태 확인에 실패하면
`current` 심볼릭 링크를 직전 릴리스로 되돌리고 서비스를 다시 시작합니다.

## GitHub production 환경 설정

Repository settings의 `Environments > production`에 다음 값을 등록합니다.

Variables:

- `OCI_APP_HOST`: Oracle Cloud 앱 서버의 공인 IP 또는 DNS 이름
- `OCI_APP_USER`: SSH 사용자명(현재 서버는 `ubuntu`)

Secrets:

- `OCI_SSH_PRIVATE_KEY`: 앱 서버 접속용 전용 SSH 개인키
- `OCI_KNOWN_HOSTS`: 앱 서버의 검증된 SSH host key 한 줄

개인키, 데이터베이스 비밀번호, API 키는 저장소 파일이나 Actions 로그에 넣지
않습니다. 애플리케이션 환경변수는 서버의 `/etc/all-my-trips/runtime.env`에서
관리합니다.

## 서버 전제조건

- `allmytrips` 시스템 사용자와 `/opt/all-my-trips/releases` 디렉터리
- `/etc/all-my-trips/runtime.env`
- `all-my-trips.service`
- `ubuntu` 사용자의 비대화형 `sudo`
- 서버 내부 `http://127.0.0.1:8080/actuator/health` 상태 확인

워크플로는 앱 서버만 재배포하며 별도 PostgreSQL 서버의 데이터나 서비스를
변경하지 않습니다.
