package estructuras;

import modelo.Equipo;

/** Nodo del arbol AVL de equipos. */
public class NodoAVL {
    Equipo equipo;
    NodoAVL izquierdo, derecho;
    int altura = 1;

    NodoAVL(Equipo equipo) { this.equipo = equipo; }
}
