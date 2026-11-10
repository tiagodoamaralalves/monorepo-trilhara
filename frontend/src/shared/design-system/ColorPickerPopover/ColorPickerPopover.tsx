import React, { useEffect, useRef, useState } from 'react';
import { X } from 'lucide-react';
import { Button } from '../Button/Button';
import { ColorPicker, DISCIPLINE_COLORS } from '../Controls/Controls';
import styles from './ColorPickerPopover.module.css';

export interface ColorPickerPopoverProps {
  color: string;
  onChangeColor: (color: string) => void;
  usedColors?: ReadonlyMap<string, string>;
  title?: string;
  eyebrow?: string;
  description?: string;
  disabled?: boolean;
  buttonSize?: number;
  swatchSize?: number;
  align?: 'left' | 'right';
  className?: string;
  onOpenChange?: (open: boolean) => void;
}

export const ColorPickerPopover: React.FC<ColorPickerPopoverProps> = ({
  color,
  onChangeColor,
  usedColors,
  title = 'Cor de identificação',
  eyebrow = 'Aparência',
  description = 'Escolha uma cor para identificar este item com facilidade.',
  disabled = false,
  buttonSize = 30,
  swatchSize = 18,
  align = 'left',
  className = '',
  onOpenChange
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const updateIsOpen = (next: boolean | ((prev: boolean) => boolean)) => {
    setIsOpen((prev) => {
      const resolved = typeof next === 'function' ? next(prev) : next;
      onOpenChange?.(resolved);
      return resolved;
    });
  };

  useEffect(() => {
    if (!isOpen) return;

    const handlePointerDown = (event: PointerEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        updateIsOpen(false);
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        updateIsOpen(false);
      }
    };

    document.addEventListener('pointerdown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen]);

  const corValida = color || DISCIPLINE_COLORS[0];

  return (
    <div className={`${styles.wrapper} ${className}`} ref={containerRef}>
      {/* Botão circular com a cor atual */}
      <button
        type="button"
        className={`${styles.triggerButton} ${isOpen ? styles.triggerButtonActive : ''}`}
        style={{ width: `${buttonSize}px`, height: `${buttonSize}px` }}
        onClick={() => !disabled && updateIsOpen((prev) => !prev)}
        disabled={disabled}
        aria-label={disabled ? `Cor: ${corValida}` : 'Alterar cor de identificação'}
        title={disabled ? undefined : 'Clique para alterar a cor'}
      >
        <span
          className={styles.colorSwatch}
          style={{
            width: `${swatchSize}px`,
            height: `${swatchSize}px`,
            backgroundColor: corValida
          }}
          aria-hidden="true"
        />
      </button>

      {/* Popover flutuante com a grade de 27 cores */}
      {isOpen && (
        <div
          className={`${styles.popover} ${align === 'right' ? styles.popoverAlignRight : ''}`}
          role="dialog"
          aria-modal="true"
        >
          <div className={styles.popoverHeader}>
            <div className={styles.popoverHeaderText}>
              <span className={styles.eyebrow}>{eyebrow}</span>
              <span className={styles.title}>{title}</span>
            </div>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              iconOnly
              onClick={() => updateIsOpen(false)}
              aria-label="Fechar"
            >
              <X size={18} />
            </Button>
          </div>

          <p className={styles.description}>{description}</p>

          <div className={styles.selectedColorPreview}>
            <span
              className={styles.previewSwatch}
              style={{ backgroundColor: corValida }}
              aria-hidden="true"
            />
            <span>
              Cor atual: <strong>{corValida.toUpperCase()}</strong>
            </span>
          </div>

          <ColorPicker
            value={corValida}
            onChange={(novaCor) => {
              onChangeColor(novaCor);
            }}
            usedColors={usedColors}
            compact
            label="Escolha uma cor"
          />
        </div>
      )}
    </div>
  );
};

