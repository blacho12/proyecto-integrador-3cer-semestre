package ui.paneles;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

import app.GestorOrdenes;
import modelo.Criticidad;
import modelo.Equipo;
import modelo.OrdenMantenimiento;
import modelo.OrdenMantenimiento.Estado;
import ui.ContextoApp;
import ui.componentes.PanelTarjeta;
import ui.componentes.TarjetaIndicador;
import ui.componentes.Tema;

/** Vista general: totales reales de PostgreSQL y estado de las estructuras en memoria. */
public class PanelDashboard extends PanelBase {

    private final TarjetaIndicador total = new TarjetaIndicador("Órdenes registradas", Tema.ACENTO, "Histórico en PostgreSQL");
    private final TarjetaIndicador pendientes = new TarjetaIndicador("Pendientes", Tema.ACENTO_CLARO, "En la cola de prioridad");
    private final TarjetaIndicador cerradas = new TarjetaIndicador("Cerradas", Tema.VERDE, "Atendidas");
    private final TarjetaIndicador canceladas = new TarjetaIndicador("Canceladas", Tema.ROJO, "Retiradas de la cola");
    private final TarjetaIndicador equipos = new TarjetaIndicador("Equipos registrados", Tema.CIAN, "Índice AVL");
    private final TarjetaIndicador altas = new TarjetaIndicador("Criticidad alta", Tema.ROJO, "Órdenes históricas");
    private final TarjetaIndicador medias = new TarjetaIndicador("Criticidad media", Tema.AMBAR, "Órdenes históricas");
    private final TarjetaIndicador bajas = new TarjetaIndicador("Criticidad baja", Tema.VERDE, "Órdenes históricas");

    private final JLabel avisoVacio = new JLabel();
    private final JLabel topOrden = new JLabel("—");
    private final JLabel topEquipo = new JLabel(" ");
    private final JLabel topDescripcion = new JLabel(" ");
    private final JLabel topCriterios = new JLabel(" ");
    private final JLabel topCriticidad = Tema.badge("SIN PENDIENTES", Tema.TEXTO_TENUE);
    private final JButton btnIrCola = Tema.botonSecundario("Abrir cola de prioridad");
    private final PanelTarjeta tarjetaTop = new PanelTarjeta(
            Tema.PANEL, Tema.mezclar(Tema.ACENTO, Tema.FONDO, 0.45f), 14);

    private final JLabel lblCola = new JLabel("0");
    private final JLabel lblAvl = new JLabel("0");
    private final JLabel lblAltura = new JLabel("0");
    private final JLabel lblPila = new JLabel("0");
    private final JProgressBar progreso = Tema.barraProgreso();

    public PanelDashboard(GestorOrdenes gestor, ContextoApp contexto) {
        super(gestor, contexto);
        setLayout(new BorderLayout(0, 14));
        add(Tema.cabeceraVista("Panel principal",
                "Resumen operativo calculado con las órdenes registradas en PostgreSQL.", progreso),
                BorderLayout.NORTH);
        add(construirCuerpo(), BorderLayout.CENTER);
    }

    private JScrollPane construirCuerpo() {
        JPanel cuerpo = new JPanel();
        cuerpo.setOpaque(false);
        cuerpo.setLayout(new BoxLayout(cuerpo, BoxLayout.Y_AXIS));

        JPanel tarjetas = new JPanel(new GridLayout(2, 4, 12, 12));
        tarjetas.setOpaque(false);
        tarjetas.add(total);
        tarjetas.add(pendientes);
        tarjetas.add(cerradas);
        tarjetas.add(canceladas);
        tarjetas.add(equipos);
        tarjetas.add(altas);
        tarjetas.add(medias);
        tarjetas.add(bajas);
        cuerpo.add(tarjetas);

        avisoVacio.setText("No hay órdenes registradas todavía. Registre la primera desde «Órdenes de mantenimiento».");
        avisoVacio.setFont(Tema.FUENTE);
        avisoVacio.setForeground(Tema.TEXTO_SEC);
        avisoVacio.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tema.BORDE),
                new javax.swing.border.EmptyBorder(10, 12, 10, 12)));
        avisoVacio.setOpaque(true);
        avisoVacio.setBackground(Tema.PANEL_ALT);
        avisoVacio.setVisible(false);
        JPanel filaAviso = new JPanel(new BorderLayout());
        filaAviso.setOpaque(false);
        filaAviso.add(avisoVacio, BorderLayout.CENTER);
        cuerpo.add(Box.createVerticalStrut(14));
        cuerpo.add(filaAviso);

        cuerpo.add(Box.createVerticalStrut(14));
        cuerpo.add(construirTarjetaTope());

        cuerpo.add(Box.createVerticalStrut(14));
        cuerpo.add(construirTarjetaEstructuras());

        JScrollPane scroll = new JScrollPane(cuerpo);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private PanelTarjeta construirTarjetaTope() {
        tarjetaTop.setLayout(new BorderLayout(16, 8));

        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setOpaque(false);
        JLabel titulo = Tema.encabezadoSeccion("Próxima orden a atender");
        JLabel raiz = Tema.badge("TOPE DEL HEAP · O(1)", Tema.ACENTO_CLARO);
        cabecera.add(titulo, BorderLayout.WEST);
        cabecera.add(raiz, BorderLayout.EAST);

        JPanel detalles = new JPanel();
        detalles.setOpaque(false);
        detalles.setLayout(new BoxLayout(detalles, BoxLayout.Y_AXIS));
        topOrden.setFont(Tema.MONO_BOLD.deriveFont(16f));
        topOrden.setForeground(Tema.TEXTO);
        topEquipo.setFont(Tema.FUENTE_MEDIA);
        topEquipo.setForeground(Tema.CIAN);
        topDescripcion.setFont(Tema.FUENTE);
        topDescripcion.setForeground(Tema.TEXTO_SEC);
        topCriterios.setFont(Tema.MONO);
        topCriterios.setForeground(Tema.TEXTO_TENUE);

        JPanel linea = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        linea.setOpaque(false);
        linea.add(topOrden);
        linea.add(topCriticidad);
        linea.setAlignmentX(LEFT_ALIGNMENT);
        topEquipo.setAlignmentX(LEFT_ALIGNMENT);
        topDescripcion.setAlignmentX(LEFT_ALIGNMENT);
        topCriterios.setAlignmentX(LEFT_ALIGNMENT);

        detalles.add(linea);
        detalles.add(Box.createVerticalStrut(4));
        detalles.add(topEquipo);
        detalles.add(Box.createVerticalStrut(4));
        detalles.add(topDescripcion);
        detalles.add(Box.createVerticalStrut(5));
        detalles.add(topCriterios);

        JPanel acciones = new JPanel();
        acciones.setOpaque(false);
        acciones.setLayout(new BoxLayout(acciones, BoxLayout.Y_AXIS));
        btnIrCola.setAlignmentX(CENTER_ALIGNMENT);
        btnIrCola.addActionListener(e -> contexto.abrirVista("cola"));
        acciones.add(btnIrCola);

        tarjetaTop.add(cabecera, BorderLayout.NORTH);
        tarjetaTop.add(detalles, BorderLayout.CENTER);
        tarjetaTop.add(acciones, BorderLayout.EAST);
        return tarjetaTop;
    }

    private PanelTarjeta construirTarjetaEstructuras() {
        PanelTarjeta tarjeta = new PanelTarjeta();
        tarjeta.setLayout(new BorderLayout(0, 12));

        JLabel titulo = Tema.encabezadoSeccion("Estado de las estructuras en memoria");
        JLabel nota = Tema.etiquetaPequena("La pila de acciones es temporal: vive solo durante la sesión.");
        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setOpaque(false);
        cabecera.add(titulo, BorderLayout.WEST);
        cabecera.add(nota, BorderLayout.EAST);
        tarjeta.add(cabecera, BorderLayout.NORTH);

        JPanel estructuras = new JPanel(new GridLayout(1, 3, 12, 0));
        estructuras.setOpaque(false);
        estructuras.add(panelEstructura("COLA DE PRIORIDAD", "Heap binario · O(log n)", lblCola, Tema.ACENTO_CLARO));
        estructuras.add(panelEstructura("ÍNDICE AVL", "Altura actual", lblAltura, Tema.CIAN));
        estructuras.add(panelEstructura("PILA DE ACCIONES", "LIFO · O(1)", lblPila, Tema.AMBAR));
        tarjeta.add(estructuras, BorderLayout.CENTER);
        return tarjeta;
    }

    private JPanel panelEstructura(String titulo, String detalle, JLabel valor, Color color) {
        JPanel panel = new JPanel(new BorderLayout(0, 3));
        panel.setOpaque(true);
        panel.setBackground(Tema.PANEL_ALT);
        panel.setBorder(new javax.swing.border.EmptyBorder(10, 12, 10, 12));

        JLabel t = new JLabel(titulo);
        t.setFont(Tema.FUENTE_PEQUENA_BOLD);
        t.setForeground(color);
        valor.setFont(Tema.FUENTE_VALOR.deriveFont(22f));
        valor.setForeground(Tema.TEXTO);
        JLabel d = Tema.etiquetaPequena(detalle);

        panel.add(t, BorderLayout.NORTH);
        panel.add(valor, BorderLayout.CENTER);
        panel.add(d, BorderLayout.SOUTH);
        return panel;
    }

    @Override
    public void recargar() {
        cargar(gestor::consultarTodasLasOrdenes, this::aplicarOrdenes,
                "No se pudieron cargar los indicadores");
    }

    private void aplicarOrdenes(List<OrdenMantenimiento> ordenes) {
        int pend = 0, cer = 0, can = 0, altasN = 0, mediasN = 0, bajasN = 0;
        for (OrdenMantenimiento o : ordenes) {
            if (o.getEstado() == Estado.PENDIENTE) pend++;
            else if (o.getEstado() == Estado.CERRADA) cer++;
            else if (o.getEstado() == Estado.CANCELADA) can++;

            Criticidad c = o.getCriticidad();
            if (c == Criticidad.ALTA) altasN++;
            else if (c == Criticidad.MEDIA) mediasN++;
            else bajasN++;
        }

        total.setValor(String.valueOf(ordenes.size()));
        pendientes.setValor(String.valueOf(pend));
        cerradas.setValor(String.valueOf(cer));
        canceladas.setValor(String.valueOf(can));
        altas.setValor(String.valueOf(altasN));
        medias.setValor(String.valueOf(mediasN));
        bajas.setValor(String.valueOf(bajasN));

        equipos.setValor(String.valueOf(gestor.getEquipos().tamano()));
        equipos.setDetalle("Índice AVL · altura " + gestor.getEquipos().altura());

        avisoVacio.setVisible(ordenes.isEmpty());
        actualizarTop();
        actualizarEstructuras();
        contexto.actualizarBarraSuperior();
    }

    private void actualizarTop() {
        OrdenMantenimiento primera = gestor.getOrdenes().verPrimera();
        if (primera == null) {
            topOrden.setText("Sin órdenes pendientes");
            topEquipo.setText("No hay elementos en la cola de prioridad.");
            topDescripcion.setText("Los indicadores se actualizan al registrar una orden.");
            topCriterios.setText("Cola vacía · verPrimera() O(1)");
            Tema.aplicarBadge(topCriticidad, "SIN PENDIENTES", Tema.TEXTO_TENUE);
            btnIrCola.setEnabled(false);
            return;
        }

        Equipo equipo = gestor.getEquipos().buscar(primera.getCodigoEquipo());
        int puntaje = Criticidad.puntaje(primera.getImpacto(), primera.getRiesgoSeguridad(), primera.isHayRespaldo());
        topOrden.setText("OM-" + primera.getId() + " · " + primera.getTipo());
        Tema.aplicarBadge(topCriticidad, primera.getCriticidad() + " · " + puntaje + " pts",
                Tema.colorCriticidad(primera.getCriticidad()));
        topEquipo.setText(primera.getCodigoEquipo()
                + (equipo == null ? "" : " — " + equipo.getNombre()));
        topDescripcion.setText("\"" + Tema.truncar(primera.getDescripcion(), 120) + "\"");
        topCriterios.setText("Impacto " + primera.getImpacto() + "/3 · Riesgo "
                + primera.getRiesgoSeguridad() + "/3 · Respaldo "
                + (primera.isHayRespaldo() ? "Sí" : "No (+2 pts)"));
        btnIrCola.setEnabled(true);
    }

    private void actualizarEstructuras() {
        lblCola.setText(String.valueOf(gestor.getOrdenes().tamano()));
        lblAvl.setText(String.valueOf(gestor.getEquipos().tamano()));
        lblAltura.setText(String.valueOf(gestor.getEquipos().altura()));
        lblPila.setText(String.valueOf(gestor.getHistorial().tamano()));
    }

    @Override
    protected void alCambiarOcupado(boolean ocupado) {
        progreso.setVisible(ocupado);
    }
}
