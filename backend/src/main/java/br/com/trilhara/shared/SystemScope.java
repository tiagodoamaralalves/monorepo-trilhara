package br.com.trilhara.shared;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca uma classe que opera <strong>fora</strong> do escopo de um único tenant.
 *
 * <p>Acesso multi-tenant — purgas, schedulers, exclusão de conta, reconciliadores — só pode
 * existir em classes anotadas com {@code @SystemScope}, sem exposição HTTP. Classes anotadas
 * não estendem {@code *Resource} e não dependem do cabeçalho {@code X-Timezone} da requisição.</p>
 *
 * <p>Primeira materialização ({@code SessaoPurgaService}), permitindo que o
 * {@code TenantIsolationArchTest} deixe de ignorar a regra de escopo de sistema.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SystemScope {
}
