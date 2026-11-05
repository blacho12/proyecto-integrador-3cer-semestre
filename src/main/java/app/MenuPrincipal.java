package app;

import java.sql.SQLException;
import java.util.Scanner;

import estructuras.Accion;
import modelo.Equipo;
import modelo.OrdenMantenimiento;

/**
 * Menu principal. Al iniciar se conecta a PostgreSQL, carga los equipos al AVL,
 * las ordenes PENDIENTE a la cola de prioridad y deja la pila de acciones vacia.
 */
public class MenuPrincipal {

    public static void main(String[] args) {
        GestorOrdenes gestor;
        try {
            gestor = new GestorOrdenes();
            gestor.inicializar();
        } catch (SQLException e) {
            System.err.println("No se pudo iniciar el sistema: " + e.getMessage());
            System.err.println("Revise src/main/.env (o las variables de entorno) y la migracion "
                    + "src/main/resources/esquema_carbono_areas.sql.");
            return;
        }

        Scanner in = new Scanner(System.in);
        int opcion;
        do {
            System.out.println();
            System.out.println("=== GESTION DE ORDENES DE MANTENIMIENTO ===");
            System.out.println(" Equipos en el AVL: " + gestor.getEquipos().tamano()
                    + " | Ordenes pendientes en cola: " + gestor.getOrdenes().tamano()
                    + " | Acciones por deshacer: " + gestor.getHistorial().tamano());
            System.out.println(" 1. Ordenes pendientes (orden de atencion)");
            System.out.println(" 2. Crear orden");
            System.out.println(" 3. Modificar orden");
            System.out.println(" 4. Cancelar orden");
            System.out.println(" 5. Cerrar la orden mas prioritaria");
            System.out.println(" 6. Deshacer la ultima accion");
            System.out.println(" 7. Equipos (recorrido inorden del AVL)");
            System.out.println(" 8. Ordenes historicas de un equipo");
            System.out.println(" 0. Salir");
            System.out.print("Opcion: ");
            opcion = leerEntero(in);

            try {
                switch (opcion) {
                    case 1: listarCola(gestor); break;
                    case 2: crearOrden(gestor, in); break;
                    case 3: modificarOrden(gestor, in); break;
                    case 4: cancelarOrden(gestor, in); break;
                    case 5: cerrarOrden(gestor); break;
                    case 6: deshacer(gestor); break;
                    case 7: listarEquipos(gestor); break;
                    case 8: ordenesPorEquipo(gestor, in); break;
                    case 0: break;
                    default: System.out.println("Opcion no valida.");
                }
            } catch (SQLException e) {
                System.err.println("Error en PostgreSQL: " + e.getMessage());
            }
        } while (opcion != 0);

        gestor.cerrar();
        System.out.println("Sesion terminada. La pila de deshacer queda vacia (solo vive en memoria).");
    }

    // ------------------------------------------------------------------ opciones

    private static void listarCola(GestorOrdenes gestor) {
        OrdenMantenimiento[] pendientes = gestor.getOrdenes().recorridoPorPrioridad();
        if (pendientes.length == 0) { System.out.println("No hay ordenes pendientes."); return; }
        System.out.println("Orden de atencion:");
        for (OrdenMantenimiento o : pendientes) System.out.println("  " + o);
    }

    private static void crearOrden(GestorOrdenes gestor, Scanner in) {
        System.out.print("Codigo del equipo: ");
        String equipo = in.nextLine().trim();
        System.out.print("Tipo (PREVENTIVO/CORRECTIVO): ");
        String tipo = in.nextLine().trim().toUpperCase();
        System.out.print("Descripcion: ");
        String descripcion = in.nextLine();
        int impacto = leerRango(in, "Impacto de produccion (1-3): ");
        int riesgo = leerRango(in, "Riesgo de seguridad (1-3): ");
        System.out.print("Hay respaldo de produccion? (S/N): ");
        boolean respaldo = in.nextLine().trim().equalsIgnoreCase("S");

        OrdenMantenimiento o = gestor.crearOrden(equipo, tipo, descripcion, impacto, riesgo, respaldo);
        if (o != null) System.out.println("Orden creada: " + o);
        else System.out.println("La orden no se pudo crear.");
    }

    private static void modificarOrden(GestorOrdenes gestor, Scanner in) {
        System.out.print("Id de la orden a modificar: ");
        long id = leerEntero(in);
        System.out.print("Nuevo tipo (PREVENTIVO/CORRECTIVO): ");
        String tipo = in.nextLine().trim().toUpperCase();
        System.out.print("Nueva descripcion: ");
        String descripcion = in.nextLine();
        int impacto = leerRango(in, "Nuevo impacto de produccion (1-3): ");
        int riesgo = leerRango(in, "Nuevo riesgo de seguridad (1-3): ");
        System.out.print("Hay respaldo de produccion? (S/N): ");
        boolean respaldo = in.nextLine().trim().equalsIgnoreCase("S");
        System.out.println(gestor.modificarOrden(id, tipo, descripcion, impacto, riesgo, respaldo)
                ? "Orden modificada y repriorizada."
                : "La orden no se pudo modificar.");
    }

    private static void cancelarOrden(GestorOrdenes gestor, Scanner in) {
        System.out.print("Id de la orden a cancelar: ");
        System.out.println(gestor.cancelarOrden(leerEntero(in))
                ? "Orden cancelada (use deshacer para revertirla)."
                : "La orden no se pudo cancelar.");
    }

    private static void cerrarOrden(GestorOrdenes gestor) {
        OrdenMantenimiento o = gestor.atenderSiguiente();
        System.out.println(o != null ? "Orden cerrada: " + o : "No hay ordenes pendientes para cerrar.");
    }

    private static void deshacer(GestorOrdenes gestor) {
        Accion ultima = gestor.deshacer();
        System.out.println(ultima != null
                ? "Se revirtio la accion " + ultima.getTipo() + " de la orden OM-" + ultima.getIdOrden()
                : "No hay acciones por deshacer (o la revision fallo).");
    }

    private static void listarEquipos(GestorOrdenes gestor) {
        if (gestor.getEquipos().estaVacio()) { System.out.println("El AVL esta vacio."); return; }
        for (Equipo e : gestor.getEquipos().inOrden()) System.out.println("  " + e);
    }

    private static void ordenesPorEquipo(GestorOrdenes gestor, Scanner in) throws SQLException {
        System.out.print("Codigo del equipo: ");
        String codigo = in.nextLine().trim();
        for (OrdenMantenimiento o : gestor.consultarOrdenesPorEquipo(codigo)) {
            System.out.println("  OM-" + o.getId() + " #" + o.getConsecutivo()
                    + " [" + o.getCriticidad() + ", " + o.getTipo() + ", " + o.getEstado()
                    + ", creada " + o.getFechaCreacion()
                    + (o.getFechaCierre() != null ? ", cerrada " + o.getFechaCierre() : "") + "]");
        }
    }

    // ------------------------------------------------------------------ utilidades de consola

    private static int leerEntero(Scanner in) {
        while (true) {
            try { return Integer.parseInt(in.nextLine().trim()); }
            catch (NumberFormatException e) {
                System.out.print("Ingrese un numero valido: ");
            }
        }
    }

    private static int leerRango(Scanner in, String mensaje) {
        System.out.print(mensaje);
        return leerEntero(in);
    }
}
