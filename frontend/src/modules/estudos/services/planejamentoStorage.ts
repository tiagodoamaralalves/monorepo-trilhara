import { DisciplinaProgramada } from '../types/trilhara';
import { ApiError } from './api';
import { EstudosService } from './estudosService';

export interface EstadoCicloLocal {
  indiceRastreador: number;
  blocosConcluidos: string[];
  cronograma: DisciplinaProgramada[];
  cronogramaConfigurado?: boolean;
  revisao?: number;
  desatualizado?: boolean;
}

export function avancarSobreBlocosConcluidos(
  estado: EstadoCicloLocal,
  indiceInicial: number
): number {
  let indice = indiceInicial;
  for (let avancos = 0; avancos < estado.cronograma.length; avancos++) {
    const blocoConcluido = estado.cronograma.some((bloco) =>
      bloco.indiceRotacao === indice
      && estado.blocosConcluidos.includes(bloco.ocorrenciaId)
    );
    if (!blocoConcluido) break;
    indice++;
  }
  return indice;
}

const ESTADO_VAZIO: EstadoCicloLocal = {
  indiceRastreador: 0,
  blocosConcluidos: [],
  cronograma: [],
  cronogramaConfigurado: false,
  revisao: 0
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isDisciplinaProgramada(value: unknown): value is DisciplinaProgramada {
  if (!isRecord(value)) return false;
  return typeof value.ocorrenciaId === 'string'
    && typeof value.cicloId === 'string'
    && typeof value.data === 'string'
    && typeof value.ordem === 'number'
    && (value.indiceRotacao === undefined
      || (typeof value.indiceRotacao === 'number' && Number.isInteger(value.indiceRotacao)))
    && (value.concluida === undefined || typeof value.concluida === 'boolean')
    && typeof value.disciplinaId === 'string'
    && typeof value.disciplinaNome === 'string'
    && typeof value.duracaoMinutos === 'number';
}

function isEstadoCicloLocal(value: unknown): value is EstadoCicloLocal {
  if (!isRecord(value)) return false;
  return typeof value.indiceRastreador === 'number'
    && (value.cronogramaConfigurado === undefined
      || typeof value.cronogramaConfigurado === 'boolean')
    && (value.desatualizado === undefined
      || typeof value.desatualizado === 'boolean')
    && (value.revisao === undefined
      || (typeof value.revisao === 'number' && Number.isInteger(value.revisao)))
    && Array.isArray(value.blocosConcluidos)
    && value.blocosConcluidos.every((id) => typeof id === 'string')
    && Array.isArray(value.cronograma)
    && value.cronograma.every(isDisciplinaProgramada);
}

function chaveTrilha(trilhaId: string): string {
  return `trilhara_planejamento_v1:${trilhaId}`;
}

function migrarIndicesRotacao(estado: EstadoCicloLocal): EstadoCicloLocal {
  if (estado.revisao !== undefined
    && estado.cronogramaConfigurado !== undefined
    && estado.cronograma.every((bloco) => bloco.indiceRotacao !== undefined)) {
    return estado;
  }

  const ordenados = [...estado.cronograma].sort((a, b) =>
    a.data.localeCompare(b.data) || a.ordem - b.ordem
  );
  const indices = new Map<string, number>();
  let proximoIndice = 0;
  ordenados.forEach((bloco) => {
    if (bloco.indiceRotacao !== undefined) {
      indices.set(bloco.ocorrenciaId, bloco.indiceRotacao);
      proximoIndice = Math.max(proximoIndice, bloco.indiceRotacao + 1);
      return;
    }
    indices.set(bloco.ocorrenciaId, proximoIndice);
    proximoIndice++;
  });

  return {
    ...estado,
    cronogramaConfigurado: estado.cronogramaConfigurado ?? estado.cronograma.length > 0,
    revisao: estado.revisao ?? 0,
    cronograma: estado.cronograma.map((bloco) => ({
      ...bloco,
      indiceRotacao: indices.get(bloco.ocorrenciaId)
    }))
  };
}

function lerEstados(trilhaId: string): Record<string, EstadoCicloLocal> {
  let conteudo: string | null;
  try {
    conteudo = window.localStorage.getItem(chaveTrilha(trilhaId));
  } catch {
    throw new Error('Não foi possível ler o planejamento salvo no armazenamento local.');
  }
  if (!conteudo) return {};

  let dados: unknown;
  try {
    dados = JSON.parse(conteudo);
  } catch {
    throw new Error('Os dados locais do planejamento estão inválidos.');
  }
  if (!isRecord(dados)) throw new Error('Os dados locais do planejamento estão inválidos.');

  const estados: Record<string, EstadoCicloLocal> = {};
  let migrado = false;
  for (const [cicloId, estado] of Object.entries(dados)) {
    if (!isEstadoCicloLocal(estado)) {
      throw new Error('Os dados locais do planejamento estão inválidos.');
    }
    const estadoMigrado = migrarIndicesRotacao(estado);
    estados[cicloId] = estadoMigrado;
    migrado ||= estadoMigrado !== estado;
  }
  if (migrado) salvarEstados(trilhaId, estados);
  return estados;
}

function salvarEstados(trilhaId: string, estados: Record<string, EstadoCicloLocal>): void {
  try {
    window.localStorage.setItem(chaveTrilha(trilhaId), JSON.stringify(estados));
  } catch {
    throw new Error('Não foi possível salvar o planejamento no armazenamento local.');
  }
}

export const PlanejamentoStorage = {
  async carregar(trilhaId: string, cicloId: string): Promise<EstadoCicloLocal> {
    let remoto = await EstudosService.obterPlanejamentoCiclo(trilhaId, cicloId);
    if (remoto.revisao === 0) {
      const legado = this.obter(trilhaId, cicloId);
      if (legado.cronogramaConfigurado
        || legado.indiceRastreador > 0
        || legado.blocosConcluidos.length > 0
        || legado.cronograma.length > 0) {
        try {
          remoto = await EstudosService.salvarPlanejamentoCiclo(trilhaId, cicloId, {
            revisaoEsperada: 0,
            indiceRastreador: legado.indiceRastreador,
            blocosConcluidos: legado.blocosConcluidos,
            cronograma: legado.cronograma,
            cronogramaConfigurado: legado.cronogramaConfigurado
              ?? legado.cronograma.length > 0
          });
        } catch (error) {
          if (!(error instanceof ApiError) || error.status !== 409) throw error;
          remoto = await EstudosService.obterPlanejamentoCiclo(trilhaId, cicloId);
        }
      }
    }

    const estado: EstadoCicloLocal = {
      indiceRastreador: remoto.indiceRastreador,
      blocosConcluidos: remoto.blocosConcluidos,
      cronograma: remoto.cronograma,
      cronogramaConfigurado: remoto.cronogramaConfigurado,
      revisao: remoto.revisao,
      desatualizado: remoto.desatualizado
    };
    this.salvar(trilhaId, cicloId, estado);
    return estado;
  },

  listar(trilhaId: string): Record<string, EstadoCicloLocal> {
    return lerEstados(trilhaId);
  },

  obter(trilhaId: string, cicloId: string): EstadoCicloLocal {
    return lerEstados(trilhaId)[cicloId] || {
      ...ESTADO_VAZIO,
      blocosConcluidos: [],
      cronograma: []
    };
  },

  salvar(trilhaId: string, cicloId: string, estado: EstadoCicloLocal): void {
    const estados = lerEstados(trilhaId);
    salvarEstados(trilhaId, { ...estados, [cicloId]: estado });
  },

  removerCiclo(trilhaId: string, cicloId: string): void {
    const estados = lerEstados(trilhaId);
    delete estados[cicloId];
    salvarEstados(trilhaId, estados);
  }
};
