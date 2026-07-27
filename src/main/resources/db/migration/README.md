# Flyway migration location

Spring Boot loads versioned Flyway migrations from this directory.

Only PostgreSQL-compatible migrations may be added here because the deployed
database is Amazon RDS for PostgreSQL.

The existing files under `database/migration` target MySQL 8.0 and intentionally
remain outside the application classpath until they are converted and reviewed.

After conversion, add the PostgreSQL baseline as `V1__baseline_schema.sql` and
continue with new immutable versioned files. Never edit a migration after it has
run in a shared database.
