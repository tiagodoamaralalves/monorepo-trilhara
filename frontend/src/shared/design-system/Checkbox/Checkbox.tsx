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
        <div className={`${styles.box} ${checked || indeterminate ? styles.checked : ''}`}>
          {checked && <Check size={14} className={styles.checkIcon} strokeWidth={3} />}
          {!checked && indeterminate && <Minus size={14} className={styles.checkIcon} strokeWidth={3} />}
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
