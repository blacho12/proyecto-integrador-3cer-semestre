package ui.componentes;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.swing.JPanel;

import estructuras.ArbolAVL;
import estructuras.NodoAVL;
import modelo.Equipo;

/**
 * Dibuja el arbol AVL real del sistema. No reconstruye ni reordena nada:
 * recorre la raiz y los enlaces del ArbolAVL para ubicar cada nodo.
 */
public class PanelArbolAVL extends JPanel {

    private static final int SEPARACION_X = 132;
    private static final int SEPARACION_Y = 88;
    private static final int ANCHO_NODO = 116;
    private static final int ALTO_NODO = 52;

    private final ArbolAVL arbol;

    public PanelArbolAVL(ArbolAVL arbol) {
        this.arbol = arbol;
        setOpaque(false);
        setPreferredSize(new Dimension(640, 320));
    }

    /** Vuelve a dibujar el arbol despues de una insercion o al recargar la vista. */
    public void actualizar() {
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        NodoAVL raiz = arbol.getRaiz();
        if (raiz == null) {
            g2.setFont(Tema.FUENTE);
            g2.setColor(Tema.TEXTO_TENUE);
            String texto = "El índice AVL está vacío";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(texto, (getWidth() - fm.stringWidth(texto)) / 2, getHeight() / 2);
            g2.dispose();
            return;
        }

        Map<NodoAVL, int[]> posiciones = new IdentityHashMap<>();
        int[] indice = {0};
        int[] profundidadMaxima = {0};
        posicionar(raiz, 0, indice, posiciones, profundidadMaxima);

        int contenidoAncho = Math.max(1, indice[0] * SEPARACION_X);
        int contenidoAlto = Math.max(1, (profundidadMaxima[0] + 1) * SEPARACION_Y);
        int disponibleAncho = Math.max(1, getWidth() - 24);
        int disponibleAlto = Math.max(1, getHeight() - 24);

        double escala = Math.min(1.0, Math.min(disponibleAncho / (double) contenidoAncho,
                disponibleAlto / (double) contenidoAlto));
        double trasladoX = (getWidth() - contenidoAncho * escala) / 2.0;
        double trasladoY = (getHeight() - contenidoAlto * escala) / 2.0;

        g2.translate(trasladoX, trasladoY);
        g2.scale(escala, escala);

        // Enlaces padre-hijo
        g2.setColor(new Color(0x2B3F5C));
        g2.setStroke(new java.awt.BasicStroke(1.4f));
        dibujarEnlaces(g2, raiz, posiciones);

        // Nodos
        g2.setStroke(new java.awt.BasicStroke(1f));
        dibujarNodos(g2, raiz, posiciones);

        g2.dispose();
    }

    private void posicionar(NodoAVL nodo, int profundidad, int[] indice,
                            Map<NodoAVL, int[]> posiciones, int[] profundidadMaxima) {
        if (nodo == null) return;
        posicionar(nodo.getIzquierdo(), profundidad + 1, indice, posiciones, profundidadMaxima);
        posiciones.put(nodo, new int[]{indice[0]++, profundidad});
        profundidadMaxima[0] = Math.max(profundidadMaxima[0], profundidad);
        posicionar(nodo.getDerecho(), profundidad + 1, indice, posiciones, profundidadMaxima);
    }

    private void dibujarEnlaces(Graphics2D g2, NodoAVL nodo, Map<NodoAVL, int[]> posiciones) {
        if (nodo == null) return;
        int[] origen = posiciones.get(nodo);
        if (nodo.getIzquierdo() != null) {
            int[] destino = posiciones.get(nodo.getIzquierdo());
            g2.drawLine(centroX(origen), centroY(origen), centroX(destino), centroY(destino));
        }
        if (nodo.getDerecho() != null) {
            int[] destino = posiciones.get(nodo.getDerecho());
            g2.drawLine(centroX(origen), centroY(origen), centroX(destino), centroY(destino));
        }
        dibujarEnlaces(g2, nodo.getIzquierdo(), posiciones);
        dibujarEnlaces(g2, nodo.getDerecho(), posiciones);
    }

    private void dibujarNodos(Graphics2D g2, NodoAVL nodo, Map<NodoAVL, int[]> posiciones) {
        if (nodo == null) return;
        int[] pos = posiciones.get(nodo);
        int x = pos[0] * SEPARACION_X + (SEPARACION_X - ANCHO_NODO) / 2;
        int y = pos[1] * SEPARACION_Y;

        RoundRectangle2D forma = new RoundRectangle2D.Float(x, y, ANCHO_NODO, ALTO_NODO, 12, 12);
        g2.setColor(new Color(0x0B1A2C));
        g2.fill(forma);
        g2.setColor(new Color(0x1E4E66));
        g2.draw(forma);

        Equipo equipo = nodo.getEquipo();
        g2.setFont(Tema.MONO_BOLD);
        g2.setColor(Tema.CIAN);
        g2.drawString(equipo.getCodigo(), x + 10, y + 21);

        g2.setFont(Tema.FUENTE_PEQUENA);
        g2.setColor(Tema.TEXTO_SEC);
        g2.drawString(Tema.truncar(equipo.getNombre(), 16), x + 10, y + 38);

        int alturaIzquierda = nodo.getIzquierdo() == null ? 0 : nodo.getIzquierdo().getAltura();
        int alturaDerecha = nodo.getDerecho() == null ? 0 : nodo.getDerecho().getAltura();
        int factor = alturaIzquierda - alturaDerecha;
        g2.setFont(new java.awt.Font("Consolas", java.awt.Font.PLAIN, 9));
        g2.setColor(Tema.TEXTO_TENUE);
        g2.drawString("h" + nodo.getAltura() + " fb" + (factor > 0 ? "+" + factor : factor),
                x + 10, y + 49);

        dibujarNodos(g2, nodo.getIzquierdo(), posiciones);
        dibujarNodos(g2, nodo.getDerecho(), posiciones);
    }

    private int centroX(int[] posicion) {
        return posicion[0] * SEPARACION_X + SEPARACION_X / 2;
    }

    private int centroY(int[] posicion) {
        return posicion[1] * SEPARACION_Y + ALTO_NODO / 2;
    }
}
