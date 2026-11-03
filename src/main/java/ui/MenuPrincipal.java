package ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import app.GestorOrdenes;
import estructuras.Accion;
import ui.componentes.PanelTarjeta;
import ui.componentes.TareaBD;
import ui.componentes.Tema;
import ui.paneles.PanelColaPrioridad;
import ui.paneles.PanelDashboard;
import ui.paneles.PanelEquipos;
import ui.paneles.PanelEstadisticas;
import ui.paneles.PanelHistorial;
import ui.paneles.PanelOrdenes;

/**
 * Ventana principal del Sistema de Gestion de Mantenimiento Industrial.
 *
 * La interfaz es una capa de presentacion: todas las operaciones se delegan en
 * GestorOrdenes y los paneles nunca ejecutan SQL directamente. La conexion JDBC y
 * la carga inicial se ejecutan fuera del EDT.
 */
public class MenuPrincipal extends JFrame implements ContextoApp {

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contenido = new JPanel(cardLayout);
    private final Map<String, PanelVista> vistas = new LinkedHashMap<>();
    private final Map<String, JButton> botonesNav = new LinkedHashMap<>();

    private final JLabel lblPuntoConexion = new JLabel("●");
    private final JLabel lblEstadoJdbc = new JLabel("JDBC: verificando…");
    private final JLabel lblCola = new JLabel("0");
    private final JLabel lblAvl = new JLabel("0");
    private final JLabel lblPilaBadge = Tema.badge("0", Tema.AMBAR);
    private final JButton btnDeshacer = Tema.botonAdvertencia("Deshacer");
    private final JButton btnRefrescar = Tema.botonSecundario("Actualizar");
    private final JLabel lblMensaje = new JLabel("Listo.");
    private final JLabel lblActualizado = new JLabel(" ");
    private final JLabel lblSesionCabecera = new JLabel("Sesión iniciada " + Tema.hora(LocalDateTime.now()));
    private final JProgressBar progresoConexion = Tema.barraProgreso();

    private final LocalDateTime inicioSesion = LocalDateTime.now();
    private GestorOrdenes gestor;
    private String vistaActiva = "dashboard";
    private boolean conectado;
    private boolean verificandoConexion;
    private boolean deshaciendo;
    private Timer monitorConexion;
    private Timer limpiarMensaje;

    public MenuPrincipal() {
        super("SGMI · Sistema de Gestión de Mantenimiento Industrial");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1160, 720));
        setSize(1380, 860);
        setLocationRelativeTo(null);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { cerrarAplicacion(); }
        });

        JPanel raiz = new JPanel(new BorderLayout());
        raiz.setBackground(Tema.FONDO);
        raiz.add(construirCabecera(), BorderLayout.NORTH);

        JPanel cuerpo = new JPanel(new BorderLayout());
        cuerpo.setOpaque(false);
        cuerpo.add(construirMenuLateral(), BorderLayout.WEST);

        contenido.setBackground(Tema.FONDO);
        contenido.setBorder(new EmptyBorder(18, 20, 14, 20));
        cuerpo.add(contenido, BorderLayout.CENTER);
        raiz.add(cuerpo, BorderLayout.CENTER);
        raiz.add(construirBarraEstado(), BorderLayout.SOUTH);
        setContentPane(raiz);
    }

    public static void main(String[] args) {
        Tema.inicializar();
        SwingUtilities.invokeLater(() -> {
            MenuPrincipal ventana = new MenuPrincipal();
            ventana.setVisible(true);
            ventana.arrancar();
        });
    }

    /** Muestra la ventana y comienza la conexion real con PostgreSQL. */
    public void arrancar() {
        iniciarConexion();
    }

    // ------------------------------------------------------------------ cabecera

    private JPanel construirCabecera() {
        JPanel cabecera = new JPanel(new BorderLayout(16, 0));
        cabecera.setBackground(Tema.FONDO_CABECERA);
        cabecera.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Tema.BORDE),
                new EmptyBorder(12, 20, 12, 20)));

        JPanel marca = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        marca.setOpaque(false);

        JLabel logo = new JLabel("⚙", JLabel.CENTER);
        logo.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 22));
        logo.setForeground(Tema.ACENTO_CLARO);
        logo.setOpaque(true);
        logo.setBackground(Tema.mezclar(Tema.ACENTO, Tema.FONDO, 0.8f));
        logo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tema.mezclar(Tema.ACENTO, Tema.FONDO, 0.5f)),
                new EmptyBorder(4, 8, 4, 8)));
        logo.setPreferredSize(new Dimension(44, 44));
        marca.add(logo);

        JPanel titulos = new JPanel();
        titulos.setOpaque(false);
        titulos.setLayout(new BoxLayout(titulos, BoxLayout.Y_AXIS));
        JLabel titulo = new JLabel("SGMI · Planta Industrial");
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 17));
        titulo.setForeground(Tema.TEXTO);
        JLabel subtitulo = new JLabel(
                "Mantenimiento industrial · PostgreSQL + estructuras propias");
        subtitulo.setFont(Tema.FUENTE_PEQUENA);
        subtitulo.setForeground(Tema.TEXTO_SEC);
        lblSesionCabecera.setFont(Tema.FUENTE_PEQUENA);
        lblSesionCabecera.setForeground(Tema.TEXTO_TENUE);
        titulos.add(titulo);
        titulos.add(Box.createVerticalStrut(1));
        titulos.add(subtitulo);
        titulos.add(Box.createVerticalStrut(1));
        titulos.add(lblSesionCabecera);
        marca.add(titulos);
        cabecera.add(marca, BorderLayout.WEST);

        JPanel derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        derecha.setOpaque(false);
        derecha.add(construirChipConexion());
        derecha.add(construirContador("Cola", lblCola, Tema.ACENTO_CLARO));
        derecha.add(construirContador("AVL", lblAvl, Tema.CIAN));

        JPanel pila = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        pila.setOpaque(false);
        btnDeshacer.setToolTipText("Deshacer la última acción de la pila (LIFO)");
        btnDeshacer.addActionListener(e -> deshacerGlobal());
        pila.add(btnDeshacer);
        pila.add(lblPilaBadge);
        derecha.add(pila);

        btnRefrescar.addActionListener(e -> refrescarVista());
        derecha.add(btnRefrescar);
        cabecera.add(derecha, BorderLayout.EAST);
        return cabecera;
    }

    private JPanel construirChipConexion() {
        JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        chip.setBackground(Tema.FONDO);
        chip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tema.BORDE),
                new EmptyBorder(7, 10, 7, 10)));
        lblPuntoConexion.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        lblPuntoConexion.setForeground(Tema.AMBAR);
        lblEstadoJdbc.setFont(Tema.FUENTE_PEQUENA_BOLD);
        lblEstadoJdbc.setForeground(Tema.TEXTO_SEC);
        chip.add(lblPuntoConexion);
        chip.add(lblEstadoJdbc);
        chip.add(progresoConexion);
        return chip;
    }

    private JPanel construirContador(String texto, JLabel valor, Color color) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setOpaque(false);
        JLabel etiqueta = new JLabel(texto);
        etiqueta.setFont(Tema.FUENTE_PEQUENA);
        etiqueta.setForeground(Tema.TEXTO_SEC);
        valor.setFont(Tema.MONO_BOLD);
        valor.setForeground(color);
        panel.add(etiqueta);
        panel.add(valor);
        return panel;
    }

    // ------------------------------------------------------------------ menu lateral

    private JPanel construirMenuLateral() {
        JPanel lateral = new JPanel();
        lateral.setBackground(Tema.FONDO_CABECERA);
        lateral.setLayout(new BoxLayout(lateral, BoxLayout.Y_AXIS));
        lateral.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Tema.BORDE),
                new EmptyBorder(18, 12, 16, 12)));
        lateral.setPreferredSize(new Dimension(238, 100));

        JLabel navegacion = new JLabel("NAVEGACIÓN");
        navegacion.setFont(Tema.FUENTE_PEQUENA_BOLD);
        navegacion.setForeground(Tema.TEXTO_TENUE);
        navegacion.setBorder(new EmptyBorder(0, 10, 8, 0));
        navegacion.setAlignmentX(LEFT_ALIGNMENT);
        lateral.add(navegacion);

        registrarBotonNav(lateral, "dashboard", "Panel principal");
        registrarBotonNav(lateral, "ordenes", "Órdenes de mantenimiento");
        registrarBotonNav(lateral, "cola", "Cola de prioridad");
        registrarBotonNav(lateral, "equipos", "Equipos e índice AVL");
        registrarBotonNav(lateral, "historial", "Historial de acciones");
        registrarBotonNav(lateral, "estadisticas", "Indicadores y estadísticas");

        lateral.add(Box.createVerticalGlue());
        lateral.add(construirTarjetaRegla());
        return lateral;
    }

    private void registrarBotonNav(JPanel lateral, String clave, String texto) {
        JButton boton = new JButton(texto);
        boton.setFont(Tema.FUENTE_MEDIA);
        boton.setHorizontalAlignment(JButton.LEFT);
        boton.setFocusPainted(false);
        boton.setContentAreaFilled(true);
        boton.setOpaque(true);
        boton.setBackground(Tema.FONDO_CABECERA);
        boton.setForeground(Tema.TEXTO_SEC);
        boton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        boton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, Tema.FONDO_CABECERA),
                new EmptyBorder(9, 12, 9, 10)));
        boton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        boton.setAlignmentX(LEFT_ALIGNMENT);
        boton.addActionListener(e -> cambiarVista(clave));
        boton.setEnabled(false);
        botonesNav.put(clave, boton);
        lateral.add(boton);
        lateral.add(Box.createVerticalStrut(3));
    }

    private PanelTarjeta construirTarjetaRegla() {
        PanelTarjeta tarjeta = new PanelTarjeta(Tema.FONDO, Tema.BORDE, 12);
        tarjeta.setLayout(new BoxLayout(tarjeta, BoxLayout.Y_AXIS));
        tarjeta.setAlignmentX(LEFT_ALIGNMENT);
        tarjeta.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        JLabel titulo = new JLabel("REGLA DE CRITICIDAD");
        titulo.setFont(Tema.FUENTE_PEQUENA_BOLD);
        titulo.setForeground(Tema.ACENTO_CLARO);
        titulo.setAlignmentX(LEFT_ALIGNMENT);
        tarjeta.add(titulo);
        tarjeta.add(Box.createVerticalStrut(7));

        JLabel formula = new JLabel("<html><code>2×Imp + 2×Riesgo<br>+ (2 si no hay respaldo)</code></html>");
        formula.setFont(Tema.MONO);
        formula.setForeground(Tema.TEXTO_SEC);
        formula.setAlignmentX(LEFT_ALIGNMENT);
        tarjeta.add(formula);
        tarjeta.add(Box.createVerticalStrut(9));

        tarjeta.add(lineaRegla("ALTA", "≥ 11 pts o Riesgo 3", Tema.ROJO));
        tarjeta.add(Box.createVerticalStrut(3));
        tarjeta.add(lineaRegla("MEDIA", "7 – 10 pts", Tema.AMBAR));
        tarjeta.add(Box.createVerticalStrut(3));
        tarjeta.add(lineaRegla("BAJA", "≤ 6 pts", Tema.VERDE));
        tarjeta.add(Box.createVerticalStrut(9));

        JLabel nota = new JLabel("<html>El orden de atención y la criticidad<br>los calcula el modelo, no la interfaz.</html>");
        nota.setFont(Tema.FUENTE_PEQUENA);
        nota.setForeground(Tema.TEXTO_TENUE);
        nota.setAlignmentX(LEFT_ALIGNMENT);
        tarjeta.add(nota);
        return tarjeta;
    }

    private JPanel lineaRegla(String nivel, String detalle, Color color) {
        JPanel fila = new JPanel(new BorderLayout(6, 0));
        fila.setOpaque(false);
        fila.setAlignmentX(LEFT_ALIGNMENT);
        fila.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        JLabel izquierda = new JLabel(nivel);
        izquierda.setFont(Tema.FUENTE_PEQUENA_BOLD);
        izquierda.setForeground(color);
        JLabel derecha = new JLabel(detalle);
        derecha.setFont(Tema.FUENTE_PEQUENA);
        derecha.setForeground(Tema.TEXTO_SEC);
        fila.add(izquierda, BorderLayout.WEST);
        fila.add(derecha, BorderLayout.EAST);
        return fila;
    }

    private JPanel construirBarraEstado() {
        JPanel barra = new JPanel(new BorderLayout(12, 0));
        barra.setBackground(Tema.FONDO_CABECERA);
        barra.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Tema.BORDE),
                new EmptyBorder(7, 20, 7, 20)));
        lblMensaje.setFont(Tema.FUENTE_PEQUENA);
        lblMensaje.setForeground(Tema.TEXTO_TENUE);
        lblActualizado.setFont(Tema.FUENTE_PEQUENA);
        lblActualizado.setForeground(Tema.TEXTO_TENUE);
        barra.add(lblMensaje, BorderLayout.WEST);
        barra.add(lblActualizado, BorderLayout.EAST);
        return barra;
    }

    // ------------------------------------------------------------------ conexion y paneles

    private void iniciarConexion() {
        conectado = false;
        lblPuntoConexion.setForeground(Tema.AMBAR);
        lblEstadoJdbc.setText("JDBC: verificando…");
        progresoConexion.setVisible(true);
        btnRefrescar.setEnabled(false);
        btnDeshacer.setEnabled(false);
        mostrarMensaje("Conectando con PostgreSQL y cargando equipos y órdenes pendientes…", Nivel.INFO);

        new SwingWorker<GestorOrdenes, Void>() {
            @Override
            protected GestorOrdenes doInBackground() throws Exception {
                GestorOrdenes nuevo = new GestorOrdenes();
                nuevo.inicializar();
                return nuevo;
            }

            @Override
            protected void done() {
                progresoConexion.setVisible(false);
                try {
                    gestor = get();
                    conectado = true;
                    aplicarEstadoConexion(true);
                    construirPaneles();
                    habilitarNavegacion(true);
                    cambiarVista("dashboard");
                    btnRefrescar.setEnabled(true);
                    actualizarBarraSuperior();
                    iniciarMonitorConexion();
                    mostrarMensaje("Conexión establecida. Equipos en AVL: "
                            + gestor.getEquipos().tamano() + " · órdenes pendientes en cola: "
                            + gestor.getOrdenes().tamano() + ".", Nivel.EXITO);
                } catch (Exception ex) {
                    Throwable causa = ex.getCause() == null ? ex : ex.getCause();
                    aplicarEstadoConexion(false);
                    String detalle = TareaBD.mensajeError(causa);
                    System.err.println("No se pudo iniciar la aplicación: " + detalle);
                    int opcion = JOptionPane.showOptionDialog(MenuPrincipal.this,
                            "No se pudo conectar con PostgreSQL.\n\n" + detalle
                                    + "\n\nVerifique que el servidor esté activo y que existan las "
                                    + "variables de entorno DB_URL, DB_USER y DB_PASSWORD.",
                            "Sin conexión con PostgreSQL", JOptionPane.DEFAULT_OPTION,
                            JOptionPane.ERROR_MESSAGE, null,
                            new Object[]{"Reintentar", "Salir"}, "Reintentar");
                    if (opcion == 0) {
                        iniciarConexion();
                    } else {
                        if (monitorConexion != null) monitorConexion.stop();
                        dispose();
                        System.exit(0);
                    }
                }
            }
        }.execute();
    }

    private void construirPaneles() {
        vistas.clear();
        contenido.removeAll();
        registrarVista("dashboard", new PanelDashboard(gestor, this));
        registrarVista("ordenes", new PanelOrdenes(gestor, this));
        registrarVista("cola", new PanelColaPrioridad(gestor, this));
        registrarVista("equipos", new PanelEquipos(gestor, this));
        registrarVista("historial", new PanelHistorial(gestor, this));
        registrarVista("estadisticas", new PanelEstadisticas(gestor, this));
        contenido.revalidate();
        contenido.repaint();
    }

    private void registrarVista(String clave, PanelVista vista) {
        vistas.put(clave, vista);
        contenido.add((java.awt.Component) vista, clave);
    }

    private void habilitarNavegacion(boolean habilitada) {
        for (JButton boton : botonesNav.values()) boton.setEnabled(habilitada);
    }

    private void cambiarVista(String clave) {
        if (gestor == null || !vistas.containsKey(clave)) return;
        vistaActiva = clave;
        cardLayout.show(contenido, clave);
        actualizarNavegacion();
        PanelVista vista = vistas.get(clave);
        if (vista != null) vista.recargar();
        actualizarBarraSuperior();
    }

    private void actualizarNavegacion() {
        for (Map.Entry<String, JButton> entrada : botonesNav.entrySet()) {
            boolean activo = entrada.getKey().equals(vistaActiva);
            JButton boton = entrada.getValue();
            boton.setBackground(activo ? Tema.mezclar(Tema.ACENTO, Tema.FONDO_CABECERA, 0.82f)
                    : Tema.FONDO_CABECERA);
            boton.setForeground(activo ? Tema.ACENTO_CLARO : Tema.TEXTO_SEC);
            boton.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 3, 0, 0, activo ? Tema.ACENTO
                            : Tema.FONDO_CABECERA),
                    new EmptyBorder(9, 12, 9, 10)));
        }
    }

    private void iniciarMonitorConexion() {
        if (monitorConexion != null) monitorConexion.stop();
        monitorConexion = new Timer(25000, e -> verificarConexion());
        monitorConexion.start();
    }

    private void verificarConexion() {
        if (gestor == null || verificandoConexion) return;
        verificandoConexion = true;
        TareaBD.ejecutar(gestor::conexionActiva,
                activa -> aplicarEstadoConexion(activa),
                e -> aplicarEstadoConexion(false),
                () -> verificandoConexion = false);
    }

    private void aplicarEstadoConexion(boolean activa) {
        boolean estabaConectado = conectado;
        conectado = activa;
        lblPuntoConexion.setForeground(activa ? Tema.VERDE : Tema.ROJO);
        lblEstadoJdbc.setText(activa ? "JDBC: Conectado" : "JDBC: Sin conexión");
        lblEstadoJdbc.setForeground(activa ? Tema.TEXTO_SEC : Tema.ROJO);
        if (estabaConectado && !activa) {
            mostrarMensaje("Se perdió la conexión con PostgreSQL. Revise el servidor y actualice.",
                    Nivel.ERROR);
        }
    }

    private void refrescarVista() {
        if (gestor == null) return;
        mostrarMensaje("Actualizando información desde PostgreSQL…", Nivel.INFO);
        PanelVista vista = vistas.get(vistaActiva);
        if (vista != null) vista.recargar();
        verificarConexion();
    }

    private void deshacerGlobal() {
        if (gestor == null || deshaciendo) return;
        Accion tope = gestor.getHistorial().verTope();
        if (tope == null) {
            mostrarMensaje("No hay acciones previas para deshacer en la pila.", Nivel.ADVERTENCIA);
            return;
        }

        int respuesta = JOptionPane.showConfirmDialog(this,
                "¿Deshacer la última acción de la pila?\n\n"
                        + "Tipo: " + tope.getTipo() + "\n"
                        + "Orden: OM-" + tope.getIdOrden() + "\n\n"
                        + "La reversión se confirma primero en PostgreSQL. Si falla, la acción "
                        + "permanece en la pila.",
                "Confirmar deshacer", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (respuesta != JOptionPane.YES_OPTION) return;

        deshaciendo = true;
        btnDeshacer.setEnabled(false);
        TareaBD.ejecutar(() -> gestor.deshacer(), accion -> {
            if (accion == null) {
                mostrarMensaje(gestor.getUltimoError() != null
                        ? gestor.getUltimoError()
                        : "No se pudo deshacer la última acción.", Nivel.ERROR);
            } else {
                mostrarMensaje("Se revirtió la acción " + accion.getTipo()
                        + " de la orden OM-" + accion.getIdOrden() + ".", Nivel.EXITO);
            }
            actualizarBarraSuperior();
            PanelVista vista = vistas.get(vistaActiva);
            if (vista != null) vista.recargar();
        }, e -> mostrarMensaje("Error al deshacer: " + TareaBD.mensajeError(e), Nivel.ERROR),
                () -> {
                    deshaciendo = false;
                    actualizarBarraSuperior();
                });
    }

    // ------------------------------------------------------------------ ContextoApp

    @Override
    public void mostrarMensaje(String mensaje, Nivel nivel) {
        String prefijo;
        Color color;
        switch (nivel) {
            case EXITO: prefijo = "✓ "; color = Tema.VERDE; break;
            case ADVERTENCIA: prefijo = "⚠ "; color = Tema.AMBAR; break;
            case ERROR: prefijo = "✕ "; color = Tema.ROJO; break;
            default: prefijo = "ℹ "; color = Tema.ACENTO_CLARO; break;
        }
        lblMensaje.setText(prefijo + mensaje);
        lblMensaje.setForeground(color);
        if (limpiarMensaje != null) limpiarMensaje.stop();
        limpiarMensaje = new Timer(9000, e -> {
            lblMensaje.setText("Listo.");
            lblMensaje.setForeground(Tema.TEXTO_TENUE);
        });
        limpiarMensaje.setRepeats(false);
        limpiarMensaje.start();
    }

    @Override
    public void actualizarBarraSuperior() {
        if (gestor == null) return;
        lblCola.setText(String.valueOf(gestor.getOrdenes().tamano()));
        lblAvl.setText(String.valueOf(gestor.getEquipos().tamano()));
        Tema.aplicarBadge(lblPilaBadge, String.valueOf(gestor.getHistorial().tamano()), Tema.AMBAR);
        btnDeshacer.setEnabled(!deshaciendo && gestor.getHistorial().tamano() > 0);
        lblActualizado.setText("Sesión iniciada " + Tema.hora(inicioSesion)
                + " · Actualizado " + Tema.hora(LocalDateTime.now()));
    }

    @Override
    public void abrirVista(String clave) {
        cambiarVista(clave);
    }

    // ------------------------------------------------------------------ cierre

    private void cerrarAplicacion() {
        int respuesta = JOptionPane.showConfirmDialog(this,
                "¿Cerrar la aplicación?\n\nLa pila de acciones solo vive en memoria: "
                        + "las acciones pendientes de deshacer se perderán.",
                "Salir del sistema", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (respuesta != JOptionPane.YES_OPTION) return;
        if (monitorConexion != null) monitorConexion.stop();
        if (gestor != null) gestor.cerrar();
        dispose();
        System.exit(0);
    }
}
