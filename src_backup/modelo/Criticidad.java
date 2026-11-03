package modelo;

/** Niveles de criticidad y regla de calculo a partir de los criterios del formulario. */
public enum Criticidad {
    BAJA(1), MEDIA(2), ALTA(3);

    private final int peso;
    Criticidad(int peso) { this.peso = peso; }
    public int getPeso() { return peso; }

    /**
     * Puntaje = 2*impacto + 2*riesgoSeguridad + (sinRespaldo ? 2 : 0).
     * impacto y riesgoSeguridad estan en la escala 1 (bajo) a 3 (alto).
     */
    public static int puntaje(int impacto, int riesgoSeguridad, boolean hayRespaldo) {
        return 2 * impacto + 2 * riesgoSeguridad + (hayRespaldo ? 0 : 2);
    }

    /** ALTA si puntaje >= 11 o riesgo de seguridad maximo; MEDIA si puntaje >= 7; BAJA en otro caso. */
    public static Criticidad calcular(int impacto, int riesgoSeguridad, boolean hayRespaldo) {
        int p = puntaje(impacto, riesgoSeguridad, hayRespaldo);
        if (riesgoSeguridad == 3 || p >= 11) return ALTA;
        if (p >= 7) return MEDIA;
        return BAJA;
    }
}
