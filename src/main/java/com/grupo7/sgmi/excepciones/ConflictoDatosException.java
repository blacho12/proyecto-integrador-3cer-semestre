package com.grupo7.sgmi.excepciones;

/**
 * La operacion choca con una regla de la base de datos: clave duplicada,
 * clave foranea invalida o registro con dependencias (HTTP 409).
 */
public class ConflictoDatosException extends RuntimeException {

    public ConflictoDatosException(String mensaje) {
        super(mensaje);
    }

    public ConflictoDatosException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
