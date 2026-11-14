import { AuthResponse } from './types';
import { AuthStorage } from './authStorage';
import { CodigoErroApi, ehCodigoErro } from '../api';

export interface ApiErrorDetalhes {
  status: number;
  code: CodigoErroApi;
  detail: string;
  errors?: Record<string, string[]>;
  type?: string;
  title?: string;
  instance?: string;
  correlationId?: string;
  data?: unknown;
  /** Valor numérico de `Retry-After` quando o servidor informar (429). */
  retryAfterSeconds?: number;
}

/**
 * Erro de API já normalizado para o catálogo do produto.
 *
 * `code` está **sempre** presente: `NETWORK_ERROR` para falha de transporte
 * (`status: 0`) e `UNKNOWN` quando o backend responde algo fora do catálogo.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: CodigoErroApi;
  readonly detail: string;
  readonly errors?: Record<string, string[]>;
  readonly data?: unknown;
  readonly type?: string;
  readonly title?: string;
  readonly instance?: string;
  readonly correlationId?: string;
  /** Segundos informados em `Retry-After` (429), quando presentes. */
  readonly retryAfterSeconds?: number;

  constructor(detalhes: ApiErrorDetalhes) {
    super(detalhes.detail);
    this.name = 'ApiError';
    this.status = detalhes.status;
    this.code = detalhes.code;
    this.detail = detalhes.detail;
    this.data = detalhes.data;
    this.errors = detalhes.errors;
    this.type = detalhes.type;
    this.title = detalhes.title;
    this.instance = detalhes.instance;
    this.correlationId = detalhes.correlationId;
    this.retryAfterSeconds = detalhes.retryAfterSeconds;
  }
}

/** Normaliza o `code` recebido: fora do catálogo vira `UNKNOWN`. */
export function normalizarCodigo(code: unknown, status: number): CodigoErroApi {
  if (ehCodigoErro(code)) return code;
  if (status === 0) return 'NETWORK_ERROR';
  return 'UNKNOWN';
}

export function obterTimezoneNavegador(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'America/Sao_Paulo';
  } catch {
    return 'America/Sao_Paulo';
  }
}

export const AUTH_REQUIRED_EVENT = 'trilhara:auth-required';

const ROTAS_AUTH_PUBLICAS = new Set([
  '/auth/login',
  '/auth/logout',
  '/auth/cadastro',
  '/auth/refresh',
  '/auth/recuperar-senha',
  '/auth/resetar-senha',
  '/auth/handoff/consume'
]);

/** Opções adicionais do cliente HTTP que não pertencem a `RequestInit`. */
export interface OpcoesRequisicao extends RequestInit {
  /**
 * Não tenta renovar o token nem dispara `AUTH_REQUIRED_EVENT` no 401.
 *
 * Usado exclusivamente por `DELETE /api/auth/conta`: depois do 202 as credenciais já
 * foram revogadas, então o 401 significa "exclusão em andamento" — nunca "sessão expirada".
 */
  semRefresh?: boolean;
}

function parseRetryAfter(valor: string | null): number | undefined {
  if (!valor) return undefined;
  const segundos = Number.parseInt(valor, 10);
  return Number.isFinite(segundos) && segundos >= 0 ? segundos : undefined;
}

let refreshPromiseEmAndamento: Promise<AuthResponse> | null = null;

export async function tentarRenovarToken(): Promise<AuthResponse> {
  if (refreshPromiseEmAndamento) {
    return refreshPromiseEmAndamento;
  }

  refreshPromiseEmAndamento = (async () => {
    const headers = new Headers({
      'Content-Type': 'application/json',
      'X-Timezone': obterTimezoneNavegador()
    });

    const res = await fetch('/api/auth/refresh', {
      method: 'POST',
      credentials: 'include',
      headers,
      body: '{}'
    });

    if (!res.ok) {
      if (res.status === 400 || res.status === 401 || res.status === 403) {
        AuthStorage.limparSessao();
      }
      throw new ApiError({
        status: res.status,
        code: 'UNAUTHORIZED',
        detail: 'Sessão expirada. Faça login novamente.'
      });
    }

    const dados = (await res.json()) as AuthResponse;
    AuthStorage.salvarSessao(dados);
    return dados;
  })().finally(() => {
    refreshPromiseEmAndamento = null;
  });

  return refreshPromiseEmAndamento;
}

export async function request<T>(
  path: string,
  options: OpcoesRequisicao = {},
  tentativaAposRefresh = false
): Promise<T> {
  const url = path.startsWith('http') ? path : `/api${path}`;
  const pathNormalizado = path.startsWith('/api') ? path.slice(4) : path;
  const ehRotaAuthPublica = ROTAS_AUTH_PUBLICAS.has(pathNormalizado);

  const headers = new Headers(options.headers || {});
  if (!headers.has('Content-Type') && options.body && typeof options.body === 'string') {
    headers.set('Content-Type', 'application/json');
  }

  if (!headers.has('X-Timezone')) {
    headers.set('X-Timezone', obterTimezoneNavegador());
  }

  const accessToken = AuthStorage.obterAccessToken();
  if (accessToken && !headers.has('Authorization') && !ehRotaAuthPublica) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }

  try {
    const semRefresh = options.semRefresh === true;
    const { semRefresh: _ignorar, ...opcoesFetch } = options;
    const res = await fetch(url, {
      ...opcoesFetch,
      headers,
      credentials: options.credentials || 'include'
    });

    if (
      res.status === 401 &&
      !tentativaAposRefresh &&
      !ehRotaAuthPublica &&
      !semRefresh
    ) {
      let renovou = false;
      try {
        await tentarRenovarToken();
        renovou = true;
      } catch {
        // tentarRenovarToken já decide se a sessão deve ser limpa
      }
      if (renovou) {
        return request<T>(path, options, true);
      }
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent(AUTH_REQUIRED_EVENT));
      }
    }

    if (!res.ok) {
      let errorDetail = `Erro HTTP ${res.status}`;
      let errorCode: string | undefined;
      let errorErrors: Record<string, string[]> | undefined;
      let errorType: string | undefined;
      let errorTitle: string | undefined;
      let errorInstance: string | undefined;
      let errorData: unknown;

      try {
        errorData = await res.json();
        if (typeof errorData === 'object' && errorData !== null) {
          const fields = errorData as {
            detail?: unknown;
            title?: unknown;
            message?: unknown;
            error?: unknown;
            code?: unknown;
            errors?: unknown;
            type?: unknown;
            instance?: unknown;
          };
          const message = [fields.detail, fields.title, fields.message, fields.error]
            .find((value): value is string => typeof value === 'string' && value.length > 0);
          if (message) errorDetail = message;
          if (typeof fields.code === 'string') errorCode = fields.code;
          if (typeof fields.type === 'string') errorType = fields.type;
          if (typeof fields.title === 'string') errorTitle = fields.title;
          if (typeof fields.instance === 'string') errorInstance = fields.instance;
          if (fields.errors && typeof fields.errors === 'object' && !Array.isArray(fields.errors)) {
            errorErrors = fields.errors as Record<string, string[]>;
          }
        }
      } catch {
        errorData = undefined;
      }

      throw new ApiError({
        status: res.status,
        code: normalizarCodigo(errorCode, res.status),
        detail: errorDetail,
        errors: errorErrors,
        type: errorType,
        title: errorTitle,
        instance: errorInstance,
        correlationId: res.headers.get('X-Correlation-ID') ?? undefined,
        retryAfterSeconds: parseRetryAfter(res.headers.get('Retry-After')),
        data: errorData
      });
    }

    if (res.status === 204) {
      return {} as T;
    }

    return await res.json();
  } catch (err) {
    if (err instanceof ApiError) throw err;
    const msg = err instanceof Error ? err.message : 'Falha de comunicação com o servidor';
    throw new ApiError({
      status: 0,
      code: 'NETWORK_ERROR',
      detail: msg
    });
  }
}

