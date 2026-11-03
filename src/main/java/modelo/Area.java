package modelo;

/** Area de la planta industrial. Corresponde a la tabla area. */
public class Area {
    private final int idArea;
    private String nombre;
    private String descripcion;

    /** Area aun sin registrar: el id lo asigna PostgreSQL. */
    public Area(String nombre, String descripcion) {
        this(0, nombre, descripcion);
    }

    public Area(int idArea, String nombre, String descripcion) {
        this.idArea = idArea;
        this.nombre = nombre;
        this.descripcion = descripcion;
    }
    public int getIdArea() { return idArea; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    @Override public String toString() {
        return idArea + " - " + nombre + (descripcion == null ? "" : " (" + descripcion + ")");
    }
}
