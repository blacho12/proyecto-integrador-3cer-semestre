package estructuras;

/** Nodo de la pila de acciones (lista simplemente enlazada). */
public class NodoPila {
    Accion accion;
    NodoPila siguiente;

    NodoPila(Accion accion, NodoPila siguiente) { this.accion = accion; this.siguiente = siguiente; }
}
