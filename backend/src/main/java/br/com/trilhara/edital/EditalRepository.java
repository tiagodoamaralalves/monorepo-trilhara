package br.com.trilhara.edital;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.or;
import static com.mongodb.client.model.Filters.regex;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EditalRepository {

    private static final String CAMPO_STATUS = "status";
    private static final String CAMPO_BANCA = "banca";
    private static final String CAMPO_ORGAO = "orgao";
    private static final String CAMPO_CARGO = "cargo";

    private final MongoCollection<Document> editais;
    private final ObjectMapper mapper;

    public EditalRepository(
            MongoClient client,
            ObjectMapper mapper,
            @ConfigProperty(name = "quarkus.mongodb.database") String database) {
        this.editais = client.getDatabase(database).getCollection("editais");
        this.mapper = mapper;
    }

    public void salvar(Edital edital) {
        Document documento = paraDocumento(edital);
        documento.remove("id");
        documento.put("_id", new ObjectId(edital.id()));
        editais.replaceOne(eq("_id", new ObjectId(edital.id())), documento, new ReplaceOptions().upsert(true));
    }

    public Optional<Edital> buscar(String id) {
        if (!ObjectId.isValid(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(editais.find(eq("_id", new ObjectId(id))).first()).map(this::paraEdital);
    }

    public boolean excluir(String id) {
        return ObjectId.isValid(id)
                && editais.deleteOne(eq("_id", new ObjectId(id))).getDeletedCount() > 0;
    }

    public Pagina<Edital> listarAdmin(StatusEdital status, String banca, String orgao, String cargo, int page, int size) {
        List<Bson> filtros = new ArrayList<>();
        if (status != null) {
            filtros.add(eq(CAMPO_STATUS, status.name()));
        }
        adicionarFiltroTexto(filtros, CAMPO_BANCA, banca);
        adicionarFiltroTexto(filtros, CAMPO_ORGAO, orgao);
        adicionarFiltroTexto(filtros, CAMPO_CARGO, cargo);
        return listar(filtros, page, size);
    }

    public Pagina<Edital> listarPublicos(FiltroPublico filtro) {
        List<Bson> filtros = new ArrayList<>();
        filtros.add(eq(CAMPO_STATUS, StatusEdital.PUBLICADA.name()));
        FiltroEditalPublico criterios = filtro.criterios();
        if (filtro.esfera() != null) {
            filtros.add(eq("esfera", filtro.esfera().name()));
        }
        adicionarFiltroTexto(filtros, "uf", criterios.uf());
        adicionarFiltroTexto(filtros, CAMPO_BANCA, criterios.banca());
        adicionarFiltroTexto(filtros, "area", criterios.area());
        adicionarFiltroTexto(filtros, CAMPO_CARGO, criterios.cargo());
        if (criterios.busca() != null && !criterios.busca().isBlank()) {
            String termo = Pattern.quote(criterios.busca().trim());
            filtros.add(or(
                    regex("nome", termo, "i"),
                    regex("area", termo, "i"),
                    regex(CAMPO_CARGO, termo, "i"),
                    regex(CAMPO_BANCA, termo, "i")));
        }
        if (Boolean.TRUE.equals(criterios.emAndamento())) {
            filtros.add(gt("dataProva", LocalDate.now().toString()));
        }
        return listar(filtros, filtro.paginacao().page(), filtro.paginacao().size());
    }

    private Pagina<Edital> listar(List<Bson> filtros, int page, int size) {
        Bson filtro = filtros.isEmpty() ? new Document() : and(filtros);
        long total = editais.countDocuments(filtro);
        List<Edital> itens = new ArrayList<>();
        editais.find(filtro)
                .sort(Sorts.descending("atualizadoEm"))
                .skip(page * size)
                .limit(size)
                .forEach(documento -> itens.add(paraEdital(documento)));
        return new Pagina<>(itens, page, size, total);
    }

    private void adicionarFiltroTexto(List<Bson> filtros, String campo, String valor) {
        if (valor != null && !valor.isBlank()) {
            filtros.add(regex(campo, Pattern.quote(valor.trim()), "i"));
        }
    }

    private Document paraDocumento(Edital edital) {
        try {
            return Document.parse(mapper.writeValueAsString(edital));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível serializar o edital.", exception);
        }
    }

    private Edital paraEdital(Document documento) {
        ObjectId id = documento.getObjectId("_id");
        documento.remove("_id");
        documento.put("id", id.toHexString());
        try {
            return mapper.readValue(documento.toJson(), Edital.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Documento de edital inválido: " + id.toHexString(), exception);
        }
    }



    public record FiltroPublico(
            Esfera esfera,
            FiltroEditalPublico criterios,
            Paginacao paginacao) {
    }
}
