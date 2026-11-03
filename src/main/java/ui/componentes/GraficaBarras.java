package ui.componentes;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.JPanel;

/**
 * Grafica de barras horizontales sin dependencias externas.
 * Se alimenta exclusivamente con los conteos reales que calcula el panel de estadisticas.
 */
public class GraficaBarras extends JPanel {

    private Map<String, Integer> datos = new LinkedHashMap<>();
    private Map<String, Color> colores = new LinkedHashMap<>();

    public GraficaBarras() {
        setOpaque(false);
        setPreferredSize(new Dimension(340, 210));
    }

    public void setDatos(Map<String, Integer> datos, Map<String, Color> colores) {
        this.datos = datos == null ? new LinkedHashMap<>() : new LinkedHashMap<>(datos);
        this.colores = colores == null ? new LinkedHashMap<>() : new LinkedHashMap<>(colores);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int ancho = getWidth();
        int alto = getHeight();
        if (ancho <= 20 || alto <= 20) { g2.dispose(); return; }

        int total = 0;
        int maximo = 0;
        for (int v : datos.values()) {
            total += v;
            maximo = Math.max(maximo, v);
        }
        if (datos.isEmpty() || total == 0) {
            g2.setFont(Tema.FUENTE);
            g2.setColor(Tema.TEXTO_TENUE);
            String texto = "Sin datos registrados todavía";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(texto, (ancho - fm.stringWidth(texto)) / 2, alto / 2);
            g2.dispose();
            return;
        }

        int margenSuperior = 8;
        int margenInferior = 8;
        int etiquetaAncho = Math.min(132, Math.max(88, ancho / 3));
        int valorAncho = 44;
        int espacio = 4;
        int filas = datos.size();
        int altoFila = Math.max(24, (alto - margenSuperior - margenInferior) / filas);
        int barraMax = Math.max(10, ancho - etiquetaAncho - valorAncho - 16);
        int i = 0;

        for (Map.Entry<String, Integer> entrada : datos.entrySet()) {
            int y = margenSuperior + i * altoFila;
            int base = y + altoFila / 2 + 4;
            int valor = Math.max(0, entrada.getValue());
            Color color = colores.getOrDefault(entrada.getKey(), Tema.ACENTO);

            g2.setFont(Tema.FUENTE_PEQUENA);
            g2.setColor(Tema.TEXTO_SEC);
            String etiqueta = Tema.truncar(entrada.getKey(), 22);
            g2.drawString(etiqueta, 4, base);

            int anchoBarra = maximo == 0 ? 0 : (int) Math.round(valor / (double) maximo * barraMax);
            int x = etiquetaAncho;
            int barraAlto = Math.max(12, altoFila - 10);
            g2.setColor(Tema.mezclar(color, Tema.PANEL, 0.78f));
            g2.fillRoundRect(x, base - barraAlto + 3, barraMax, barraAlto, 7, 7);
            if (anchoBarra > 0) {
                g2.setColor(color);
                g2.fillRoundRect(x, base - barraAlto + 3, Math.max(6, anchoBarra), barraAlto, 7, 7);
            }

            g2.setFont(Tema.MONO_BOLD);
            g2.setColor(valor > 0 ? Tema.TEXTO : Tema.TEXTO_TENUE);
            String textoValor = String.valueOf(valor);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(textoValor, Math.min(ancho - fm.stringWidth(textoValor) - 4, x + anchoBarra + 8), base);
            i++;
        }
        g2.dispose();
    }
}
