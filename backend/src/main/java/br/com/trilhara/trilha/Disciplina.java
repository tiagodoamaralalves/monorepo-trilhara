package br.com.trilhara.trilha;

import java.time.Instant;
import java.util.List;

import org.bson.types.ObjectId;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Entidade normalizada da coleção 'disciplinas'.
 * Conforme REQUISITOS-REESTRUTURACAO-DADOS.md §4.1 e PLANO-IMPLEMENTACAO-REESTRUTURACAO.md §2.2.
 *
 * <p>O campo {@code cadernoLink} foi removido (DC-04): vínculo entre disciplina e notas é
 * derivado de {@code cadernos.origemTrilhaId}, nunca de uma URL guardada na disciplina. O limite
 * de 500 tópicos pertence ao serviço ({@code 409 RESOURCE_LIMIT_EXCEEDED}), não ao BSON.</p>
 */
public record Disciplina(
        ObjectId id,
        ObjectId usuarioId,
        ObjectId trilhaId,
        @NotBlank @Size(max = Disciplina.MAX_NOME, message = "O nome da disciplina deve ter no máximo 120 caracteres.") String nome,
        @Size(max = 32, message = "A cor da disciplina deve ter no máximo 32 caracteres.") String cor,
        @Positive @Min(value = 1, message = "O peso mínimo da disciplina é 1.") @Max(value = 5, message = "O peso máximo da disciplina é 5.") Integer peso,
        @NotNull Integer ordem,
        @NotNull List<@Valid TopicoEmbutido> topicos,
        Instant criadoEm,
        Instant atualizadoEm) {

    public static final int MAX_NOME = 120;
    public static final int MAX_TOPICOS = 500;

    public Disciplina {
        peso = peso == null ? 1 : Math.max(1, Math.min(5, peso));
        ordem = ordem == null ? 0 : Math.max(0, ordem);
        topicos = topicos == null ? List.of() : List.copyOf(topicos);
    }

    public String idHex() {
        return id != null ? id.toHexString() : null;
    }

    public String trilhaIdHex() {
        return trilhaId != null ? trilhaId.toHexString() : null;
    }

    public String usuarioIdHex() {
        return usuarioId != null ? usuarioId.toHexString() : null;
    }
}
