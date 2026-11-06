package com.grupo7.sgmi.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.grupo7.sgmi.excepciones.ErrorBaseDatosException;
import org.junit.jupiter.api.Test;

class EjecutorBDTest {

    @Test
    void revierteLaTransaccionSiFallaUnPasoAntesDelCommit() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection conexion = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(conexion);
        EjecutorBD ejecutor = new EjecutorBD(dataSource);

        assertThrows(ErrorBaseDatosException.class,
                () -> ejecutor.enTransaccion(conn -> {
                    throw new SQLException("fallo simulado", "XX000");
                }));

        verify(conexion).rollback();
        verify(conexion, never()).commit();
    }
}
