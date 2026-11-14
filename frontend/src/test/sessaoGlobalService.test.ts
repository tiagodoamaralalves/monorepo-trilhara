import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { sessaoGlobalService } from '../shared/services/sessaoGlobalService';
import * as apiModule from '../shared/auth/api';
import { AUTH_REQUIRED_EVENT } from '../shared/auth/api';
import type { RegistrarSessaoPayload } from '../shared/contracts/domainContracts';

const payload: RegistrarSessaoPayload = {
  disciplinaId: '6af2000000000000000000d1',
  dataSessao: '2026-11-12',
  duracaoSegundos: 1537,
  questoes: 20,
  acertos: 17
};

const chave = 'idemp-chave-r6';

describe('sessaoGlobalService — canal único de registro de sessão', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('envia header Idempotency-Key e body com a mesma chave', async () => {
    const requestSpy = vi
      .spyOn(apiModule, 'request')
      .mockResolvedValueOnce({ id: 'sess-1', duracaoSegundos: 1537 });

    await sessaoGlobalService.registrarSessao('6af2000000000000000000a1', payload, chave);

    expect(requestSpy).toHaveBeenCalledTimes(1);
    const [caminho, opcoes] = requestSpy.mock.calls[0];
    expect(caminho).toBe('/trilhas/6af2000000000000000000a1/sessoes');
    const headers = (opcoes as RequestInit).headers as Record<string, string>;
    expect(headers['Idempotency-Key']).toBe(chave);
    expect(JSON.parse(String((opcoes as RequestInit).body))).toMatchObject({
      ...payload,
      chaveIdempotencia: chave
    });
  });

  it('executa o fluxo de duas etapas concluindo sessão e bloco', async () => {
    const requestSpy = vi
      .spyOn(apiModule, 'request')
      .mockResolvedValueOnce({ id: 'sess-2' })
      .mockResolvedValueOnce({ id: 'bloco-1', concluido: true });

    const resultado = await sessaoGlobalService.executarFluxo({
      trilhaId: '6af2000000000000000000a1',
      blocoId: '6af2000000000000000000b1',
      payload,
      chave
    });

    expect(resultado).toEqual({
      sessaoSalva: true,
      sessaoId: 'sess-2',
      blocoConcluido: true
    });
    expect(requestSpy).toHaveBeenCalledTimes(2);
    expect(requestSpy.mock.calls[1][0]).toBe('/blocos/6af2000000000000000000b1/conclusao');
    expect(JSON.parse(String((requestSpy.mock.calls[1][1] as RequestInit).body))).toEqual({
      concluido: true
    });
  });

  it('falha na etapa 2 permite retry apenas do PATCH sem reenviar o POST', async () => {
    const requestSpy = vi
      .spyOn(apiModule, 'request')
      .mockResolvedValueOnce({ id: 'sess-3' })
      .mockRejectedValueOnce(new Error('rede ao concluir'));

    const primeira = await sessaoGlobalService.executarFluxo({
      trilhaId: '6af2000000000000000000a1',
      blocoId: '6af2000000000000000000b1',
      payload,
      chave
    });

    expect(primeira.sessaoSalva).toBe(true);
    expect(primeira.blocoConcluido).toBe(false);
    expect(primeira.erro).toBeInstanceOf(Error);

    requestSpy.mockResolvedValueOnce({ id: 'bloco-1', concluido: true });

    const retry = await sessaoGlobalService.executarFluxo({
      trilhaId: '6af2000000000000000000a1',
      blocoId: '6af2000000000000000000b1',
      payload,
      chave,
      sessaoSalva: { id: primeira.sessaoId! }
    });

    expect(retry.blocoConcluido).toBe(true);
    const posts = requestSpy.mock.calls.filter(([caminho]) =>
      String(caminho).endsWith('/sessoes')
    );
    expect(posts).toHaveLength(1);
  });

  it('401 dispara trilhara:auth-required e o retry reutiliza a mesma chave após login', async () => {
    const requestSpy = vi
      .spyOn(apiModule, 'request')
      .mockRejectedValueOnce(
        new apiModule.ApiError({ status: 401, code: 'UNAUTHORIZED', detail: 'Sessão expirada.' })
      )
      .mockResolvedValueOnce({ id: 'sess-4' });

    const ouvinte = vi.fn();
    window.addEventListener(AUTH_REQUIRED_EVENT, ouvinte);

    await expect(
      sessaoGlobalService.registrarSessao('6af2000000000000000000a1', payload, chave)
    ).rejects.toBeInstanceOf(apiModule.ApiError);

    // O modal é quem publica o evento a partir da categoria do erro; aqui simulamos o fluxo.
    window.dispatchEvent(new CustomEvent(AUTH_REQUIRED_EVENT));
    expect(ouvinte).toHaveBeenCalledTimes(1);

    const sessao = await sessaoGlobalService.registrarSessao(
      '6af2000000000000000000a1',
      payload,
      chave
    );
    expect(sessao.id).toBe('sess-4');

    const chavesEnviadas = requestSpy.mock.calls.map(
      ([, opcoes]) => ((opcoes as RequestInit).headers as Record<string, string>)['Idempotency-Key']
    );
    expect(chavesEnviadas).toEqual([chave, chave]);

    window.removeEventListener(AUTH_REQUIRED_EVENT, ouvinte);
  });

  it('concluirBloco envia PATCH concluido true e reabrirBloco envia false', async () => {
    const requestSpy = vi
      .spyOn(apiModule, 'request')
      .mockResolvedValue({ id: 'bloco-9', concluido: true });

    await sessaoGlobalService.concluirBloco('6af2000000000000000000b1');
    await sessaoGlobalService.reabrirBloco('6af2000000000000000000b1');

    expect(requestSpy.mock.calls[0][0]).toBe('/blocos/6af2000000000000000000b1/conclusao');
    expect(JSON.parse(String((requestSpy.mock.calls[0][1] as RequestInit).body))).toEqual({
      concluido: true
    });
    expect(JSON.parse(String((requestSpy.mock.calls[1][1] as RequestInit).body))).toEqual({
      concluido: false
    });
  });
});
