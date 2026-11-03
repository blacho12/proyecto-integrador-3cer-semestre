package ui.paneles;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

import javax.swing.JPanel;

import app.GestorOrdenes;
import ui.ContextoApp;
import ui.PanelVista;
import ui.componentes.TareaBD;
import ui.componentes.Tema;

/**
 * Base de los paneles: concentra el acceso asincrono a la capa de aplicacion,
 * el bloqueo de operaciones simultaneas y el aviso de resultados.
 */
public abstract class PanelBase extends JPanel implements PanelVista {

    protected final GestorOrdenes gestor;
    protected final ContextoApp contexto;

    private boolean ocupado;
    private boolean recargarDespues;

    protected PanelBase(GestorOrdenes gestor, ContextoApp contexto) {
        this.gestor = gestor;
        this.contexto = contexto;
        setOpaque(true);
        setBackground(Tema.FONDO);
    }

    protected boolean estaOcupado() {
        return ocupado;
    }

    /** Carga datos sin bloquear el EDT y avisa al panel cuando el resultado esta listo. */
    protected <T> void cargar(Callable<T> tarea, Consumer<T> alExito, String descripcionError) {
        if (ocupado) return;
        ocupado = true;
        alCambiarOcupado(true);
        TareaBD.ejecutar(tarea, alExito,
                e -> contexto.mostrarMensaje(descripcionError + ": " + TareaBD.mensajeError(e),
                        ContextoApp.Nivel.ERROR),
                () -> {
                    ocupado = false;
                    alCambiarOcupado(false);
                    contexto.actualizarBarraSuperior();
                });
    }

    /**
     * Ejecuta una operacion que modifica datos. El mensaje de exito solo se muestra cuando
     * la capa de aplicacion confirma la operacion; al terminar se recarga la vista.
     */
    protected void ejecutarOperacion(Callable<?> tarea, String mensajeExito) {
        if (ocupado) return;
        ocupado = true;
        recargarDespues = false;
        alCambiarOcupado(true);
        TareaBD.ejecutar(tarea, valor -> {
            boolean exito = valor instanceof Boolean b ? b : valor != null;
            if (exito) {
                contexto.mostrarMensaje(mensajeExito, ContextoApp.Nivel.EXITO);
                recargarDespues = true;
                alOperacionExitosa(valor);
            } else {
                contexto.mostrarMensaje(mensajeErrorGestor(), ContextoApp.Nivel.ERROR);
                alErrorOperacion();
            }
        }, e -> contexto.mostrarMensaje(TareaBD.mensajeError(e), ContextoApp.Nivel.ERROR), () -> {
            ocupado = false;
            alCambiarOcupado(false);
            contexto.actualizarBarraSuperior();
            if (recargarDespues) {
                recargarDespues = false;
                recargar();
            }
        });
    }

    /** Punto de extension para habilitar/deshabilitar controles mientras hay una tarea. */
    protected void alCambiarOcupado(boolean ocupado) { /* opcional */ }

    /** Punto de extension para reaccionar al resultado confirmado de una operacion. */
    protected void alOperacionExitosa(Object valor) { /* opcional */ }

    /** Punto de extension para limpiar estado temporal cuando la operacion no se confirma. */
    protected void alErrorOperacion() { /* opcional */ }

    /** Mensaje de negocio o de PostgreSQL registrado por GestorOrdenes. */
    protected String mensajeErrorGestor() {
        String error = gestor.getUltimoError();
        return error != null && !error.isBlank()
                ? error
                : "La operación no se pudo completar; PostgreSQL no la confirmó.";
    }
}
