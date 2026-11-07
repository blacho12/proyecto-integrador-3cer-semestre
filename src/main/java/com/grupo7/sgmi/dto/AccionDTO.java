package com.grupo7.sgmi.dto;

import java.time.LocalDateTime;

import modelo.OrdenMantenimiento;

/** Accion del historial de deshacer tal como se devuelve en JSON. */
public record AccionDTO(
        String tipo,
        long idOrden,
        LocalDateTime fecha,
        OrdenMantenimiento estadoPrevio) {
}
