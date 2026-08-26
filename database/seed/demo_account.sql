-- README에 적어 두는 공개 체험 계정.
--
-- 이 계정의 비밀번호는 비밀이 아니다. 저장소와 README에 그대로 적혀 있고, 누구나 로그인해
-- 둘러보라고 만든 것이다. 그래서 두 가지를 지킨다.
--
--   1) 역할은 항상 USER다. 다시 실행할 때도 강제로 USER로 되돌린다.
--      database/seed/demo_video.sql은 지정한 계정을 ADMIN으로 올린다. 촬영용 계정에는
--      그게 맞지만 이 계정에 쓰면 공개된 비밀번호로 관리자 화면에 들어갈 수 있게 된다.
--      (실제로는 /admin 앞에 Cloudflare Access가 한 겹 더 있지만, 그 한 겹에 기대지 않는다.)
--
--   2) 해시는 파일에 박지 않고 pgcrypto가 만든다. gen_salt('bf')는 BCrypt 해시를 만들고
--      Spring Security의 BCryptPasswordEncoder가 그대로 검증한다. 실행할 때마다 salt가
--      달라 같은 비밀번호라도 다른 해시가 남는다.
--
-- 실행 (운영 DB에 붙은 psql 세션에서):
--   \i database/seed/demo_account.sql
--
-- 도커 로컬:
--   docker compose exec -T postgres psql -U allmytrips -d all_my_trips \
--     -f - < database/seed/demo_account.sql
--
-- 비밀번호를 바꾸려면 아래 :'demo_password' 기본값과 README의 표를 함께 고친다.

\set demo_email 'demo@allmytrip.click'
\set demo_password 'AllMyTrips2026!'
\set demo_nickname '데모여행자'

BEGIN;

INSERT INTO users (email, password_hash, nickname, role, status)
VALUES (
    :'demo_email',
    crypt(:'demo_password', gen_salt('bf', 10)),
    :'demo_nickname',
    'USER',
    'ACTIVE'
)
ON CONFLICT (email) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    nickname      = EXCLUDED.nickname,
    -- 누가 올려놨더라도 되돌린다. 공개된 비밀번호를 가진 관리자를 만들지 않는다.
    role          = 'USER',
    -- 정지·탈퇴 상태로 남아 있으면 로그인이 막혀 체험 계정 구실을 못 한다.
    status        = 'ACTIVE',
    deleted_at    = NULL;

COMMIT;

-- 확인. password_hash는 찍지 않는다 — 화면 공유나 로그에 남는다.
SELECT user_id, email, nickname, role, status
FROM users
WHERE email = :'demo_email';
