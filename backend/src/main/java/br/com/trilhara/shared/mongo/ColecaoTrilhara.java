package br.com.trilhara.shared.mongo;

import java.util.Arrays;
import java.util.List;

/**
 * Inventário fechado das coleções do Trilhara (D-08).
 *
 * <p>São <strong>20 coleções: 19 privadas + 1 catálogo público</strong> ({@code editais}).
 * A exclusão de conta percorre {@link #filhasPrivadas()} (18 coleções filhas) e remove
 * {@code usuarios} por último.</p>
 *
 * <p>A ordem de declaração é a ordem de purga: filhas antes de {@code usuarios}.</p>
 */
public enum ColecaoTrilhara {

    USUARIOS("usuarios", true),
    REFRESH_TOKENS("refresh_tokens", true),
    TOKENS_RECUPERACAO_SENHA("tokens_recuperacao_senha", true),
    TOKENS_CONFIRMACAO_EMAIL("tokens_confirmacao_email", true),
    TOKENS_HANDOFF_SSO("tokens_handoff_sso", true),
    TRILHAS("trilhas", true),
    DISCIPLINAS("disciplinas", true),
    PROGRESSO_TOPICOS("progresso_topicos", true),
    CRONOGRAMAS("cronogramas", true),
    BLOCOS_CRONOGRAMA("blocos_cronograma", true),
    SESSOES("sessoes", true),
    LISTAS_TAREFAS("listas_tarefas", true),
    SERIES_RECORRENTES("series_recorrentes", true),
    TAREFAS("tarefas", true),
    CADERNOS("cadernos", true),
    SECOES("secoes", true),
    NOTAS("notas", true),
    PUSH_SUBSCRIPTIONS("push_subscriptions", true),
    LEMBRETES_PUSH("lembretes_push", true),
    EDITAIS("editais", false);

    private final String nome;
    private final boolean privada;

    ColecaoTrilhara(String nome, boolean privada) {
        this.nome = nome;
        this.privada = privada;
    }

    public String nome() {
        return nome;
    }

    public boolean privada() {
        return privada;
    }

    /** 18 coleções filhas privadas — todas as privadas exceto {@code usuarios}. */
    public static List<ColecaoTrilhara> filhasPrivadas() {
        return Arrays.stream(values())
                .filter(c -> c.privada && c != USUARIOS)
                .toList();
    }

    public static List<String> nomesFilhasPrivadas() {
        return filhasPrivadas().stream().map(ColecaoTrilhara::nome).toList();
    }

    public static List<String> nomes() {
        return Arrays.stream(values()).map(ColecaoTrilhara::nome).toList();
    }

    /** Busca tolerante: {@code null} quando o nome não pertence ao inventário. */
    public static ColecaoTrilhara porNome(String nome) {
        for (ColecaoTrilhara colecao : values()) {
            if (colecao.nome.equals(nome)) {
                return colecao;
            }
        }
        return null;
    }
}
