package br.com.trilhara.arquitetura;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import java.util.List;
import java.util.Set;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.junit.ArchIgnore;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Regras arquiteturais de isolamento por tenant (D-06).
 *
 * <p>Escopo R0: apenas o esqueleto. As regras que dependem de refatorações de
 * fases posteriores ficam com {@link ArchIgnore} e a fase que as habilita está
 * registrada na razão da anotação. A regra já ativa serve para provar que o
 * ArchUnit está de fato executando e barrando violações desde a R0.</p>
 */
@AnalyzeClasses(packages = "br.com.trilhara")
@DisplayName("R0 - Regras arquiteturais de isolamento por tenant (esqueleto D-06)")
class TenantIsolationArchTest {

    /**
     * Métodos que recebem a entidade inteira (que carrega {@code usuarioId}) ou que são
     * helpers internos de conversão BSON — não se aplica a regra do primeiro parâmetro.
     */
    private static final Set<String> EXCECOES_POR_ENTIDADE = Set.of(
            "inserir", "inserirEmLote", "inserirLote", "atualizar", "atualizarOrdem",
            "paraDocumento", "paraEntidade", "paraSessao");

    /**
     * Operações explicitamente multi-tenant de classes {@code @SystemScope} (D-06b): recebem
     * apenas o limite temporal e nunca um {@code usuarioId}.
     */
    private static final Set<String> EXCECOES_SISTEMA = Set.of(
            "purgarTombstonesAnteriores");

    /**
     * Operações cujo {@code usuarioId} chega dentro de um filtro composto tipado — o filtro
     * continua começando por {@code usuarioId} e o aplica em {@code Filters.eq}, mas a assinatura
     * recebe o record em vez do {@code ObjectId} solto.
     */
    private static final Set<String> EXCECOES_POR_FILTRO = Set.of(
            "listarPaginado");

    /**
     * Varreduras multi-tenant do reconciliador de cronogramas ({@code @SystemScope}, D-18b):
     * recebem apenas o estado de publicação, nunca um {@code usuarioId}.
     */
    private static final Set<String> EXCECOES_DE_SISTEMA_CRONOGRAMA = Set.of(
            "listarPorEstadoPublicacao");

    /**
     * Varreduras da purga da lixeira de tarefas ({@code TarefaPurgaService}, {@code @SystemScope},
     * D-27): operam por {@code expiresAt} em todos os tenants, sem {@code usuarioId}.
     */
    private static final Set<String> EXCECOES_PURGA_TAREFA = Set.of(
            "purgarExpiradas", "listarExpiradas");

    /**
     * Varreduras multi-tenant da purga da lixeira de Notas ({@code NotaPurgaService},
     * {@code @SystemScope}, D-34): operam por {@code expiresAt} em todos os tenants, sem
     * {@code usuarioId}.
     */
    private static final Set<String> EXCECOES_PURGA_NOTA = Set.of(
            "purgarExpirados", "listarLixeira", "contarLixeira");

    /**
     * Ativa em R0: nenhum recurso HTTP acessa o MongoDB diretamente. Vale hoje
     * (todos os {@code *Resource} usam serviços/repositórios).
     */
    @ArchTest
    static final ArchRule recursosNaoAcessamMongoDiretamente = noClasses()
            .that(simpleNameEndingWith("Resource"))
            .should()
            .dependOnClassesThat()
            .areAssignableTo(MongoClient.class)
            .orShould()
            .dependOnClassesThat()
            .areAssignableTo(MongoCollection.class)
            .because("D-06(c): recurso HTTP não injeta MongoClient/MongoCollection diretamente.");

    /**
     * Habilita em R2 (D-06a): todo {@code *Repository} de coleção privada recebe
     * {@code ObjectId usuarioId} como primeiro parâmetro e filtra por ele.
     * Ignorada agora porque há repositórios multi-tenant legítimos ainda não
     * marcados com {@code @SystemScope} (ex.: MongoIndexInitializer,
     * HandoffService, AccountDeletionService).
     */
    @ArchIgnore(reason = "Habilitar em R2 (D-06a): repositórios ainda sem usuarioId no prefixo")
    @ArchTest
    static final ArchRule repositoriosRecebemUsuarioIdPrimeiro = noClasses()
            .that(simpleNameEndingWith("Repository"))
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..job..")
            .because("D-06(a): assinatura começa com ObjectId usuarioId e filtro por usuarioId.");

    /**
     * Habilitada na R3 (D-06a, escopo do domínio de estudos): todo método de consulta/escrita
     * pública dos repositórios de {@code ..trilha..} recebe {@code ObjectId usuarioId} como
     * primeiro parâmetro, exceto os métodos que recebem a entidade inteira (que já carrega o
     * {@code usuarioId}).
     */
    @ArchTest
    static final ArchRule repositoriosDeTrilhaExigemUsuarioId =
            methods()
                    .that().arePublic()
                    .and().areDeclaredInClassesThat().resideInAPackage("..trilha..")
                    .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Repository")
                    .should(new ArchCondition<JavaMethod>("ter ObjectId usuarioId como primeiro parâmetro") {
                        @Override
                        public void check(JavaMethod metodo, ConditionEvents eventos) {
                            String nome = metodo.getName();
                            if (EXCECOES_POR_ENTIDADE.contains(nome)) {
                                return;
                            }
                            List<JavaParameter> parametros = metodo.getParameters();
                            boolean valido = !parametros.isEmpty()
                                    && parametros.get(0).getRawType().isAssignableTo(ObjectId.class);
                            if (!valido) {
                                eventos.add(SimpleConditionEvent.violated(metodo,
                                        metodo.getFullName() + " não recebe ObjectId usuarioId como primeiro parâmetro"));
                            }
                        }
                    })
                    .because("D-06(a): toda leitura/escrita de coleção privada começa por ObjectId usuarioId.");

    /**
     * Habilitada na R4 (D-06a, escopo de sessões): todo método público dos repositórios de
     * {@code ..sessao..} recebe {@code ObjectId usuarioId} como primeiro parâmetro, exceto
     * {@code inserir}/{@code paraDocumento}/{@code paraSessao} (recebem a entidade, que já
     * carrega o {@code usuarioId}) e {@code purgarTombstonesAnteriores} (operação multi-tenant de
     * {@code SessaoPurgaService}, anotada com {@code @SystemScope}, sem exposição HTTP).
     */
    @ArchTest
    static final ArchRule repositoriosDeSessaoExigemUsuarioId =
            methods()
                    .that().arePublic()
                    .and().areDeclaredInClassesThat().resideInAPackage("..sessao..")
                    .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Repository")
                    .should(new ArchCondition<JavaMethod>("ter ObjectId usuarioId como primeiro parâmetro") {
                        @Override
                        public void check(JavaMethod metodo, ConditionEvents eventos) {
                            String nome = metodo.getName();
                            if (EXCECOES_POR_ENTIDADE.contains(nome)
                                    || EXCECOES_SISTEMA.contains(nome)
                                    || EXCECOES_POR_FILTRO.contains(nome)) {
                                return;
                            }
                            List<JavaParameter> parametros = metodo.getParameters();
                            boolean valido = !parametros.isEmpty()
                                    && parametros.get(0).getRawType().isAssignableTo(ObjectId.class);
                            if (!valido) {
                                eventos.add(SimpleConditionEvent.violated(metodo,
                                        metodo.getFullName() + " não recebe ObjectId usuarioId como primeiro parâmetro"));
                            }
                        }
                    })
                    .because("D-06(a): o repositório de sessoes só consulta por usuarioId, "
                            + "exceto a purga de tombstones em @SystemScope.");

    /**
     * Habilita em R2 (D-06b): acesso multi-tenant apenas em classes
     * {@code @SystemScope} dentro de pacote {@code ..job..}.
     * A anotação {@code @SystemScope} ainda não existe no código.
     */
    @ArchIgnore(reason = "Habilitar em R2 (D-06b): @SystemScope ainda não existe no código")
    @ArchTest
    static final ArchRule acessoMultiTenantSomenteEmSystemScope = noClasses()
            .that().resideInAPackage("..job..")
            .should().bePublic()
            .because("D-06(b): purgas/scheduler/exclusão de conta vivem em ..job.. sem HTTP.");

    /**
     * Ativa em R5 (D-06a): todo {@code *Repository} do pacote {@code cronograma} recebe
     * {@code ObjectId usuarioId} como primeiro parâmetro. Exceções: métodos de conversão
     * ({@code paraDocumento}/{@code paraEntidade}), inserção/atualização da própria entidade
     * (que já carrega {@code usuarioId}) e as operações do reconciliador {@code @SystemScope}
     * ({@code listarPorEstadoPublicacao}, que é multi-tenant por definição).
     */
    @ArchTest
    static final ArchRule repositoriosDeCronogramaExigemUsuarioId =
            methods()
                    .that().arePublic()
                    .and().areDeclaredInClassesThat().resideInAPackage("..cronograma..")
                    .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Repository")
                    .should(new ArchCondition<JavaMethod>("ter ObjectId usuarioId como primeiro parâmetro") {
                        @Override
                        public void check(JavaMethod metodo, ConditionEvents eventos) {
                            String nome = metodo.getName();
                            if (EXCECOES_POR_ENTIDADE.contains(nome)
                                    || EXCECOES_DE_SISTEMA_CRONOGRAMA.contains(nome)) {
                                return;
                            }
                            List<JavaParameter> parametros = metodo.getParameters();
                            boolean valido = !parametros.isEmpty()
                                    && parametros.get(0).getRawType().isAssignableTo(ObjectId.class);
                            if (!valido) {
                                eventos.add(SimpleConditionEvent.violated(metodo,
                                        metodo.getFullName() + " não recebe ObjectId usuarioId como primeiro parâmetro"));
                            }
                        }
                    })
                    .because("D-06(a): repositórios de cronograma e blocos só consultam por usuarioId, "
                            + "exceto as varreduras do reconciliador em @SystemScope.");

    /**
     * Ativa em R7 (D-06a): todo {@code *Repository} do pacote {@code tarefa} recebe
     * {@code ObjectId usuarioId} como primeiro parâmetro. Exceções: conversões BSON
     * ({@code paraDocumento}/{@code paraEntidade}), inserção/atualização da própria entidade
     * (que já carrega {@code usuarioId}) e as varreduras de purga em {@code @SystemScope}
     * ({@code purgarExpiradas}/{@code listarExpiradas}), que são multi-tenant por definição.
     */
    @ArchTest
    static final ArchRule repositoriosDeTarefaExigemUsuarioId =
            methods()
                    .that().arePublic()
                    .and().areDeclaredInClassesThat().resideInAPackage("..tarefa..")
                    .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Repository")
                    .should(new ArchCondition<JavaMethod>("ter ObjectId usuarioId como primeiro parâmetro") {
                        @Override
                        public void check(JavaMethod metodo, ConditionEvents eventos) {
                            String nome = metodo.getName();
                            if (EXCECOES_POR_ENTIDADE.contains(nome)
                                    || EXCECOES_POR_FILTRO.contains(nome)
                                    || EXCECOES_PURGA_TAREFA.contains(nome)) {
                                return;
                            }
                            List<JavaParameter> parametros = metodo.getParameters();
                            boolean valido = !parametros.isEmpty()
                                    && parametros.get(0).getRawType().isAssignableTo(ObjectId.class);
                            if (!valido) {
                                eventos.add(SimpleConditionEvent.violated(metodo,
                                        metodo.getFullName() + " não recebe ObjectId usuarioId como primeiro parâmetro"));
                            }
                        }
                    })
                    .because("D-06(a): as coleções de tarefa são privadas e sempre filtram por usuarioId; "
                            + "só a purga em @SystemScope varre por expiresAt.");

    /**
     * Ativa em R9 (D-06a): todo {@code *Repository} do pacote {@code nota} recebe
     * {@code ObjectId usuarioId} como primeiro parâmetro. Exceções: conversões BSON
     * ({@code paraDocumento}/{@code paraEntidade}), inserção/atualização da própria entidade
     * (que já carrega {@code usuarioId}) e a purga em {@code @SystemScope}
     * ({@code purgarExpirados}), multi-tenant por definição.
     */
    @ArchTest
    static final ArchRule repositoriosDeNotaExigemUsuarioId =
            methods()
                    .that().arePublic()
                    .and().areDeclaredInClassesThat().resideInAPackage("..nota..")
                    .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Repository")
                    .should(new ArchCondition<JavaMethod>("ter ObjectId usuarioId como primeiro parâmetro") {
                        @Override
                        public void check(JavaMethod metodo, ConditionEvents eventos) {
                            String nome = metodo.getName();
                            if (EXCECOES_POR_ENTIDADE.contains(nome)
                                    || EXCECOES_PURGA_NOTA.contains(nome)) {
                                return;
                            }
                            List<JavaParameter> parametros = metodo.getParameters();
                            boolean valido = !parametros.isEmpty()
                                    && parametros.get(0).getRawType().isAssignableTo(ObjectId.class);
                            if (!valido) {
                                eventos.add(SimpleConditionEvent.violated(metodo,
                                        metodo.getFullName()
                                                + " não recebe ObjectId usuarioId como primeiro parâmetro"));
                            }
                        }
                    })
                    .because("D-06(a): cadernos, secoes e notas são coleções privadas e sempre "
                            + "filtram por usuarioId; só a purga em @SystemScope varre por expiresAt.");

    /**
     * Anti-regressão do R9: nenhum repositório expõe busca por ID sem {@code usuarioId}.
     *
     * <p>O método {@code buscarPorIdSemUsuario} existia em três repositórios sem nenhum call site
     * e era uma porta aberta para vazamento entre tenants. A regra impede a reintrodução em
     * qualquer {@code *Repository} do domínio {@code nota}.</p>
     */
    @ArchTest
    static final ArchRule repositoriosDeNotaNaoBuscamSemUsuario =
            noMethods()
                    .that().areDeclaredInClassesThat().resideInAPackage("..nota..")
                    .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Repository")
                    .should().haveNameMatching(".*SemUsuario.*")
                    .because("D-06(a): leitura sem usuarioId revela recurso de outro tenant.");

    /**
     * Habilitada em R2: guarda anti-regressão dos pacotes legados (Anexo C / DC-01).
     * O grep já dá 0 ocorrências; a regra impede reintrodução.
     */
    @ArchTest
    static final ArchRule pacotesLegadosProibidos = noClasses()
            .should()
            .resideInAnyPackage(
                    "br.com.trilhara.estudos..",
                    "br.com.trilhara.tarefas..",
                    "br.com.trilhara.notas..")
            .because("Anexo C: pacotes br.com.trilhara.{estudos,tarefas,notas} são legados.");

    /**
     * Guarda auxiliar: garante que o predicado de pacote continua compilando e
     * que o pacote base existe (uma classe sem pacote indicaria erro de scan).
     */
    @ArchTest
    static final ArchRule pacoteBaseDeveExistir = noClasses()
            .that(resideInAPackage("..arquitetura.."))
            .should()
            .haveSimpleNameEndingWith("Inexistente")
            .because("Sanidade do scan do ArchUnit no pacote br.com.trilhara.");
}
