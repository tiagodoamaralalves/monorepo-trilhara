import {
  CronogramaAtivoResumoDTO,
  TrilhaResumoDTO,
  TrilhaDetalheDTO,
  DisciplinaDTO,
  ProgressoTopicoDTO,
  CriarDisciplinaRequest,
  AtualizarDisciplinaRequest,
  RegistrarSessaoPayload,
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
  RegistrarSessaoPayload,
  SessaoRegistradaDTO,
  EstatisticaDisciplinaDTO,
  EstatisticasTrilhaDTO,
  RemocaoSessoesDisciplinaResponse
};

export interface Topico {
  id: string;
  nome: string;
  ordem?: number;
}

export interface Disciplina {
  id: string;
  nome: string;
  cor?: string;
  peso: number;
  cadernoLink?: string;
  ordem?: number;
  topicos: Topico[];
}

interface DisciplinaConfigCiclo {
  disciplinaId: string;
  participa: boolean;
}

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

export interface ItemProgresso {
  done: boolean;
  doneAt?: string | null;
}

export interface Trilha {
  id: string;
  usuarioId?: string;
  donoId?: string;
  origemEditalId?: string;
  origemVersao?: number;
  nome: string;
  cargo?: string;
  areaCargo?: string;
  banca?: string;
  dataProva?: string; // YYYY-MM-DD
  informacoesComplementares?: string;
  cor?: string;
  disciplinas: Disciplina[];
  topicosConcluidosIds?: string[];
  progresso: Record<string, ItemProgresso>;
  ciclos: Ciclo[];
  cicloAtivoId?: string;
  totalDisciplinas?: number;
  totalTopicos?: number;
  topicosConcluidos?: number;
  percentualProgresso?: number;
  cronogramaAtivoResumo?: CronogramaAtivoResumoDTO | null;
  criadoEm?: string;
  atualizadoEm?: string;
}

export type AtualizacaoMetadadosTrilha = Pick<
  Trilha,
  'nome' | 'areaCargo' | 'banca' | 'dataProva' | 'informacoesComplementares'
>;

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

export interface Sessao {
  id: string;
  trilhaId: string;
  disciplinaId?: string;
  disciplinaNome?: string;
  disciplinaCor?: string;
  disciplinaRemovida?: boolean;
  dataSessao?: string;
  duracaoSegundos?: number;
  inicio?: string;
  minutos?: number;
  questoes: number;
  acertos: number;
  erros: number;
  percentualAcerto?: number;
  editavel?: boolean;
  chaveIdempotencia?: string;
  tipo?: 'ESTUDO' | 'QUESTOES' | 'ESTUDO_E_QUESTOES' | 'AJUSTE_MANUAL';
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

type RoleUsuario = 'USER' | 'ADMIN';

export interface UsuarioAutenticado {
  id: string;
  nome: string;
  email: string;
  roles: RoleUsuario[];
  ativo: boolean;
  emailVerificado: boolean;
  criadoEm: string;
}

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
