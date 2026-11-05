package ui.paneles;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;

import app.GestorOrdenes;
import modelo.Area;
import modelo.Equipo;
import ui.ContextoApp;
import ui.componentes.PanelArbolAVL;
import ui.componentes.PanelTarjeta;
import ui.componentes.Tema;

/**
 * Equipos e indice AVL: la tabla y el dibujo se alimentan del AVL real, la busqueda usa
 * ArbolAVL.buscar y el registro se delega en GestorOrdenes.registrarEquipo.
 */
public class PanelEquipos extends PanelBase {

    private final ModeloEquipos modelo = new ModeloEquipos();
    private final JTable tabla = new JTable(modelo);
    private final PanelArbolAVL panelArbol;

    private final JTextField txtBuscar = new JTextField();
    private final JTextField txtCodigo = new JTextField();
    private final JTextField txtNombre = new JTextField();
    private final JComboBox<Area> cmbArea = new JComboBox<>();
    private final JLabel lblBusqueda = Tema.etiquetaPequena(" ");
    private final JLabel lblAltura = new JLabel("0");
    private final JLabel lblTotal = new JLabel("0");
    private final JLabel lblTituloForm = Tema.encabezadoSeccion("Registrar nuevo equipo");
    private final JLabel lblSubtituloForm = Tema.etiquetaPequena(
            "Se inserta en PostgreSQL y luego en el AVL con las rotaciones necesarias.");
    private final JLabel lblNotaForm = Tema.etiquetaPequena(
            "El código se guarda en mayúsculas. Si ya existe, PostgreSQL rechaza el registro.");
    private final JButton btnBuscar = Tema.botonSecundario("Buscar");
    private final JButton btnRegistrar = Tema.botonPrimario("Insertar equipo en el AVL");
    private final JButton btnGuardar = Tema.botonSecundario("Guardar");
    private final JButton btnEliminar = Tema.botonPeligro("Eliminar");
    private final JButton btnLimpiar = Tema.botonSecundario("Cancelar");
    private final JButton btnNuevaArea = Tema.botonSecundario("Nueva área");
    private final JProgressBar progreso = Tema.barraProgreso();

    private final Map<Integer, String> nombresArea = new HashMap<>();
    private List<Area> areas = new ArrayList<>();

    /** Codigo del equipo seleccionado en la tabla; null cuando el formulario crea uno nuevo. */
    private String codigoEnEdicion;

    public PanelEquipos(GestorOrdenes gestor, ContextoApp contexto) {
        super(gestor, contexto);
        this.panelArbol = new PanelArbolAVL(gestor.getEquipos());

        setLayout(new BorderLayout(0, 14));
        add(Tema.cabeceraVista("Equipos e índice AVL",
                "Búsqueda O(log n), inserción autobalanceada y recorrido inorden del árbol real.",
                progreso), BorderLayout.NORTH);

        JPanel contenido = new JPanel(new BorderLayout(0, 14));
        contenido.setOpaque(false);
        contenido.add(construirZonaSuperior(), BorderLayout.CENTER);
        contenido.add(construirTablaEquipos(), BorderLayout.SOUTH);
        add(contenido, BorderLayout.CENTER);

        configurarTabla();
        configurarEventos();
        actualizarModo();
        actualizarEquiposMemoria();
    }

    private JPanel construirZonaSuperior() {
        JPanel zona = new JPanel(new BorderLayout(14, 0));
        zona.setOpaque(false);
        zona.add(construirTarjetaArbol(), BorderLayout.CENTER);
        zona.add(construirFormulario(), BorderLayout.EAST);
        return zona;
    }

    private PanelTarjeta construirTarjetaArbol() {
        PanelTarjeta tarjeta = new PanelTarjeta();
        tarjeta.setLayout(new BorderLayout(0, 10));

        JPanel cabecera = new JPanel(new BorderLayout(12, 0));
        cabecera.setOpaque(false);
        JPanel textos = new JPanel();
        textos.setOpaque(false);
        textos.setLayout(new BoxLayout(textos, BoxLayout.Y_AXIS));
        textos.add(Tema.encabezadoSeccion("Índice de equipos: árbol AVL"));
        textos.add(Box.createVerticalStrut(2));
        textos.add(Tema.etiquetaPequena("Cada nodo muestra su altura (h) y factor de balance (fb)."));

        JPanel busqueda = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        busqueda.setOpaque(false);
        txtBuscar.setPreferredSize(new Dimension(200, 34));
        Tema.placeholder(txtBuscar, "Código, ej. EQ-001");
        txtBuscar.setFont(Tema.MONO);
        busqueda.add(txtBuscar);
        busqueda.add(btnBuscar);

        cabecera.add(textos, BorderLayout.WEST);
        cabecera.add(busqueda, BorderLayout.EAST);

        JPanel resultados = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        resultados.setOpaque(false);
        resultados.add(lblBusqueda);

        JPanel centro = new JPanel(new BorderLayout(0, 6));
        centro.setOpaque(false);
        centro.add(panelArbol, BorderLayout.CENTER);
        centro.add(resultados, BorderLayout.SOUTH);

        JPanel pie = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 0));
        pie.setOpaque(false);
        lblAltura.setFont(Tema.MONO_BOLD);
        lblAltura.setForeground(Tema.CIAN);
        lblTotal.setFont(Tema.MONO_BOLD);
        lblTotal.setForeground(Tema.TEXTO);
        JLabel lblBalance = Tema.etiquetaPequena("Autobalanceado: |fb| ≤ 1");
        pie.add(etiquetaConValor("Altura del árbol:", lblAltura));
        pie.add(etiquetaConValor("Equipos indexados:", lblTotal));
        pie.add(lblBalance);

        tarjeta.add(cabecera, BorderLayout.NORTH);
        tarjeta.add(centro, BorderLayout.CENTER);
        tarjeta.add(pie, BorderLayout.SOUTH);
        tarjeta.setPreferredSize(new Dimension(100, 360));
        return tarjeta;
    }

    private JPanel etiquetaConValor(String texto, JLabel valor) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        panel.setOpaque(false);
        panel.add(Tema.etiquetaPequena(texto));
        panel.add(valor);
        return panel;
    }

    private PanelTarjeta construirFormulario() {
        PanelTarjeta tarjeta = new PanelTarjeta();
        tarjeta.setLayout(new BorderLayout(0, 10));
        tarjeta.setPreferredSize(new Dimension(348, 360));

        JPanel cabecera = new JPanel();
        cabecera.setOpaque(false);
        cabecera.setLayout(new BoxLayout(cabecera, BoxLayout.Y_AXIS));
        cabecera.add(lblTituloForm);
        cabecera.add(Box.createVerticalStrut(3));
        cabecera.add(lblSubtituloForm);

        txtCodigo.setFont(Tema.MONO);
        Tema.placeholder(txtCodigo, "EQ-008");
        txtNombre.setFont(Tema.FUENTE);
        Tema.placeholder(txtNombre, "Compresor auxiliar B");

        JPanel formulario = new JPanel();
        formulario.setOpaque(false);
        formulario.setLayout(new BoxLayout(formulario, BoxLayout.Y_AXIS));
        formulario.add(Tema.campo("Código único (clave primaria, máx. 30)", txtCodigo));
        formulario.add(Box.createVerticalStrut(10));
        formulario.add(Tema.campo("Nombre del equipo (máx. 120)", txtNombre));
        formulario.add(Box.createVerticalStrut(10));

        JPanel filaArea = new JPanel(new BorderLayout(8, 0));
        filaArea.setOpaque(false);
        filaArea.add(Tema.campo("Área industrial", cmbArea), BorderLayout.CENTER);
        btnNuevaArea.setPreferredSize(new Dimension(110, 34));
        JPanel envoltorioBoton = new JPanel(new BorderLayout());
        envoltorioBoton.setOpaque(false);
        envoltorioBoton.setBorder(new EmptyBorder(17, 6, 0, 0));
        envoltorioBoton.add(btnNuevaArea, BorderLayout.NORTH);
        filaArea.add(envoltorioBoton, BorderLayout.EAST);
        formulario.add(filaArea);
        formulario.add(Box.createVerticalStrut(16));

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        acciones.setOpaque(false);
        acciones.setAlignmentX(LEFT_ALIGNMENT);
        acciones.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        acciones.add(btnRegistrar);
        acciones.add(btnGuardar);
        acciones.add(btnEliminar);
        acciones.add(btnLimpiar);
        formulario.add(acciones);
        formulario.add(Box.createVerticalStrut(8));

        lblNotaForm.setAlignmentX(LEFT_ALIGNMENT);
        formulario.add(lblNotaForm);

        tarjeta.add(cabecera, BorderLayout.NORTH);
        tarjeta.add(formulario, BorderLayout.CENTER);
        return tarjeta;
    }

    private PanelTarjeta construirTablaEquipos() {
        PanelTarjeta tarjeta = new PanelTarjeta();
        tarjeta.setLayout(new BorderLayout(0, 10));
        tarjeta.setPreferredSize(new Dimension(100, 220));

        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setOpaque(false);
        cabecera.add(Tema.encabezadoSeccion("Catálogo de equipos (recorrido inorden)"), BorderLayout.WEST);
        JLabel pista = Tema.etiquetaPequena(
                "Orden ascendente · seleccione una fila para editar o eliminar.");
        cabecera.add(pista, BorderLayout.EAST);

        JScrollPane scroll = Tema.scroll(tabla);
        tarjeta.add(cabecera, BorderLayout.NORTH);
        tarjeta.add(scroll, BorderLayout.CENTER);
        return tarjeta;
    }

    private void configurarTabla() {
        Tema.configurarTabla(tabla);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        tabla.getColumnModel().getColumn(0).setPreferredWidth(110);
        tabla.getColumnModel().getColumn(1).setPreferredWidth(340);
        tabla.getColumnModel().getColumn(2).setPreferredWidth(280);
        tabla.getColumnModel().getColumn(3).setPreferredWidth(80);
        tabla.getColumnModel().getColumn(0).setCellRenderer(Tema.renderizadorMono(Tema.CIAN));
    }

    private void configurarEventos() {
        btnBuscar.addActionListener(e -> buscarEquipo());
        txtBuscar.addActionListener(e -> buscarEquipo());
        btnRegistrar.addActionListener(e -> registrarEquipo());
        btnGuardar.addActionListener(e -> guardarCambios());
        btnEliminar.addActionListener(e -> eliminarEquipoSeleccionado());
        btnLimpiar.addActionListener(e -> limpiarFormulario());
        btnNuevaArea.addActionListener(e -> registrarArea());
        tabla.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            Equipo equipo = modelo.getEquipo(tabla.getSelectedRow());
            if (equipo != null) cargarEnFormulario(equipo);
        });
        actualizarBotones();
    }

    @Override
    public void recargar() {
        actualizarEquiposMemoria();
        cargar(gestor::consultarAreas, lista -> {
            areas = lista;
            nombresArea.clear();
            cmbArea.removeAllItems();
            for (Area area : lista) {
                cmbArea.addItem(area);
                nombresArea.put(area.getIdArea(), area.getNombre());
            }
            if (lista.isEmpty()) {
                lblBusqueda.setText("No hay áreas registradas; cree un área antes de registrar equipos.");
                lblBusqueda.setForeground(Tema.AMBAR);
            }
            actualizarEquiposMemoria();
            // El equipo en edicion pudo eliminarse desde otra vista: el formulario vuelve a NUEVO.
            if (codigoEnEdicion != null && gestor.getEquipos().buscar(codigoEnEdicion) == null) {
                limpiarFormulario();
            } else {
                actualizarBotones();
            }
        }, "No se pudieron cargar las áreas desde PostgreSQL");
    }

    private void actualizarEquiposMemoria() {
        Equipo[] equipos = gestor.getEquipos().inOrden();
        modelo.setFilas(equipos, nombresArea);
        panelArbol.actualizar();
        lblAltura.setText(String.valueOf(gestor.getEquipos().altura()));
        lblTotal.setText(String.valueOf(gestor.getEquipos().tamano()));
        actualizarBotones();
    }

    private void actualizarBotones() {
        boolean libre = !estaOcupado();
        boolean editando = codigoEnEdicion != null;
        btnRegistrar.setEnabled(libre && !editando && !areas.isEmpty());
        btnGuardar.setEnabled(libre && editando && !areas.isEmpty());
        btnEliminar.setEnabled(libre && editando);
        btnLimpiar.setEnabled(libre && editando);
        btnNuevaArea.setEnabled(libre);
        btnBuscar.setEnabled(libre);
    }

    /** Carga en el formulario el equipo seleccionado y pasa a modo edicion. */
    private void cargarEnFormulario(Equipo equipo) {
        codigoEnEdicion = equipo.getCodigo();
        txtCodigo.setText(equipo.getCodigo());
        txtCodigo.setEditable(false);
        txtNombre.setText(equipo.getNombre());
        seleccionarArea(equipo.getIdArea());
        actualizarModo();
    }

    private void seleccionarArea(int idArea) {
        for (int i = 0; i < cmbArea.getItemCount(); i++) {
            Area area = cmbArea.getItemAt(i);
            if (area != null && area.getIdArea() == idArea) {
                cmbArea.setSelectedIndex(i);
                return;
            }
        }
    }

    /** Vuelve al modo de alta y deja los campos vacios. */
    private void limpiarFormulario() {
        codigoEnEdicion = null;
        txtCodigo.setEditable(true);
        txtCodigo.setText("");
        txtNombre.setText("");
        tabla.clearSelection();
        actualizarModo();
    }

    /** Ajusta titulos, nota y botones visibles segun haya o no un equipo seleccionado. */
    private void actualizarModo() {
        boolean editando = codigoEnEdicion != null;
        lblTituloForm.setText(editando ? "Editar equipo " + codigoEnEdicion : "Registrar nuevo equipo");
        lblSubtituloForm.setText(editando
                ? "Cambie el nombre o el área y guarde; el código es la clave y no se modifica."
                : "Se inserta en PostgreSQL y luego en el AVL con las rotaciones necesarias.");
        lblNotaForm.setText(editando
                ? "Eliminar borra también sus órdenes asociadas, previa confirmación."
                : "El código se guarda en mayúsculas. Si ya existe, PostgreSQL rechaza el registro.");
        btnRegistrar.setVisible(!editando);
        btnGuardar.setVisible(editando);
        btnEliminar.setVisible(editando);
        btnLimpiar.setVisible(editando);
        actualizarBotones();
    }

    private void buscarEquipo() {
        String codigo = txtBuscar.getText() == null ? "" : txtBuscar.getText().trim().toUpperCase();
        if (codigo.isEmpty()) {
            contexto.mostrarMensaje("Escriba un código para buscar en el índice AVL.",
                    ContextoApp.Nivel.ADVERTENCIA);
            txtBuscar.requestFocusInWindow();
            return;
        }
        Equipo equipo = gestor.getEquipos().buscar(codigo);
        if (equipo == null) {
            lblBusqueda.setText("El equipo " + codigo + " no existe en el índice AVL.");
            lblBusqueda.setForeground(Tema.ROJO);
            contexto.mostrarMensaje("Búsqueda AVL: no se encontró " + codigo + ".",
                    ContextoApp.Nivel.ADVERTENCIA);
            return;
        }

        String area = nombresArea.getOrDefault(equipo.getIdArea(), "Área " + equipo.getIdArea());
        lblBusqueda.setText("Encontrado O(log n): " + equipo.getCodigo() + " — "
                + equipo.getNombre() + " · " + area);
        lblBusqueda.setForeground(Tema.VERDE);
        int fila = modelo.buscarFila(equipo.getCodigo());
        if (fila >= 0) {
            tabla.setRowSelectionInterval(fila, fila);
            tabla.scrollRectToVisible(tabla.getCellRect(fila, 0, true));
        }
        contexto.mostrarMensaje("Equipo " + equipo.getCodigo() + " encontrado en el AVL.",
                ContextoApp.Nivel.INFO);
    }

    private void registrarEquipo() {
        if (estaOcupado()) return;
        Area area = validarFormulario();
        if (area == null) return;
        final String codigo = codigoEscrito();
        final String nombre = nombreEscrito();
        ejecutarOperacion(() -> gestor.registrarEquipo(
                        new Equipo(codigo, nombre, area.getIdArea())),
                "Equipo " + codigo + " insertado en PostgreSQL y en el índice AVL.");
    }

    /** Actualiza nombre y area del equipo seleccionado; el codigo no se puede cambiar. */
    private void guardarCambios() {
        if (estaOcupado() || codigoEnEdicion == null) return;
        Area area = validarFormulario();
        if (area == null) return;
        final String codigo = codigoEnEdicion;
        final String nombre = nombreEscrito();
        ejecutarOperacion(() -> gestor.modificarEquipo(codigo, nombre, area.getIdArea()),
                "Equipo " + codigo + " actualizado en PostgreSQL y en el índice AVL.");
    }

    /**
     * Elimina el equipo seleccionado. Primero cuenta sus ordenes para avisar exactamente que
     * se borrara: la clave foranea ON DELETE RESTRICT impide borrar un equipo con ordenes.
     */
    private void eliminarEquipoSeleccionado() {
        if (estaOcupado() || codigoEnEdicion == null) return;
        final String codigo = codigoEnEdicion;
        cargar(() -> gestor.contarOrdenesDeEquipo(codigo), total -> {
            String detalle = total == 0
                    ? "Se borra de PostgreSQL y del índice AVL. Esta acción no se puede deshacer."
                    : "Tiene " + total + (total == 1 ? " orden asociada" : " órdenes asociadas")
                      + ", así que también se " + (total == 1 ? "eliminará esa orden" : "eliminarán esas órdenes")
                      + " (ON DELETE RESTRICT). Esta acción no se puede deshacer.";
            int respuesta = JOptionPane.showConfirmDialog(this,
                    "¿Eliminar el equipo " + codigo + "?\n\n" + detalle,
                    "Confirmar eliminación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (respuesta != JOptionPane.YES_OPTION) return;
            // Se aplaza para que la operacion arranque con el panel ya libre (ver PanelBase.cargar).
            SwingUtilities.invokeLater(() -> ejecutarOperacion(
                    () -> gestor.eliminarEquipo(codigo, total > 0),
                    "Equipo " + codigo + " eliminado"
                    + (total > 0 ? " junto con " + total + " orden(es)" : "")
                    + " de PostgreSQL y del índice AVL."));
        }, "No se pudieron consultar las órdenes del equipo");
    }

    /** Valida los campos del formulario y devuelve el area elegida, o null si hay un error. */
    private Area validarFormulario() {
        String codigo = codigoEscrito();
        String nombre = nombreEscrito();
        Area area = (Area) cmbArea.getSelectedItem();

        if (codigo.isEmpty()) {
            aviso("El código del equipo es obligatorio.");
            return null;
        }
        if (codigo.length() > 30) {
            aviso("El código no puede exceder 30 caracteres (restricción VARCHAR(30) de PostgreSQL).");
            return null;
        }
        if (nombre.isEmpty()) {
            aviso("El nombre del equipo es obligatorio.");
            return null;
        }
        if (nombre.length() > 120) {
            aviso("El nombre no puede exceder 120 caracteres (restricción VARCHAR(120) de PostgreSQL).");
            return null;
        }
        if (area == null) {
            aviso("Seleccione un área industrial. Si no hay ninguna, cree un área primero.");
            return null;
        }
        return area;
    }

    private String codigoEscrito() {
        return txtCodigo.getText() == null ? "" : txtCodigo.getText().trim().toUpperCase();
    }

    private String nombreEscrito() {
        return txtNombre.getText() == null ? "" : txtNombre.getText().trim();
    }

    private void registrarArea() {
        if (estaOcupado()) return;
        String nombre = JOptionPane.showInputDialog(this,
                "Nombre del área (máx. 100 caracteres):", "Nueva área", JOptionPane.QUESTION_MESSAGE);
        if (nombre == null) return;
        nombre = nombre.trim();
        if (nombre.isEmpty()) {
            aviso("El nombre del área es obligatorio.");
            return;
        }
        if (nombre.length() > 100) {
            aviso("El nombre del área no puede exceder 100 caracteres.");
            return;
        }

        String descripcion = JOptionPane.showInputDialog(this,
                "Descripción del área (opcional, máx. 250 caracteres):", "Nueva área",
                JOptionPane.QUESTION_MESSAGE);
        if (descripcion == null) descripcion = "";
        descripcion = descripcion.trim();
        if (descripcion.length() > 250) {
            aviso("La descripción no puede exceder 250 caracteres.");
            return;
        }

        final String nombreFinal = nombre;
        final String descripcionFinal = descripcion.isEmpty() ? null : descripcion;
        ejecutarOperacion(() -> gestor.registrarArea(nombreFinal, descripcionFinal),
                "Área «" + nombreFinal + "» registrada en PostgreSQL.");
    }

    private void aviso(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Datos incompletos", JOptionPane.WARNING_MESSAGE);
    }

    @Override
    protected void alCambiarOcupado(boolean ocupado) {
        progreso.setVisible(ocupado);
        actualizarBotones();
    }

    @Override
    protected void alOperacionExitosa(Object valor) {
        limpiarFormulario();
        txtCodigo.requestFocusInWindow();
        SwingUtilities.invokeLater(() -> {
            if (cmbArea.getItemCount() > 0) cmbArea.setSelectedIndex(0);
        });
    }

    @Override
    protected void alErrorOperacion() {
        // Los datos se conservan para corregirlos; solo se restaura el modo del formulario.
        actualizarModo();
    }

    /** Modelo de la tabla de equipos; el orden proviene del recorrido inorden del AVL. */
    private static class ModeloEquipos extends AbstractTableModel {
        private static final String[] COLUMNAS = {"Código", "Nombre", "Área", "ID área"};
        private Equipo[] filas = new Equipo[0];
        private Map<Integer, String> areas = new HashMap<>();

        void setFilas(Equipo[] filas, Map<Integer, String> areas) {
            this.filas = filas;
            this.areas = new HashMap<>(areas);
            fireTableDataChanged();
        }

        Equipo getEquipo(int fila) {
            return fila >= 0 && fila < filas.length ? filas[fila] : null;
        }

        int buscarFila(String codigo) {
            for (int i = 0; i < filas.length; i++) {
                if (filas[i].getCodigo().equals(codigo)) return i;
            }
            return -1;
        }

        @Override public int getRowCount() { return filas.length; }
        @Override public int getColumnCount() { return COLUMNAS.length; }
        @Override public String getColumnName(int columna) { return COLUMNAS[columna]; }

        @Override
        public Object getValueAt(int fila, int columna) {
            Equipo e = filas[fila];
            switch (columna) {
                case 0: return e.getCodigo();
                case 1: return e.getNombre();
                case 2: return areas.getOrDefault(e.getIdArea(), "Área " + e.getIdArea());
                case 3: return String.valueOf(e.getIdArea());
                default: return "";
            }
        }
    }
}
