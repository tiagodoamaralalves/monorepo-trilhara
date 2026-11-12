package br.com.trilhara.shared.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoClient;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * R2 — teste-guarda do inventário de coleções (D-08).
 *
 * <p>Falha se existir coleção no banco sem declaração no enum {@link ColecaoTrilhara},
 * exceto a allowlist temporária de resíduos cujo dono é outra revisão. Também garante que
 * toda coleção declarada tenha índices canônicos.</p>
 */
@QuarkusTest
@DisplayName("R2 - Inventário de coleções (D-08)")
class InventarioColecoesTest {

    /**
     * Nenhuma coleção fora do inventário é tolerada. A allowlist temporária de resíduos de
     * R5 foi removida junto com `operacoes_concluir_atrasados` (D-17) e `planejamento_ciclo`
     * (D-16/DC-15); `handoff_tokens` deixou de ser criada na R2.
     */
    private static final Set<String> ALLOWLIST_LEGADO = Set.of();

    @Inject
    MongoClient mongoClient;

    @Inject
    MongoIndexInitializer indexInitializer;

    @Test
    @DisplayName("nenhuma coleção fora do inventário é criada pela aplicação")
    void naoDeveExistirColecaoForaDoInventario() {
        List<String> naoDeclaradas = new ArrayList<>();
        for (String nome : mongoClient.getDatabase(bancoDeTeste()).listCollectionNames()) {
            if (ColecaoTrilhara.porNome(nome) == null && !ALLOWLIST_LEGADO.contains(nome)) {
                naoDeclaradas.add(nome);
            }
        }
        assertTrue(naoDeclaradas.isEmpty(),
                "Coleções fora do inventário (D-08): " + naoDeclaradas
                        + " — declare no enum ColecaoTrilhara ou remova a criação");
    }

    @Test
    @DisplayName("todas as 20 coleções declaradas possuem índices canônicos")
    void todasAsColecoesDeclaradasPossuemIndices() {
        for (ColecaoTrilhara colecao : ColecaoTrilhara.values()) {
            assertTrue(!IndicesCanonicos.porColecao(colecao).isEmpty(),
                    "Coleção sem índice declarado: " + colecao.nome());
        }
        assertEquals(20, ColecaoTrilhara.values().length);
        assertEquals(18, ColecaoTrilhara.filhasPrivadas().size());
    }

    @Test
    @DisplayName("inventário não contém a coleção legada de handoff nem nomes duplicados")
    void inventarioNaoDuplicaNomes() {
        assertEquals(ColecaoTrilhara.values().length,
                ColecaoTrilhara.nomes().stream().distinct().count());
        assertTrue(ColecaoTrilhara.porNome("handoff_tokens") == null,
                "handoff_tokens foi substituída por tokens_handoff_sso");
        assertTrue(ColecaoTrilhara.porNome("tokens_handoff_sso") != null);
        assertTrue(ColecaoTrilhara.porNome("editais") != null);
    }

    @Test
    @DisplayName("exclusão de conta cobre 18 filhas + usuarios, sem repetição")
    void exclusaoDeContaCobreTodoOInventarioPrivado() {
        List<String> filhas = ColecaoTrilhara.nomesFilhasPrivadas();
        assertEquals(18, filhas.size());
        assertEquals(filhas.size(), filhas.stream().distinct().count());
        assertTrue(!filhas.contains(ColecaoTrilhara.USUARIOS.nome()),
                "usuarios é removida por último, fora da lista de filhas");
        assertTrue(!filhas.contains(ColecaoTrilhara.EDITAIS.nome()),
                "editais é catálogo público e não pode ser purgado");
        assertEquals(ColecaoTrilhara.values().length - 2, filhas.size());
    }

    @Test
    @DisplayName("tabela canônica não contém índices duplicados por coleção")
    void tabelaCanonicaNaoTemDuplicidade() {
        Set<String> vistos = new java.util.HashSet<>();
        for (IndicesCanonicos.IndiceCanonico indice : IndicesCanonicos.todos()) {
            String chave = indice.colecao().nome() + "." + indice.nome();
            assertTrue(vistos.add(chave), "Índice duplicado na tabela canônica: " + chave);
        }
        assertEquals(20, IndicesCanonicos.todos().stream()
                .map(i -> i.colecao().nome()).distinct().count());
    }

    private String bancoDeTeste() {
        return "trilhara_test";
    }
}
