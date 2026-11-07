package datos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lectura de la configuracion desde el archivo .env del proyecto.
 *
 * Las variables de entorno del sistema tienen prioridad; si no existe alguna,
 * se toma del archivo .env ubicado en la carpeta del proyecto (o en una carpeta
 * padre, por si la aplicacion se ejecuta desde target/ o desde un subdirectorio).
 * Tambien se acepta src/main/.env, que es donde algunos IDE lo dejan.
 * Asi la aplicacion funciona igual desde VS Code, desde una terminal o desde el IDE.
 */
public final class Configuracion {
    /** Ubicaciones del .env, en orden de prioridad, relativas a cada carpeta buscada. */
    private static final String[] RUTAS_ARCHIVO = {".env", "src/main/.env"};

    private static Map<String, String> valoresArchivo;
    private static Path rutaArchivo;

    private Configuracion() { /* utilitaria: no se instancia */ }

    /** Devuelve el valor de la variable: primero del sistema, luego del archivo .env. */
    public static String obtener(String clave) {
        String valor = limpiar(System.getenv(clave));
        if (valor != null) return valor;
        return cargarArchivo().get(clave);
    }

    /** Indica si la variable tiene un valor utilizable (sistema o .env). */
    public static boolean existe(String clave) {
        return obtener(clave) != null;
    }

    /** Ruta del .env que se leyo, o null si no se encontro ninguno. */
    public static synchronized Path rutaArchivo() {
        cargarArchivo();
        return rutaArchivo;
    }

    /** Carga (una sola vez) el contenido del .env y lo deja en memoria. */
    private static synchronized Map<String, String> cargarArchivo() {
        if (valoresArchivo != null) return valoresArchivo;

        Map<String, String> valores = new HashMap<>();
        Path encontrado = buscarArchivo();

        if (encontrado != null) {
            try {
                List<String> lineas = Files.readAllLines(encontrado, StandardCharsets.UTF_8);
                for (String linea : lineas) {
                    procesarLinea(linea, valores);
                }
            } catch (IOException e) {
                System.err.println("No se pudo leer el archivo " + encontrado + ": " + e.getMessage());
            }
        }

        rutaArchivo = encontrado;
        valoresArchivo = Collections.unmodifiableMap(valores);
        return valoresArchivo;
    }

    /** Busca .env en la carpeta actual y en sus carpetas padre. */
    private static Path buscarArchivo() {
        Path carpeta = Path.of("").toAbsolutePath();
        for (int nivel = 0; nivel < 4 && carpeta != null; nivel++) {
            for (String ruta : RUTAS_ARCHIVO) {
                Path candidato = carpeta.resolve(ruta);
                if (Files.isRegularFile(candidato)) return candidato;
            }
            carpeta = carpeta.getParent();
        }
        return null;
    }

    /** Interpreta una linea con formato CLAVE=valor, ignorando comentarios y lineas vacias. */
    private static void procesarLinea(String linea, Map<String, String> destino) {
        String texto = linea;
        if (texto == null) return;
        if (texto.endsWith("\r")) texto = texto.substring(0, texto.length() - 1); // archivos de Windows
        texto = texto.trim();
        if (texto.isEmpty() || texto.startsWith("#")) return;
        if (texto.startsWith("export ")) texto = texto.substring("export ".length()).trim();

        int separador = texto.indexOf('=');
        if (separador <= 0) return;

        String clave = texto.substring(0, separador).trim();
        String valor = quitarComillas(texto.substring(separador + 1).trim());
        if (!clave.isEmpty() && valor != null && !valor.isBlank()) {
            destino.put(clave, valor);
        }
    }

    /** Elimina las comillas simples o dobles que envuelven al valor. */
    private static String quitarComillas(String valor) {
        if (valor == null) return null;
        if (valor.length() >= 2) {
            char primero = valor.charAt(0);
            char ultimo = valor.charAt(valor.length() - 1);
            if ((primero == '"' && ultimo == '"') || (primero == '\'' && ultimo == '\'')) {
                return valor.substring(1, valor.length() - 1).trim();
            }
        }
        return valor;
    }

    /** Normaliza un valor: null si falta o esta en blanco. */
    private static String limpiar(String valor) {
        if (valor == null) return null;
        String texto = valor.trim();
        return texto.isEmpty() ? null : texto;
    }
}
