-- Run with psql while connected to the postgres maintenance database.
-- psql meta-commands make database creation repeatable for local development.
SELECT 'CREATE DATABASE all_my_trips ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'all_my_trips')\gexec

\connect all_my_trips
\ir create_extensions.sql
