package com.grupo7.sgmi.excepciones;

/** Falla inesperada de PostgreSQL (HTTP 500). */
public class ErrorBaseDatosException extends RuntimeException {

    public ErrorBaseDatosException(String mensaje) {
        super(mensaje);
    }

    public ErrorBaseDatosException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
