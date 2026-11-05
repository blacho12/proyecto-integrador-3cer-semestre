package com.grupo7.sgmi.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CarbonoRegistroDTO(
        long idRegistro,
        String codigoEquipo,
        String nombreEquipo,
        String nombreArea,
        BigDecimal horasMalEstado,
        BigDecimal consumoAdicionalKwh,
        BigDecimal emisionesKgCo2,
        BigDecimal factorEmision,
        LocalDateTime fechaRegistro) {
}
