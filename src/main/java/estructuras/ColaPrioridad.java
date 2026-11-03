package estructuras;

import modelo.OrdenMantenimiento;

/**
 * Cola de prioridad implementada como monticulo binario (heap) sobre un arreglo propio.
 * La raiz siempre es la orden mas critica y, a igual criticidad, la mas antigua.
 * No usa colecciones de java.util.
 */
public class ColaPrioridad {
    private OrdenMantenimiento[] datos = new OrdenMantenimiento[8];
    private int tamano = 0;

    public boolean estaVacia() { return tamano == 0; }
    public int tamano() { return tamano; }

    /** Inserta una orden. Complejidad O(log n). */
    public void encolar(OrdenMantenimiento o) {
        if (o == null) throw new IllegalArgumentException("La orden no puede ser nula");
        if (tamano == datos.length) crecer();
        datos[tamano] = o;
        subir(tamano);
        tamano++;
    }

    /** Extrae la orden de mayor prioridad; devuelve null si la cola esta vacia. O(log n). */
    public OrdenMantenimiento desencolar() {
        if (tamano == 0) return null;
        OrdenMantenimiento raiz = datos[0];
        tamano--;
        datos[0] = datos[tamano];
        datos[tamano] = null;
        if (tamano > 0) bajar(0);
        return raiz;
    }

    /** Consulta la orden de mayor prioridad sin extraerla. O(1). */
    public OrdenMantenimiento verPrimera() { return tamano == 0 ? null : datos[0]; }

    /** Busca una orden por id (recorrido lineal). O(n). */
    public OrdenMantenimiento buscarPorId(long id) {
        int i = indiceDe(id);
        return i < 0 ? null : datos[i];
    }

    /** Elimina una orden por id (p. ej. cancelacion). Devuelve false si no existe. O(n). */
    public boolean eliminarPorId(long id) {
        int i = indiceDe(id);
        if (i < 0) return false;
        tamano--;
        datos[i] = datos[tamano];
        datos[tamano] = null;
        if (i < tamano) { subir(i); bajar(i); }
        return true;
    }

    /** Reubica una orden cuyos criterios fueron modificados. O(n) por la busqueda + O(log n). */
    public boolean reordenar(long id) {
        int i = indiceDe(id);
        if (i < 0) return false;
        subir(i); bajar(i);
        return true;
    }

    /** Devuelve las ordenes en orden de atencion sin alterar la cola (trabaja sobre una copia). O(n log n). */
    public OrdenMantenimiento[] recorridoPorPrioridad() {
        ColaPrioridad copia = new ColaPrioridad();
        for (int i = 0; i < tamano; i++) copia.encolar(datos[i]);
        OrdenMantenimiento[] salida = new OrdenMantenimiento[tamano];
        for (int i = 0; i < salida.length; i++) salida[i] = copia.desencolar();
        return salida;
    }

    private int indiceDe(long id) {
        for (int i = 0; i < tamano; i++) if (datos[i].getId() == id) return i;
        return -1;
    }

    private void subir(int i) {
        while (i > 0) {
            int padre = (i - 1) / 2;
            if (!datos[i].tienePrioridadSobre(datos[padre])) break;
            intercambiar(i, padre);
            i = padre;
        }
    }

    private void bajar(int i) {
        while (true) {
            int izq = 2 * i + 1, der = izq + 1, mayor = i;
            if (izq < tamano && datos[izq].tienePrioridadSobre(datos[mayor])) mayor = izq;
            if (der < tamano && datos[der].tienePrioridadSobre(datos[mayor])) mayor = der;
            if (mayor == i) return;
            intercambiar(i, mayor);
            i = mayor;
        }
    }

    private void intercambiar(int a, int b) {
        OrdenMantenimiento t = datos[a]; datos[a] = datos[b]; datos[b] = t;
    }

    private void crecer() {
        OrdenMantenimiento[] nuevo = new OrdenMantenimiento[datos.length * 2];
        System.arraycopy(datos, 0, nuevo, 0, tamano);
        datos = nuevo;
    }
}
