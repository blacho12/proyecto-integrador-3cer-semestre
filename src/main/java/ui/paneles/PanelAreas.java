package ui.paneles;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;

import app.GestorOrdenes;
import modelo.Area;
import ui.ContextoApp;
import ui.componentes.PanelTarjeta;
import ui.componentes.Tema;

/**
 * Gestion completa de areas: crear, consultar, modificar y eliminar.
 * La interfaz solo valida y delega en GestorOrdenes; toda la persistencia y las reglas
 * de integridad (duplicados y equipos asociados) viven en la capa de aplicacion y PostgreSQL.
 */
public class PanelAreas extends PanelBase {

    private final ModeloAreas modelo = new ModeloAreas();
    private final JTable tabla = new JTable(modelo);

    private final JTextField txtNombre = new JTextField();
    private final JTextField txtDescripcion = new JTextField();
    private final JLabel lblSeleccion = Tema.etiquetaPequena("Ninguna área seleccionada.");
    private final JLabel lblResumen = Tema.etiquetaPequena(" ");
    private final JButton btnNuevo = Tema.botonSecundario("Nuevo / limpiar");
    private final JButton btnAgregar = Tema.botonPrimario("Agregar área");
    private final JButton btnModificar = Tema.botonSecundario("Modificar área");
    private final JButton btnEliminar = Tema.botonPeligro("Eliminar área");
    private final JProgressBar progreso = Tema.barraProgreso();

    private Integer idSeleccionado;

    public PanelAreas(GestorOrdenes gestor, ContextoApp contexto) {
        super(gestor, contexto);
        setLayout(new BorderLayout(0, 14));
        add(Tema.cabeceraVista("Gestión de áreas",
                "CRUD completo sobre PostgreSQL: los cambios se confirman antes de refrescar la tabla.",
                progreso), BorderLayout.NORTH);

        JPanel centro = new JPanel(new BorderLayout(14, 0));
        centro.setOpaque(false);
        centro.add(construirTabla(), BorderLayout.CENTER);
        centro.add(construirFormulario(), BorderLayout.EAST);
        add(centro, BorderLayout.CENTER);

        configurarTabla();
        configurarEventos();
        actualizarBotones();
    }

    private PanelTarjeta construirTabla() {
        PanelTarjeta tarjeta = new PanelTarjeta();
        tarjeta.setLayout(new BorderLayout(0, 10));

        JPanel cabecera = new JPanel(new BorderLayout(12, 0));
        cabecera.setOpaque(false);
        cabecera.add(Tema.encabezadoSeccion("Áreas registradas"), BorderLayout.WEST);
        cabecera.add(lblResumen, BorderLayout.EAST);

        tarjeta.add(cabecera, BorderLayout.NORTH);
        tarjeta.add(Tema.scroll(tabla), BorderLayout.CENTER);
        tarjeta.add(Tema.etiquetaPequena(
                "Seleccione una fila para cargar sus datos en el formulario."), BorderLayout.SOUTH);
        return tarjeta;
    }

    private PanelTarjeta construirFormulario() {
        PanelTarjeta tarjeta = new PanelTarjeta();
        tarjeta.setLayout(new BorderLayout(0, 12));
        tarjeta.setPreferredSize(new Dimension(390, 100));

        JPanel cabecera = new JPanel();
        cabecera.setOpaque(false);
        cabecera.setLayout(new BoxLayout(cabecera, BoxLayout.Y_AXIS));
        cabecera.add(Tema.encabezadoSeccion("Datos del área"));
        cabecera.add(Box.createVerticalStrut(3));
        JLabel subtitulo = Tema.etiquetaPequena(
                "El nombre es obligatorio y no puede repetirse (sin distinguir mayúsculas).");
        subtitulo.setAlignmentX(LEFT_ALIGNMENT);
        cabecera.add(subtitulo);

        Tema.placeholder(txtNombre, "Producción");
        Tema.placeholder(txtDescripcion, "Zona de envasado y empaque");
        txtNombre.setFont(Tema.FUENTE);
        txtDescripcion.setFont(Tema.FUENTE);

        JPanel campos = new JPanel();
        campos.setOpaque(false);
        campos.setLayout(new BoxLayout(campos, BoxLayout.Y_AXIS));
        campos.add(Tema.campo("Nombre (obligatorio, máx. 100)", txtNombre));
        campos.add(Box.createVerticalStrut(10));
        campos.add(Tema.campo("Descripción (opcional, máx. 250)", txtDescripcion));
        campos.add(Box.createVerticalStrut(8));
        lblSeleccion.setAlignmentX(LEFT_ALIGNMENT);
        campos.add(lblSeleccion);
        campos.add(Box.createVerticalStrut(14));

        JPanel botones = new JPanel();
        botones.setOpaque(false);
        botones.setLayout(new BoxLayout(botones, BoxLayout.Y_AXIS));
        btnAgregar.setAlignmentX(LEFT_ALIGNMENT);
        btnModificar.setAlignmentX(LEFT_ALIGNMENT);
        btnEliminar.setAlignmentX(LEFT_ALIGNMENT);
        btnNuevo.setAlignmentX(LEFT_ALIGNMENT);
        botones.add(btnAgregar);
        botones.add(Box.createVerticalStrut(7));
        botones.add(btnModificar);
        botones.add(Box.createVerticalStrut(7));
        botones.add(btnEliminar);
        botones.add(Box.createVerticalStrut(7));
        botones.add(btnNuevo);
        campos.add(botones);
        campos.add(Box.createVerticalStrut(14));

        JLabel nota = new JLabel("<html><body style='width:320px'>"
                + "No se elimina un área con equipos asociados. Primero reasigne o elimine "
                + "esos equipos: no existe borrado en cascada y se conserva el historial "
                + "de mantenimiento.</body></html>");
        nota.setFont(Tema.FUENTE_PEQUENA);
        nota.setForeground(Tema.TEXTO_TENUE);
        nota.setAlignmentX(LEFT_ALIGNMENT);
        campos.add(nota);

        tarjeta.add(cabecera, BorderLayout.NORTH);
        tarjeta.add(campos, BorderLayout.CENTER);
        return tarjeta;
    }

    private void configurarTabla() {
        Tema.configurarTabla(tabla);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        tabla.getColumnModel().getColumn(0).setPreferredWidth(50);
        tabla.getColumnModel().getColumn(1).setPreferredWidth(230);
        tabla.getColumnModel().getColumn(2).setPreferredWidth(420);
        tabla.getColumnModel().getColumn(3).setPreferredWidth(80);
        tabla.getColumnModel().getColumn(0).setCellRenderer(Tema.renderizadorMono(Tema.CIAN));
    }

    private void configurarEventos() {
        tabla.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) cargarSeleccion();
        });
        btnAgregar.addActionListener(e -> agregar());
        btnModificar.addActionListener(e -> modificar());
        btnEliminar.addActionListener(e -> eliminar());
        btnNuevo.addActionListener(e -> limpiarFormulario());
    }

    @Override
    public void recargar() {
        if (estaOcupado()) return;
        cargar(() -> new Datos(gestor.consultarAreas(), gestor.contarEquiposPorArea()),
                this::aplicar, "No se pudieron cargar las áreas desde PostgreSQL");
    }

    private void aplicar(Datos datos) {
        modelo.setFilas(datos.areas, datos.equiposPorArea);
        lblResumen.setText(datos.areas.isEmpty()
                ? "Sin áreas registradas"
                : datos.areas.size() + " área(s) en PostgreSQL");
        limpiarFormulario();
    }

    private void cargarSeleccion() {
        int fila = tabla.getSelectedRow();
        if (fila < 0 || fila >= modelo.getRowCount()) {
            limpiarFormulario();
            return;
        }
        Area area = modelo.getArea(fila);
        idSeleccionado = area.getIdArea();
        txtNombre.setText(area.getNombre());
        txtDescripcion.setText(area.getDescripcion() == null ? "" : area.getDescripcion());
        lblSeleccion.setText("Seleccionada: " + area.getNombre() + " · "
                + modelo.getEquipos(area.getIdArea()) + " equipo(s) asociado(s)");
        actualizarBotones();
    }

    private void agregar() {
        if (estaOcupado() || !validarFormulario()) return;
        String nombre = txtNombre.getText().trim();
        String descripcion = descripcionNormalizada();
        ejecutarOperacion(() -> gestor.registrarArea(nombre, descripcion),
                "Área «" + nombre + "» registrada en PostgreSQL.");
    }

    private void modificar() {
        if (estaOcupado()) return;
        if (idSeleccionado == null) {
            aviso("Seleccione un área de la tabla para modificarla.");
            return;
        }
        if (!validarFormulario()) return;
        final int id = idSeleccionado;
        String nombre = txtNombre.getText().trim();
        String descripcion = descripcionNormalizada();
        ejecutarOperacion(() -> gestor.modificarArea(id, nombre, descripcion),
                "Área «" + nombre + "» actualizada en PostgreSQL.");
    }

    private void eliminar() {
        if (estaOcupado()) return;
        int fila = tabla.getSelectedRow();
        if (fila < 0 || fila >= modelo.getRowCount()) {
            aviso("Seleccione el área que desea eliminar.");
            return;
        }

        Area area = modelo.getArea(fila);
        int equipos = modelo.getEquipos(area.getIdArea());
        if (equipos > 0) {
            JOptionPane.showMessageDialog(this,
                    "No se puede eliminar el área «" + area.getNombre() + "».\n\n"
                            + "Tiene " + equipos + " equipo(s) asociado(s). Reasigne o elimine "
                            + "primero esos equipos.\nNo se realiza borrado en cascada para no "
                            + "perder información histórica.",
                    "Área con equipos asociados", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int respuesta = JOptionPane.showConfirmDialog(this,
                "¿Eliminar definitivamente el área «" + area.getNombre() + "»?\n\n"
                        + "Esta acción no se puede deshacer.",
                "Confirmar eliminación", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (respuesta != JOptionPane.YES_OPTION) return;

        ejecutarOperacion(() -> gestor.eliminarArea(area.getIdArea()),
                "Área «" + area.getNombre() + "» eliminada de PostgreSQL.");
    }

    private boolean validarFormulario() {
        String nombre = txtNombre.getText() == null ? "" : txtNombre.getText().trim();
        if (nombre.isEmpty()) {
            aviso("El nombre del área es obligatorio.");
            txtNombre.requestFocusInWindow();
            return false;
        }
        if (nombre.length() > 100) {
            aviso("El nombre no puede exceder 100 caracteres (VARCHAR(100) en PostgreSQL).");
            return false;
        }
        String descripcion = txtDescripcion.getText() == null ? "" : txtDescripcion.getText().trim();
        if (descripcion.length() > 250) {
            aviso("La descripción no puede exceder 250 caracteres (VARCHAR(250) en PostgreSQL).");
            return false;
        }
        return true;
    }

    private String descripcionNormalizada() {
        String descripcion = txtDescripcion.getText() == null ? "" : txtDescripcion.getText().trim();
        return descripcion.isEmpty() ? null : descripcion;
    }

    private void limpiarFormulario() {
        idSeleccionado = null;
        txtNombre.setText("");
        txtDescripcion.setText("");
        lblSeleccion.setText("Ninguna área seleccionada.");
        tabla.clearSelection();
        actualizarBotones();
    }

    private void aviso(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Datos incompletos",
                JOptionPane.WARNING_MESSAGE);
    }

    private void actualizarBotones() {
        boolean libre = !estaOcupado();
        btnAgregar.setEnabled(libre);
        btnNuevo.setEnabled(libre);
        btnModificar.setEnabled(libre && idSeleccionado != null);
        btnEliminar.setEnabled(libre && idSeleccionado != null);
    }

    @Override
    protected void alCambiarOcupado(boolean ocupado) {
        progreso.setVisible(ocupado);
        actualizarBotones();
    }

    @Override
    protected void alOperacionExitosa(Object valor) {
        limpiarFormulario();
    }

    /** Datos de la vista cargados desde PostgreSQL. */
    private static class Datos {
        final List<Area> areas;
        final Map<Integer, Integer> equiposPorArea;

        Datos(List<Area> areas, Map<Integer, Integer> equiposPorArea) {
            this.areas = areas;
            this.equiposPorArea = equiposPorArea;
        }
    }

    /** Modelo de la tabla de areas con el conteo de equipos asociados. */
    private static class ModeloAreas extends AbstractTableModel {
        private static final String[] COLUMNAS = {"ID", "Nombre", "Descripción", "Equipos"};
        private List<Area> filas = new ArrayList<>();
        private Map<Integer, Integer> equipos = new HashMap<>();

        void setFilas(List<Area> areas, Map<Integer, Integer> equiposPorArea) {
            this.filas = new ArrayList<>(areas);
            this.equipos = new HashMap<>(equiposPorArea);
            fireTableDataChanged();
        }

        Area getArea(int fila) {
            return fila >= 0 && fila < filas.size() ? filas.get(fila) : null;
        }

        int getEquipos(int idArea) {
            return equipos.getOrDefault(idArea, 0);
        }

        @Override public int getRowCount() { return filas.size(); }
        @Override public int getColumnCount() { return COLUMNAS.length; }
        @Override public String getColumnName(int columna) { return COLUMNAS[columna]; }

        @Override
        public Object getValueAt(int fila, int columna) {
            Area area = filas.get(fila);
            switch (columna) {
                case 0: return String.valueOf(area.getIdArea());
                case 1: return area.getNombre();
                case 2: return area.getDescripcion() == null ? "—" : area.getDescripcion();
                case 3: return getEquipos(area.getIdArea());
                default: return "";
            }
        }
    }
}
