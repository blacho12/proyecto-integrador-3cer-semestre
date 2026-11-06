package modelo;

import java.math.BigDecimal;

/** Equipo de la planta industrial (clave de busqueda: codigo). Corresponde a la tabla equipo. */
public class Equipo {
    private long id;
    private final String codigo;
    private String nombre;
    private int idArea;
    private BigDecimal consumoNormalKwh;
    private BigDecimal porcentajeExtra;
    private boolean activo;

    public Equipo(String codigo, String nombre, int idArea) {
        this(0, codigo, nombre, idArea, BigDecimal.ZERO, BigDecimal.ZERO, true);
    }

    public Equipo(String codigo, String nombre, int idArea, BigDecimal consumoNormalKwh,
                  BigDecimal porcentajeExtra) {
        this(0, codigo, nombre, idArea, consumoNormalKwh, porcentajeExtra, true);
    }

    public Equipo(long id, String codigo, String nombre, int idArea, BigDecimal consumoNormalKwh,
                  BigDecimal porcentajeExtra, boolean activo) {
        this.id = id;
        this.codigo = codigo;
        this.nombre = nombre;
        this.idArea = idArea;
        this.consumoNormalKwh = consumoNormalKwh;
        this.porcentajeExtra = porcentajeExtra;
        this.activo = activo;
    }
    public long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public int getIdArea() { return idArea; }
    public BigDecimal getConsumoNormalKwh() { return consumoNormalKwh; }
    public BigDecimal getPorcentajeExtra() { return porcentajeExtra; }
    public boolean isActivo() { return activo; }
    public void setId(long id) { this.id = id; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setIdArea(int idArea) { this.idArea = idArea; }
    public void setConsumoNormalKwh(BigDecimal consumoNormalKwh) { this.consumoNormalKwh = consumoNormalKwh; }
    public void setPorcentajeExtra(BigDecimal porcentajeExtra) { this.porcentajeExtra = porcentajeExtra; }
    public void setActivo(boolean activo) { this.activo = activo; }

    @Override public String toString() { return codigo + " - " + nombre + " (area " + idArea + ")"; }
}
