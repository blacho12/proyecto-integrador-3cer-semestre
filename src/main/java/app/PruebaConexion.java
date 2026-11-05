package app;

import java.sql.Connection;
import java.sql.SQLException;

import datos.ConexionBD;
import datos.ConfiguracionEntorno;

/** Diagnostico de conexion: comprueba el entorno/.env y ejecuta una conexion real. */
public class PruebaConexion {

    public static void main(String[] args) {
        System.out.println("Origen de credenciales: " + ConfiguracionEntorno.origen());

        try (Connection conexion = ConexionBD.getConnection()) {
            System.out.println("CONEXION EXITOSA");
            System.out.println("Base de datos: " + conexion.getCatalog());
            System.out.println("Usuario: " + conexion.getMetaData().getUserName());
        } catch (SQLException e) {
            System.out.println("ERROR AL CONECTAR CON POSTGRESQL");
            System.out.println(ConexionBD.mensajeAmigable(e));
        }
    }
}
