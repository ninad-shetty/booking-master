#!/bin/sh
set -eu

if [ -n "${DB_URL:-}" ]; then
    jdbc_url=$(printf '%s' "$DB_URL" | sed \
        -e 's#^postgresql://[^@]*@#jdbc:postgresql://#' \
        -e 's#^postgres://[^@]*@#jdbc:postgresql://#')
    case "$jdbc_url" in
        jdbc:postgresql://*) ;;
        *) echo "DB_URL is not a valid PostgreSQL connection string" >&2; exit 1 ;;
    esac
    export SPRING_DATASOURCE_URL="$jdbc_url"
fi

exec java -jar /app/app.jar "$@"