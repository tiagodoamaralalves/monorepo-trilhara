/**
 * Barramento único de eventos entre abas (D-21).
 *
 * Regras normativas:
 * - existe **um** `BroadcastChannel("trilhara")` para todas as mensagens internas;
 * - quando `BroadcastChannel` não existe (ex.: jsdom), o transporte cai para o evento
 *   `storage` na chave `trilhara:bus` — funcionalmente equivalente para abas da mesma origem;
 * - o envelope é versionado (`v: 1`) e sempre carrega `usuarioId` e `origemAbaId`;
 * - mensagens de outra aba **da própria origem** são ignoradas;
 * - mensagens cujo `usuarioId` difere do usuário em memória são **descartadas** (isolamento
 *   por conta, §8), inclusive quando a mensagem é anônima (`usuarioId: null`).
 *
 * Nenhum módulo deve criar um canal próprio: timers, Agenda Hoje e autenticação publicam aqui.
 */

export type TipoMensagemBarramento =
  | 'TIMER_ESTUDOS_ATUALIZADO'
  | 'TIMER_POMODORO_ATUALIZADO'
  | 'AGENDA_INVALIDADA'
  | 'SESSAO_ENCERRADA'
  | 'CONTA_EXCLUIDA';

export interface MensagemBarramento<T = unknown> {
  v: 1;
  tipo: TipoMensagemBarramento;
  usuarioId: string | null;
  origemAbaId: string;
  payload: T;
}

/** Nome do canal único de `BroadcastChannel`. */
export const NOME_CANAL_BARRAMENTO = 'trilhara';

/** Chave usada pelo fallback por evento `storage`. */
export const CHAVE_BARRAMENTO_STORAGE = 'trilhara:bus';

type Ouvinte = (mensagem: MensagemBarramento) => void;

const TIPOS_CONHECIDOS: ReadonlySet<string> = new Set<TipoMensagemBarramento>([
  'TIMER_ESTUDOS_ATUALIZADO',
  'TIMER_POMODORO_ATUALIZADO',
  'AGENDA_INVALIDADA',
  'SESSAO_ENCERRADA',
  'CONTA_EXCLUIDA'
]);

const ouvintes = new Map<TipoMensagemBarramento, Set<Ouvinte>>();
const origemAbaId = criarIdOrigem();
let usuarioCorrente: string | null = null;
let canal: BroadcastChannel | null = null;
let listenersGlobaisInstalados = false;

function criarIdOrigem(): string {
  try {
    return globalThis.crypto.randomUUID();
  } catch {
    return `aba-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }
}

/** Identificador desta aba; mensagens com o mesmo valor são ignoradas no recebimento. */
export function obterOrigemAbaId(): string {
  return origemAbaId;
}

/**
 * Declara o usuário em memória desta aba. Chamado pela autenticação e pelos providers que
 * dependem de isolamento por conta; é a referência usada para descartar mensagens alheias.
 */
export function definirUsuarioCorrente(usuarioId: string | null): void {
  usuarioCorrente = usuarioId ?? null;
}

/** Usuário em memória usado no filtro de mensagens recebidas. */
export function obterUsuarioCorrente(): string | null {
  return usuarioCorrente;
}

function transporteDisponivel(): boolean {
  return typeof window !== 'undefined' && 'BroadcastChannel' in window;
}

function obterCanal(): BroadcastChannel | null {
  if (!transporteDisponivel()) return null;
  if (canal) return canal;
  try {
    canal = new BroadcastChannel(NOME_CANAL_BARRAMENTO);
    canal.onmessage = (evento) => entregar(evento.data as MensagemBarramento);
  } catch {
    canal = null;
  }
  return canal;
}

function janelaDisponivel(): Window | null {
  return typeof window === 'undefined' ? null : window;
}

function instalarListenersGlobais(): void {
  if (listenersGlobaisInstalados) return;
  const janela = janelaDisponivel();
  if (!janela) return;
  listenersGlobaisInstalados = true;

  if (transporteDisponivel()) {
    obterCanal();
    return;
  }

  // Fallback: outra aba gravou a mesma chave; `storage` não dispara na aba originadora.
  janela.addEventListener('storage', (evento: StorageEvent) => {
    if (evento.key !== CHAVE_BARRAMENTO_STORAGE || !evento.newValue) return;
    try {
      entregar(JSON.parse(evento.newValue) as MensagemBarramento);
    } catch {
      // Conteúdo inválido na chave do barramento é ignorado.
    }
  });
}

function entregar(mensagem: MensagemBarramento | null | undefined): void {
  if (!mensagem || mensagem.v !== 1) return;
  if (!TIPOS_CONHECIDOS.has(mensagem.tipo)) return;
  if (mensagem.origemAbaId === origemAbaId) return;
  if ((mensagem.usuarioId ?? null) !== usuarioCorrente) return;

  const inscritos = ouvintes.get(mensagem.tipo);
  if (!inscritos) return;

  inscritos.forEach((ouvinte) => {
    try {
      ouvinte(mensagem);
    } catch {
      // Um ouvinte com defeito não pode derrubar o barramento.
    }
  });
}

/**
 * Publica uma mensagem tipada para as demais abas da mesma origem.
 *
 * @param usuarioId sobrescreve o usuário em memória (necessário em `CONTA_EXCLUIDA`, quando a
 *   sessão já foi limpa, mas as demais abas ainda precisam reconhecer a conta removida).
 */
export function publicar<T>(
  tipo: TipoMensagemBarramento,
  payload: T = null as T,
  usuarioId?: string | null
): void {
  const mensagem: MensagemBarramento<T> = {
    v: 1,
    tipo,
    usuarioId: usuarioId === undefined ? usuarioCorrente : usuarioId,
    origemAbaId,
    payload
  };

  instalarListenersGlobais();

  const canalAtivo = obterCanal();
  if (canalAtivo) {
    try {
      canalAtivo.postMessage(mensagem);
    } catch {
      // postMessage indisponível: a mensagem é local e o estado já foi persistido.
    }
    return;
  }

  const janela = janelaDisponivel();
  if (!janela) return;
  try {
    janela.localStorage.setItem(CHAVE_BARRAMENTO_STORAGE, JSON.stringify(mensagem));
  } catch {
    // Storage restrito não impede o fluxo local.
  }
}

/** Assina um tipo de mensagem; retorna a função de cancelamento. */
export function assinar(tipo: TipoMensagemBarramento, ouvinte: Ouvinte): () => void {
  instalarListenersGlobais();

  const inscritos = ouvintes.get(tipo) ?? new Set<Ouvinte>();
  inscritos.add(ouvinte);
  ouvintes.set(tipo, inscritos);

  return () => {
    inscritos.delete(ouvinte);
    if (inscritos.size === 0) ouvintes.delete(tipo);
  };
}

/** Apenas para testes: limpa inscrições e o usuário em memória. */
export function __resetarParaTeste(): void {
  ouvintes.clear();
  usuarioCorrente = null;
  if (canal) {
    try {
      canal.close();
    } catch {
      // Canal já fechado.
    }
    canal = null;
  }
}

/**
 * Apenas para testes: injeta uma mensagem como se tivesse chegado de outra aba.
 *
 * O envio local é descartado por design (mesma `origemAbaId`), e no jsdom não existe
 * `BroadcastChannel` para simular outra aba — este seam permite exercitar o filtro.
 */
export function __receberParaTeste(mensagem: MensagemBarramento): void {
  entregar({ ...mensagem, origemAbaId: mensagem.origemAbaId || 'aba-remota' });
}
