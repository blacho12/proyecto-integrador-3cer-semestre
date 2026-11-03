package ui.paneles;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import app.GestorOrdenes;
import estructuras.Accion;
import modelo.OrdenMantenimiento;
import ui.ContextoApp;
import ui.componentes.PanelTarjeta;
import ui.componentes.Tema;

/**
 * Historial de acciones (pila LIFO). Muestra el contenido de PilaAcciones en orden
 * tope-base y delega el deshacer en GestorOrdenes, que solo desapila si PostgreSQL
 * confirmo la reversion.
 */
public class PanelHistorial extends PanelBase {

    private final ModeloHistorial modelo = new ModeloHistorial();
    private final JTable tabla = new JTable(modelo);
    private final JLabel lblVacio = Tema.etiquetaPequena("La pila está vacía: no hay acciones por deshacer.");
    private final JLabel lblTamano = new JLabel("0");
    private final JLabel lblTope = Tema.etiquetaPequena("Sin acciones registradas.");
    private final JButton btnDeshacer = Tema.botonAdvertencia("Deshacer última acción");
    private final JProgressBar progreso = Tema.barraProgreso();

    public PanelHistorial(GestorOrdenes gestor, ContextoApp contexto) {
        super(gestor, contexto);
        setLayout(new BorderLayout(0, 14));
        add(Tema.cabeceraVista("Historial de acciones",
                "Pila LIFO en memoria: cada operación guarda la foto previa de la orden para poder revertirla.",
                progreso), BorderLayout.NORTH);

        JPanel cuerpo = new JPanel(new BorderLayout(14, 0));
        cuerpo.setOpaque(false);
        cuerpo.add(construirTabla(), BorderLayout.CENTER);
        cuerpo.add(construirPropiedades(), BorderLayout.EAST);
        add(cuerpo, BorderLayout.CENTER);

        configurarTabla();
        btnDeshacer.addActionListener(e -> deshacer());
        actualizar();
    }

    private PanelTarjeta construirTabla() {
        PanelTarjeta tarjeta = new PanelTarjeta();
        tarjeta.setLayout(new BorderLayout(0, 10));

        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setOpaque(false);
        JPanel textos = new JPanel();
        textos.setOpaque(false);
        textos.setLayout(new BoxLayout(textos, BoxLayout.Y_AXIS));
        textos.add(Tema.encabezadoSeccion("Acciones apiladas (tope arriba)"));
        textos.add(Box.createVerticalStrut(2));
        textos.add(Tema.etiquetaPequena("El orden de la tabla es el orden real de la pila."));
        cabecera.add(textos, BorderLayout.WEST);
        cabecera.add(btnDeshacer, BorderLayout.EAST);

        JScrollPane scroll = Tema.scroll(tabla);
        scroll.setPreferredSize(new Dimension(100, 380));

        tarjeta.add(cabecera, BorderLayout.NORTH);
        tarjeta.add(scroll, BorderLayout.CENTER);
        tarjeta.add(lblVacio, BorderLayout.SOUTH);
        return tarjeta;
    }

    private PanelTarjeta construirPropiedades() {
        PanelTarjeta tarjeta = new PanelTarjeta();
        tarjeta.setPreferredSize(new Dimension(330, 100));
        tarjeta.setLayout(new BoxLayout(tarjeta, BoxLayout.Y_AXIS));

        JLabel titulo = Tema.encabezadoSeccion("Propiedades de la pila");
        titulo.setAlignmentX(LEFT_ALIGNMENT);
        tarjeta.add(titulo);
        tarjeta.add(Box.createVerticalStrut(10));

        JLabel etiquetaTamano = new JLabel("Acciones disponibles");
        etiquetaTamano.setFont(Tema.FUENTE_PEQUENA_BOLD);
        etiquetaTamano.setForeground(Tema.TEXTO_SEC);
        etiquetaTamano.setAlignmentX(LEFT_ALIGNMENT);
        lblTamano.setFont(Tema.FUENTE_VALOR);
        lblTamano.setForeground(Tema.AMBAR);
        lblTamano.setAlignmentX(LEFT_ALIGNMENT);
        tarjeta.add(etiquetaTamano);
        tarjeta.add(Box.createVerticalStrut(2));
        tarjeta.add(lblTamano);
        tarjeta.add(Box.createVerticalStrut(10));

        JLabel etiquetaTope = new JLabel("Tope actual");
        etiquetaTope.setFont(Tema.FUENTE_PEQUENA_BOLD);
        etiquetaTope.setForeground(Tema.TEXTO_SEC);
        etiquetaTope.setAlignmentX(LEFT_ALIGNMENT);
        lblTope.setAlignmentX(LEFT_ALIGNMENT);
        tarjeta.add(etiquetaTope);
        tarjeta.add(Box.createVerticalStrut(2));
        tarjeta.add(lblTope);
        tarjeta.add(Box.createVerticalStrut(14));

        JLabel explicacion = new JLabel(
                "<html><div style='width:285px'>Cada acción guarda una copia del estado previo. "
                        + "Al deshacer, la revisión se aplica primero en PostgreSQL; solo si el commit "
                        + "es exitoso se retira la acción de la pila.</div></html>");
        explicacion.setFont(Tema.FUENTE_PEQUENA);
        explicacion.setForeground(Tema.TEXTO_SEC);
        explicacion.setAlignmentX(LEFT_ALIGNMENT);
        tarjeta.add(explicacion);
        tarjeta.add(Box.createVerticalStrut(14));

        JLabel complejidadTitulo = new JLabel("COMPLEJIDAD");
        complejidadTitulo.setFont(Tema.FUENTE_PEQUENA_BOLD);
        complejidadTitulo.setForeground(Tema.TEXTO_TENUE);
        complejidadTitulo.setAlignmentX(LEFT_ALIGNMENT);
        tarjeta.add(complejidadTitulo);
        tarjeta.add(Box.createVerticalStrut(6));

        JPanel complejidad = new JPanel();
        complejidad.setOpaque(true);
        complejidad.setBackground(Tema.FONDO);
        complejidad.setBorder(BorderFactory.createLineBorder(Tema.BORDE));
        complejidad.setLayout(new BoxLayout(complejidad, BoxLayout.Y_AXIS));
        complejidad.setAlignmentX(LEFT_ALIGNMENT);
        complejidad.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
        complejidad.add(lineaComplejidad("Apilar (push)", "O(1)"));
        complejidad.add(lineaComplejidad("Desapilar (pop)", "O(1)"));
        complejidad.add(lineaComplejidad("Ver tope (peek)", "O(1)"));
        complejidad.setBorder(new EmptyBorder(8, 10, 8, 10));
        tarjeta.add(complejidad);
        tarjeta.add(Box.createVerticalStrut(14));

        JLabel tipos = new JLabel(
                "<html><div style='width:285px'>Tipos registrados: "
                        + "<span style='color:#34D399'>CREAR</span>, "
                        + "<span style='color:#FBBF24'>MODIFICAR</span>, "
                        + "<span style='color:#F87171'>CANCELAR</span> y "
                        + "<span style='color:#60A5FA'>CERRAR</span>.</div></html>");
        tipos.setFont(Tema.FUENTE_PEQUENA);
        tipos.setForeground(Tema.TEXTO_SEC);
        tipos.setAlignmentX(LEFT_ALIGNMENT);
        tarjeta.add(tipos);

        tarjeta.add(Box.createVerticalGlue());
        JLabel nota = Tema.etiquetaPequena("La pila es temporal: no se guarda en PostgreSQL.");
        nota.setAlignmentX(LEFT_ALIGNMENT);
        tarjeta.add(nota);
        return tarjeta;
    }

    private JPanel lineaComplejidad(String operacion, String costo) {
        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 1));
        fila.setOpaque(false);
        JLabel izq = new JLabel(operacion + ": ");
        izq.setFont(Tema.MONO);
        izq.setForeground(Tema.TEXTO_SEC);
        JLabel der = new JLabel(costo);
        der.setFont(Tema.MONO_BOLD);
        der.setForeground(Tema.AMBAR);
        fila.add(izq);
        fila.add(der);
        fila.setAlignmentX(LEFT_ALIGNMENT);
        return fila;
    }

    private void configurarTabla() {
        Tema.configurarTabla(tabla);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] anchos = {100, 100, 80, 110, 110, 250, 130};
        for (int i = 0; i < anchos.length; i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }
        tabla.setDefaultRenderer(Object.class, new RenderizadorHistorial());
    }

    @Override
    public void recargar() {
        actualizar();
    }

    private void actualizar() {
        modelo.setAcciones(gestor.getHistorial().acciones());
        lblTamano.setText(String.valueOf(gestor.getHistorial().tamano()));
        Accion tope = gestor.getHistorial().verTope();
        if (tope == null) {
            lblTope.setText("Sin acciones registradas.");
            lblTope.setForeground(Tema.TEXTO_TENUE);
        } else {
            lblTope.setText("<html><div style='width:285px'>" + tope.getTipo()
                    + " · OM-" + tope.getIdOrden() + " · "
                    + Tema.fechaHora(tope.getFecha()) + "</div></html>");
            lblTope.setForeground(colorAccion(tope.getTipo()));
        }
        lblVacio.setVisible(modelo.getRowCount() == 0);
        actualizarBotones();
    }

    private void actualizarBotones() {
        btnDeshacer.setEnabled(!estaOcupado() && gestor.getHistorial().tamano() > 0);
    }

    private void deshacer() {
        if (estaOcupado()) return;
        Accion tope = gestor.getHistorial().verTope();
        if (tope == null) {
            contexto.mostrarMensaje("No hay acciones previas para deshacer en la pila.",
                    ContextoApp.Nivel.ADVERTENCIA);
            return;
        }

        int respuesta = JOptionPane.showConfirmDialog(this,
                "¿Deshacer la última acción?\n\n"
                        + "Tipo: " + tope.getTipo() + "\n"
                        + "Orden: OM-" + tope.getIdOrden() + "\n\n"
                        + "La reversión se aplicará primero en PostgreSQL. Si falla, la acción "
                        + "permanecerá en la pila.",
                "Confirmar deshacer", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (respuesta != JOptionPane.YES_OPTION) return;

        Accion accion = tope;
        ejecutarOperacion(() -> gestor.deshacer(),
                "Se revirtió la acción " + accion.getTipo() + " de la orden OM-"
                        + accion.getIdOrden() + ".");
    }

    private Color colorAccion(Accion.Tipo tipo) {
        switch (tipo) {
            case CREAR: return Tema.VERDE;
            case MODIFICAR: return Tema.AMBAR;
            case CANCELAR: return Tema.ROJO;
            case CERRAR: return Tema.ACENTO_CLARO;
            default: return Tema.TEXTO_SEC;
        }
    }

    @Override
    protected void alCambiarOcupado(boolean ocupado) {
        progreso.setVisible(ocupado);
        actualizarBotones();
    }

    private class RenderizadorHistorial extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean sel,
                                                       boolean foco, int fila, int columna) {
            Component componente = super.getTableCellRendererComponent(
                    tabla, valor, sel, foco, fila, columna);
            setBorder(new EmptyBorder(0, 10, 0, 10));
            setFont(columna == 1 ? Tema.FUENTE_MEDIA : (columna == 0 || columna == 2 ? Tema.MONO : Tema.FUENTE));
            if (!sel) {
                if (fila == 0) {
                    setBackground(Tema.mezclar(Tema.AMBAR, Tema.PANEL, 0.86f));
                    setForeground(columna == 1 ? Tema.AMBAR : Tema.TEXTO);
                } else {
                    setBackground(fila % 2 == 0 ? Tema.PANEL
                            : Tema.mezclar(Tema.PANEL, Tema.PANEL_ALT, 0.45f));
                    setForeground(columna == 1 ? colorAccion(modelo.getAccion(fila).getTipo()) : Tema.TEXTO);
                }
            }
            if (columna == 1) setHorizontalAlignment(CENTER);
            else setHorizontalAlignment(LEFT);
            return componente;
        }
    }

    /** Modelo del historial, con el tope en la primera fila. */
    private static class ModeloHistorial extends AbstractTableModel {
        private static final String[] COLUMNAS = {
                "Posición", "Acción", "Orden", "Equipo", "Estado previo", "Descripción", "Fecha"
        };
        private List<Accion> acciones = new ArrayList<>();

        void setAcciones(Accion[] acciones) {
            this.acciones = new ArrayList<>();
            for (Accion accion : acciones) this.acciones.add(accion);
            fireTableDataChanged();
        }

        Accion getAccion(int fila) {
            return fila >= 0 && fila < acciones.size() ? acciones.get(fila) : null;
        }

        @Override public int getRowCount() { return acciones.size(); }
        @Override public int getColumnCount() { return COLUMNAS.length; }
        @Override public String getColumnName(int columna) { return COLUMNAS[columna]; }

        @Override
        public Object getValueAt(int fila, int columna) {
            Accion accion = acciones.get(fila);
            OrdenMantenimiento orden = accion.getEstadoPrevio();
            switch (columna) {
                case 0: return fila == 0 ? "1 · TOPE" : String.valueOf(fila + 1);
                case 1: return accion.getTipo().name();
                case 2: return "OM-" + accion.getIdOrden();
                case 3: return orden == null ? "—" : orden.getCodigoEquipo();
                case 4: return orden == null ? "—" : orden.getEstado().name();
                case 5: return orden == null ? "—" : Tema.truncar(orden.getDescripcion(), 44);
                case 6: return Tema.fechaHora(accion.getFecha());
                default: return "";
            }
        }
    }
}
