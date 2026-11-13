import { request } from '@shared/auth';
import { registrarFalha } from '@shared/diagnostico';
import {
  BuscaNotasResponse,
  Caderno,
  CadernoImpactoExclusao,
  ExclusaoLixeiraResponse,
  LixeiraNotasResponse,
  Nota,
  NotaMetadata,
  RascunhoNotaLocal,
  RestauracaoLixeiraResponse,
  Secao,
  SecaoImpactoExclusao,
  SincronizacaoTrilhaResponse,
  TipoItemLixeira
} from '../types/nota';

/** Envelope normativo de paginação por offset (D-28). */
export interface PaginaNotas {
  itens: NotaMetadata[];
  pagina: number;
  tamanho: number;
  totalItens: number;
  totalPaginas: number;
}

export interface OpcoesRequisicao {
  signal?: AbortSignal;
}

/**
 * Cliente HTTP do domínio Notas.
 *
 * Regras transversais: toda chamada privada usa o `request` central (que já injeta
 * `Authorization` e `X-Timezone`), IDs vão em `encodeURIComponent` e nenhuma listagem
 * transfere `conteudoMarkdown` — o Markdown completo só é buscado em `buscarNota`.
 */
export const NotaService = {
  // ── CADERNOS ──

  async listarCadernos(origemTrilhaId?: string): Promise<Caderno[]> {
    const qs = origemTrilhaId ? `?origemTrilhaId=${encodeURIComponent(origemTrilhaId)}` : '';
    return request<Caderno[]>(`/cadernos${qs}`);
  },

  async buscarCaderno(id: string): Promise<Caderno> {
    return request<Caderno>(`/cadernos/${encodeURIComponent(id)}`);
  },

  async criarCaderno(dados: { titulo: string; cor?: string; icone?: string }): Promise<Caderno> {
    return request<Caderno>('/cadernos', {
      method: 'POST',
      body: JSON.stringify(dados)
    });
  },

  async atualizarCaderno(
    id: string,
    dados: { titulo?: string; cor?: string; icone?: string }
  ): Promise<Caderno> {
    return request<Caderno>(`/cadernos/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(dados)
    });
  },

  /** Contagens exibidas no ConfirmDialog antes de mover o caderno para a lixeira (D-33). */
  async obterImpactoExclusaoCaderno(id: string): Promise<CadernoImpactoExclusao> {
    return request<CadernoImpactoExclusao>(`/cadernos/${encodeURIComponent(id)}/impacto-exclusao`);
  },

  async excluirCaderno(id: string): Promise<ExclusaoLixeiraResponse> {
    return request<ExclusaoLixeiraResponse>(`/cadernos/${encodeURIComponent(id)}`, {
      method: 'DELETE'
    });
  },

  // ── IMPORTAÇÃO E SINCRONIZAÇÃO DE TRILHA (D-29) ──

  /**
   * Importa uma trilha. Sem `reimportar`, uma trilha já vinculada responde
   * `409 TRAIL_ALREADY_IMPORTED` com `errors.cadernoId`.
   */
  async importarTrilha(trilhaId: string, reimportar = false): Promise<Caderno> {
    const qs = reimportar ? '?reimportar=true' : '';
    return request<Caderno>(`/cadernos/importar-trilha/${encodeURIComponent(trilhaId)}${qs}`, {
      method: 'POST'
    });
  },

  async sincronizarTrilha(cadernoId: string): Promise<SincronizacaoTrilhaResponse> {
    return request<SincronizacaoTrilhaResponse>(
      `/cadernos/${encodeURIComponent(cadernoId)}/sincronizar-trilha`,
      { method: 'POST' }
    );
  },

  // ── SEÇÕES ──

  async listarSecoes(cadernoId: string): Promise<Secao[]> {
    return request<Secao[]>(`/cadernos/${encodeURIComponent(cadernoId)}/secoes`);
  },

  async criarSecao(cadernoId: string, dados: { titulo: string; cor?: string }): Promise<Secao> {
    return request<Secao>(`/cadernos/${encodeURIComponent(cadernoId)}/secoes`, {
      method: 'POST',
      body: JSON.stringify(dados)
    });
  },

  async atualizarSecao(
    cadernoId: string,
    secaoId: string,
    dados: { titulo?: string; cor?: string }
  ): Promise<Secao> {
    return request<Secao>(
      `/cadernos/${encodeURIComponent(cadernoId)}/secoes/${encodeURIComponent(secaoId)}`,
      { method: 'PUT', body: JSON.stringify(dados) }
    );
  },

  async obterImpactoExclusaoSecao(secaoId: string): Promise<SecaoImpactoExclusao> {
    return request<SecaoImpactoExclusao>(`/secoes/${encodeURIComponent(secaoId)}/impacto-exclusao`);
  },

  async excluirSecao(cadernoId: string, secaoId: string): Promise<ExclusaoLixeiraResponse> {
    return request<ExclusaoLixeiraResponse>(
      `/cadernos/${encodeURIComponent(cadernoId)}/secoes/${encodeURIComponent(secaoId)}`,
      { method: 'DELETE' }
    );
  },

  // ── PÁGINAS ──

  /** Metadados paginados — sem `conteudoMarkdown` (D-29). */
  async listarNotasPorSecao(
    cadernoId: string,
    secaoId: string,
    pagina = 1,
    tamanho = 50,
    opcoes: OpcoesRequisicao = {}
  ): Promise<PaginaNotas> {
    return request<PaginaNotas>(
      `/cadernos/${encodeURIComponent(cadernoId)}/secoes/${encodeURIComponent(secaoId)}` +
        `/notas?pagina=${pagina}&tamanho=${tamanho}`,
      { signal: opcoes.signal }
    );
  },

  /** Único caminho de leitura completa da página (D-32). */
  async buscarNota(id: string, opcoes: OpcoesRequisicao = {}): Promise<Nota> {
    return request<Nota>(`/notas/${encodeURIComponent(id)}`, { signal: opcoes.signal });
  },

  async criarNota(dados: {
    cadernoId: string;
    secaoId: string;
    titulo: string;
    conteudoMarkdown?: string;
  }): Promise<Nota> {
    return request<Nota>(
      `/cadernos/${encodeURIComponent(dados.cadernoId)}/secoes/${encodeURIComponent(dados.secaoId)}/notas`,
      {
        method: 'POST',
        body: JSON.stringify({
          titulo: dados.titulo,
          conteudoMarkdown: dados.conteudoMarkdown
        })
      }
    );
  },

  /**
   * Atualiza uma página. O `signal` permite abortar o PUT pendente na troca de nota
   * (D-31c) sem perder o rascunho local, que é gravado antes do abort.
   */
  async atualizarNota(
    id: string,
    dados: { titulo?: string; conteudoMarkdown?: string },
    opcoes: OpcoesRequisicao = {}
  ): Promise<Nota> {
    return request<Nota>(`/notas/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(dados),
      signal: opcoes.signal
    });
  },

  async excluirNota(id: string): Promise<ExclusaoLixeiraResponse> {
    return request<ExclusaoLixeiraResponse>(`/notas/${encodeURIComponent(id)}`, {
      method: 'DELETE'
    });
  },

  // ── BUSCA GLOBAL (D-30) ──

  async buscarGlobal(
    termo: string,
    pagina = 1,
    tamanho = 20,
    opcoes: OpcoesRequisicao = {}
  ): Promise<BuscaNotasResponse> {
    return request<BuscaNotasResponse>(
      `/notas/busca?q=${encodeURIComponent(termo)}&pagina=${pagina}&tamanho=${tamanho}`,
      { signal: opcoes.signal }
    );
  },

  // ── LIXEIRA HIERÁRQUICA (D-34) ──

  async listarLixeira(pagina = 1, tamanho = 20): Promise<LixeiraNotasResponse> {
    return request<LixeiraNotasResponse>(`/notas/lixeira?pagina=${pagina}&tamanho=${tamanho}`);
  },

  async restaurarItemLixeira(
    tipo: TipoItemLixeira,
    id: string
  ): Promise<RestauracaoLixeiraResponse> {
    return request<RestauracaoLixeiraResponse>(
      `/notas/lixeira/${tipo.toLowerCase()}/${encodeURIComponent(id)}/restaurar`,
      { method: 'POST' }
    );
  },

  async esvaziarLixeira(): Promise<{
    cadernosRemovidos: number;
    secoesRemovidas: number;
    notasRemovidas: number;
  }> {
    return request<{
      cadernosRemovidos: number;
      secoesRemovidas: number;
      notasRemovidas: number;
    }>('/notas/lixeira/esvaziar', { method: 'DELETE' });
  },

  // ── RASCUNHOS DE CONTINGÊNCIA (D-31) ──

  obterChaveRascunho(usuarioId: string, notaId: string): string {
    return `trilhara:u:${usuarioId}:nota_draft:${notaId}`;
  },

  salvarRascunhoLocal(usuarioId: string, notaId: string, rascunho: RascunhoNotaLocal): void {
    if (!usuarioId || !notaId) return;
    try {
      window.localStorage.setItem(this.obterChaveRascunho(usuarioId, notaId), JSON.stringify(rascunho));
    } catch (e) {
      registrarFalha('notas.rascunho.gravar', e);
    }
  },

  obterRascunhoLocal(usuarioId: string, notaId: string): RascunhoNotaLocal | null {
    if (!usuarioId || !notaId) return null;
    try {
      const raw = window.localStorage.getItem(this.obterChaveRascunho(usuarioId, notaId));
      if (!raw) return null;
      const dados = JSON.parse(raw) as RascunhoNotaLocal;
      if (typeof dados?.conteudoMarkdown !== 'string' || typeof dados?.titulo !== 'string') {
        return null;
      }
      return dados;
    } catch (e) {
      registrarFalha('notas.rascunho.ler', e);
      return null;
    }
  },

  removerRascunhoLocal(usuarioId: string, notaId: string): void {
    if (!usuarioId || !notaId) return;
    try {
      window.localStorage.removeItem(this.obterChaveRascunho(usuarioId, notaId));
    } catch (e) {
      registrarFalha('notas.rascunho.remover', e);
    }
  },

  listarRascunhosLocais(usuarioId: string): Array<RascunhoNotaLocal & { notaId: string }> {
    if (!usuarioId) return [];
    const prefixo = `trilhara:u:${usuarioId}:nota_draft:`;
    const rascunhos: Array<RascunhoNotaLocal & { notaId: string }> = [];
    try {
      for (let i = 0; i < window.localStorage.length; i++) {
        const chave = window.localStorage.key(i);
        if (chave && chave.startsWith(prefixo)) {
          const notaId = chave.substring(prefixo.length);
          const raw = window.localStorage.getItem(chave);
          if (raw) {
            rascunhos.push({ ...(JSON.parse(raw) as RascunhoNotaLocal), notaId });
          }
        }
      }
    } catch (e) {
      registrarFalha('notas.rascunho.listar', e);
    }
    return rascunhos;
  }
};
