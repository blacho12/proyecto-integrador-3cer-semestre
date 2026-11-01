package app;

import estructuras.Accion;
import estructuras.ArbolAVL;
import estructuras.ColaPrioridad;
import estructuras.PilaAcciones;
import modelo.Equipo;
import modelo.OrdenMantenimiento;

/** Pruebas manuales por consola de las primeras operaciones (casos normales y casos borde). */
public class PruebaEstructuras {
    public static void main(String[] args) {
        System.out.println("=== PRUEBA 1: Cola de prioridad ===");
        ColaPrioridad cola = new ColaPrioridad();
        System.out.println("Desencolar con cola vacia -> " + cola.desencolar());
        cola.encolar(new OrdenMantenimiento(1, 1, "EQ-003", "CORRECTIVO", "Fuga en bomba",      2, 2, true));  // MEDIA
        cola.encolar(new OrdenMantenimiento(2, 2, "EQ-001", "PREVENTIVO", "Lubricacion banda",  1, 1, true));  // BAJA
        cola.encolar(new OrdenMantenimiento(3, 3, "EQ-002", "CORRECTIVO", "Falla en caldera",   3, 3, false)); // ALTA
        cola.encolar(new OrdenMantenimiento(4, 4, "EQ-005", "CORRECTIVO", "Sensor danado",      3, 2, false)); // ALTA
        cola.encolar(new OrdenMantenimiento(5, 5, "EQ-004", "PREVENTIVO", "Revision compresor", 2, 2, true));  // MEDIA
        System.out.println("Tamano: " + cola.tamano() + " | Primera: " + cola.verPrimera());
        System.out.println("Orden de atencion:");
        for (OrdenMantenimiento o : cola.recorridoPorPrioridad()) System.out.println("  " + o);
        System.out.println("Eliminar orden inexistente (99) -> " + cola.eliminarPorId(99));
        System.out.println("Cancelar OM-3 -> " + cola.eliminarPorId(3) + " | Nueva primera: " + cola.verPrimera());
        while (!cola.estaVacia()) cola.desencolar();
        System.out.println("Cola vaciada. Primera -> " + cola.verPrimera());

        System.out.println("\n=== PRUEBA 2: Arbol AVL de equipos ===");
        ArbolAVL avl = new ArbolAVL();
        System.out.println("Buscar con arbol vacio -> " + avl.buscar("EQ-001"));
        for (int i = 1; i <= 7; i++) avl.insertar(new Equipo("EQ-00" + i, "Equipo " + i, "Area " + (i % 3 + 1)));
        System.out.println("Insertados 7 codigos consecutivos. Tamano: " + avl.tamano() + " | Altura: " + avl.altura()
                + " (un arbol sin balancear tendria altura 7)");
        System.out.println("Insertar codigo duplicado EQ-004 -> " + avl.insertar(new Equipo("EQ-004", "X", "Y")));
        System.out.println("Buscar EQ-005 -> " + avl.buscar("EQ-005"));
        System.out.println("Buscar EQ-999 (inexistente) -> " + avl.buscar("EQ-999"));
        System.out.println("Modificar EQ-005 -> " + avl.modificar("EQ-005", "Compresor nuevo", "Area 9")
                + " | " + avl.buscar("EQ-005"));
        System.out.println("Eliminar EQ-004 (raiz, dos hijos) -> " + avl.eliminar("EQ-004"));
        System.out.println("Eliminar EQ-999 (inexistente) -> " + avl.eliminar("EQ-999"));
        StringBuilder sb = new StringBuilder("Inorden: ");
        for (Equipo e : avl.inOrden()) sb.append(e.getCodigo()).append(' ');
        System.out.println(sb + "| Altura: " + avl.altura());

        System.out.println("\n=== PRUEBA 3: Pila de acciones ===");
        PilaAcciones pila = new PilaAcciones();
        System.out.println("Desapilar con pila vacia -> " + pila.desapilar());
        pila.apilar(new Accion(Accion.Tipo.CREAR, null));
        System.out.println("Un solo elemento. Tope: " + pila.verTope().getTipo() + " | Tamano: " + pila.tamano());
        pila.apilar(new Accion(Accion.Tipo.CANCELAR, null));
        pila.apilar(new Accion(Accion.Tipo.CERRAR, null));
        System.out.print("Desapilando (LIFO): ");
        while (!pila.estaVacia()) System.out.print(pila.desapilar().getTipo() + " ");
        System.out.println();

        System.out.println("\n=== PRUEBA 4: Gestor integrado y deshacer ===");
        GestorOrdenes g = new GestorOrdenes();
        System.out.println("Deshacer sin historial -> " + g.deshacer());
        g.getEquipos().insertar(new Equipo("EQ-001", "Caldera principal", "Planta 1"));
        g.getEquipos().insertar(new Equipo("EQ-002", "Banda transportadora", "Planta 2"));
        System.out.println("Crear orden con equipo inexistente -> " + g.crearOrden("EQ-777", "CORRECTIVO", "x", 1, 1, true));
        OrdenMantenimiento a = g.crearOrden("EQ-002", "PREVENTIVO", "Ajuste de banda", 1, 1, true);
        OrdenMantenimiento b = g.crearOrden("EQ-001", "CORRECTIVO", "Fuga de vapor", 3, 3, false);
        System.out.println("Creadas: " + a + " y " + b + " | Primera: " + g.getOrdenes().verPrimera());
        g.cancelarOrden(b.getId());
        System.out.println("Cancelada " + b + " -> Primera: " + g.getOrdenes().verPrimera());
        g.deshacer();
        System.out.println("Deshacer cancelacion -> Primera: " + g.getOrdenes().verPrimera());
        g.deshacer();
        System.out.println("Deshacer creacion de OM-" + b.getId() + " -> Primera: " + g.getOrdenes().verPrimera()
                + " | Tamano cola: " + g.getOrdenes().tamano());
    }
}
