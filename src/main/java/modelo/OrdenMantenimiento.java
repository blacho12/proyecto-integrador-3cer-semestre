package modelo;

import java.time.LocalDateTime;

/**
 * Orden de mantenimiento. Corresponde a la tabla orden_mantenimiento.
 * El consecutivo hace de marca de antiguedad (menor = mas antigua) y lo genera PostgreSQL.
 */
public class OrdenMantenimiento {
    public enum Estado { PENDIENTE, CERRADA, CANCELADA }

    private final long id;
    private final long consecutivo;
    private final String codigoEquipo;
    private String tipo;                 // PREVENTIVO o CORRECTIVO
    private String descripcion;
    private int impacto;                 // 1..3
    private int riesgoSeguridad;         // 1..3
    private boolean hayRespaldo;
    private Estado estado;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaCierre;

    /** Orden aun sin registrar: id, consecutivo y fecha_de_creacion los asigna PostgreSQL. */
    public OrdenMantenimiento(String codigoEquipo, String tipo, String descripcion,
                              int impacto, int riesgoSeguridad, boolean hayRespaldo) {
        this.id = 0;
        this.consecutivo = 0;
        this.codigoEquipo = codigoEquipo;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.impacto = impacto;
        this.riesgoSeguridad = riesgoSeguridad;
        this.hayRespaldo = hayRespaldo;
        this.estado = Estado.PENDIENTE;
        this.fechaCreacion = null;
        this.fechaCierre = null;
    }

    /** Orden leida completa desde la base de datos (o snapshot con fechas y estado incluidos). */
    public OrdenMantenimiento(long id, long consecutivo, String codigoEquipo, String tipo,
                              String descripcion, int impacto, int riesgoSeguridad, boolean hayRespaldo,
                              Estado estado, LocalDateTime fechaCreacion, LocalDateTime fechaCierre) {
        this.id = id;
        this.consecutivo = consecutivo;
        this.codigoEquipo = codigoEquipo;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.impacto = impacto;
        this.riesgoSeguridad = riesgoSeguridad;
        this.hayRespaldo = hayRespaldo;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;
        this.fechaCierre = fechaCierre;
    }

    /** Constructor de pruebas en memoria: orden pendiente con fecha de creacion actual. */
    public OrdenMantenimiento(long id, long consecutivo, String codigoEquipo, String tipo,
                              String descripcion, int impacto, int riesgoSeguridad, boolean hayRespaldo) {
        this(id, consecutivo, codigoEquipo, tipo, descripcion, impacto, riesgoSeguridad, hayRespaldo,
                Estado.PENDIENTE, LocalDateTime.now(), null);
    }

    /** Copia profunda: se usa como "foto" previa para poder deshacer una accion. */
    public OrdenMantenimiento copia() {
        return new OrdenMantenimiento(id, consecutivo, codigoEquipo, tipo, descripcion,
                impacto, riesgoSeguridad, hayRespaldo, estado, fechaCreacion, fechaCierre);
    }

    public Criticidad getCriticidad() { return Criticidad.calcular(impacto, riesgoSeguridad, hayRespaldo); }

    /** Orden de atencion: true si esta orden debe atenderse antes que la otra. */
    public boolean tienePrioridadSobre(OrdenMantenimiento o) {
        int a = getCriticidad().getPeso(), b = o.getCriticidad().getPeso();
        if (a != b) return a > b;           // mayor criticidad primero
        return consecutivo < o.consecutivo; // empate: la mas antigua primero
    }

    public long getId() { return id; }
    public long getConsecutivo() { return consecutivo; }
    public String getCodigoEquipo() { return codigoEquipo; }
    public String getTipo() { return tipo; }
    public String getDescripcion() { return descripcion; }
    public int getImpacto() { return impacto; }
    public int getRiesgoSeguridad() { return riesgoSeguridad; }
    public boolean isHayRespaldo() { return hayRespaldo; }
    public Estado getEstado() { return estado; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }

    public void setEstado(Estado e) { this.estado = e; }
    public void setFechaCierre(LocalDateTime f) { this.fechaCierre = f; }
    public void setTipo(String t) { this.tipo = t; }
    public void setDescripcion(String d) { this.descripcion = d; }
    public void setCriterios(int impacto, int riesgoSeguridad, boolean hayRespaldo) {
        this.impacto = impacto;
        this.riesgoSeguridad = riesgoSeguridad;
        this.hayRespaldo = hayRespaldo;
    }

    @Override public String toString() {
        return "OM-" + id + (consecutivo > 0 ? "#" + consecutivo : "")
                + " [" + getCriticidad() + ", equipo " + codigoEquipo + ", " + tipo + ", " + estado + "]";
    }
}
