package br.com.trilhara.shared;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.trilhara.auth.TokenService;
import br.com.trilhara.auth.UserRepository;
import br.com.trilhara.auth.Usuario;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * mass assignment parametrizado para <strong>todo</strong> DTO de request.
 *
 * <p>A provou mass assignment em uma única rota ({@code POST /api/trilhas}). Este teste
 * fecha a lacuna enumerando por reflexão todos os records {@code *Request} de
 * {@code br.com.trilhara.**.dto} e verificando que cada um declara <em>apenas</em> campos
 * funcionais: nenhum campo de sistema ({@code usuarioId}, {@code _id}, {@code criadoEm},
 * {@code atualizadoEm}, {@code deletedAt}) é aceito por contrato.</p>
 *
 * <p>O teste é de <strong>contrato de tipo</strong>, não de HTTP: um DTO com
 * {@code ObjectId usuarioId} seria aceito pelo Jackson e o cliente poderia forjar o dono do
 * recurso. A defesa em runtime é {@code quarkus.jackson.fail-on-unknown-properties=true}
 * (provada em {@code MassAssignmentTest}); esta é a defesa que impede o campo de existir.</p>
 */
@QuarkusTest
@DisplayName("Mass assignment parametrizado em todos os DTOs de request")
class MassAssignmentParametrizadoTest {

    /**
 * Campos que jamais podem existir num DTO de entrada: o servidor os deriva.
 *
 * <p>{@code id} <strong>não</strong> entra nesta lista: exigem que o {@code id} de
 * tópico embutido seja uma string estável gerada no cliente ({@code new ObjectId
 * .toHexString}), e é por isso que {@code CriarTopicoRequest}/{@code CriarDisciplinaRequest}
 * declaram {@code id}. O que não pode existir é o campo de <em>tenant</em> e os metadados
 * de servidor.</p>
 */
    private static final List<String> CAMPOS_PROIBIDOS = List.of(
            "usuarioId", "_id", "criadoEm", "atualizadoEm", "deletedAt",
            "expiraEm", "consumidoEm", "versaoGeracao");

    /** Pacotes de DTO do domínio (os únicos que recebem corpo do cliente). */
    private static final List<String> PACOTES_DTO = List.of(
            "br.com.trilhara.auth.dto",
            "br.com.trilhara.trilha.dto",
            "br.com.trilhara.cronograma.dto",
            "br.com.trilhara.sessao.dto",
            "br.com.trilhara.tarefa.dto",
            "br.com.trilhara.nota.dto",
            "br.com.trilhara.push.dto");

    @Inject
    TokenService tokenService;

    @Inject
    UserRepository userRepository;

    private String token;

    @BeforeEach
    void setUp() {
        String usuarioId = new org.bson.types.ObjectId().toHexString();
        java.time.Instant agora = java.time.Instant.now();
        Usuario usuario = new Usuario(
                usuarioId,
                "Usuario Mass Assignment Parametrizado",
                "massparam-" + usuarioId + "@trilhara.test",
                "hash",
                List.of(),
                true,
                true,
                0,
                null,
                agora,
                agora,
                agora);
        userRepository.inserir(usuario);
        token = tokenService.gerarAccessToken(usuario);
    }

    /**
 * Enumera os records de request por reflexão, sem lista escrita a mão.
 *
 * <p>Os pacotes são varridos a partir do diretório de classes de teste
 * ({@code target/classes}), que o próprio Maven produz antes da suíte. Assim, um DTO novo
 * entra automaticamente no teste sem edição — e um DTO que aceite campo de sistema falha.</p>
 */
    private static List<Class<?>> dtosDeRequest() {
        List<Class<?>> encontrados = new ArrayList<>();
        Path raiz = Paths.get("target", "classes");
        for (String pacote : PACOTES_DTO) {
            Path dir = raiz.resolve(pacote.replace('.', '/'));
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> arquivos = Files.list(dir)) {
                for (Path arquivo : arquivos.toList()) {
                    String nome = arquivo.getFileName().toString();
                    if (!nome.endsWith("Request.class")) {
                        continue;
                    }
                    String simples = nome.substring(0, nome.length() - ".class".length());
                    try {
                        Class<?> classe = Class.forName(pacote + "." + simples);
                        if (classe.isRecord()) {
                            encontrados.add(classe);
                        }
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException("DTO não carregável: " + simples, e);
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Falha ao varrer " + dir, e);
            }
        }
        return encontrados;
    }

    @Test
    @DisplayName("todo DTO *Request é um record e nenhum declara campo de sistema")
    void nenhumDtoDeclaraCampoDeSistema() {
        List<Class<?>> dtos = dtosDeRequest();
        assertEquals(true, dtos.size() >= 40,
                "Esperado encontrar pelo menos 40 DTOs *Request; encontrados: " + dtos.size());

        List<String> violacoes = new ArrayList<>();
        for (Class<?> dto : dtos) {
            for (var componente : dto.getRecordComponents()) {
                if (CAMPOS_PROIBIDOS.contains(componente.getName())) {
                    violacoes.add(dto.getSimpleName() + "." + componente.getName());
                }
            }
        }
        assertEquals(List.of(), violacoes,
                "DTOs de request que aceitam campo de sistema (mass assignment): " + violacoes);
    }

    @Test
    @DisplayName("DTO que representa corpo enviado ao cliente não declara usuarioId")
    void nenhumDtoTemUsuarioId() {
        List<String> violacoes = new ArrayList<>();
        for (Class<?> dto : dtosDeRequest()) {
            try {
                Method m = dto.getMethod("usuarioId");
                violacoes.add(dto.getSimpleName() + " expõe usuarioId()");
                assertEquals(m, m);
            } catch (NoSuchMethodException esperado) {
                // Correto: o DTO não tem o campo.
            }
        }
        assertEquals(List.of(), violacoes,
                "DTOs que expõem usuarioId: " + violacoes);
    }

    @Test
    @DisplayName("usuarioId forjado no corpo continua rejeitado com 400 VALIDATION_ERROR")
    void usuarioIdForjadoContinuaRejeitado() {
        // Controle de runtime nas três raízes que recebem corpo com mais frequência.
        for (String rota : List.of("/api/trilhas", "/api/listas-tarefas", "/api/cadernos")) {
            given()
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(Map.of("nome", "Forjado", "usuarioId", new org.bson.types.ObjectId().toHexString()))
                    .when().post(rota)
                    .then()
                    .statusCode(400)
                    .body("code", org.hamcrest.Matchers.equalTo("VALIDATION_ERROR"));
        }
    }
}
