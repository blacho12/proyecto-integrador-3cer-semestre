package ui.componentes;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import com.formdev.flatlaf.FlatDarkLaf;

import modelo.Criticidad;
import modelo.OrdenMantenimiento.Estado;

/**
 * Paleta, tipografias y fabricas de componentes compartidas por la interfaz.
 * Toda la apariencia se concentra aqui para que los paneles solo se ocupen de su contenido.
 */
public final class Tema {

    // ---------------------------------------------------------------- paleta oscura
    public static final Color FONDO = new Color(0x070D18);
    public static final Color FONDO_CABECERA = new Color(0x0B1322);
    public static final Color PANEL = new Color(0x0F1A2B);
    public static final Color PANEL_ALT = new Color(0x111F33);
    public static final Color INPUT = new Color(0x0A1322);
    public static final Color BORDE = new Color(0x1E2C42);
    public static final Color BORDE_SUAVE = new Color(0x17243A);

    public static final Color TEXTO = new Color(0xE6EDF7);
    public static final Color TEXTO_SEC = new Color(0x94A3B8);
    public static final Color TEXTO_TENUE = new Color(0x64748B);

    public static final Color ACENTO = new Color(0x3B82F6);
    public static final Color ACENTO_CLARO = new Color(0x60A5FA);
    public static final Color CIAN = new Color(0x22D3EE);
    public static final Color AMBAR = new Color(0xFBBF24);
    public static final Color VERDE = new Color(0x34D399);
    public static final Color ROJO = new Color(0xF87171);
    public static final Color MORADO = new Color(0xA78BFA);

    public static final Font FUENTE = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FUENTE_MEDIA = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FUENTE_PEQUENA = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FUENTE_PEQUENA_BOLD = new Font("Segoe UI", Font.BOLD, 10);
    public static final Font FUENTE_TITULO = new Font("Segoe UI", Font.BOLD, 21);
    public static final Font FUENTE_SUBTITULO = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FUENTE_VALOR = new Font("Segoe UI", Font.BOLD, 27);
    public static final Font MONO = new Font("Consolas", Font.PLAIN, 12);
    public static final Font MONO_BOLD = new Font("Consolas", Font.BOLD, 12);

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private Tema() { /* utilitaria */ }

    /** Aplica FlatDarkLaf y unifica la paleta y los radios de los componentes Swing. */
    public static void inicializar() {
        FlatDarkLaf.setup();

        UIManager.put("defaultFont", FUENTE);
        UIManager.put("Panel.background", FONDO);
        UIManager.put("Viewport.background", FONDO);
        UIManager.put("Label.foreground", TEXTO);
        UIManager.put("Button.foreground", TEXTO);
        UIManager.put("Button.font", FUENTE_MEDIA);

        UIManager.put("Table.background", PANEL);
        UIManager.put("Table.foreground", TEXTO);
        UIManager.put("Table.font", FUENTE);
        UIManager.put("Table.rowHeight", 30);
        UIManager.put("Table.gridColor", BORDE_SUAVE);
        UIManager.put("Table.selectionBackground", new Color(0x1D4ED8));
        UIManager.put("Table.selectionForeground", Color.WHITE);
        UIManager.put("TableHeader.background", PANEL_ALT);
        UIManager.put("TableHeader.foreground", TEXTO_SEC);
        UIManager.put("TableHeader.font", FUENTE_PEQUENA_BOLD);
        UIManager.put("TableHeader.height", 34);

        UIManager.put("TextField.background", INPUT);
        UIManager.put("TextField.foreground", TEXTO);
        UIManager.put("TextField.font", FUENTE);
        UIManager.put("ComboBox.background", INPUT);
        UIManager.put("ComboBox.foreground", TEXTO);
        UIManager.put("ComboBox.font", FUENTE);
        UIManager.put("TextArea.background", INPUT);
        UIManager.put("TextArea.foreground", TEXTO);
        UIManager.put("TextArea.font", FUENTE);
        UIManager.put("CheckBox.foreground", TEXTO);
        UIManager.put("CheckBox.font", FUENTE);

        UIManager.put("ScrollBar.width", 9);
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("Component.borderColor", BORDE);
        UIManager.put("Component.focusColor", ACENTO);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXTO);
        UIManager.put("OptionPane.messageFont", FUENTE);
        UIManager.put("SplitPane.background", FONDO);
        UIManager.put("SplitPane.dividerSize", 8);
    }

    // ---------------------------------------------------------------- fabricas

    public static JLabel tituloVista(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(FUENTE_TITULO);
        l.setForeground(TEXTO);
        return l;
    }

    public static JLabel subtituloVista(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(FUENTE_SUBTITULO);
        l.setForeground(TEXTO_SEC);
        return l;
    }

    public static JLabel etiqueta(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(FUENTE);
        l.setForeground(TEXTO_SEC);
        return l;
    }

    public static JLabel etiquetaPequena(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(FUENTE_PEQUENA);
        l.setForeground(TEXTO_SEC);
        return l;
    }

    public static JLabel encabezadoSeccion(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(FUENTE_MEDIA);
        l.setForeground(TEXTO);
        return l;
    }

    public static JButton botonPrimario(String texto) {
        return boton(texto, ACENTO, Color.WHITE, ACENTO);
    }

    public static JButton botonSecundario(String texto) {
        return boton(texto, PANEL_ALT, TEXTO, BORDE);
    }

    public static JButton botonAdvertencia(String texto) {
        return boton(texto, new Color(0x3A2A0A), AMBAR, new Color(0x6B4E0B));
    }

    public static JButton botonPeligro(String texto) {
        return boton(texto, new Color(0x3A1518), ROJO, new Color(0x6B2027));
    }

    public static JButton boton(String texto, Color fondo, Color frente, Color borde) {
        JButton b = new JButton(texto);
        b.setFont(FUENTE_MEDIA);
        b.setBackground(fondo);
        b.setForeground(frente);
        b.setFocusPainted(false);
        b.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borde),
                new EmptyBorder(7, 14, 7, 14)));
        return b;
    }

    /** Etiqueta con apariencia de chip/badge, sin bordes redondeados personalizados. */
    public static JLabel badge(String texto, Color color) {
        JLabel l = new JLabel(texto, JLabel.CENTER);
        l.setFont(FUENTE_PEQUENA_BOLD);
        l.setOpaque(true);
        aplicarBadge(l, texto, color);
        return l;
    }

    /** Actualiza texto y colores de un badge existente sin crear otro componente. */
    public static void aplicarBadge(JLabel l, String texto, Color color) {
        l.setText(texto);
        l.setForeground(color);
        l.setBackground(mezclar(color, FONDO, 0.82f));
        l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(mezclar(color, FONDO, 0.55f)),
                new EmptyBorder(3, 8, 3, 8)));
    }

    /** Cabecera estandar de una vista: titulo, subtitulo y espacio para indicadores. */
    public static JPanel cabeceraVista(String titulo, String subtitulo, JComponent derecha) {
        JPanel cabecera = new JPanel(new java.awt.BorderLayout(12, 0));
        cabecera.setOpaque(false);
        JPanel textos = new JPanel();
        textos.setOpaque(false);
        textos.setLayout(new javax.swing.BoxLayout(textos, javax.swing.BoxLayout.Y_AXIS));
        textos.add(tituloVista(titulo));
        textos.add(javax.swing.Box.createVerticalStrut(3));
        textos.add(subtituloVista(subtitulo));
        cabecera.add(textos, java.awt.BorderLayout.WEST);
        if (derecha != null) {
            JPanel zona = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
            zona.setOpaque(false);
            zona.add(derecha);
            cabecera.add(zona, java.awt.BorderLayout.EAST);
        }
        return cabecera;
    }

    public static javax.swing.JProgressBar barraProgreso() {
        javax.swing.JProgressBar barra = new javax.swing.JProgressBar();
        barra.setIndeterminate(true);
        barra.setVisible(false);
        barra.setPreferredSize(new Dimension(120, 6));
        barra.setBackground(PANEL_ALT);
        barra.setForeground(ACENTO);
        barra.setBorder(BorderFactory.createEmptyBorder());
        return barra;
    }

    public static JPanel campo(String texto, JComponent componente) {
        JPanel p = new JPanel(new java.awt.BorderLayout(0, 5));
        p.setOpaque(false);
        JLabel l = new JLabel(texto);
        l.setFont(FUENTE_PEQUENA_BOLD);
        l.setForeground(TEXTO_SEC);
        p.add(l, java.awt.BorderLayout.NORTH);
        p.add(componente, java.awt.BorderLayout.CENTER);
        return p;
    }

    public static JScrollPane scroll(Component vista) {
        JScrollPane sp = new JScrollPane(vista);
        sp.setBorder(BorderFactory.createLineBorder(BORDE_SUAVE));
        sp.getViewport().setBackground(PANEL);
        sp.setBackground(PANEL);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    public static void configurarTabla(JTable tabla) {
        tabla.setFont(FUENTE);
        tabla.setForeground(TEXTO);
        tabla.setBackground(PANEL);
        tabla.setRowHeight(31);
        tabla.setShowHorizontalLines(true);
        tabla.setShowVerticalLines(false);
        tabla.setGridColor(BORDE_SUAVE);
        tabla.setIntercellSpacing(new Dimension(0, 1));
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setFillsViewportHeight(true);
        tabla.setAutoCreateRowSorter(false);
        tabla.setDefaultRenderer(Object.class, new RenderizadorBase());
        tabla.getTableHeader().setReorderingAllowed(false);
        tabla.getTableHeader().setPreferredSize(new Dimension(0, 34));
        tabla.getTableHeader().setFont(FUENTE_PEQUENA_BOLD);
        tabla.getTableHeader().setBackground(PANEL_ALT);
        tabla.getTableHeader().setForeground(TEXTO_SEC);
    }

    public static void placeholder(JTextField campo, String texto) {
        campo.putClientProperty("JTextField.placeholderText", texto);
    }

    public static void margen(JComponent componente, int arriba, int izquierda, int abajo, int derecha) {
        componente.setBorder(new EmptyBorder(arriba, izquierda, abajo, derecha));
    }

    public static Insets insets(int arriba, int izquierda, int abajo, int derecha) {
        return new Insets(arriba, izquierda, abajo, derecha);
    }

    // ---------------------------------------------------------------- colores de dominio

    public static Color colorCriticidad(Criticidad c) {
        if (c == null) return TEXTO_SEC;
        switch (c) {
            case ALTA: return ROJO;
            case MEDIA: return AMBAR;
            default: return VERDE;
        }
    }

    public static Color colorEstado(Estado e) {
        if (e == null) return TEXTO_SEC;
        switch (e) {
            case PENDIENTE: return ACENTO_CLARO;
            case CERRADA: return VERDE;
            default: return ROJO;
        }
    }

    /** Renderizador de la columna Criticidad (texto coloreado, sin saturar la fila). */
    public static TableCellRenderer renderizadorCriticidad() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean sel,
                                                           boolean foco, int fila, int columna) {
                super.getTableCellRendererComponent(tabla, valor, sel, foco, fila, columna);
                setHorizontalAlignment(CENTER);
                setFont(FUENTE_MEDIA);
                if (!sel) {
                    Criticidad c;
                    try { c = Criticidad.valueOf(String.valueOf(valor)); }
                    catch (IllegalArgumentException ex) { c = null; }
                    setForeground(colorCriticidad(c));
                }
                return this;
            }
        };
    }

    /** Renderizador de la columna Estado. */
    public static TableCellRenderer renderizadorEstado() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean sel,
                                                           boolean foco, int fila, int columna) {
                super.getTableCellRendererComponent(tabla, valor, sel, foco, fila, columna);
                setHorizontalAlignment(CENTER);
                setFont(FUENTE_PEQUENA_BOLD);
                if (!sel) {
                    Estado e;
                    try { e = Estado.valueOf(String.valueOf(valor)); }
                    catch (IllegalArgumentException ex) { e = null; }
                    setForeground(colorEstado(e));
                }
                return this;
            }
        };
    }

    public static TableCellRenderer renderizadorMono(Color color) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean sel,
                                                           boolean foco, int fila, int columna) {
                super.getTableCellRendererComponent(tabla, valor, sel, foco, fila, columna);
                setFont(MONO);
                if (!sel) setForeground(color);
                return this;
            }
        };
    }

    // ---------------------------------------------------------------- formato

    public static String fechaHora(LocalDateTime fecha) {
        return fecha == null ? "—" : FECHA_HORA.format(fecha);
    }

    public static String hora(LocalDateTime fecha) {
        return fecha == null ? "—" : HORA.format(fecha);
    }

    public static String truncar(String texto, int maximo) {
        if (texto == null) return "";
        return texto.length() <= maximo ? texto : texto.substring(0, Math.max(0, maximo - 1)) + "…";
    }

    /** Mezcla lineal de dos colores; factor 0 devuelve a, 1 devuelve b. */
    public static Color mezclar(Color a, Color b, float factor) {
        float f = Math.max(0f, Math.min(1f, factor));
        return new Color(
                Math.round(a.getRed() * (1 - f) + b.getRed() * f),
                Math.round(a.getGreen() * (1 - f) + b.getGreen() * f),
                Math.round(a.getBlue() * (1 - f) + b.getBlue() * f));
    }

    /** Renderizador base: alterna filas y conserva el color de seleccion de FlatLaf. */
    private static class RenderizadorBase extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean sel,
                                                       boolean foco, int fila, int columna) {
            super.getTableCellRendererComponent(tabla, valor, sel, foco, fila, columna);
            setFont(FUENTE);
            if (!sel) {
                setBackground(fila % 2 == 0 ? PANEL : mezclar(PANEL, PANEL_ALT, 0.45f));
                setForeground(TEXTO);
            }
            setBorder(new EmptyBorder(0, 10, 0, 10));
            return this;
        }
    }
}
