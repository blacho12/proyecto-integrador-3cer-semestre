package com.grupo7.sgmi.services;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.grupo7.sgmi.excepciones.ConflictoDatosException;
import com.grupo7.sgmi.excepciones.DatosInvalidosException;
import com.grupo7.sgmi.excepciones.ErrorBaseDatosException;
import com.grupo7.sgmi.excepciones.ServicioNoDisponibleException;

import datos.ConexionBD;

/**
 * Abre una conexion del pool de Hikari por cada operacion y controla la transaccion.
 *
 * Los DAO del paquete datos/ siguen recibiendo una Connection y usando JDBC puro,
 * tal como exige el proyecto academico. Aqui solo se agrega el manejo del ciclo de
 * vida (commit/rollback/close) y la traduccion de los SQLState a excepciones HTTP.
 */
@Component
public class EjecutorBD {

    private static final Logger log = LoggerFactory.getLogger(EjecutorBD.class);

    private final DataSource dataSource;

    public EjecutorBD(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Ejecuta el trabajo dentro de una transaccion: commit si termina, rollback si falla. */
    public <T> T enTransaccion(TrabajoBD<T> trabajo) {
        try (Connection conexion = dataSource.getConnection()) {
            conexion.setAutoCommit(false);
            try {
                T resultado = trabajo.ejecutar(conexion);
                conexion.commit();
                return resultado;
            } catch (SQLException e) {
                revertir(conexion);
                throw traducir(e);
            } catch (RuntimeException e) {
                revertir(conexion);
                throw e;
            }
        } catch (SQLException e) {
            throw traducir(e);
        }
    }

    /** Abre una conexion para consultas de solo lectura. */
    public <T> T enLectura(TrabajoBD<T> trabajo) {
        try (Connection conexion = dataSource.getConnection()) {
            return trabajo.ejecutar(conexion);
        } catch (SQLException e) {
            throw traducir(e);
        }
    }

    private void revertir(Connection conexion) {
        try {
            conexion.rollback();
        } catch (SQLException e) {
            log.warn("No se pudo hacer rollback: {}", e.getMessage());
        }
    }

    /** Convierte un SQLException en la excepcion de dominio que corresponde al codigo HTTP. */
    private RuntimeException traducir(SQLException e) {
        String estado = e.getSQLState();
        String mensaje = ConexionBD.mensajeAmigable(e);

        if (estado != null) {
            if (estado.startsWith("08")) {
                return new ServicioNoDisponibleException("No hay conexion con PostgreSQL.", e);
            }
            switch (estado) {
                case "23505", "23503" -> {
                    return new ConflictoDatosException(mensaje, e);
                }
                case "23514", "22001", "22P02" -> {
                    return new DatosInvalidosException(mensaje, e);
                }
                default -> {
                    // Continua con el error generico.
                }
            }
        }
        return new ErrorBaseDatosException("Error en PostgreSQL: " + mensaje, e);
    }

    /** Trabajo JDBC que recibe una conexion abierta y puede lanzar SQLException. */
    @FunctionalInterface
    public interface TrabajoBD<T> {
        T ejecutar(Connection conexion) throws SQLException;
    }
}
