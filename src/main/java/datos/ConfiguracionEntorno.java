package datos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resuelve las credenciales de PostgreSQL desde las variables de entorno o desde un
 * archivo .env local, sin agregar dependencias externas.
 *
 * Prioridad:
 *   1. Variables de entorno reales: DB_URL, DB_USER y DB_PASSWORD.
 *   2. Archivo indicado por la variable SGMI_ENV_FILE.
 *   3. src/main/.env o .env en el directorio actual o en cualquiera de sus padres.
 *
 * El parser acepta lineas "CLAVE=valor", "CLAVE = valor", "export CLAVE=valor",
 * comentarios con # y valores entre comillas simples o dobles. Nunca imprime ni
 * registra los valores de las credenciales.
 */
public final class ConfiguracionEntorno {

    /** Variable (de entorno o propiedad Java) para indicar una ruta .env explicita. */
    public static final String ARCHIVO_PERSONALIZADO = "SGMI_ENV_FILE";

    private static Map<String, String> valores;
    private static String origen;
    private static boolean buscado;

    private ConfiguracionEntorno() { /* utilitaria: no se instancia */ }

    /** Devuelve el valor de una clave, o cadena vacia si no esta definida. */
    public static String obtener(String clave) {
        String valorEntorno = System.getenv(clave);
        if (valorEntorno != null && !valorEntorno.isBlank()) return valorEntorno.trim();
        return cargar().getOrDefault(clave, "");
    }

    /** Ruta del .env utilizado o "variables de entorno" si no se uso ningun archivo. */
    public static synchronized String origen() {
        cargarArchivo();
        return origen == null ? "variables de entorno" : origen;
    }

    /** Vuelve a leer el archivo en la proxima consulta (util al editar el .env en caliente). */
    public static synchronized void recargar() {
        buscado = false;
        valores = null;
        origen = null;
    }

    private static synchronized Map<String, String> cargar() {
        cargarArchivo();
        return valores == null ? Collections.emptyMap() : valores;
    }

    private static void cargarArchivo() {
        if (buscado) return;
        buscado = true;

        Path archivo = archivoConfigurado();
        if (archivo == null) archivo = buscarArchivo();
        if (archivo == null) {
            valores = Collections.emptyMap();
            return;
        }

        try {
            valores = leer(archivo);
            origen = archivo.toAbsolutePath().normalize().toString();
        } catch (IOException e) {
            valores = Collections.emptyMap();
            origen = null;
        }
    }

    /** Ruta indicada explicitamente por SGMI_ENV_FILE (entorno o propiedad Java). */
    private static Path archivoConfigurado() {
        String ruta = System.getenv(ARCHIVO_PERSONALIZADO);
        if (ruta == null || ruta.isBlank()) {
            ruta = System.getProperty(ARCHIVO_PERSONALIZADO);
        }
        if (ruta == null || ruta.isBlank()) return null;

        Path archivo = Paths.get(ruta.trim());
        return Files.isRegularFile(archivo) ? archivo : null;
    }

    /**
     * Busca src/main/.env antes que .env en cada nivel, de modo que el archivo del
     * proyecto tiene prioridad sobre el de la raiz si ambos existen.
     */
    private static Path buscarArchivo() {
        Path base = Paths.get(System.getProperty("user.dir", "."))
                .toAbsolutePath().normalize();

        for (Path directorio = base; directorio != null; directorio = directorio.getParent()) {
            Path enSrcMain = directorio.resolve("src").resolve("main").resolve(".env");
            if (Files.isRegularFile(enSrcMain)) return enSrcMain;

            Path enRaiz = directorio.resolve(".env");
            if (Files.isRegularFile(enRaiz)) return enRaiz;
        }
        return null;
    }

    /** Lee el archivo y devuelve solo las claves declaradas; ignora lineas invalidas. */
    private static Map<String, String> leer(Path archivo) throws IOException {
        Map<String, String> resultado = new LinkedHashMap<>();
        List<String> lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);

        for (String original : lineas) {
            String linea = original;
            if (!linea.isEmpty() && linea.charAt(0) == '\uFEFF') {
                linea = linea.substring(1);
            }
            linea = linea.strip();
            if (linea.isEmpty() || linea.startsWith("#")) continue;
            if (linea.startsWith("export ")) linea = linea.substring(7).strip();

            int separador = linea.indexOf('=');
            if (separador <= 0) continue;

            String clave = linea.substring(0, separador).strip();
            String valor = linea.substring(separador + 1).strip();
            if (clave.isEmpty()) continue;

            if (valor.length() >= 2
                    && ((valor.startsWith("\"") && valor.endsWith("\""))
                    || (valor.startsWith("'") && valor.endsWith("'")))) {
                valor = valor.substring(1, valor.length() - 1);
            }
            resultado.put(clave, valor);
        }
        return resultado;
    }
}
