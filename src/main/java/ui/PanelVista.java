package ui;

/** Vista principal que puede recargar sus datos desde la capa de aplicacion. */
public interface PanelVista {

    /** Solicita una recarga asincrona de la informacion de la vista. */
    void recargar();
}
