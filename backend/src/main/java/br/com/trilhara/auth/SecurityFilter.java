package br.com.trilhara.auth;

import java.io.IOException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import br.com.trilhara.auth.TokenService.ClaimsToken;
import br.com.trilhara.auth.TokenService.TokenInvalidoException;
import br.com.trilhara.shared.TimezoneContext;
import br.com.trilhara.shared.erro.CodigoErro;
import br.com.trilhara.shared.erro.ProblemDetails;
import br.com.trilhara.shared.erro.ProblemDetailsFactory;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;

@Provider
@ApplicationScoped
@Priority(Priorities.AUTHENTICATION)
public class SecurityFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ORIGIN_HEADER = "Origin";
    private static final Set<String> ROTAS_AUTH_PUBLICAS = Set.of(
            "/api/auth/cadastro",
            "/api/auth/login",
            "/api/auth/logout",
            "/api/auth/refresh",
            "/api/auth/recuperar-senha",
            "/api/auth/resetar-senha",
            "/api/auth/confirmar-email",
            "/api/auth/reenviar-confirmacao",
            "/api/auth/handoff/consume");

    @Context
    ResourceInfo resourceInfo;

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final AuthContext authContext;
    private final TimezoneContext timezoneContext;
    private final Set<String> origensPermitidas;
    private volatile boolean strictMode;

    public SecurityFilter(
            TokenService tokenService,
            UserRepository userRepository,
            AuthContext authContext,
            TimezoneContext timezoneContext,
            @ConfigProperty(name = "trilhara.auth.strict-mode", defaultValue = "true") boolean strictMode,
            @ConfigProperty(name = "trilhara.auth.cors.allowed-origins", defaultValue = "http://localhost:5173") String allowedOrigins) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.authContext = authContext;
        this.timezoneContext = timezoneContext;
        this.strictMode = strictMode;
        this.origensPermitidas = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (tratarPreflight(requestContext)) {
            return;
        }

        definirTimezone(requestContext);

        String path = normalizarPath(requestContext.getUriInfo().getPath());
        if (autenticarSeNecessario(requestContext)) {
            return;
        }

        validarAutorizacao(requestContext, path);
    }

    private boolean tratarPreflight(ContainerRequestContext requestContext) {
        if (!"OPTIONS".equalsIgnoreCase(requestContext.getMethod())) {
            return false;
        }

        String origin = requestContext.getHeaderString(ORIGIN_HEADER);
        if (origin != null && origensPermitidas.contains(origin)) {
            requestContext.abortWith(Response.ok()
                    .header("Access-Control-Allow-Origin", origin)
                    .header("Access-Control-Allow-Credentials", "true")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept, Idempotency-Key, X-Timezone, X-Correlation-ID")
                    .header("Access-Control-Expose-Headers", "X-Correlation-ID, Retry-After")
                    .header("Access-Control-Max-Age", "3600")
                    .build());
        }
        return true;
    }

    private void definirTimezone(ContainerRequestContext requestContext) {
        if (timezoneContext != null) {
            timezoneContext.definir(requestContext.getHeaderString(TimezoneContext.HEADER_TIMEZONE));
        }
    }

    /** Retorna true quando uma credencial inválida já abortou a requisição. */
    private boolean autenticarSeNecessario(ContainerRequestContext requestContext) {
        String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || authHeader.isBlank()) {
            return false;
        }
        if (!authHeader.startsWith(BEARER_PREFIX)) {
            abortarNaoAutorizado(requestContext, "Formato de autorização inválido. Utilize 'Bearer <token>'.");
            return true;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            abortarNaoAutorizado(requestContext, "Token de acesso ausente.");
            return true;
        }

        try {
            ClaimsToken claims = tokenService.validarAccessToken(token);
            Usuario usuario = userRepository.buscarPorId(claims.usuarioId()).orElse(null);
            if (usuario == null) {
                abortarNaoAutorizado(requestContext, "Usuário inválido ou conta inexistente.");
                return true;
            }
            if (!usuario.ativo()) {
                String path = requestContext.getUriInfo() != null ? requestContext.getUriInfo().getPath() : "";
                boolean isExclusaoConta = path.endsWith("/auth/conta") || path.endsWith("/conta");
                if (!isExclusaoConta || !"DELETE".equalsIgnoreCase(requestContext.getMethod())) {
                    abortarNaoAutorizado(requestContext, "Usuário inválido ou conta desativada.");
                    return true;
                }
            }
            if (senhaAlteradaDepoisDaEmissao(claims, usuario)) {
                abortarNaoAutorizado(requestContext, "Sessão expirada devido a alteração de credenciais.");
                return true;
            }

            ClaimsToken claimsAtualizados = atualizarClaims(claims, usuario);
            authContext.definir(claimsAtualizados);
            configurarSecurityContext(requestContext, claimsAtualizados);
            return false;
        } catch (TokenInvalidoException exception) {
            abortarNaoAutorizado(requestContext, exception.getMessage());
            return true;
        }
    }

    private static boolean senhaAlteradaDepoisDaEmissao(ClaimsToken claims, Usuario usuario) {
        return claims.senhaAlteradaEm() != null && usuario.senhaAlteradaEm() != null
                && usuario.senhaAlteradaEm().toEpochMilli() > claims.senhaAlteradaEm().toEpochMilli() + 1000;
    }

    private static ClaimsToken atualizarClaims(ClaimsToken claims, Usuario usuario) {
        return new ClaimsToken(
                usuario.id(),
                usuario.email(),
                usuario.roles(),
                claims.jti(),
                claims.emitidoEm(),
                claims.expiraEm(),
                claims.senhaAlteradaEm());
    }

    private void validarAutorizacao(ContainerRequestContext requestContext, String path) {
        Autenticado anotacaoAutenticado = obterAnotacaoAutenticado();
        boolean rotaAuthProtegida = path.startsWith("/api/auth") && !ROTAS_AUTH_PUBLICAS.contains(path);
        boolean rotaAdmin = path.startsWith("/api/admin") || path.startsWith("/api/auth/admin");
        boolean rotaNegocioProtegida = path.startsWith("/api/trilhas")
                || path.startsWith("/api/notas")
                || path.startsWith("/api/blocos")
                || path.startsWith("/api/tarefas")
                || path.startsWith("/api/listas-tarefas")
                || path.startsWith("/api/push");

        if ((rotaAdmin || anotacaoAutenticado != null || rotaAuthProtegida || (rotaNegocioProtegida && strictMode))
                && !authContext.estaAutenticado()) {
            abortarNaoAutorizado(requestContext, "Autenticação necessária para acessar este recurso.");
            return;
        }

        if (authContext.estaAutenticado()) {
            if (anotacaoAutenticado != null && anotacaoAutenticado.roles().length > 0) {
                boolean autorizado = Arrays.stream(anotacaoAutenticado.roles())
                        .anyMatch(authContext::possuiRole);
                if (!autorizado) {
                    abortarProibido(requestContext, "Acesso negado: você não possui permissão para realizar esta operação.");
                    return;
                }
            }
            if (rotaAdmin && !authContext.possuiRole(Role.ADMIN)) {
                abortarProibido(requestContext, "Acesso negado: permissão de administrador (ADMIN) necessária.");
            }
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        String origin = requestContext.getHeaderString(ORIGIN_HEADER);
        if (origin != null && origensPermitidas.contains(origin)) {
            responseContext.getHeaders().putSingle("Access-Control-Allow-Origin", origin);
            responseContext.getHeaders().putSingle("Access-Control-Allow-Credentials", "true");
            responseContext.getHeaders().putSingle("Access-Control-Expose-Headers", "X-Correlation-ID, Retry-After");
            responseContext.getHeaders().putSingle("Vary", ORIGIN_HEADER);
        }

        String path = normalizarPath(requestContext.getUriInfo().getPath());
        if (path.startsWith("/api/auth")) {
            responseContext.getHeaders().putSingle("Cache-Control", "no-store, no-cache, must-revalidate");
            responseContext.getHeaders().putSingle("Pragma", "no-cache");
        }
    }

    private Autenticado obterAnotacaoAutenticado() {
        if (resourceInfo == null) {
            return null;
        }
        Method method = resourceInfo.getResourceMethod();
        if (method != null && method.isAnnotationPresent(Autenticado.class)) {
            return method.getAnnotation(Autenticado.class);
        }
        Class<?> clazz = resourceInfo.getResourceClass();
        if (clazz != null && clazz.isAnnotationPresent(Autenticado.class)) {
            return clazz.getAnnotation(Autenticado.class);
        }
        return null;
    }

    private void configurarSecurityContext(ContainerRequestContext requestContext, ClaimsToken claims) {
        SecurityContext atual = requestContext.getSecurityContext();
        boolean seguro = atual != null && atual.isSecure();
        requestContext.setSecurityContext(new SecurityContext() {
            @Override
            public Principal getUserPrincipal() {
                return claims::usuarioId;
            }

            @Override
            public boolean isUserInRole(String role) {
                return claims.roles().stream().anyMatch(r -> r.name().equalsIgnoreCase(role));
            }

            @Override
            public boolean isSecure() {
                return seguro;
            }

            @Override
            public String getAuthenticationScheme() {
                return "Bearer";
            }
        });
    }

    private static String normalizarPath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return "/";
        }
        return rawPath.startsWith("/") ? rawPath : "/" + rawPath;
    }

    private static void abortarNaoAutorizado(ContainerRequestContext requestContext, String detalhe) {
        ProblemDetails problem = ProblemDetailsFactory.criar(
                CodigoErro.UNAUTHORIZED,
                detalhe,
                instanciaDaRequisicao(requestContext));
        requestContext.abortWith(Response.status(problem.status())
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"trilhara\"")
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build());
    }

    private static void abortarProibido(ContainerRequestContext requestContext, String detalhe) {
        ProblemDetails problem = ProblemDetailsFactory.criar(
                CodigoErro.FORBIDDEN,
                detalhe,
                instanciaDaRequisicao(requestContext));
        requestContext.abortWith(Response.status(problem.status())
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build());
    }

    private static String instanciaDaRequisicao(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo() != null ? requestContext.getUriInfo().getPath() : null;
        return normalizarPath(path);
    }
}

