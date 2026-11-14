import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { AuthStorage, limparDadosLocaisDoUsuario, AUTH_CONTA_EXCLUIDA_EVENT } from '../shared/auth/authStorage';
import { AuthService } from '../shared/auth/authService';
import { ApiError } from '../shared/auth/api';
import {
  MENSAGEM_CONTA_EXCLUIDA,
  lerMensagemContaExcluida,
  limparParametroContaExcluida,
  redirecionarAposExclusaoDeConta
} from '../shared/auth/redirecionamentoConta';
import { __receberParaTeste, definirUsuarioCorrente } from '../shared/sync/barramento';
import * as apiModule from '../shared/auth/api';

const USER_A = '6af20000000000000000000a';
const USER_B = '6af20000000000000000000b';

function sessaoDe(usuarioId: string) {
  return {
    accessToken: 'jwt-em-memoria',
    tokenType: 'Bearer',
    expiresIn: 900,
    usuario: {
      id: usuarioId,
      nome: 'Aluno Teste',
      email: 'aluno@trilhara.com.br',
      papeis: ['USER'] as ('USER' | 'ADMIN')[],
      preferencias: {
        tema: 'dark' as const,
        disponibilidadeSemanalMinutos: [0, 0, 0, 0, 0, 0, 0],
        padraoLembreteMinutosAntes: 15
      },
      emailVerificado: true,
      ativo: true,
      criadoEm: '2026-11-09T00:00:00Z'
    }
  };
}

function comLocation(fake: Record<string, unknown>, acao: () => void): void {
  const original = window.location;
  Object.defineProperty(window, 'location', { configurable: true, value: fake });
  try {
    acao();
  } finally {
    Object.defineProperty(window, 'location', { configurable: true, value: original });
  }
}

describe('exclusão de conta: contrato do cliente, limpeza local e redirecionamento', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.restoreAllMocks();
    AuthStorage.__resetarParaTeste();
    definirUsuarioCorrente(null);
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it('envia Idempotency-Key no cabeçalho e não duplica a chave no corpo', async () => {
    AuthStorage.salvarSessao(sessaoDe(USER_A));
    const requestSpy = vi
      .spyOn(apiModule, 'request')
      .mockResolvedValue({ operationId: 'op-1', estado: 'PENDENTE' });

    await AuthService.excluirConta({
      senha: 'Senh@Forte2026!',
      confirmacao: 'EXCLUIR MINHA CONTA',
      idempotencyKey: 'idem-fixo'
    });

    expect(requestSpy).toHaveBeenCalledTimes(1);
    const [path, opcoes] = requestSpy.mock.calls[0];
    expect(path).toBe('/auth/conta');
    expect(opcoes?.method).toBe('DELETE');
    expect(opcoes?.semRefresh).toBe(true);
    expect((opcoes?.headers as Record<string, string>)['Idempotency-Key']).toBe('idem-fixo');

    const corpo = JSON.parse(String(opcoes?.body));
    expect(corpo).toEqual({ senha: 'Senh@Forte2026!', confirmacao: 'EXCLUIR MINHA CONTA' });
    expect(corpo.idempotencyKey).toBeUndefined();
  });

  it('gera uma chave de idempotência quando o chamador não informa', async () => {
    AuthStorage.salvarSessao(sessaoDe(USER_A));
    const requestSpy = vi
      .spyOn(apiModule, 'request')
      .mockResolvedValue({ operationId: 'op-2', estado: 'PENDENTE' });

    await AuthService.excluirConta({ senha: 'Senh@Forte2026!' });

    const [, opcoes] = requestSpy.mock.calls[0];
    const chave = (opcoes?.headers as Record<string, string>)['Idempotency-Key'];
    expect(chave).toBeTruthy();
    expect(chave.length).toBeGreaterThan(8);
  });

  it('limparDadosLocaisDoUsuario apaga trilhara:u:{id}:* de localStorage e sessionStorage preservando outra conta', () => {
    localStorage.setItem(`trilhara:u:${USER_A}:nota_draft:1`, '# A');
    localStorage.setItem(`trilhara:u:${USER_A}:pomodoro_foco`, '{"tipo":"TAREFA"}');
    sessionStorage.setItem(`trilhara:u:${USER_A}:filtro`, 'hoje');
    localStorage.setItem(`trilhara:u:${USER_B}:nota_draft:2`, '# B');
    sessionStorage.setItem(`trilhara:u:${USER_B}:filtro`, 'futuro');

    limparDadosLocaisDoUsuario(USER_A);

    expect(localStorage.getItem(`trilhara:u:${USER_A}:nota_draft:1`)).toBeNull();
    expect(localStorage.getItem(`trilhara:u:${USER_A}:pomodoro_foco`)).toBeNull();
    expect(sessionStorage.getItem(`trilhara:u:${USER_A}:filtro`)).toBeNull();
    expect(localStorage.getItem(`trilhara:u:${USER_B}:nota_draft:2`)).toBe('# B');
    expect(sessionStorage.getItem(`trilhara:u:${USER_B}:filtro`)).toBe('futuro');
  });

  it('dispara o evento local e publica CONTA_EXCLUIDA para as demais abas da origem', () => {
    AuthStorage.salvarSessao(sessaoDe(USER_A));
    const evento = vi.fn();
    window.addEventListener(AUTH_CONTA_EXCLUIDA_EVENT, evento);

    limparDadosLocaisDoUsuario(USER_A);

    expect(evento).toHaveBeenCalledTimes(1);
    expect((evento.mock.calls[0][0] as CustomEvent).detail).toEqual({ usuarioId: USER_A });
    window.removeEventListener(AUTH_CONTA_EXCLUIDA_EVENT, evento);
  });

  it('outra aba com a mesma conta recebe CONTA_EXCLUIDA e perde os dados locais', () => {
    localStorage.setItem(`trilhara:u:${USER_A}:nota_draft:9`, '# rascunho');
    definirUsuarioCorrente(USER_A);

    __receberParaTeste({
      v: 1,
      tipo: 'CONTA_EXCLUIDA',
      usuarioId: USER_A,
      origemAbaId: 'outra-aba',
      payload: null
    });

    expect(localStorage.getItem(`trilhara:u:${USER_A}:nota_draft:9`)).toBeNull();
  });

  it('redireciona para a tela pública sinalizando a exclusão em andamento', () => {
    const assign = vi.fn();
    comLocation(
      {
        href: 'https://estudos.trilhara.com.br/?pagina=hub',
        pathname: '/',
        search: '',
        hash: '',
        assign
      },
      () => redirecionarAposExclusaoDeConta()
    );

    expect(assign).toHaveBeenCalledTimes(1);
    expect(String(assign.mock.calls[0][0])).toContain('conta=excluida');
  });

  it('expõe a mensagem normativa somente quando o parâmetro está presente e o remove depois', () => {
    const replaceState = vi.fn();
    window.history.replaceState = replaceState;

    comLocation(
      {
        href: 'https://estudos.trilhara.com.br/?conta=excluida',
        pathname: '/',
        search: '?conta=excluida',
        hash: '',
        assign: vi.fn()
      },
      () => {
        expect(lerMensagemContaExcluida()).toBe(MENSAGEM_CONTA_EXCLUIDA);
        limparParametroContaExcluida();
      }
    );

    expect(replaceState).toHaveBeenCalledTimes(1);
    expect(String(replaceState.mock.calls[0][2])).not.toContain('conta=excluida');
  });

  it('sem o parâmetro, não há mensagem de conta excluída', () => {
    comLocation(
      {
        href: 'https://estudos.trilhara.com.br/',
        pathname: '/',
        search: '',
        hash: '',
        assign: vi.fn()
      },
      () => expect(lerMensagemContaExcluida()).toBeNull()
    );
  });

  it('401 no fluxo de exclusão chega como ApiError 401 (não como sessão expirada)', async () => {
    AuthStorage.salvarSessao(sessaoDe(USER_A));
    vi.spyOn(apiModule, 'request').mockRejectedValue(
      new ApiError({ status: 401, code: 'UNAUTHORIZED', detail: 'Senha atual incorreta.' })
    );

    await expect(AuthService.excluirConta({ senha: 'errada' })).rejects.toMatchObject({
      status: 401
    });
  });
});
