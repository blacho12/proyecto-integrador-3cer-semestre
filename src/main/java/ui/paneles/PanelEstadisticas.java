package ui.paneles;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

import app.GestorOrdenes;
import modelo.Area;
import modelo.Equipo;
import modelo.OrdenMantenimiento;
import modelo.OrdenMantenimiento.Estado;
import ui.ContextoApp;
import ui.componentes.GraficaBarras;
import ui.componentes.PanelTarjeta;
import ui.componentes.Tema;

/**
 * Indicadores calculados exclusivamente con ordenes, equipos y areas reales.
 * No se presentan porcentajes de sostenibilidad ni ahorros que no puedan deducirse
 * de los datos almacenados.
 */
public class PanelEstadisticas extends PanelBase {

    private final GraficaBarras graficaEstado = new GraficaBarras();
    private final GraficaBarras graficaCriticidad = new GraficaBarras();
    private final GraficaBarras graficaTipo = new GraficaBarras();
    private final GraficaBarras graficaArea = new GraficaBarras();
    private final JLabel lblResumen = Tema.etiquetaPequena(" ");
    private final JLabel lblVacio = Tema.etiquetaPequena(
            "No hay órdenes registradas; las gráficas se llenarán con datos reales.");
    private final JProgressBar progreso = Tema.barraProgreso();

    public PanelEstadisticas(GestorOrdenes gestor, ContextoApp contexto) {
        super(gestor, contexto);
        setLayout(new BorderLayout(0, 14));
        add(Tema.cabeceraVista("Indicadores y estadísticas",
                "Distribuciones calculadas con las órdenes y los equipos persistidos en PostgreSQL.",
                progreso), BorderLayout.NORTH);
        add(construirCuerpo(), BorderLayout.CENTER);
    }

    private JScrollPane construirCuerpo() {
        JPanel cuerpo = new JPanel();
        cuerpo.setOpaque(false);
        cuerpo.setLayout(new BoxLayout(cuerpo, BoxLayout.Y_AXIS));

        JPanel resumen = new JPanel(new BorderLayout());
        resumen.setOpaque(false);
        resumen.add(lblResumen, BorderLayout.WEST);
        lblVacio.setForeground(Tema.AMBAR);
        lblVacio.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tema.BORDE),
                new javax.swing.border.EmptyBorder(8, 10, 8, 10)));
        lblVacio.setOpaque(true);
        lblVacio.setBackground(Tema.PANEL_ALT);
        lblVacio.setVisible(false);
        resumen.add(lblVacio, BorderLayout.SOUTH);
        cuerpo.add(resumen);
        cuerpo.add(Box.createVerticalStrut(12));

        JPanel graficas = new JPanel(new GridLayout(2, 2, 14, 14));
        graficas.setOpaque(false);
        graficas.add(tarjetaGrafica("Órdenes por estado", graficaEstado));
        graficas.add(tarjetaGrafica("Órdenes por criticidad", graficaCriticidad));
        graficas.add(tarjetaGrafica("Órdenes por tipo de mantenimiento", graficaTipo));
        graficas.add(tarjetaGrafica("Distribución de órdenes por área", graficaArea));
        cuerpo.add(graficas);

        cuerpo.add(Box.createVerticalStrut(12));
        JLabel nota = new JLabel(
                "<html>Los conteos incluyen órdenes PENDIENTE, CERRADA y CANCELADA. "
                        + "El área se resuelve buscando cada equipo en el índice AVL. "
                        + "No se muestran métricas de ahorro o sostenibilidad porque no forman parte "
                        + "de los datos registrados.</html>");
        nota.setFont(Tema.FUENTE_PEQUENA);
        nota.setForeground(Tema.TEXTO_TENUE);
        nota.setAlignmentX(LEFT_ALIGNMENT);
        cuerpo.add(nota);

        JScrollPane scroll = new JScrollPane(cuerpo);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private PanelTarjeta tarjetaGrafica(String titulo, GraficaBarras grafica) {
        PanelTarjeta tarjeta = new PanelTarjeta();
        tarjeta.setLayout(new BorderLayout(0, 8));
        tarjeta.add(Tema.encabezadoSeccion(titulo), BorderLayout.NORTH);
        tarjeta.add(grafica, BorderLayout.CENTER);
        tarjeta.setPreferredSize(new java.awt.Dimension(340, 260));
        return tarjeta;
    }

    @Override
    public void recargar() {
        cargar(this::calcular, this::aplicar, "No se pudieron calcular las estadísticas");
    }

    private Datos calcular() throws Exception {
        List<OrdenMantenimiento> ordenes = gestor.consultarTodasLasOrdenes();
        List<Area> areas = gestor.consultarAreas();

        Map<Integer, String> nombreArea = new HashMap<>();
        for (Area area : areas) nombreArea.put(area.getIdArea(), area.getNombre());

        Map<String, Integer> porEstado = new LinkedHashMap<>();
        porEstado.put("PENDIENTE", 0);
        porEstado.put("CERRADA", 0);
        porEstado.put("CANCELADA", 0);

        Map<String, Integer> porCriticidad = new LinkedHashMap<>();
        porCriticidad.put("ALTA", 0);
        porCriticidad.put("MEDIA", 0);
        porCriticidad.put("BAJA", 0);

        Map<String, Integer> porTipo = new LinkedHashMap<>();
        porTipo.put("CORRECTIVO", 0);
        porTipo.put("PREVENTIVO", 0);

        Map<String, Integer> porArea = new LinkedHashMap<>();

        int pendientes = 0;
        for (OrdenMantenimiento orden : ordenes) {
            porEstado.merge(orden.getEstado().name(), 1, Integer::sum);
            porCriticidad.merge(orden.getCriticidad().name(), 1, Integer::sum);
            porTipo.merge(orden.getTipo(), 1, Integer::sum);
            if (orden.getEstado() == Estado.PENDIENTE) pendientes++;

            Equipo equipo = gestor.getEquipos().buscar(orden.getCodigoEquipo());
            String area = equipo == null
                    ? "Sin área (equipo no indexado)"
                    : nombreArea.getOrDefault(equipo.getIdArea(), "Área " + equipo.getIdArea());
            porArea.merge(area, 1, Integer::sum);
        }

        return new Datos(ordenes.size(), pendientes, gestor.getEquipos().tamano(),
                porEstado, porCriticidad, porTipo, porArea);
    }

    private void aplicar(Datos datos) {
        lblResumen.setText(datos.total + " órdenes históricas · " + datos.pendientes
                + " pendientes en la cola · " + datos.equipos + " equipos en el AVL · "
                + gestor.getHistorial().tamano() + " acciones por deshacer");
        lblVacio.setVisible(datos.total == 0);

        graficaEstado.setDatos(datos.porEstado, colores(
                "PENDIENTE", Tema.ACENTO_CLARO,
                "CERRADA", Tema.VERDE,
                "CANCELADA", Tema.ROJO));
        graficaCriticidad.setDatos(datos.porCriticidad, colores(
                "ALTA", Tema.ROJO,
                "MEDIA", Tema.AMBAR,
                "BAJA", Tema.VERDE));
        graficaTipo.setDatos(datos.porTipo, colores(
                "CORRECTIVO", Tema.MORADO,
                "PREVENTIVO", Tema.CIAN));

        Map<String, Color> coloresArea = new LinkedHashMap<>();
        Color[] paleta = {Tema.ACENTO, Tema.CIAN, Tema.VERDE, Tema.AMBAR, Tema.MORADO,
                Tema.ROJO, Tema.ACENTO_CLARO};
        int i = 0;
        for (String area : datos.porArea.keySet()) {
            coloresArea.put(area, paleta[i % paleta.length]);
            i++;
        }
        graficaArea.setDatos(datos.porArea, coloresArea);
        contexto.actualizarBarraSuperior();
    }

    private Map<String, Color> colores(Object... pares) {
        Map<String, Color> mapa = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pares.length; i += 2) {
            mapa.put((String) pares[i], (Color) pares[i + 1]);
        }
        return mapa;
    }

    @Override
    protected void alCambiarOcupado(boolean ocupado) {
        progreso.setVisible(ocupado);
    }

    /** Resultado del calculo, listo para pintarse en el EDT. */
    private static class Datos {
        final int total;
        final int pendientes;
        final int equipos;
        final Map<String, Integer> porEstado;
        final Map<String, Integer> porCriticidad;
        final Map<String, Integer> porTipo;
        final Map<String, Integer> porArea;

        Datos(int total, int pendientes, int equipos,
              Map<String, Integer> porEstado, Map<String, Integer> porCriticidad,
              Map<String, Integer> porTipo, Map<String, Integer> porArea) {
            this.total = total;
            this.pendientes = pendientes;
            this.equipos = equipos;
            this.porEstado = porEstado;
            this.porCriticidad = porCriticidad;
            this.porTipo = porTipo;
            this.porArea = porArea;
        }
    }
}
