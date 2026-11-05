package com.grupo7.sgmi.dto;

import java.math.BigDecimal;

public record CarbonoEmisionesEquipoDTO(
        String codigoEquipo,
        String nombreEquipo,
        String nombreArea,
        BigDecimal emisionesKgCo2) {
}
