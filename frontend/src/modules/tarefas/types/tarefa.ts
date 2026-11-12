import {
  EstadoTarefa,
  PrioridadeTarefa,
  EscopoRecorrencia,
  FrequenciaRecorrencia,
  ModoMensal,
  TipoTermino,
  ListaTarefasDTO,
  SerieRecorrenteDTO,
  TarefaDTO,
  TarefasHojeDTO,
  LixeiraTarefasDTO,
  ExclusaoListaDTO
} from '@shared/contracts/domainContracts';

export type {
  PrioridadeTarefa,
  EscopoRecorrencia,
  FrequenciaRecorrencia,
  ModoMensal,
  TipoTermino,
  ListaTarefasDTO,
  SerieRecorrenteDTO,
  TarefaDTO,
  TarefasHojeDTO,
  LixeiraTarefasDTO,
  ExclusaoListaDTO
};

export type Prioridade = PrioridadeTarefa;

export interface Subtarefa {
  id: string;
  titulo: string;
  concluida: boolean;
  ordem?: number;
}

export interface ListaTarefas {
  id: string;
  usuarioId?: string;
  userId?: string; // alias retrocompatível
  nome: string;
  titulo?: string; // alias para exibição
  cor?: string;
  ordem?: number;
  totalTarefas?: number;
  deletedAt?: string;
  expiresAt?: string;
  loteExclusaoId?: string;
  criadoEm?: string;
  atualizadoEm?: string;
  origem?: string;
  cicloId?: string;
}

export interface Tarefa {
  id: string;
  usuarioId?: string;
  userId?: string;
  listaId?: string;
  serieId?: string;
  dataOriginalOcorrencia?: string;
  desvinculadaDaSerie?: boolean;
  titulo: string;
  descricao?: string;
  observacoes?: string;
  dataVencimento?: string;
  data?: string;
  horario?: string;
  duracaoMin?: number;
  estado?: EstadoTarefa;
  concluida: boolean;
  concluidaEm?: string | null;
  prioridade: Prioridade;
  ordem?: number;
  subtarefas?: Subtarefa[];
  lembreteMinutosAntes?: number;
  usaPadraoGlobalLembrete?: boolean;
  fusoAgendamento?: string;
  deletedAt?: string;
  expiresAt?: string;
  loteExclusaoId?: string;
  criadoEm?: string;
  atualizadoEm?: string;

  // Propriedades visuais e legadas opcionais
  disciplinaId?: string;
  disciplinaNome?: string;
  disciplinaCor?: string;
  origem?: string;
}

export type FiltroVisualizacao = 'hoje' | 'proximas' | 'concluidas' | 'todas' | 'calendario' | 'lixeira';
