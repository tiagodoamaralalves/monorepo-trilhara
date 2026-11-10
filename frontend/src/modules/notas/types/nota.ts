export interface Secao {
  id: string;
  usuarioId?: string;
  cadernoId?: string;
  titulo: string;
  cor?: string;
  ordem: number;
  origemDisciplinaId?: string;
  totalNotas?: number;
  deletedAt?: string | null;
  criadoEm?: string;
  atualizadoEm?: string;
}

export interface Caderno {
  id: string;
  usuarioId?: string;
  userId?: string;
  titulo: string;
  cor?: string;
  icone?: string;
  origemTrilhaId?: string;
  vinculadoATrilha?: boolean;
  totalSecoes?: number;
  totalNotas?: number;
  secoes?: Secao[];
  deletedAt?: string | null;
  criadoEm: string;
  atualizadoEm: string;
}

export interface NotaMetadata {
  id: string;
  usuarioId?: string;
  cadernoId: string;
  secaoId: string;
  titulo: string;
  ordem?: number;
  origemTopicoId?: string;
  deletedAt?: string | null;
  criadoEm: string;
  atualizadoEm: string;
}

export interface Nota extends NotaMetadata {
  conteudoMarkdown: string;
}

export interface ResultadoBuscaNota {
  notaId: string;
  titulo: string;
  cadernoId: string;
  cadernoTitulo: string;
  secaoId: string;
  secaoTitulo: string;
  atualizadoEm: string;
  snippet: string;
}

export interface BuscaNotasResponse {
  itens: ResultadoBuscaNota[];
  total: number;
  pagina: number;
  tamanho: number;
  totalPaginas: number;
}

export interface ItemLixeira {
  tipo: 'CADERNO' | 'SECAO' | 'NOTA';
  id: string;
  titulo: string;
  loteExclusaoId?: string;
  cadernoId?: string;
  cadernoTitulo?: string;
  secaoId?: string;
  secaoTitulo?: string;
  deletedAt: string;
  expiresAt: string;
}

export interface RascunhoNotaLocal {
  titulo: string;
  conteudoMarkdown: string;
  atualizadoEmLocal: string;
  baseAtualizadoEmServidor?: string;
}

export interface SincronizacaoTrilhaResponse {
  cadernoId: string;
  secoesCriadas: number;
  paginasCriadas: number;
  secoesDesvinculadas: number;
}

export interface ExclusaoLixeiraResponse {
  id: string;
  tipo: string;
  secoesAfetadas: number;
  notasAfetadas: number;
  loteExclusaoId: string;
}
