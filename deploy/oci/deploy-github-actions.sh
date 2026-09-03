#!/usr/bin/env bash
set -euo pipefail

JAR_SOURCE="${1:-}"
GIT_SHA="${2:-}"
UNIT="all-my-trips.service"
RELEASES_DIR="/opt/all-my-trips/releases"
CURRENT_LINK="/opt/all-my-trips/current"
HEALTH_URL="http://127.0.0.1:8080/actuator/health"

cleanup() {
  rm -f -- "$JAR_SOURCE" "$0"
}
trap cleanup EXIT

if [[ "$EUID" -ne 0 ]]; then
  echo "이 스크립트는 sudo로 실행해야 합니다." >&2
  exit 1
fi

if [[ ! -f "$JAR_SOURCE" ]]; then
  echo "배포할 JAR 파일을 찾지 못했습니다: $JAR_SOURCE" >&2
  exit 1
fi

if [[ ! "$GIT_SHA" =~ ^[0-9a-f]{40}$ ]]; then
  echo "올바르지 않은 Git 커밋 SHA입니다." >&2
  exit 1
fi

if [[ ! -f /etc/all-my-trips/runtime.env ]]; then
  echo "/etc/all-my-trips/runtime.env 파일이 없습니다." >&2
  exit 1
fi

PREVIOUS_RELEASE="$(readlink -f "$CURRENT_LINK" 2>/dev/null || true)"
RELEASE_ID="$(date -u +%Y%m%dT%H%M%SZ)-${GIT_SHA:0:12}"
RELEASE_DIR="$RELEASES_DIR/$RELEASE_ID"

restore_previous_release() {
  if [[ -z "$PREVIOUS_RELEASE" || ! -f "$PREVIOUS_RELEASE/app.jar" ]]; then
    echo "복구할 이전 버전이 없습니다." >&2
    return 1
  fi

  echo "이전 버전으로 복구합니다: $PREVIOUS_RELEASE" >&2
  ln -sfn "$PREVIOUS_RELEASE" "$CURRENT_LINK"
  if ! systemctl restart "$UNIT"; then
    echo "이전 버전의 서비스를 다시 시작하지 못했습니다." >&2
    return 1
  fi

  for _ in $(seq 1 30); do
    if curl --fail --silent "$HEALTH_URL" | grep -q '"status":"UP"'; then
      rm -rf -- "$RELEASE_DIR"
      echo "이전 버전 복구를 확인했습니다." >&2
      return 0
    fi
    sleep 2
  done

  echo "이전 버전도 정상 기동하지 못했습니다." >&2
  return 1
}

install -d -m 0755 "$RELEASE_DIR"
install -m 0644 "$JAR_SOURCE" "$RELEASE_DIR/app.jar"
chown -R allmytrips:allmytrips "$RELEASE_DIR"
ln -sfn "$RELEASE_DIR" "$CURRENT_LINK"

if ! systemctl restart "$UNIT"; then
  echo "새 버전의 서비스를 시작하지 못했습니다." >&2
  journalctl -u "$UNIT" -n 80 --no-pager >&2 || true
  restore_previous_release || true
  exit 1
fi

for _ in $(seq 1 45); do
  if curl --fail --silent "$HEALTH_URL" | grep -q '"status":"UP"'; then
    echo "배포 성공: $RELEASE_ID"
    find "$RELEASES_DIR" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' \
      | sort -nr \
      | tail -n +5 \
      | cut -d' ' -f2- \
      | xargs -r rm -rf --
    exit 0
  fi
  sleep 2
done

echo "새 버전의 상태 확인에 실패했습니다." >&2
journalctl -u "$UNIT" -n 80 --no-pager >&2 || true
restore_previous_release || true
exit 1
