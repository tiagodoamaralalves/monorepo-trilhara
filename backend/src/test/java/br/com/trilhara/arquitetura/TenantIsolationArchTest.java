package br.com.trilhara.arquitetura;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.List;
import java.util.Set;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Regras arquiteturais de isolamento de dados por usuário (multi-tenant).
 */
@AnalyzeClasses(packages = "br.com.trilhara")
@DisplayName("Regras arquiteturais de isolamento por tenant")
class TenantIsolationArchTest {

    /**
 * Métodos que recebem a entidade inteira (que carrega {@code usuarioId}) ou que são
 * helpers internos de conversão BSON — não se aplica a regra do primeiro parâmetro.
 */
    private static final Set<String> EXCECOES_POR_ENTIDADE = Set.of(
            "inserir", "inserirEmLote", "inserirLote", "inserirNovaVersao", "atualizar",
            "atualizarOrdem", "salvarOuAtualizar",
            "paraDocumento", "paraEntidade", "paraSessao");

    /**
 * Operações explicitamente multi-tenant de classes {@code @SystemScope}: recebem
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
 * Varreduras multi-tenant do reconciliador de cronogramas ({@code @SystemScope}):
 * recebem apenas o estado de publicação, nunca um {@code usuarioId}.
 */
    private static final Set<String> EXCECOES_DE_SISTEMA_CRONOGRAMA = Set.of(
            "listarPorEstadoPublicacao");

    /**
 * Varreduras da purga da lixeira de tarefas ({@code TarefaPurgaService}, {@code @SystemScope}):
 * operam por {@code expiresAt} em todos os tenants, sem {@code usuarioId}.
 */
    private static final Set<String> EXCECOES_PURGA_TAREFA = Set.of(
            "purgarExpiradas", "listarExpiradas");

    /**
 * Varreduras multi-tenant da purga da lixeira de Notas ({@code NotaPurgaService},
 * {@code @SystemScope}): operam por {@code expiresAt} em todos os tenants, sem
 * {@code usuarioId}.
 */
    private static final Set<String> EXCECOES_PURGA_NOTA = Set.of(
            "purgarExpirados", "listarLixeira", "contarLixeira");

    /**
 * Operação de sistema da entrega de Web Push: {@code removerEndpointInvalido} só é
 * chamada por {@code PushDeliveryScheduler}, que é {@code @SystemScope}.
 */
    private static final Set<String> EXCECOES_SISTEMA_PUSH = Set.of(
            "removerEndpointInvalido");

    /**
 * Nenhum recurso HTTP acessa o MongoDB diretamente; todos os {@code *Resource} delegam a
 * serviços/repositórios.
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
            .because("Recursos HTTP não injetam MongoClient/MongoCollection diretamente.");

    /**
 * Todo {@code *Repository} guarda apenas acesso a dados — nenhum repositório
 * pode depender de {@code ..job..} (job chama repositório, nunca o contrário).
 */
    @ArchTest
    static final ArchRule repositoriosNaoDependemDeJobs = noClasses()
            .that().haveSimpleNameEndingWith("Repository")
            .should().dependOnClassesThat().resideInAPackage("..job..")
            .because("A direção da dependência é job -> repositório, nunca o inverso.");

    /**
 * Todo método de consulta/escrita pública dos repositórios de {@code ..trilha..} recebe
 * {@code ObjectId usuarioId} como primeiro parâmetro, exceto os métodos que recebem a
 * entidade inteira.
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
                    .because("Toda leitura/escrita de coleção privada começa por ObjectId usuarioId.");

    /**
 * Todo método público dos repositórios de {@code ..sessao..} recebe {@code ObjectId usuarioId}
 * como primeiro parâmetro, exceto métodos de entidade/filtro tipado e a purga de tombstones em
 * {@code @SystemScope}.
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
                    .because("O repositório de sessões só consulta por usuarioId, exceto a purga de tombstones em @SystemScope.");

    /**
 * Toda varredura multi-tenant declara {@code @SystemScope} — diretamente na
 * classe ou na classe para a qual ela delega.
 */
    @ArchTest
    static final ArchRule schedulersExigemEscopoDeSistema = classes()
            .that().haveSimpleNameEndingWith("Scheduler")
            .or().haveSimpleNameEndingWith("Job")
            .should().beAnnotatedWith(br.com.trilhara.shared.SystemScope.class)
            .orShould().dependOnClassesThat()
            .areAnnotatedWith(br.com.trilhara.shared.SystemScope.class)
            .because("Job/scheduler que varre todos os tenants é @SystemScope ou delega a um serviço marcado como tal.");

    /**
 * Todo {@code *Repository} do pacote {@code cronograma} recebe
 * {@code ObjectId usuarioId} como primeiro parâmetro, exceto métodos de entidade e as
 * operações do reconciliador {@code @SystemScope}.
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
                    .because("Repositórios de cronograma e blocos só consultam por usuarioId, exceto varreduras @SystemScope.");

    /**
 * Todo {@code *Repository} do pacote {@code tarefa} recebe
 * {@code ObjectId usuarioId} como primeiro parâmetro, exceto métodos de entidade/filtro e
 * varreduras de purga em {@code @SystemScope}.
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
                    .because("As coleções de tarefa são privadas e sempre filtram por usuarioId; só a purga em @SystemScope varre por expiresAt.");

    /**
 * Todo {@code *Repository} do pacote {@code nota} recebe
 * {@code ObjectId usuarioId} como primeiro parâmetro, exceto métodos de entidade e a purga
 * em {@code @SystemScope}.
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
                    .because("Cadernos, seções e notas são coleções privadas e sempre filtram por usuarioId; só a purga em @SystemScope varre por expiresAt.");

    /**
 * Todo {@code *Repository} do pacote {@code push} recebe {@code ObjectId usuarioId} como
 * primeiro parâmetro, exceto conversão BSON e a fila multi-tenant de {@code @SystemScope}.
 */
    @ArchTest
    static final ArchRule repositoriosDePushExigemUsuarioId =
            methods()
                    .that().arePublic()
                    .and().areDeclaredInClassesThat().resideInAPackage("..push..")
                    .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Repository")
                    .and().areDeclaredInClassesThat().areNotAnnotatedWith(
                            br.com.trilhara.shared.SystemScope.class)
                    .should(new ArchCondition<JavaMethod>("ter ObjectId usuarioId como primeiro parâmetro") {
                        @Override
                        public void check(JavaMethod metodo, ConditionEvents eventos) {
                            String nome = metodo.getName();
                            if (EXCECOES_POR_ENTIDADE.contains(nome)
                                    || EXCECOES_SISTEMA_PUSH.contains(nome)
                                    || nome.startsWith("para")) {
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
                    .because("push_subscriptions e lembretes_push são coleções privadas; só a fila @SystemScope varre todos os tenants.");
}
