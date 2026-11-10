import { AuthResponse } from './types';
import { AuthStorage } from './authStorage';

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly detail: string;
  readonly errors?: Record<string, string[]>;
  readonly data?: unknown;
  readonly type?: string;
  readonly title?: string;
  readonly instance?: string;

  constructor(
    message: string,
    status: number,
    data?: unknown,
    code?: string,
    errors?: Record<string, string[]>,
    type?: string,
    title?: string,
    instance?: string
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.detail = message;
    this.data = data;
    this.code = code;
    this.errors = errors;
    this.type = type;
    this.title = title;
    this.instance = instance;
  }
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
      throw new ApiError('Sessão expirada. Faça login novamente.', res.status, undefined, 'SESSION_EXPIRED');
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
  options: RequestInit = {},
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
    const res = await fetch(url, {
      ...options,
      headers,
      credentials: options.credentials || 'include'
    });

    if (
      res.status === 401 &&
      !tentativaAposRefresh &&
      !ehRotaAuthPublica
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

      throw new ApiError(
        errorDetail,
        res.status,
        errorData,
        errorCode,
        errorErrors,
        errorType,
        errorTitle,
        errorInstance
      );
    }

    if (res.status === 204) {
      return {} as T;
    }

    return await res.json();
  } catch (err) {
    if (err instanceof ApiError) throw err;
    const msg = err instanceof Error ? err.message : 'Falha de comunicação com o servidor';
    throw new ApiError(msg, 0, undefined, 'NETWORK_ERROR');
  }
}

export const apiFetch = request;
