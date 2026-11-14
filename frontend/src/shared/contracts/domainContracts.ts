/**
 * Contratos de domínio compartilhados e congelados .
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
  /**
 * chamado quando a conclusão do bloco responde 404 (bloco de versão substituída).
 * O chamador recarrega lista/agenda e mostra o toast normativo; a sessão já salva **não**
 * é reenviada.
 */
  onBlocoObsoleto?: () => void;
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
  chaveIdempotencia?: string;
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

/**
 * Ponto da série temporal de estatísticas.
 *
 * A chave (`data`) segue a `granularidade` da resposta: `yyyy-MM-dd` em DIA, a segunda-feira
 * da semana ISO em SEMANA e `yyyy-MM-01` em MES. A soma dos pontos é igual em qualquer
 * granularidade.
 */
interface SerieTemporalPontoDTO {
  data: string;
  totalSegundos: number;
  totalQuestoes: number;
  totalAcertos: number;
}

/** Granularidade do agrupamento da série temporal. */
type GranularidadeSerie = 'DIA' | 'SEMANA' | 'MES';

export interface EstatisticasTrilhaDTO {
  totalSegundos: number;
  totalSessoes: number;
  totalQuestoes: number;
  totalAcertos: number;
  totalErros: number;
  percentualAcertoGlobal: number;
  porDisciplina: EstatisticaDisciplinaDTO[];
  serieTemporal: SerieTemporalPontoDTO[];
  streakDias: number;
  granularidade: GranularidadeSerie;
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
 * Estado do badge de sincronização do Notas (autosave / rascunho local).
 */
export type SyncStatusState = 'salvo' | 'salvando' | 'alteracoes-locais' | 'erro';

// =========================================================================
// CONTRATOS TRILHAS, DISCIPLINAS E PROGRESSO NORMALIZADOS
// =========================================================================

interface CronogramaAtivoResumoDTO {
  cronogramaAtivoId: string;
  modalidade: string;
  dataInicio: string;
  dataFim: string;
}

export interface TrilhaResumoDTO {
  id: string;
  nome: string;
  cargo?: string;
  banca?: string;
  dataProva?: string;
  cor?: string;
  origemEditalId?: string;
  totalDisciplinas: number;
  disciplinasConcluidas?: number;
  temDisciplinaSemTopico?: boolean;
  totalTopicos: number;
  topicosConcluidos: number;
  percentualProgresso: number;
  cronogramaAtivoResumo?: CronogramaAtivoResumoDTO | null;
  criadoEm?: string;
  atualizadoEm?: string;
}

interface TopicoDTO {
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
  topicos: TopicoDTO[];
  totalTopicos?: number;
  topicosConcluidos?: number;
  percentualProgresso?: number;
  concluida?: boolean;
}

export interface TrilhaDetalheDTO {
  id: string;
  origemEditalId?: string;
  origemVersao?: number;
  nome: string;
  cargo?: string;
  banca?: string;
  dataProva?: string;
  informacoesComplementares?: string;
  cor?: string;
  totalDisciplinas?: number;
  disciplinasConcluidas?: number;
  temDisciplinaSemTopico?: boolean;
  totalTopicos?: number;
  topicosConcluidos?: number;
  percentualProgresso?: number;
  cronogramaAtivoResumo?: CronogramaAtivoResumoDTO | null;
  disciplinas: DisciplinaDTO[];
  topicosConcluidosIds: string[];
  criadoEm?: string;
  atualizadoEm?: string;
}

export interface ProgressoTopicoDTO {
  topicoId: string;
  concluido: boolean;
  concluidoEm?: string;
}

interface CriarTopicoPayload {
  id?: string;
  nome: string;
  ordem?: number;
  }

interface CriarDisciplinaPayload {
  id?: string;
  nome: string;
  cor?: string;
  peso?: number;
  topicos?: CriarTopicoPayload[];
}

export type CriarDisciplinaRequest = CriarDisciplinaPayload;

interface AtualizarDisciplinaPayload {
  nome: string;
  cor?: string;
  peso?: number;
  topicos?: CriarTopicoPayload[];
}

export type AtualizarDisciplinaRequest = AtualizarDisciplinaPayload;

/**
 * Payload de criação de trilha. É exatamente um entre `editalId` e `disciplinasIniciais`
 * (enviar os dois responde 400 VALIDATION_ERROR em `errors.editalId`).
 */
export interface CriarTrilhaRequest {
  nome: string;
  cargo?: string;
  banca?: string;
  dataProva?: string;
  cor?: string;
  editalId?: string;
  disciplinasIniciais?: CriarDisciplinaPayload[];
  /** Metadados opcionais do formulário de edição. */
  informacoesComplementares?: string;
}

export interface TrilhaImpactoExclusaoDTO {
  disciplinas: number;
  cronogramas: number;
  sessoes: number;
  cadernosVinculados: number;
}


// =========================================================================
// CONTRATOS CRONOGRAMAS, BLOCOS E DISPONIBILIDADE SEMANAL
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
  blocosPorDiaSemana?: Record<number, number>;
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
  blocosPorDiaSemana?: Record<number, number>;
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

// ============================================================================
// Domínio Tarefas: Listas, Tarefas Manuais, Séries Recorrentes e Lixeira
// ============================================================================

export type PrioridadeTarefa = 'BAIXA' | 'MEDIA' | 'ALTA';
export type EstadoTarefa = 'PENDENTE' | 'CONCLUIDA' | 'CANCELADA';
export type EscopoRecorrencia = 'ESTA' | 'ESTA_E_FUTURAS' | 'TODA_A_SERIE';
export type FrequenciaRecorrencia = 'DIARIO' | 'SEMANAL' | 'MENSAL';
export type ModoMensal = 'DIA_DO_MES' | 'ORDINAL_SEMANA';
export type TipoTermino = 'NUNCA' | 'EM_DATA' | 'APOS_OCORRENCIAS';

interface SubtarefaDTO {
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
  modoLembrete?: ModoLembrete;
  lembreteMinutosAntes?: number;
  fusoAgendamento?: string;
  deletedAt?: string;
  expiresAt?: string;
  loteExclusaoId?: string;
  criadoEm?: string;
  atualizadoEm?: string;
}

/** Modelo explícito de lembrete: ausência de minutos nunca significa "herdar". */
export type ModoLembrete = 'HERDAR' | 'SEM_ALERTA' | 'PERSONALIZADO';

export type TipoConexaoFluxo = 'ANTECESSORA' | 'SUCESSORA' | 'PAR';

export interface PosicaoFluxoDTO {
  x: number;
  y: number;
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
  modoLembrete?: ModoLembrete;
  lembreteMinutosAntes?: number;
  fusoAgendamento?: string;
  deletedAt?: string;
  expiresAt?: string;
  loteExclusaoId?: string;
  noFluxo?: boolean;
  sucessorasIds?: string[];
  antecessorasIds?: string[];
  paresIds?: string[];
  posicaoFluxo?: PosicaoFluxoDTO | null;
  criadoEm?: string;
  atualizadoEm?: string;
}

/** Resposta de GET /api/tarefas/hoje: apenas tarefas PENDENTES. */
export interface TarefasHojeDTO {
  hojeLocal: string;
  atrasadas: TarefaDTO[];
  hoje: TarefaDTO[];
  semData: TarefaDTO[];
  concluidasHoje?: TarefaDTO[];
}

/** Resposta de GET /api/tarefas/lixeira: tarefas paginadas + metadados. */
export interface LixeiraTarefasDTO {
  tarefas: TarefaDTO[];
  listas: ListaTarefasDTO[];
  series: SerieRecorrenteDTO[];
  pagina: number;
  tamanho: number;
  totalItens: number;
  totalPaginas: number;
}

/** Resposta de POST /api/tarefas/lixeira/{id}/restaurar. */
export interface RestauracaoTarefaDTO {
  tarefa: TarefaDTO;
  realocadaParaListaGeral: boolean;
}

/** Resposta de GET /api/listas-tarefas/{id}/impacto-exclusao. */
export interface ListaImpactoExclusaoDTO {
  id: string;
  nome: string;
  tarefasAtivas: number;
  tarefasConcluidas: number;
  seriesVinculadas: number;
}

/** Resposta de DELETE /api/listas-tarefas/{id}. */
export interface ExclusaoListaDTO {
  id: string;
  tarefasAfetadas: number;
  loteExclusaoId: string;
}



