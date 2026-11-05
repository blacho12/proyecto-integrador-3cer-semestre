package com.grupo7.sgmi.excepciones;

/** PostgreSQL no esta disponible o rechaza la conexion (HTTP 503). */
public class ServicioNoDisponibleException extends RuntimeException {

    public ServicioNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
