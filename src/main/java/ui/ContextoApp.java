package ui;

/**
 * Servicios de la ventana principal que necesitan los paneles.
 * Evita que los paneles conozcan detalles de la ventana o del menu.
 */
public interface ContextoApp {

    enum Nivel { INFO, EXITO, ADVERTENCIA, ERROR }

    /** Muestra un mensaje breve en la barra de estado con el color del nivel. */
    void mostrarMensaje(String mensaje, Nivel nivel);

    /** Refresca los contadores de la cabecera (cola, AVL, pila y estado de conexion). */
    void actualizarBarraSuperior();

    /** Cambia la vista activa del menu lateral. */
    void abrirVista(String clave);
}
