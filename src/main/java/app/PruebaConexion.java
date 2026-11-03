
package app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class PruebaConexion {

    public static void main(String[] args) {

        String url = System.getenv("DB_URL");
        String usuario = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");

        if (url == null || usuario == null || password == null) {
            System.out.println("ERROR: Faltan variables de entorno.");
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
