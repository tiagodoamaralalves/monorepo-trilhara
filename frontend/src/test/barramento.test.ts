import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  NOME_CANAL_BARRAMENTO,
  CHAVE_BARRAMENTO_STORAGE,
  assinar,
  definirUsuarioCorrente,
  obterOrigemAbaId,
  obterUsuarioCorrente,
  publicar,
  __receberParaTeste,
  __resetarParaTeste
} from '../shared/sync/barramento';

describe('barramento — canal único e isolamento por conta', () => {
  beforeEach(() => {
    localStorage.clear();
    __resetarParaTeste();
  });

  afterEach(() => {
    localStorage.clear();
    __resetarParaTeste();
  });

  it('usa um único canal nomeado "trilhara"', () => {
    expect(NOME_CANAL_BARRAMENTO).toBe('trilhara');
    expect(CHAVE_BARRAMENTO_STORAGE).toBe('trilhara:bus');
  });

  it('cada aba tem um identificador estável de origem', () => {
    const id = obterOrigemAbaId();
    expect(id).toBeTruthy();
    expect(obterOrigemAbaId()).toBe(id);
  });

  it('entrega mensagem v:1 ao ouvinte do tipo inscrito', () => {
    definirUsuarioCorrente('user-1');
    const ouvinte = vi.fn();
    const cancelar = assinar('AGENDA_INVALIDADA', ouvinte);

    __receberParaTeste({
      v: 1,
      tipo: 'AGENDA_INVALIDADA',
      usuarioId: 'user-1',
      origemAbaId: 'outra-aba',
      payload: null
    });

    expect(ouvinte).toHaveBeenCalledTimes(1);
    cancelar();
  });

  it('descarta mensagem de outro usuarioId', () => {
    definirUsuarioCorrente('user-1');
    const ouvinte = vi.fn();
    assinar('TIMER_ESTUDOS_ATUALIZADO', ouvinte);

    __receberParaTeste({
      v: 1,
      tipo: 'TIMER_ESTUDOS_ATUALIZADO',
      usuarioId: 'user-2',
      origemAbaId: 'outra-aba',
      payload: {}
    });

    expect(ouvinte).not.toHaveBeenCalled();
  });

  it('descarta mensagem anônima quando o usuário em memória é autenticado', () => {
    definirUsuarioCorrente('user-1');
    const ouvinte = vi.fn();
    assinar('TIMER_POMODORO_ATUALIZADO', ouvinte);

    __receberParaTeste({
      v: 1,
      tipo: 'TIMER_POMODORO_ATUALIZADO',
      usuarioId: null,
      origemAbaId: 'outra-aba',
      payload: {}
    });

    expect(ouvinte).not.toHaveBeenCalled();
  });

  it('descarta envelope com versão diferente de 1', () => {
    definirUsuarioCorrente('user-1');
    const ouvinte = vi.fn();
    assinar('SESSAO_ENCERRADA', ouvinte);

    __receberParaTeste({
      v: 2 as never,
      tipo: 'SESSAO_ENCERRADA',
      usuarioId: 'user-1',
      origemAbaId: 'outra-aba',
      payload: null
    });

    expect(ouvinte).not.toHaveBeenCalled();
  });

  it('publicar usa o fallback storage quando não há BroadcastChannel', () => {
    // O ambiente de teste pode expor BroadcastChannel; forçamos o transporte de fallback.
    const original = (window as unknown as { BroadcastChannel?: unknown }).BroadcastChannel;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    delete (window as any).BroadcastChannel;
    __resetarParaTeste();

    try {
      definirUsuarioCorrente('user-1');
      publicar('AGENDA_INVALIDADA', null);

      const gravado = localStorage.getItem(CHAVE_BARRAMENTO_STORAGE);
      expect(gravado).not.toBeNull();
      expect(JSON.parse(String(gravado))).toMatchObject({
        v: 1,
        tipo: 'AGENDA_INVALIDADA',
        usuarioId: 'user-1'
      });
    } finally {
      if (original) {
        (window as unknown as { BroadcastChannel?: unknown }).BroadcastChannel = original;
      }
    }
  });

  it('publicar permite usuarioId explícito (conta excluída)', () => {
    const original = (window as unknown as { BroadcastChannel?: unknown }).BroadcastChannel;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    delete (window as any).BroadcastChannel;
    __resetarParaTeste();

    try {
      definirUsuarioCorrente(null);
      publicar('CONTA_EXCLUIDA', null, 'user-9');

      const gravado = JSON.parse(String(localStorage.getItem(CHAVE_BARRAMENTO_STORAGE)));
      expect(gravado.usuarioId).toBe('user-9');
      expect(obterUsuarioCorrente()).toBeNull();
    } finally {
      if (original) {
        (window as unknown as { BroadcastChannel?: unknown }).BroadcastChannel = original;
      }
    }
  });
});
