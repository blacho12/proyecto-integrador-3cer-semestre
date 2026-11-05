package modelo;

import java.math.BigDecimal;

/** Equipo de la planta industrial (clave de busqueda: codigo). Corresponde a la tabla equipo. */
public class Equipo {
    private final String codigo;
    private String nombre;
    private int idArea;
    private BigDecimal consumoNormalKwh;
    private BigDecimal porcentajeExtra;

    /** Equipo sin parametros de carbono configurados (ambos quedan en cero). */
    public Equipo(String codigo, String nombre, int idArea) {
        this(codigo, nombre, idArea, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public Equipo(String codigo, String nombre, int idArea,
                  BigDecimal consumoNormalKwh, BigDecimal porcentajeExtra) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.idArea = idArea;
        this.consumoNormalKwh = consumoNormalKwh == null ? BigDecimal.ZERO : consumoNormalKwh;
        this.porcentajeExtra = porcentajeExtra == null ? BigDecimal.ZERO : porcentajeExtra;
    }

    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public int getIdArea() { return idArea; }
    public BigDecimal getConsumoNormalKwh() { return consumoNormalKwh; }
    public BigDecimal getPorcentajeExtra() { return porcentajeExtra; }

    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setIdArea(int idArea) { this.idArea = idArea; }
    public void setConsumoNormalKwh(BigDecimal consumoNormalKwh) {
        this.consumoNormalKwh = consumoNormalKwh == null ? BigDecimal.ZERO : consumoNormalKwh;
    }
    public void setPorcentajeExtra(BigDecimal porcentajeExtra) {
        this.porcentajeExtra = porcentajeExtra == null ? BigDecimal.ZERO : porcentajeExtra;
    }

    @Override public String toString() { return codigo + " - " + nombre + " (area " + idArea + ")"; }
}
