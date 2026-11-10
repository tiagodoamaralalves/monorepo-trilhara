package br.com.trilhara.auth;

import static com.mongodb.client.model.Filters.eq;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import br.com.trilhara.auth.dto.AccountDeletionResponse;
import br.com.trilhara.auth.dto.ExcluirContaRequest;
import br.com.trilhara.shared.mongo.BsonDates;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;

/**
 * Serviço responsável pela exclusão definitiva, idempotente e auditável da conta do usuário
 * percorrendo nominalmente as 18 coleções privadas do sistema (17 filhas + usuarios por último).
 */
@ApplicationScoped
public class AccountDeletionService {

    private static final String VALOR_PENDENTE = "PENDENTE";
    private static final String CAMPO_EXCLUSAO_CONTA = "exclusaoConta";
    private static final String CAMPO_OPERATION_ID = "operationId";

    private static final Logger LOG = Logger.getLogger(AccountDeletionService.class);

    public static final String TEXTO_CONFIRMACAO_OBRIGATORIO = "EXCLUIR MINHA CONTA";

    /**
     * Lista ordenada nominalmente das 17 coleções filhas privadas do sistema.
     * A 18ª coleção privada (usuarios) é purgada por último após a limpeza das filhas.
     */
    public static final List<String> COLECOES_FILHAS = List.of(
            "refresh_tokens",
            "tokens_recuperacao_senha",
            "tokens_confirmacao_email",
            "lembretes_push",
            "push_subscriptions",
            "blocos_cronograma",
            "cronogramas",
            "progresso_topicos",
            "disciplinas",
            "sessoes",
            "trilhas",
            "tarefas",
            "series_recorrentes",
            "listas_tarefas",
            "notas",
            "secoes",
            "cadernos"
    );

    public static final String COLECAO_USUARIOS = "usuarios";
    public static final String COLECAO_EDITAIS = "editais";

    @FunctionalInterface
    public interface HookFalhaExclusao {
        void antesDeProcessarColecao(String nomeColecao, ObjectId usuarioId);
    }

    private final MongoClient mongoClient;
    private final String databaseName;
    private final PasswordService passwordService;
    private final RefreshTokenService refreshTokenService;
    private final TokenRecuperacaoRepository tokenRecuperacaoRepository;
    private final TokenConfirmacaoEmailRepository tokenConfirmacaoEmailRepository;

    private final ExecutorService workerExecutor = Executors.newFixedThreadPool(2);

    private final AtomicReference<HookFalhaExclusao> hookFalhaRef = new AtomicReference<>();

    @Inject
    public AccountDeletionService(
            MongoClient mongoClient,
            @ConfigProperty(name = "quarkus.mongodb.database") String databaseName,
            PasswordService passwordService,
            RefreshTokenService refreshTokenService,
            TokenRecuperacaoRepository tokenRecuperacaoRepository,
            TokenConfirmacaoEmailRepository tokenConfirmacaoEmailRepository) {
        this.mongoClient = mongoClient;
        this.databaseName = databaseName;
        this.passwordService = passwordService;
        this.refreshTokenService = refreshTokenService;
        this.tokenRecuperacaoRepository = tokenRecuperacaoRepository;
        this.tokenConfirmacaoEmailRepository = tokenConfirmacaoEmailRepository;
    }

    /**
     * Hook para testes de injeção de falha por coleção.
     */
    public void setHookFalha(HookFalhaExclusao hookFalha) {
        this.hookFalhaRef.set(hookFalha);
    }

    private MongoDatabase getDatabase() {
        return mongoClient.getDatabase(databaseName);
    }

    /**
     * Valida credenciais e intenção, persiste atomicamente usuarios.exclusaoConta, revoga
     * credenciais e agenda a limpeza assíncrona.
     */
    public AccountDeletionResponse solicitarExclusao(String usuarioIdStr, ExcluirContaRequest request, String idempotencyKey) {
        ObjectId usuarioId = validarSolicitacaoExclusao(usuarioIdStr, request);

        MongoCollection<Document> usuarios = getDatabase().getCollection(COLECAO_USUARIOS);
        Document userDoc = usuarios.find(eq("_id", usuarioId)).first();
        if (userDoc == null) {
            throw new NotAuthorizedException("Usuário não encontrado.");
        }

        Optional<AccountDeletionResponse> respostaIdempotente = verificarIdempotenciaExclusao(userDoc, idempotencyKey);
        if (respostaIdempotente.isPresent()) {
            return respostaIdempotente.get();
        }

        String senhaHash = userDoc.getString("senhaHash");
        if (!passwordService.verificarSenha(request.senha(), senhaHash)) {
            throw new NotAuthorizedException("Senha atual incorreta.");
        }

        String operationId = UUID.randomUUID().toString();
        String effectiveIdempotencyKey = idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : operationId;
        Instant agora = Instant.now();

        Document exclusaoDoc = new Document()
                .append(CAMPO_OPERATION_ID, operationId)
                .append("idempotencyKey", effectiveIdempotencyKey)
                .append("estado", VALOR_PENDENTE)
                .append("solicitadaEm", BsonDates.toBsonDate(agora))
                .append("tentativas", 0);

        usuarios.updateOne(
                eq("_id", usuarioId),
                Updates.combine(
                        Updates.set("ativo", false),
                        Updates.set(CAMPO_EXCLUSAO_CONTA, exclusaoDoc),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(agora))
                )
        );

        revogarCredenciaisParaExclusao(usuarioIdStr, agora);

        // Agendamento assíncrono do worker
        workerExecutor.submit(() -> executarLimpezaUsuario(usuarioId, operationId));

        return new AccountDeletionResponse(operationId, VALOR_PENDENTE);
    }

    private ObjectId validarSolicitacaoExclusao(String usuarioIdStr, ExcluirContaRequest request) {
        if (usuarioIdStr == null || !ObjectId.isValid(usuarioIdStr)) {
            throw new NotAuthorizedException("Usuário não autenticado ou inválido.");
        }
        if (request == null || request.senha() == null || request.senha().isBlank()) {
            throw new BadRequestException("A senha atual é obrigatória para excluir a conta.");
        }
        if (request.confirmacao() == null || !request.confirmacao().trim().equalsIgnoreCase(TEXTO_CONFIRMACAO_OBRIGATORIO)) {
            throw new BadRequestException("Confirmação textual inválida. Digite EXCLUIR MINHA CONTA para confirmar.");
        }
        return new ObjectId(usuarioIdStr);
    }

    private Optional<AccountDeletionResponse> verificarIdempotenciaExclusao(Document userDoc, String idempotencyKey) {
        Document exclusaoExistente = userDoc.get(CAMPO_EXCLUSAO_CONTA, Document.class);
        if (exclusaoExistente == null || idempotencyKey == null) {
            return Optional.empty();
        }
        String existingIdem = exclusaoExistente.getString("idempotencyKey");
        if (!idempotencyKey.equals(existingIdem)) {
            return Optional.empty();
        }
        String existingOpId = exclusaoExistente.getString(CAMPO_OPERATION_ID);
        String existingEstado = exclusaoExistente.getString("estado");
        return Optional.of(new AccountDeletionResponse(
                existingOpId,
                existingEstado != null ? existingEstado : VALOR_PENDENTE));
    }

    private void revogarCredenciaisParaExclusao(String usuarioIdStr, Instant agora) {
        try {
            refreshTokenService.revogarTodasSessoesDoUsuario(usuarioIdStr, "EXCLUSAO_CONTA");
        } catch (Exception e) {
            LOG.warnf("Falha não bloqueante ao revogar refresh tokens para exclusão: %s", e.getMessage());
        }
        try {
            tokenRecuperacaoRepository.invalidarPendentesDoUsuario(usuarioIdStr, agora);
            tokenConfirmacaoEmailRepository.invalidarPendentesDoUsuario(usuarioIdStr, agora);
        } catch (Exception e) {
            LOG.warnf("Falha não bloqueante ao invalidar tokens de email/senha para exclusão: %s", e.getMessage());
        }
    }

    /**
     * Executa nominalmente a limpeza das 17 coleções filhas privadas por usuarioId: ObjectId
     * e, após confirmação de contagem zero em todas, purga o documento raiz na 18ª coleção (usuarios).
     */
    public boolean executarLimpezaUsuario(ObjectId alvoObjectId, String operationId) {
        MongoCollection<Document> usuarios = getDatabase().getCollection(COLECAO_USUARIOS);
        Document userDoc = usuarios.find(eq("_id", alvoObjectId)).first();
        if (userDoc == null) {
            // Documento já foi purgado completamente em tentativa anterior
            return true;
        }

        usuarios.updateOne(
                eq("_id", alvoObjectId),
                Updates.set("exclusaoConta.estado", "PROCESSANDO")
        );

        String colecaoEmExecucao = null;
        try {
            HookFalhaExclusao hook = hookFalhaRef.get();
            for (String nomeColecao : COLECOES_FILHAS) {
                colecaoEmExecucao = nomeColecao;

                // Hook para simulação/injeção de falhas em testes
                if (hook != null) {
                    hook.antesDeProcessarColecao(nomeColecao, alvoObjectId);
                }

                // Remoção estrita por proprietário tipado usuarioId: ObjectId
                getDatabase().getCollection(nomeColecao).deleteMany(Filters.eq("usuarioId", alvoObjectId));

                usuarios.updateOne(
                        eq("_id", alvoObjectId),
                        Updates.combine(
                                Updates.set("exclusaoConta.ultimaColecaoProcessada", nomeColecao),
                                Updates.set("exclusaoConta.atualizadoEm", BsonDates.toBsonDate(Instant.now()))
                        )
                );
            }

            // Verificação nominal de integridade: todas as 17 coleções filhas privadas devem ter zero documentos
            for (String nomeColecao : COLECOES_FILHAS) {
                long restantes = getDatabase().getCollection(nomeColecao).countDocuments(Filters.eq("usuarioId", alvoObjectId));
                if (restantes > 0) {
                    throw new IllegalStateException("Integridade violada: coleção " + nomeColecao + " ainda possui " + restantes + " documentos.");
                }
            }

            // 18ª coleção: remoção do documento raiz em usuarios
            usuarios.deleteOne(eq("_id", alvoObjectId));

            LOG.infof("event=ACCOUNT_DELETED operationId=%s collectionsCleaned=18", operationId);
            return true;
        } catch (Exception e) {
            LOG.errorf(e, "event=ACCOUNT_DELETION_FAILED operationId=%s collection=%s error=%s",
                    operationId,
                    colecaoEmExecucao != null ? colecaoEmExecucao : "desconhecida",
                    e.getMessage());

            try {
                usuarios.updateOne(
                        eq("_id", alvoObjectId),
                        Updates.combine(
                                Updates.set("exclusaoConta.estado", "FALHA"),
                                Updates.set("exclusaoConta.erroColecao", colecaoEmExecucao),
                                Updates.inc("exclusaoConta.tentativas", 1),
                                Updates.set("exclusaoConta.ultimoErro", e.getMessage()),
                                Updates.set("exclusaoConta.atualizadoEm", BsonDates.toBsonDate(Instant.now()))
                        )
                );
            } catch (Exception _) {
                LOG.debug("Falha secundária ignorada ao persistir estado de erro na exclusão de conta.");
            }
            return false;
        }
    }

    /**
     * Varrer todas as contas com exclusão pendente/falha e retomar sem intervenção do usuário
     * ou autenticação.
     */
    public int processarExclusoesPendentes() {
        MongoCollection<Document> usuarios = getDatabase().getCollection(COLECAO_USUARIOS);
        int processadas = 0;

        for (Document doc : usuarios.find(Filters.exists(CAMPO_EXCLUSAO_CONTA, true))) {
            ObjectId usuarioId = doc.getObjectId("_id");
            Document exc = doc.get(CAMPO_EXCLUSAO_CONTA, Document.class);
            String opId = exc != null ? exc.getString(CAMPO_OPERATION_ID) : "RECOVERY-OP";

            boolean sucesso = executarLimpezaUsuario(usuarioId, opId);
            if (sucesso) {
                processadas++;
            }
        }
        return processadas;
    }
}
