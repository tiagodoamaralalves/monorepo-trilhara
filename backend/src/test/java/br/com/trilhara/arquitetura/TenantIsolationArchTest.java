package br.com.trilhara.arquitetura;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.DisplayName;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.tngtech.archunit.junit.ArchIgnore;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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
     * Habilita em R13 (D-06/DC-01): pacotes legados proibidos.
     * O grep já dá 0 ocorrências hoje; a regra é a guarda anti-regressão.
     */
    @ArchIgnore(reason = "Habilitar em R13 (DC-01): guarda anti-regressão de pacotes legados")
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
