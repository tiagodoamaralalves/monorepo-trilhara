package br.com.trilhara.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import br.com.trilhara.auth.RefreshTokenService.ParRefreshToken;
import br.com.trilhara.auth.TokenService.TokenInvalidoException;
import br.com.trilhara.auth.dto.AlterarSenhaRequest;
import br.com.trilhara.auth.dto.AuthResponse;
import br.com.trilhara.auth.dto.CadastroRequest;
import br.com.trilhara.auth.dto.LoginRequest;
import br.com.trilhara.auth.dto.RecuperarSenhaRequest;
import br.com.trilhara.auth.dto.ResetarSenhaRequest;
import br.com.trilhara.auth.dto.SessaoAtivaResponse;
import br.com.trilhara.auth.dto.UsuarioResponse;
import br.com.trilhara.shared.Pseudonimo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class);
    private static final String MENSAGEM_CREDENCIAIS_INVALIDAS = "E-mail ou senha inválidos.";

    private final UserService userService;
    private final PasswordService passwordService;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final TokenRecuperacaoRepository tokenRecuperacaoRepository;
    private final TokenConfirmacaoEmailRepository tokenConfirmacaoEmailRepository;
    private final EmailService emailService;
    private final RateLimitService rateLimitService;
    private final long expiracaoResetMinutos;

    public AuthService(
            UserService userService,
            PasswordService passwordService,
            TokenService tokenService,
            RefreshTokenService refreshTokenService,
            TokenRecuperacaoRepository tokenRecuperacaoRepository,
            TokenConfirmacaoEmailRepository tokenConfirmacaoEmailRepository,
            EmailService emailService,
            RateLimitService rateLimitService,
            @ConfigProperty(name = "trilhara.auth.reset.expiration-minutes", defaultValue = "15") long expiracaoResetMinutos) {
        this.userService = userService;
        this.passwordService = passwordService;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.tokenRecuperacaoRepository = tokenRecuperacaoRepository;
        this.tokenConfirmacaoEmailRepository = tokenConfirmacaoEmailRepository;
        this.emailService = emailService;
        this.rateLimitService = rateLimitService;
        this.expiracaoResetMinutos = Math.max(5, expiracaoResetMinutos);
    }

    public AuthResponse cadastrar(CadastroRequest request, String userAgent, String ip) {
        return cadastrar(request, userAgent, ip, null);
    }

    public AuthResponse cadastrar(CadastroRequest request, String userAgent, String ip, String baseUrl) {
        if (request == null) {
            throw new BadRequestException("Dados de cadastro são obrigatórios.");
        }
        rateLimitService.verificarLimiteCadastro(ip);

        Usuario criado = userService.criarUsuario(
                request.nome(),
                request.email(),
                request.senha(),
                List.of(Role.USER));

        // Emite token de confirmação de e-mail e envia e-mail assincronamente
        Instant agora = Instant.now();
        String tokenPuro = refreshTokenService.gerarTokenOpacoAleatorio();
        String tokenHash = refreshTokenService.calcularHashSha256(tokenPuro);

        TokenConfirmacaoEmail tokenConfirmacao = new TokenConfirmacaoEmail(
                new ObjectId().toHexString(),
                tokenHash,
                criado.id(),
                criado.email(),
                agora,
                agora.plus(24, ChronoUnit.HOURS),
                null);
        tokenConfirmacaoEmailRepository.inserir(tokenConfirmacao);

        emailService.enviarEmailConfirmacao(criado.email(), criado.nome(), tokenPuro, baseUrl);

        LOG.infof("event=USER_REGISTERED conta=%s", Pseudonimo.de(criado.id()));
        return emitirRespostaAutenticacao(criado, userAgent, ip);
    }

    public AuthResponse login(LoginRequest request, String userAgent, String ip) {
        if (request == null || request.email() == null || request. email().isBlank()
                || request.senha() == null || request.senha().isEmpty()) {
            throw criarExcecaoNaoAutorizado(MENSAGEM_CREDENCIAIS_INVALIDAS);
        }

        String emailNormalizado = UserService.normalizarEmail(request.email());
        rateLimitService.verificarLimiteLogin(ip, emailNormalizado);

        Optional<Usuario> candidatoOpt = userService.buscarPorEmail(emailNormalizado);
        if (candidatoOpt.isEmpty()) {
            passwordService.simularVerificacaoConstante(request.senha());
            LOG.debugf("Tentativa de login falhou (usuario inexistente): ip=%s", ip);
            throw criarExcecaoNaoAutorizado(MENSAGEM_CREDENCIAIS_INVALIDAS);
        }

        Usuario usuario = candidatoOpt.get();
        Instant agora = Instant.now();

        if (!usuario.ativo()) {
            passwordService.simularVerificacaoConstante(request.senha());
            LOG.warnf("event=LOGIN_CONTA_DESATIVADA conta=%s ip=%s", Pseudonimo.de(usuario.id()), ip);
            throw criarExcecaoNaoAutorizado(MENSAGEM_CREDENCIAIS_INVALIDAS);
        }

        if (usuario.estaBloqueado(agora)) {
            long segundosRestantes = Math.max(1, agora.until(usuario.bloqueadoAte(), ChronoUnit.SECONDS));
            LOG.warnf("event=LOGIN_CONTA_BLOQUEADA conta=%s ip=%s", Pseudonimo.de(usuario.id()), ip);
            throw new RateLimitService.RateLimitExceededException(
                    "Conta temporariamente bloqueada devido a múltiplas tentativas de login malsucedidas. Tente novamente mais tarde.",
                    segundosRestantes);
        }

        boolean senhaValida = passwordService.verificarSenha(request.senha(), usuario.senhaHash());
        if (!senhaValida) {
            Usuario atualizado = userService.registrarFalhaLogin(usuario, agora);
            if (atualizado.estaBloqueado(agora)) {
                long segundosRestantes = Math.max(1, agora.until(atualizado.bloqueadoAte(), ChronoUnit.SECONDS));
                LOG.warnf("event=CONTA_BLOQUEADA_BRUTE_FORCE conta=%s ip=%s", Pseudonimo.de(usuario.id()), ip);
                throw new RateLimitService.RateLimitExceededException(
                        "Conta temporariamente bloqueada devido a múltiplas tentativas de login malsucedidas. Tente novamente mais tarde.",
                        segundosRestantes);
            }
            throw criarExcecaoNaoAutorizado(MENSAGEM_CREDENCIAIS_INVALIDAS);
        }

        Usuario usuarioLimpo = userService.registrarSucessoLogin(usuario, agora);
        rateLimitService.limparTentativasLogin(ip, emailNormalizado);
        LOG.infof("event=LOGIN_SUCESSO conta=%s ip=%s", Pseudonimo.de(usuarioLimpo.id()), ip);

        return emitirRespostaAutenticacao(usuarioLimpo, userAgent, ip);
    }

    public AuthResponse renovarToken(String refreshTokenPuro, String userAgent, String ip) {
        if (refreshTokenPuro == null || refreshTokenPuro.isBlank()) {
            throw criarExcecaoNaoAutorizado("Refresh token é obrigatório.");
        }
        try {
            ParRefreshToken rotacionado = refreshTokenService.rotacionar(refreshTokenPuro, userAgent, ip);
            Usuario usuario = userService.buscarPorId(rotacionado.entidade().usuarioId())
                    .orElseThrow(() -> new TokenInvalidoException("Usuário associado ao token não encontrado."));

            if (!usuario.ativo()) {
                refreshTokenService.revogarTodasSessoesDoUsuario(usuario.id(), "CONTA_INATIVA");
                throw criarExcecaoNaoAutorizado("Conta de usuário inativa.");
            }

            String novoAccessToken = tokenService.gerarAccessToken(usuario);
            return new AuthResponse(
                    novoAccessToken,
                    rotacionado.tokenPuro(),
                    "Bearer",
                    tokenService.getAccessExpirationSeconds(),
                    UsuarioResponse.de(usuario, userService.preferenciasDe(usuario.id())));
        } catch (TokenInvalidoException exception) {
            throw criarExcecaoNaoAutorizado(exception.getMessage());
        }
    }

    public void logout(String refreshTokenPuro, String usuarioIdAutenticado) {
        try {
            refreshTokenService.revogarPorTokenPuro(refreshTokenPuro, usuarioIdAutenticado);
        } catch (SecurityException exception) {
            throw new jakarta.ws.rs.ForbiddenException(exception.getMessage());
        }
    }

    public void logoutTodosDispositivos(String usuarioIdAutenticado) {
        refreshTokenService.revogarTodasSessoesDoUsuario(usuarioIdAutenticado, "LOGOUT_GLOBAL");
    }

    public List<SessaoAtivaResponse> listarSessoesAtivas(String usuarioIdAutenticado) {
        return refreshTokenService.listarSessoesAtivas(usuarioIdAutenticado).stream()
                .map(SessaoAtivaResponse::de)
                .toList();
    }

    public boolean revogarSessaoEspecifica(String usuarioIdAutenticado, String familiaId) {
        return refreshTokenService.revogarFamiliaDoUsuario(usuarioIdAutenticado, familiaId);
    }

    public void alterarSenha(String usuarioIdAutenticado, AlterarSenhaRequest request) {
        if (request == null || request.senhaAtual() == null || request.novaSenha() == null) {
            throw new BadRequestException("A senha atual e a nova senha são obrigatórias.");
        }
        Usuario usuario = userService.buscarPorIdObrigatorio(usuarioIdAutenticado);
        if (!passwordService.verificarSenha(request.senhaAtual(), usuario.senhaHash())) {
            throw criarExcecaoNaoAutorizado("A senha atual informada está incorreta.");
        }
        if (passwordService.verificarSenha(request.novaSenha(), usuario.senhaHash())) {
            throw new BadRequestException("A nova senha deve ser diferente da senha atual.");
        }

        userService.atualizarSenha(usuario, request.novaSenha());
        refreshTokenService.revogarTodasSessoesDoUsuario(usuario.id(), "SENHA_ALTERADA");
        LOG.infof("event=SENHA_ALTERADA conta=%s", Pseudonimo.de(usuario.id()));
    }

    /**
     * Inicia o fluxo de recuperação de senha gerando um token de uso único (single-use)
     * com expiração curta. Nunca revela se o e-mail existe ou não.
     */
    public Optional<String> solicitarRecuperacaoSenha(RecuperarSenhaRequest request, String ip) {
        if (request == null || request.email() == null || request.email().isBlank()) {
            throw new BadRequestException("O e-mail é obrigatório.");
        }
        String emailNormalizado = UserService.validarENormalizarEmail(request.email());
        rateLimitService.verificarLimiteRecuperacaoSenha(ip, emailNormalizado);

        Optional<Usuario> usuarioOpt = userService.buscarPorEmail(emailNormalizado);
        if (usuarioOpt.isEmpty() || !usuarioOpt.get().ativo()) {
            passwordService.simularVerificacaoConstante(emailNormalizado);
            return Optional.empty();
        }

        Usuario usuario = usuarioOpt.get();
        Instant agora = Instant.now();
        tokenRecuperacaoRepository.invalidarPendentesDoUsuario(usuario.id(), agora);

        String tokenPuro = refreshTokenService.gerarTokenOpacoAleatorio();
        String tokenHash = refreshTokenService.calcularHashSha256(tokenPuro);

        TokenRecuperacaoSenha tokenRecuperacao = new TokenRecuperacaoSenha(
                new ObjectId().toHexString(),
                tokenHash,
                usuario.id(),
                ip,
                agora,
                agora.plus(expiracaoResetMinutos, ChronoUnit.MINUTES),
                null);
        tokenRecuperacaoRepository.inserir(tokenRecuperacao);
        LOG.infof("event=TOKEN_RECUPERACAO_EMITIDO conta=%s", Pseudonimo.de(usuario.id()));
        return Optional.of(tokenPuro);
    }

    public void resetarSenhaComToken(ResetarSenhaRequest request, String ip) {
        rateLimitService.verificarLimiteResetSenha(ip);
        if (request == null || request.token() == null || request.token().isBlank()
                || request.novaSenha() == null || request.novaSenha().isBlank()) {
            throw new BadRequestException("O token e a nova senha são obrigatórios.");
        }
        String tokenHash = refreshTokenService.calcularHashSha256(request.token().trim());
        TokenRecuperacaoSenha registro = tokenRecuperacaoRepository.buscarPorHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("Token de recuperação inválido ou expirado."));

        Instant agora = Instant.now();
        if (registro.foiUtilizado() || registro.estaExpirado(agora)) {
            throw new BadRequestException("Token de recuperação inválido ou expirado.");
        }

        Usuario usuario = userService.buscarPorId(registro.usuarioId())
                .filter(Usuario::ativo)
                .orElseThrow(() -> new BadRequestException("Token de recuperação inválido ou expirado."));

        passwordService.validarPoliticaSenhaForte(request.novaSenha(), usuario.email());

        if (!tokenRecuperacaoRepository.marcarUtilizado(registro.id(), agora)) {
            throw new BadRequestException("Token de recuperação inválido ou expirado.");
        }
        userService.atualizarSenha(usuario, request.novaSenha());
        refreshTokenService.revogarTodasSessoesDoUsuario(usuario.id(), "RESET_SENHA");
        LOG.infof("event=SENHA_REDEFINIDA conta=%s", Pseudonimo.de(usuario.id()));
    }

    public Usuario confirmarEmail(String tokenPuro) {
        if (tokenPuro == null || tokenPuro.isBlank()) {
            throw new BadRequestException("Token de confirmação é obrigatório.");
        }
        String hash = refreshTokenService.calcularHashSha256(tokenPuro);
        TokenConfirmacaoEmail token = tokenConfirmacaoEmailRepository.buscarPorHash(hash)
                .orElseThrow(() -> new BadRequestException("Token de confirmação inválido ou inexistente."));

        Instant agora = Instant.now();
        if (token.foiConfirmado()) {
            return userService.buscarPorIdObrigatorio(token.usuarioId());
        }
        if (token.estaExpirado(agora)) {
            throw new BadRequestException("Token de confirmação expirado. Solicite um novo link.");
        }

        tokenConfirmacaoEmailRepository.marcarConfirmado(token.id(), agora);
        String contaPseudonimo = Pseudonimo.de(token.usuarioId());
        Usuario atualizado = userService.marcarEmailVerificado(token.usuarioId());
        LOG.infof("event=EMAIL_VERIFICADO conta=%s", contaPseudonimo);
        return atualizado;
    }

    public void reenviarConfirmacaoEmail(String emailOuUsuarioId, String ip, String baseUrl) {
        rateLimitService.verificarLimiteConfirmacaoEmail(ip);
        if (emailOuUsuarioId == null || emailOuUsuarioId.isBlank()) {
            throw new BadRequestException("E-mail ou ID de usuário é obrigatório.");
        }
        Optional<Usuario> usuarioOpt = emailOuUsuarioId.contains("@")
                ? userService.buscarPorEmail(UserService.validarENormalizarEmail(emailOuUsuarioId))
                : userService.buscarPorId(emailOuUsuarioId);

        if (usuarioOpt.isEmpty()) {
            return; // Resposta neutra anti-enumeração
        }
        Usuario usuario = usuarioOpt.get();
        if (usuario.emailVerificado()) {
            return; // Já verificado
        }

        Instant agora = Instant.now();
        tokenConfirmacaoEmailRepository.invalidarPendentesDoUsuario(usuario.id(), agora);

        String tokenPuro = refreshTokenService.gerarTokenOpacoAleatorio();
        String tokenHash = refreshTokenService.calcularHashSha256(tokenPuro);

        TokenConfirmacaoEmail novoToken = new TokenConfirmacaoEmail(
                new ObjectId().toHexString(),
                tokenHash,
                usuario.id(),
                usuario.email(),
                agora,
                agora.plus(24, ChronoUnit.HOURS),
                null);
        tokenConfirmacaoEmailRepository.inserir(novoToken);

        emailService.enviarEmailConfirmacao(usuario.email(), usuario.nome(), tokenPuro, baseUrl);
        LOG.infof("event=TOKEN_CONFIRMACAO_EMAIL_ENVIADO conta=%s", Pseudonimo.de(usuario.id()));
    }

    public AuthResponse autenticarPorUsuarioId(String usuarioId, String userAgent, String ip) {
        Usuario usuario = userService.buscarPorIdObrigatorio(usuarioId);
        return emitirRespostaAutenticacao(usuario, userAgent, ip);
    }

    public AuthResponse emitirRespostaAutenticacao(Usuario usuario, String userAgent, String ip) {
        String accessToken = tokenService.gerarAccessToken(usuario);
        ParRefreshToken parRefresh = refreshTokenService.emitirNovo(usuario.id(), userAgent, ip);
        return new AuthResponse(
                accessToken,
                parRefresh.tokenPuro(),
                "Bearer",
                tokenService.getAccessExpirationSeconds(),
                UsuarioResponse.de(usuario, userService.preferenciasDe(usuario.id())));
    }

    private static NotAuthorizedException criarExcecaoNaoAutorizado(String detalhe) {
        return new NotAuthorizedException(
                detalhe,
                Response.status(Response.Status.UNAUTHORIZED)
                        .header("WWW-Authenticate", "Bearer realm=\"trilhara\"")
                        .build());
    }
}

