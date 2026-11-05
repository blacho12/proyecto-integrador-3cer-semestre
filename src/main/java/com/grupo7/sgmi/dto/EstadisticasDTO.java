package com.grupo7.sgmi.dto;

import java.util.Map;

/** Indicadores calculados con las ordenes, equipos y areas reales. */
public record EstadisticasDTO(
        long totalOrdenes,
        long pendientes,
        long cerradas,
        long canceladas,
        long equiposEnIndice,
        int alturaArbolEquipos,
        long ordenesEnCola,
        long accionesPorDeshacer,
        Map<String, Long> ordenesPorEstado,
        Map<String, Long> ordenesPorCriticidad,
        Map<String, Long> ordenesPorTipo,
        Map<String, Long> ordenesPorArea) {
}
