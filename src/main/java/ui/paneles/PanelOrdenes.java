package ui.paneles;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;

import app.GestorOrdenes;
import modelo.Criticidad;
import modelo.Equipo;
import modelo.OrdenMantenimiento;
import modelo.OrdenMantenimiento.Estado;
import ui.ContextoApp;
import ui.componentes.PanelTarjeta;
import ui.componentes.Tema;

/**
 * Gestion de ordenes: consulta el historial completo desde PostgreSQL, filtra en memoria
 * los datos ya cargados y delega crear/modificar/cerrar/cancelar en GestorOrdenes.
 */
public class PanelOrdenes extends PanelBase {

    private static final String TODOS = "TODOS";
    private static final String TODAS = "TODAS";

    private final ModeloOrdenes modelo = new ModeloOrdenes();
    private final JTable tabla = new JTable(modelo);
    private final JTextField txtBuscar = new JTextField();
    private final JComboBox<String> cmbEstado =
            new JComboBox<>(new String[]{TODOS, "PENDIENTE", "CERRADA", "CANCELADA"});
    private final JComboBox<String> cmbCriticidad =
            new JComboBox<>(new String[]{TODAS, "ALTA", "MEDIA", "BAJA"});

    private final JButton btnNueva = Tema.botonPrimario("Nueva orden");
    private final JButton btnModificar = Tema.botonSecundario("Modificar");
    private final JButton btnCerrar = Tema.botonSecundario("Cerrar");
    private final JButton btnCancelar = Tema.botonPeligro("Cancelar");
    private final JButton btnDetalle = Tema.botonSecundario("Ver detalle");

    private final JLabel lblConteo = Tema.etiquetaPequena("0 órdenes");
    private final JLabel lblVacio = Tema.etiquetaPequena("No hay órdenes registradas.");
    private final JProgressBar progreso = Tema.barraProgreso();

    private List<OrdenMantenimiento> todas = new ArrayList<>();

    public PanelOrdenes(GestorOrdenes gestor, ContextoApp contexto) {
        super(gestor, contexto);
        setLayout(new BorderLayout(0, 14));

        add(Tema.cabeceraVista("Órdenes de mantenimiento",
                "Registro, consulta y cambio de estado. Los datos provienen de PostgreSQL.", progreso),
                BorderLayout.NORTH);

        JPanel superior = new JPanel();
        superior.setOpaque(false);
        superior.setLayout(new javax.swing.BoxLayout(superior, javax.swing.BoxLayout.Y_AXIS));
        superior.add(construirFiltros());
        superior.add(Box.createVerticalStrut(10));
        superior.add(construirAcciones());

        JPanel cuerpo = new JPanel(new BorderLayout(0, 12));
        cuerpo.setOpaque(false);
        cuerpo.add(superior, BorderLayout.NORTH);
        cuerpo.add(construirTabla(), BorderLayout.CENTER);
        add(cuerpo, BorderLayout.CENTER);

        configurarTabla();
        configurarEventos();
        actualizarBotones();
    }

    private JPanel construirFiltros() {
        PanelTarjeta filtros = new PanelTarjeta();
        filtros.setLayout(new BorderLayout(12, 0));

        JPanel izquierda = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        izquierda.setOpaque(false);

        JLabel lblBuscar = Tema.etiquetaPequena("Buscar");
        txtBuscar.setPreferredSize(new Dimension(250, 34));
        txtBuscar.setFont(Tema.FUENTE);
        Tema.placeholder(txtBuscar, "ID, consecutivo, equipo, tipo o descripción");

        JLabel lblEstado = Tema.etiquetaPequena("Estado");
        cmbEstado.setPreferredSize(new Dimension(132, 34));
        JLabel lblCriticidad = Tema.etiquetaPequena("Criticidad");
        cmbCriticidad.setPreferredSize(new Dimension(112, 34));

        izquierda.add(lblBuscar);
        izquierda.add(txtBuscar);
        izquierda.add(lblEstado);
        izquierda.add(cmbEstado);
        izquierda.add(lblCriticidad);
        izquierda.add(cmbCriticidad);

        JPanel derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        derecha.setOpaque(false);
        derecha.add(lblConteo);
        derecha.add(progreso);

        filtros.add(izquierda, BorderLayout.CENTER);
        filtros.add(derecha, BorderLayout.EAST);
        return filtros;
    }

    private JPanel construirAcciones() {
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        acciones.setOpaque(false);
        acciones.add(btnNueva);
        acciones.add(btnModificar);
        acciones.add(btnCerrar);
        acciones.add(btnCancelar);
        acciones.add(btnDetalle);
        return acciones;
    }

    private PanelTarjeta construirTabla() {
        PanelTarjeta tarjeta = new PanelTarjeta();
        tarjeta.setLayout(new BorderLayout(0, 10));

        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setOpaque(false);
        cabecera.add(Tema.encabezadoSeccion("Historial de órdenes"), BorderLayout.WEST);
        JLabel pista = Tema.etiquetaPequena(
                "Solo las órdenes PENDIENTE se pueden modificar, cerrar o cancelar.");
        cabecera.add(pista, BorderLayout.EAST);

        JScrollPane scroll = Tema.scroll(tabla);
        scroll.setPreferredSize(new Dimension(100, 360));

        tarjeta.add(cabecera, BorderLayout.NORTH);
        tarjeta.add(scroll, BorderLayout.CENTER);
        tarjeta.add(lblVacio, BorderLayout.SOUTH);
        return tarjeta;
    }

    private void configurarTabla() {
        Tema.configurarTabla(tabla);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        int[] anchos = {78, 98, 110, 104, 270, 70, 66, 82, 92, 92, 140};
        for (int i = 0; i < anchos.length; i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }
        tabla.getColumnModel().getColumn(8).setCellRenderer(Tema.renderizadorCriticidad());
        tabla.getColumnModel().getColumn(9).setCellRenderer(Tema.renderizadorEstado());
        tabla.getColumnModel().getColumn(2).setCellRenderer(Tema.renderizadorMono(Tema.CIAN));
        tabla.getColumnModel().getColumn(0).setCellRenderer(Tema.renderizadorMono(Tema.TEXTO_SEC));
    }

    private void configurarEventos() {
        txtBuscar.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { aplicarFiltros(); }
            @Override public void removeUpdate(DocumentEvent e) { aplicarFiltros(); }
            @Override public void changedUpdate(DocumentEvent e) { aplicarFiltros(); }
        });
        cmbEstado.addActionListener(e -> aplicarFiltros());
        cmbCriticidad.addActionListener(e -> aplicarFiltros());
        tabla.getSelectionModel().addListSelectionListener(e -> actualizarBotones());

        btnNueva.addActionListener(e -> abrirFormulario(null));
        btnModificar.addActionListener(e -> {
            OrdenMantenimiento orden = seleccionada();
            if (orden != null) abrirFormulario(orden);
        });
        btnCerrar.addActionListener(e -> cerrarSeleccionada());
        btnCancelar.addActionListener(e -> cancelarSeleccionada());
        btnDetalle.addActionListener(e -> mostrarDetalle());
    }

    @Override
    public void recargar() {
        if (estaOcupado()) return;
        cargar(gestor::consultarTodasLasOrdenes, lista -> {
            todas = lista;
            aplicarFiltros();
        }, "No se pudieron cargar las órdenes");
    }

    private void aplicarFiltros() {
        String busqueda = txtBuscar.getText() == null ? "" : txtBuscar.getText().trim().toLowerCase();
        String estado = (String) cmbEstado.getSelectedItem();
        String criticidad = (String) cmbCriticidad.getSelectedItem();

        List<OrdenMantenimiento> filtradas = new ArrayList<>();
        for (OrdenMantenimiento o : todas) {
            boolean coincideTexto = busqueda.isEmpty()
                    || ("om-" + o.getId()).contains(busqueda)
                    || String.valueOf(o.getConsecutivo()).contains(busqueda)
                    || o.getCodigoEquipo().toLowerCase().contains(busqueda)
                    || o.getTipo().toLowerCase().contains(busqueda)
                    || o.getDescripcion().toLowerCase().contains(busqueda);
            boolean coincideEstado = TODOS.equals(estado) || o.getEstado().name().equals(estado);
            boolean coincideCriticidad = TODAS.equals(criticidad)
                    || o.getCriticidad().name().equals(criticidad);
            if (coincideTexto && coincideEstado && coincideCriticidad) filtradas.add(o);
        }

        modelo.setFilas(filtradas);
        lblConteo.setText(filtradas.size() + " de " + todas.size() + " órdenes");
        lblVacio.setVisible(filtradas.isEmpty());
        lblVacio.setText(todas.isEmpty()
                ? "No hay órdenes registradas todavía."
                : "No hay órdenes que coincidan con los filtros aplicados.");
        actualizarBotones();
    }

    private OrdenMantenimiento seleccionada() {
        int fila = tabla.getSelectedRow();
        return fila < 0 ? null : modelo.getOrden(tabla.convertRowIndexToModel(fila));
    }

    private void actualizarBotones() {
        OrdenMantenimiento orden = seleccionada();
        boolean pendiente = orden != null && orden.getEstado() == Estado.PENDIENTE;
        boolean libre = !estaOcupado();
        btnNueva.setEnabled(libre && !gestor.getEquipos().estaVacio());
        btnModificar.setEnabled(libre && pendiente);
        btnCerrar.setEnabled(libre && pendiente);
        btnCancelar.setEnabled(libre && pendiente);
        btnDetalle.setEnabled(libre && orden != null);
    }

    private void abrirFormulario(OrdenMantenimiento orden) {
        if (estaOcupado()) return;
        Equipo[] equipos = gestor.getEquipos().inOrden();
        if (equipos.length == 0) {
            contexto.mostrarMensaje("Registre primero un equipo en el índice AVL para poder crear órdenes.",
                    ContextoApp.Nivel.ADVERTENCIA);
            return;
        }

        DialogoOrden dialogo = new DialogoOrden(SwingUtilities.getWindowAncestor(this), orden, equipos);
        if (!dialogo.mostrarDialogo()) return;

        DialogoOrden.DatosOrden datos = dialogo.getDatos();
        if (orden == null) {
            ejecutarOperacion(() -> gestor.crearOrden(
                            datos.getCodigoEquipo(), datos.getTipo(), datos.getDescripcion(),
                            datos.getImpacto(), datos.getRiesgoSeguridad(), datos.isHayRespaldo()),
                    "Orden registrada, persistida y encolada correctamente.");
        } else {
            ejecutarOperacion(() -> gestor.modificarOrden(
                            orden.getId(), datos.getTipo(), datos.getDescripcion(),
                            datos.getImpacto(), datos.getRiesgoSeguridad(), datos.isHayRespaldo()),
                    "Orden OM-" + orden.getId() + " modificada y repriorizada.");
        }
    }

    private void cerrarSeleccionada() {
        OrdenMantenimiento orden = seleccionada();
        if (orden == null) return;
        int respuesta = JOptionPane.showConfirmDialog(this,
                "¿Cerrar la orden OM-" + orden.getId() + " (" + orden.getCodigoEquipo() + ")?\n"
                        + "Pasará a estado CERRADA, saldrá de la cola y quedará registrada en la pila para deshacer.",
                "Confirmar cierre", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (respuesta != JOptionPane.YES_OPTION) return;

        ejecutarOperacion(() -> gestor.cerrarOrden(orden.getId()),
                "Orden OM-" + orden.getId() + " cerrada en PostgreSQL y retirada de la cola.");
    }

    private void cancelarSeleccionada() {
        OrdenMantenimiento orden = seleccionada();
        if (orden == null) return;
        int respuesta = JOptionPane.showConfirmDialog(this,
                "¿Cancelar la orden OM-" + orden.getId() + " (" + orden.getCodigoEquipo() + ")?\n"
                        + "Pasará a estado CANCELADA, saldrá de la cola y quedará registrada en la pila para deshacer.",
                "Confirmar cancelación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (respuesta != JOptionPane.YES_OPTION) return;

        ejecutarOperacion(() -> gestor.cancelarOrden(orden.getId()),
                "Orden OM-" + orden.getId() + " cancelada en PostgreSQL y retirada de la cola.");
    }

    private void mostrarDetalle() {
        OrdenMantenimiento orden = seleccionada();
        if (orden == null) return;
        int puntaje = Criticidad.puntaje(orden.getImpacto(), orden.getRiesgoSeguridad(), orden.isHayRespaldo());
        String texto = "ID: OM-" + orden.getId() + "\n"
                + "Consecutivo: #" + orden.getConsecutivo() + "\n"
                + "Equipo: " + orden.getCodigoEquipo() + "\n"
                + "Tipo: " + orden.getTipo() + "\n"
                + "Descripción: " + orden.getDescripcion() + "\n\n"
                + "Impacto en producción: " + orden.getImpacto() + "/3\n"
                + "Riesgo de seguridad: " + orden.getRiesgoSeguridad() + "/3\n"
                + "Respaldo / redundancia: " + (orden.isHayRespaldo() ? "Sí" : "No") + "\n"
                + "Puntaje de criticidad: " + puntaje + "\n"
                + "Nivel: " + orden.getCriticidad() + "\n"
                + "Estado: " + orden.getEstado() + "\n\n"
                + "Fecha de creación: " + Tema.fechaHora(orden.getFechaCreacion()) + "\n"
                + "Fecha de cierre: " + Tema.fechaHora(orden.getFechaCierre());

        JTextArea area = new JTextArea(texto);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBackground(Tema.PANEL);
        area.setForeground(Tema.TEXTO);
        area.setFont(Tema.FUENTE);
        area.setBorder(new EmptyBorder(10, 12, 10, 12));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(440, 320));
        scroll.setBorder(BorderFactory.createLineBorder(Tema.BORDE));

        JOptionPane.showMessageDialog(this, scroll, "Detalle de OM-" + orden.getId(),
                JOptionPane.INFORMATION_MESSAGE);
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

    /** Modelo de tabla que conserva la referencia a la orden de cada fila. */
    private static class ModeloOrdenes extends AbstractTableModel {
        private static final String[] COLUMNAS = {
                "ID", "Consecutivo", "Equipo", "Tipo", "Descripción",
                "Impacto", "Riesgo", "Respaldo", "Criticidad", "Estado", "Creada"
        };
        private List<OrdenMantenimiento> filas = new ArrayList<>();

        void setFilas(List<OrdenMantenimiento> filas) {
            this.filas = new ArrayList<>(filas);
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
                case 0: return "OM-" + o.getId();
                case 1: return "#" + o.getConsecutivo();
                case 2: return o.getCodigoEquipo();
                case 3: return o.getTipo();
                case 4: return o.getDescripcion();
                case 5: return o.getImpacto() + "/3";
                case 6: return o.getRiesgoSeguridad() + "/3";
                case 7: return o.isHayRespaldo() ? "Sí" : "No";
                case 8: return o.getCriticidad().name();
                case 9: return o.getEstado().name();
                case 10: return Tema.fechaHora(o.getFechaCreacion());
                default: return "";
            }
        }
    }
}
