package br.com.trilhara.tarefa;

/**
 * Modelo explícito de lembrete de tarefa (D-26).
 *
 * <p>O valor ausente de minutos nunca significa "herdar": "sem alerta" é uma escolha
 * própria do usuário. Por isso a decisão é materializada em um campo dedicado, e
 * {@code lembreteMinutosAntes} só é significativo em {@link #PERSONALIZADO}.</p>
 */
public enum ModoLembrete {
    /** Herda a preferência global do usuário (padrão 15 min) e acompanha mudanças. */
    HERDAR,
    /** Não dispara push, independentemente do padrão global. */
    SEM_ALERTA,
    /** Usa {@code lembreteMinutosAntes} ∈ {0,5,15,30,60}. */
    PERSONALIZADO;

    public static ModoLembrete fromString(String texto) {
        if (texto == null || texto.isBlank()) {
            return HERDAR;
        }
        for (ModoLembrete modo : values()) {
            if (modo.name().equalsIgnoreCase(texto.trim())) {
                return modo;
            }
        }
        return HERDAR;
    }
}
