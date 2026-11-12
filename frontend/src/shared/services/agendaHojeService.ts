import { useState, useEffect, useCallback } from 'react';
import { request } from '../auth/api';
import {
  BlocoHojeDTO,
  TarefasHojeDTO,
  TarefaDTO
} from '../contracts/domainContracts';

export interface GrupoAtrasadas {
  principal: TarefaDTO;
  anteriores: TarefaDTO[];
}

export interface AgendaHojeData {
  hojeLocal: string;
  // Seção 1: Atrasadas
  atrasadas: TarefaDTO[];
  atrasadasAgrupadas: GrupoAtrasadas[];
  // Seção 2: Hoje
  blocosHoje: BlocoHojeDTO[];
  tarefasHoje: TarefaDTO[];
  tarefasConcluidasHoje: TarefaDTO[];
  // Seção 3: Sem Data
  semData: TarefaDTO[];
  // Resiliência a falhas parciais
  erroEstudos: Error | null;
  erroTarefas: Error | null;
}

const PESO_PRIORIDADE: Record<string, number> = {
  ALTA: 3,
  URGENTE: 3,
  MEDIA: 2,
  BAIXA: 1,
  NENHUMA: 0
};

function obterPesoPrioridade(p?: string): number {
  if (!p) return PESO_PRIORIDADE.MEDIA;
  const upper = String(p).toUpperCase();
  return PESO_PRIORIDADE[upper] ?? PESO_PRIORIDADE.MEDIA;
}

function obterDataLocalFallback(): string {
  try {
    const tz = Intl.DateTimeFormat().resolvedOptions().timeZone || 'America/Sao_Paulo';
    const formatter = new Intl.DateTimeFormat('en-CA', {
      timeZone: tz,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    });
    return formatter.format(new Date());
  } catch {
    return new Date().toISOString().slice(0, 10);
  }
}

/**
 * Agrupa ocorrências atrasadas de uma mesma série recorrente.
 * Ocorrência mais recente é a principal; as anteriores ficam sob ela.
 * O conjunto de grupos é ordenado por: dataVencimento ASC, prioridade DESC, ordem ASC.
 */
export function agruparAtrasadas(atrasadas: TarefaDTO[]): GrupoAtrasadas[] {
  const porSerie: Record<string, TarefaDTO[]> = {};
  const avulsas: TarefaDTO[] = [];

  for (const t of atrasadas) {
    if (t.serieId && !t.desvinculadaDaSerie) {
      if (!porSerie[t.serieId]) porSerie[t.serieId] = [];
      porSerie[t.serieId].push(t);
    } else {
      avulsas.push(t);
    }
  }

  const grupos: GrupoAtrasadas[] = [];

  for (const serieId of Object.keys(porSerie)) {
    const ocorrencias = porSerie[serieId];
    // Ordena decrescente por data de vencimento para a mais recente ser a principal
    ocorrencias.sort((a, b) => (b.dataVencimento || '').localeCompare(a.dataVencimento || ''));
    grupos.push({
      principal: ocorrencias[0],
      anteriores: ocorrencias.slice(1)
    });
  }

  for (const t of avulsas) {
    grupos.push({
      principal: t,
      anteriores: []
    });
  }

  // Ordena grupos canonicamente: dataVencimento ASC, prioridade DESC, ordem ASC
  grupos.sort((gA, gB) => {
    const dataA = gA.principal.dataVencimento || '';
    const dataB = gB.principal.dataVencimento || '';
    const compData = dataA.localeCompare(dataB);
    if (compData !== 0) return compData;

    const pesoA = obterPesoPrioridade(gA.principal.prioridade);
    const pesoB = obterPesoPrioridade(gB.principal.prioridade);
    if (pesoB !== pesoA) return pesoB - pesoA;

    return (gA.principal.ordem ?? 0) - (gB.principal.ordem ?? 0);
  });

  return grupos;
}

const CANAL_NOME = 'trilhara_agenda_hoje';
const THROTTLE_MS = 5000;

class AgendaHojeManager {
  private cacheDados: AgendaHojeData | null = null;
  private ouvintes: Set<(dados: AgendaHojeData) => void> = new Set();
  private canal: BroadcastChannel | null = null;
  private ultimoCarregamentoTimestamp = 0;
  private carregamentoEmAndamento: Promise<AgendaHojeData> | null = null;

  constructor() {
    this.iniciarListenersGlobais();
  }

  private iniciarListenersGlobais() {
    if (typeof window === 'undefined') return;

    if ('BroadcastChannel' in window) {
      try {
        this.canal = new BroadcastChannel(CANAL_NOME);
        this.canal.onmessage = (event) => {
          if (event.data?.tipo === 'AGENDA_ATUALIZADA') {
            this.recarregarComThrottle();
          }
        };
      } catch {
        this.canal = null;
      }
    }

    const handlerVisibilidade = () => {
      if (typeof document !== 'undefined' && document.visibilityState === 'visible') {
        this.recarregarComThrottle();
      }
    };

    const handlerFocus = () => {
      this.recarregarComThrottle();
    };

    window.addEventListener('visibilitychange', handlerVisibilidade);
    window.addEventListener('focus', handlerFocus);
  }

  public notificarMudanca() {
    if (this.canal) {
      try {
        this.canal.postMessage({ tipo: 'AGENDA_ATUALIZADA', timestamp: Date.now() });
      } catch {
        // Fallback silencioso
      }
    }
    // Dispara recarregamento imediato sem throttle
    this.recarregar();
  }

  public recarregarComThrottle(): Promise<AgendaHojeData | null> {
    const agora = Date.now();
    if (agora - this.ultimoCarregamentoTimestamp < THROTTLE_MS) {
      return Promise.resolve(this.cacheDados);
    }
    return this.recarregar();
  }

  public obterDados(): AgendaHojeData | null {
    return this.cacheDados;
  }

  public resetarParaTestes() {
    this.cacheDados = null;
    this.ultimoCarregamentoTimestamp = 0;
    this.carregamentoEmAndamento = null;
    this.ouvintes.clear();
  }

  public subscrever(ouvinte: (dados: AgendaHojeData) => void): () => void {
    this.ouvintes.add(ouvinte);
    if (this.cacheDados) {
      ouvinte(this.cacheDados);
    }

    this.recarregar();

    return () => {
      this.ouvintes.delete(ouvinte);
    };
  }

  private notificarOuvintes(dados: AgendaHojeData) {
    this.cacheDados = dados;
    for (const ouvinte of this.ouvintes) {
      try {
        ouvinte(dados);
      } catch (e) {
        console.error('Erro em ouvinte da AgendaHoje:', e);
      }
    }
  }

  public async recarregar(): Promise<AgendaHojeData> {
    if (this.carregamentoEmAndamento) {
      return this.carregamentoEmAndamento;
    }

    this.carregamentoEmAndamento = this.executarCarga();
    try {
      const res = await this.carregamentoEmAndamento;
      this.ultimoCarregamentoTimestamp = Date.now();
      this.notificarOuvintes(res);
      return res;
    } finally {
      this.carregamentoEmAndamento = null;
    }
  }

  private async executarCarga(): Promise<AgendaHojeData> {
    const [tarefasResult, blocosResult] = await Promise.allSettled([
      request<TarefasHojeDTO>('/tarefas/hoje'),
      request<BlocoHojeDTO[]>('/blocos/hoje')
    ]);

    let erroTarefas: Error | null = null;
    let erroEstudos: Error | null = null;

    let tarefasHojeDTO: TarefasHojeDTO | null = null;
    let blocosHojeDTO: BlocoHojeDTO[] = [];

    if (tarefasResult.status === 'fulfilled') {
      tarefasHojeDTO = tarefasResult.value;
    } else {
      erroTarefas =
        tarefasResult.reason instanceof Error
          ? tarefasResult.reason
          : new Error(String(tarefasResult.reason || 'Falha ao carregar tarefas'));
    }

    if (blocosResult.status === 'fulfilled') {
      blocosHojeDTO = Array.isArray(blocosResult.value) ? blocosResult.value : [];
    } else {
      erroEstudos =
        blocosResult.reason instanceof Error
          ? blocosResult.reason
          : new Error(String(blocosResult.reason || 'Falha ao carregar blocos de estudos'));
    }

    const hojeLocal = tarefasHojeDTO?.hojeLocal || obterDataLocalFallback();

    // ── SEÇÃO 1: ATRASADAS ──
    // Apenas tarefas manuais pendentes com dataVencimento < hojeLocal
    const atrasadasPendentes = (tarefasHojeDTO?.atrasadas || []).filter(
      (t) => !t.concluida && t.dataVencimento && t.dataVencimento < hojeLocal
    );
    atrasadasPendentes.sort((a, b) => {
      const compData = (a.dataVencimento || '').localeCompare(b.dataVencimento || '');
      if (compData !== 0) return compData;
      const compPrio = obterPesoPrioridade(b.prioridade) - obterPesoPrioridade(a.prioridade);
      if (compPrio !== 0) return compPrio;
      return (a.ordem ?? 0) - (b.ordem ?? 0);
    });

    const atrasadasAgrupadas = agruparAtrasadas(atrasadasPendentes);

    // ── SEÇÃO 2: HOJE ──
    // Subdivisão 1: blocos pendentes de hojeLocal (trilhaNome ASC, ordem ASC)
    const blocosHojePendentes = blocosHojeDTO.filter((b) => !b.concluido);
    blocosHojePendentes.sort((a, b) => {
      const compTrilha = (a.trilhaNome || '').localeCompare(b.trilhaNome || '');
      if (compTrilha !== 0) return compTrilha;
      const compOrdem = (a.ordem ?? 0) - (b.ordem ?? 0);
      if (compOrdem !== 0) return compOrdem;
      return (a.blocoId || a.id || '').localeCompare(b.blocoId || b.id || '');
    });

    // Subdivisão 2: tarefas manuais pendentes com dataVencimento == hojeLocal
    const tarefasHojePendentes = (tarefasHojeDTO?.hoje || []).filter(
      (t) => !t.concluida && t.dataVencimento === hojeLocal
    );
    tarefasHojePendentes.sort((a, b) => {
      // horario ASC NULLS LAST
      if (a.horario && !b.horario) return -1;
      if (!a.horario && b.horario) return 1;
      if (a.horario && b.horario) {
        const compHorario = a.horario.localeCompare(b.horario);
        if (compHorario !== 0) return compHorario;
      }
      // prioridade DESC
      const compPrio = obterPesoPrioridade(b.prioridade) - obterPesoPrioridade(a.prioridade);
      if (compPrio !== 0) return compPrio;
      // ordem ASC
      return (a.ordem ?? 0) - (b.ordem ?? 0);
    });

    const tarefasConcluidasHoje = (tarefasHojeDTO?.hoje || []).filter(
      (t) => t.concluida || t.estado === 'CONCLUIDA'
    );

    // ── SEÇÃO 3: SEM DATA ──
    // Apenas tarefas manuais pendentes com dataVencimento == null
    const semDataPendentes = (tarefasHojeDTO?.semData || []).filter(
      (t) => !t.concluida && (!t.dataVencimento || t.dataVencimento.trim() === '')
    );
    semDataPendentes.sort((a, b) => {
      const compPrio = obterPesoPrioridade(b.prioridade) - obterPesoPrioridade(a.prioridade);
      if (compPrio !== 0) return compPrio;
      return (a.ordem ?? 0) - (b.ordem ?? 0);
    });

    return {
      hojeLocal,
      atrasadas: atrasadasPendentes,
      atrasadasAgrupadas,
      blocosHoje: blocosHojePendentes,
      tarefasHoje: tarefasHojePendentes,
      tarefasConcluidasHoje,
      semData: semDataPendentes,
      erroEstudos,
      erroTarefas
    };
  }
}

export const agendaHojeService = new AgendaHojeManager();

/**
 * Hook React para consumo simplificado e reativo da Agenda Hoje.
 */
export function useAgendaHoje() {
  const [dados, setDados] = useState<AgendaHojeData | null>(() => agendaHojeService.obterDados());
  const [carregando, setCarregando] = useState<boolean>(!agendaHojeService.obterDados());

  useEffect(() => {
    let ativo = true;
    const desinscrever = agendaHojeService.subscrever((novosDados) => {
      if (ativo) {
        setDados(novosDados);
        setCarregando(false);
      }
    });

    return () => {
      ativo = false;
      desinscrever();
    };
  }, []);

  const recarregar = useCallback(async () => {
    setCarregando(true);
    try {
      const res = await agendaHojeService.recarregar();
      setDados(res);
      return res;
    } finally {
      setCarregando(false);
    }
  }, []);

  return {
    dados,
    carregando,
    recarregar,
    notificarMudanca: useCallback(() => agendaHojeService.notificarMudanca(), [])
  };
}
