#!/bin/sh
# Ejecuta las pruebas de integracion PostgreSQL en una base aislada.
# No usa la base de .env ni Aiven: si TEST_DB_URL no esta definida, levanta un
# cluster temporal en el directorio de temporales y lo detiene al terminar.
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)

if [ -n "${TEST_DB_URL:-}" ]; then
    echo "Usando la base indicada por TEST_DB_URL."
else
    PG_BIN=${PG_BIN:-}
    if [ -z "$PG_BIN" ]; then
        for candidato in /usr/lib/postgresql/*/bin; do
            if [ -x "$candidato/initdb" ]; then
                PG_BIN=$candidato
            fi
        done
    fi
    if [ -z "$PG_BIN" ] && command -v pg_config >/dev/null 2>&1; then
        PG_BIN=$(pg_config --bindir)
    fi
    if [ ! -x "$PG_BIN/initdb" ]; then
        echo "No se encontro initdb. Instale PostgreSQL o defina PG_BIN/TEST_DB_URL." >&2
        exit 1
    fi

    WORK_ROOT=${TMPDIR:-/tmp}/opencode/sgmi-integracion
    PGDATA=$WORK_ROOT/data
    PGSOCK=$WORK_ROOT/socket
    PORT=${TEST_DB_PORT:-55432}

    rm -rf "$WORK_ROOT"
    mkdir -p "$PGDATA" "$PGSOCK"

    cleanup() {
        "$PG_BIN/pg_ctl" -D "$PGDATA" -m fast stop >/dev/null 2>&1 || true
    }
    trap cleanup EXIT INT TERM

    "$PG_BIN/initdb" -D "$PGDATA" -U postgres -A trust \
        --no-locale --encoding=UTF8 >/dev/null
    "$PG_BIN/pg_ctl" -D "$PGDATA" \
        -o "-p $PORT -k $PGSOCK -c listen_addresses=127.0.0.1" \
        -l "$WORK_ROOT/postgres.log" start >/dev/null
    "$PG_BIN/createdb" -h "$PGSOCK" -p "$PORT" -U postgres sgmi_test

    export TEST_DB_URL="jdbc:postgresql://127.0.0.1:$PORT/sgmi_test"
    export TEST_DB_USER=postgres
    export TEST_DB_PASSWORD=
fi

echo "Base de integracion: $TEST_DB_URL"
cd "$ROOT"
./mvnw -q -Dtest=BajaEquipoIntegracionTest test
echo "Pruebas de integracion completadas."
