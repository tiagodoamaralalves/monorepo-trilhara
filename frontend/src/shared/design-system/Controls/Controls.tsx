import React from 'react';
import { Button } from '../Button/Button';
import styles from './Controls.module.css';

interface ToggleOption<T extends string> {
  value: T;
  label: string;
  icon?: React.ReactNode;
  disabled?: boolean;
}

export interface ToggleGroupProps<T extends string> {
  options: readonly ToggleOption<T>[];
  value: T;
  onChange: (value: T) => void;
  ariaLabel: string;
  className?: string;
}

export function ToggleGroup<const T extends string>({
  options,
  value,
  onChange,
  ariaLabel,
  className = ''
}: ToggleGroupProps<T>) {
  return (
    <div className={`${styles.toggleGroup} ${className}`} role="group" aria-label={ariaLabel}>
      {options.map((option) => (
        <Button
          key={option.value}
          type="button"
          variant="ghost"
          size="sm"
          selected={value === option.value}
          disabled={option.disabled}
          leftIcon={option.icon}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </Button>
      ))}
    </div>
  );
}


export interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  helperText?: string;
  error?: string;
}

export const Textarea: React.FC<TextareaProps> = ({
  label,
  helperText,
  error,
  id,
  className = '',
  ...props
}) => {
  const generatedId = React.useId();
  const inputId = id || generatedId;

  return (
    <div className={styles.field}>
      {label && <label htmlFor={inputId} className={styles.label}>{label}</label>}
      <textarea
        {...props}
        id={inputId}
        className={`${styles.textarea} ${error ? styles.invalid : ''} ${className}`}
        aria-invalid={error ? true : undefined}
      />
      {(error || helperText) && (
        <span className={error ? styles.error : styles.help}>{error || helperText}</span>
      )}
    </div>
  );
};

export interface SwitchProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
  disabled?: boolean;
}

export const Switch: React.FC<SwitchProps> = ({ checked, onChange, label, disabled = false }) => (
  <button
    type="button"
    role="switch"
    aria-checked={checked}
    className={`${styles.switch} ${checked ? styles.switchOn : ''}`}
    disabled={disabled}
    onClick={() => onChange(!checked)}
  >
    <span className={styles.switchThumb} />
    <span>{label}</span>
  </button>
);

export const DISCIPLINE_COLORS = [
  // Linha 1: Tons vibrantes e abertos (9 cores)
  '#dc2626', '#ea580c', '#d97706', '#ca8a04', '#65a30d', '#16a34a', '#0d9488', '#0891b2', '#0284c7',
  // Linha 2: Tons médios, frios e rosados (9 cores)
  '#2563eb', '#4f46e5', '#6d28d9', '#9333ea', '#c026d3', '#db2777', '#e11d48', '#9f1239', '#831843',
  // Linha 3: Tons profundos e terrosos (9 cores)
  '#7f1d1d', '#7c2d12', '#78350f', '#713f12', '#365314', '#14532d', '#134e4a', '#1e3a8a', '#581c87'
] as const;

export interface ColorPickerProps {
  value: string;
  onChange: (color: string) => void;
  label?: string;
  colors?: readonly string[];
  usedColors?: ReadonlyMap<string, string>;
  compact?: boolean;
}

export const ColorPicker: React.FC<ColorPickerProps> = ({
  value,
  onChange,
  label = 'Cor da disciplina',
  colors = DISCIPLINE_COLORS,
  usedColors,
  compact = false
}) => {
  const disciplinaUsandoCor = usedColors?.get(value.toLowerCase());

  return (
    <div className={`${styles.field} ${compact ? styles.colorPickerCompact : ''}`}>
      <span className={styles.label}>{label}</span>
      <div className={`${styles.colorGrid} ${compact ? styles.colorGridCompact : ''}`} role="group" aria-label={label}>
        {colors.map((color) => {
          const disciplinaUsando = usedColors?.get(color.toLowerCase());
          return (
            <Button
              key={color}
              type="button"
              variant="ghost"
              size="sm"
              iconOnly
              selected={value.toLowerCase() === color.toLowerCase()}
              className={[
                styles.colorOption,
                compact ? styles.colorOptionCompact : '',
                disciplinaUsando ? styles.colorOptionUsed : ''
              ].filter(Boolean).join(' ')}
              style={{ '--swatch-color': color } as React.CSSProperties}
              onClick={() => onChange(color)}
              aria-label={`Selecionar cor ${color}${disciplinaUsando ? `, já usada por ${disciplinaUsando}` : ''}`}
              title={disciplinaUsando ? `${color} · usada por ${disciplinaUsando}` : color}
            >
              {value.toLowerCase() === color.toLowerCase() ? '✓' : ''}
            </Button>
          );
        })}
      </div>
      {disciplinaUsandoCor && (
        <span className={styles.help}>Esta cor já está sendo usada por {disciplinaUsandoCor}.</span>
      )}
    </div>
  );
};

export interface ProgressBarProps {
  value: number;
  max?: number;
  label?: string;
  color?: string;
  size?: 'sm' | 'md';
  variant?: 'solid' | 'gradient';
  className?: string;
}

export const ProgressBar: React.FC<ProgressBarProps> = ({
  value,
  max = 100,
  label = 'Progresso',
  color = 'var(--primary)',
  size = 'md',
  variant = 'solid',
  className = ''
}) => {
  const progress = max > 0 ? Math.min(100, Math.max(0, (value / max) * 100)) : 0;
  const classNames = [
    styles.progress,
    size === 'sm' ? styles.progressSm : '',
    variant === 'gradient' ? styles.progressGradient : '',
    className
  ].filter(Boolean).join(' ');
  const style = {
    '--progress': `${progress}%`,
    '--progress-color': color
  } as React.CSSProperties;

  return (
    <div
      className={classNames}
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={max}
      aria-valuenow={Math.min(max, Math.max(0, value))}
    >
      <span className={styles.progressValue} style={style} />
    </div>
  );
};

