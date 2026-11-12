/**
 * Registro global de alterações não salvas (D-41).
 *
 * Qualquer formulário com edição pendente registra-se aqui. Com ao menos um item sujo:
 * - `beforeunload` nativo é ativado (uma única vez);
 * - `AppSwitcher`, logout voluntário e troca de trilha pedem confirmação antes de navegar.
 *
 * O registro guarda também um `flush` opcional, executado **antes** de qualquer navegação
 * para gravar rascunhos (Notas) em `localStorage`.
 */

export interface ItemNaoSalvo {
  /** Identificador estável do formulário/registro (ex.: `nota:{id}`, `tarefa:{id}`). */
  id: string;
  /** Descrição curta usada na mensagem de confirmação. */
  descricao?: string;
  /** Grava o rascunho local antes de navegar (best-effort). */
  flush?: () => void;
}

const itens = new Map<string, ItemNaoSalvo>();
const ouvintes = new Set<() => void>();

let beforeUnloadAtivo = false;

function aoAntesDeDescarregar(event: BeforeUnloadEvent): void {
  if (itens.size === 0) return;
  event.preventDefault();
  // Navegadores modernos ignoram a mensagem customizada, mas exigem returnValue.
  event.returnValue = '';
}

function sincronizarListenerBeforeUnload(): void {
  if (typeof window === 'undefined') return;
  const precisa = itens.size > 0;
  if (precisa && !beforeUnloadAtivo) {
    window.addEventListener('beforeunload', aoAntesDeDescarregar);
    beforeUnloadAtivo = true;
  } else if (!precisa && beforeUnloadAtivo) {
    window.removeEventListener('beforeunload', aoAntesDeDescarregar);
    beforeUnloadAtivo = false;
  }
}

function notificarOuvintes(): void {
  ouvintes.forEach((ouvinte) => {
    try {
      ouvinte();
    } catch {
      // Ouvinte de UI não pode quebrar o registro
    }
  });
}

export const unsavedChangesRegistry = {
  /** Registra (ou atualiza) um item sujo. */
  registrar(item: ItemNaoSalvo): void {
    itens.set(item.id, item);
    sincronizarListenerBeforeUnload();
    notificarOuvintes();
  },

  /** Remove o registro quando o formulário é salvo ou descartado. */
  remover(id: string): void {
    if (!itens.delete(id)) return;
    sincronizarListenerBeforeUnload();
    notificarOuvintes();
  },

  /** Há alguma alteração não persistida? */
  temAlteracoes(): boolean {
    return itens.size > 0;
  },

  /** Itens registrados (cópia, para mensagens de confirmação). */
  listar(): ItemNaoSalvo[] {
    return Array.from(itens.values());
  },

  /**
   * Executa os `flush` registrados (rascunhos em `localStorage`) antes de navegar.
   * Não altera o estado sujo: quem decide sair chama `limpar()` depois de confirmar.
   */
  flushTodos(): void {
    itens.forEach((item) => {
      try {
        item.flush?.();
      } catch {
        // Rascunho é best-effort; não bloqueia a navegação confirmada
      }
    });
  },

  /** Limpa todos os registros (usado após confirmação de saída). */
  limpar(): void {
    itens.clear();
    sincronizarListenerBeforeUnload();
    notificarOuvintes();
  },

  /** Assina mudanças no registro; retorna a função de cancelamento. */
  assinar(ouvinte: () => void): () => void {
    ouvintes.add(ouvinte);
    return () => ouvintes.delete(ouvinte);
  },

  /** Apenas para testes: restaura o estado inicial. */
  __resetarParaTeste(): void {
    itens.clear();
    sincronizarListenerBeforeUnload();
    notificarOuvintes();
  }
};
