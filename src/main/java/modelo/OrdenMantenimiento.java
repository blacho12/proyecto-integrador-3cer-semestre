package modelo;

/** Orden de mantenimiento. El consecutivo hace de marca de antiguedad (menor = mas antigua). */
public class OrdenMantenimiento {
    public enum Estado { PENDIENTE, CERRADA, CANCELADA }

    private final int id;
    private final long consecutivo;
    private final String codigoEquipo;
    private String tipo;                 // PREVENTIVO o CORRECTIVO
    private String descripcion;
    private int impacto;                 // 1..3
    private int riesgoSeguridad;         // 1..3
    private boolean hayRespaldo;
    private Estado estado = Estado.PENDIENTE;

    public OrdenMantenimiento(int id, long consecutivo, String codigoEquipo, String tipo,
                              String descripcion, int impacto, int riesgoSeguridad, boolean hayRespaldo) {
        this.id = id; this.consecutivo = consecutivo; this.codigoEquipo = codigoEquipo;
        this.tipo = tipo; this.descripcion = descripcion;
        this.impacto = impacto; this.riesgoSeguridad = riesgoSeguridad; this.hayRespaldo = hayRespaldo;
    }

    /** Copia profunda: se usa como "foto" previa para poder deshacer una accion. */
    public OrdenMantenimiento copia() {
        OrdenMantenimiento c = new OrdenMantenimiento(id, consecutivo, codigoEquipo, tipo,
                descripcion, impacto, riesgoSeguridad, hayRespaldo);
        c.estado = estado;
        return c;
    }

    public Criticidad getCriticidad() { return Criticidad.calcular(impacto, riesgoSeguridad, hayRespaldo); }

    /** Orden de atencion: true si esta orden debe atenderse antes que la otra. */
    public boolean tienePrioridadSobre(OrdenMantenimiento o) {
        int a = getCriticidad().getPeso(), b = o.getCriticidad().getPeso();
        if (a != b) return a > b;           // mayor criticidad primero
        return consecutivo < o.consecutivo; // empate: la mas antigua primero
    }

    public int getId() { return id; }
    public long getConsecutivo() { return consecutivo; }
    public String getCodigoEquipo() { return codigoEquipo; }
    public String getTipo() { return tipo; }
    public String getDescripcion() { return descripcion; }
    public int getImpacto() { return impacto; }
    public int getRiesgoSeguridad() { return riesgoSeguridad; }
    public boolean isHayRespaldo() { return hayRespaldo; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado e) { this.estado = e; }
    public void setDescripcion(String d) { this.descripcion = d; }
    public void setCriterios(int impacto, int riesgoSeguridad, boolean hayRespaldo) {
        this.impacto = impacto; this.riesgoSeguridad = riesgoSeguridad; this.hayRespaldo = hayRespaldo;
    }

    @Override public String toString() {
        return "OM-" + id + " [" + getCriticidad() + ", equipo " + codigoEquipo + ", " + tipo + ", " + estado + "]";
    }
}
