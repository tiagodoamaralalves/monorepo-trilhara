package br.com.trilhara.auth;

import static com.mongodb.client.model.Filters.eq;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import br.com.trilhara.shared.mongo.BsonDates;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserRepository {

    private static final String CAMPO_EMAIL = "email";
    private static final String CAMPO_CRIADO_EM = "criadoEm";
    private static final String CAMPO_FALHAS_LOGIN = "falhasLoginConsecutivas";
    private static final String CAMPO_ATUALIZADO_EM = "atualizadoEm";
    private static final String CAMPO_BLOQUEADO_ATE = "bloqueadoAte";
    private static final String CAMPO_ROLES = "roles";
    private static final String CAMPO_PREFERENCIAS = "preferencias";
    private static final String CAMPO_SENHA_ALTERADA_EM = "senhaAlteradaEm";

    private final MongoCollection<Document> usuarios;

    public UserRepository(
            MongoClient client,
            @ConfigProperty(name = "quarkus.mongodb.database") String database) {
        this.usuarios = client.getDatabase(database).getCollection("usuarios");
    }

    public void inserir(Usuario usuario) {
        try {
            usuarios.insertOne(paraDocumento(usuario));
        } catch (MongoWriteException exception) {
            if (exception.getError() != null && exception.getError().getCode() == 11000) {
                throw new EmailJaCadastradoException("Não foi possível concluir o cadastro com os dados informados.");
            }
            throw exception;
        }
    }

    public boolean atualizar(Usuario usuario) {
        if (!ObjectId.isValid(usuario.id())) {
            return false;
        }
        return usuarios.replaceOne(
                eq("_id", new ObjectId(usuario.id())),
                paraDocumento(usuario),
                new ReplaceOptions().upsert(false)).getMatchedCount() > 0;
    }

    public Optional<Usuario> buscarPorId(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(usuarios.find(eq("_id", new ObjectId(id))).first())
                .map(this::paraUsuario);
    }

    public Optional<Usuario> buscarPorEmail(String emailNormalizado) {
        if (emailNormalizado == null || emailNormalizado.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(usuarios.find(eq(CAMPO_EMAIL, emailNormalizado)).first())
                .map(this::paraUsuario);
    }

    public boolean existePorEmail(String emailNormalizado) {
        if (emailNormalizado == null || emailNormalizado.isBlank()) {
            return false;
        }
        return usuarios.countDocuments(eq(CAMPO_EMAIL, emailNormalizado)) > 0;
    }

    /**
 * {@code true} quando a conta possui o checkpoint {@code exclusaoConta}.
 *
 * <p>Leitura pontual e projetada: o record {@link Usuario} não carrega o subdocumento para
 * não alterar o contrato de domínio nem os ~20 call sites de {@code new Usuario(...)}.</p>
 */
    public boolean estaEmExclusao(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            return false;
        }
        Document doc = usuarios.find(eq("_id", new ObjectId(id)))
                .projection(new Document("exclusaoConta", 1))
                .first();
        return doc != null && doc.get("exclusaoConta") != null;
    }

    public List<Usuario> listar(int pagina, int tamanho) {
        List<Usuario> lista = new ArrayList<>();
        usuarios.find().sort(Sorts.descending(CAMPO_CRIADO_EM))
                .skip(pagina * tamanho).limit(tamanho)
                .forEach(doc -> lista.add(paraUsuario(doc)));
        return lista;
    }

    public Optional<Usuario> incrementarFalhasLogin(String id, Instant agora) {
        if (id == null || !ObjectId.isValid(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(usuarios.findOneAndUpdate(
                eq("_id", new ObjectId(id)),
                Updates.combine(Updates.inc(CAMPO_FALHAS_LOGIN, 1),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(agora))),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)))
                .map(this::paraUsuario);
    }

    public void bloquearAte(String id, Instant ate) {
        if (id == null || !ObjectId.isValid(id)) {
            return;
        }
        usuarios.updateOne(eq("_id", new ObjectId(id)), Updates.set(CAMPO_BLOQUEADO_ATE, BsonDates.toBsonDate(ate)));
    }

    public void atualizarRoles(String id, List<Role> roles) {
        if (id == null || !ObjectId.isValid(id)) {
            return;
        }
        usuarios.updateOne(eq("_id", new ObjectId(id)), Updates.combine(
                Updates.set(CAMPO_ROLES, roles.stream().map(Role::name).toList()),
                Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))));
    }

    public List<Integer> obterDisponibilidadeSemanal(ObjectId usuarioId) {
        Document doc = usuarios.find(eq("_id", usuarioId)).first();
        if (doc == null) {
            return List.of(0, 0, 0, 0, 0, 0, 0);
        }
        Document pref = doc.get(CAMPO_PREFERENCIAS, Document.class);
        if (pref == null) {
            return List.of(0, 0, 0, 0, 0, 0, 0);
        }
        List<Integer> disp = pref.getList("disponibilidadeSemanalMinutos", Integer.class);
        if (disp == null || disp.size() != 7) {
            return List.of(0, 0, 0, 0, 0, 0, 0);
        }
        return disp;
    }

    public void salvarDisponibilidadeSemanal(ObjectId usuarioId, List<Integer> minutos) {
        usuarios.updateOne(
                eq("_id", usuarioId),
                Updates.combine(
                        Updates.set("preferencias.disponibilidadeSemanalMinutos", minutos),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
                )
        );
    }

    public int obterPadraoLembreteMinutos(ObjectId usuarioId) {
        Document doc = usuarios.find(eq("_id", usuarioId)).first();
        if (doc == null) {
            return 15;
        }
        Document pref = doc.get(CAMPO_PREFERENCIAS, Document.class);
        if (pref == null) {
            return 15;
        }
        Integer padrao = pref.getInteger("padraoLembreteMinutosAntes");
        return padrao != null ? padrao : 15;
    }

    public void salvarPadraoLembreteMinutos(ObjectId usuarioId, int minutos) {
        usuarios.updateOne(
                eq("_id", usuarioId),
                Updates.combine(
                        Updates.set("preferencias.padraoLembreteMinutosAntes", minutos),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
                )
        );
    }

    /** Tema persistido em {@code usuarios.preferencias.tema}. */
    public Optional<String> obterTema(ObjectId usuarioId) {
        Document doc = usuarios.find(eq("_id", usuarioId)).first();
        if (doc == null) {
            return Optional.empty();
        }
        Document pref = doc.get(CAMPO_PREFERENCIAS, Document.class);
        if (pref == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(pref.getString("tema"));
    }

    public void salvarTema(ObjectId usuarioId, String tema) {
        usuarios.updateOne(
                eq("_id", usuarioId),
                Updates.combine(
                        Updates.set("preferencias.tema", tema),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
                )
        );
    }

    public boolean excluir(String id) {
        return id != null
                && ObjectId.isValid(id)
                && usuarios.deleteOne(eq("_id", new ObjectId(id))).getDeletedCount() > 0;
    }

    private Document paraDocumento(Usuario u) {
        Document doc = new Document();
        doc.put("_id", new ObjectId(u.id()));
        doc.put("nome", u.nome());
        doc.put(CAMPO_EMAIL, u.email());
        doc.put("senhaHash", u.senhaHash());
        doc.put(CAMPO_ROLES, u.roles().stream().map(Role::name).toList());
        doc.put("ativo", u.ativo());
        doc.put("emailVerificado", u.emailVerificado());
        doc.put(CAMPO_FALHAS_LOGIN, u.falhasLoginConsecutivas());
        doc.put(CAMPO_BLOQUEADO_ATE, BsonDates.toBsonDate(u.bloqueadoAte()));
        doc.put(CAMPO_SENHA_ALTERADA_EM, BsonDates.toBsonDate(u.senhaAlteradaEm()));
        doc.put(CAMPO_CRIADO_EM, BsonDates.toBsonDate(u.criadoEm() != null ? u.criadoEm() : Instant.now()));
        doc.put(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(u.atualizadoEm() != null ? u.atualizadoEm() : Instant.now()));
        return doc;
    }

    private Usuario paraUsuario(Document doc) {
        ObjectId objectId = doc.getObjectId("_id");
        List<String> rolesRaw = doc.getList(CAMPO_ROLES, String.class, List.of(Role.USER.name()));
        List<Role> roles = rolesRaw.stream()
                .map(r -> {
                    try {
                        return Role.valueOf(r);
                    } catch (IllegalArgumentException _) {
                        return Role.USER;
                    }
                })
                .distinct()
                .toList();

        return new Usuario(
                objectId.toHexString(),
                doc.getString("nome"),
                doc.getString(CAMPO_EMAIL),
                doc.getString("senhaHash"),
                roles,
                doc.getBoolean("ativo", true),
                doc.getBoolean("emailVerificado", false),
                doc.getInteger(CAMPO_FALHAS_LOGIN, 0),
                BsonDates.toInstant(doc.getDate(CAMPO_BLOQUEADO_ATE)),
                BsonDates.toInstant(doc.getDate(CAMPO_SENHA_ALTERADA_EM)),
                BsonDates.toInstant(doc.getDate(CAMPO_CRIADO_EM)),
                BsonDates.toInstant(doc.getDate(CAMPO_ATUALIZADO_EM)));
    }



    public static class EmailJaCadastradoException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public EmailJaCadastradoException(String message) {
            super(message);
        }
    }
}

