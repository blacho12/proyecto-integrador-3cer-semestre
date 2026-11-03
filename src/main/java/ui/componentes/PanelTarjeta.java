package ui.componentes;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** Panel con fondo y borde redondeados, base visual de las tarjetas del sistema. */
public class PanelTarjeta extends JPanel {

    private final Color fondo;
    private final Color borde;
    private final int arco;

    public PanelTarjeta() {
        this(Tema.PANEL, Tema.BORDE, 14);
    }

    public PanelTarjeta(Color fondo, Color borde, int arco) {
        this.fondo = fondo;
        this.borde = borde;
        this.arco = arco;
        setOpaque(false);
        setBorder(new EmptyBorder(16, 16, 16, 16));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int ancho = Math.max(1, getWidth() - 1);
        int alto = Math.max(1, getHeight() - 1);
        g2.setColor(fondo);
        g2.fillRoundRect(0, 0, ancho, alto, arco, arco);
        g2.setColor(borde);
        g2.drawRoundRect(0, 0, ancho, alto, arco, arco);
        g2.dispose();
        super.paintComponent(g);
    }
}
