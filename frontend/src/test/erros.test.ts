import { describe, it, expect } from 'vitest';
import { classificarErro, ehCodigoErro, extrairErrosDeCampo, erroDoCampo, CODIGOS_ERRO } from '../shared/api';
import { ApiError, normalizarCodigo } from '../shared/auth/api';

describe('R1 — classificação de erros (D-37)', () => {
  it('classifica falha de transporte como rede', () => {
    expect(classificarErro(new ApiError({ status: 0, code: 'NETWORK_ERROR', detail: 'sem rede' }))).toBe('rede');
  });

  it('classifica 401 como autenticacao', () => {
    expect(classificarErro(new ApiError({ status: 401, code: 'UNAUTHORIZED', detail: 'x' }))).toBe('autenticacao');
  });

  it('classifica 404 e TRASH_ITEM_EXPIRED como nao-encontrado', () => {
    expect(classificarErro(new ApiError({ status: 404, code: 'NOT_FOUND', detail: 'x' }))).toBe('nao-encontrado');
    expect(classificarErro(new ApiError({ status: 400, code: 'TRASH_ITEM_EXPIRED', detail: 'x' }))).toBe(
      'nao-encontrado'
    );
  });

  it('classifica 409 como conflito', () => {
    expect(classificarErro(new ApiError({ status: 409, code: 'LATEST_SESSION_ONLY', detail: 'x' }))).toBe('conflito');
  });

  it('classifica validação e limite', () => {
    expect(classificarErro(new ApiError({ status: 400, code: 'VALIDATION_ERROR', detail: 'x' }))).toBe('validacao');
    expect(classificarErro(new ApiError({ status: 400, code: 'INVALID_IDENTIFIER', detail: 'x' }))).toBe('validacao');
    expect(classificarErro(new ApiError({ status: 409, code: 'RESOURCE_LIMIT_EXCEEDED', detail: 'x' }))).toBe('limite');
    expect(classificarErro(new ApiError({ status: 429, code: 'RATE_LIMITED', detail: 'x' }))).toBe('limite');
  });

  it('classifica 5xx e desconhecidos como servidor', () => {
    expect(classificarErro(new ApiError({ status: 500, code: 'INTERNAL_ERROR', detail: 'x' }))).toBe('servidor');
    expect(classificarErro(new ApiError({ status: 503, code: 'SERVICE_UNAVAILABLE', detail: 'x' }))).toBe('servidor');
    expect(classificarErro(null)).toBe('rede');
    expect(classificarErro(new Error('qualquer'))).toBe('rede');
  });
});

describe('R1 — catálogo de códigos no cliente', () => {
  it('reconhece somente códigos do catálogo fechado', () => {
    expect(ehCodigoErro('VALIDATION_ERROR')).toBe(true);
    expect(ehCodigoErro('BAD_REQUEST')).toBe(false);
    expect(ehCodigoErro('PAYLOAD_TOO_LARGE')).toBe(false);
    expect(ehCodigoErro(42)).toBe(false);
  });

  it('não contém códigos removidos por D-04', () => {
    expect(CODIGOS_ERRO).not.toContain('PAYLOAD_TOO_LARGE');
    expect(CODIGOS_ERRO).not.toContain('SCHEDULE_LIMIT_EXCEEDED');
    expect(CODIGOS_ERRO).not.toContain('BAD_REQUEST');
  });

  it('normaliza código desconhecido para UNKNOWN (ou NETWORK_ERROR em status 0)', () => {
    expect(normalizarCodigo('CODIGO_LEGADO', 400)).toBe('UNKNOWN');
    expect(normalizarCodigo(undefined, 0)).toBe('NETWORK_ERROR');
    expect(normalizarCodigo('NOT_FOUND', 404)).toBe('NOT_FOUND');
  });
});

describe('R1 — extração de erros de campo', () => {
  it('extrai errors por campo de um ProblemDetails', () => {
    const erro = new ApiError({
      status: 400,
      code: 'VALIDATION_ERROR',
      detail: 'Um ou mais campos contêm valores inválidos.',
      errors: { nome: ['não pode ser vazio'], email: ['formato inválido'] }
    });

    expect(extrairErrosDeCampo(erro)).toEqual({
      nome: ['não pode ser vazio'],
      email: ['formato inválido']
    });
    expect(erroDoCampo(erro, 'nome')).toBe('não pode ser vazio');
    expect(erroDoCampo(erro, 'inexistente')).toBeUndefined();
  });

  it('retorna objeto vazio quando não há errors', () => {
    expect(extrairErrosDeCampo(new Error('x'))).toEqual({});
    expect(extrairErrosDeCampo(undefined)).toEqual({});
  });
});
