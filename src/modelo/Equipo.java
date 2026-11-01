package modelo;

/** Equipo de la planta industrial (clave de busqueda: codigo). */
public class Equipo {
    private final String codigo;
    private String nombre;
    private String ubicacion;

    public Equipo(String codigo, String nombre, String ubicacion) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.ubicacion = ubicacion;
    }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getUbicacion() { return ubicacion; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }

    @Override public String toString() { return codigo + " - " + nombre + " (" + ubicacion + ")"; }
}
