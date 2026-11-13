import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { request, ApiError, obterTimezoneNavegador } from '../shared/auth/api';
import type {
  ProblemDetailsResponse,
  ModoModalSessao,
  GlobalSessionModalOpenParams,
  RegistrarSessaoPayload,
  SessaoRegistradaDTO,
  BlocoHojeDTO,
  SyncStatusState
} from '../shared/contracts';

describe('Frontend API e Contratos Base (FASE 0)', () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it('smoke test: ambiente Vitest e jsdom funcional', () => {
    const div = document.createElement('div');
    div.textContent = 'Trilhara FASE 0';
    document.body.appendChild(div);
    expect(document.body.textContent).toContain('Trilhara FASE 0');
    document.body.removeChild(div);
  });

  it('deve incluir automaticamente o cabeçalho X-Timezone em todas as requisições de apiFetch', async () => {
    const timezoneDetectado = obterTimezoneNavegador();
    let cabecalhosEnviados: Headers | null = null;

    globalThis.fetch = vi.fn().mockImplementation(async (_url: string, init?: RequestInit) => {
      cabecalhosEnviados = new Headers(init?.headers);
      return new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      });
    });

    const resultado = await request<{ ok: boolean }>('/test-endpoint');
    expect(resultado).toEqual({ ok: true });
    expect(cabecalhosEnviados).not.toBeNull();
    expect(cabecalhosEnviados?.get('X-Timezone')).toBe(timezoneDetectado);
    expect(cabecalhosEnviados?.get('X-Timezone')).toBeTruthy();
  });

  it('deve mapear resposta RFC 9457 em ApiError com status, code, detail e errors', async () => {
    const problemResponse: ProblemDetailsResponse = {
      type: 'urn:trilhara:error:validation',
      title: 'Erro de validação',
      status: 400,
      detail: 'Um ou mais campos contêm valores inválidos.',
      instance: '/api/trilhas',
      code: 'VALIDATION_ERROR',
      errors: {
        nome: ['não pode ser vazio'],
        esfera: ['esfera inválida']
      }
    };

    globalThis.fetch = vi.fn().mockImplementation(async () => {
      return new Response(JSON.stringify(problemResponse), {
        status: 400,
        headers: { 'Content-Type': 'application/problem+json' }
      });
    });

    try {
      await request('/trilhas');
      expect.unreachable('Deveria ter lançado ApiError');
    } catch (err) {
      expect(err).toBeInstanceOf(ApiError);
      const apiError = err as ApiError;
      expect(apiError.status).toBe(400);
      expect(apiError.code).toBe('VALIDATION_ERROR');
      expect(apiError.detail).toBe('Um ou mais campos contêm valores inválidos.');
      expect(apiError.errors).toEqual({
        nome: ['não pode ser vazio'],
        esfera: ['esfera inválida']
      });
      expect(apiError.type).toBe('urn:trilhara:error:validation');
      expect(apiError.title).toBe('Erro de validação');
      expect(apiError.instance).toBe('/api/trilhas');
    }
  });

  it('deve suportar erro 409 Conflict estruturado (IDEMPOTENCY_KEY_CONFLICT)', async () => {
    const problemResponse: ProblemDetailsResponse = {
      type: 'urn:trilhara:error:conflict',
      title: 'Conflito de negócio',
      status: 409,
      detail: 'Chave de idempotência já utilizada com parâmetros divergentes.',
      instance: '/api/trilhas/123/sessoes',
      code: 'IDEMPOTENCY_KEY_CONFLICT'
    };

    globalThis.fetch = vi.fn().mockImplementation(async () => {
      return new Response(JSON.stringify(problemResponse), {
        status: 409,
        headers: { 'Content-Type': 'application/problem+json' }
      });
    });

    try {
      await request('/trilhas/123/sessoes');
      expect.unreachable('Deveria ter lançado ApiError');
    } catch (err) {
      expect(err).toBeInstanceOf(ApiError);
      const apiError = err as ApiError;
      expect(apiError.status).toBe(409);
      expect(apiError.code).toBe('IDEMPOTENCY_KEY_CONFLICT');
      expect(apiError.detail).toBe('Chave de idempotência já utilizada com parâmetros divergentes.');
      expect(apiError.instance).toBe('/api/trilhas/123/sessoes');
    }
  });

  it('deve validar tipagens dos contratos compartilhados congelados', () => {
    const modo: ModoModalSessao = 'BLOCO_TIMER';
    const params: GlobalSessionModalOpenParams = {
      modo,
      trilhaId: 'trilha-1',
      blocoId: 'bloco-1',
      duracaoInicialSegundos: 1800
    };
    expect(params.modo).toBe('BLOCO_TIMER');

    const payload: RegistrarSessaoPayload = {
      disciplinaId: 'disc-1',
      dataSessao: '2026-11-09',
      duracaoSegundos: 3600,
      questoes: 20,
      acertos: 18,
      chaveIdempotencia: 'idemp-123'
    };
    expect(payload.duracaoSegundos).toBe(3600);

    const sessao: SessaoRegistradaDTO = {
      id: 'sess-1',
      trilhaId: 'trilha-1',
      disciplinaId: 'disc-1',
      duracaoSegundos: 3600,
      dataSessao: '2026-11-09',
      criadoEm: '2026-11-09T14:00:00Z',
      disciplinaRemovida: false,
      editavel: true
    };
    expect(sessao.editavel).toBe(true);

    const bloco: BlocoHojeDTO = {
      blocoId: 'bloco-1',
      trilhaId: 'trilha-1',
      cronogramaId: 'crono-1',
      disciplinaId: 'disc-1',
      disciplinaNome: 'Direito Constitucional',
      disciplinaCor: '#3B82F6',
      data: '2026-11-09',
      ordem: 1,
      duracaoMinutos: 60,
      concluido: false
    };
    expect(bloco.concluido).toBe(false);

    const statusSync: SyncStatusState = 'salvo';
    expect(statusSync).toBe('salvo');
  });
});
