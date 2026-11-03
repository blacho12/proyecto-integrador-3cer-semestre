package estructuras;

import modelo.OrdenMantenimiento;

/** Accion registrada en el historial; guarda la "foto" previa de la orden para poder deshacerla. */
public class Accion {
    public enum Tipo { CREAR, MODIFICAR, CANCELAR, CERRAR }

    /**
     * Snapshot completo del estado previo de la orden.
     * Para CREAR es una copia de la orden recien creada (todavia pendiente);
     * para MODIFICAR, CANCELAR y CERRAR es una copia completa de los valores anteriores.
     */
    private final OrdenMantenimiento estadoPrevio;
    private final Tipo tipo;

    public Accion(Tipo tipo, OrdenMantenimiento estadoPrevio) {
        this.tipo = tipo;
        this.estadoPrevio = estadoPrevio;
    }
    public Tipo getTipo() { return tipo; }
    public OrdenMantenimiento getEstadoPrevio() { return estadoPrevio; }

    /** Identificador de la orden afectada; -1 si la accion no tiene snapshot. */
    public long getIdOrden() { return estadoPrevio != null ? estadoPrevio.getId() : -1L; }
}
