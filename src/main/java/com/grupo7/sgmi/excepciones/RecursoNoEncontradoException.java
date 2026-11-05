package com.grupo7.sgmi.excepciones;

/** El recurso solicitado no existe en PostgreSQL (HTTP 404). */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
