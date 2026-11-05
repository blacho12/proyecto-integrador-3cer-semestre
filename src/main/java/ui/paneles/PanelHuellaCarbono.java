package ui.paneles;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.table.AbstractTableModel;

import app.GestorOrdenes;
import modelo.Area;
import modelo.Equipo;
import modelo.RegistroCarbono;
import modelo.ResumenCarbono;
import ui.ContextoApp;
import ui.componentes.PanelTarjeta;
import ui.componentes.TarjetaIndicador;
import ui.componentes.Tema;

/**
 * Modulo de huella de carbono.
 *
 * Permite configurar el consumo normal y el porcentaje extra de cada equipo, registrar las
 * horas en mal estado, calcular las emisiones adicionales con BigDecimal y guardarlas en
 * PostgreSQL. Los indicadores, la tabla por maquina, la tabla por area y el historial se
 * construyen exclusivamente con los registros almacenados (ResumenCarbono).
 */
public class PanelHuellaCarbono extends PanelBase {

    private static final DecimalFormat DECIMAL_2 = new DecimalFormat("#,##0.00",
            DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-CO")));
    private static final DecimalFormat DECIMAL_4 = new DecimalFormat("#,##0.0000",
            DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-CO")));

    private final TarjetaIndicador indTotal = new TarjetaIndicador(
            "Emisiones adicionales", Tema.ROJO, "kg CO₂ en toda la planta");
    private final TarjetaIndicador indEquipos = new TarjetaIndicador(
            "Máquinas con emisiones", Tema.AMBAR, "Con registros mayores que cero");
    private final TarjetaIndicador indRegistros = new TarjetaIndicador(
            "Registros históricos", Tema.CIAN, "Persistidos en PostgreSQL");

    private final JComboBox<Equipo> cmbEquipo = new JComboBox<>();
    private final JTextField txtConsumo = new JTextField();
    private final JTextField txtPorcentaje = new JTextField();
    private final JTextField txtHoras = new JTextField();
    private final JTextField txtFactor = new JTextField();
    private final JLabel lblParametros = Tema.etiquetaPequena("Seleccione un equipo.");
    private final JButton btnGuardarParametros = Tema.botonSecundario("Guardar parámetros");
    private final JButton btnRegistrar = Tema.botonPrimario("Registrar horas y calcular");
    private final JButton btnGuardarFactor = Tema.botonSecundario("Guardar factor");

    private final ModeloResumenAreas modeloAreas = new ModeloResumenAreas();
    private final ModeloEquiposCarbono modeloEquipos = new ModeloEquiposCarbono();
    private final ModeloRegistros modeloRegistros = new ModeloRegistros();
    private final JTable tablaAreas = new JTable(modeloAreas);
    private final JTable tablaEquipos = new JTable(modeloEquipos);
    private final JTable tablaRegistros = new JTable(modeloRegistros);
    private final JProgressBar progreso = Tema.barraProgreso();

    private List<Equipo> equipos = new ArrayList<>();
    private Map<Integer, String> areas = new LinkedHashMap<>();
    private boolean actualizandoCombo;
    private boolean limpiarHorasAlExito;

    public PanelHuellaCarbono(GestorOrdenes gestor, ContextoApp contexto) {
        super(gestor, contexto);
        setLayout(new BorderLayout(0, 14));
        add(Tema.cabeceraVista("Huella de carbono",
                "Estimación de CO₂ adicional por máquinas en mal estado, calculada con datos persistidos.",
                progreso), BorderLayout.NORTH);
        add(construirCuerpo(), BorderLayout.CENTER);

        configurarTablas();
        configurarEventos();
        actualizarBotones();
    }

    // ------------------------------------------------------------------ estructura

    private JScrollPane construirCuerpo() {
        JPanel cuerpo = new JPanel();
        cuerpo.setOpaque(false);
        cuerpo.setLayout(new BoxLayout(cuerpo, BoxLayout.Y_AXIS));

        JPanel indicadores = new JPanel(new GridLayout(1, 3, 12, 12));
        indicadores.setOpaque(false);
        indicadores.add(indTotal);
        indicadores.add(indEquipos);
        indicadores.add(indRegistros);
        cuerpo.add(indicadores);
        cuerpo.add(Box.createVerticalStrut(14));

        JPanel medio = new JPanel(new BorderLayout(14, 0));
        medio.setOpaque(false);
        medio.add(construirFormulario(), BorderLayout.WEST);
        medio.add(tarjetaTabla("Emisiones adicionales por área",
                "Consolidado de los registros guardados en PostgreSQL.",
                tablaAreas, new Dimension(100, 540)), BorderLayout.CENTER);
        cuerpo.add(medio);
        cuerpo.add(Box.createVerticalStrut(14));

        JPanel inferior = new JPanel();
        inferior.setOpaque(false);
        inferior.setLayout(new BoxLayout(inferior, BoxLayout.Y_AXIS));

        PanelTarjeta tarjetaEquipos = tarjetaTabla("Emisiones adicionales por máquina",
                "Suma histórica de cada equipo con registros.",
                tablaEquipos, new Dimension(100, 240));
        tarjetaEquipos.setAlignmentX(LEFT_ALIGNMENT);
        tarjetaEquipos.setMaximumSize(new Dimension(Integer.MAX_VALUE, 240));
        inferior.add(tarjetaEquipos);
        inferior.add(Box.createVerticalStrut(14));

        PanelTarjeta tarjetaRegistros = tarjetaTabla("Registros históricos de carbono",
                "Horas en mal estado, consumo adicional, emisiones y factor utilizado.",
                tablaRegistros, new Dimension(100, 340));
        tarjetaRegistros.setAlignmentX(LEFT_ALIGNMENT);
        tarjetaRegistros.setMaximumSize(new Dimension(Integer.MAX_VALUE, 340));
        inferior.add(tarjetaRegistros);
        cuerpo.add(inferior);
        cuerpo.add(Box.createVerticalStrut(12));

        JLabel nota = new JLabel("<html>El resultado es una estimación, no una medición directa. "
                + "Los indicadores se recalculan desde registro_carbono y el factor de emisión "
                + "queda guardado en cada registro para conservar la trazabilidad histórica.</html>");
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

    private PanelTarjeta construirFormulario() {
        PanelTarjeta tarjeta = new PanelTarjeta();
        tarjeta.setLayout(new BorderLayout(0, 12));
        tarjeta.setPreferredSize(new Dimension(410, 540));

        JPanel cabecera = new JPanel();
        cabecera.setOpaque(false);
        cabecera.setLayout(new BoxLayout(cabecera, BoxLayout.Y_AXIS));
        cabecera.add(Tema.encabezadoSeccion("Calcular emisiones de un equipo"));
        cabecera.add(Box.createVerticalStrut(3));
        JLabel formula = new JLabel("<html><body style='width:340px'>"
                + "Consumo adicional = kWh/h × horas × % extra / 100<br>"
                + "Emisiones = consumo adicional × factor de emisión</body></html>");
        formula.setFont(Tema.FUENTE_PEQUENA);
        formula.setForeground(Tema.TEXTO_SEC);
        formula.setAlignmentX(LEFT_ALIGNMENT);
        cabecera.add(formula);

        cmbEquipo.setFont(Tema.FUENTE);
        cmbEquipo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> lista, Object valor, int indice,
                                                          boolean seleccionado, boolean foco) {
                super.getListCellRendererComponent(lista, valor, indice, seleccionado, foco);
                if (valor instanceof Equipo equipo) {
                    setText(equipo.getCodigo() + " — " + equipo.getNombre());
                }
                return this;
            }
        });

        Tema.placeholder(txtConsumo, "10.00");
        Tema.placeholder(txtPorcentaje, "15.00");
        Tema.placeholder(txtHoras, "8");
        Tema.placeholder(txtFactor, "0.1500");

        JPanel campos = new JPanel();
        campos.setOpaque(false);
        campos.setLayout(new BoxLayout(campos, BoxLayout.Y_AXIS));

        JPanel campoEquipo = Tema.campo("Equipo registrado", cmbEquipo);
        campoEquipo.setAlignmentX(LEFT_ALIGNMENT);
        campos.add(campoEquipo);
        campos.add(Box.createVerticalStrut(5));
        lblParametros.setAlignmentX(LEFT_ALIGNMENT);
        campos.add(lblParametros);
        campos.add(Box.createVerticalStrut(9));

        JPanel filaParametros = new JPanel(new GridLayout(1, 2, 8, 0));
        filaParametros.setOpaque(false);
        filaParametros.add(Tema.campo("Consumo normal (kWh/h)", txtConsumo));
        filaParametros.add(Tema.campo("% extra en mal estado", txtPorcentaje));
        filaParametros.setAlignmentX(LEFT_ALIGNMENT);
        campos.add(filaParametros);
        campos.add(Box.createVerticalStrut(7));
        btnGuardarParametros.setAlignmentX(LEFT_ALIGNMENT);
        campos.add(btnGuardarParametros);
        campos.add(Box.createVerticalStrut(14));

        JPanel campoHoras = Tema.campo("Horas en mal estado", txtHoras);
        campoHoras.setAlignmentX(LEFT_ALIGNMENT);
        campos.add(campoHoras);
        campos.add(Box.createVerticalStrut(7));
        btnRegistrar.setAlignmentX(LEFT_ALIGNMENT);
        campos.add(btnRegistrar);
        campos.add(Box.createVerticalStrut(14));

        JPanel campoFactor = Tema.campo(
                "Factor de emisión de la planta (kg CO₂/kWh)", txtFactor);
        campoFactor.setAlignmentX(LEFT_ALIGNMENT);
        campos.add(campoFactor);
        campos.add(Box.createVerticalStrut(7));
        btnGuardarFactor.setAlignmentX(LEFT_ALIGNMENT);
        campos.add(btnGuardarFactor);
        campos.add(Box.createVerticalStrut(8));

        JLabel nota = new JLabel("<html><body style='width:340px'>"
                + "El consumo normal y el porcentaje quedan asociados al equipo. "
                + "El factor se guarda en cada registro histórico.</body></html>");
        nota.setFont(Tema.FUENTE_PEQUENA);
        nota.setForeground(Tema.TEXTO_TENUE);
        nota.setAlignmentX(LEFT_ALIGNMENT);
        campos.add(nota);

        tarjeta.add(cabecera, BorderLayout.NORTH);
        tarjeta.add(campos, BorderLayout.CENTER);
        return tarjeta;
    }

    private PanelTarjeta tarjetaTabla(String titulo, String subtitulo, JTable tabla,
                                      Dimension preferida) {
        PanelTarjeta tarjeta = new PanelTarjeta();
        tarjeta.setLayout(new BorderLayout(0, 10));
        tarjeta.setPreferredSize(preferida);

        JPanel cabecera = new JPanel();
        cabecera.setOpaque(false);
        cabecera.setLayout(new BoxLayout(cabecera, BoxLayout.Y_AXIS));
        cabecera.add(Tema.encabezadoSeccion(titulo));
        JLabel sub = Tema.etiquetaPequena(subtitulo);
        sub.setAlignmentX(LEFT_ALIGNMENT);
        cabecera.add(Box.createVerticalStrut(2));
        cabecera.add(sub);

        tarjeta.add(cabecera, BorderLayout.NORTH);
        tarjeta.add(Tema.scroll(tabla), BorderLayout.CENTER);
        return tarjeta;
    }

    private void configurarTablas() {
        Tema.configurarTabla(tablaAreas);
        tablaAreas.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        tablaAreas.getColumnModel().getColumn(0).setPreferredWidth(260);
        tablaAreas.getColumnModel().getColumn(1).setPreferredWidth(140);

        Tema.configurarTabla(tablaEquipos);
        tablaEquipos.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        tablaEquipos.getColumnModel().getColumn(0).setPreferredWidth(95);
        tablaEquipos.getColumnModel().getColumn(1).setPreferredWidth(190);
        tablaEquipos.getColumnModel().getColumn(2).setPreferredWidth(120);
        tablaEquipos.getColumnModel().getColumn(3).setPreferredWidth(110);
        tablaEquipos.getColumnModel().getColumn(0).setCellRenderer(Tema.renderizadorMono(Tema.CIAN));

        Tema.configurarTabla(tablaRegistros);
        tablaRegistros.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        tablaRegistros.getColumnModel().getColumn(0).setPreferredWidth(90);
        tablaRegistros.getColumnModel().getColumn(1).setPreferredWidth(170);
        tablaRegistros.getColumnModel().getColumn(2).setPreferredWidth(130);
        tablaRegistros.getColumnModel().getColumn(3).setPreferredWidth(65);
        tablaRegistros.getColumnModel().getColumn(4).setPreferredWidth(135);
        tablaRegistros.getColumnModel().getColumn(5).setPreferredWidth(120);
        tablaRegistros.getColumnModel().getColumn(6).setPreferredWidth(130);
        tablaRegistros.getColumnModel().getColumn(0).setCellRenderer(Tema.renderizadorMono(Tema.CIAN));
    }

    private void configurarEventos() {
        cmbEquipo.addActionListener(e -> {
            if (!actualizandoCombo) mostrarParametros();
        });
        btnGuardarParametros.addActionListener(e -> guardarParametros());
        btnRegistrar.addActionListener(e -> registrarHoras());
        btnGuardarFactor.addActionListener(e -> guardarFactor());
    }

    // ------------------------------------------------------------------ carga

    @Override
    public void recargar() {
        if (estaOcupado()) return;
        cargar(this::cargarDatos, this::aplicarDatos,
                "No se pudo cargar la huella de carbono desde PostgreSQL");
    }

    private Datos cargarDatos() throws Exception {
        BigDecimal factor = gestor.consultarFactorEmision();
        ResumenCarbono resumen = gestor.calcularResumenCarbono();
        List<Equipo> equiposBd = gestor.consultarEquipos();
        Map<Integer, String> areasBd = new LinkedHashMap<>();
        for (Area area : gestor.consultarAreas()) areasBd.put(area.getIdArea(), area.getNombre());
        return new Datos(factor, resumen, equiposBd, areasBd);
    }

    private void aplicarDatos(Datos datos) {
        equipos = datos.equipos;
        areas = datos.areas;

        String codigoPrevio = codigoSeleccionado();
        actualizandoCombo = true;
        cmbEquipo.removeAllItems();
        for (Equipo equipo : datos.equipos) cmbEquipo.addItem(equipo);
        if (codigoPrevio != null) {
            seleccionarEquipo(codigoPrevio);
        } else if (cmbEquipo.getItemCount() > 0) {
            cmbEquipo.setSelectedIndex(0);
        }
        actualizandoCombo = false;
        mostrarParametros();

        indTotal.setValor(dos(datos.resumen.getTotalEmisiones()) + " kg");
        indTotal.setDetalle("kg CO₂ adicionales en la planta");
        indEquipos.setValor(String.valueOf(datos.resumen.getEquiposConEmisiones()));
        indRegistros.setValor(String.valueOf(datos.resumen.getRegistros().size()));

        txtFactor.setText(datos.factor.toPlainString());

        modeloAreas.setFilas(new ArrayList<>(
                datos.resumen.getEmisionesPorArea().entrySet()));

        List<FilaEquipo> filasEquipos = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entrada
                : datos.resumen.getEmisionesPorEquipo().entrySet()) {
            Equipo equipo = buscarEquipo(entrada.getKey());
            String nombre = equipo == null ? entrada.getKey() : equipo.getNombre();
            String area = equipo == null ? "—"
                    : datos.areas.getOrDefault(equipo.getIdArea(),
                            "Área " + equipo.getIdArea());
            filasEquipos.add(new FilaEquipo(entrada.getKey(), nombre, area, entrada.getValue()));
        }
        modeloEquipos.setFilas(filasEquipos);
        modeloRegistros.setFilas(datos.resumen.getRegistros());
        actualizarBotones();
    }

    private void seleccionarEquipo(String codigo) {
        for (int i = 0; i < cmbEquipo.getItemCount(); i++) {
            Equipo equipo = cmbEquipo.getItemAt(i);
            if (equipo != null && equipo.getCodigo().equals(codigo)) {
                cmbEquipo.setSelectedIndex(i);
                return;
            }
        }
    }

    private Equipo buscarEquipo(String codigo) {
        for (Equipo equipo : equipos) {
            if (equipo.getCodigo().equals(codigo)) return equipo;
        }
        return null;
    }

    private void mostrarParametros() {
        Equipo equipo = (Equipo) cmbEquipo.getSelectedItem();
        if (equipo == null) {
            lblParametros.setText("<html><body style='width:330px'>No hay equipos registrados. "
                    + "Cree uno en «Equipos e índice AVL».</body></html>");
            txtConsumo.setText("");
            txtPorcentaje.setText("");
        } else {
            txtConsumo.setText(equipo.getConsumoNormalKwh().toPlainString());
            txtPorcentaje.setText(equipo.getPorcentajeExtra().toPlainString());
            String area = areas.getOrDefault(equipo.getIdArea(), "Área " + equipo.getIdArea());
            lblParametros.setText("<html><body style='width:330px'>"
                    + html(equipo.getCodigo() + " · " + equipo.getNombre()) + "<br>"
                    + html(area) + "</body></html>");
        }
        actualizarBotones();
    }

    // ------------------------------------------------------------------ acciones

    private void guardarParametros() {
        if (estaOcupado()) return;
        Equipo equipo = (Equipo) cmbEquipo.getSelectedItem();
        if (equipo == null) {
            aviso("Seleccione un equipo registrado.");
            return;
        }
        try {
            BigDecimal consumo = leerDecimal(txtConsumo, "consumo normal");
            BigDecimal porcentaje = leerDecimal(txtPorcentaje, "porcentaje adicional");
            if (porcentaje.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException(
                        "El porcentaje de consumo adicional no puede superar 100.");
            }
            limpiarHorasAlExito = false;
            ejecutarOperacion(
                    () -> gestor.configurarParametrosEquipo(equipo.getCodigo(), consumo, porcentaje),
                    "Parámetros de carbono guardados para " + equipo.getCodigo() + ".");
        } catch (IllegalArgumentException e) {
            aviso(e.getMessage());
        }
    }

    private void registrarHoras() {
        if (estaOcupado()) return;
        Equipo equipo = (Equipo) cmbEquipo.getSelectedItem();
        if (equipo == null) {
            aviso("Seleccione un equipo registrado.");
            return;
        }
        try {
            BigDecimal horas = leerDecimal(txtHoras, "horas en mal estado");
            limpiarHorasAlExito = true;
            ejecutarOperacion(() -> gestor.registrarEmisionCarbono(equipo.getCodigo(), horas),
                    "Emisiones de " + equipo.getCodigo() + " registradas en PostgreSQL.");
        } catch (IllegalArgumentException e) {
            limpiarHorasAlExito = false;
            aviso(e.getMessage());
        }
    }

    private void guardarFactor() {
        if (estaOcupado()) return;
        try {
            BigDecimal factor = leerDecimal(txtFactor, "factor de emisión");
            limpiarHorasAlExito = false;
            ejecutarOperacion(() -> gestor.actualizarFactorEmision(factor),
                    "Factor de emisión de la planta actualizado.");
        } catch (IllegalArgumentException e) {
            aviso(e.getMessage());
        }
    }

    private BigDecimal leerDecimal(JTextField campo, String nombre) {
        String texto = campo.getText() == null ? "" : campo.getText().trim().replace(',', '.');
        if (texto.isEmpty()) {
            throw new IllegalArgumentException("Ingrese el " + nombre + ".");
        }
        try {
            BigDecimal valor = new BigDecimal(texto);
            if (valor.signum() < 0) {
                throw new IllegalArgumentException("El " + nombre + " no puede ser negativo.");
            }
            return valor;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "El " + nombre + " debe ser un número válido (punto o coma decimal).");
        }
    }

    private void aviso(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Datos inválidos",
                JOptionPane.WARNING_MESSAGE);
    }

    private String codigoSeleccionado() {
        Equipo equipo = (Equipo) cmbEquipo.getSelectedItem();
        return equipo == null ? null : equipo.getCodigo();
    }

    private void actualizarBotones() {
        boolean libre = !estaOcupado();
        boolean hayEquipo = cmbEquipo.getSelectedItem() != null;
        cmbEquipo.setEnabled(libre);
        txtConsumo.setEnabled(libre);
        txtPorcentaje.setEnabled(libre);
        txtHoras.setEnabled(libre);
        txtFactor.setEnabled(libre);
        btnGuardarParametros.setEnabled(libre && hayEquipo);
        btnRegistrar.setEnabled(libre && hayEquipo);
        btnGuardarFactor.setEnabled(libre);
    }

    @Override
    protected void alCambiarOcupado(boolean ocupado) {
        progreso.setVisible(ocupado);
        actualizarBotones();
    }

    @Override
    protected void alOperacionExitosa(Object valor) {
        if (limpiarHorasAlExito) txtHoras.setText("");
        limpiarHorasAlExito = false;
    }

    @Override
    protected void alErrorOperacion() {
        limpiarHorasAlExito = false;
    }

    // ------------------------------------------------------------------ formato

    private static String dos(BigDecimal valor) {
        return valor == null ? "—" : DECIMAL_2.format(valor);
    }

    private static String cuatro(BigDecimal valor) {
        return valor == null ? "—" : DECIMAL_4.format(valor);
    }

    private static String html(String texto) {
        return texto == null ? "" : texto.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Datos de la vista cargados desde la capa de aplicacion. */
    private static class Datos {
        final BigDecimal factor;
        final ResumenCarbono resumen;
        final List<Equipo> equipos;
        final Map<Integer, String> areas;

        Datos(BigDecimal factor, ResumenCarbono resumen, List<Equipo> equipos,
              Map<Integer, String> areas) {
            this.factor = factor;
            this.resumen = resumen;
            this.equipos = equipos;
            this.areas = areas;
        }
    }

    private static class FilaEquipo {
        final String codigo;
        final String nombre;
        final String area;
        final BigDecimal emisiones;

        FilaEquipo(String codigo, String nombre, String area, BigDecimal emisiones) {
            this.codigo = codigo;
            this.nombre = nombre;
            this.area = area;
            this.emisiones = emisiones;
        }
    }

    /** Consolidado por area; cada fila proviene de los registros de PostgreSQL. */
    private static class ModeloResumenAreas extends AbstractTableModel {
        private static final String[] COLUMNAS = {"Área", "Emisiones (kg CO₂)"};
        private List<Map.Entry<String, BigDecimal>> filas = new ArrayList<>();

        void setFilas(List<Map.Entry<String, BigDecimal>> nuevas) {
            filas = new ArrayList<>(nuevas);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return filas.size(); }
        @Override public int getColumnCount() { return COLUMNAS.length; }
        @Override public String getColumnName(int columna) { return COLUMNAS[columna]; }

        @Override
        public Object getValueAt(int fila, int columna) {
            Map.Entry<String, BigDecimal> entrada = filas.get(fila);
            return columna == 0 ? entrada.getKey() : dos(entrada.getValue());
        }
    }

    /** Consolidado por maquina; el nombre y el area se resuelven con el equipo persistido. */
    private static class ModeloEquiposCarbono extends AbstractTableModel {
        private static final String[] COLUMNAS =
                {"Código", "Equipo", "Área", "Emisiones (kg CO₂)"};
        private List<FilaEquipo> filas = new ArrayList<>();

        void setFilas(List<FilaEquipo> nuevas) {
            filas = new ArrayList<>(nuevas);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return filas.size(); }
        @Override public int getColumnCount() { return COLUMNAS.length; }
        @Override public String getColumnName(int columna) { return COLUMNAS[columna]; }

        @Override
        public Object getValueAt(int fila, int columna) {
            FilaEquipo filaEquipo = filas.get(fila);
            switch (columna) {
                case 0: return filaEquipo.codigo;
                case 1: return filaEquipo.nombre;
                case 2: return filaEquipo.area;
                case 3: return dos(filaEquipo.emisiones);
                default: return "";
            }
        }
    }

    /** Historico de registro_carbono con todas las columnas solicitadas. */
    private static class ModeloRegistros extends AbstractTableModel {
        private static final String[] COLUMNAS = {
                "Código", "Equipo", "Área", "Horas", "Consumo adicional (kWh)",
                "Emisiones (kg CO₂)", "Fecha"
        };
        private List<RegistroCarbono> filas = new ArrayList<>();

        void setFilas(List<RegistroCarbono> nuevas) {
            filas = new ArrayList<>(nuevas);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return filas.size(); }
        @Override public int getColumnCount() { return COLUMNAS.length; }
        @Override public String getColumnName(int columna) { return COLUMNAS[columna]; }

        @Override
        public Object getValueAt(int fila, int columna) {
            RegistroCarbono registro = filas.get(fila);
            switch (columna) {
                case 0: return registro.getCodigoEquipo();
                case 1: return registro.getNombreEquipo();
                case 2: return registro.getNombreArea();
                case 3: return dos(registro.getHorasMalEstado());
                case 4: return cuatro(registro.getConsumoAdicionalKwh());
                case 5: return cuatro(registro.getEmisionesKgCo2());
                case 6: return Tema.fechaHora(registro.getFechaRegistro());
                default: return "";
            }
        }
    }
}
