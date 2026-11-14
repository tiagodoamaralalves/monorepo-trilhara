package br.com.trilhara.shared.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoClient;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * teste-guarda do inventário de coleções.
 *
 * <p>Falha se existir coleção no banco sem declaração no enum {@link ColecaoTrilhara},
 * exceto a allowlist temporária de resíduos cujo dono é outra revisão. Também garante que
 * toda coleção declarada tenha índices canônicos.</p>
 */
@QuarkusTest
@DisplayName("Inventário de coleções")
class InventarioColecoesTest {

    /**
 * Nenhuma coleção fora do inventário é tolerada. A allowlist temporária de resíduos de
 * foi removida junto com `operacoes_concluir_atrasados` e `planejamento_ciclo`
 *; `handoff_tokens` deixou de ser criada .
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
                "Coleções fora do inventário: " + naoDeclaradas
                        + " — declare no enum ColecaoTrilhara ou remova a criação");
    }

    @Test
    @DisplayName("todas as 20 coleções declaradas possuem índices canônicos")
    void todasAsColecoesDeclaradasPossuemIndices() {
        for (ColecaoTrilhara colecao : ColecaoTrilhara.values()) {
            assertFalse(IndicesCanonicos.porColecao(colecao).isEmpty(),
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
        assertNull(ColecaoTrilhara.porNome("handoff_tokens"),
                "handoff_tokens foi substituída por tokens_handoff_sso");
        assertNotNull(ColecaoTrilhara.porNome("tokens_handoff_sso"));
        assertNotNull(ColecaoTrilhara.porNome("editais"));
    }

    @Test
    @DisplayName("exclusão de conta cobre 18 filhas + usuarios, sem repetição")
    void exclusaoDeContaCobreTodoOInventarioPrivado() {
        List<String> filhas = ColecaoTrilhara.nomesFilhasPrivadas();
        assertEquals(18, filhas.size());
        assertEquals(filhas.size(), filhas.stream().distinct().count());
        assertFalse(filhas.contains(ColecaoTrilhara.USUARIOS.nome()),
                "usuarios é removida por último, fora da lista de filhas");
        assertFalse(filhas.contains(ColecaoTrilhara.EDITAIS.nome()),
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
