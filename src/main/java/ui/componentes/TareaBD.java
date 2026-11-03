package ui.componentes;

import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.swing.SwingWorker;

import datos.ConexionBD;

/**
 * Ejecuta trabajo de base de datos fuera del Event Dispatch Thread.
 *
 * GestorOrdenes comparte una unica Connection JDBC, por lo que todas las tareas se
 * serializan en un ejecutor de un solo hilo. Asi nunca hay dos consultas simultaneas
 * sobre la misma conexion y la ventana no se congela.
 */
public final class TareaBD {

    private static final ExecutorService EJECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread hilo = new Thread(r, "postgres-jdbc");
        hilo.setDaemon(true);
        return hilo;
    });

    private TareaBD() { /* utilitaria */ }

    /**
     * Ejecuta una tarea JDBC y devuelve el resultado en el EDT.
     * alExito, alError y alFinalizar se invocan siempre en el EDT.
     */
    public static <T> void ejecutar(Callable<T> tarea,
                                    Consumer<T> alExito,
                                    Consumer<Exception> alError,
                                    Runnable alFinalizar) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return EJECUTOR.submit(tarea).get();
            }

            @Override
            protected void done() {
                try {
                    T valor = get();
                    if (alExito != null) alExito.accept(valor);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (alError != null) alError.accept(e);
                } catch (ExecutionException e) {
                    Throwable causa = e.getCause();
                    if (alError != null) {
                        alError.accept(causa instanceof Exception ex ? ex : e);
                    }
                } finally {
                    if (alFinalizar != null) alFinalizar.run();
                }
            }
        }.execute();
    }

    /** Traduce una excepcion a un mensaje comprensible para el usuario. */
    public static String mensajeError(Throwable e) {
        if (e instanceof SQLException sql) {
            return ConexionBD.mensajeAmigable(sql);
        }
        String mensaje = e == null ? null : e.getMessage();
        return mensaje == null || mensaje.isBlank() ? "Error inesperado en la operacion." : mensaje;
    }
}
