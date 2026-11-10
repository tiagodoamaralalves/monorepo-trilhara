import React, { useEffect, useId, useRef, useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { DisciplineLabel } from '../DisciplineLabel/DisciplineLabel';
import styles from './Select.module.css';

export interface SelectOption {
  value: string;
  label: string;
  dotColor?: string;
}

export interface SelectProps extends React.AriaAttributes {
  label?: string;
  helperText?: string;
  error?: string;
  options: SelectOption[];
  value: string;
  onChange: (value: string) => void;
  id?: string;
  title?: string;
  className?: string;
  disabled?: boolean;
  required?: boolean;
}

export const Select: React.FC<SelectProps> = ({
  label,
  helperText,
  error,
  options,
  id,
  className = '',
  disabled = false,
  value,
  onChange,
  title,
  required,
  ...ariaProps
}) => {
  const generatedId = useId();
  const triggerId = id || `${generatedId}-trigger`;
  const listboxId = `${generatedId}-listbox`;
  const containerRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const optionRefs = useRef<(HTMLDivElement | null)[]>([]);
  const selectedIndex = options.findIndex((option) => option.value === value);
  const initialActiveIndex = selectedIndex < 0 ? 0 : selectedIndex;
  const [isOpen, setIsOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(initialActiveIndex);
  const selectedOption = options[selectedIndex];
  const activeOption = options[activeIndex];

  useEffect(() => {
    if (!isOpen) return;

    const handlePointerDown = (event: PointerEvent) => {
      if (event.target instanceof Node && !containerRef.current?.contains(event.target)) {
        setIsOpen(false);
      }
    };

    document.addEventListener('pointerdown', handlePointerDown);
    return () => document.removeEventListener('pointerdown', handlePointerDown);
  }, [isOpen]);

  const openAtSelection = () => {
    setActiveIndex(initialActiveIndex);
    setIsOpen(true);
  };

  const selectOption = (option: SelectOption) => {
    onChange(option.value);
    setIsOpen(false);
    triggerRef.current?.focus();
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLButtonElement>) => {
    if (event.key === 'Escape' && isOpen) {
      event.preventDefault();
      setIsOpen(false);
      return;
    }

    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (options.length === 0) return;
      if (!isOpen) {
        openAtSelection();
        return;
      }
      const direction = event.key === 'ArrowDown' ? 1 : -1;
      const nextIndex = (activeIndex + direction + options.length) % options.length;
      setActiveIndex(nextIndex);
      optionRefs.current[nextIndex]?.scrollIntoView({ block: 'nearest' });
      return;
    }

    if (event.key === 'Home' || event.key === 'End') {
      if (!isOpen || options.length === 0) return;
      event.preventDefault();
      const nextIndex = event.key === 'Home' ? 0 : options.length - 1;
      setActiveIndex(nextIndex);
      optionRefs.current[nextIndex]?.scrollIntoView({ block: 'nearest' });
      return;
    }

    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      if (!isOpen) {
        openAtSelection();
      } else if (activeOption) {
        selectOption(activeOption);
      }
    }
  };

  return (
    <div
      ref={containerRef}
      className={`${styles.wrapper} ${disabled ? styles.disabled : ''} ${className}`}
    >
      {label && (
        <label htmlFor={triggerId} className={styles.label}>
          {label}
        </label>
      )}
      <div className={`${styles.selectContainer} ${error ? styles.hasError : ''} ${isOpen ? styles.open : ''}`}>
        <button
          ref={triggerRef}
          id={triggerId}
          type="button"
          className={styles.trigger}
          role="combobox"
          aria-haspopup="listbox"
          aria-expanded={isOpen}
          aria-controls={listboxId}
          aria-activedescendant={isOpen && activeOption ? `${listboxId}-${activeIndex}` : undefined}
          aria-required={required || undefined}
          disabled={disabled}
          title={title}
          onClick={() => isOpen ? setIsOpen(false) : openAtSelection()}
          onKeyDown={handleKeyDown}
          onBlur={(event) => {
            if (
              !(event.relatedTarget instanceof Node)
              || !containerRef.current?.contains(event.relatedTarget)
            ) {
              setIsOpen(false);
            }
          }}
          {...ariaProps}
        >
          <span className={styles.selectedLabel}>
            {selectedOption?.dotColor ? (
              <DisciplineLabel name={selectedOption.label} color={selectedOption.dotColor} />
            ) : (
              selectedOption?.label || ''
            )}
          </span>
          <span className={styles.arrow} aria-hidden="true">
            <ChevronDown size={16} />
          </span>
        </button>
        {isOpen && (
          <div
            id={listboxId}
            className={styles.menu}
            role="listbox"
            aria-label={label || ariaProps['aria-label'] || 'Opções'}
          >
            {options.map((option, index) => (
              <div
                key={option.value}
                ref={(el) => {
                  optionRefs.current[index] = el;
                }}
                id={`${listboxId}-${index}`}
                className={`${styles.menuOption} ${activeIndex === index ? styles.menuOptionActive : ''} ${value === option.value ? styles.menuOptionSelected : ''}`}
                role="option"
                aria-selected={value === option.value}
                onMouseDown={(event) => event.preventDefault()}
                onMouseMove={() => setActiveIndex(index)}
                onClick={() => selectOption(option)}
              >
                {option.dotColor ? (
                  <DisciplineLabel name={option.label} color={option.dotColor} />
                ) : (
                  option.label
                )}
              </div>
            ))}
          </div>
        )}
      </div>
      {(error || helperText) && (
        <span className={error ? styles.errorText : styles.helperText}>
          {error || helperText}
        </span>
      )}
    </div>
  );
};
