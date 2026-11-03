package ui.componentes;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;

import javax.swing.JLabel;

/** Tarjeta compacta de indicador: titulo, valor grande y detalle. */
public class TarjetaIndicador extends PanelTarjeta {

    private final JLabel valor = new JLabel("—");
    private final JLabel detalle = new JLabel(" ");
    private final Color acento;

    public TarjetaIndicador(String titulo, Color acento, String detalleInicial) {
        super();
        this.acento = acento;
        setLayout(new BorderLayout(0, 5));
        setPreferredSize(new Dimension(190, 116));

        JLabel tituloLabel = new JLabel(titulo.toUpperCase());
        tituloLabel.setFont(Tema.FUENTE_PEQUENA_BOLD);
        tituloLabel.setForeground(acento);

        valor.setFont(Tema.FUENTE_VALOR);
        valor.setForeground(Tema.TEXTO);

        detalle.setText(detalleInicial);
        detalle.setFont(Tema.FUENTE_PEQUENA);
        detalle.setForeground(Tema.TEXTO_SEC);

        add(tituloLabel, BorderLayout.NORTH);
        add(valor, BorderLayout.CENTER);
        add(detalle, BorderLayout.SOUTH);
    }

    public void setValor(String texto) {
        valor.setText(texto);
    }

    public void setValorColor(Color color) {
        valor.setForeground(color == null ? Tema.TEXTO : color);
    }

    public void setDetalle(String texto) {
        detalle.setText(texto == null ? " " : texto);
    }

    public Color getAcento() {
        return acento;
    }
}
