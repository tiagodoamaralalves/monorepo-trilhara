export interface DisciplinaProgramada {
  ocorrenciaId: string;
  cicloId: string;
  data: string; // YYYY-MM-DD
  ordem: number;
  disciplinaId: string;
  disciplinaNome: string;
  duracaoMinutos: number;
  concluida?: boolean;
}

export interface TrilhaDisciplina {
  id: string;
  nome: string;
  cor?: string;
  peso: number;
}

export interface CicloResumo {
  id: string;
  nome: string;
  tipo: string;
  duracaoBlocoMin: number;
  dataInicio: string;
  dataFim?: string;
}

export interface TrilhaResumo {
  id: string;
  nome: string;
  cargo?: string;
  banca?: string;
  area?: string;
  orgao?: string;
  dataProva?: string;
  cicloAtivoId?: string;
  ciclos?: CicloResumo[];
  disciplinas?: TrilhaDisciplina[];
}

export interface PlanejamentoCiclo {
  indiceRastreador: number;
  blocosConcluidos: string[];
  cronograma: DisciplinaProgramada[];
  cronogramaConfigurado: boolean;
  revisao: number;
  atualizadoEm: string;
  desatualizado?: boolean;
}
