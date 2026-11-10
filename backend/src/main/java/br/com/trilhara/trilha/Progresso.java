package br.com.trilhara.trilha;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

public record Progresso(@NotNull Boolean done, Instant doneAt) {
}
