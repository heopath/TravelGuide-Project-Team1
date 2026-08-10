-- Run once as the all_my_trips database owner or a PostgreSQL administrator.
-- Application/Flyway users must not be responsible for extension installation.
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;

-- 아래 둘은 Spring AI의 PgVectorStore가 스키마를 초기화할 때 요구한다.
-- PgVectorStore는 vector / hstore / uuid-ossp 세 개를 순서대로
-- CREATE EXTENSION IF NOT EXISTS 로 실행하므로, 하나라도 없으면 그 지점에서 멈춘다.
-- 관리자가 미리 설치해 두면 애플리케이션 계정의 같은 문장은 그냥 통과한다.
-- 그래서 애플리케이션 계정에 확장 설치 권한을 줄 필요가 없다. (#109)
--
-- uuid-ossp: vector_store 기본 스키마의 id 컬럼이 uuid_generate_v4() 를 기본값으로 쓴다.
-- hstore   : 현재 버전은 metadata 를 json 으로 저장해 실제로 쓰이지는 않지만,
--            초기화 문장이 조건 없이 실행되므로 없으면 실패한다.
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
