package br.com.trilhara.tarefa;

public enum EscopoRecorrencia {
    ESTA,
    ESTA_E_FUTURAS,
    TODA_A_SERIE;

    public static EscopoRecorrencia fromString(String texto) {
        if (texto == null || texto.isBlank()) {
            return ESTA;
        }
        for (EscopoRecorrencia escopo : values()) {
            if (escopo.name().equalsIgnoreCase(texto.trim())) {
                return escopo;
            }
        }
        return ESTA;
    }
}
