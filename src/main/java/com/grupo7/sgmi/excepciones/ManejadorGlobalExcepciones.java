package com.grupo7.sgmi.excepciones;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.grupo7.sgmi.dto.ErrorDTO;

import jakarta.servlet.http.HttpServletRequest;

/** Traduce las excepciones de la aplicacion a respuestas HTTP con el mismo formato. */
@RestControllerAdvice
public class ManejadorGlobalExcepciones {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalExcepciones.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorDTO> noEncontrado(RecursoNoEncontradoException e, HttpServletRequest peticion) {
        return respuesta(HttpStatus.NOT_FOUND, e.getMessage(), peticion);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorDTO> rutaNoEncontrada(NoResourceFoundException e, HttpServletRequest peticion) {
        return respuesta(HttpStatus.NOT_FOUND, "No existe el recurso solicitado.", peticion);
    }

    @ExceptionHandler(ConflictoDatosException.class)
    public ResponseEntity<ErrorDTO> conflicto(ConflictoDatosException e, HttpServletRequest peticion) {
        return respuesta(HttpStatus.CONFLICT, e.getMessage(), peticion);
    }

    @ExceptionHandler(DatosInvalidosException.class)
    public ResponseEntity<ErrorDTO> datosInvalidos(DatosInvalidosException e, HttpServletRequest peticion) {
        return respuesta(HttpStatus.BAD_REQUEST, e.getMessage(), peticion);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDTO> validacion(MethodArgumentNotValidException e, HttpServletRequest peticion) {
        String mensaje = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return respuesta(HttpStatus.BAD_REQUEST, mensaje, peticion);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorDTO> parametroInvalido(MethodArgumentTypeMismatchException e,
                                                      HttpServletRequest peticion) {
        return respuesta(HttpStatus.BAD_REQUEST,
                "El parametro '" + e.getName() + "' no tiene un valor valido.", peticion);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDTO> cuerpoIlegible(HttpMessageNotReadableException e,
                                                   HttpServletRequest peticion) {
        return respuesta(HttpStatus.BAD_REQUEST,
                "El cuerpo de la peticion no es un JSON valido o tiene un tipo de dato incorrecto.",
                peticion);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorDTO> argumentoInvalido(IllegalArgumentException e,
                                                      HttpServletRequest peticion) {
        return respuesta(HttpStatus.BAD_REQUEST, e.getMessage(), peticion);
    }

    @ExceptionHandler(ServicioNoDisponibleException.class)
    public ResponseEntity<ErrorDTO> noDisponible(ServicioNoDisponibleException e,
                                                 HttpServletRequest peticion) {
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), peticion);
    }

    @ExceptionHandler(ErrorBaseDatosException.class)
    public ResponseEntity<ErrorDTO> errorBaseDatos(ErrorBaseDatosException e,
                                                   HttpServletRequest peticion) {
        log.error("Error de base de datos en {} {}: {}", peticion.getMethod(),
                peticion.getRequestURI(), e.getMessage());
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), peticion);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDTO> errorInesperado(Exception e, HttpServletRequest peticion) {
        log.error("Error inesperado en {} {}", peticion.getMethod(), peticion.getRequestURI(), e);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrio un error inesperado en el servidor.", peticion);
    }

    private ResponseEntity<ErrorDTO> respuesta(HttpStatus estado, String mensaje,
                                               HttpServletRequest peticion) {
        ErrorDTO error = new ErrorDTO(
                LocalDateTime.now().withNano(0),
                estado.value(),
                estado.getReasonPhrase(),
                mensaje,
                peticion.getRequestURI());
        return ResponseEntity.status(estado).body(error);
    }
}
