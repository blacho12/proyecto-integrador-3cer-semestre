-- Migracion para las funcionalidades de huella de carbono y gestion de areas.
-- Es idempotente: puede aplicarse sobre una base que ya tenga el esquema original.
--
--   psql -U postgres -d mantenimiento -f esquema_carbono_areas.sql
--
-- No elimina ni modifica datos historicos de mantenimiento.

-- 1) Parametros de consumo asociados a cada equipo (no se duplican por registro).
ALTER TABLE equipo
    ADD COLUMN IF NOT EXISTS consumo_normal_kwh NUMERIC(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS porcentaje_extra NUMERIC(5,2) NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_equipo_consumo_normal') THEN
        ALTER TABLE equipo
            ADD CONSTRAINT ck_equipo_consumo_normal CHECK (consumo_normal_kwh >= 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_equipo_porcentaje_extra') THEN
        ALTER TABLE equipo
            ADD CONSTRAINT ck_equipo_porcentaje_extra CHECK (porcentaje_extra >= 0);
    END IF;
END $$;

-- 2) Factor de emision electrica configurable para la planta (una sola fila).
CREATE TABLE IF NOT EXISTS configuracion_planta (
    id_configuracion SMALLINT NOT NULL,
    factor_emision NUMERIC(10,4) NOT NULL,

    CONSTRAINT pk_configuracion_planta PRIMARY KEY (id_configuracion),
    CONSTRAINT ck_configuracion_unica CHECK (id_configuracion = 1),
    CONSTRAINT ck_configuracion_factor CHECK (factor_emision >= 0)
);

INSERT INTO configuracion_planta (id_configuracion, factor_emision)
VALUES (1, 0.1500)
ON CONFLICT (id_configuracion) DO NOTHING;

-- 3) Registros historicos de emisiones adicionales.
--    Se guarda el factor utilizado para conservar la trazabilidad del calculo.
CREATE TABLE IF NOT EXISTS registro_carbono (
    id_registro BIGINT GENERATED ALWAYS AS IDENTITY,
    codigo_equipo VARCHAR(30) NOT NULL,
    horas_mal_estado NUMERIC(10,2) NOT NULL,
    consumo_adicional_kwh NUMERIC(14,4) NOT NULL,
    emisiones_kg_co2 NUMERIC(14,4) NOT NULL,
    factor_emision NUMERIC(10,4) NOT NULL,
    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_registro_carbono PRIMARY KEY (id_registro),

    CONSTRAINT fk_registro_carbono_equipo
        FOREIGN KEY (codigo_equipo)
        REFERENCES equipo(codigo)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT ck_registro_carbono_horas CHECK (horas_mal_estado >= 0),
    CONSTRAINT ck_registro_carbono_consumo CHECK (consumo_adicional_kwh >= 0),
    CONSTRAINT ck_registro_carbono_emisiones CHECK (emisiones_kg_co2 >= 0),
    CONSTRAINT ck_registro_carbono_factor CHECK (factor_emision >= 0)
);

CREATE INDEX IF NOT EXISTS idx_registro_carbono_equipo
    ON registro_carbono(codigo_equipo);

CREATE INDEX IF NOT EXISTS idx_registro_carbono_fecha
    ON registro_carbono(fecha_registro);
