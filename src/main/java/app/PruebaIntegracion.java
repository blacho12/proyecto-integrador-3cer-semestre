package app;

import java.sql.SQLException;

import datos.ConexionBD;
import estructuras.Accion;
import estructuras.ArbolAVL;
import estructuras.ColaPrioridad;
import modelo.Area;
import modelo.Criticidad;
import modelo.Equipo;
import modelo.OrdenMantenimiento;
import modelo.OrdenMantenimiento.Estado;

/**
 * Prueba de integracion con PostgreSQL (variables DB_URL, DB_USER y DB_PASSWORD).
 * Cubre: conexion, CRUD de areas y equipos, clave foranea de area, creacion de ordenes
 * con id/consecutivo/fecha generados por la BD, rechazo de datos invalidos, carga
 * inicial del AVL y de la cola, orden por criticidad/consecutivo, cancelar, cerrar,
 * modificar, las cuatro reversiones de deshacer (respetando el LIFO) y el reinicio
 * con la pila vacia. Deja la base de datos como estaba al terminar.
 */
public class PruebaIntegracion {
    private static final String CODIGO_A = "EQ-TE-001";
    private static final String CODIGO_B = "EQ-TE-002";
    private static final String NOMBRE_AREA = "Area de pruebas de integracion";

    private static int aciertos = 0, fallas = 0;

    public static void main(String[] args) {
        System.out.println("=== PRUEBA DE INTEGRACION CON POSTGRESQL ===");
        System.out.println("--- 1. Conectando a PostgreSQL... ---");
        GestorOrdenes g;
        try {
            g = new GestorOrdenes();
        } catch (SQLException e) {
            fallas++;
            System.out.println("FALLO | La conexion: " + e.getMessage());
            System.out.println("Configure las variables DB_URL, DB_USER y DB_PASSWORD.");
            return;
        }
        System.out.println("OK    | La conexion se establecio.");

        try {
            System.out.println("\n--- Limpieza de datos de ejecuciones anteriores ---");
            limpiarDatosPrevios(g);

            System.out.println("\n--- 2-3. Areas ---");
            Area area = g.registrarArea(NOMBRE_AREA, "Temporal para la prueba de integracion");
            verificar("2. Se inserto un area con id generado", area != null && area.getIdArea() > 0);
            Area leida = g.getAreaDAO().consultarPorId(area.getIdArea());
            verificar("3. Se consulta el area por id", leida != null && NOMBRE_AREA.equals(leida.getNombre()));
            verificar("3. Se listan las areas", !g.consultarAreas().isEmpty());
            verificar("3. Se rechaza un nombre de area duplicado",
                    g.registrarArea(NOMBRE_AREA, "duplicada") == null);
            verificar("3. Se actualiza el area y se consulta de nuevo",
                    g.getAreaDAO().actualizar(area) && NOMBRE_AREA.equals(
                            g.getAreaDAO().consultarPorId(area.getIdArea()).getNombre()));

            System.out.println("\n--- 4. Equipos y clave foranea ---");
            verificar("4. Se insertaron dos equipos",
                    g.registrarEquipo(new Equipo(CODIGO_A, "Caldera de prueba", area.getIdArea())) != null
                 && g.registrarEquipo(new Equipo(CODIGO_B, "Banda de prueba", area.getIdArea())) != null);
            verificar("4. Se rechaza un equipo con area inexistente",
                    g.registrarEquipo(new Equipo("EQ-TE-009", "Area fantasma", 999999)) == null);

            System.out.println("\n--- 4b. Modificar y eliminar equipos ---");
            verificar("4b. Se corrige el nombre del equipo",
                    g.modificarEquipo(CODIGO_A, CODIGO_A, "Caldera corregida", area.getIdArea()));
            Equipo corregido = g.getEquipos().buscar(CODIGO_A);
            verificar("4b. El AVL refleja el nombre corregido",
                    corregido != null && "Caldera corregida".equals(corregido.getNombre()));
            Equipo enBd = g.getEquipoDAO().consultarPorCodigo(CODIGO_A);
            verificar("4b. PostgreSQL refleja el nombre corregido",
                    enBd != null && "Caldera corregida".equals(enBd.getNombre()));
            verificar("4b. Rechaza cambiar a un codigo duplicado",
                    !g.modificarEquipo(CODIGO_A, CODIGO_B, "Duplicado", area.getIdArea()));
            verificar("4b. Rechaza modificar con un area inexistente",
                    !g.modificarEquipo(CODIGO_A, CODIGO_A, "Caldera corregida", 999999));
            verificar("4b. Se registra un equipo temporal",
                    g.registrarEquipo(new Equipo("EQ-TE-003", "Equipo temporal", area.getIdArea())) != null);
            verificar("4b. Se corrige el codigo de un equipo sin historial",
                    g.modificarEquipo("EQ-TE-003", "EQ-TE-004", "Equipo corregido", area.getIdArea()));
            verificar("4b. El AVL cambia de clave",
                    g.getEquipos().buscar("EQ-TE-003") == null
                            && g.getEquipos().buscar("EQ-TE-004") != null);
            verificar("4b. PostgreSQL cambia de clave",
                    g.getEquipoDAO().consultarPorCodigo("EQ-TE-003") == null
                            && g.getEquipoDAO().consultarPorCodigo("EQ-TE-004") != null);
            verificar("4b. Se elimina un equipo sin historial", g.eliminarEquipo("EQ-TE-004"));
            verificar("4b. El equipo sale del AVL y de PostgreSQL",
                    g.getEquipos().buscar("EQ-TE-004") == null
                            && g.getEquipoDAO().consultarPorCodigo("EQ-TE-004") == null);

            System.out.println("\n--- 5-6. Creacion de ordenes (id y consecutivo de la BD) ---");
            OrdenMantenimiento alta  = g.crearOrden(CODIGO_A, "CORRECTIVO", "Fuga de vapor", 3, 3, false);  // ALTA
            OrdenMantenimiento baja  = g.crearOrden(CODIGO_B, "PREVENTIVO", "Lubricacion",  1, 1, true);     // BAJA
            OrdenMantenimiento media = g.crearOrden(CODIGO_B, "CORRECTIVO", "Ajuste",       2, 2, true);     // MEDIA
            verificar("5. Se crearon tres ordenes", alta != null && baja != null && media != null);
            if (alta != null) {
                verificar("6. La BD genero el id_orden", alta.getId() > 0);
                verificar("6. La BD genero el consecutivo", alta.getConsecutivo() > 0);
                verificar("6. La BD genero la fecha de creacion", alta.getFechaCreacion() != null);
                verificar("6. El consecutivo crece con la antiguedad",
                        media.getConsecutivo() > baja.getConsecutivo() && baja.getConsecutivo() > alta.getConsecutivo());
            }

            System.out.println("\n--- 7-8. Rechazo de datos invalidos ---");
            verificar("7. Rechaza impacto fuera de 1-3",
                    g.crearOrden(CODIGO_A, "CORRECTIVO", "x", 5, 1, true) == null);
            verificar("7. Rechaza riesgo fuera de 1-3",
                    g.crearOrden(CODIGO_A, "CORRECTIVO", "x", 1, 0, true) == null);
            verificar("8. Rechaza un tipo invalido",
                    g.crearOrden(CODIGO_A, "URGENTE", "x", 1, 1, true) == null);
            verificar("8. Rechaza una descripcion nula",
                    g.crearOrden(CODIGO_A, "CORRECTIVO", "  ", 1, 1, true) == null);
            verificar("8. Rechaza un equipo inexistente",
                    g.crearOrden("EQ-NADA", "CORRECTIVO", "x", 1, 1, true) == null);

            System.out.println("\n--- 9-11. Carga inicial y orden de atencion ---");
            GestorOrdenes g2 = new GestorOrdenes();  // instancia nueva = reinicio simulado
            g2.inicializar();
            ArbolAVL avlReiniciado = g2.getEquipos();
            ColaPrioridad colaReiniciada = g2.getOrdenes();
            verificar("9. El AVL se recargo desde la BD", avlReiniciado.buscar(CODIGO_A) != null);
            verificar("10. La cola se recargo con las pendientes", colaReiniciada.tamano() == 3);
            verificar("11. La primera es la mas critica",
                    colaReiniciada.verPrimera().getId() == alta.getId());
            verificar("11. El orden es criticidad y luego consecutivo",
                    enOrdenDeAtencion(colaReiniciada, alta, media, baja));
            g2.cerrar();

            System.out.println("\n--- 12. Cancelar ---");
            long idBaja = baja.getId();
            verificar("12. Se cancela una pendiente", g.cancelarOrden(idBaja));
            verificar("12. Sale de la cola", g.getOrdenes().buscarPorId(idBaja) == null);
            OrdenMantenimiento bdBaja = g.getOrdenDAO().consultarPorId(idBaja);
            verificar("12. En BD queda CANCELADA con fecha de cierre",
                    bdBaja.getEstado() == Estado.CANCELADA && bdBaja.getFechaCierre() != null);
            verificar("12. No se puede cancelar de nuevo", !g.cancelarOrden(idBaja));

            System.out.println("\n--- 13. Cerrar la mas prioritaria ---");
            verificar("13. Se cierra la mas prioritaria", g.atenderSiguiente().getId() == alta.getId());
            OrdenMantenimiento bdAlta = g.getOrdenDAO().consultarPorId(alta.getId());
            verificar("13. En BD queda CERRADA con fecha de cierre",
                    bdAlta.getEstado() == Estado.CERRADA && bdAlta.getFechaCierre() != null);

            System.out.println("\n--- 17 y 18. Deshacer CERRAR y CANCELAR (las ultimas acciones, LIFO) ---");
            Accion r1 = g.deshacer();   // revierte CERRAR de alta
            bdAlta = g.getOrdenDAO().consultarPorId(alta.getId());
            verificar("17. (CERRAR) restaura PENDIENTE sin fecha de cierre",
                    r1 != null && r1.getTipo() == Accion.Tipo.CERRAR
                            && bdAlta.getEstado() == Estado.PENDIENTE && bdAlta.getFechaCierre() == null);
            verificar("17. La orden vuelve a la cola", g.getOrdenes().buscarPorId(alta.getId()) != null);
            Accion r2 = g.deshacer();   // revierte CANCELAR de baja
            bdBaja = g.getOrdenDAO().consultarPorId(idBaja);
            verificar("18. (CANCELAR) restaura PENDIENTE sin fecha de cierre",
                    r2 != null && r2.getTipo() == Accion.Tipo.CANCELAR
                            && bdBaja.getEstado() == Estado.PENDIENTE && bdBaja.getFechaCierre() == null);
            verificar("18. La orden vuelve a la cola", g.getOrdenes().buscarPorId(idBaja) != null);

            System.out.println("\n--- 14. Modificar ---");
            OrdenMantenimiento movible = g.crearOrden(CODIGO_A, "CORRECTIVO", "Reparar", 2, 2, true); // MEDIA
            verificar("14. Se modifica una pendiente",
                    g.modificarOrden(movible.getId(), "PREVENTIVO", "Revision ampliada", 3, 3, false));
            OrdenMantenimiento bdMovible = g.getOrdenDAO().consultarPorId(movible.getId());
            verificar("14. La BD refleja la modificacion y la criticidad se recalcula",
                    "PREVENTIVO".equals(bdMovible.getTipo()) && bdMovible.getImpacto() == 3
                            && bdMovible.getCriticidad() == Criticidad.ALTA);
            verificar("14. No se puede modificar una orden inexistente",
                    !g.modificarOrden(999999, "PREVENTIVO", "x", 1, 1, true));

            System.out.println("\n--- 16. Deshacer MODIFICAR (la ultima accion del LIFO) ---");
            Accion r3 = g.deshacer();   // revierte MODIFICAR de movible
            bdMovible = g.getOrdenDAO().consultarPorId(movible.getId());
            verificar("16. (MODIFICAR) restaura los valores del snapshot",
                    r3 != null && r3.getTipo() == Accion.Tipo.MODIFICAR
                            && "CORRECTIVO".equals(bdMovible.getTipo()) && bdMovible.getImpacto() == 2
                            && bdMovible.getCriticidad() == Criticidad.MEDIA);

            System.out.println("\n--- 15. Deshacer CREAR ---");
            OrdenMantenimiento temporal = g.crearOrden(CODIGO_A, "CORRECTIVO", "Temporal", 1, 1, true);
            verificar("15. La accion quedo en la pila", g.getHistorial().tamano() >= 2);
            g.deshacer();               // revierte CREAR de temporal
            verificar("15. La BD ya no tiene la orden creada",
                    g.getOrdenDAO().consultarPorId(temporal.getId()) == null);
            verificar("15. La cola ya no tiene la orden creada",
                    g.getOrdenes().buscarPorId(temporal.getId()) == null);

            System.out.println("\n--- 15b. Deshacer CREAR de la orden modificada (siguiente en LIFO) ---");
            g.deshacer();               // revierte CREAR de movible: se elimina por completo
            verificar("15b. La creacion revertida elimino la orden de la BD",
                    g.getOrdenDAO().consultarPorId(movible.getId()) == null);
            verificar("15b. Y ya no esta en la cola", g.getOrdenes().buscarPorId(movible.getId()) == null);

            System.out.println("\n--- 19-20. Reinicio con recuperacion y pila vacia ---");
            GestorOrdenes g3 = new GestorOrdenes();
            g3.inicializar();
            verificar("20. Tras reiniciar, la pila de deshacer esta vacia", g3.getHistorial().estaVacia());
            verificar("19. Tras reiniciar, las pendientes siguen en la cola", g3.getOrdenes().tamano() == 3);
            verificar("19. Tras reiniciar, el AVL conserva los equipos", g3.getEquipos().tamano() >= 2);
            g3.cerrar();

            System.out.println("\n--- 21. Proteccion del historial de equipos ---");
            verificar("21. No se elimina un equipo con ordenes", !g.eliminarEquipo(CODIGO_A));
            verificar("21. El equipo permanece en el AVL", g.getEquipos().buscar(CODIGO_A) != null);
            verificar("21. No se cambia el codigo de un equipo con historial",
                    !g.modificarEquipo(CODIGO_A, "EQ-TE-005", "Caldera corregida", area.getIdArea()));
            verificar("21. El mensaje explica que el codigo no se puede cambiar",
                    g.getUltimoError() != null
                            && g.getUltimoError().toLowerCase().contains("codigo"));
        } catch (SQLException e) {
            fallas++;
            System.out.println("FALLO | Inesperado en base de datos: " + ConexionBD.mensajeAmigable(e));
        } finally {
            System.out.println("\n--- Limpieza final de los datos de prueba ---");
            limpiarDatosPrevios(g);
            g.cerrar();
        }

        System.out.println("\n=== RESUMEN: " + aciertos + " OK, " + fallas + " FALLAS ===");
        if (fallas > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ utilidades

    /** True si el recorrido de la cola visita las ordenes en el orden dado. */
    private static boolean enOrdenDeAtencion(ColaPrioridad cola, OrdenMantenimiento... esperadas) {
        OrdenMantenimiento[] actual = cola.recorridoPorPrioridad();
        if (actual.length != esperadas.length) return false;
        for (int i = 0; i < esperadas.length; i++) {
            if (actual[i].getId() != esperadas[i].getId()) return false;
        }
        return true;
    }

    /**
     * Deja la zona de prueba limpia: elimina las ordenes de esos equipos, luego equipos y area.
     * Cada error hace rollback: una transaccion abortada no debe afectar a las sentencias siguientes.
     */
    private static void limpiarDatosPrevios(GestorOrdenes g) {
        for (String codigo : new String[] { CODIGO_A, CODIGO_B, "EQ-TE-003", "EQ-TE-004",
                "EQ-TE-009", "EQ-DBG" }) {
            try {
                for (OrdenMantenimiento o : g.getOrdenDAO().consultarPorEquipo(codigo)) {
                    if (g.getOrdenDAO().eliminar(o.getId())) commitPrueba(g);
                }
                if (g.getEquipoDAO().eliminar(codigo)) commitPrueba(g);
            } catch (SQLException e) { rollbackPrueba(g); }
        }
        try {
            for (Area a : g.consultarAreas()) {
                if (NOMBRE_AREA.equals(a.getNombre()) || "Area debug".equals(a.getNombre())) {
                    try {
                        if (g.getAreaDAO().eliminar(a.getIdArea())) commitPrueba(g);
                    } catch (SQLException e) { rollbackPrueba(g); }   // por elemento: una falla no aborta a las demas
                }
            }
        } catch (SQLException e) { rollbackPrueba(g); }
    }

    private static void commitPrueba(GestorOrdenes g) {
        try { g.getConexionBD().commit(); } catch (SQLException e) { /* sin conexion */ }
    }

    private static void rollbackPrueba(GestorOrdenes g) {
        try { g.getConexionBD().rollback(); } catch (SQLException e) { /* no hay conexion */ }
    }

    private static void verificar(String nombre, boolean condicion) {
        if (condicion) {
            aciertos++;
            System.out.println("OK    | " + nombre);
        } else {
            fallas++;
            System.out.println("FALLO | " + nombre);
        }
    }
}
