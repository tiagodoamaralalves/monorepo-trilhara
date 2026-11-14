import React, { useState } from 'react';
import { Plus } from 'lucide-react';
import styles from './QuickAddInput.module.css';

export interface QuickAddInputProps {
  onAdd: (text: string) => Promise<boolean> | boolean;
  placeholder: string;
  buttonTitle: string;
  buttonAriaLabel: string;
  inputAriaLabel?: string;
  value?: string;
  onValueChange?: (value: string) => void;
  maxLength?: number;
  disabled?: boolean;
  className?: string;
}

export const QuickAddInput: React.FC<QuickAddInputProps> = ({
  onAdd,
  placeholder,
  buttonTitle,
  buttonAriaLabel,
  inputAriaLabel,
  value,
  onValueChange,
  maxLength,
  disabled,
  className = ''
}) => {
  const [internalValue, setInternalValue] = useState('');
  const [salvando, setSalvando] = useState(false);

  const isControlled = value !== undefined;
  const currentValue = isControlled ? value : internalValue;

  const setCurrentValue = (next: string) => {
    if (!isControlled) {
      setInternalValue(next);
    }
    onValueChange?.(next);
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    const texto = currentValue.trim();
    if (!texto || salvando || disabled) return;

    const resultado = onAdd(texto);
    if (resultado instanceof Promise) {
      setSalvando(true);
      try {
        const ok = await resultado;
        if (ok) {
          setCurrentValue('');
        }
      } finally {
        setSalvando(false);
      }
      return;
    }

    if (resultado) {
      setCurrentValue('');
    }
  };

  return (
    <form className={`${styles.container} ${className}`} onSubmit={handleSubmit}>
      <button
        type="submit"
        className={styles.iconButton}
        disabled={!currentValue.trim() || salvando || disabled}
        title={buttonTitle}
        aria-label={buttonAriaLabel}
      >
        <Plus size={18} />
      </button>
      <input
        type="text"
        className={styles.input}
        placeholder={placeholder}
        aria-label={inputAriaLabel ?? placeholder}
        maxLength={maxLength}
        value={currentValue}
        onChange={(event) => setCurrentValue(event.target.value)}
        disabled={salvando || disabled}
      />
    </form>
  );
};
