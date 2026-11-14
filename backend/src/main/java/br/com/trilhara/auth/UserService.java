package br.com.trilhara.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import br.com.trilhara.auth.dto.PreferenciasDTO;
import br.com.trilhara.auth.dto.UsuarioResponse;
import br.com.trilhara.shared.mongo.BsonIds;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class UserService {

    private static final Pattern PADRAO_EMAIL = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,63}$");

    private final UserRepository repository;
    private final PasswordService passwordService;
    private final int maxFalhasLogin;
    private final long bloqueioMinutos;

    public UserService(
            UserRepository repository,
            PasswordService passwordService,
            @ConfigProperty(name = "trilhara.auth.bruteforce.max-failures", defaultValue = "5") int maxFalhasLogin,
            @ConfigProperty(name = "trilhara.auth.bruteforce.lock-minutes", defaultValue = "15") long bloqueioMinutos) {
        this.repository = repository;
        this.passwordService = passwordService;
        this.maxFalhasLogin = Math.max(2, maxFalhasLogin);
        this.bloqueioMinutos = Math.max(1, bloqueioMinutos);
    }

    public Usuario criarUsuario(String nome, String email, String senha, List<Role> roles) {
        String nomeLimpo = validarENormalizarNome(nome);
        String emailNormalizado = validarENormalizarEmail(email);
        passwordService.validarPoliticaSenhaForte(senha, emailNormalizado);

        if (repository.existePorEmail(emailNormalizado)) {
            throw new UserRepository.EmailJaCadastradoException(
                    "Não foi possível concluir o cadastro com os dados informados.");
        }

        Instant agora = Instant.now();
        String senhaHash = passwordService.gerarHash(senha);
        List<Role> rolesFinais = (roles == null || roles.isEmpty()) ? List.of(Role.USER) : List.copyOf(roles);

        Usuario novo = new Usuario(
                new ObjectId().toHexString(),
                nomeLimpo,
                emailNormalizado,
                senhaHash,
                rolesFinais,
                true,
                false,
                0,
                null,
                agora,
                agora,
                agora);

        repository.inserir(novo);
        return novo;
    }

    public Optional<Usuario> buscarPorEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return repository.buscarPorEmail(normalizarEmail(email));
    }

    public Optional<Usuario> buscarPorId(String id) {
        return repository.buscarPorId(id);
    }

    public Usuario buscarPorIdObrigatorio(String id) {
        return repository.buscarPorId(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
    }

    public Usuario registrarFalhaLogin(Usuario usuario, Instant agora) {
        Usuario atualizado = repository.incrementarFalhasLogin(usuario.id(), agora).orElse(usuario);
        if (atualizado.falhasLoginConsecutivas() >= maxFalhasLogin) {
            Instant ate = agora.plus(bloqueioMinutos, ChronoUnit.MINUTES);
            repository.bloquearAte(atualizado.id(), ate);
            return repository.buscarPorId(atualizado.id()).orElse(atualizado);
        }
        return atualizado;
    }

    public Usuario registrarSucessoLogin(Usuario usuario, Instant agora) {
        if (usuario.falhasLoginConsecutivas() == 0 && usuario.bloqueadoAte() == null) {
            return usuario;
        }
        Usuario atualizado = new Usuario(
                usuario.id(),
                usuario.nome(),
                usuario.email(),
                usuario.senhaHash(),
                usuario.roles(),
                usuario.ativo(),
                usuario.emailVerificado(),
                0,
                null,
                usuario.senhaAlteradaEm(),
                usuario.criadoEm(),
                agora);
        repository.atualizar(atualizado);
        return atualizado;
    }

    public Usuario atualizarSenha(Usuario usuario, String novaSenhaPura) {
        passwordService.validarPoliticaSenhaForte(novaSenhaPura, usuario.email());
        String novoHash = passwordService.gerarHash(novaSenhaPura);
        Instant agora = Instant.now();

        Usuario atualizado = new Usuario(
                usuario.id(),
                usuario.nome(),
                usuario.email(),
                novoHash,
                usuario.roles(),
                usuario.ativo(),
                usuario.emailVerificado(),
                0,
                null,
                agora,
                usuario.criadoEm(),
                agora);
        repository.atualizar(atualizado);
        return atualizado;
    }

    public Usuario marcarEmailVerificado(String usuarioId) {
        Usuario usuario = buscarPorIdObrigatorio(usuarioId);
        if (usuario.emailVerificado()) {
            return usuario;
        }
        Instant agora = Instant.now();
        Usuario atualizado = new Usuario(
                usuario.id(),
                usuario.nome(),
                usuario.email(),
                usuario.senhaHash(),
                usuario.roles(),
                usuario.ativo(),
                true,
                usuario.falhasLoginConsecutivas(),
                usuario.bloqueadoAte(),
                usuario.senhaAlteradaEm(),
                usuario.criadoEm(),
                agora);
        repository.atualizar(atualizado);
        return atualizado;
    }

    public Usuario atualizarRoles(String usuarioId, List<Role> novasRoles) {
        if (novasRoles == null || novasRoles.isEmpty()) {
            throw new BadRequestException("Pelo menos uma role deve ser informada.");
        }
        buscarPorIdObrigatorio(usuarioId);
        repository.atualizarRoles(usuarioId, novasRoles.stream().distinct().toList());
        return buscarPorIdObrigatorio(usuarioId);
    }

    public List<UsuarioResponse> listarPublico(int pagina, int tamanho) {
        return repository.listar(pagina, tamanho).stream()
                .map(UsuarioResponse::de)
                .toList();
    }

    /** Preferências persistidas com defaults normativos. */
    public PreferenciasDTO preferenciasDe(String usuarioId) {
        ObjectId alvo = BsonIds.parse(usuarioId, "usuarioId");
        String tema = repository.obterTema(alvo).orElse(PreferenciasDTO.TEMA_PADRAO);
        List<Integer> disponibilidade = repository.obterDisponibilidadeSemanal(alvo);
        int padrao = repository.obterPadraoLembreteMinutos(alvo);
        return new PreferenciasDTO(tema, disponibilidade, padrao);
    }

    /** Atualiza o tema do usuário autenticado e devolve as preferências completas. */
    public PreferenciasDTO atualizarTema(String usuarioId, String tema) {
        ObjectId alvo = BsonIds.parse(usuarioId, "usuarioId");
        repository.salvarTema(alvo, tema);
        return preferenciasDe(usuarioId);
    }

    public static String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public static String validarENormalizarEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("O e-mail é obrigatório.");
        }
        String normalizado = normalizarEmail(email);
        if (normalizado.length() > 254 || !PADRAO_EMAIL.matcher(normalizado).matches()) {
            throw new BadRequestException("Formato de e-mail inválido.");
        }
        return normalizado;
    }

    public static String validarENormalizarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new BadRequestException("O nome é obrigatório.");
        }
        String limpo = nome.trim().replaceAll("\\s+", " ");
        if (limpo.length() < 2 || limpo.length() > 120) {
            throw new BadRequestException("O nome deve ter entre 2 e 120 caracteres.");
        }
        if (limpo.contains("<") || limpo.contains(">") || limpo.contains("\0")) {
            throw new BadRequestException("O nome contém caracteres inválidos.");
        }
        return limpo;
    }
}

