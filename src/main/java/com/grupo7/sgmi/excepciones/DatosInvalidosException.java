package com.grupo7.sgmi.excepciones;

/** Los datos enviados no cumplen las reglas de negocio o de la tabla (HTTP 400). */
public class DatosInvalidosException extends RuntimeException {

    public DatosInvalidosException(String mensaje) {
        super(mensaje);
    }

    public DatosInvalidosException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
