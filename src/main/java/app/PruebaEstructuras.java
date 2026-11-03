package app;

import java.sql.SQLException;
import java.util.List;

import estructuras.Accion;
import estructuras.ArbolAVL;
import estructuras.ColaPrioridad;
import estructuras.PilaAcciones;
import modelo.Area;
import modelo.Equipo;
import modelo.OrdenMantenimiento;

/** Pruebas manuales por consola: pruebas 1 a 3 son de memoria; la 4 requiere PostgreSQL. */
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
        System.out.println("Eliminar orden inexistente (99) -> " + cola.eliminarPorId(99L));
        System.out.println("Cancelar OM-3 -> " + cola.eliminarPorId(3L) + " | Nueva primera: " + cola.verPrimera());
        while (!cola.estaVacia()) cola.desencolar();
        System.out.println("Cola vaciada. Primera -> " + cola.verPrimera());

        System.out.println("\n=== PRUEBA 2: Arbol AVL de equipos ===");
        ArbolAVL avl = new ArbolAVL();
        System.out.println("Buscar con arbol vacio -> " + avl.buscar("EQ-001"));
        for (int i = 1; i <= 7; i++) avl.insertar(new Equipo("EQ-00" + i, "Equipo " + i, i % 3 + 1));
        System.out.println("Insertados 7 codigos consecutivos. Tamano: " + avl.tamano() + " | Altura: " + avl.altura()
                + " (un arbol sin balancear tendria altura 7)");
        System.out.println("Insertar codigo duplicado EQ-004 -> " + avl.insertar(new Equipo("EQ-004", "X", 1)));
        System.out.println("Buscar EQ-005 -> " + avl.buscar("EQ-005"));
        System.out.println("Buscar EQ-999 (inexistente) -> " + avl.buscar("EQ-999"));
        System.out.println("Modificar EQ-005 -> " + avl.modificar("EQ-005", "Compresor nuevo", 9)
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

        prueba4ConBaseDeDatos();
    }

    /** Prueba 4: usa PostgreSQL (variables DB_URL, DB_USER, DB_PASSWORD). Limpia sus datos al final. */
    private static void prueba4ConBaseDeDatos() {
        System.out.println("\n=== PRUEBA 4: Gestor integrado y deshacer (requiere base de datos) ===");
        GestorOrdenes g;
        try {
            g = new GestorOrdenes();
        } catch (SQLException e) {
            System.out.println("Sin conexion a PostgreSQL, se omite la prueba 4: " + e.getMessage());
            return;
        }
        try {
            String nombreArea = "Area de pruebas de estructuras";
            Area area = buscarAreaPorNombre(g, nombreArea);
            if (area == null) {
                area = g.registrarArea(nombreArea, "Creada por PruebaEstructuras");
                if (area == null) { System.out.println("No se pudo crear el area de prueba."); return; }
            }

            limpiarEquiposDePrueba(g);
            System.out.println("Deshacer sin historial -> " + g.deshacer());
            System.out.println("Registrar equipo EQ-TE-001 -> " + (g.registrarEquipo(new Equipo("EQ-TE-001", "Caldera de prueba", area.getIdArea())) != null));
            System.out.println("Registrar equipo duplicado EQ-TE-001 -> " + (g.registrarEquipo(new Equipo("EQ-TE-001", "otro", area.getIdArea())) != null));
            g.registrarEquipo(new Equipo("EQ-TE-002", "Banda de prueba", area.getIdArea()));

            System.out.println("Crear orden con equipo inexistente -> " + g.crearOrden("EQ-999", "CORRECTIVO", "x", 1, 1, true));
            OrdenMantenimiento a = g.crearOrden("EQ-TE-002", "PREVENTIVO", "Ajuste de banda", 1, 1, true);
            OrdenMantenimiento b = g.crearOrden("EQ-TE-001", "CORRECTIVO", "Fuga de vapor", 3, 3, false);
            if (a == null || b == null) { System.out.println("No se pudieron crear las ordenes de prueba."); return; }
            System.out.println("Creadas: " + a + " y " + b + " | Primera: " + g.getOrdenes().verPrimera());
            long idB = b.getId();
            g.cancelarOrden(idB);
            System.out.println("Cancelada OM-" + idB + " -> Primera: " + g.getOrdenes().verPrimera());
            g.deshacer();
            System.out.println("Deshacer cancelacion -> Primera: " + g.getOrdenes().verPrimera());
            g.deshacer();
            System.out.println("Deshacer creacion de OM-" + idB + " -> Primera: " + g.getOrdenes().verPrimera()
                    + " | Tamano cola: " + g.getOrdenes().tamano());
        } catch (SQLException e) {
            System.out.println("Error de base de datos en la prueba 4: " + e.getMessage());
        } finally {
            limpiarEquiposDePrueba(g);
            g.cerrar();
        }
    }

    private static Area buscarAreaPorNombre(GestorOrdenes g, String nombre) throws SQLException {
        List<Area> areas = g.consultarAreas();
        for (Area a : areas) if (a.getNombre().equals(nombre)) return a;
        return null;
    }

    /** Elimina los equipos de prueba y sus ordenes asociadas, dejando la BD como estaba. */
    private static void limpiarEquiposDePrueba(GestorOrdenes g) {
        for (String codigo : new String[] { "EQ-TE-001", "EQ-TE-002" }) {
            try {
                for (OrdenMantenimiento o : g.getOrdenDAO().consultarPorEquipo(codigo)) {
                    if (g.getOrdenDAO().eliminar(o.getId())) g.getConexionBD().commit();
                }
                if (g.getEquipoDAO().eliminar(codigo)) g.getConexionBD().commit();
                g.getEquipos().eliminar(codigo);        // por si acaso quedo en memoria
            } catch (SQLException e) {
                try { g.getConexionBD().rollback(); } catch (SQLException r) { /* sin conexion */ }
            }
        }
    }
}
