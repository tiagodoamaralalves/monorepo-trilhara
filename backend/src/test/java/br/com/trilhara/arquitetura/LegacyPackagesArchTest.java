package br.com.trilhara.arquitetura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import org.junit.jupiter.api.DisplayName;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * R13/D-44 — guarda arquitetural contra reintrodução do legado (Anexo C).
 *
 * <p>Complementa {@code scripts/verificar-legado.sh}: o script varre o texto, esta classe
 * varre o <strong>bytecode</strong>. Um símbolo legado pode entrar no código compilado sem
 * aparecer no texto de origem (constante inlinada, import estático, gerador de código) — e,
 * ao contrário do script, esta regra falha no {@code ./mvnw verify} de qualquer máquina,
 * sem depender de {@code rg}/{@code grep}.</p>
 *
 * <p>A lista de símbolos é a mesma do Anexo C (seção "Legado" e "Nomes não canônicos"),
 * mantida em sincronia por {@code verificar-legado.sh}.</p>
 */
@AnalyzeClasses(packages = "br.com.trilhara")
@DisplayName("R13 - Pacotes e símbolos legados proibidos (D-44 / Anexo C)")
class LegacyPackagesArchTest {

    /** Pacotes removidos pela reestruturação; recriar qualquer um é regressão. */
    @ArchTest
    static final ArchRule pacotesLegadosNaoExistem = noClasses()
            .should().resideInAnyPackage(
                    "br.com.trilhara.estudos..",
                    "br.com.trilhara.tarefas..",
                    "br.com.trilhara.notas..",
                    "br.com.trilhara.planejamento..",
                    "br.com.trilhara.ciclo..")
            .because("Anexo C: esses pacotes foram substituídos por trilha/cronograma/tarefa/nota.");

    /** Nomes de tipo legados (Anexo C — "Legado"). */
    @ArchTest
    static final ArchRule tiposLegadosProibidos = noClasses()
            .should().haveNameMatching(
                    ".*\\.AjusteQuestoes"
                            + "|.*\\.TipoSessao"
                            + "|.*\\.TipoRegistroSessao"
                            + "|.*\\.NovaSessao"
                            + "|.*\\.DisciplinaProgramada"
                            + "|.*\\.CicloAtivoResponse"
                            + "|.*\\.OrigemTarefa"
                            + "|.*\\.EstatisticaDisciplina")
            .because("Anexo C: tipos legados removidos pela reestruturação.");

    /** Prefixos legados de planejamento por ciclo (D-16/DC-15). */
    @ArchTest
    static final ArchRule tiposDeCicloProibidos = noClasses()
            .should().haveNameMatching(".*\\.(PlanejamentoCiclo|AtualizacaoPlanejamentoCiclo).*")
            .because("D-16: o modelo de planejamento por ciclo foi substituído por cronograma.");

    /** Métodos e campos legados (Anexo C — "Legado" e D-02). */
    @ArchTest
    static final ArchRule metodosLegadosProibidos = noMethods()
            .should().haveNameMatching(
                    "removerTarefasDeCicloLegadas"
                            + "|toObjectIdOrNotFound"
                            + "|testFailOnCollectionHook")
            .because("Anexo C: métodos removidos; reintroduzi-los reabre o caminho legado.");

    @ArchTest
    static final ArchRule camposNaoCanonicosProibidos = noFields()
            .should().haveNameMatching(
                    "dataEstudo"
                            + "|ordemDia"
                            + "|duracaoRealSegundos"
                            + "|horarioDisparoUtc"
                            + "|dataTermino"
                            + "|usaPadraoGlobalLembrete")
            .because("D-02: nomes canônicos são dataSessao/duracaoSegundos/instanteEntregaUtc.");

    /** Constantes de fallback inseguro de usuário (D-09/D-21). */
    @ArchTest
    static final ArchRule semFallbackDeUsuarioLocal = classes()
            .should(new ArchCondition<JavaClass>("não declarar USUARIO_LOCAL_PADRAO") {
                @Override
                public void check(JavaClass classe, ConditionEvents eventos) {
                    classe.getFields().stream()
                            .filter(campo -> "USUARIO_LOCAL_PADRAO".equals(campo.getName()))
                            .forEach(campo -> eventos.add(SimpleConditionEvent.violated(campo,
                                    campo.getFullName() + " reintroduz o fallback inseguro de usuário")));
                }
            })
            .because("Anexo C: USUARIO_LOCAL_PADRAO removido; autenticação vem só do token.");
}
