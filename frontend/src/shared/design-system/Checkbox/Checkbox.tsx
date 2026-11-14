import React, { useEffect, useRef } from 'react';
import { Check, Minus } from 'lucide-react';
import styles from './Checkbox.module.css';

export interface CheckboxProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label?: React.ReactNode;
  description?: React.ReactNode;
  indeterminate?: boolean;
  strikeThroughOnChecked?: boolean;
}

export const Checkbox: React.FC<CheckboxProps> = ({
  label,
  description,
  checked = false,
  disabled = false,
  className = '',
  onChange,
  indeterminate = false,
  strikeThroughOnChecked = false,
  id,
  ...props
}) => {
  const generatedId = React.useId();
  const inputId = id || generatedId;
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (inputRef.current) inputRef.current.indeterminate = indeterminate;
  }, [indeterminate]);

  const marcado = Boolean(checked || indeterminate);

  return (
    <label
      htmlFor={inputId}
      className={`${styles.container} ${disabled ? styles.disabled : ''} ${className}`}
    >
      <div className={styles.checkboxWrapper}>
        <input
          type="checkbox"
          id={inputId}
          checked={checked}
          disabled={disabled}
          onChange={onChange}
          ref={inputRef}
          className={styles.nativeInput}
          {...props}
        />
        <div
          className={`${styles.box} ${marcado ? styles.checked : ''} ${disabled ? styles.disabledBox : ''}`}
          aria-hidden="true"
        >
          {!checked && indeterminate ? (
            <Minus size={12} className={styles.checkIcon} strokeWidth={2.75} />
          ) : (
            <Check
              size={12}
              className={checked ? styles.checkIcon : styles.idleIcon}
              strokeWidth={checked ? 2.75 : 2.2}
            />
          )}
        </div>
      </div>
      {(label || description) && (
        <div className={styles.labelGroup}>
          {label && (
            <span className={`${styles.label} ${checked && strikeThroughOnChecked ? styles.labelChecked : ''}`}>
              {label}
            </span>
          )}
          {description && <span className={styles.desc}>{description}</span>}
        </div>
      )}
    </label>
  );
};

export interface CheckCircleButtonProps
  extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'type'> {
  checked?: boolean;
  size?: 'sm' | 'md';
}

export const CheckCircleButton: React.FC<CheckCircleButtonProps> = ({
  checked = false,
  disabled = false,
  size = 'md',
  className = '',
  ...props
}) => {
  const iconSize = size === 'md' ? 13 : 12;

  return (
    <button
      type="button"
      disabled={disabled}
      className={`${styles.circleButton} ${className}`}
      {...props}
    >
      <span
        className={`${styles.box} ${size === 'md' ? styles.boxMd : ''} ${checked ? styles.checked : ''}`}
        aria-hidden="true"
      >
        <Check
          size={iconSize}
          className={checked ? styles.checkIcon : styles.idleIcon}
          strokeWidth={checked ? 2.75 : 2.2}
        />
      </span>
    </button>
  );
};
