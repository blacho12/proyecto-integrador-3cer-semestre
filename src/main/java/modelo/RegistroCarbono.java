package modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registro historico de emisiones adicionales de un equipo.
 * Corresponde a la tabla registro_carbono; nombreEquipo y nombreArea se obtienen
 * mediante JOIN solo para mostrarlos en la interfaz y no se almacenan de nuevo.
 */
public class RegistroCarbono {
    private final long idRegistro;
    private final String codigoEquipo;
    private final String nombreEquipo;
    private final String nombreArea;
    private final BigDecimal horasMalEstado;
    private final BigDecimal consumoAdicionalKwh;
    private final BigDecimal emisionesKgCo2;
    private final BigDecimal factorEmision;
    private final LocalDateTime fechaRegistro;

    public RegistroCarbono(long idRegistro, String codigoEquipo, String nombreEquipo,
                           String nombreArea, BigDecimal horasMalEstado,
                           BigDecimal consumoAdicionalKwh, BigDecimal emisionesKgCo2,
                           BigDecimal factorEmision, LocalDateTime fechaRegistro) {
        this.idRegistro = idRegistro;
        this.codigoEquipo = codigoEquipo;
        this.nombreEquipo = nombreEquipo;
        this.nombreArea = nombreArea;
        this.horasMalEstado = horasMalEstado;
        this.consumoAdicionalKwh = consumoAdicionalKwh;
        this.emisionesKgCo2 = emisionesKgCo2;
        this.factorEmision = factorEmision;
        this.fechaRegistro = fechaRegistro;
    }

    public long getIdRegistro() { return idRegistro; }
    public String getCodigoEquipo() { return codigoEquipo; }
    public String getNombreEquipo() { return nombreEquipo; }
    public String getNombreArea() { return nombreArea; }
    public BigDecimal getHorasMalEstado() { return horasMalEstado; }
    public BigDecimal getConsumoAdicionalKwh() { return consumoAdicionalKwh; }
    public BigDecimal getEmisionesKgCo2() { return emisionesKgCo2; }
    public BigDecimal getFactorEmision() { return factorEmision; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
}
