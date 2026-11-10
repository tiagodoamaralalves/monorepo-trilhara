import { request } from '@shared/auth';
import {
  Caderno,
  Secao,
  Nota,
  NotaMetadata,
  ResultadoBuscaNota,
  BuscaNotasResponse,
  ItemLixeira,
  RascunhoNotaLocal,
  SincronizacaoTrilhaResponse,
  ExclusaoLixeiraResponse
} from '../types/nota';

export const NotaService = {
  // ── GESTÃO DE CADERNOS (Nível 1) ──

  async listarCadernos(): Promise<Caderno[]> {
    return request<Caderno[]>('/cadernos');
  },

  async buscarCaderno(id: string): Promise<Caderno> {
    return request<Caderno>(`/cadernos/${id}`);
  },

  async criarCaderno(dados: { titulo: string; cor?: string; icone?: string }): Promise<Caderno> {
    return request<Caderno>('/cadernos', {
      method: 'POST',
      body: JSON.stringify(dados)
    });
  },

  async atualizarCaderno(id: string, dados: { titulo?: string; cor?: string; icone?: string }): Promise<Caderno> {
    return request<Caderno>(`/cadernos/${id}`, {
      method: 'PUT',
      body: JSON.stringify(dados)
    });
  },

  async excluirCaderno(id: string): Promise<ExclusaoLixeiraResponse> {
    return request<ExclusaoLixeiraResponse>(`/cadernos/${id}`, {
      method: 'DELETE'
    });
  },

  // ── IMPORTAÇÃO E SINCRONIZAÇÃO DE TRILHA ──

  async importarTrilha(trilhaId: string): Promise<Caderno> {
    return request<Caderno>(`/cadernos/importar-trilha/${trilhaId}`, {
      method: 'POST'
    });
  },

  async sincronizarTrilha(cadernoId: string): Promise<SincronizacaoTrilhaResponse> {
    return request<SincronizacaoTrilhaResponse>(`/cadernos/${cadernoId}/sincronizar-trilha`, {
      method: 'POST'
    });
  },

  // ── GESTÃO DE SEÇÕES (Nível 2) ──

  async listarSecoes(cadernoId: string): Promise<Secao[]> {
    return request<Secao[]>(`/cadernos/${cadernoId}/secoes`);
  },

  async criarSecao(cadernoId: string, dados: { titulo: string; cor?: string }): Promise<Secao> {
    return request<Secao>(`/cadernos/${cadernoId}/secoes`, {
      method: 'POST',
      body: JSON.stringify(dados)
    });
  },

  async atualizarSecao(cadernoId: string, secaoId: string, dados: { titulo?: string; cor?: string }): Promise<Secao> {
    return request<Secao>(`/cadernos/${cadernoId}/secoes/${secaoId}`, {
      method: 'PUT',
      body: JSON.stringify(dados)
    });
  },

  async excluirSecao(cadernoId: string, secaoId: string): Promise<ExclusaoLixeiraResponse> {
    return request<ExclusaoLixeiraResponse>(`/cadernos/${cadernoId}/secoes/${secaoId}`, {
      method: 'DELETE'
    });
  },

  // ── GESTÃO DE NOTAS / PÁGINAS (Nível 3: Projeção de Metadados e Carregamento Sob Demanda) ──

  async listarNotasPorSecao(cadernoId: string, secaoId: string, pagina = 1, tamanho = 50): Promise<NotaMetadata[]> {
    return request<NotaMetadata[]>(`/cadernos/${cadernoId}/secoes/${secaoId}/notas?pagina=${pagina}&tamanho=${tamanho}`);
  },

  async buscarNota(id: string): Promise<Nota> {
    return request<Nota>(`/notas/${id}`);
  },

  async criarNota(dados: {
    cadernoId: string;
    secaoId: string;
    titulo: string;
    conteudoMarkdown?: string;
  }): Promise<Nota> {
    return request<Nota>(`/cadernos/${dados.cadernoId}/secoes/${dados.secaoId}/notas`, {
      method: 'POST',
      body: JSON.stringify({
        titulo: dados.titulo,
        conteudoMarkdown: dados.conteudoMarkdown
      })
    });
  },

  async atualizarNota(id: string, dados: { titulo?: string; conteudoMarkdown?: string }): Promise<Nota> {
    return request<Nota>(`/notas/${id}`, {
      method: 'PUT',
      body: JSON.stringify(dados)
    });
  },

  async excluirNota(id: string): Promise<ExclusaoLixeiraResponse> {
    return request<ExclusaoLixeiraResponse>(`/notas/${id}`, {
      method: 'DELETE'
    });
  },

  // ── BUSCA GLOBAL NO BACKEND ──

  async buscarGlobal(termo: string, pagina = 1, tamanho = 20): Promise<BuscaNotasResponse> {
    return request<BuscaNotasResponse>(`/notas/busca?q=${encodeURIComponent(termo)}&pagina=${pagina}&tamanho=${tamanho}`);
  },

  // ── LIXEIRA HIERÁRQUICA E PURGA ──

  async listarLixeira(pagina = 1, tamanho = 50): Promise<ItemLixeira[]> {
    return request<ItemLixeira[]>(`/notas/lixeira?pagina=${pagina}&tamanho=${tamanho}`);
  },

  async restaurarItemLixeira(tipo: 'CADERNO' | 'SECAO' | 'NOTA', id: string): Promise<{ mensagem: string }> {
    return request<{ mensagem: string }>(`/notas/lixeira/${tipo.toLowerCase()}/${id}/restaurar`, {
      method: 'POST'
    });
  },

  async esvaziarLixeira(): Promise<{ cadernosRemovidos: number; secoesRemovidas: number; notasRemovidas: number }> {
    return request<{ cadernosRemovidos: number; secoesRemovidas: number; notasRemovidas: number }>('/notas/lixeira/esvaziar', {
      method: 'DELETE'
    });
  },

  // ── RASCUNHOS DE CONTINGÊNCIA EM LOCALSTORAGE ──

  obterChaveRascunho(usuarioId: string, notaId: string): string {
    return `trilhara:u:${usuarioId}:nota_draft:${notaId}`;
  },

  salvarRascunhoLocal(usuarioId: string, notaId: string, rascunho: RascunhoNotaLocal): void {
    if (!usuarioId || !notaId) return;
    try {
      const chave = this.obterChaveRascunho(usuarioId, notaId);
      window.localStorage.setItem(chave, JSON.stringify(rascunho));
    } catch (e) {
      console.warn('Falha ao gravar rascunho local em localStorage', e);
    }
  },

  obterRascunhoLocal(usuarioId: string, notaId: string): RascunhoNotaLocal | null {
    if (!usuarioId || !notaId) return null;
    try {
      const chave = this.obterChaveRascunho(usuarioId, notaId);
      const raw = window.localStorage.getItem(chave);
      if (!raw) return null;
      return JSON.parse(raw) as RascunhoNotaLocal;
    } catch (e) {
      console.warn('Falha ao ler rascunho local de localStorage', e);
      return null;
    }
  },

  removerRascunhoLocal(usuarioId: string, notaId: string): void {
    if (!usuarioId || !notaId) return;
    try {
      const chave = this.obterChaveRascunho(usuarioId, notaId);
      window.localStorage.removeItem(chave);
    } catch (e) {
      console.warn('Falha ao remover rascunho local de localStorage', e);
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
            const data = JSON.parse(raw);
            rascunhos.push({ ...data, notaId });
          }
        }
      }
    } catch {
      // Ignora erro de acesso a localStorage
    }
    return rascunhos;
  }
};
