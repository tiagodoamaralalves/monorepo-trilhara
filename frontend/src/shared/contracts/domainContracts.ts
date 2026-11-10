/**
 * Contratos de domínio compartilhados e congelados na FASE 0.
 * Conforme PLANO-IMPLEMENTACAO-REESTRUTURACAO.md e REQUISITOS-REESTRUTURACAO-DADOS.md.
 */

/**
 * Resposta de erro estruturada conforme RFC 9457 (Problem Details for HTTP APIs)
 * com extensões Trilhara (`code`, `errors`).
 */
export interface ProblemDetailsResponse {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  code?: string;
  errors?: Record<string, string[]>;
}

/**
 * Modos de abertura do GlobalSessionModal reutilizados em Estudos, Tarefas e Pomodoro.
 */
export type ModoModalSessao = 'ESTUDOS_TIMER' | 'BLOCO_DIRETO' | 'BLOCO_TIMER' | 'MANUAL';

/**
 * Parâmetros para abertura do GlobalSessionModal.
 */
export interface GlobalSessionModalOpenParams {
  modo: ModoModalSessao;
  trilhaId?: string;
  disciplinaId?: string;
  blocoId?: string;
  duracaoInicialSegundos?: number;
  dataSessao?: string;
  trilhaNome?: string;
  disciplinaNome?: string;
  disciplinaCor?: string;
  onSuccess?: (sessao?: SessaoRegistradaDTO, blocoConcluido?: boolean) => void;
  triggerElement?: HTMLElement | null;
}

/**
 * Payload para registro de sessão via POST /api/trilhas/{trilhaId}/sessoes.
 */
export interface RegistrarSessaoPayload {
  disciplinaId: string;
  dataSessao: string; // ISO yyyy-MM-dd
  duracaoSegundos: number; // >= 60
  questoes?: number;
  acertos?: number;
  chaveIdempotencia?: string;
}

/**
 * Representação pública de uma sessão registrada retornada pela API.
 */
export interface SessaoRegistradaDTO {
  id: string;
  trilhaId: string;
  disciplinaId: string;
  disciplinaNome?: string;
  disciplinaCor?: string;
  blocoId?: string;
  duracaoSegundos: number;
  questoes?: number;
  acertos?: number;
  erros?: number;
  percentualAcerto?: number;
  disciplinaNomeSnapshot?: string;
  disciplinaCorSnapshot?: string;
  dataSessao: string; // ISO yyyy-MM-dd
  criadoEm: string; // ISO RFC 3339
  atualizadoEm?: string;
  disciplinaRemovida?: boolean;
  editavel?: boolean;
  chaveIdempotencia?: string;
}

export interface PaginaSessoesDTO {
  itens: SessaoRegistradaDTO[];
  pagina: number;
  tamanho: number;
  totalItens: number;
  totalPaginas: number;
}

export interface EstatisticaDisciplinaDTO {
  disciplinaId: string;
  nome: string;
  cor: string;
  disciplinaRemovida: boolean;
  totalSegundos: number;
  totalSessoes: number;
  totalQuestoes: number;
  totalAcertos: number;
  totalErros: number;
  percentualAcerto: number;
}

export interface SerieTemporalDiaDTO {
  data: string; // yyyy-MM-dd
  totalSegundos: number;
  totalQuestoes: number;
  totalAcertos: number;
}

export interface EstatisticasTrilhaDTO {
  totalSegundos: number;
  totalSessoes: number;
  totalQuestoes: number;
  totalAcertos: number;
  totalErros: number;
  percentualAcertoGlobal: number;
  porDisciplina: EstatisticaDisciplinaDTO[];
  serieTemporal: SerieTemporalDiaDTO[];
  streakDias: number;
}

export interface RemocaoSessoesDisciplinaResponse {
  sessoesRemovidas: number;
  totalSegundosRemovidos: number;
}

/**
 * Representação de um bloco do dia para a Visão Hoje compartilhada.
 * IDs de usuário omitidos no contrato público.
 */
export interface BlocoHojeDTO {
  id?: string;
  blocoId: string;
  trilhaId: string;
  cronogramaId: string;
  disciplinaId: string;
  disciplinaNome: string;
  disciplinaCor: string;
  trilhaNome?: string;
  data: string; // ISO yyyy-MM-dd
  ordem: number;
  duracaoMinutos: number;
  concluido: boolean;
  statusData?: 'HOJE' | 'hoje' | string;
}

/**
 * Item da agenda de Hoje: união discriminada entre tarefa manual e bloco de cronograma.
 */
export type ItemAgendaHojeDTO =
  | {
      tipo: 'MANUAL';
      tarefaId: string;
      titulo: string;
      concluida: boolean;
      prioridade?: 'BAIXA' | 'MEDIA' | 'ALTA' | 'URGENTE';
      dataVencimento?: string | null;
      ordem?: number;
      listaId?: string;
    }
  | {
      tipo: 'BLOCO_CRONOGRAMA';
      blocoId: string;
      trilhaId: string;
      cronogramaId: string;
      disciplinaId: string;
      disciplinaNome: string;
      disciplinaCor: string;
      trilhaNome?: string;
      data: string;
      ordem: number;
      duracaoMinutos: number;
      concluido: boolean;
    };

/**
 * Estado do badge de sincronização do Notas (autosave / rascunho local).
 */
export type SyncStatusState = 'salvo' | 'salvando' | 'alteracoes-locais' | 'erro';

// =========================================================================
// CONTRATOS FASE 2: TRILHAS, DISCIPLINAS E PROGRESSO NORMALIZADOS
// =========================================================================

export interface CronogramaAtivoResumoDTO {
  cronogramaAtivoId: string;
  modalidade: string;
  dataInicio: string;
  dataFim: string;
}

export interface TrilhaResumoDTO {
  id: string;
  usuarioId?: string;
  donoId?: string;
  nome: string;
  cargo?: string;
  areaCargo?: string;
  banca?: string;
  dataProva?: string;
  cor?: string;
  origemEditalId?: string;
  totalDisciplinas: number;
  totalTopicos: number;
  topicosConcluidos: number;
  percentualProgresso: number;
  cronogramaAtivoResumo?: CronogramaAtivoResumoDTO | null;
  criadoEm?: string;
  atualizadoEm?: string;
}

export interface TopicoDTO {
  id: string;
  nome: string;
  ordem?: number;
  concluido?: boolean;
}

export interface DisciplinaDTO {
  id: string;
  trilhaId?: string;
  nome: string;
  cor?: string;
  peso: number;
  ordem?: number;
  cadernoLink?: string;
  topicos: TopicoDTO[];
  totalTopicos?: number;
  topicosConcluidos?: number;
  percentualProgresso?: number;
}

export interface TrilhaDetalheDTO {
  id: string;
  usuarioId?: string;
  donoId?: string;
  origemEditalId?: string;
  origemVersao?: number;
  nome: string;
  cargo?: string;
  areaCargo?: string;
  banca?: string;
  dataProva?: string;
  informacoesComplementares?: string;
  cor?: string;
  totalDisciplinas?: number;
  totalTopicos?: number;
  topicosConcluidos?: number;
  percentualProgresso?: number;
  cronogramaAtivoResumo?: CronogramaAtivoResumoDTO | null;
  disciplinas: DisciplinaDTO[];
  topicosConcluidosIds: string[];
  ciclos?: unknown[];
  cronogramas?: unknown[];
  cicloAtivoId?: string;
  progresso?: Record<string, { done: boolean; doneAt?: string | null }>;
  criadoEm?: string;
  atualizadoEm?: string;
}

export interface ProgressoTopicoDTO {
  id?: string;
  usuarioId?: string;
  trilhaId: string;
  topicoId: string;
  concluido: boolean;
  concluidoEm?: string;
}

export interface CriarTopicoPayload {
  id?: string;
  nome: string;
  ordem?: number;
}

export interface CriarDisciplinaPayload {
  id?: string;
  nome: string;
  cor?: string;
  peso?: number;
  cadernoLink?: string;
  topicos?: CriarTopicoPayload[];
}

export type CriarDisciplinaRequest = CriarDisciplinaPayload;

export interface AtualizarDisciplinaPayload {
  nome: string;
  cor?: string;
  peso?: number;
  cadernoLink?: string;
  topicos?: CriarTopicoPayload[];
}

export type AtualizarDisciplinaRequest = AtualizarDisciplinaPayload;

export interface ReordenarDisciplinasPayload {
  disciplinaIds: string[];
}

export interface ReordenarDisciplinasRequest {
  ordemIds: string[];
}

// =========================================================================
// CONTRATOS FASE 4: CRONOGRAMAS, BLOCOS E DISPONIBILIDADE SEMANAL
// =========================================================================

export type ModalidadeCronograma = 'SEQUENCIAL' | 'PONDERADO' | 'MANUAL';

export interface CronogramaDTO {
  id: string;
  trilhaId: string;
  modalidade: ModalidadeCronograma;
  ativo: boolean;
  versaoAtiva: number;
  duracaoBlocoMinutos: number;
  tipoPeriodo: 'ATE_PROVA' | 'UMA_SEMANA' | 'UM_MES' | 'TRES_MESES' | string;
  dataInicio: string;
  dataFim: string;
  diasAtivosSemana?: number[];
  blocosPorDia?: number;
  disciplinasParticipantesIds: string[];
  semanaModeloManual?: Record<number, string[]>;
  configuracao?: Record<string, unknown>;
  quantidadeBlocos: number;
  cargaMinutosPorDia: number[];
  estadoPublicacao?: 'ATIVO' | 'PREPARANDO' | 'RECUPERACAO_NECESSARIA' | string;
  criadoEm?: string;
  atualizadoEm?: string;
}

export interface CriarCronogramaPayload {
  modalidade: ModalidadeCronograma;
  duracaoBlocoMinutos: number;
  tipoPeriodo: string;
  dataInicio: string;
  dataFim?: string;
  diasAtivosSemana?: number[];
  blocosPorDia?: number;
  disciplinasParticipantesIds: string[];
  semanaModeloManual?: Record<number, string[]>;
}

export type AtualizarCronogramaPayload = CriarCronogramaPayload;

export interface BlocoCronogramaItemDTO {
  id: string;
  trilhaId: string;
  cronogramaId: string;
  versaoGeracao: number;
  data: string;
  ordem: number;
  disciplinaId: string;
  disciplinaNome: string;
  disciplinaCor: string;
  duracaoMinutos: number;
  concluido: boolean;
  concluidoEm?: string | null;
  statusData: 'ATRASADO' | 'HOJE' | 'FUTURO';
  criadoEm?: string;
  atualizadoEm?: string;
}

export interface PaginaBlocosDTO {
  itens: BlocoCronogramaItemDTO[];
  pagina: number;
  tamanho: number;
  totalItens: number;
  totalPaginas: number;
}

export interface ConcluirAtrasadosResponse {
  operationId: string;
  totalProcessados: number;
  totalConcluidos: number;
  falhas: Array<{ blocoId: string; code: string }>;
}



export interface DisponibilidadeSemanalDTO {
  disponibilidadeSemanalMinutos: number[];
  cargaAtivaMinutosPorDia: number[];
  sobrecargaPorDia: boolean[];
}

export interface AtualizarDisponibilidadeSemanalPayload {
  disponibilidadeSemanalMinutos: number[];
}

// ============================================================================
// Domínio Tarefas: Listas, Tarefas Manuais, Séries Recorrentes e Lixeira
// ============================================================================

export type PrioridadeTarefa = 'BAIXA' | 'MEDIA' | 'ALTA';
export type EstadoTarefa = 'PENDENTE' | 'CONCLUIDA' | 'CANCELADA';
export type EscopoRecorrencia = 'ESTA' | 'ESTA_E_FUTURAS' | 'TODA_A_SERIE';
export type FrequenciaRecorrencia = 'DIARIO' | 'SEMANAL' | 'MENSAL';
export type ModoMensal = 'DIA_DO_MES' | 'ORDINAL_SEMANA';
export type TipoTermino = 'NUNCA' | 'EM_DATA' | 'APOS_OCORRENCIAS';

export interface SubtarefaDTO {
  id: string;
  titulo: string;
  concluida: boolean;
  ordem?: number;
}

export interface ListaTarefasDTO {
  id: string;
  usuarioId?: string;
  nome: string;
  cor?: string;
  ordem?: number;
  totalTarefas?: number;
  deletedAt?: string;
  expiresAt?: string;
  loteExclusaoId?: string;
  criadoEm?: string;
  atualizadoEm?: string;
}

export interface SerieRecorrenteDTO {
  id: string;
  usuarioId?: string;
  listaId?: string;
  titulo: string;
  descricao?: string;
  prioridade: PrioridadeTarefa;
  horario?: string;
  subtarefasModelo: SubtarefaDTO[];
  frequencia: FrequenciaRecorrencia;
  intervalo: number;
  diasDaSemana: number[];
  modoMensal?: ModoMensal;
  diaDoMes?: number;
  ordinalSemana?: number;
  diaSemanaOrdinal?: number;
  tipoTermino: TipoTermino;
  dataInicio: string;
  dataFim?: string;
  maxOcorrencias?: number;
  geradoAte?: string;
  totalGeradas: number;
  ativa: boolean;
  lembreteMinutosAntes?: number;
  usaPadraoGlobalLembrete?: boolean;
  fusoAgendamento?: string;
  deletedAt?: string;
  expiresAt?: string;
  loteExclusaoId?: string;
  criadoEm?: string;
  atualizadoEm?: string;
}

export interface TarefaDTO {
  id: string;
  usuarioId?: string;
  listaId?: string;
  serieId?: string;
  dataOriginalOcorrencia?: string;
  desvinculadaDaSerie?: boolean;
  titulo: string;
  descricao?: string;
  prioridade: PrioridadeTarefa;
  dataVencimento?: string;
  horario?: string;
  estado?: EstadoTarefa;
  concluida: boolean;
  concluidaEm?: string | null;
  ordem?: number;
  subtarefas?: SubtarefaDTO[];
  lembreteMinutosAntes?: number;
  usaPadraoGlobalLembrete?: boolean;
  fusoAgendamento?: string;
  deletedAt?: string;
  expiresAt?: string;
  loteExclusaoId?: string;
  criadoEm?: string;
  atualizadoEm?: string;
}

export interface TarefasHojeDTO {
  hojeLocal: string;
  atrasadas: TarefaDTO[];
  hoje: TarefaDTO[];
  semData: TarefaDTO[];
}

export interface LixeiraTarefasDTO {
  tarefas: TarefaDTO[];
  listas: ListaTarefasDTO[];
  series: SerieRecorrenteDTO[];
}

export interface ExclusaoListaDTO {
  id: string;
  tarefasAfetadas: number;
  loteExclusaoId: string;
}



