import {
  TrilhaResumoDTO,
  TrilhaDetalheDTO,
  DisciplinaDTO,
  ProgressoTopicoDTO,
  CriarDisciplinaRequest,
  AtualizarDisciplinaRequest,
  CriarTrilhaRequest,
  TrilhaImpactoExclusaoDTO,
  SessaoRegistradaDTO,
  EstatisticaDisciplinaDTO,
  EstatisticasTrilhaDTO,
  RemocaoSessoesDisciplinaResponse
} from '@shared/contracts/domainContracts';

export type {
  TrilhaResumoDTO,
  TrilhaDetalheDTO,
  DisciplinaDTO,
  ProgressoTopicoDTO,
  CriarDisciplinaRequest,
  AtualizarDisciplinaRequest,
  CriarTrilhaRequest,
  TrilhaImpactoExclusaoDTO,
  SessaoRegistradaDTO,
  EstatisticaDisciplinaDTO,
  EstatisticasTrilhaDTO,
  RemocaoSessoesDisciplinaResponse
};

/**
 * Metadados editáveis da trilha. Derivado do contrato único (`PUT /api/trilhas/{id}`),
 * sem tipo de transporte paralelo.
 */
export type AtualizacaoMetadadosTrilha = Pick<
  TrilhaDetalheDTO,
  'nome' | 'cargo' | 'banca' | 'dataProva' | 'informacoesComplementares'
>;

/** tipo de disciplina usado pela UI: o próprio DTO do backend. */
export type Disciplina = DisciplinaDTO;

/** tópico usado pela UI: o próprio DTO do backend. */
export type Topico = DisciplinaDTO['topicos'][number];

interface DisciplinaConfigCiclo {
  disciplinaId: string;
  participa: boolean;
}

/** Estrutura herdada de planejamento (remoção pertence às revisões de cronograma). */
export interface Ciclo {
  id: string;
  nome: string;
  tipo: 'ponderado' | 'sequencial';
  duracaoBlocoMin: number;
  dataInicio: string; // YYYY-MM-DD
  dataFim?: string; // YYYY-MM-DD
  blocosSemana: number[]; // 7 inteiros
  disciplinasConfig: DisciplinaConfigCiclo[];
}

/**
 * Progresso de uma trilha é o conjunto de IDs de tópicos concluídos, derivado de
 * `topicosConcluidosIds` (D-11). Nunca um mapa `{ topicoId: { done } }`.
 */
export type ProgressoTrilha = ReadonlySet<string>;

export interface EditalResumo {
  id: string;
  nome: string;
  area?: string;
  cargo: string;
  orgao?: string;
  banca?: string;
  esfera?: 'FEDERAL' | 'ESTADUAL' | 'MUNICIPAL';
  uf?: string;
  inicioInscricao?: string;
  fimInscricao?: string;
  dataProva?: string;
  disciplinas: Disciplina[];
}

export interface PaginaEditais {
  itens: EditalResumo[];
  page: number;
  size: number;
  total: number;
}

/**
 * Projeção de sessão usada pela UI.
 *
 * Derivados (`erros`, `percentualAcerto`) vêm calculados na leitura; `minutos`, `inicio` e `tipo`
 * não existem mais — a fonte de verdade é `duracaoSegundos` e `dataSessao` (F3/R4).
 */
export interface Sessao {
  id: string;
  trilhaId: string;
  disciplinaId?: string;
  disciplinaNome?: string;
  disciplinaCor?: string;
  disciplinaRemovida?: boolean;
  dataSessao?: string;
  duracaoSegundos?: number;
  questoes: number;
  acertos: number;
  erros: number;
  percentualAcerto?: number;
  editavel?: boolean;
  chaveIdempotencia?: string;
  criadoEm?: string;
  atualizadoEm?: string;
}

export interface DisciplinaProgramada {
  ocorrenciaId: string;
  cicloId: string;
  data: string;
  ordem: number;
  indiceRotacao?: number;
  disciplinaId: string;
  disciplinaNome: string;
  duracaoMinutos: number;
  concluida?: boolean;
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

export interface RegistroPlanejamentoPendente {
  requestId: string;
  trilhaId: string;
  cicloId: string;
  ocorrenciaId?: string;
  indiceRotacao?: number;
  disciplinaId: string;
  duracaoMinutos: number;
  proximoIndiceRastreador?: number;
}

/** Reexporta o contrato único de perfil (D-09/D-10), evitando duplicata de tipo. */
export type { UsuarioAutenticado } from '@shared/auth/types';

export type PaginaAtiva =
  | 'landing'
  | 'catalogo'
  | 'login'
  | 'cadastro'
  | 'hub'
  | 'planejamento'
  | 'disciplinas'
  | 'estudar'
  | 'dashboard';

