
package app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import datos.Configuracion;

public class PruebaConexion {

    public static void main(String[] args) {

        String url = Configuracion.obtener("DB_URL");
        String usuario = Configuracion.obtener("DB_USER");
        String password = Configuracion.obtener("DB_PASSWORD");

        if (url == null || usuario == null || password == null) {
            System.out.println("ERROR: Faltan DB_URL, DB_USER y DB_PASSWORD (entorno o archivo .env).");
            return;
        }

        try (Connection conexion =
                DriverManager.getConnection(url, usuario, password)) {

            System.out.println("CONEXION EXITOSA");
            System.out.println("Base de datos: "
                    + conexion.getCatalog());
            System.out.println("Usuario: "
                    + conexion.getMetaData().getUserName());

        } catch (SQLException e) {

            System.out.println("ERROR AL CONECTAR CON POSTGRESQL");
            System.out.println(e.getMessage());
        }
    }
}
