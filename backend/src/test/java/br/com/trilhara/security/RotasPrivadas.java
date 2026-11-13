package br.com.trilhara.security;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.params.provider.Arguments;

/**
 * Allowlist executável das rotas privadas (R12, RI-39).
 *
 * <p>Fonte única para {@link AuthorizationByOwnerTest}: cada rota privada declara
 * como deve ser exercitada com os IDs do usuário A autenticado como usuário B.
 * Rotas ausentes desta tabela <strong>e</strong> das listas {@link #PUBLICAS} /
 * {@link #ADMIN} fazem o teste-guarda falhar — rota nova sem caso não passa em
 * silêncio.</p>
 *
 * <p>Status esperado: {@code 404} quando o identificador vem no <em>path</em>
 * (não revela existência); {@code 400} quando vem no <em>body</em>; listagens sem
 * ID exigem que B não receba nenhum item de A.</p>
 */
public final class RotasPrivadas {

    private RotasPrivadas() {
    }

    /**
     * Caso de auditoria anti-IDOR.
     *
     * @param metodo         verbo HTTP
     * @param caminho        caminho com marcadores ({@code {trilhaA}}, ...)
     * @param comCorpo       se envia corpo JSON
     * @param statusEsperado 404 (ID no path) ou 400 (ID no body)
     * @param descricao      rótulo para o relatório do teste
     */
    public record Caso(String metodo, String caminho, boolean comCorpo,
                       int statusEsperado, String descricao) {
    }

    /** Rotas sem ID de recurso de outro tenant (auth, usuário, push, catálogo público). */
    public static final Set<String> PUBLICAS = Set.of(
            "POST /api/auth/login",
            "POST /api/auth/cadastro",
            "POST /api/auth/confirmar-email",
            "POST /api/auth/recuperar-senha",
            "POST /api/auth/reenviar-confirmacao",
            "POST /api/auth/resetar-senha",
            "POST /api/auth/refresh",
            "POST /api/auth/logout",
            "POST /api/auth/logout-all",
            "POST /api/auth/handoff",
            "POST /api/auth/handoff/consume",
            "GET /api/auth/me",
            "PUT /api/auth/senha",
            "DELETE /api/auth/conta",
            "PUT /api/usuarios/me/preferencias/tema",
            "GET /api/usuarios/me/disponibilidade-semanal",
            "PUT /api/usuarios/me/disponibilidade-semanal",
            "GET /api/push/config",
            "POST /api/push/subscriptions",
            "DELETE /api/push/subscriptions",
            "PUT /api/push/preferencias",
            "GET /api/editais"
    );

    /** Rotas administrativas: 403 sem papel ADMIN (não são anti-IDOR de tenant). */
    public static final Set<String> ADMIN = Set.of(
            "GET /api/admin/editais",
            "POST /api/admin/editais",
            "GET /api/admin/editais/{id}",
            "PUT /api/admin/editais/{id}",
            "DELETE /api/admin/editais/{id}",
            "PATCH /api/admin/editais/{id}/status",
            "GET /api/auth/admin/usuarios",
            "PATCH /api/auth/admin/usuarios/{id}/papeis"
    );

    /** Rotas de sessão de autenticação: sempre do próprio usuário autenticado. */
    public static final Set<String> PROPRIA_CONTA = Set.of(
            "GET /api/auth/sessoes",
            "DELETE /api/auth/sessoes/{familiaId}"
    );

    /** Catálogo público e esvaziamento das lixeiras (escopo = próprio tenant). */
    public static final Set<String> CATALOGO_E_PURGA = Set.of(
            "GET /api/editais/{id}",
            "DELETE /api/notas/lixeira/esvaziar",
            "DELETE /api/tarefas/lixeira/esvaziar"
    );

    /** Listagens sem ID e criações de recurso raiz: B só vê/cria no próprio tenant. */
    public static final Set<String> LISTAGENS = Set.of(
            "GET /api/trilhas",
            "POST /api/trilhas",
            "GET /api/listas-tarefas",
            "POST /api/listas-tarefas",
            "GET /api/tarefas",
            "POST /api/tarefas",
            "GET /api/tarefas/hoje",
            "GET /api/tarefas/lixeira",
            "GET /api/series-recorrentes",
            "POST /api/series-recorrentes",
            "GET /api/cadernos",
            "POST /api/cadernos",
            "GET /api/blocos/hoje",
            "GET /api/notas/busca",
            "GET /api/notas/lixeira"
    );

    public static Stream<Arguments> casos() {
        return casosDeclarados().stream().map(caso -> Arguments.of(
                caso.metodo(), caso.caminho(), caso.comCorpo(),
                caso.statusEsperado(), caso.descricao()));
    }

    public static List<Caso> casosDeclarados() {
        return List.of(

                // ── Trilhas, disciplinas e progresso ──
                new Caso("GET", "/api/trilhas/{trilhaA}", false, 404, "trilha de A: buscar"),
                new Caso("PUT", "/api/trilhas/{trilhaA}", true, 404, "trilha de A: atualizar"),
                new Caso("DELETE", "/api/trilhas/{trilhaA}", false, 404, "trilha de A: excluir"),
                new Caso("GET", "/api/trilhas/{trilhaA}/impacto-exclusao", false, 404,
                        "trilha de A: impacto de exclusão"),
                new Caso("POST", "/api/trilhas/{trilhaA}/disciplinas", true, 404,
                        "trilha de A: criar disciplina"),
                new Caso("PUT", "/api/trilhas/{trilhaA}/disciplinas/reordenar", true, 404,
                        "trilha de A: reordenar disciplinas"),
                new Caso("PUT", "/api/trilhas/{trilhaA}/disciplinas/{disciplinaA}", true, 404,
                        "disciplina de A: atualizar"),
                new Caso("DELETE", "/api/trilhas/{trilhaA}/disciplinas/{disciplinaA}", false, 404,
                        "disciplina de A: excluir"),
                new Caso("PUT", "/api/trilhas/{trilhaA}/topicos/{topicoA}/progresso", true, 404,
                        "tópico de A: marcar progresso"),
                new Caso("DELETE", "/api/trilhas/{trilhaA}/topicos/{topicoA}/progresso", false, 404,
                        "tópico de A: desmarcar progresso"),

                // ── Sessões e estatísticas ──
                new Caso("GET", "/api/trilhas/{trilhaA}/sessoes", false, 404, "sessões de A: listar"),
                new Caso("POST", "/api/trilhas/{trilhaA}/sessoes", true, 404, "sessões de A: registrar"),
                new Caso("PATCH", "/api/trilhas/{trilhaA}/sessoes/{sessaoA}", true, 404,
                        "sessão de A: editar"),
                new Caso("DELETE", "/api/trilhas/{trilhaA}/sessoes/{sessaoA}", false, 404,
                        "sessão de A: excluir"),
                new Caso("DELETE", "/api/trilhas/{trilhaA}/disciplinas/{disciplinaA}/sessoes", false, 404,
                        "disciplina de A: apagar sessões"),
                new Caso("GET", "/api/trilhas/{trilhaA}/estatisticas", false, 404,
                        "trilha de A: estatísticas"),

                // ── Cronogramas e blocos ──
                new Caso("GET", "/api/trilhas/{trilhaA}/cronogramas", false, 404,
                        "cronogramas de A: listar"),
                new Caso("POST", "/api/trilhas/{trilhaA}/cronogramas", true, 404,
                        "cronogramas de A: gerar"),
                new Caso("PUT", "/api/trilhas/{trilhaA}/cronogramas/{cronogramaA}", true, 404,
                        "cronograma de A: atualizar"),
                new Caso("DELETE", "/api/trilhas/{trilhaA}/cronogramas/{cronogramaA}", false, 404,
                        "cronograma de A: excluir"),
                new Caso("PATCH", "/api/trilhas/{trilhaA}/cronogramas/{cronogramaA}/ativar", false, 404,
                        "cronograma de A: ativar"),
                new Caso("GET", "/api/trilhas/{trilhaA}/cronogramas/{cronogramaA}/blocos", false, 404,
                        "cronograma de A: listar blocos"),
                new Caso("POST",
                        "/api/trilhas/{trilhaA}/cronogramas/{cronogramaA}/blocos/concluir-atrasados",
                        true, 404, "cronograma de A: concluir atrasados"),
                new Caso("PATCH", "/api/blocos/{blocoA}/conclusao", true, 404, "bloco de A: concluir"),

                // ── Tarefas, listas e séries ──
                new Caso("GET", "/api/listas-tarefas/{listaA}", false, 404, "lista de A: buscar"),
                new Caso("PUT", "/api/listas-tarefas/{listaA}", true, 404, "lista de A: atualizar"),
                new Caso("DELETE", "/api/listas-tarefas/{listaA}", false, 404, "lista de A: excluir"),
                new Caso("GET", "/api/listas-tarefas/{listaA}/impacto-exclusao", false, 404,
                        "lista de A: impacto de exclusão"),
                new Caso("POST", "/api/listas-tarefas/{listaA}/restaurar", false, 404,
                        "lista de A: restaurar"),
                new Caso("GET", "/api/tarefas/{tarefaA}", false, 404, "tarefa de A: buscar"),
                new Caso("PUT", "/api/tarefas/{tarefaA}", true, 404, "tarefa de A: atualizar"),
                new Caso("DELETE", "/api/tarefas/{tarefaA}", false, 404, "tarefa de A: excluir"),
                new Caso("PATCH", "/api/tarefas/{tarefaA}/conclusao", true, 404, "tarefa de A: concluir"),
                new Caso("PATCH", "/api/tarefas/{tarefaA}/subtarefas/{subtarefaA}", true, 404,
                        "subtarefa de A: alternar"),
                new Caso("POST", "/api/tarefas/{tarefaA}/pular", false, 404, "tarefa de A: pular"),
                new Caso("POST", "/api/tarefas/lixeira/{tarefaA}/restaurar", false, 404,
                        "tarefa de A na lixeira: restaurar"),
                new Caso("GET", "/api/series-recorrentes/{serieA}", false, 404, "série de A: buscar"),
                new Caso("PUT", "/api/series-recorrentes/{serieA}", true, 404, "série de A: atualizar"),
                new Caso("DELETE", "/api/series-recorrentes/{serieA}", false, 404, "série de A: excluir"),

                // ── Notas ──
                new Caso("GET", "/api/cadernos/{cadernoA}/impacto-exclusao", false, 404,
                        "caderno de A: impacto de exclusão"),
                new Caso("GET", "/api/cadernos/{cadernoA}", false, 404, "caderno de A: buscar"),
                new Caso("PUT", "/api/cadernos/{cadernoA}", true, 404, "caderno de A: atualizar"),
                new Caso("DELETE", "/api/cadernos/{cadernoA}", false, 404, "caderno de A: excluir"),
                new Caso("GET", "/api/cadernos/{cadernoA}/secoes", false, 404,
                        "caderno de A: listar seções"),
                new Caso("POST", "/api/cadernos/{cadernoA}/secoes", true, 404,
                        "caderno de A: criar seção"),
                new Caso("PUT", "/api/cadernos/{cadernoA}/secoes/{secaoA}", true, 404,
                        "seção de A: atualizar pelo caderno"),
                new Caso("DELETE", "/api/cadernos/{cadernoA}/secoes/{secaoA}", false, 404,
                        "seção de A: excluir pelo caderno"),
                new Caso("GET", "/api/secoes/{secaoA}", false, 404, "seção de A: buscar"),
                new Caso("PUT", "/api/secoes/{secaoA}", true, 404, "seção de A: atualizar"),
                new Caso("DELETE", "/api/secoes/{secaoA}", false, 404, "seção de A: excluir"),
                new Caso("GET", "/api/secoes/{secaoA}/impacto-exclusao", false, 404,
                        "seção de A: impacto de exclusão"),
                new Caso("GET", "/api/cadernos/{cadernoA}/secoes/{secaoA}/notas", false, 404,
                        "seção de A: listar notas"),
                new Caso("POST", "/api/cadernos/{cadernoA}/secoes/{secaoA}/notas", true, 404,
                        "seção de A: criar nota"),
                new Caso("GET", "/api/notas/{notaA}", false, 404, "nota de A: buscar"),
                new Caso("PUT", "/api/notas/{notaA}", true, 404, "nota de A: atualizar"),
                new Caso("DELETE", "/api/notas/{notaA}", false, 404, "nota de A: excluir"),
                new Caso("POST", "/api/cadernos/importar-trilha/{trilhaA}", false, 404,
                        "trilha de A: importar como caderno"),
                new Caso("POST", "/api/cadernos/{cadernoA}/sincronizar-trilha", false, 404,
                        "caderno de A: sincronizar trilha"),
                new Caso("POST", "/api/notas/lixeira/{tipo}/{notaA}/restaurar", false, 404,
                        "nota de A na lixeira: restaurar")
        );
    }
}

