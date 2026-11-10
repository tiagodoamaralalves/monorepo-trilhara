import React from 'react';
import styles from './Field.module.css';

export interface FieldProps {
  label: string;
  htmlFor: string;
  helperText?: string;
  error?: string;
  children: React.ReactNode;
  className?: string;
}

export const Field: React.FC<FieldProps> = ({
  label,
  htmlFor,
  helperText,
  error,
  children,
  className = ''
}) => (
  <div className={`${styles.field} ${className}`}>
    <label htmlFor={htmlFor} className={styles.label}>{label}</label>
    {children}
    {(error || helperText) && (
      <span className={error ? styles.error : styles.help}>
        {error || helperText}
      </span>
    )}
  </div>
);
