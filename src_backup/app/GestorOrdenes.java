package app;

import estructuras.Accion;
import estructuras.ArbolAVL;
import estructuras.ColaPrioridad;
import estructuras.PilaAcciones;
import modelo.Equipo;
import modelo.OrdenMantenimiento;

/** Une las tres estructuras: AVL de equipos, cola de prioridad de ordenes y pila de acciones. */
public class GestorOrdenes {
    private final ArbolAVL equipos = new ArbolAVL();
    private final ColaPrioridad ordenes = new ColaPrioridad();
    private final PilaAcciones historial = new PilaAcciones();
    private int siguienteId = 1;
    private long siguienteConsecutivo = 1;

    public ArbolAVL getEquipos() { return equipos; }
    public ColaPrioridad getOrdenes() { return ordenes; }
    public PilaAcciones getHistorial() { return historial; }

    /** Crea una orden si el equipo existe; registra la accion en la pila. Devuelve null si el equipo no existe. */
    public OrdenMantenimiento crearOrden(String codigoEquipo, String tipo, String descripcion,
                                         int impacto, int riesgoSeguridad, boolean hayRespaldo) {
        if (equipos.buscar(codigoEquipo) == null) return null;
        OrdenMantenimiento o = new OrdenMantenimiento(siguienteId++, siguienteConsecutivo++,
                codigoEquipo, tipo, descripcion, impacto, riesgoSeguridad, hayRespaldo);
        ordenes.encolar(o);
        historial.apilar(new Accion(Accion.Tipo.CREAR, o.copia()));
        return o;
    }

    /** Cancela una orden pendiente (la saca de la cola) y guarda su estado previo para deshacer. */
    public boolean cancelarOrden(int id) {
        OrdenMantenimiento o = ordenes.buscarPorId(id);
        if (o == null) return false;
        Accion a = new Accion(Accion.Tipo.CANCELAR, o.copia());
        ordenes.eliminarPorId(id);
        historial.apilar(a);
        return true;
    }

    /** Atiende (cierra) la orden mas prioritaria. */
    public OrdenMantenimiento atenderSiguiente() {
        OrdenMantenimiento o = ordenes.verPrimera();
        if (o == null) return null;
        Accion a = new Accion(Accion.Tipo.CERRAR, o.copia());
        ordenes.desencolar();
        o.setEstado(OrdenMantenimiento.Estado.CERRADA);
        historial.apilar(a);
        return o;
    }

    /** Deshace la ultima accion. Devuelve la accion revertida o null si no hay historial. */
    public Accion deshacer() {
        Accion a = historial.desapilar();
        if (a == null) return null;
        switch (a.getTipo()) {
            case CREAR:                       // deshacer una creacion = quitar la orden
                ordenes.eliminarPorId(a.getIdOrden());
                break;
            case CANCELAR:                    // deshacer una cancelacion o un cierre = reinsertar la orden
            case CERRAR:
                ordenes.encolar(a.getEstadoPrevio().copia());
                break;
            default:                          // MODIFICAR se implementa en la siguiente entrega
                break;
        }
        return a;
    }
}
