package com.grupo7.sgmi.dto;

import java.time.LocalDateTime;
import java.util.List;

import modelo.Equipo;
import modelo.OrdenMantenimiento;

/** Accion del historial de deshacer tal como se devuelve en JSON. */
public record AccionDTO(
        String tipo,
        long idAccion,
        Long idOrden,
        LocalDateTime fecha,
        OrdenMantenimiento estadoPrevio,
        Equipo equipoPrevio,
        List<OrdenMantenimiento> ordenesEliminadas) {
}
