#!/bin/sh
set -eu

: "${APP_ENV:?Defina APP_ENV=development para habilitar este comando}"
: "${DEV_DATABASE_URL:?Defina DEV_DATABASE_URL apuntando a la base de desarrollo}"
: "${DEV_DATABASE_NAME:?Defina DEV_DATABASE_NAME con el nombre exacto de la base}"
: "${CONFIRM_RESET_DEVELOPMENT_DATA:?Defina CONFIRM_RESET_DEVELOPMENT_DATA=YES para confirmar}"

if [ "$APP_ENV" != "development" ] || [ "$CONFIRM_RESET_DEVELOPMENT_DATA" != "YES" ]; then
    echo "Reinicio cancelado: requiere APP_ENV=development y CONFIRM_RESET_DEVELOPMENT_DATA=YES." >&2
    exit 1
fi

case "$DEV_DATABASE_NAME" in
    *prod*|*production*)
        echo "Reinicio cancelado: nunca se permite reiniciar una base de producción." >&2
        exit 1
        ;;
esac

# Por omision solo se aceptan bases con nombre de desarrollo. Una base de pruebas
# institucional con otro nombre (por ejemplo defaultdb) exige escribir su nombre
# exacto en RESET_CONFIRM_DATABASE_NAME; el SQL vuelve a verificar ese valor.
confirm_name=${RESET_CONFIRM_DATABASE_NAME:-}
if [ -n "$confirm_name" ]; then
    if [ "$confirm_name" != "$DEV_DATABASE_NAME" ]; then
        echo "Reinicio cancelado: RESET_CONFIRM_DATABASE_NAME debe coincidir exactamente con DEV_DATABASE_NAME." >&2
        exit 1
    fi
else
    case "$DEV_DATABASE_NAME" in
        *dev*|*development*|*local*|*test*) ;;
        *)
            echo "Reinicio cancelado: el nombre de la base debe indicar dev, development, local o test." >&2
            exit 1
            ;;
    esac
fi

actual_database=$(psql "$DEV_DATABASE_URL" -XAtq -v ON_ERROR_STOP=1 -c 'SELECT current_database()')
if [ "$actual_database" != "$DEV_DATABASE_NAME" ]; then
    echo "Reinicio cancelado: la URL apunta a '$actual_database', no a '$DEV_DATABASE_NAME'." >&2
    exit 1
fi

PGOPTIONS="${PGOPTIONS:-} -c app.reset_guard=DEVELOPMENT_ONLY -c app.reset_confirmed_database=$confirm_name" \
    psql "$DEV_DATABASE_URL" -X -v ON_ERROR_STOP=1 \
    -f "$(dirname "$0")/../src/main/resources/reiniciar_datos_desarrollo.sql"
echo "Datos operativos de desarrollo reiniciados."
