package br.com.trilhara.tarefa;

public enum EscopoRecorrencia {
    ESTA,
    ESTA_E_FUTURAS,
    TODA_A_SERIE;

    /**
     * Resolve o escopo informado.
     *
     * @return o escopo correspondente ou {@code null} quando ausente/desconhecido, para que
     *         o serviço decida entre default (tarefa avulsa) e {@code 400 VALIDATION_ERROR}
     *         (tarefa recorrente). Nunca adivinha um escopo (D-24).
     */
    public static EscopoRecorrencia fromString(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        for (EscopoRecorrencia escopo : values()) {
            if (escopo.name().equalsIgnoreCase(texto.trim())) {
                return escopo;
            }
        }
        return null;
    }
}
