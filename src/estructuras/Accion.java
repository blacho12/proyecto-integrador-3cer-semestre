package estructuras;

import modelo.OrdenMantenimiento;

/** Accion registrada en el historial; guarda la "foto" previa de la orden para poder deshacerla. */
public class Accion {
    public enum Tipo { CREAR, MODIFICAR, CANCELAR, CERRAR }

    private final Tipo tipo;
    private final OrdenMantenimiento estadoPrevio; // null cuando la accion es CREAR

    public Accion(Tipo tipo, OrdenMantenimiento estadoPrevio) {
        this.tipo = tipo;
        this.estadoPrevio = estadoPrevio;
    }
    public Tipo getTipo() { return tipo; }
    public OrdenMantenimiento getEstadoPrevio() { return estadoPrevio; }
    public int getIdOrden() { return estadoPrevio != null ? estadoPrevio.getId() : -1; }
}
