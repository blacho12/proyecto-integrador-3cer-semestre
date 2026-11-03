package ui.paneles;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.Hashtable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import modelo.Criticidad;
import modelo.Equipo;
import modelo.OrdenMantenimiento;
import ui.componentes.PanelTarjeta;
import ui.componentes.Tema;

/**
 * Formulario modal de una orden de mantenimiento.
 * Solo captura y valida datos: la persistencia la ejecuta GestorOrdenes desde el panel que lo abre.
 * El puntaje y el nivel se calculan con la regla unica del modelo (modelo.Criticidad).
 */
public class DialogoOrden extends JDialog {

    /** Datos ya validados que el panel entrega a GestorOrdenes. */
    public static final class DatosOrden {
        private final String codigoEquipo;
        private final String tipo;
        private final String descripcion;
        private final int impacto;
        private final int riesgoSeguridad;
        private final boolean hayRespaldo;

        DatosOrden(String codigoEquipo, String tipo, String descripcion,
                   int impacto, int riesgoSeguridad, boolean hayRespaldo) {
            this.codigoEquipo = codigoEquipo;
            this.tipo = tipo;
            this.descripcion = descripcion;
            this.impacto = impacto;
            this.riesgoSeguridad = riesgoSeguridad;
            this.hayRespaldo = hayRespaldo;
        }

        public String getCodigoEquipo() { return codigoEquipo; }
        public String getTipo() { return tipo; }
        public String getDescripcion() { return descripcion; }
        public int getImpacto() { return impacto; }
        public int getRiesgoSeguridad() { return riesgoSeguridad; }
        public boolean isHayRespaldo() { return hayRespaldo; }
    }

    private final JComboBox<Equipo> cmbEquipo = new JComboBox<>();
    private final JComboBox<String> cmbTipo = new JComboBox<>(new String[]{"CORRECTIVO", "PREVENTIVO"});
    private final JTextArea txtDescripcion = new JTextArea(3, 30);
    private final JSlider sldImpacto = new JSlider(1, 3, 2);
    private final JSlider sldRiesgo = new JSlider(1, 3, 2);
    private final JLabel lblImpacto = new JLabel("2", SwingConstants.RIGHT);
    private final JLabel lblRiesgo = new JLabel("2", SwingConstants.RIGHT);
    private final JCheckBox chkRespaldo = new JCheckBox("Hay respaldo / redundancia (sin recargo)");
    private final JLabel lblPuntaje = new JLabel("8 puntos");
    private final JLabel lblNivel = new JLabel("MEDIA", SwingConstants.CENTER);
    private final JLabel lblIdentificacion = new JLabel();
    private final OrdenMantenimiento ordenExistente;

    private DatosOrden datos;
    private boolean aceptado;

    public DialogoOrden(Window propietario, OrdenMantenimiento orden, Equipo[] equipos) {
        super(propietario, orden == null ? "Registrar orden de mantenimiento" : "Modificar orden",
                ModalityType.APPLICATION_MODAL);
        this.ordenExistente = orden;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(construir(equipos));
        setMinimumSize(new Dimension(580, 620));
        setSize(620, 700);
        setLocationRelativeTo(propietario);

        if (orden != null) precargar(orden);
    }

    /** Muestra el dialogo y devuelve true solo si el usuario confirmo con datos validos. */
    public boolean mostrarDialogo() {
        setVisible(true);
        return aceptado;
    }

    public DatosOrden getDatos() {
        return datos;
    }

    // ------------------------------------------------------------------ construccion

    private JPanel construir(Equipo[] equipos) {
        JPanel raiz = new JPanel(new BorderLayout(0, 14));
        raiz.setBackground(Tema.FONDO);
        raiz.setBorder(new EmptyBorder(20, 22, 18, 22));

        // Encabezado
        JPanel encabezado = new JPanel();
        encabezado.setOpaque(false);
        encabezado.setLayout(new BoxLayout(encabezado, BoxLayout.Y_AXIS));
        JLabel titulo = Tema.tituloVista(ordenExistente == null
                ? "Nueva orden de mantenimiento" : "Modificar OM-" + ordenExistente.getId());
        JLabel subtitulo = Tema.subtituloVista(
                "La criticidad se calcula automáticamente con la regla del sistema.");
        encabezado.add(titulo);
        encabezado.add(Box.createVerticalStrut(3));
        encabezado.add(subtitulo);
        raiz.add(encabezado, BorderLayout.NORTH);

        // Formulario desplazable
        JPanel formulario = new JPanel();
        formulario.setOpaque(false);
        formulario.setLayout(new BoxLayout(formulario, BoxLayout.Y_AXIS));

        if (equipos != null) {
            for (Equipo equipo : equipos) cmbEquipo.addItem(equipo);
        }
        cmbEquipo.setEnabled(equipos != null && equipos.length > 0 && ordenExistente == null);
        if (ordenExistente != null) {
            cmbEquipo.setToolTipText("El equipo no se modifica: la orden conserva su equipo original.");
        }

        if (equipos == null || equipos.length == 0) {
            JLabel aviso = Tema.badge("No hay equipos registrados en el índice AVL", Tema.ROJO);
            aviso.setAlignmentX(Component.LEFT_ALIGNMENT);
            formulario.add(aviso);
            formulario.add(Box.createVerticalStrut(10));
        }

        lblIdentificacion.setFont(Tema.FUENTE_PEQUENA);
        lblIdentificacion.setForeground(Tema.TEXTO_TENUE);
        formulario.add(Tema.campo("Equipo industrial (índice AVL)", cmbEquipo));
        formulario.add(Box.createVerticalStrut(6));
        lblIdentificacion.setText(ordenExistente == null
                ? "El id y el consecutivo los genera PostgreSQL al confirmar."
                : "OM-" + ordenExistente.getId() + " · consecutivo #" + ordenExistente.getConsecutivo());
        formulario.add(lblIdentificacion);
        formulario.add(Box.createVerticalStrut(12));

        JPanel filaTipo = new JPanel(new GridLayout(1, 2, 12, 0));
        filaTipo.setOpaque(false);
        filaTipo.add(Tema.campo("Tipo de mantenimiento", cmbTipo));
        filaTipo.add(Tema.campo("Respaldo de producción", chkRespaldo));
        chkRespaldo.setOpaque(false);
        chkRespaldo.setFont(Tema.FUENTE_PEQUENA);
        filaTipo.setAlignmentX(Component.LEFT_ALIGNMENT);
        filaTipo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        formulario.add(filaTipo);
        formulario.add(Box.createVerticalStrut(12));

        txtDescripcion.setLineWrap(true);
        txtDescripcion.setWrapStyleWord(true);
        txtDescripcion.setFont(Tema.FUENTE);
        JScrollPane scrollDescripcion = new JScrollPane(txtDescripcion);
        scrollDescripcion.setBorder(BorderFactory.createLineBorder(Tema.BORDE));
        scrollDescripcion.setPreferredSize(new Dimension(100, 80));
        JPanel campoDescripcion = Tema.campo("Descripción de la falla o solicitud", scrollDescripcion);
        campoDescripcion.setAlignmentX(Component.LEFT_ALIGNMENT);
        campoDescripcion.setMaximumSize(new Dimension(Integer.MAX_VALUE, 112));
        formulario.add(campoDescripcion);
        formulario.add(Box.createVerticalStrut(14));

        // Criterios
        PanelTarjeta criterios = new PanelTarjeta(Tema.FONDO, Tema.BORDE, 12);
        criterios.setLayout(new BoxLayout(criterios, BoxLayout.Y_AXIS));
        criterios.setAlignmentX(Component.LEFT_ALIGNMENT);
        criterios.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
        JLabel tituloCriterios = new JLabel("CRITERIOS DE EVALUACIÓN");
        tituloCriterios.setFont(Tema.FUENTE_PEQUENA_BOLD);
        tituloCriterios.setForeground(Tema.TEXTO_SEC);
        criterios.add(tituloCriterios);
        criterios.add(Box.createVerticalStrut(10));
        criterios.add(filaCriterio("Impacto en producción (1-3)", sldImpacto, lblImpacto));
        criterios.add(Box.createVerticalStrut(10));
        criterios.add(filaCriterio("Riesgo para la seguridad (1-3)", sldRiesgo, lblRiesgo));
        formulario.add(criterios);
        formulario.add(Box.createVerticalStrut(14));

        // Previsualizacion
        PanelTarjeta vistaPrevia = new PanelTarjeta(
                Tema.mezclar(Tema.ACENTO, Tema.FONDO, 0.88f), Tema.mezclar(Tema.ACENTO, Tema.FONDO, 0.62f), 12);
        vistaPrevia.setLayout(new BorderLayout(12, 0));
        vistaPrevia.setAlignmentX(Component.LEFT_ALIGNMENT);
        vistaPrevia.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        JPanel textosPuntaje = new JPanel();
        textosPuntaje.setOpaque(false);
        textosPuntaje.setLayout(new BoxLayout(textosPuntaje, BoxLayout.Y_AXIS));
        JLabel etiquetaPuntaje = new JLabel("PUNTAJE CALCULADO");
        etiquetaPuntaje.setFont(Tema.FUENTE_PEQUENA_BOLD);
        etiquetaPuntaje.setForeground(Tema.TEXTO_SEC);
        lblPuntaje.setFont(Tema.MONO_BOLD.deriveFont(18f));
        lblPuntaje.setForeground(Tema.ACENTO_CLARO);
        textosPuntaje.add(etiquetaPuntaje);
        textosPuntaje.add(Box.createVerticalStrut(3));
        textosPuntaje.add(lblPuntaje);
        vistaPrevia.add(textosPuntaje, BorderLayout.WEST);

        JPanel zonaNivel = new JPanel();
        zonaNivel.setOpaque(false);
        zonaNivel.setLayout(new BoxLayout(zonaNivel, BoxLayout.Y_AXIS));
        JLabel etiquetaNivel = new JLabel("NIVEL ASIGNADO");
        etiquetaNivel.setFont(Tema.FUENTE_PEQUENA_BOLD);
        etiquetaNivel.setForeground(Tema.TEXTO_SEC);
        etiquetaNivel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        lblNivel.setFont(Tema.FUENTE_MEDIA);
        lblNivel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        zonaNivel.add(etiquetaNivel);
        zonaNivel.add(Box.createVerticalStrut(4));
        zonaNivel.add(lblNivel);
        vistaPrevia.add(zonaNivel, BorderLayout.EAST);
        formulario.add(vistaPrevia);

        JScrollPane scrollFormulario = new JScrollPane(formulario);
        scrollFormulario.setBorder(BorderFactory.createEmptyBorder());
        scrollFormulario.getViewport().setBackground(Tema.FONDO);
        scrollFormulario.setOpaque(false);
        scrollFormulario.getVerticalScrollBar().setUnitIncrement(16);
        raiz.add(scrollFormulario, BorderLayout.CENTER);

        // Acciones
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        acciones.setOpaque(false);
        JButtonCancelar cancelar = new JButtonCancelar();
        JButtonGuardar guardar = new JButtonGuardar();
        acciones.add(cancelar);
        acciones.add(guardar);
        raiz.add(acciones, BorderLayout.SOUTH);

        sldImpacto.addChangeListener(e -> actualizarVistaPrevia());
        sldRiesgo.addChangeListener(e -> actualizarVistaPrevia());
        chkRespaldo.addActionListener(e -> actualizarVistaPrevia());
        actualizarVistaPrevia();

        return raiz;
    }

    private JPanel filaCriterio(String texto, JSlider slider, JLabel valor) {
        JPanel panel = new JPanel(new BorderLayout(10, 2));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));

        JLabel etiqueta = new JLabel(texto);
        etiqueta.setFont(Tema.FUENTE_PEQUENA);
        etiqueta.setForeground(Tema.TEXTO_SEC);
        valor.setFont(Tema.MONO_BOLD);
        valor.setForeground(Tema.ACENTO_CLARO);
        valor.setPreferredSize(new Dimension(22, 18));

        panel.add(etiqueta, BorderLayout.WEST);
        panel.add(valor, BorderLayout.EAST);

        slider.setOpaque(false);
        slider.setMajorTickSpacing(1);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setSnapToTicks(true);
        slider.setFont(Tema.FUENTE_PEQUENA);
        Hashtable<Integer, JLabel> etiquetas = new Hashtable<>();
        for (int i = 1; i <= 3; i++) {
            JLabel l = new JLabel(String.valueOf(i));
            l.setFont(Tema.FUENTE_PEQUENA);
            l.setForeground(Tema.TEXTO_SEC);
            etiquetas.put(i, l);
        }
        slider.setLabelTable(etiquetas);
        panel.add(slider, BorderLayout.SOUTH);
        return panel;
    }

    private void precargar(OrdenMantenimiento orden) {
        for (int i = 0; i < cmbEquipo.getItemCount(); i++) {
            if (cmbEquipo.getItemAt(i).getCodigo().equals(orden.getCodigoEquipo())) {
                cmbEquipo.setSelectedIndex(i);
                break;
            }
        }
        cmbTipo.setSelectedItem(orden.getTipo());
        txtDescripcion.setText(orden.getDescripcion());
        sldImpacto.setValue(orden.getImpacto());
        sldRiesgo.setValue(orden.getRiesgoSeguridad());
        chkRespaldo.setSelected(orden.isHayRespaldo());
        actualizarVistaPrevia();
    }

    private void actualizarVistaPrevia() {
        int impacto = sldImpacto.getValue();
        int riesgo = sldRiesgo.getValue();
        boolean respaldo = chkRespaldo.isSelected();
        lblImpacto.setText(String.valueOf(impacto));
        lblRiesgo.setText(String.valueOf(riesgo));

        int puntaje = Criticidad.puntaje(impacto, riesgo, respaldo);
        Criticidad nivel = Criticidad.calcular(impacto, riesgo, respaldo);
        Color color = Tema.colorCriticidad(nivel);
        lblPuntaje.setText(puntaje + " puntos");
        Tema.aplicarBadge(lblNivel, nivel.name(), color);
    }

    // ------------------------------------------------------------------ acciones

    private void confirmar() {
        Equipo equipo = (Equipo) cmbEquipo.getSelectedItem();
        if (equipo == null) {
            error("Seleccione un equipo industrial. Si no aparece ninguno, registre primero un equipo.");
            return;
        }
        String descripcion = txtDescripcion.getText().trim();
        if (descripcion.isEmpty()) {
            error("La descripción es obligatoria.");
            txtDescripcion.requestFocusInWindow();
            return;
        }
        int impacto = sldImpacto.getValue();
        int riesgo = sldRiesgo.getValue();
        if (impacto < 1 || impacto > 3 || riesgo < 1 || riesgo > 3) {
            error("El impacto y el riesgo deben estar entre 1 y 3.");
            return;
        }
        datos = new DatosOrden(equipo.getCodigo(), (String) cmbTipo.getSelectedItem(),
                descripcion, impacto, riesgo, chkRespaldo.isSelected());
        aceptado = true;
        dispose();
    }

    private void error(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Datos incompletos", JOptionPane.WARNING_MESSAGE);
    }

    private class JButtonGuardar extends javax.swing.JButton {
        JButtonGuardar() {
            super(ordenExistente == null ? "Registrar y encolar" : "Guardar cambios");
            setFont(Tema.FUENTE_MEDIA);
            setBackground(Tema.ACENTO);
            setForeground(Color.WHITE);
            setFocusPainted(false);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Tema.ACENTO),
                    new EmptyBorder(8, 16, 8, 16)));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            addActionListener(e -> confirmar());
        }
    }

    private class JButtonCancelar extends javax.swing.JButton {
        JButtonCancelar() {
            super("Cancelar");
            setFont(Tema.FUENTE_MEDIA);
            setBackground(Tema.PANEL_ALT);
            setForeground(Tema.TEXTO);
            setFocusPainted(false);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Tema.BORDE),
                    new EmptyBorder(8, 16, 8, 16)));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            addActionListener(e -> dispose());
        }
    }
}
