import { useEffect, useRef } from 'react';
import { unsavedChangesRegistry } from '../state/unsavedChangesRegistry';

/**
 * Registra um formulário com alterações não salvas no registro global (D-41).
 *
 * ```tsx
 * useUnsavedChanges(`nota:${notaId}`, sujo, () => gravarRascunhoLocal());
 * ```
 *
 * Com `sujo === true` o item entra no `unsavedChangesRegistry`: `beforeunload` fica ativo e
 * `AppSwitcher`/logout/troca de trilha passam a pedir confirmação. Ao salvar/descartar
 * (`sujo === false`) ou desmontar, o registro é removido.
 */
export function useUnsavedChanges(id: string, sujo: boolean, flush?: () => void): void {
  const flushRef = useRef(flush);

  useEffect(() => {
    flushRef.current = flush;
  }, [flush]);

  useEffect(() => {
    if (!sujo) {
      unsavedChangesRegistry.remover(id);
      return;
    }
    unsavedChangesRegistry.registrar({
      id,
      flush: () => flushRef.current?.()
    });
    return () => unsavedChangesRegistry.remover(id);
  }, [id, sujo]);
}
