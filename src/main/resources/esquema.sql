-- Esquema de la base de datos PostgreSQL (fuente de verdad, no modificar).
-- Aplicar una sola vez:
--   psql -U postgres -d mantenimiento -f esquema.sql

CREATE TABLE area (
    id_area INTEGER GENERATED ALWAYS AS IDENTITY,
    nombre VARCHAR(100) NOT NULL,
    descripcion VARCHAR(250),

    CONSTRAINT pk_area PRIMARY KEY (id_area),
    CONSTRAINT uq_area_nombre UNIQUE (nombre)
);

CREATE TABLE equipo (
    codigo VARCHAR(30),
    nombre VARCHAR(120) NOT NULL,
    id_area INTEGER NOT NULL,

    CONSTRAINT pk_equipo PRIMARY KEY (codigo),
    CONSTRAINT fk_equipo_area
        FOREIGN KEY (id_area)
        REFERENCES area(id_area)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);

CREATE TABLE orden_mantenimiento (
    id_orden BIGINT GENERATED ALWAYS AS IDENTITY,
    consecutivo BIGINT GENERATED ALWAYS AS IDENTITY,
    codigo_equipo VARCHAR(30) NOT NULL,
    tipo VARCHAR(15) NOT NULL,
    descripcion TEXT NOT NULL,
    impacto_produccion SMALLINT NOT NULL,
    riesgo_seguridad SMALLINT NOT NULL,
    hay_respaldo BOOLEAN NOT NULL,
    estado VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE',
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_cierre TIMESTAMP NULL,

    CONSTRAINT pk_orden_mantenimiento
        PRIMARY KEY (id_orden),

    CONSTRAINT uq_orden_consecutivo
        UNIQUE (consecutivo),

    CONSTRAINT fk_orden_equipo
        FOREIGN KEY (codigo_equipo)
        REFERENCES equipo(codigo)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT ck_orden_tipo
        CHECK (tipo IN ('PREVENTIVO', 'CORRECTIVO')),

    CONSTRAINT ck_orden_impacto
        CHECK (impacto_produccion BETWEEN 1 AND 3),

    CONSTRAINT ck_orden_riesgo
        CHECK (riesgo_seguridad BETWEEN 1 AND 3),

    CONSTRAINT ck_orden_estado
        CHECK (estado IN ('PENDIENTE', 'CERRADA', 'CANCELADA')),

    CONSTRAINT ck_orden_fechas
        CHECK (
            (estado = 'PENDIENTE' AND fecha_cierre IS NULL)
            OR
            (estado IN ('CERRADA', 'CANCELADA')
             AND fecha_cierre IS NOT NULL)
        )
);

CREATE INDEX idx_orden_estado
    ON orden_mantenimiento(estado);

CREATE INDEX idx_orden_equipo
    ON orden_mantenimiento(codigo_equipo);

CREATE INDEX idx_orden_prioridad
    ON orden_mantenimiento(estado, consecutivo);
