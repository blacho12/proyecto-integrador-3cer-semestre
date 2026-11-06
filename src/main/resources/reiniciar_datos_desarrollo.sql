-- Uso exclusivo para desarrollo. Este script elimina los datos operativos y sus historiales.
-- Ejecútelo mediante scripts/reiniciar-datos-desarrollo.sh, que valida el entorno,
-- el nombre exacto de la base y una confirmación explícita. No ejecutar en producción.
BEGIN;

DO $$
BEGIN
    IF current_setting('app.reset_guard', TRUE) IS DISTINCT FROM 'DEVELOPMENT_ONLY' THEN
        RAISE EXCEPTION 'Reinicio bloqueado: use el script de reinicio con la guarda de desarrollo.';
    END IF;
    IF current_database() ~* '(prod|production)' THEN
        RAISE EXCEPTION 'Reinicio bloqueado: nunca se permite una base de producción.';
    END IF;
    -- Una base de pruebas con nombre distinto de dev/test/local solo pasa si el
    -- operador escribió su nombre exacto en RESET_CONFIRM_DATABASE_NAME.
    IF current_database() !~* '(dev|development|local|test)'
       AND current_setting('app.reset_confirmed_database', TRUE)
           IS DISTINCT FROM current_database() THEN
        RAISE EXCEPTION 'Reinicio bloqueado: la base no está identificada como desarrollo.';
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('public.registro_carbono') IS NOT NULL THEN
        EXECUTE 'TRUNCATE TABLE public.registro_carbono, public.accion_historial, '
             || 'public.orden_mantenimiento, public.equipo, public.area RESTART IDENTITY';
    ELSE
        EXECUTE 'TRUNCATE TABLE public.accion_historial, public.orden_mantenimiento, '
             || 'public.equipo, public.area RESTART IDENTITY';
    END IF;
    IF to_regclass('public.configuracion_planta') IS NOT NULL THEN
        EXECUTE 'UPDATE public.configuracion_planta SET factor_emision = 0.1500 '
             || 'WHERE id_configuracion = 1';
    END IF;
END $$;

COMMIT;
