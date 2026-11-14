/**
 * Catálogo fechado de códigos de erro do Trilhara no cliente.
 *
 * <p>União literal idêntica ao enum `CodigoErro` do backend. Novos códigos só entram
 * aqui quando entrarem lá — o cliente decide por `status` + `code`.</p>
 */
export const CODIGOS_ERRO = [
  'VALIDATION_ERROR',
  'INVALID_IDENTIFIER',
  'INVALID_PAGINATION',
  'INVALID_SEARCH_QUERY',
  'INVALID_LOCAL_DATETIME',
  'IDEMPOTENCY_KEY_REQUIRED',
  'IDEMPOTENCY_KEY_MISMATCH',
  'UNAUTHORIZED',
  'FORBIDDEN',
  'NOT_FOUND',
  'TRASH_ITEM_EXPIRED',
  'RESOURCE_LIMIT_EXCEEDED',
  'DUPLICATE_MODALITY',
  'SCHEDULE_GENERATION_IN_PROGRESS',
  'IDEMPOTENCY_KEY_CONFLICT',
  'IDEMPOTENCY_RESULT_REMOVED',
  'TRAIL_ALREADY_IMPORTED',
  'DUPLICATE_RESOURCE',
  'RATE_LIMITED',
  'INTERNAL_ERROR',
  'SERVICE_UNAVAILABLE'
] as const;

export type CodigoErro = (typeof CODIGOS_ERRO)[number];

/** Códigos exclusivos do transporte: não vêm do backend. */
type CodigoErroTransporte = 'NETWORK_ERROR' | 'UNKNOWN';

export type CodigoErroApi = CodigoErro | CodigoErroTransporte;

const CONJUNTO_CODIGOS = new Set<string>(CODIGOS_ERRO);

export function ehCodigoErro(valor: unknown): valor is CodigoErro {
  return typeof valor === 'string' && CONJUNTO_CODIGOS.has(valor);
}

/** Categorias de estado de interface. */
export type CategoriaErro =
  | 'rede'
  | 'autenticacao'
  | 'nao-encontrado'
  | 'conflito'
  | 'validacao'
  | 'limite'
  | 'servidor';

interface ErroClassificavel {
  status?: number;
  code?: string;
}

/**
 * Classifica qualquer erro em uma categoria de UI.
 *
 * <p>Nunca lança: entradas desconhecidas caem em `servidor`. É a única fonte de decisão
 * de estado de erro usada pelos componentes.</p>
 */
export function classificarErro(erro: unknown): CategoriaErro {
  const candidato = erro as ErroClassificavel | null;
  const status = typeof candidato?.status === 'number' ? candidato.status : 0;
  const code = typeof candidato?.code === 'string' ? candidato.code : undefined;

  if (code === 'NETWORK_ERROR' || status === 0) return 'rede';
  if (status === 401 || code === 'UNAUTHORIZED') return 'autenticacao';
  if (status === 403 || code === 'FORBIDDEN') return 'limite';
  if (code === 'RESOURCE_LIMIT_EXCEEDED' || code === 'RATE_LIMITED') return 'limite';
  if (status === 404 || code === 'NOT_FOUND' || code === 'TRASH_ITEM_EXPIRED') return 'nao-encontrado';
  if (status === 400 || code === 'VALIDATION_ERROR' || code === 'INVALID_IDENTIFIER') return 'validacao';
  if (status === 409) return 'conflito';
  return 'servidor';
}

/** Extrai `errors` (campo → mensagens) para ligar a `FieldError` via `aria-describedby`. */
export function extrairErrosDeCampo(erro: unknown): Record<string, string[]> {
  const candidato = erro as { errors?: unknown } | null;
  const errors = candidato?.errors;
  if (!errors || typeof errors !== 'object' || Array.isArray(errors)) return {};

  const resultado: Record<string, string[]> = {};
  for (const [campo, mensagens] of Object.entries(errors as Record<string, unknown>)) {
    if (Array.isArray(mensagens)) {
      resultado[campo] = mensagens.filter((m): m is string => typeof m === 'string');
    } else if (typeof mensagens === 'string') {
      resultado[campo] = [mensagens];
    }
  }
  return resultado;
}

/** Primeira mensagem de erro de um campo, se houver. */
export function erroDoCampo(erro: unknown, campo: string): string | undefined {
  return extrairErrosDeCampo(erro)[campo]?.[0];
}
