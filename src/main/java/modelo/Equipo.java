package modelo;

/** Equipo de la planta industrial (clave de busqueda: codigo). Corresponde a la tabla equipo. */
public class Equipo {
    private final String codigo;
    private String nombre;
    private int idArea;

    public Equipo(String codigo, String nombre, int idArea) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.idArea = idArea;
    }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public int getIdArea() { return idArea; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setIdArea(int idArea) { this.idArea = idArea; }

    @Override public String toString() { return codigo + " - " + nombre + " (area " + idArea + ")"; }
}
