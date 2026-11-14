import React, { useId } from 'react';
import styles from './FieldError.module.css';

export interface FieldErrorProps {
  /** Mensagem vinda de `ProblemDetails.errors[campo]`. */
  mensagem?: string | null;
  /** `id` do elemento de erro, referenciado pelo input via `aria-describedby`. */
  id?: string;
  className?: string;
}

/**
 * Erro de campo ligado ao input por `aria-describedby`.
 *
 * @example
 * const idErro = `${idCampo}-erro`;
 * <input id={idCampo} aria-describedby={erro ? idErro : undefined} aria-invalid={Boolean(erro)} />
 * <FieldError id={idErro} mensagem={erro} />
 */
export const FieldError: React.FC<FieldErrorProps> = ({ mensagem, id, className = '' }) => {
  const idGerado = useId();
  if (!mensagem) return null;
  return (
    <span id={id ?? idGerado} className={`${styles.erro} ${className}`.trim()} role="alert">
      {mensagem}
    </span>
  );
};
