# Contrato de Erros — Trilhara (RFC 9457 + catálogo fechado D-04)

> Gerado a partir do enum `br.com.trilhara.shared.erro.CodigoErro` (R1). **Fonte única de
> verdade:** qualquer código novo entra no enum (backend) e na união literal
> `CodigoErro` em `frontend/src/shared/api/erros.ts` no mesmo change.

Todo erro do produto é `Content-Type: application/problem+json` e **somente** erro usa esse
media type. Corpo:

```json
{
  "type": "urn:trilhara:problem:validation-error",
  "title": "Erro de validação",
  "status": 400,
  "detail": "Um ou mais campos contêm valores inválidos.",
  "instance": "/api/trilhas/507f1f77bcf86cd799439011",
  "code": "VALIDATION_ERROR",
  "errors": { "nome": ["não pode ser vazio"] }
}
```

## Regras transversais

| Regra | Comportamento |
|---|---|
| `type` | `urn:trilhara:problem:<código em kebab-case>` — estável por código |
| `instance` | path com barra inicial e **sem** query string (nunca `null`) |
| `code` | sempre presente e sempre do catálogo; é o **único** identificador de máquina |
| `detail` | pt-BR, localizável, sem stack trace, SQL, consulta ou valor enviado pelo cliente |
| `errors` | presente em todo `VALIDATION_ERROR`/`INVALID_IDENTIFIER`, com uma mensagem por campo |
| `X-Correlation-ID` | presente em **toda** resposta (ecoa o valor recebido ou gera um UUID); exposto via `Access-Control-Expose-Headers` |
| 401 | sem autenticação, sessão revogada, conta em exclusão |
| 403 | operação administrativa sem papel exigido |
| 404 | inexistente **ou** de outro tenant (nunca revela existência) |
| 400 × 404 | ID fora do formato hexadecimal de 24 caracteres é `400 INVALID_IDENTIFIER`; ID válido e inexistente é `404 NOT_FOUND` |

## Catálogo

| HTTP | `code` | `type` | Uso |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `urn:trilhara:problem:validation-error` | Bean Validation, propriedade desconhecida, Markdown > 512 KB (`errors.conteudoMarkdown`), datas/duração/questões inválidas |
| 400 | `INVALID_IDENTIFIER` | `urn:trilhara:problem:invalid-identifier` | ID malformado em path/query/body (D-03) |
| 400 | `INVALID_PAGINATION` | `urn:trilhara:problem:invalid-pagination` | `pagina < 1` ou `tamanho` fora do limite do endpoint |
| 400 | `INVALID_SEARCH_QUERY` | `urn:trilhara:problem:invalid-search-query` | Busca de Notas sem termo significativo |
| 400 | `INVALID_LOCAL_DATETIME` | `urn:trilhara:problem:invalid-local-datetime` | Horário local inexistente (gap de DST) |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | `urn:trilhara:problem:idempotency-key-required` | Falta `Idempotency-Key` onde é obrigatória |
| 400 | `IDEMPOTENCY_KEY_MISMATCH` | `urn:trilhara:problem:idempotency-key-mismatch` | Header diverge do body |
| 401 | `UNAUTHORIZED` | `urn:trilhara:problem:unauthorized` | Sem autenticação/sessão revogada/conta em exclusão |
| 403 | `FORBIDDEN` | `urn:trilhara:problem:forbidden` | Operação administrativa sem papel |
| 404 | `NOT_FOUND` | `urn:trilhara:problem:not-found` | Inexistente ou de outro tenant |
| 404 | `TRASH_ITEM_EXPIRED` | `urn:trilhara:problem:trash-item-expired` | Restauração fora do prazo de 30 dias |
| 409 | `RESOURCE_LIMIT_EXCEEDED` | `urn:trilhara:problem:resource-limit-exceeded` | 50 trilhas, 100 disciplinas, 500 tópicos, 3 cronogramas, limites de blocos |
| 409 | `DUPLICATE_MODALITY` | `urn:trilhara:problem:duplicate-modality` | Cronogramas |
| 409 | `SCHEDULE_GENERATION_IN_PROGRESS` | `urn:trilhara:problem:schedule-generation-in-progress` | Lease ocupado na trilha / operação concorrente |
| 409 | `LATEST_SESSION_ONLY` | `urn:trilhara:problem:latest-session-only` | Edição de sessão não mais recente |
| 409 | `IDEMPOTENCY_KEY_CONFLICT` | `urn:trilhara:problem:idempotency-key-conflict` | Mesma chave, payload funcional diferente |
| 409 | `IDEMPOTENCY_RESULT_REMOVED` | `urn:trilhara:problem:idempotency-result-removed` | Retry após exclusão (tombstone) |
| 409 | `TRAIL_ALREADY_IMPORTED` | `urn:trilhara:problem:trail-already-imported` | Importação sem `reimportar=true` (D-29) |
| 409 | `DUPLICATE_RESOURCE` | `urn:trilhara:problem:duplicate-resource` | `E11000` não mapeado especificamente; e-mail já cadastrado |
| 429 | `RATE_LIMITED` | `urn:trilhara:problem:rate-limited` | Com header `Retry-After` |
| 500 | `INTERNAL_ERROR` | `urn:trilhara:problem:internal-error` | Genérico, `detail` neutro, `instance` + `X-Correlation-ID` |
| 503 | `SERVICE_UNAVAILABLE` | `urn:trilhara:problem:service-unavailable` | MongoDB indisponível, timeout de servidor |

## Códigos removidos (não reutilizar)

`BAD_REQUEST` (virou `VALIDATION_ERROR`), `CONFLICT` (default genérico), `PAYLOAD_TOO_LARGE`,
`SCHEDULE_LIMIT_EXCEEDED`, `EMAIL_JA_CADASTRADO` (virou `DUPLICATE_RESOURCE`),
`HTTP_<status>` e o status **422**.

## Uso no cliente

```ts
import { classificarErro, extrairErrosDeCampo } from '@shared/api';

try { await salvar(); } catch (e) {
  switch (classificarErro(e)) {
    case 'rede':      /* SectionErrorCard: "Verifique sua conexão e tente novamente." */ break;
    case 'autenticacao': /* AuthModal — nunca SectionErrorCard */ break;
    case 'validacao': /* FieldError por campo via extrairErrosDeCampo(e) */ break;
    case 'conflito':  /* Toast com detail + recarregar a seção */ break;
    case 'limite':    /* Toast informando o limite atingido */ break;
    default:          /* SectionErrorCard com "Código: {correlationId curto}" */
  }
}
```
