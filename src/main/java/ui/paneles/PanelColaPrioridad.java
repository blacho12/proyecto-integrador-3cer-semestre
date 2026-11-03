package ui.paneles;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import app.GestorOrdenes;
import modelo.Criticidad;
import modelo.Equipo;
import modelo.OrdenMantenimiento;
import ui.ContextoApp;
import ui.componentes.PanelTarjeta;
import ui.componentes.Tema;

/**
 * Vista de la cola de prioridad. El orden y el tope se toman directamente de
 * ColaPrioridad (heap binario); Swing no reordena ni compara las ordenes.
 */
public class PanelColaPrioridad extends PanelBase {

    private final ModeloCola modelo = new ModeloCola();
    private final JTable tabla = new JTable(modelo);

    private final JLabel lblTopeOrden = new JLabel("—");
    private final JLabel lblTopeEquipo = new JLabel(" ");
    private final JLabel lblTopeDescripcion = new JLabel(" ");
    private final JLabel lblTopeCriterios = new JLabel(" ");
    private final JLabel lblTopeBadge = Tema.badge("SIN PENDIENTES", Tema.TEXTO_TENUE);
    private final JLabel lblTopeFecha = new JLabel(" ");
    private final JLabel lblTotal = Tema.etiquetaPequena("0 órdenes en cola");
    private final JLabel lblVacio = Tema.etiquetaPequena("La cola de prioridad está vacía.");
    private final JButton btnAtender = Tema.botonPrimario("Atender la más prioritaria");
    private final JButton btnCancelar = Tema.botonPeligro("Cancelar seleccionada");
    private final JProgressBar progreso = Tema.barraProgreso();

    private final PanelTarjeta tarjetaTope = new PanelTarjeta(
            Tema.PANEL, Tema.mezclar(Tema.ACENTO, Tema.FONDO, 0.4f), 14);

    public PanelColaPrioridad(GestorOrdenes gestor, ContextoApp contexto) {
        super(gestor, contexto);
        setLayout(new BorderLayout(0, 14));
        add(Tema.cabeceraVista("Cola de prioridad",
                "Órdenes PENDIENTE ordenadas por el heap: mayor criticidad y, en empate, menor consecutivo.",
                progreso), BorderLayout.NORTH);

        JPanel cuerpo = new JPanel(new BorderLayout(0, 14));
        cuerpo.setOpaque(false);
        cuerpo.add(construirTarjetaTope(), BorderLayout.NORTH);
        cuerpo.add(construirTabla(), BorderLayout.CENTER);
        add(cuerpo, BorderLayout.CENTER);

        configurarTabla();
        configurarEventos();
        actualizarTope();
        llenarTabla();
    }

    private PanelTarjeta construirTarjetaTope() {
        tarjetaTope.setLayout(new BorderLayout(16, 8));

        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setOpaque(false);
        cabecera.add(Tema.encabezadoSeccion("Siguiente orden a atender"), BorderLayout.WEST);
        cabecera.add(Tema.badge("TOPE DEL HEAP · RAÍZ O(1)", Tema.ACENTO_CLARO), BorderLayout.EAST);

        JPanel detalles = new JPanel();
        detalles.setOpaque(false);
        detalles.setLayout(new BoxLayout(detalles, BoxLayout.Y_AXIS));

        JPanel linea = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        linea.setOpaque(false);
        lblTopeOrden.setFont(Tema.MONO_BOLD.deriveFont(17f));
        lblTopeOrden.setForeground(Tema.TEXTO);
        linea.add(lblTopeOrden);
        linea.add(lblTopeBadge);
        linea.setAlignmentX(LEFT_ALIGNMENT);

        lblTopeEquipo.setFont(Tema.FUENTE_MEDIA);
        lblTopeEquipo.setForeground(Tema.CIAN);
        lblTopeEquipo.setAlignmentX(LEFT_ALIGNMENT);
        lblTopeDescripcion.setFont(Tema.FUENTE);
        lblTopeDescripcion.setForeground(Tema.TEXTO_SEC);
        lblTopeDescripcion.setAlignmentX(LEFT_ALIGNMENT);
        lblTopeCriterios.setFont(Tema.MONO);
        lblTopeCriterios.setForeground(Tema.TEXTO_TENUE);
        lblTopeCriterios.setAlignmentX(LEFT_ALIGNMENT);
        lblTopeFecha.setFont(Tema.FUENTE_PEQUENA);
        lblTopeFecha.setForeground(Tema.TEXTO_TENUE);
        lblTopeFecha.setAlignmentX(LEFT_ALIGNMENT);

        detalles.add(linea);
        detalles.add(Box.createVerticalStrut(4));
        detalles.add(lblTopeEquipo);
        detalles.add(Box.createVerticalStrut(4));
        detalles.add(lblTopeDescripcion);
        detalles.add(Box.createVerticalStrut(5));
        detalles.add(lblTopeCriterios);
        detalles.add(Box.createVerticalStrut(3));
        detalles.add(lblTopeFecha);

        JPanel acciones = new JPanel();
        acciones.setOpaque(false);
        acciones.setLayout(new BoxLayout(acciones, BoxLayout.Y_AXIS));
        btnAtender.setAlignmentX(CENTER_ALIGNMENT);
        acciones.add(btnAtender);

        tarjetaTope.add(cabecera, BorderLayout.NORTH);
        tarjetaTope.add(detalles, BorderLayout.CENTER);
        tarjetaTope.add(acciones, BorderLayout.EAST);
        return tarjetaTope;
    }

    private PanelTarjeta construirTabla() {
        PanelTarjeta tarjeta = new PanelTarjeta();
        tarjeta.setLayout(new BorderLayout(0, 10));

        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setOpaque(false);
        JPanel textos = new JPanel();
        textos.setOpaque(false);
        textos.setLayout(new BoxLayout(textos, BoxLayout.Y_AXIS));
        textos.add(Tema.encabezadoSeccion("Órdenes pendientes en la cola"));
        textos.add(Box.createVerticalStrut(2));
        textos.add(Tema.etiquetaPequena("Recorrido del heap sin modificar la estructura original."));
        cabecera.add(textos, BorderLayout.WEST);
        cabecera.add(lblTotal, BorderLayout.EAST);

        JScrollPane scroll = Tema.scroll(tabla);
        scroll.setPreferredSize(new Dimension(100, 280));

        JPanel pie = new JPanel(new BorderLayout(12, 0));
        pie.setOpaque(false);
        JPanel izquierda = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        izquierda.setOpaque(false);
        izquierda.add(lblVacio);
        JPanel derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        derecha.setOpaque(false);
        derecha.add(btnCancelar);
        pie.add(izquierda, BorderLayout.WEST);
        pie.add(derecha, BorderLayout.EAST);

        tarjeta.add(cabecera, BorderLayout.NORTH);
        tarjeta.add(scroll, BorderLayout.CENTER);
        tarjeta.add(pie, BorderLayout.SOUTH);
        return tarjeta;
    }

    private void configurarTabla() {
        Tema.configurarTabla(tabla);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] anchos = {92, 78, 118, 112, 88, 100, 380};
        for (int i = 0; i < anchos.length; i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }
        tabla.getColumnModel().getColumn(0).setCellRenderer(Tema.renderizadorMono(Tema.AMBAR));
        tabla.getColumnModel().getColumn(1).setCellRenderer(Tema.renderizadorMono(Tema.TEXTO_SEC));
        tabla.getColumnModel().getColumn(2).setCellRenderer(Tema.renderizadorMono(Tema.CIAN));
        tabla.getColumnModel().getColumn(4).setCellRenderer(Tema.renderizadorMono(Tema.AMBAR));
        tabla.getColumnModel().getColumn(5).setCellRenderer(Tema.renderizadorCriticidad());

        btnAtender.addActionListener(e -> atenderSiguiente());
        btnCancelar.addActionListener(e -> cancelarSeleccionada());
        tabla.getSelectionModel().addListSelectionListener(e -> actualizarBotones());
    }

    private void configurarEventos() {
        actualizarBotones();
    }

    @Override
    public void recargar() {
        actualizarTope();
        llenarTabla();
    }

    private void llenarTabla() {
        modelo.setFilas(gestor.getOrdenes().recorridoPorPrioridad());
        lblTotal.setText(modelo.getRowCount() + " órdenes en cola");
        lblVacio.setVisible(modelo.getRowCount() == 0);
        actualizarBotones();
    }

    private void actualizarTope() {
        OrdenMantenimiento primera = gestor.getOrdenes().verPrimera();
        if (primera == null) {
            lblTopeOrden.setText("No hay órdenes pendientes");
            lblTopeEquipo.setText("La cola de prioridad está vacía.");
            lblTopeDescripcion.setText("Registre una orden para encolarla en el heap.");
            lblTopeCriterios.setText("verPrimera() O(1) · cola.tamano() = 0");
            lblTopeFecha.setText(" ");
            Tema.aplicarBadge(lblTopeBadge, "SIN PENDIENTES", Tema.TEXTO_TENUE);
            btnAtender.setEnabled(false);
            return;
        }

        Equipo equipo = gestor.getEquipos().buscar(primera.getCodigoEquipo());
        int puntaje = Criticidad.puntaje(primera.getImpacto(), primera.getRiesgoSeguridad(),
                primera.isHayRespaldo());
        lblTopeOrden.setText("OM-" + primera.getId() + " · " + primera.getTipo());
        Tema.aplicarBadge(lblTopeBadge, primera.getCriticidad() + " · " + puntaje + " pts",
                Tema.colorCriticidad(primera.getCriticidad()));
        lblTopeEquipo.setText(primera.getCodigoEquipo()
                + (equipo == null ? "" : " — " + equipo.getNombre()));
        lblTopeDescripcion.setText("\"" + Tema.truncar(primera.getDescripcion(), 140) + "\"");
        lblTopeCriterios.setText("Impacto " + primera.getImpacto() + "/3 · Riesgo "
                + primera.getRiesgoSeguridad() + "/3 · Respaldo "
                + (primera.isHayRespaldo() ? "Sí" : "No (+2 pts)") + " · Criticidad "
                + primera.getCriticidad());
        lblTopeFecha.setText("Creada: " + Tema.fechaHora(primera.getFechaCreacion())
                + " · Consecutivo #" + primera.getConsecutivo());
        btnAtender.setEnabled(!estaOcupado());
    }

    private OrdenMantenimiento seleccionada() {
        int fila = tabla.getSelectedRow();
        return fila < 0 ? null : modelo.getOrden(fila);
    }

    private void actualizarBotones() {
        boolean libre = !estaOcupado();
        btnAtender.setEnabled(libre && gestor.getOrdenes().verPrimera() != null);
        btnCancelar.setEnabled(libre && seleccionada() != null);
    }

    private void atenderSiguiente() {
        if (estaOcupado()) return;
        OrdenMantenimiento primera = gestor.getOrdenes().verPrimera();
        if (primera == null) {
            contexto.mostrarMensaje("No hay órdenes pendientes en la cola.",
                    ContextoApp.Nivel.ADVERTENCIA);
            return;
        }
        int respuesta = JOptionPane.showConfirmDialog(this,
                "¿Atender y cerrar la orden más prioritaria OM-" + primera.getId()
                        + " (" + primera.getCodigoEquipo() + ")?\n"
                        + "Pasará a estado CERRADA en PostgreSQL y saldrá del heap. Podrá deshacerse desde la pila.",
                "Confirmar atención", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (respuesta != JOptionPane.YES_OPTION) return;

        ejecutarOperacion(() -> gestor.atenderSiguiente(),
                "Orden prioritaria atendida y cerrada en PostgreSQL.");
    }

    private void cancelarSeleccionada() {
        if (estaOcupado()) return;
        OrdenMantenimiento orden = seleccionada();
        if (orden == null) return;
        int respuesta = JOptionPane.showConfirmDialog(this,
                "¿Cancelar la orden OM-" + orden.getId() + " (" + orden.getCodigoEquipo() + ")?\n"
                        + "Pasará a estado CANCELADA y saldrá de la cola. Podrá deshacerse desde la pila.",
                "Confirmar cancelación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (respuesta != JOptionPane.YES_OPTION) return;
        ejecutarOperacion(() -> gestor.cancelarOrden(orden.getId()),
                "Orden OM-" + orden.getId() + " cancelada y retirada de la cola.");
    }

    @Override
    protected void alCambiarOcupado(boolean ocupado) {
        progreso.setVisible(ocupado);
        actualizarBotones();
    }

    @Override
    protected void alOperacionExitosa(Object valor) {
        tabla.clearSelection();
    }

    /** Modelo de la tabla de la cola; conserva el orden entregado por ColaPrioridad. */
    private static class ModeloCola extends AbstractTableModel {
        private static final String[] COLUMNAS = {
                "Pos. heap", "ID", "Equipo", "Tipo", "Puntaje", "Criticidad", "Descripción"
        };
        private List<OrdenMantenimiento> filas = new ArrayList<>();

        void setFilas(OrdenMantenimiento[] ordenes) {
            filas = new ArrayList<>();
            for (OrdenMantenimiento orden : ordenes) filas.add(orden);
            fireTableDataChanged();
        }

        OrdenMantenimiento getOrden(int fila) {
            return fila >= 0 && fila < filas.size() ? filas.get(fila) : null;
        }

        @Override public int getRowCount() { return filas.size(); }
        @Override public int getColumnCount() { return COLUMNAS.length; }
        @Override public String getColumnName(int columna) { return COLUMNAS[columna]; }

        @Override
        public Object getValueAt(int fila, int columna) {
            OrdenMantenimiento o = filas.get(fila);
            switch (columna) {
                case 0: return fila == 0 ? "1º · tope" : (fila + 1) + "º";
                case 1: return "OM-" + o.getId();
                case 2: return o.getCodigoEquipo();
                case 3: return o.getTipo();
                case 4: return Criticidad.puntaje(o.getImpacto(), o.getRiesgoSeguridad(), o.isHayRespaldo()) + " pts";
                case 5: return o.getCriticidad().name();
                case 6: return o.getDescripcion();
                default: return "";
            }
        }
    }
}
