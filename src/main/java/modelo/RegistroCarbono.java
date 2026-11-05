package modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Registro historico persistido de emisiones adicionales. */
public record RegistroCarbono(
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
