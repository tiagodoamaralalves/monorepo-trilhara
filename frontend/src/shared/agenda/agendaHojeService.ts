import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { request } from '../auth/api';
import { useAuth } from '../auth';
import { registrarFalha } from '../diagnostico';
import { hojeISO } from '../format';
import { assinar, publicar } from '../sync/barramento';
import { BlocoHojeDTO, TarefaDTO, TarefasHojeDTO } from '../contracts/domainContracts';
import { agruparAtrasadasPorSerie, GrupoAtrasadas } from './agruparAtrasadasPorSerie';
import {
  compararAtrasadas,
  compararBlocosHoje,
  compararSemData,
  compararTarefasHoje,
  ordenar
} from './ordenarAgenda';

/** Ciclo de vida de cada fonte da agenda (D-37). */
type StatusFonte = 'carregando' | 'sucesso' | 'erro';

interface FonteEstado<T> {
  status: StatusFonte;
  /** Última resposta 2xx; `undefined` quando nunca houve sucesso. */
  dados?: T;
  erro?: Error | null;
}

/**
 * Estado da Visão Hoje de **um** usuário (D-46).
 *
 * Nunca existe estado compartilhado entre contas: o manager guarda um mapa
 * `usuarioId -> EstadoAgendaUsuario` e apaga a fatia no logout, na exclusão da conta e quando
 * a conta em memória muda.
 */
export interface EstadoAgendaUsuario {
  status: StatusFonte;
  hojeLocal?: string;
  tarefas: FonteEstado<TarefasHojeDTO>;
  blocos: FonteEstado<BlocoHojeDTO[]>;
}

/** Estrutura pronta para renderização: três seções + estados por fonte. */
export interface AgendaHojeDerivado {
  hojeLocal: string;
  status: StatusFonte;
  statusTarefas: StatusFonte;
  statusBlocos: StatusFonte;
  atrasadas: TarefaDTO[];
  atrasadasAgrupadas: GrupoAtrasadas[];
  blocosHoje: BlocoHojeDTO[];
  tarefasHoje: TarefaDTO[];
  semData: TarefaDTO[];
  erroTarefas: Error | null;
  erroBlocos: Error | null;
  /** `true` apenas quando as duas fontes responderam 2xx e não há nenhum item. */
  vazioTotal: boolean;
}

type Ouvinte = (estado: EstadoAgendaUsuario | null) => void;

interface Inscricao {
  usuarioId: string;
  ouvinte: Ouvinte;
}

const THROTTLE_MS = 5000;

function estadoInicial(): EstadoAgendaUsuario {
  return {
    status: 'carregando',
    tarefas: { status: 'carregando' },
    blocos: { status: 'carregando' }
  };
}

function comoErro(motivo: unknown, padrao: string): Error {
  return motivo instanceof Error ? motivo : new Error(String(motivo || padrao));
}

function statusAgregado(
  tarefas: FonteEstado<unknown>,
  blocos: FonteEstado<unknown>
): StatusFonte {
  if (tarefas.status === 'erro' && blocos.status === 'erro') return 'erro';
  if (tarefas.status === 'carregando' && blocos.status === 'carregando') return 'carregando';
  return 'sucesso';
}

/**
 * Agregador da Visão Hoje (§9): combina `GET /api/tarefas/hoje` e `GET /api/blocos/hoje`
 * em um único estado, sem mover lógica de domínio para uma coleção agregadora.
 *
 * Regras implementadas:
 * - estado indexado por `usuarioId`, zerado em `trilhara:logout`, `trilhara:conta-excluida`
 *   e na troca de conta (D-46);
 * - cada fonte tem estado próprio (`carregando`/`sucesso`/`erro`) para que a falha de uma
 *   não substitua a lista da outra por vazio;
 * - `focus`/`visibilitychange` recarregam com *throttle* de 5 s;
 * - `AGENDA_INVALIDADA` (barramento único, D-21) invalida as fontes em todas as abas;
 * - virada de `hojeLocal` descarta os itens do dia anterior e recarrega uma única vez.
 */
class AgendaHojeManager {
  private estados = new Map<string, EstadoAgendaUsuario>();
  private epocas = new Map<string, number>();
  private ultimoCarregamento = new Map<string, number>();
  private cargasEmAndamento = new Map<string, Promise<EstadoAgendaUsuario>>();
  private inscricoes = new Set<Inscricao>();
  private listenersInstalados = false;

  constructor() {
    this.instalarListenersGlobais();
  }

  public obterEstado(usuarioId: string): EstadoAgendaUsuario | null {
    return this.estados.get(usuarioId) ?? null;
  }

  public subscrever(usuarioId: string, ouvinte: Ouvinte): () => void {
    const inscricao: Inscricao = { usuarioId, ouvinte };
    this.inscricoes.add(inscricao);

    const atual = this.estados.get(usuarioId);
    if (atual) ouvinte(atual);

    // Só dispara rede quando não há estado do dia para esta conta: Tarefas e Pomodoro
    // podem montar juntos sem rajada de requisições.
    if (!atual || atual.status === 'erro' || !this.estadoDoDia(atual)) {
      void this.recarregar(usuarioId);
    }

    return () => {
      this.inscricoes.delete(inscricao);
    };
  }

  /** Invalida a agenda local e nas demais abas. */
  public notificarMudanca(usuarioId: string | null | undefined): void {
    publicar('AGENDA_INVALIDADA', null);
    if (!usuarioId) return;
    void this.recarregar(usuarioId);
  }

  public recarregarComThrottle(usuarioId: string): Promise<EstadoAgendaUsuario | null> {
    const agora = Date.now();
    const ultimo = this.ultimoCarregamento.get(usuarioId) ?? 0;
    if (agora - ultimo < THROTTLE_MS) {
      return Promise.resolve(this.estados.get(usuarioId) ?? null);
    }
    return this.recarregar(usuarioId);
  }

  /** Recarrega as duas fontes em paralelo (retry total). */
  public recarregar(usuarioId: string): Promise<EstadoAgendaUsuario> {
    const emAndamento = this.cargasEmAndamento.get(usuarioId);
    if (emAndamento) return emAndamento;

    const promessa = this.executarCarga(usuarioId, false);
    this.cargasEmAndamento.set(usuarioId, promessa);
    return promessa.finally(() => {
      if (this.cargasEmAndamento.get(usuarioId) === promessa) {
        this.cargasEmAndamento.delete(usuarioId);
      }
    });
  }

  /** Retry isolado da fonte de Tarefas, mantendo os blocos disponíveis. */
  public recarregarTarefas(usuarioId: string): Promise<EstadoAgendaUsuario> {
    return this.recarregarFonte(usuarioId, 'tarefas');
  }

  /** Retry isolado da fonte de Blocos, mantendo as tarefas disponíveis. */
  public recarregarBlocos(usuarioId: string): Promise<EstadoAgendaUsuario> {
    return this.recarregarFonte(usuarioId, 'blocos');
  }

  /** Apenas para testes: zera caches sem reinstalar listeners globais. */
  public resetarParaTestes(): void {
    this.estados.clear();
    this.epocas.clear();
    this.ultimoCarregamento.clear();
    this.cargasEmAndamento.clear();
    this.inscricoes.clear();
  }

  // ── Interno ──────────────────────────────────────────────────────────────

  private estadoDoDia(estado: EstadoAgendaUsuario): boolean {
    return estado.hojeLocal === hojeISO();
  }

  private epocaDe(usuarioId: string): number {
    return this.epocas.get(usuarioId) ?? 0;
  }

  private async recarregarFonte(
    usuarioId: string,
    fonte: 'tarefas' | 'blocos'
  ): Promise<EstadoAgendaUsuario> {
    const anterior = this.estados.get(usuarioId) ?? estadoInicial();
    const epoca = this.epocaDe(usuarioId);

    if (fonte === 'tarefas') {
      const resultado = await this.buscarTarefas();
      if (this.epocaDe(usuarioId) !== epoca) {
        return this.estados.get(usuarioId) ?? estadoInicial();
      }
      return this.aplicarResultado(usuarioId, anterior, { tarefas: resultado });
    }

    const resultado = await this.buscarBlocos();
    if (this.epocaDe(usuarioId) !== epoca) {
      return this.estados.get(usuarioId) ?? estadoInicial();
    }
    return this.aplicarResultado(usuarioId, anterior, { blocos: resultado });
  }

  private async buscarTarefas(): Promise<FonteEstado<TarefasHojeDTO>> {
    const [resultado] = await Promise.allSettled([request<TarefasHojeDTO>('/tarefas/hoje')]);
    if (resultado.status === 'fulfilled') {
      return { status: 'sucesso', dados: resultado.value };
    }
    return { status: 'erro', erro: comoErro(resultado.reason, 'Falha ao carregar tarefas') };
  }

  private async buscarBlocos(): Promise<FonteEstado<BlocoHojeDTO[]>> {
    const [resultado] = await Promise.allSettled([request<BlocoHojeDTO[]>('/blocos/hoje')]);
    if (resultado.status === 'fulfilled') {
      return { status: 'sucesso', dados: Array.isArray(resultado.value) ? resultado.value : [] };
    }
    return {
      status: 'erro',
      erro: comoErro(resultado.reason, 'Falha ao carregar blocos de estudos')
    };
  }

  /**
   * Carga das duas fontes em paralelo. `recargaPorVirada` evita laço infinito na virada de
   * dia: a segunda passagem apenas consolida o estado novo.
   */
  private async executarCarga(
    usuarioId: string,
    recargaPorVirada: boolean
  ): Promise<EstadoAgendaUsuario> {
    const epoca = this.epocaDe(usuarioId);
    const anterior = this.estados.get(usuarioId) ?? estadoInicial();

    this.ultimoCarregamento.set(usuarioId, Date.now());

    const [tarefasResult, blocosResult] = await Promise.allSettled([
      request<TarefasHojeDTO>('/tarefas/hoje'),
      request<BlocoHojeDTO[]>('/blocos/hoje')
    ]);

    // Logout/exclusão/troca de conta durante a requisição: o resultado é descartado.
    if (this.epocaDe(usuarioId) !== epoca) {
      return this.estados.get(usuarioId) ?? estadoInicial();
    }

    const tarefas: FonteEstado<TarefasHojeDTO> =
      tarefasResult.status === 'fulfilled'
        ? { status: 'sucesso', dados: tarefasResult.value }
        : { status: 'erro', erro: comoErro(tarefasResult.reason, 'Falha ao carregar tarefas') };

    const blocos: FonteEstado<BlocoHojeDTO[]> =
      blocosResult.status === 'fulfilled'
        ? {
            status: 'sucesso',
            dados: Array.isArray(blocosResult.value) ? blocosResult.value : []
          }
        : {
            status: 'erro',
            erro: comoErro(blocosResult.reason, 'Falha ao carregar blocos de estudos')
          };

    const hojeLocal =
      tarefas.status === 'sucesso' && tarefas.dados?.hojeLocal
        ? tarefas.dados.hojeLocal
        : anterior.hojeLocal ?? hojeISO();

    if (anterior.hojeLocal && anterior.hojeLocal !== hojeLocal) {
      // Dia novo: nada do dia anterior sobrevive a esta carga.
      const renovado = this.aplicarResultado(
        usuarioId,
        estadoInicial(),
        { tarefas, blocos },
        hojeLocal
      );
      if (recargaPorVirada) return renovado;
      return this.executarCarga(usuarioId, true);
    }

    return this.aplicarResultado(usuarioId, anterior, { tarefas, blocos }, hojeLocal);
  }

  private aplicarResultado(
    usuarioId: string,
    anterior: EstadoAgendaUsuario,
    fontes: { tarefas?: FonteEstado<TarefasHojeDTO>; blocos?: FonteEstado<BlocoHojeDTO[]> },
    hojeLocalInformado?: string
  ): EstadoAgendaUsuario {
    const tarefas = fontes.tarefas ?? anterior.tarefas;
    const blocos = fontes.blocos ?? anterior.blocos;

    const hojeLocal =
      hojeLocalInformado ??
      (tarefas.status === 'sucesso' && tarefas.dados?.hojeLocal
        ? tarefas.dados.hojeLocal
        : anterior.hojeLocal ?? hojeISO());

    const estado: EstadoAgendaUsuario = {
      status: statusAgregado(tarefas, blocos),
      hojeLocal,
      tarefas,
      blocos
    };

    this.estados.set(usuarioId, estado);
    this.notificar(usuarioId, estado);
    return estado;
  }

  private notificar(usuarioId: string, estado: EstadoAgendaUsuario | null): void {
    for (const inscricao of this.inscricoes) {
      if (inscricao.usuarioId !== usuarioId) continue;
      try {
        inscricao.ouvinte(estado);
      } catch (falha) {
        registrarFalha('agenda.ouvinte', falha);
      }
    }
  }

  /** Descarta o estado de uma conta; a próxima assinatura recarrega do zero (D-46). */
  private zerar(usuarioId?: string | null): void {
    const alvos = usuarioId
      ? [usuarioId]
      : [...new Set([...this.estados.keys(), ...this.epocas.keys()])];

    for (const alvo of alvos) {
      this.epocas.set(alvo, this.epocaDe(alvo) + 1);
      this.estados.delete(alvo);
      this.ultimoCarregamento.delete(alvo);
      this.cargasEmAndamento.delete(alvo);
      this.notificar(alvo, null);
    }
  }

  private instalarListenersGlobais(): void {
    if (typeof window === 'undefined' || this.listenersInstalados) return;
    this.listenersInstalados = true;

    assinar('AGENDA_INVALIDADA', () => {
      const alvos = new Set([...this.inscricoes].map((inscricao) => inscricao.usuarioId));
      for (const usuarioId of alvos) {
        void this.recarregarComThrottle(usuarioId);
      }
    });

    const recarregarAtivos = () => {
      const alvos = new Set([...this.inscricoes].map((inscricao) => inscricao.usuarioId));
      for (const usuarioId of alvos) {
        void this.recarregarComThrottle(usuarioId);
      }
    };

    window.addEventListener('focus', recarregarAtivos);
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') recarregarAtivos();
    });

    window.addEventListener('trilhara:logout', (evento: Event) => {
      const detalhe = (evento as CustomEvent<{ usuarioId?: string }>).detail;
      this.zerar(detalhe?.usuarioId ?? null);
    });

    window.addEventListener('trilhara:conta-excluida', (evento: Event) => {
      const detalhe = (evento as CustomEvent<{ usuarioId?: string }>).detail;
      this.zerar(detalhe?.usuarioId ?? null);
    });
  }
}

export const agendaHojeService = new AgendaHojeManager();

/**
 * Deriva as três seções da Visão Hoje (§9) a partir do estado bruto por conta.
 *
 * Ordens normativas:
 * 1. Atrasadas — `dataVencimento < hojeLocal`, agrupadas por série (D-23);
 * 2. Hoje — blocos pendentes do dia (`trilhaNome ASC, ordem ASC`) e tarefas do dia
 *    (`horario ASC NULLS LAST, prioridade DESC, ordem ASC`), em grupos separados;
 * 3. Sem data — sem `dataVencimento`, `prioridade DESC, ordem ASC`.
 */
export function derivarAgenda(estado: EstadoAgendaUsuario | null): AgendaHojeDerivado | null {
  if (!estado) return null;

  const hojeLocal = estado.hojeLocal ?? hojeISO();

  const atrasadas = ordenar(
    (estado.tarefas.dados?.atrasadas ?? []).filter(
      (tarefa) =>
        !tarefa.concluida && Boolean(tarefa.dataVencimento) && tarefa.dataVencimento! < hojeLocal
    ),
    compararAtrasadas
  );

  const blocosHoje = ordenar(
    (estado.blocos.dados ?? []).filter((bloco) => !bloco.concluido),
    compararBlocosHoje
  );

  const tarefasHoje = ordenar(
    (estado.tarefas.dados?.hoje ?? []).filter(
      (tarefa) => !tarefa.concluida && tarefa.dataVencimento === hojeLocal
    ),
    compararTarefasHoje
  );

  const semData = ordenar(
    (estado.tarefas.dados?.semData ?? []).filter(
      (tarefa) => !tarefa.concluida && !tarefa.dataVencimento
    ),
    compararSemData
  );

  const erroTarefas =
    estado.tarefas.status === 'erro'
      ? estado.tarefas.erro ?? new Error('Falha ao carregar tarefas')
      : null;
  const erroBlocos =
    estado.blocos.status === 'erro'
      ? estado.blocos.erro ?? new Error('Falha ao carregar blocos de estudos')
      : null;

  const vazioTotal =
    estado.tarefas.status === 'sucesso' &&
    estado.blocos.status === 'sucesso' &&
    atrasadas.length === 0 &&
    blocosHoje.length === 0 &&
    tarefasHoje.length === 0 &&
    semData.length === 0;

  return {
    hojeLocal,
    status: estado.status,
    statusTarefas: estado.tarefas.status,
    statusBlocos: estado.blocos.status,
    atrasadas,
    atrasadasAgrupadas: agruparAtrasadasPorSerie(atrasadas),
    blocosHoje,
    tarefasHoje,
    semData,
    erroTarefas,
    erroBlocos,
    vazioTotal
  };
}

export interface UseAgendaHojeResultado {
  dados: AgendaHojeDerivado | null;
  estado: EstadoAgendaUsuario | null;
  carregando: boolean;
  recarregar: () => void;
  recarregarTarefas: () => void;
  recarregarBlocos: () => void;
  notificarMudanca: () => void;
}

/**
 * Consumo reativo da Visão Hoje.
 *
 * - visitante (sem `usuarioId`) não dispara requisição e recebe `dados: null` (D-46);
 * - a troca de conta descarta o estado anterior antes de assinar o novo usuário, de modo
 *   que nenhuma renderização intermediária exibe dados da conta anterior.
 */
export function useAgendaHoje(): UseAgendaHojeResultado {
  const { usuario } = useAuth();
  const usuarioId = usuario?.id ?? null;

  const [estado, setEstado] = useState<EstadoAgendaUsuario | null>(() =>
    usuarioId ? agendaHojeService.obterEstado(usuarioId) : null
  );

  const usuarioIdRef = useRef<string | null>(usuarioId);
  usuarioIdRef.current = usuarioId;

  useEffect(() => {
    if (!usuarioId) {
      setEstado(null);
      return;
    }

    // D-46: o estado em cache só é aceito se pertencer à conta corrente.
    setEstado(agendaHojeService.obterEstado(usuarioId));

    let ativo = true;
    const cancelar = agendaHojeService.subscrever(usuarioId, (novo) => {
      if (ativo) setEstado(novo);
    });

    return () => {
      ativo = false;
      cancelar();
    };
  }, [usuarioId]);

  const recarregar = useCallback(() => {
    if (usuarioIdRef.current) void agendaHojeService.recarregar(usuarioIdRef.current);
  }, []);

  const recarregarTarefas = useCallback(() => {
    if (usuarioIdRef.current) void agendaHojeService.recarregarTarefas(usuarioIdRef.current);
  }, []);

  const recarregarBlocos = useCallback(() => {
    if (usuarioIdRef.current) void agendaHojeService.recarregarBlocos(usuarioIdRef.current);
  }, []);

  const notificarMudanca = useCallback(() => {
    agendaHojeService.notificarMudanca(usuarioIdRef.current);
  }, []);

  const dados = useMemo(() => derivarAgenda(estado), [estado]);

  const carregando =
    estado === null || estado.tarefas.status === 'carregando' || estado.blocos.status === 'carregando';

  return {
    dados,
    estado,
    carregando,
    recarregar,
    recarregarTarefas,
    recarregarBlocos,
    notificarMudanca
  };
}
