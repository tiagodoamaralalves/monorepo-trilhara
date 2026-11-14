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

/**
 * Progresso de uma trilha é o conjunto de IDs de tópicos concluídos, derivado de
 * `topicosConcluidosIds`. Nunca um mapa `{ topicoId: { done } }`.
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
 * não existem mais — a fonte de verdade é `duracaoSegundos` e `dataSessao`.
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

export interface RegistroBlocoPendente {
  requestId: string;
  trilhaId: string;
  cronogramaId: string;
  /** Identificador do bloco de cronograma; inicia o timer com este `blocoId`. */
  blocoId: string;
  disciplinaId: string;
  duracaoMinutos: number;
}

/** Reexporta o contrato único de perfil, evitando duplicata de tipo. */
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

