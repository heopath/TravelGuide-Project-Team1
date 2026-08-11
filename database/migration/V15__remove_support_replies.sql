-- Administrator support is owned by another module. Keep this migration
-- separate so the already-applied V14 Flyway history remains immutable.
DROP TABLE IF EXISTS support_replies;
