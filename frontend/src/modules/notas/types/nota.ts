/**
 * Contratos de Notas (R9: D-29 a D-34).
 *
 * Três coleções independentes no backend — caderno, seção e página — sem modelo embutido.
 * As listagens nunca trazem `conteudoMarkdown`: o Markdown completo chega apenas por
 * `GET /api/notas/{notaId}` quando a página é aberta.
 */

export interface Secao {
  id: string;
  cadernoId: string;
  titulo: string;
  ordem: number;
  origemDisciplinaId?: string | null;
  totalPaginas: number;
  criadoEm: string;
  atualizadoEm: string;
}

export interface Caderno {
  id: string;
  titulo: string;
  cor: string;
  icone: string;
  origemTrilhaId?: string | null;
  /** Nome atual da trilha vinculada (só existe quando `origemTrilhaId` está preenchido). */
  trilhaNome?: string | null;
  totalSecoes: number;
  totalPaginas: number;
  criadoEm: string;
  atualizadoEm: string;
}

export interface NotaMetadata {
  id: string;
  cadernoId: string;
  secaoId: string;
  titulo: string;
  ordem: number;
  topicoOrigemId?: string | null;
  criadoEm: string;
  atualizadoEm: string;
}

export interface Nota extends NotaMetadata {
  conteudoMarkdown: string;
}

/** Intervalo realçado dentro do snippet, em offsets UTF-16 do próprio snippet (D-30). */
export interface DestaqueBusca {
  inicio: number;
  fim: number;
}

export interface ResultadoBuscaNota {
  notaId: string;
  titulo: string;
  cadernoId: string;
  cadernoTitulo: string;
  secaoId: string;
  secaoTitulo: string;
  atualizadoEm: string;
  /** Texto puro, ≤ 180 caracteres — nunca HTML. */
  snippet: string;
  destaques: DestaqueBusca[];
}

export interface BuscaNotasResponse {
  itens: ResultadoBuscaNota[];
  pagina: number;
  tamanho: number;
  totalItens: number;
  totalPaginas: number;
}

export type TipoItemLixeira = 'CADERNO' | 'SECAO' | 'NOTA';

export interface ItemLixeira {
  tipo: TipoItemLixeira;
  id: string;
  titulo: string;
  /** Caminho do nível superior ("Caderno › Seção"); vazio para caderno. */
  breadcrumb: string;
  cadernoId?: string | null;
  secaoId?: string | null;
  loteExclusaoId?: string | null;
  deletedAt: string;
  expiresAt: string;
}

export interface LixeiraNotasResponse {
  itens: ItemLixeira[];
  pagina: number;
  tamanho: number;
  totalItens: number;
  totalPaginas: number;
}

export interface RascunhoNotaLocal {
  titulo: string;
  conteudoMarkdown: string;
  atualizadoEmLocal: string;
  /** `atualizadoEm` do servidor no momento em que o rascunho nasceu (D-31b). */
  baseAtualizadoEmServidor?: string | null;
}

interface RealocacaoDestino {
  cadernoId: string;
  secaoId: string | null;
}

export interface RestauracaoLixeiraResponse {
  mensagem: string;
  tipo: TipoItemLixeira;
  id: string;
  cadernoId: string;
  secaoId: string | null;
  ancestralRestaurado: boolean;
  realocadaPara: RealocacaoDestino | null;
}

export interface SincronizacaoTrilhaResponse {
  cadernoId: string;
  secoesCriadas: number;
  secoesRenomeadas: number;
  secoesDesvinculadas: number;
  paginasCriadas: number;
}

export interface ExclusaoLixeiraResponse {
  id: string;
  tipo: TipoItemLixeira;
  secoesAfetadas: number;
  notasAfetadas: number;
  loteExclusaoId: string;
}

export interface CadernoImpactoExclusao {
  id: string;
  titulo: string;
  secoes: number;
  paginas: number;
}

export interface SecaoImpactoExclusao {
  id: string;
  titulo: string;
  paginas: number;
}
