package com.grupo7.sgmi.dto;

import java.math.BigDecimal;
import java.util.List;

public record CarbonoResumenDTO(
        BigDecimal totalEmisionesKgCo2,
        int equiposConEmisiones,
        int totalRegistros,
        BigDecimal factorEmision,
        List<CarbonoEmisionesAreaDTO> emisionesPorArea,
        List<CarbonoEmisionesEquipoDTO> emisionesPorEquipo,
        List<CarbonoRegistroDTO> registros) {
}
