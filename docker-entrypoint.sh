#!/bin/sh
set -eu

if [ -n "${DB_URL:-}" ]; then
    case "$DB_URL" in
        jdbc:postgresql://*) database_address=${DB_URL#jdbc:postgresql://} ;;
        postgresql://*) database_address=${DB_URL#postgresql://} ;;
        postgres://*) database_address=${DB_URL#postgres://} ;;
        *) echo "DB_URL is not a valid PostgreSQL connection string" >&2; exit 1 ;;
    esac

    case "$database_address" in
        *@*) database_address=${database_address#*@} ;;
    esac
    case "$database_address" in
        ""|/*|:*) echo "DB_URL is not a valid PostgreSQL connection string" >&2; exit 1 ;;
    esac

    jdbc_url="jdbc:postgresql://$database_address"
    export SPRING_DATASOURCE_URL="$jdbc_url"
fi

exec java -jar /app/app.jar "$@"