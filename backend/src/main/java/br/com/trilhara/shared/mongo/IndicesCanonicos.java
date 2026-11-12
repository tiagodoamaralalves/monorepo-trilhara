package br.com.trilhara.shared.mongo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.exists;

import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

/**
 * Tabela canônica e única de índices do Trilhara (D-07).
 *
 * <p>Fonte normativa de <em>todos</em> os índices do sistema. Qualquer índice criado fora
 * desta tabela é drift (reportado em {@code WARN} e no health, sem remoção automática).
 * A tabela cobre o plano F1 (itens 1–19) acrescido de D-08 ({@code tokens_handoff_sso}),
 * D-24 ({@code series_recorrentes.origemOperacaoId}) e D-36 ({@code lembretes_push}).</p>
 *
 * <p>Convenção de nomes: {@code idx_{colecao}_{campos}} para índices comuns e
 * {@code uk_{colecao}_{campos}} para únicos (inclusive únicos parciais).</p>
 */
public final class IndicesCanonicos {

    /** TTL imediato ({@code expireAfterSeconds: 0}) sobre {@code expiraEm}. */
    public static final int TTL_IMEDIATO = 0;

    private IndicesCanonicos() {
    }

    /**
     * Um índice declarado da tabela canônica.
     *
     * @param colecao       coleção do inventário (D-08)
     * @param nome          nome canônico do índice
     * @param chaves        especificação de chaves
     * @param unico         {@code true} para índices únicos (inclusive parciais)
     * @param ttlSegundos   {@code expireAfterSeconds}; {@code null} quando não é TTL
     * @param filtroParcial {@code partialFilterExpression}; {@code null} quando integral
     * @param idioma        {@code default_language} (apenas índices de texto)
     * @param pesos         {@code weights} (apenas índices de texto)
     */
    public record IndiceCanonico(
            ColecaoTrilhara colecao,
            String nome,
            Bson chaves,
            boolean unico,
            Integer ttlSegundos,
            Bson filtroParcial,
            String idioma,
            Document pesos) {

        public IndexOptions opcoes() {
            IndexOptions opcoes = new IndexOptions().name(nome);
            if (unico) {
                opcoes.unique(true);
            }
            if (ttlSegundos != null) {
                opcoes.expireAfter(ttlSegundos.longValue(), java.util.concurrent.TimeUnit.SECONDS);
            }
            if (filtroParcial != null) {
                opcoes.partialFilterExpression(filtroParcial);
            }
            if (idioma != null) {
                opcoes.defaultLanguage(idioma);
            }
            if (pesos != null) {
                opcoes.weights(pesos);
            }
            return opcoes;
        }
    }

    private static IndiceCanonico simples(ColecaoTrilhara colecao, String nome, Bson chaves) {
        return new IndiceCanonico(colecao, nome, chaves, false, null, null, null, null);
    }

    private static IndiceCanonico unico(ColecaoTrilhara colecao, String nome, Bson chaves) {
        return new IndiceCanonico(colecao, nome, chaves, true, null, null, null, null);
    }

    private static IndiceCanonico unicoParcial(ColecaoTrilhara colecao, String nome, Bson chaves, Bson filtro) {
        return new IndiceCanonico(colecao, nome, chaves, true, null, filtro, null, null);
    }

    private static IndiceCanonico ttl(ColecaoTrilhara colecao, String nome, Bson chaves) {
        return new IndiceCanonico(colecao, nome, chaves, false, TTL_IMEDIATO, null, null, null);
    }

    /**
     * Índice não-único com {@code partialFilterExpression}.
     *
     * <p>Usado quando só um subconjunto de documentos precisa ser indexado — por exemplo os
     * tombstones de sessão ({@code deletedAt} presente), que são consultados apenas pela purga
     * horária e não devem pesar na maioria dos documentos vivos.</p>
     */
    private static IndiceCanonico parcial(ColecaoTrilhara colecao, String nome, Bson chaves, Bson filtro) {
        return new IndiceCanonico(colecao, nome, chaves, false, null, filtro, null, null);
    }

    private static Bson composto(Bson... partes) {
        return Indexes.compoundIndex(partes);
    }

    private static Bson asc(String campo) {
        return Indexes.ascending(campo);
    }

    private static Bson desc(String campo) {
        return Indexes.descending(campo);
    }

    /** Tabela canônica completa: 44 índices em 20 coleções. */
    public static List<IndiceCanonico> todos() {
        return List.of(
                // 1. usuarios
                unico(ColecaoTrilhara.USUARIOS, "uk_usuarios_email", asc("email")),
                simples(ColecaoTrilhara.USUARIOS, "idx_usuarios_criadoEm", desc("criadoEm")),

                // 2. refresh_tokens
                unico(ColecaoTrilhara.REFRESH_TOKENS, "uk_refresh_tokens_tokenHash", asc("tokenHash")),
                simples(ColecaoTrilhara.REFRESH_TOKENS, "idx_refresh_tokens_usuarioId_familiaId",
                        composto(asc("usuarioId"), asc("familiaId"))),
                ttl(ColecaoTrilhara.REFRESH_TOKENS, "idx_refresh_tokens_expiraEm", asc("expiraEm")),

                // 3. tokens_recuperacao_senha
                unico(ColecaoTrilhara.TOKENS_RECUPERACAO_SENHA,
                        "uk_tokens_recuperacao_senha_tokenHash", asc("tokenHash")),
                simples(ColecaoTrilhara.TOKENS_RECUPERACAO_SENHA,
                        "idx_tokens_recuperacao_senha_usuarioId", asc("usuarioId")),
                ttl(ColecaoTrilhara.TOKENS_RECUPERACAO_SENHA,
                        "idx_tokens_recuperacao_senha_expiraEm", asc("expiraEm")),

                // 4. tokens_confirmacao_email
                unico(ColecaoTrilhara.TOKENS_CONFIRMACAO_EMAIL,
                        "uk_tokens_confirmacao_email_tokenHash", asc("tokenHash")),
                simples(ColecaoTrilhara.TOKENS_CONFIRMACAO_EMAIL,
                        "idx_tokens_confirmacao_email_usuarioId", asc("usuarioId")),
                ttl(ColecaoTrilhara.TOKENS_CONFIRMACAO_EMAIL,
                        "idx_tokens_confirmacao_email_expiraEm", asc("expiraEm")),

                // 5. tokens_handoff_sso (D-08)
                unico(ColecaoTrilhara.TOKENS_HANDOFF_SSO,
                        "uk_tokens_handoff_sso_codigoHash", asc("codigoHash")),
                simples(ColecaoTrilhara.TOKENS_HANDOFF_SSO,
                        "idx_tokens_handoff_sso_usuarioId", asc("usuarioId")),
                ttl(ColecaoTrilhara.TOKENS_HANDOFF_SSO,
                        "idx_tokens_handoff_sso_expiraEm", asc("expiraEm")),

                // 6. editais (catálogo público, gerido por ADMIN)
                simples(ColecaoTrilhara.EDITAIS, "idx_editais_status_esfera_atualizadoEm",
                        composto(asc("status"), asc("esfera"), desc("atualizadoEm"))),

                // 7. trilhas
                simples(ColecaoTrilhara.TRILHAS, "idx_trilhas_usuarioId_atualizadoEm",
                        composto(asc("usuarioId"), desc("atualizadoEm"))),
                simples(ColecaoTrilhara.TRILHAS, "idx_trilhas_usuarioId_origemEditalId",
                        composto(asc("usuarioId"), asc("origemEditalId"))),

                // 8. disciplinas
                simples(ColecaoTrilhara.DISCIPLINAS, "idx_disciplinas_usuarioId_trilhaId_ordem",
                        composto(asc("usuarioId"), asc("trilhaId"), asc("ordem"))),

                // 9. progresso_topicos
                unico(ColecaoTrilhara.PROGRESSO_TOPICOS,
                        "uk_progresso_topicos_usuarioId_trilhaId_topicoId",
                        composto(asc("usuarioId"), asc("trilhaId"), asc("topicoId"))),

                // 10. cronogramas
                unico(ColecaoTrilhara.CRONOGRAMAS,
                        "uk_cronogramas_usuarioId_trilhaId_modalidade",
                        composto(asc("usuarioId"), asc("trilhaId"), asc("modalidade"))),
                unicoParcial(ColecaoTrilhara.CRONOGRAMAS,
                        "uk_cronogramas_usuarioId_trilhaId_ativo",
                        composto(asc("usuarioId"), asc("trilhaId")), eq("ativo", true)),

                // 11. blocos_cronograma
                unico(ColecaoTrilhara.BLOCOS_CRONOGRAMA,
                        "uk_blocos_cronograma_usuarioId_trilhaId_cronogramaId_versaoGeracao_data_ordem",
                        composto(asc("usuarioId"), asc("trilhaId"), asc("cronogramaId"),
                                asc("versaoGeracao"), asc("data"), asc("ordem"))),
                simples(ColecaoTrilhara.BLOCOS_CRONOGRAMA,
                        "idx_blocos_cronograma_usuarioId_data_concluido",
                        composto(asc("usuarioId"), asc("data"), asc("concluido"))),
                simples(ColecaoTrilhara.BLOCOS_CRONOGRAMA,
                        "idx_blocos_cronograma_usuarioId_trilhaId_cronogramaId_versaoGeracao_concluido_data",
                        composto(asc("usuarioId"), asc("trilhaId"), asc("cronogramaId"),
                                asc("versaoGeracao"), asc("concluido"), asc("data"))),

                // 12. sessoes
                simples(ColecaoTrilhara.SESSOES,
                        "idx_sessoes_usuarioId_trilhaId_dataSessao_criadoEm_id",
                        composto(asc("usuarioId"), asc("trilhaId"), desc("dataSessao"),
                                desc("criadoEm"), desc("_id"))),
                simples(ColecaoTrilhara.SESSOES,
                        "idx_sessoes_usuarioId_trilhaId_disciplinaId_dataSessao",
                        composto(asc("usuarioId"), asc("trilhaId"), asc("disciplinaId"),
                                desc("dataSessao"))),
                unicoParcial(ColecaoTrilhara.SESSOES, "uk_sessoes_usuarioId_chaveIdempotencia",
                        composto(asc("usuarioId"), asc("chaveIdempotencia")),
                        exists("chaveIdempotencia", true)),
                // Consulta exclusiva da purga de tombstones (D-15): só documentos com deletedAt.
                parcial(ColecaoTrilhara.SESSOES, "idx_sessoes_deletedAt", asc("deletedAt"),
                        exists("deletedAt", true)),

                // 13. listas_tarefas
                simples(ColecaoTrilhara.LISTAS_TAREFAS,
                        "idx_listas_tarefas_usuarioId_deletedAt_ordem",
                        composto(asc("usuarioId"), asc("deletedAt"), asc("ordem"))),

                // 14. series_recorrentes
                simples(ColecaoTrilhara.SERIES_RECORRENTES,
                        "idx_series_recorrentes_usuarioId_deletedAt_ativa",
                        composto(asc("usuarioId"), asc("deletedAt"), asc("ativa"))),
                unicoParcial(ColecaoTrilhara.SERIES_RECORRENTES,
                        "uk_series_recorrentes_usuarioId_origemOperacaoId",
                        composto(asc("usuarioId"), asc("origemOperacaoId")),
                        exists("origemOperacaoId", true)),

                // 15. tarefas
                simples(ColecaoTrilhara.TAREFAS,
                        "idx_tarefas_usuarioId_deletedAt_listaId_concluida_ordem",
                        composto(asc("usuarioId"), asc("deletedAt"), asc("listaId"),
                                asc("concluida"), asc("ordem"))),
                simples(ColecaoTrilhara.TAREFAS,
                        "idx_tarefas_usuarioId_deletedAt_concluida_dataVencimento",
                        composto(asc("usuarioId"), asc("deletedAt"), asc("concluida"),
                                asc("dataVencimento"))),
                unicoParcial(ColecaoTrilhara.TAREFAS,
                        "uk_tarefas_usuarioId_serieId_dataOriginalOcorrencia",
                        composto(asc("usuarioId"), asc("serieId"), asc("dataOriginalOcorrencia")),
                        exists("serieId", true)),

                // 16. cadernos
                simples(ColecaoTrilhara.CADERNOS,
                        "idx_cadernos_usuarioId_deletedAt_atualizadoEm",
                        composto(asc("usuarioId"), asc("deletedAt"), desc("atualizadoEm"))),
                unicoParcial(ColecaoTrilhara.CADERNOS, "uk_cadernos_usuarioId_origemTrilhaId",
                        composto(asc("usuarioId"), asc("origemTrilhaId")),
                        and(eq("deletedAt", null), exists("origemTrilhaId", true))),

                // 17. secoes
                simples(ColecaoTrilhara.SECOES,
                        "idx_secoes_usuarioId_cadernoId_deletedAt_ordem",
                        composto(asc("usuarioId"), asc("cadernoId"), asc("deletedAt"), asc("ordem"))),
                simples(ColecaoTrilhara.SECOES,
                        "idx_secoes_usuarioId_cadernoId_origemDisciplinaId",
                        composto(asc("usuarioId"), asc("cadernoId"), asc("origemDisciplinaId"))),

                // 18. notas
                simples(ColecaoTrilhara.NOTAS,
                        "idx_notas_usuarioId_secaoId_deletedAt_ordem",
                        composto(asc("usuarioId"), asc("secaoId"), asc("deletedAt"), asc("ordem"))),
                simples(ColecaoTrilhara.NOTAS,
                        "idx_notas_usuarioId_cadernoId_deletedAt",
                        composto(asc("usuarioId"), asc("cadernoId"), asc("deletedAt"))),
                new IndiceCanonico(ColecaoTrilhara.NOTAS, "idx_notas_usuarioId_deletedAt_text",
                        composto(asc("usuarioId"), asc("deletedAt"),
                                Indexes.text("titulo"), Indexes.text("conteudoMarkdown")),
                        false, null, null, "portuguese",
                        new Document("titulo", 10).append("conteudoMarkdown", 1)),

                // 19. push_subscriptions
                simples(ColecaoTrilhara.PUSH_SUBSCRIPTIONS,
                        "idx_push_subscriptions_usuarioId", asc("usuarioId")),
                unico(ColecaoTrilhara.PUSH_SUBSCRIPTIONS,
                        "uk_push_subscriptions_endpoint", asc("endpoint")),

                // 20. lembretes_push (D-36)
                unico(ColecaoTrilhara.LEMBRETES_PUSH,
                        "uk_lembretes_push_tarefaId_versaoAgendamento",
                        composto(asc("tarefaId"), asc("versaoAgendamento"))),
                simples(ColecaoTrilhara.LEMBRETES_PUSH,
                        "idx_lembretes_push_estado_instanteEntregaUtc",
                        composto(asc("estado"), asc("instanteEntregaUtc"))),
                simples(ColecaoTrilhara.LEMBRETES_PUSH,
                        "idx_lembretes_push_usuarioId_tarefaId",
                        composto(asc("usuarioId"), asc("tarefaId"))));
    }

    /** Índices declarados para uma coleção, mantendo a ordem da tabela. */
    public static List<IndiceCanonico> porColecao(ColecaoTrilhara colecao) {
        return todos().stream().filter(i -> i.colecao() == colecao).toList();
    }

    /** Nomes canônicos de todos os índices declarados. */
    public static List<String> nomesDeclarados() {
        return todos().stream().map(IndiceCanonico::nome).toList();
    }
}
