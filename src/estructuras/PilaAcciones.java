package estructuras;

/** Pila (LIFO) de acciones con nodos enlazados. push, pop y peek en O(1). */
public class PilaAcciones {
    private NodoPila tope;
    private int tamano = 0;

    public boolean estaVacia() { return tope == null; }
    public int tamano() { return tamano; }

    /** Apila una accion. O(1). */
    public void apilar(Accion a) {
        if (a == null) throw new IllegalArgumentException("La accion no puede ser nula");
        tope = new NodoPila(a, tope);
        tamano++;
    }

    /** Desapila y devuelve la ultima accion; null si la pila esta vacia. O(1). */
    public Accion desapilar() {
        if (tope == null) return null;
        Accion a = tope.accion;
        tope = tope.siguiente;
        tamano--;
        return a;
    }

    /** Consulta la ultima accion sin retirarla. O(1). */
    public Accion verTope() { return tope == null ? null : tope.accion; }
}
