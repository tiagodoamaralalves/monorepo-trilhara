package br.com.trilhara.auth;

import static com.mongodb.client.model.Filters.eq;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
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

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserRepository {

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
        return Optional.ofNullable(usuarios.find(eq("email", emailNormalizado)).first())
                .map(this::paraUsuario);
    }

    public boolean existePorEmail(String emailNormalizado) {
        if (emailNormalizado == null || emailNormalizado.isBlank()) {
            return false;
        }
        return usuarios.countDocuments(eq("email", emailNormalizado)) > 0;
    }

    public List<Usuario> listar(int pagina, int tamanho) {
        List<Usuario> lista = new ArrayList<>();
        usuarios.find().sort(Sorts.descending("criadoEm"))
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
                Updates.combine(Updates.inc("falhasLoginConsecutivas", 1),
                        Updates.set("atualizadoEm", Date.from(agora))),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)))
                .map(this::paraUsuario);
    }

    public void bloquearAte(String id, Instant ate) {
        if (id == null || !ObjectId.isValid(id)) {
            return;
        }
        usuarios.updateOne(eq("_id", new ObjectId(id)), Updates.set("bloqueadoAte", Date.from(ate)));
    }

    public void atualizarRoles(String id, List<Role> roles) {
        if (id == null || !ObjectId.isValid(id)) {
            return;
        }
        usuarios.updateOne(eq("_id", new ObjectId(id)), Updates.combine(
                Updates.set("roles", roles.stream().map(Role::name).toList()),
                Updates.set("atualizadoEm", Date.from(Instant.now()))));
    }

    public List<Integer> obterDisponibilidadeSemanal(ObjectId usuarioId) {
        Document doc = usuarios.find(eq("_id", usuarioId)).first();
        if (doc == null) {
            return List.of(0, 0, 0, 0, 0, 0, 0);
        }
        Document pref = doc.get("preferencias", Document.class);
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
                        Updates.set("atualizadoEm", Date.from(Instant.now()))
                )
        );
    }

    public int obterPadraoLembreteMinutos(ObjectId usuarioId) {
        Document doc = usuarios.find(eq("_id", usuarioId)).first();
        if (doc == null) {
            return 15;
        }
        Document pref = doc.get("preferencias", Document.class);
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
                        Updates.set("atualizadoEm", Date.from(Instant.now()))
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
        doc.put("email", u.email());
        doc.put("senhaHash", u.senhaHash());
        doc.put("roles", u.roles().stream().map(Role::name).toList());
        doc.put("ativo", u.ativo());
        doc.put("emailVerificado", u.emailVerificado());
        doc.put("falhasLoginConsecutivas", u.falhasLoginConsecutivas());
        doc.put("bloqueadoAte", u.bloqueadoAte() != null ? Date.from(u.bloqueadoAte()) : null);
        doc.put("senhaAlteradaEm", u.senhaAlteradaEm() != null ? Date.from(u.senhaAlteradaEm()) : null);
        doc.put("criadoEm", u.criadoEm() != null ? Date.from(u.criadoEm()) : Date.from(Instant.now()));
        doc.put("atualizadoEm", u.atualizadoEm() != null ? Date.from(u.atualizadoEm()) : Date.from(Instant.now()));
        return doc;
    }

    private Usuario paraUsuario(Document doc) {
        ObjectId objectId = doc.getObjectId("_id");
        List<String> rolesRaw = doc.getList("roles", String.class, List.of(Role.USER.name()));
        List<Role> roles = rolesRaw.stream()
                .map(r -> {
                    try {
                        return Role.valueOf(r);
                    } catch (IllegalArgumentException e) {
                        return Role.USER;
                    }
                })
                .distinct()
                .toList();

        return new Usuario(
                objectId.toHexString(),
                doc.getString("nome"),
                doc.getString("email"),
                doc.getString("senhaHash"),
                roles,
                doc.getBoolean("ativo", true),
                doc.getBoolean("emailVerificado", false),
                doc.getInteger("falhasLoginConsecutivas", 0),
                paraInstant(doc.getDate("bloqueadoAte")),
                paraInstant(doc.getDate("senhaAlteradaEm")),
                paraInstant(doc.getDate("criadoEm")),
                paraInstant(doc.getDate("atualizadoEm")));
    }

    private static Instant paraInstant(Date data) {
        return data != null ? data.toInstant() : null;
    }



    public static class EmailJaCadastradoException extends RuntimeException {
        public EmailJaCadastradoException(String message) {
            super(message);
        }
    }
}

