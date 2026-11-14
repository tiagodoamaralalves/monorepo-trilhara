package br.com.trilhara.auth;

import static com.mongodb.client.model.Filters.eq;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
import br.com.trilhara.shared.erro.CodigoErro;
import br.com.trilhara.shared.erro.DomainConflictException;
import br.com.trilhara.shared.erro.ValidationException;
import br.com.trilhara.shared.mongo.BsonDates;
import br.com.trilhara.shared.mongo.ColecaoTrilhara;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;

/**
 * Serviço da exclusão definitiva, idempotente e retry-safe da conta.
 *
 * <p>Percorre {@link ColecaoTrilhara#filhasPrivadas} (18 coleções filhas, incluindo
 * {@code tokens_handoff_sso}) por {@code usuarioId} e só remove {@code usuarios} depois de
 * confirmar contagem zero em todas elas. O estado vive em {@code usuarios.exclusaoConta}:
 * {@code PENDENTE -> EM_ANDAMENTO -> (RETENTANDO_LENTO)*}. {@code CONCLUIDA} é registrada
 * apenas em log/métrica, porque o documento raiz já foi removido.</p>
 *
 * <p>Nenhum log desta classe contém {@code usuarioId}, e-mail ou nome.</p>
 */
@ApplicationScoped
public class AccountDeletionService {

    /** Estado inicial de uma solicitação aceita (202) e ainda não processada. */
    public static final String ESTADO_PENDENTE = "PENDENTE";
    /** Estado de uma tentativa em curso. */
    public static final String ESTADO_EM_ANDAMENTO = "EM_ANDAMENTO";
    /** Estado após esgotar as tentativas rápidas: retentativa lenta e indefinida. */
    public static final String ESTADO_RETENTANDO_LENTO = "RETENTANDO_LENTO";

    /** Métrica exigida por . */
    public static final String METRICA_STUCK = "trilhara_account_deletion_stuck";

    private static final String CAMPO_EXCLUSAO_CONTA = "exclusaoConta";
    private static final String CAMPO_OPERATION_ID = "operationId";
    private static final String CAMPO_IDEMPOTENCY_KEY = "idempotencyKey";
    private static final String CAMPO_HASH_PAYLOAD = "hashPayload";
    private static final String CAMPO_ESTADO = "estado";
    private static final String CAMPO_TENTATIVAS = "tentativas";
    private static final String CAMPO_PROXIMA_TENTATIVA = "proximaTentativa";
    private static final String CAMPO_ERRO_COLECAO = "erroColecao";
    private static final String CAMPO_ULTIMO_ERRO = "ultimoErro";
    private static final String CAMPO_ATUALIZADO_EM = "atualizadoEm";

    private static final Logger LOG = Logger.getLogger(AccountDeletionService.class);

    /** Frase de confirmação adicional — nunca é autenticação (regra normativa §12). */
    public static final String TEXTO_CONFIRMACAO_OBRIGATORIO = "EXCLUIR MINHA CONTA";

    /**
 * Lista ordenada nominalmente das 18 coleções filhas privadas do sistema.
 * A 19ª coleção privada ({@code usuarios}) é purgada por último após a limpeza das filhas.
 * Fonte única: {@link ColecaoTrilhara#filhasPrivadas}.
 */
    public static final List<String> COLECOES_FILHAS = ColecaoTrilhara.nomesFilhasPrivadas();

    public static final String COLECAO_USUARIOS = "usuarios";
    public static final String COLECAO_EDITAIS = "editais";

    private final MongoClient mongoClient;
    private final String databaseName;
    private final PasswordService passwordService;
    private final RefreshTokenService refreshTokenService;
    private final TokenRecuperacaoRepository tokenRecuperacaoRepository;
    private final TokenConfirmacaoEmailRepository tokenConfirmacaoEmailRepository;
    private final RateLimitService rateLimitService;
    private final FaultInjectorExclusaoConta faultInjector;
    private final int maxTentativasRapidas;
    private final long intervaloRetentativaLentaSegundos;
    private final Counter contadorStuck;

    @Inject
    public AccountDeletionService(
            MongoClient mongoClient,
            @ConfigProperty(name = "quarkus.mongodb.database") String databaseName,
            PasswordService passwordService,
            RefreshTokenService refreshTokenService,
            TokenRecuperacaoRepository tokenRecuperacaoRepository,
            TokenConfirmacaoEmailRepository tokenConfirmacaoEmailRepository,
            RateLimitService rateLimitService,
            FaultInjectorExclusaoConta faultInjector,
            MeterRegistry meterRegistry,
            @ConfigProperty(name = "trilhara.account-deletion.max-tentativas", defaultValue = "5")
            int maxTentativasRapidas,
            @ConfigProperty(name = "trilhara.account-deletion.retentativa-lenta-segundos", defaultValue = "3600")
            long intervaloRetentativaLentaSegundos) {
        this.mongoClient = mongoClient;
        this.databaseName = databaseName;
        this.passwordService = passwordService;
        this.refreshTokenService = refreshTokenService;
        this.tokenRecuperacaoRepository = tokenRecuperacaoRepository;
        this.tokenConfirmacaoEmailRepository = tokenConfirmacaoEmailRepository;
        this.rateLimitService = rateLimitService;
        this.faultInjector = faultInjector;
        this.maxTentativasRapidas = Math.max(1, maxTentativasRapidas);
        this.intervaloRetentativaLentaSegundos = Math.max(60, intervaloRetentativaLentaSegundos);
        this.contadorStuck = Counter.builder(METRICA_STUCK)
                .description("Exclusões de conta que esgotaram as tentativas rápidas e seguem em retentativa lenta.")
                .register(meterRegistry);
    }

    private MongoDatabase getDatabase() {
        return mongoClient.getDatabase(databaseName);
    }


    /**
 * Valida credenciais e intenção, persiste {@code usuarios.exclusaoConta}, revoga credenciais
 * e deixa a limpeza para o worker periódico.
 *
 * <p>A verificação de senha ocorre antes de qualquer escrita: senha inválida responde
 * {@code 401 UNAUTHORIZED} <strong>sem efeito colateral</strong>.</p>
 */
    public AccountDeletionResponse solicitarExclusao(String usuarioIdStr, ExcluirContaRequest request, String idempotencyKey) {
        ObjectId usuarioId = validarSolicitacaoExclusao(usuarioIdStr, request);

        MongoCollection<Document> usuarios = getDatabase().getCollection(COLECAO_USUARIOS);
        Document userDoc = usuarios.find(eq("_id", usuarioId)).first();
        if (userDoc == null) {
            throw new NotAuthorizedException("Usuário não encontrado.");
        }

        String hashPayload = calcularHashPayload(request);

        // Conta já em exclusão: devolve o mesmo operationId sem revalidar a senha (as
        // credenciais já foram revogadas no primeiro aceite).
        Optional<AccountDeletionResponse> respostaIdempotente =
                verificarExclusaoEmAndamento(userDoc, idempotencyKey, hashPayload);
        if (respostaIdempotente.isPresent()) {
            return respostaIdempotente.get();
        }

        rateLimitService.verificarLimiteExclusaoConta(usuarioIdStr);

        String senhaHash = userDoc.getString("senhaHash");
        if (!passwordService.verificarSenha(request.senha(), senhaHash)) {
            throw new NotAuthorizedException("Senha atual incorreta.");
        }

        String operationId = UUID.randomUUID().toString();
        Instant agora = Instant.now();

        Document exclusaoDoc = new Document()
                .append(CAMPO_OPERATION_ID, operationId)
                .append(CAMPO_IDEMPOTENCY_KEY, idempotencyKey)
                .append(CAMPO_HASH_PAYLOAD, hashPayload)
                .append(CAMPO_ESTADO, ESTADO_PENDENTE)
                .append("solicitadaEm", BsonDates.toBsonDate(agora))
                .append(CAMPO_TENTATIVAS, 0)
                .append(CAMPO_PROXIMA_TENTATIVA, BsonDates.toBsonDate(agora));

        usuarios.updateOne(
                eq("_id", usuarioId),
                Updates.combine(
                        Updates.set("ativo", false),
                        Updates.set(CAMPO_EXCLUSAO_CONTA, exclusaoDoc),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(agora))
                )
        );

        revogarCredenciaisParaExclusao(usuarioIdStr, agora);

        // Nenhum estado em memória: `AccountDeletionJob` retoma a partir do checkpoint em
        // `usuarios.exclusaoConta`, inclusive após reinício do servidor.
        return new AccountDeletionResponse(operationId, ESTADO_PENDENTE);
    }

    private ObjectId validarSolicitacaoExclusao(String usuarioIdStr, ExcluirContaRequest request) {
        if (usuarioIdStr == null || !ObjectId.isValid(usuarioIdStr)) {
            throw new NotAuthorizedException("Usuário não autenticado ou inválido.");
        }
        if (request == null || request.senha() == null || request.senha().isBlank()) {
            throw new ValidationException("senha", "A senha atual é obrigatória para excluir a conta.");
        }
        if (request.confirmacao() == null
                || !TEXTO_CONFIRMACAO_OBRIGATORIO.equals(request.confirmacao().trim())) {
            throw new ValidationException("confirmacao",
                    "Confirmação textual inválida. Digite EXCLUIR MINHA CONTA para confirmar.");
        }
        return new ObjectId(usuarioIdStr);
    }


    /**
 * Idempotência de {@code DELETE /api/auth/conta}.
 *
 * <ul>
 * <li>mesma chave + mesmo payload funcional → {@code 202} com o mesmo {@code operationId}
 * e o estado atual;</li>
 * <li>mesma chave + payload funcional diferente → {@code 409 IDEMPOTENCY_KEY_CONFLICT};</li>
 * <li>conta já em exclusão com outra chave → {@code 409 IDEMPOTENCY_KEY_CONFLICT}; a chave
 * é escopada ao documento do usuário e nunca colide entre contas.</li>
 * </ul>
 */
    private Optional<AccountDeletionResponse> verificarExclusaoEmAndamento(
            Document userDoc,
            String idempotencyKey,
            String hashPayload) {
        Document exclusaoExistente = userDoc.get(CAMPO_EXCLUSAO_CONTA, Document.class);
        if (exclusaoExistente == null) {
            return Optional.empty();
        }

        String existingIdem = exclusaoExistente.getString(CAMPO_IDEMPOTENCY_KEY);
        String existingHash = exclusaoExistente.getString(CAMPO_HASH_PAYLOAD);
        boolean mesmaChave = idempotencyKey != null && idempotencyKey.equals(existingIdem);
        boolean mesmoPayload = hashPayload != null && hashPayload.equals(existingHash);

        if (!mesmaChave || !mesmoPayload) {
            throw new DomainConflictException(
                    CodigoErro.IDEMPOTENCY_KEY_CONFLICT,
                    "Já existe uma exclusão de conta em andamento para esta conta com outra chave de idempotência.");
        }

        String existingOpId = exclusaoExistente.getString(CAMPO_OPERATION_ID);
        String existingEstado = exclusaoExistente.getString(CAMPO_ESTADO);
        return Optional.of(new AccountDeletionResponse(
                existingOpId,
                existingEstado != null ? existingEstado : ESTADO_PENDENTE));
    }

    /** SHA-256 do payload funcional estável (nunca da senha) — escopo da chave. */
    static String calcularHashPayload(ExcluirContaRequest request) {
        String funcional = TEXTO_CONFIRMACAO_OBRIGATORIO + "|confirmacao="
                + (request.confirmacao() == null ? "" : request.confirmacao().trim()) + "|v1";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(funcional.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
        }
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
 * Executa a limpeza das 18 coleções filhas privadas por {@code usuarioId: ObjectId} e, após
 * confirmar contagem zero em todas, purga o documento raiz em {@code usuarios}.
 *
 * @return {@code true} quando a conta foi integralmente removida
 */
    public boolean executarLimpezaUsuario(ObjectId alvoObjectId, String operationId) {
        MongoCollection<Document> usuarios = getDatabase().getCollection(COLECAO_USUARIOS);
        Document userDoc = usuarios.find(eq("_id", alvoObjectId)).first();
        if (userDoc == null) {
            // Documento já purgado em tentativa anterior: operação idempotente.
            return true;
        }

        usuarios.updateOne(
                eq("_id", alvoObjectId),
                Updates.set(CAMPO_EXCLUSAO_CONTA + "." + CAMPO_ESTADO, ESTADO_EM_ANDAMENTO)
        );

        String colecaoEmExecucao = null;
        try {
            for (ColecaoTrilhara colecao : ColecaoTrilhara.filhasPrivadas()) {
                colecaoEmExecucao = colecao.nome();

                faultInjector.antesDe(colecao);

                // Remoção estrita por proprietário tipado usuarioId: ObjectId
                getDatabase().getCollection(colecao.nome()).deleteMany(Filters.eq("usuarioId", alvoObjectId));

                usuarios.updateOne(
                        eq("_id", alvoObjectId),
                        Updates.combine(
                                Updates.set(CAMPO_EXCLUSAO_CONTA + ".ultimaColecaoProcessada", colecao.nome()),
                                Updates.set(CAMPO_EXCLUSAO_CONTA + "." + CAMPO_ATUALIZADO_EM,
                                        BsonDates.toBsonDate(Instant.now()))
                        )
                );
            }

            // Integridade: todas as 18 coleções filhas devem estar zeradas antes de remover a raiz.
            for (String nomeColecao : COLECOES_FILHAS) {
                long restantes = getDatabase().getCollection(nomeColecao)
                        .countDocuments(Filters.eq("usuarioId", alvoObjectId));
                if (restantes > 0) {
                    throw new IllegalStateException("Integridade violada: coleção " + nomeColecao
                            + " ainda possui " + restantes + " documentos.");
                }
            }

            // 19ª coleção privada: remoção do documento raiz em usuarios, por último.
            usuarios.deleteOne(eq("_id", alvoObjectId));

            LOG.infof("event=ACCOUNT_DELETION_CONCLUDED operationId=%s colecoesFilhas=%d",
                    operationId, COLECOES_FILHAS.size());
            return true;
        } catch (Exception e) {
            registrarFalha(alvoObjectId, operationId, colecaoEmExecucao, e);
            return false;
        }
    }


    /**
 * Persiste o checkpoint da falha e decide o próximo horário de tentativa.
 *
 * <p>Backoff exponencial 1 s → 2 s → 4 s → 8 s → 16 s (teto 30 s) nas tentativas rápidas.
 * Ao atingir {@code trilhara.account-deletion.max-tentativas}, o estado vira
 * {@code RETENTANDO_LENTO}, com nova tentativa a cada
 * {@code trilhara.account-deletion.retentativa-lenta-segundos} (default 1 h), indefinidamente,
 * incrementando a métrica {@code trilhara_account_deletion_stuck}. Nenhum estado é terminal
 * e silencioso.</p>
 */
    private void registrarFalha(ObjectId alvoObjectId, String operationId, String colecao, Exception erro) {
        LOG.errorf("event=ACCOUNT_DELETION_FAILED operationId=%s colecao=%s tipo=%s",
                operationId,
                colecao != null ? colecao : "desconhecida",
                erro.getClass().getSimpleName());

        Document atual = getDatabase().getCollection(COLECAO_USUARIOS).find(eq("_id", alvoObjectId)).first();
        if (atual == null) {
            return;
        }
        Document exclusao = atual.get(CAMPO_EXCLUSAO_CONTA, Document.class);
        int tentativas = 1;
        if (exclusao != null) {
            Integer anteriores = exclusao.getInteger(CAMPO_TENTATIVAS);
            tentativas = (anteriores != null ? anteriores : 0) + 1;
        }

        boolean esgotouRapidas = tentativas >= maxTentativasRapidas;
        long esperaSegundos = esgotouRapidas
                ? intervaloRetentativaLentaSegundos
                : Math.min(30L, 1L << Math.min(4, tentativas - 1));
        Instant proxima = Instant.now().plusSeconds(esperaSegundos);

        if (esgotouRapidas) {
            contadorStuck.increment();
            LOG.errorf("event=ACCOUNT_DELETION_STUCK operationId=%s colecao=%s",
                    operationId,
                    colecao != null ? colecao : "desconhecida");
        }

        try {
            getDatabase().getCollection(COLECAO_USUARIOS).updateOne(
                    eq("_id", alvoObjectId),
                    Updates.combine(
                            Updates.set(CAMPO_EXCLUSAO_CONTA + "." + CAMPO_ESTADO,
                                    esgotouRapidas ? ESTADO_RETENTANDO_LENTO : ESTADO_EM_ANDAMENTO),
                            Updates.set(CAMPO_EXCLUSAO_CONTA + "." + CAMPO_ERRO_COLECAO, colecao),
                            Updates.set(CAMPO_EXCLUSAO_CONTA + "." + CAMPO_TENTATIVAS, tentativas),
                            Updates.set(CAMPO_EXCLUSAO_CONTA + "." + CAMPO_PROXIMA_TENTATIVA,
                                    BsonDates.toBsonDate(proxima)),
                            Updates.set(CAMPO_EXCLUSAO_CONTA + "." + CAMPO_ULTIMO_ERRO,
                                    erro.getClass().getSimpleName()),
                            Updates.set(CAMPO_EXCLUSAO_CONTA + "." + CAMPO_ATUALIZADO_EM,
                                    BsonDates.toBsonDate(Instant.now()))
                    )
            );
        } catch (Exception secundaria) {
            LOG.debug("Falha secundária ignorada ao persistir estado de erro na exclusão de conta.");
        }
    }

    /**
 * Varre as contas com exclusão em aberto cujo {@code proximaTentativa} já venceu, retomando
 * sem qualquer intervenção ou reautenticação do usuário.
 */
    public int processarExclusoesPendentes() {
        MongoCollection<Document> usuarios = getDatabase().getCollection(COLECAO_USUARIOS);
        Instant agora = Instant.now();
        int processadas = 0;

        for (Document doc : usuarios.find(Filters.exists(CAMPO_EXCLUSAO_CONTA, true))) {
            Document exc = doc.get(CAMPO_EXCLUSAO_CONTA, Document.class);
            if (exc == null || !podeTentarAgora(exc, agora)) {
                continue;
            }
            ObjectId usuarioId = doc.getObjectId("_id");
            String opId = exc.getString(CAMPO_OPERATION_ID);
            if (executarLimpezaUsuario(usuarioId, opId != null ? opId : "RECOVERY-OP")) {
                processadas++;
            }
        }
        return processadas;
    }

    private static boolean podeTentarAgora(Document exclusao, Instant agora) {
        java.util.Date proxima = exclusao.getDate(CAMPO_PROXIMA_TENTATIVA);
        return proxima == null || !proxima.toInstant().isAfter(agora);
    }

    @PreDestroy
    void encerrar() {
        faultInjector.limpar();
    }
}

