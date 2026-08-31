#!/bin/sh
# Runs automatically via the official postgres image's
# docker-entrypoint-initdb.d mechanism — ONLY on a container's first startup,
# when its data volume is empty (see docker-compose.yaml's mount of this
# directory, and the matching note on POSTGRES_USER/POSTGRES_PASSWORD in
# .env.example). It does nothing on an already-initialized volume.
#
# Purpose: the app should never connect as the postgres superuser
# (POSTGRES_USER/POSTGRES_PASSWORD — used only to bootstrap the container and
# to run this script). Instead it connects as DB_USERNAME/DB_PASSWORD, a
# separate least-privilege role created here with just enough to run Flyway
# migrations and serve the app: CONNECT on the database, and USAGE + CREATE on
# the public schema (Postgres 15+ no longer grants CREATE on public to
# everyone by default). Every table this role creates via Flyway is owned by
# it, so ALTER TABLE/DROP CONSTRAINT in later migrations (V23, for example)
# works without any extra grants. It's never a superuser and can't create
# databases, create roles, or read/write outside this one database.
#
# CAVEAT: DB_USERNAME/DB_PASSWORD are interpolated directly into the SQL text
# below by the shell, not passed as bind parameters — avoid quote characters
# (', ") in either value, or the generated SQL will break.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    DO
    \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '$DB_USERNAME') THEN
            CREATE ROLE "$DB_USERNAME" WITH LOGIN PASSWORD '$DB_PASSWORD';
        END IF;
    END
    \$\$;

    GRANT CONNECT ON DATABASE "$POSTGRES_DB" TO "$DB_USERNAME";
    GRANT USAGE, CREATE ON SCHEMA public TO "$DB_USERNAME";
EOSQL
