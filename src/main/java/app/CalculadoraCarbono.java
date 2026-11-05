package app;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculos de la huella de carbono adicional.
 *
 * <pre>
 * Consumo adicional (kWh) = consumo normal (kWh/h) * horas en mal estado
 *                           * porcentaje extra / 100
 * Emisiones adicionales (kg CO2) = consumo adicional (kWh) * factor de emision
 * </pre>
 *
 * Es una estimacion, no una medicion directa. Todos los valores usan BigDecimal y se
 * redondean a cuatro decimales (HALF_UP) para conservar la precision en PostgreSQL.
 */
public final class CalculadoraCarbono {

    /** Escala usada en consumo adicional y emisiones (NUMERIC(14,4)). */
    public static final int ESCALA = 4;

    private static final BigDecimal CIEN = new BigDecimal("100");

    private CalculadoraCarbono() { /* utilitaria: no se instancia */ }

    /** Calcula consumo adicional y emisiones; rechaza valores nulos o negativos. */
    public static Resultado calcular(BigDecimal consumoNormalKwh, BigDecimal horasMalEstado,
                                     BigDecimal porcentajeExtra, BigDecimal factorEmision) {
        validarNoNegativo("consumo energetico normal", consumoNormalKwh);
        validarNoNegativo("horas en mal estado", horasMalEstado);
        validarNoNegativo("porcentaje de consumo adicional", porcentajeExtra);
        validarNoNegativo("factor de emision", factorEmision);

        BigDecimal consumoAdicional = consumoNormalKwh
                .multiply(horasMalEstado)
                .multiply(porcentajeExtra)
                .divide(CIEN)
                .setScale(ESCALA, RoundingMode.HALF_UP);

        BigDecimal emisiones = consumoAdicional
                .multiply(factorEmision)
                .setScale(ESCALA, RoundingMode.HALF_UP);

        return new Resultado(consumoAdicional, emisiones);
    }

    /** Valida que un dato de entrada exista y no sea negativo. */
    public static void validarNoNegativo(String campo, BigDecimal valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El valor de " + campo + " es obligatorio.");
        }
        if (valor.signum() < 0) {
            throw new IllegalArgumentException("El valor de " + campo + " no puede ser negativo.");
        }
    }

    /** Resultado inmutable de un calculo de emisiones adicionales. */
    public static final class Resultado {
        private final BigDecimal consumoAdicionalKwh;
        private final BigDecimal emisionesKgCo2;

        public Resultado(BigDecimal consumoAdicionalKwh, BigDecimal emisionesKgCo2) {
            this.consumoAdicionalKwh = consumoAdicionalKwh;
            this.emisionesKgCo2 = emisionesKgCo2;
        }

        public BigDecimal getConsumoAdicionalKwh() { return consumoAdicionalKwh; }
        public BigDecimal getEmisionesKgCo2() { return emisionesKgCo2; }
    }
}
