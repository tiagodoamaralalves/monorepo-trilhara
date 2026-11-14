import React, { useEffect, useState } from 'react';
import { CheckCircle2 } from 'lucide-react';
import {
  lerMensagemContaExcluida,
  limparParametroContaExcluida
} from '../../auth/redirecionamentoConta';
import styles from './AvisoContaExcluida.module.css';

/**
 * Aviso exibido na tela pública após o `202` da exclusão de conta.
 *
 * <p>Não é `SectionErrorCard` (não é falha) nem `Toast` (precisa sobreviver ao redirecionamento):
 * é um banner informativo da tela pública, com o parâmetro removido da URL após a primeira
 * leitura para não reaparecer em recarregamentos.</p>
 */
export const AvisoContaExcluida: React.FC<{ className?: string }> = ({ className = '' }) => {
  const [mensagem, setMensagem] = useState<string | null>(null);

  useEffect(() => {
    const texto = lerMensagemContaExcluida();
    if (texto) {
      setMensagem(texto);
      limparParametroContaExcluida();
    }
  }, []);

  if (!mensagem) return null;

  return (
    <div className={`${styles.aviso} ${className}`.trim()} role="status" aria-live="polite">
      <CheckCircle2 size={18} aria-hidden="true" />
      <span>{mensagem}</span>
    </div>
  );
};
