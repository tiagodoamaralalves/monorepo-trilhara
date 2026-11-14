import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { DEBOUNCE_AUTOSAVE_MS, useAutosaveNota } from '../modules/notas/hooks/useAutosaveNota';
import { NotaService } from '../modules/notas/services/notaService';
import { unsavedChangesRegistry } from '../shared/state/unsavedChangesRegistry';

const USUARIO_ID = 'usuario-1';
const NOTA_ID = 'nota-1';

function chaveRascunho(notaId: string) {
  return `trilhara:u:${USUARIO_ID}:nota_draft:${notaId}`;
}

function montarHook(overrides: Partial<Parameters<typeof useAutosaveNota>[0]> = {}) {
  const salvar = vi.fn().mockResolvedValue({ atualizadoEm: '2026-01-01T00:00:02Z' });
  const props = {
    usuarioId: USUARIO_ID,
    notaId: NOTA_ID,
    tituloInicial: 'Título',
    conteudoInicial: 'conteúdo inicial',
    atualizadoEmServidor: '2026-01-01T00:00:00Z',
    salvar,
    ...overrides
  };
  const hook = renderHook((p) => useAutosaveNota(p), { initialProps: props });
  return { ...hook, salvar, props };
}

describe('useAutosaveNota', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();
    unsavedChangesRegistry.__resetarParaTeste();
  });

  afterEach(() => {
    vi.useRealTimers();
    unsavedChangesRegistry.__resetarParaTeste();
  });

  it('debounce de 800 ms: nada é enviado antes do intervalo', async () => {
    const { result, salvar } = montarHook();

    act(() => {
      result.current.alterarConteudo('novo conteúdo');
    });

    expect(result.current.status).toBe('alteracoes-locais');
    await act(async () => {
      vi.advanceTimersByTime(DEBOUNCE_AUTOSAVE_MS - 1);
    });
    expect(salvar).not.toHaveBeenCalled();

    await act(async () => {
      vi.advanceTimersByTime(1);
      await Promise.resolve();
    });
    expect(salvar).toHaveBeenCalledTimes(1);
    expect(salvar).toHaveBeenCalledWith(
      NOTA_ID,
      'Título',
      'novo conteúdo',
      expect.any(AbortSignal)
    );
  });

  it('grava rascunho completo com baseAtualizadoEmServidor ao alterar', () => {
    const { result } = montarHook();

    act(() => {
      result.current.alterarTitulo('Título novo');
    });

    const rascunho = JSON.parse(localStorage.getItem(chaveRascunho(NOTA_ID))!);
    expect(rascunho.titulo).toBe('Título novo');
    expect(rascunho.conteudoMarkdown).toBe('conteúdo inicial');
    expect(rascunho.baseAtualizadoEmServidor).toBe('2026-01-01T00:00:00Z');
    expect(typeof rascunho.atualizadoEmLocal).toBe('string');
  });

  it('apaga o rascunho somente após 200 com conteúdo igual ao enviado', async () => {
    const { result } = montarHook();

    act(() => {
      result.current.alterarConteudo('conteúdo salvo');
    });
    expect(localStorage.getItem(chaveRascunho(NOTA_ID))).not.toBeNull();

    await act(async () => {
      vi.advanceTimersByTime(DEBOUNCE_AUTOSAVE_MS);
      await Promise.resolve();
    });
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(result.current.status).toBe('salvo');
    expect(localStorage.getItem(chaveRascunho(NOTA_ID))).toBeNull();
  });

  it('falha de rede mantém rascunho e estado "erro"; retry reenvia', async () => {
    const salvar = vi
      .fn()
      .mockRejectedValueOnce(new Error('rede'))
      .mockResolvedValueOnce({ atualizadoEm: '2026-01-01T00:00:05Z' });
    const { result } = montarHook({ salvar });

    act(() => {
      result.current.alterarConteudo('digitado offline');
    });
    await act(async () => {
      vi.advanceTimersByTime(DEBOUNCE_AUTOSAVE_MS);
      await Promise.resolve();
    });

    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(result.current.status).toBe('erro');
    expect(localStorage.getItem(chaveRascunho(NOTA_ID))).not.toBeNull();

    await act(async () => {
      result.current.tentarNovamente();
      await Promise.resolve();
    });
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(result.current.status).toBe('salvo');
  });

  it('digitação durante o voo mantém o rascunho e volta a alteracoes-locais', async () => {
    let resolver: (valor: { atualizadoEm: string }) => void = () => undefined;
    const salvar = vi.fn().mockImplementation(
      () =>
        new Promise<{ atualizadoEm: string }>((resolve) => {
          resolver = resolve;
        })
    );
    const { result } = montarHook({ salvar });

    act(() => {
      result.current.alterarConteudo('primeira versão');
    });
    await act(async () => {
      vi.advanceTimersByTime(DEBOUNCE_AUTOSAVE_MS);
      await Promise.resolve();
    });

    act(() => {
      result.current.alterarConteudo('segunda versão');
    });

    await act(async () => {
      resolver({ atualizadoEm: '2026-01-01T00:00:03Z' });
      await Promise.resolve();
    });

    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(result.current.status).toBe('alteracoes-locais');
    const rascunho = JSON.parse(localStorage.getItem(chaveRascunho(NOTA_ID))!);
    expect(rascunho.conteudoMarkdown).toBe('segunda versão');
  });

  it('resposta obsoleta de outra página é ignorada', async () => {
    let resolverAntigo: (valor: { atualizadoEm: string }) => void = () => undefined;
    const salvar = vi.fn().mockImplementationOnce(
      () =>
        new Promise<{ atualizadoEm: string }>((resolve) => {
          resolverAntigo = resolve;
        })
    );
    const { result, rerender, props } = montarHook({ salvar });

    act(() => {
      result.current.alterarConteudo('conteúdo da nota 1');
    });
    await act(async () => {
      vi.advanceTimersByTime(DEBOUNCE_AUTOSAVE_MS);
      await Promise.resolve();
    });

    rerender({ ...props, notaId: 'nota-2', tituloInicial: 'Outra', conteudoInicial: 'outro' });

    await act(async () => {
      resolverAntigo({ atualizadoEm: '2030-01-01T00:00:00Z' });
      await Promise.resolve();
    });

    expect(result.current.titulo).toBe('Outra');
    expect(result.current.conteudoMarkdown).toBe('outro');
    expect(result.current.status).toBe('salvo');
  });

  it('troca de página grava o rascunho antes de abortar o PUT pendente', async () => {
    const sinais: AbortSignal[] = [];
    const salvar = vi.fn().mockImplementation(
      (_id: string, _t: string, _m: string, signal: AbortSignal) => {
        sinais.push(signal);
        return new Promise<{ atualizadoEm: string }>(() => undefined);
      }
    );
    const { result, rerender, props } = montarHook({ salvar });

    act(() => {
      result.current.alterarConteudo('conteúdo pendente');
    });
    await act(async () => {
      vi.advanceTimersByTime(DEBOUNCE_AUTOSAVE_MS);
      await Promise.resolve();
    });
    expect(sinais).toHaveLength(1);
    expect(sinais[0].aborted).toBe(false);

    rerender({ ...props, notaId: 'nota-2', tituloInicial: 'Outra', conteudoInicial: 'outro' });

    expect(sinais[0].aborted).toBe(true);
    const rascunho = JSON.parse(localStorage.getItem(chaveRascunho(NOTA_ID))!);
    expect(rascunho.conteudoMarkdown).toBe('conteúdo pendente');
  });

  it('flush imediato em visibilitychange: hidden', async () => {
    const { result, salvar } = montarHook();

    act(() => {
      result.current.alterarConteudo('conteúdo antes de ocultar');
    });

    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      get: () => 'hidden'
    });
    act(() => {
      document.dispatchEvent(new Event('visibilitychange'));
    });
    await act(async () => {
      await Promise.resolve();
    });

    expect(salvar).toHaveBeenCalledTimes(1);
  });

  it('registra alterações não salvas enquanto o estado é sujo', async () => {
    const { result } = montarHook();

    expect(unsavedChangesRegistry.temAlteracoes()).toBe(false);

    act(() => {
      result.current.alterarConteudo('sujo');
    });
    await act(async () => {
      await Promise.resolve();
    });
    expect(unsavedChangesRegistry.temAlteracoes()).toBe(true);
    expect(unsavedChangesRegistry.listar().map((i) => i.id)).toContain(`nota:${NOTA_ID}`);

    await act(async () => {
      vi.advanceTimersByTime(DEBOUNCE_AUTOSAVE_MS);
      await Promise.resolve();
    });
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(unsavedChangesRegistry.temAlteracoes()).toBe(false);
  });

  it('não toca o localStorage quando não há usuário identificado', () => {
    const { result } = montarHook({ usuarioId: '' });

    act(() => {
      result.current.alterarConteudo('sem usuário');
    });

    expect(localStorage.getItem(chaveRascunho(NOTA_ID))).toBeNull();
  });

  it('grava e remove rascunhos sob a chave canônica por usuário', () => {
    NotaService.salvarRascunhoLocal(USUARIO_ID, NOTA_ID, {
      titulo: 'R',
      conteudoMarkdown: 'C',
      atualizadoEmLocal: '2026-01-01T00:00:00Z'
    });
    expect(JSON.parse(localStorage.getItem(chaveRascunho(NOTA_ID))!).titulo).toBe('R');

    NotaService.removerRascunhoLocal(USUARIO_ID, NOTA_ID);
    expect(localStorage.getItem(chaveRascunho(NOTA_ID))).toBeNull();
  });
});
