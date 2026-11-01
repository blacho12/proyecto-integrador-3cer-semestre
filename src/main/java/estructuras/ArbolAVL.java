package estructuras;

import modelo.Equipo;

/** Arbol AVL de equipos ordenado por codigo. Busqueda, insercion y eliminacion en O(log n). */
public class ArbolAVL {
    private NodoAVL raiz;
    private int tamano = 0;

    public boolean estaVacio() { return raiz == null; }
    public int tamano() { return tamano; }
    public int altura() { return altura(raiz); }

    /** Inserta un equipo. Devuelve false si el codigo ya existe. */
    public boolean insertar(Equipo e) {
        if (e == null) throw new IllegalArgumentException("El equipo no puede ser nulo");
        if (buscar(e.getCodigo()) != null) return false;
        raiz = insertar(raiz, e);
        tamano++;
        return true;
    }

    /** Busca por codigo; devuelve null si no existe. Iterativo, O(log n). */
    public Equipo buscar(String codigo) {
        NodoAVL actual = raiz;
        while (actual != null) {
            int c = codigo.compareTo(actual.equipo.getCodigo());
            if (c == 0) return actual.equipo;
            actual = (c < 0) ? actual.izquierdo : actual.derecho;
        }
        return null;
    }

    /** Modifica nombre y ubicacion (el codigo es la clave y no cambia). */
    public boolean modificar(String codigo, String nombre, String ubicacion) {
        Equipo e = buscar(codigo);
        if (e == null) return false;
        e.setNombre(nombre);
        e.setUbicacion(ubicacion);
        return true;
    }

    /** Elimina por codigo. Devuelve false si no existe. */
    public boolean eliminar(String codigo) {
        if (buscar(codigo) == null) return false;
        raiz = eliminar(raiz, codigo);
        tamano--;
        return true;
    }

    /** Recorrido inorden: equipos ordenados por codigo. O(n). */
    public Equipo[] inOrden() {
        Equipo[] salida = new Equipo[tamano];
        llenar(raiz, salida, new int[]{0});
        return salida;
    }

    private void llenar(NodoAVL n, Equipo[] a, int[] pos) {
        if (n == null) return;
        llenar(n.izquierdo, a, pos);
        a[pos[0]++] = n.equipo;
        llenar(n.derecho, a, pos);
    }

    private NodoAVL insertar(NodoAVL n, Equipo e) {
        if (n == null) return new NodoAVL(e);
        if (e.getCodigo().compareTo(n.equipo.getCodigo()) < 0) n.izquierdo = insertar(n.izquierdo, e);
        else n.derecho = insertar(n.derecho, e);
        return balancear(n);
    }

    private NodoAVL eliminar(NodoAVL n, String codigo) {
        if (n == null) return null;
        int c = codigo.compareTo(n.equipo.getCodigo());
        if (c < 0) n.izquierdo = eliminar(n.izquierdo, codigo);
        else if (c > 0) n.derecho = eliminar(n.derecho, codigo);
        else {
            if (n.izquierdo == null) return n.derecho;     // 0 o 1 hijo
            if (n.derecho == null) return n.izquierdo;
            NodoAVL sucesor = n.derecho;                    // 2 hijos: sucesor inorden
            while (sucesor.izquierdo != null) sucesor = sucesor.izquierdo;
            n.equipo = sucesor.equipo;
            n.derecho = eliminar(n.derecho, sucesor.equipo.getCodigo());
        }
        return balancear(n);
    }

    private int altura(NodoAVL n) { return n == null ? 0 : n.altura; }
    private int factor(NodoAVL n) { return n == null ? 0 : altura(n.izquierdo) - altura(n.derecho); }
    private void actualizar(NodoAVL n) { n.altura = 1 + Math.max(altura(n.izquierdo), altura(n.derecho)); }

    private NodoAVL balancear(NodoAVL n) {
        actualizar(n);
        int f = factor(n);
        if (f > 1) {                                        // pesado a la izquierda
            if (factor(n.izquierdo) < 0) n.izquierdo = rotarIzquierda(n.izquierdo); // caso LR
            return rotarDerecha(n);
        }
        if (f < -1) {                                       // pesado a la derecha
            if (factor(n.derecho) > 0) n.derecho = rotarDerecha(n.derecho);         // caso RL
            return rotarIzquierda(n);
        }
        return n;
    }

    private NodoAVL rotarDerecha(NodoAVL y) {
        NodoAVL x = y.izquierdo;
        y.izquierdo = x.derecho;
        x.derecho = y;
        actualizar(y); actualizar(x);
        return x;
    }

    private NodoAVL rotarIzquierda(NodoAVL x) {
        NodoAVL y = x.derecho;
        x.derecho = y.izquierdo;
        y.izquierdo = x;
        actualizar(x); actualizar(y);
        return y;
    }
}
