package br.com.trilhara.auth;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import br.com.trilhara.auth.dto.AccountDeletionResponse;
import br.com.trilhara.auth.dto.AlterarRolesRequest;
import br.com.trilhara.auth.dto.AlterarSenhaRequest;
import br.com.trilhara.auth.dto.AuthResponse;
import br.com.trilhara.auth.dto.CadastroRequest;
import br.com.trilhara.auth.dto.ConfirmarEmailRequest;
import br.com.trilhara.auth.dto.ConsumeHandoffRequest;
import br.com.trilhara.auth.dto.ExcluirContaRequest;
import br.com.trilhara.auth.dto.HandoffRequest;
import br.com.trilhara.auth.dto.HandoffResponse;
import br.com.trilhara.auth.dto.LoginRequest;
import br.com.trilhara.auth.dto.LogoutRequest;
import br.com.trilhara.auth.dto.RecuperacaoSenhaResponse;
import br.com.trilhara.auth.dto.RecuperarSenhaRequest;
import br.com.trilhara.auth.dto.ReenviarConfirmacaoRequest;
import br.com.trilhara.auth.dto.RefreshTokenRequest;
import br.com.trilhara.auth.dto.ResetarSenhaRequest;
import br.com.trilhara.auth.dto.SessaoAtivaResponse;
import br.com.trilhara.auth.dto.UsuarioResponse;
import br.com.trilhara.shared.erro.CodigoErro;
import br.com.trilhara.shared.erro.DomainConflictException;
import io.vertx.ext.web.RoutingContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Autenticação & Sessão", description = "Cadastro, login, JWT, rotação de refresh token, gestão de dispositivos, SSO e controle de acesso")
public class AuthResource {

    private static final String MENSAGEM_RECUPERACAO_NEUTRA =
            "Se o e-mail informado estiver cadastrado, enviaremos as instruções para redefinição de senha.";

    private final AuthService authService;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final AuthContext authContext;
    private final EmailService emailService;
    private final HandoffService handoffService;
    private final AccountDeletionService accountDeletionService;
    private final RateLimitService rateLimitService;

    private final String cookieName;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final Optional<String> cookieDomain;

    private final String sessionStateCookieName;
    private final String sessionStateSameSite;

    public AuthResource(
            AuthService authService,
            UserService userService,
            RefreshTokenService refreshTokenService,
            AuthContext authContext,
            EmailService emailService,
            HandoffService handoffService,
            AccountDeletionService accountDeletionService,
            RateLimitService rateLimitService,
            @ConfigProperty(name = "trilhara.auth.refresh.cookie-name", defaultValue = "trilhara_rt") String cookieName,
            @ConfigProperty(name = "trilhara.auth.refresh.cookie-secure", defaultValue = "true") boolean cookieSecure,
            @ConfigProperty(name = "trilhara.auth.refresh.cookie-same-site", defaultValue = "Strict") String cookieSameSite,
            @ConfigProperty(name = "trilhara.auth.refresh.cookie-domain") Optional<String> cookieDomain,
            @ConfigProperty(name = "trilhara.auth.session-state.cookie-name", defaultValue = "trilhara_session_state") String sessionStateCookieName,
            @ConfigProperty(name = "trilhara.auth.session-state.cookie-same-site", defaultValue = "Lax") String sessionStateSameSite) {
        this.authService = authService;
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.authContext = authContext;
        this.emailService = emailService;
        this.handoffService = handoffService;
        this.accountDeletionService = accountDeletionService;
        this.rateLimitService = rateLimitService;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.cookieDomain = cookieDomain;
        this.sessionStateCookieName = sessionStateCookieName;
        this.sessionStateSameSite = sessionStateSameSite;
    }

    @POST
    @Path("/cadastro")
    @Operation(summary = "Cadastra um novo usuário e inicia sessão autenticada")
    @APIResponse(responseCode = "201", description = "Usuário cadastrado e autenticado com sucesso")
    @APIResponse(responseCode = "400", description = "Dados inválidos ou senha fraca")
    @APIResponse(responseCode = "409", description = "Conflito no cadastro")
    @APIResponse(responseCode = "429", description = "Limite de tentativas excedido")
    public Response cadastrar(
            @Valid CadastroRequest request,
            @Context HttpHeaders headers,
            @Context RoutingContext routingContext) {
        String origin = routingContext != null && routingContext.request() != null
                ? routingContext.request().getHeader("Origin")
                : null;
        AuthResponse resposta = authService.cadastrar(
                request,
                extrairUserAgent(headers),
                extrairIpCliente(routingContext),
                origin);

        String host = extrairHost(headers);
        String sessionStateNonce = UUID.randomUUID().toString();
        String rtCookie = construirCookieRefreshToken(resposta.refreshToken(), refreshTokenService.getExpiracaoSegundos(), host);
        String ssCookie = construirCookieSessionState(sessionStateNonce, refreshTokenService.getExpiracaoSegundos(), host);

        return Response.status(Response.Status.CREATED)
                .header(HttpHeaders.SET_COOKIE, ssCookie)
                .header(HttpHeaders.SET_COOKIE, rtCookie)
                .entity(semRefreshNoCorpo(resposta))
                .build();
    }

    @POST
    @Path("/login")
    @Operation(summary = "Autentica o usuário com e-mail e senha")
    @APIResponse(responseCode = "200", description = "Login realizado com sucesso")
    @APIResponse(responseCode = "401", description = "Credenciais inválidas")
    @APIResponse(responseCode = "429", description = "Conta bloqueada temporariamente ou rate limit excedido")
    public Response login(
            @Valid LoginRequest request,
            @Context HttpHeaders headers,
            @Context RoutingContext routingContext) {
        AuthResponse resposta = authService.login(
                request,
                extrairUserAgent(headers),
                extrairIpCliente(routingContext));

        String host = extrairHost(headers);
        String sessionStateNonce = UUID.randomUUID().toString();
        String rtCookie = construirCookieRefreshToken(resposta.refreshToken(), refreshTokenService.getExpiracaoSegundos(), host);
        String ssCookie = construirCookieSessionState(sessionStateNonce, refreshTokenService.getExpiracaoSegundos(), host);

        return Response.ok(semRefreshNoCorpo(resposta))
                .header(HttpHeaders.SET_COOKIE, ssCookie)
                .header(HttpHeaders.SET_COOKIE, rtCookie)
                .build();
    }

    @POST
    @Path("/refresh")
    @Operation(summary = "Renova o access token aplicando rotação obrigatória do refresh token")
    @APIResponse(responseCode = "200", description = "Tokens renovados com sucesso")
    @APIResponse(responseCode = "401", description = "Refresh token inválido, expirado ou reutilizado")
    public Response refresh(
            @Valid RefreshTokenRequest request,
            @Context HttpHeaders headers,
            @Context RoutingContext routingContext) {
        String tokenParaRenovar = extrairRefreshToken(request != null ? request.refreshToken() : null, headers);
        AuthResponse resposta = authService.renovarToken(
                tokenParaRenovar,
                extrairUserAgent(headers),
                extrairIpCliente(routingContext));

        String host = extrairHost(headers);
        String sessionStateNonce = UUID.randomUUID().toString();
        String rtCookie = construirCookieRefreshToken(resposta.refreshToken(), refreshTokenService.getExpiracaoSegundos(), host);
        String ssCookie = construirCookieSessionState(sessionStateNonce, refreshTokenService.getExpiracaoSegundos(), host);

        return Response.ok(semRefreshNoCorpo(resposta))
                .header(HttpHeaders.SET_COOKIE, ssCookie)
                .header(HttpHeaders.SET_COOKIE, rtCookie)
                .build();
    }

    @POST
    @Path("/handoff")
    @Autenticado
    @Operation(summary = "Emite um código de handoff SSO de uso único com TTL de 60s para transição entre origens locais")
    @APIResponse(responseCode = "200", description = "Código emitido com sucesso")
    @APIResponse(responseCode = "400", description = "Destino inválido ou fora da allowlist")
    @APIResponse(responseCode = "401", description = "Não autenticado")
    public HandoffResponse emitirHandoff(@Valid HandoffRequest request,
            @Context RoutingContext routingContext) {
        // D-45: 10/min por IP — cada emissão persiste um código em tokens_handoff_sso.
        rateLimitService.verificarLimiteHandoffEmissao(extrairIpCliente(routingContext));
        return handoffService.emitirHandoff(authContext.usuarioId(), request.destino());
    }

    @POST
    @Path("/handoff/consume")
    @Operation(summary = "Consome atomicamente um código de handoff SSO e emite sessão autenticada para a nova origem")
    @APIResponse(responseCode = "200", description = "Sessão iniciada com sucesso")
    @APIResponse(responseCode = "400", description = "Destino divergente ou requisição inválida")
    @APIResponse(responseCode = "401", description = "Código inválido, expirado ou reutilizado")
    public Response consumirHandoff(
            @Valid ConsumeHandoffRequest request,
            @Context HttpHeaders headers,
            @Context RoutingContext routingContext) {
        // D-45: 20/min por IP — contenção de carga (o código é opaco e de uso único).
        rateLimitService.verificarLimiteHandoffConsumo(extrairIpCliente(routingContext));

        String host = extrairHost(headers);
        String usuarioId = handoffService.consumirHandoff(request.codigo(), host);
        AuthResponse resposta = authService.autenticarPorUsuarioId(
                usuarioId,
                extrairUserAgent(headers),
                extrairIpCliente(routingContext));

        String sessionStateNonce = UUID.randomUUID().toString();
        String rtCookie = construirCookieRefreshToken(resposta.refreshToken(), refreshTokenService.getExpiracaoSegundos(), host);
        String ssCookie = construirCookieSessionState(sessionStateNonce, refreshTokenService.getExpiracaoSegundos(), host);

        return Response.ok(semRefreshNoCorpo(resposta))
                .header(HttpHeaders.SET_COOKIE, ssCookie)
                .header(HttpHeaders.SET_COOKIE, rtCookie)
                .build();
    }

    @POST
    @Path("/logout")
    @Operation(summary = "Encerra a sessão atual invalidando a família do refresh token")
    @APIResponse(responseCode = "204", description = "Sessão encerrada com sucesso")
    public Response logout(@Valid LogoutRequest request, @Context HttpHeaders headers) {
        String refreshToken = extrairRefreshToken(request != null ? request.refreshToken() : null, headers);
        authService.logout(refreshToken, authContext.usuarioId());
        String host = extrairHost(headers);
        return Response.noContent()
                .header(HttpHeaders.SET_COOKIE, construirCookieExpirado(host))
                .header(HttpHeaders.SET_COOKIE, construirCookieSessionStateExpirado(host))
                .build();
    }

    @POST
    @Path("/logout-all")
    @Autenticado
    @Operation(summary = "Revoga todas as sessões ativas do usuário em todos os dispositivos")
    @APIResponse(responseCode = "204", description = "Todas as sessões foram revogadas")
    @APIResponse(responseCode = "401", description = "Não autenticado")
    public Response logoutTodosDispositivos(@Context HttpHeaders headers) {
        authService.logoutTodosDispositivos(authContext.usuarioId());
        String host = extrairHost(headers);
        return Response.noContent()
                .header(HttpHeaders.SET_COOKIE, construirCookieExpirado(host))
                .header(HttpHeaders.SET_COOKIE, construirCookieSessionStateExpirado(host))
                .build();
    }

    @GET
    @Path("/me")
    @Autenticado
    @Operation(summary = "Retorna os dados do perfil do usuário autenticado")
    @APIResponse(responseCode = "200", description = "Perfil do usuário")
    @APIResponse(responseCode = "401", description = "Não autenticado")
    public UsuarioResponse meuPerfil() {
        Usuario usuario = userService.buscarPorIdObrigatorio(authContext.usuarioId());
        return UsuarioResponse.de(usuario, userService.preferenciasDe(usuario.id()));
    }

    @PUT
    @Path("/senha")
    @Autenticado
    @Operation(summary = "Altera a senha do usuário autenticado e revoga todas as sessões ativas")
    @APIResponse(responseCode = "204", description = "Senha alterada com sucesso")
    @APIResponse(responseCode = "400", description = "Nova senha fraca ou idêntica à atual")
    @APIResponse(responseCode = "401", description = "Senha atual incorreta ou usuário não autenticado")
    public Response alterarSenha(@Valid AlterarSenhaRequest request, @Context HttpHeaders headers) {
        authService.alterarSenha(authContext.usuarioId(), request);
        String host = extrairHost(headers);
        return Response.noContent()
                .header(HttpHeaders.SET_COOKIE, construirCookieExpirado(host))
                .header(HttpHeaders.SET_COOKIE, construirCookieSessionStateExpirado(host))
                .build();
    }

    @GET
    @Path("/sessoes")
    @Autenticado
    @Operation(summary = "Lista as sessões/dispositivos ativos do usuário autenticado")
    @APIResponse(responseCode = "200", description = "Lista de sessões ativas")
    public List<SessaoAtivaResponse> listarSessoes() {
        return authService.listarSessoesAtivas(authContext.usuarioId());
    }

    @DELETE
    @Path("/sessoes/{familiaId}")
    @Autenticado
    @Operation(summary = "Revoga uma sessão específica do usuário autenticado")
    @APIResponse(responseCode = "204", description = "Sessão revogada")
    @APIResponse(responseCode = "404", description = "Sessão não encontrada")
    public void revogarSessao(@PathParam("familiaId") String familiaId) {
        if (!authService.revogarSessaoEspecifica(authContext.usuarioId(), familiaId)) {
            throw new NotFoundException("Sessão ativa não encontrada.");
        }
    }

    @DELETE
    @Path("/conta")
    @Autenticado
    @Operation(summary = "Solicita a exclusão definitiva da conta do usuário nas 18 coleções privadas")
    @APIResponse(responseCode = "202", description = "Exclusão aceita e em processamento assíncrono (application/json)")
    @APIResponse(responseCode = "400", description = "Chave de idempotência ausente ou confirmação textual inválida")
    @APIResponse(responseCode = "401", description = "Senha incorreta ou usuário não autenticado")
    @APIResponse(responseCode = "409", description = "Chave de idempotência já usada com outro payload funcional")
    @APIResponse(responseCode = "429", description = "Muitas tentativas de exclusão para esta conta")
    public Response excluirConta(
            @Valid ExcluirContaRequest request,
            @HeaderParam("Idempotency-Key") String idempotencyKeyHeader,
            @Context HttpHeaders headers) {
        if (idempotencyKeyHeader == null || idempotencyKeyHeader.isBlank()) {
            throw new DomainConflictException(
                    CodigoErro.IDEMPOTENCY_KEY_REQUIRED,
                    "O cabeçalho Idempotency-Key é obrigatório para solicitar a exclusão da conta.");
        }

        String idempotencyKey = idempotencyKeyHeader.trim();
        String usuarioId = authContext.usuarioId();

        AccountDeletionResponse resposta = accountDeletionService.solicitarExclusao(
                usuarioId, request, idempotencyKey);
        String host = extrairHost(headers);
        return Response.status(Response.Status.ACCEPTED)
                .type(MediaType.APPLICATION_JSON)
                .entity(resposta)
                .header(HttpHeaders.SET_COOKIE, construirCookieExpirado(host))
                .header(HttpHeaders.SET_COOKIE, construirCookieSessionStateExpirado(host))
                .build();
    }

    @POST
    @Path("/recuperar-senha")
    @Operation(summary = "Solicita recuperação de senha por e-mail sem expor se a conta existe")
    @APIResponse(responseCode = "200", description = "Solicitação processada com resposta neutra")
    public RecuperacaoSenhaResponse solicitarRecuperacaoSenha(
            @Valid RecuperarSenhaRequest request,
            @Context RoutingContext routingContext) {
        Optional<String> tokenOpt = authService.solicitarRecuperacaoSenha(request, extrairIpCliente(routingContext));
        tokenOpt.ifPresent(token -> {
            String origin = routingContext != null && routingContext.request() != null
                    ? routingContext.request().getHeader("Origin")
                    : null;
            emailService.enviarEmailRecuperacaoSenha(request.email(), token, origin);
        });
        return new RecuperacaoSenhaResponse(MENSAGEM_RECUPERACAO_NEUTRA);
    }

    @POST
    @Path("/resetar-senha")
    @Operation(summary = "Redefine a senha utilizando um token único de recuperação")
    @APIResponse(responseCode = "204", description = "Senha redefinida e sessões anteriores revogadas")
    @APIResponse(responseCode = "400", description = "Token inválido/expirado ou nova senha fraca")
    public Response resetarSenha(@Valid ResetarSenhaRequest request, @Context HttpHeaders headers, @Context RoutingContext routingContext) {
        authService.resetarSenhaComToken(request, extrairIpCliente(routingContext));
        String host = extrairHost(headers);
        return Response.noContent()
                .header(HttpHeaders.SET_COOKIE, construirCookieExpirado(host))
                .header(HttpHeaders.SET_COOKIE, construirCookieSessionStateExpirado(host))
                .build();
    }

    @POST
    @Path("/confirmar-email")
    @Operation(summary = "Confirma o endereço de e-mail do usuário utilizando o token recebido")
    @APIResponse(responseCode = "200", description = "E-mail confirmado com sucesso")
    @APIResponse(responseCode = "400", description = "Token inválido ou expirado")
    public UsuarioResponse confirmarEmail(
            @QueryParam("token") String tokenQuery,
            ConfirmarEmailRequest body) {
        String tokenPuro = tokenQuery != null && !tokenQuery.isBlank()
                ? tokenQuery
                : (body != null ? body.token() : null);
        Usuario usuario = authService.confirmarEmail(tokenPuro);
        return UsuarioResponse.de(usuario, userService.preferenciasDe(usuario.id()));
    }

    @POST
    @Path("/reenviar-confirmacao")
    @Operation(summary = "Reenvia o e-mail de confirmação de conta se ela ainda não foi verificada")
    @APIResponse(responseCode = "200", description = "Processado com resposta neutra")
    public Map<String, String> reenviarConfirmacao(
            @Valid ReenviarConfirmacaoRequest request,
            @Context RoutingContext routingContext) {
        String origin = routingContext != null && routingContext.request() != null
                ? routingContext.request().getHeader("Origin")
                : null;
        authService.reenviarConfirmacaoEmail(request.email(), extrairIpCliente(routingContext), origin);
        return Map.of("mensagem", "Se a conta existir e não estiver confirmada, uma nova mensagem foi enviada.");
    }

    @GET
    @Path("/admin/usuarios")
    @Autenticado(roles = Role.ADMIN)
    @Operation(summary = "Lista todos os usuários cadastrados com paginação (restrito a ADMIN)")
    @APIResponse(responseCode = "200", description = "Lista paginada de usuários")
    @APIResponse(responseCode = "403", description = "Acesso negado para usuários sem role ADMIN")
    public List<UsuarioResponse> listarUsuariosAdmin(
            @QueryParam("pagina") @DefaultValue("0") @Min(0) int pagina,
            @QueryParam("tamanho") @DefaultValue("50") @Min(1) @Max(100) int tamanho) {
        return userService.listarPublico(pagina, tamanho);
    }

    @PATCH
    @Path("/admin/usuarios/{id}/papeis")
    @Autenticado(roles = Role.ADMIN)
    @Operation(summary = "Atualiza os papéis de um usuário (restrito a ADMIN)")
    @APIResponse(responseCode = "200", description = "Papéis atualizados com sucesso")
    @APIResponse(responseCode = "403", description = "Acesso negado para usuários sem role ADMIN")
    public UsuarioResponse atualizarRolesUsuario(
            @PathParam("id") String id,
            @Valid @NotNull AlterarRolesRequest request) {
        if (id.equals(authContext.usuarioId()) && !request.papeis().contains(Role.ADMIN)) {
            throw new BadRequestException("Você não pode remover seu próprio perfil de administrador.");
        }
        Usuario atualizado = userService.atualizarRoles(id, request.papeis());
        return UsuarioResponse.de(atualizado, userService.preferenciasDe(atualizado.id()));
    }

    private String extrairRefreshToken(String tokenDoBody, HttpHeaders headers) {
        if (tokenDoBody != null && !tokenDoBody.isBlank()) {
            return tokenDoBody.trim();
        }
        if (headers != null && headers.getCookies() != null) {
            Cookie cookie = headers.getCookies().get(cookieName);
            if (cookie != null && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue().trim();
            }
        }
        return null;
    }

    public String determinarCookieDomain(String host) {
        if (cookieDomain == null || cookieDomain.isEmpty() || cookieDomain.get().isBlank()) {
            return null;
        }
        String configured = cookieDomain.get().trim();
        if ("localhost".equalsIgnoreCase(configured) || ".localhost".equalsIgnoreCase(configured)) {
            return null;
        }
        if (host != null && !host.isBlank()) {
            String hostSemPorta = host.split(":")[0].trim().toLowerCase();
            if (hostSemPorta.equals("localhost") || hostSemPorta.endsWith(".localhost") || hostSemPorta.equals("127.0.0.1")) {
                return null;
            }
            // Em produção: só emitir o Domain configurado se o Host validado pertencer ao domínio configurado
            String baseDomain = configured.startsWith(".") ? configured.substring(1).toLowerCase() : configured.toLowerCase();
            if (hostSemPorta.equals(baseDomain) || hostSemPorta.endsWith("." + baseDomain)) {
                return configured.startsWith(".") ? configured : "." + configured;
            }
            return null;
        }
        return null;
    }

    public String construirCookieRefreshToken(String valor, long maxAgeSegundos, String host) {
        StringBuilder sb = new StringBuilder();
        sb.append(cookieName).append("=").append(valor != null ? valor : "");
        sb.append("; Path=/api/auth");
        sb.append("; Max-Age=").append(Math.max(0, maxAgeSegundos));
        sb.append("; HttpOnly");
        if (cookieSecure) {
            sb.append("; Secure");
        }
        sb.append("; SameSite=").append(cookieSameSite);
        String domain = determinarCookieDomain(host);
        if (domain != null) {
            sb.append("; Domain=").append(domain);
        }
        return sb.toString();
    }

    public String construirCookieSessionState(String valor, long maxAgeSegundos, String host) {
        StringBuilder sb = new StringBuilder();
        sb.append(sessionStateCookieName).append("=").append(valor != null ? valor : "");
        sb.append("; Path=/");
        sb.append("; Max-Age=").append(Math.max(0, maxAgeSegundos));
        if (cookieSecure) {
            sb.append("; Secure");
        }
        sb.append("; SameSite=").append(sessionStateSameSite);
        String domain = determinarCookieDomain(host);
        if (domain != null) {
            sb.append("; Domain=").append(domain);
        }
        return sb.toString();
    }

    private String construirCookieExpirado(String host) {
        return construirCookieRefreshToken("", 0, host);
    }

    private String construirCookieSessionStateExpirado(String host) {
        return construirCookieSessionState("", 0, host);
    }

    private static String extrairHost(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String fwd = headers.getHeaderString("X-Forwarded-Host");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return headers.getHeaderString(HttpHeaders.HOST);
    }

    private static String extrairUserAgent(HttpHeaders headers) {
        if (headers == null) {
            return "desconhecido";
        }
        String ua = headers.getHeaderString(HttpHeaders.USER_AGENT);
        return ua != null && !ua.isBlank() ? ua : "desconhecido";
    }

    private static AuthResponse semRefreshNoCorpo(AuthResponse r) {
        return new AuthResponse(r.accessToken(), null, r.tokenType(), r.expiresIn(), r.usuario());
    }

    private static String extrairIpCliente(RoutingContext routingContext) {
        if (routingContext != null && routingContext.request() != null
                && routingContext.request().remoteAddress() != null) {
            return routingContext.request().remoteAddress().host();
        }
        return "desconhecido";
    }
}
