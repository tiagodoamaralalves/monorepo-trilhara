import React, { useEffect, useId, useMemo, useRef, useState } from 'react';
import { Calendar as CalendarIcon, ChevronLeft, ChevronRight, X } from 'lucide-react';
import styles from './DatePicker.module.css';

const MESES = [
  'Janeiro',
  'Fevereiro',
  'Março',
  'Abril',
  'Maio',
  'Junho',
  'Julho',
  'Agosto',
  'Setembro',
  'Outubro',
  'Novembro',
  'Dezembro'
];

const DIAS_SEMANA = ['D', 'S', 'T', 'Q', 'Q', 'S', 'S'];

function parseISO(dateISO?: string | null): { year: number; month: number; day: number } | null {
  if (!dateISO || !/^\d{4}-\d{2}-\d{2}$/.test(dateISO)) return null;
  const [y, m, d] = dateISO.split('-').map(Number);
  return { year: y, month: m - 1, day: d };
}

function formatISO(year: number, month: number, day: number): string {
  const y = String(year).padStart(4, '0');
  const m = String(month + 1).padStart(2, '0');
  const d = String(day).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function formatDisplayDate(dateISO?: string | null): string {
  if (!dateISO) return '';
  const parsed = parseISO(dateISO);
  if (!parsed) return '';
  const d = String(parsed.day).padStart(2, '0');
  const m = String(parsed.month + 1).padStart(2, '0');
  return `${d}/${m}/${parsed.year}`;
}

function getTodayISO(): string {
  const d = new Date();
  return formatISO(d.getFullYear(), d.getMonth(), d.getDate());
}

interface CalendarProps {
  value?: string;
  onChange?: (dateISO: string) => void;
  min?: string;
  max?: string;
  allowClear?: boolean;
  className?: string;
}

const Calendar: React.FC<CalendarProps> = ({
  value,
  onChange,
  min,
  max,
  allowClear = false,
  className = ''
}) => {
  const hoje = useMemo(() => getTodayISO(), []);
  const parsedValue = useMemo(() => parseISO(value), [value]);

  const [viewYear, setViewYear] = useState(() => parsedValue?.year ?? new Date().getFullYear());
  const [viewMonth, setViewMonth] = useState(() => parsedValue?.month ?? new Date().getMonth());

  useEffect(() => {
    if (parsedValue) {
      setViewYear(parsedValue.year);
      setViewMonth(parsedValue.month);
    }
  }, [parsedValue]);

  function voltarMes() {
    if (viewMonth === 0) {
      setViewYear((y) => y - 1);
      setViewMonth(11);
    } else {
      setViewMonth((m) => m - 1);
    }
  }

  function avancarMes() {
    if (viewMonth === 11) {
      setViewYear((y) => y + 1);
      setViewMonth(0);
    } else {
      setViewMonth((m) => m + 1);
    }
  }

  const diasGrade = useMemo(() => {
    const primeiroDiaSemana = new Date(viewYear, viewMonth, 1).getDay();
    const totalDiasMes = new Date(viewYear, viewMonth + 1, 0).getDate();
    const totalDiasMesAnterior = new Date(viewYear, viewMonth, 0).getDate();

    const itens: {
      dia: number;
      dateISO: string;
      isCurrentMonth: boolean;
      isDisabled: boolean;
      isSelected: boolean;
      isToday: boolean;
    }[] = [];

    // Dias do mês anterior para completar o início da semana
    for (let i = primeiroDiaSemana - 1; i >= 0; i--) {
      const dia = totalDiasMesAnterior - i;
      const prevYear = viewMonth === 0 ? viewYear - 1 : viewYear;
      const prevMonth = viewMonth === 0 ? 11 : viewMonth - 1;
      const dateISO = formatISO(prevYear, prevMonth, dia);
      const isDisabled = Boolean((min && dateISO < min) || (max && dateISO > max));
      itens.push({
        dia,
        dateISO,
        isCurrentMonth: false,
        isDisabled,
        isSelected: value === dateISO,
        isToday: dateISO === hoje
      });
    }

    // Dias do mês atual
    for (let dia = 1; dia <= totalDiasMes; dia++) {
      const dateISO = formatISO(viewYear, viewMonth, dia);
      const isDisabled = Boolean((min && dateISO < min) || (max && dateISO > max));
      itens.push({
        dia,
        dateISO,
        isCurrentMonth: true,
        isDisabled,
        isSelected: value === dateISO,
        isToday: dateISO === hoje
      });
    }

    // Dias do próximo mês para completar a grade
    const resto = (7 - (itens.length % 7)) % 7;
    for (let dia = 1; dia <= resto; dia++) {
      const nextYear = viewMonth === 11 ? viewYear + 1 : viewYear;
      const nextMonth = viewMonth === 11 ? 0 : viewMonth + 1;
      const dateISO = formatISO(nextYear, nextMonth, dia);
      const isDisabled = Boolean((min && dateISO < min) || (max && dateISO > max));
      itens.push({
        dia,
        dateISO,
        isCurrentMonth: false,
        isDisabled,
        isSelected: value === dateISO,
        isToday: dateISO === hoje
      });
    }

    return itens;
  }, [hoje, max, min, value, viewMonth, viewYear]);

  const canPrevMonth = useMemo(() => {
    if (!min) return true;
    const prevYear = viewMonth === 0 ? viewYear - 1 : viewYear;
    const prevMonth = viewMonth === 0 ? 11 : viewMonth - 1;
    const lastDayPrev = new Date(prevYear, prevMonth + 1, 0).getDate();
    return formatISO(prevYear, prevMonth, lastDayPrev) >= min;
  }, [min, viewMonth, viewYear]);

  const canNextMonth = useMemo(() => {
    if (!max) return true;
    const nextYear = viewMonth === 11 ? viewYear + 1 : viewYear;
    const nextMonth = viewMonth === 11 ? 0 : viewMonth + 1;
    return formatISO(nextYear, nextMonth, 1) <= max;
  }, [max, viewMonth, viewYear]);

  const podeSelecionarHoje = !((min && hoje < min) || (max && hoje > max));

  return (
    <div className={`${styles.calendar} ${className}`}>
      <div className={styles.calendarHeader}>
        <span className={styles.monthTitle}>
          {MESES[viewMonth]} <span className={styles.yearTitle}>{viewYear}</span>
        </span>
        <div className={styles.navButtons}>
          <button
            type="button"
            className={styles.navButton}
            onClick={voltarMes}
            disabled={!canPrevMonth}
            aria-label="Mês anterior"
            title="Mês anterior"
          >
            <ChevronLeft size={16} />
          </button>
          <button
            type="button"
            className={styles.navButton}
            onClick={avancarMes}
            disabled={!canNextMonth}
            aria-label="Próximo mês"
            title="Próximo mês"
          >
            <ChevronRight size={16} />
          </button>
        </div>
      </div>

      <div className={styles.weekDays}>
        {DIAS_SEMANA.map((dia, idx) => (
          <span key={idx} className={styles.weekDay}>
            {dia}
          </span>
        ))}
      </div>

      <div className={styles.daysGrid} role="grid" aria-label="Dias do mês">
        {diasGrade.map((item) => (
          <button
            key={item.dateISO}
            type="button"
            className={`
              ${styles.dayCell}
              ${!item.isCurrentMonth ? styles.otherMonth : ''}
              ${item.isToday ? styles.today : ''}
              ${item.isSelected ? styles.selected : ''}
              ${item.isDisabled ? styles.disabled : ''}
            `}
            disabled={item.isDisabled}
            onClick={() => onChange?.(item.dateISO)}
            aria-label={formatDisplayDate(item.dateISO)}
            aria-pressed={item.isSelected}
          >
            {item.dia}
          </button>
        ))}
      </div>

      <div className={styles.calendarFooter}>
        {allowClear && value ? (
          <button
            type="button"
            className={styles.footerAction}
            onClick={() => onChange?.('')}
          >
            Limpar
          </button>
        ) : (
          <span />
        )}
        <button
          type="button"
          className={styles.footerAction}
          disabled={!podeSelecionarHoje}
          onClick={() => {
            const today = getTodayISO();
            const parsed = parseISO(today);
            if (parsed) {
              setViewYear(parsed.year);
              setViewMonth(parsed.month);
            }
            onChange?.(today);
          }}
        >
          Hoje
        </button>
      </div>
    </div>
  );
};

export interface DatePickerProps {
  value?: string;
  onChange?: (dateISO: string) => void;
  min?: string;
  max?: string;
  label?: string;
  helperText?: string;
  error?: string;
  disabled?: boolean;
  placeholder?: string;
  allowClear?: boolean;
  size?: 'sm' | 'md';
  align?: 'left' | 'right';
  placement?: 'bottom' | 'top' | 'auto';
  id?: string;
  className?: string;
  'aria-label'?: string;
}

export const DatePicker: React.FC<DatePickerProps> = ({
  value = '',
  onChange,
  min,
  max,
  label,
  helperText,
  error,
  disabled = false,
  placeholder = 'DD/MM/AAAA',
  allowClear = false,
  size = 'md',
  align = 'left',
  placement = 'auto',
  id,
  className = '',
  'aria-label': ariaLabel
}) => {
  const generatedId = useId();
  const inputId = id || generatedId;
  const [aberto, setAberto] = useState(false);
  const [popoverPlacement, setPopoverPlacement] = useState<'bottom' | 'top'>('bottom');
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (aberto && containerRef.current) {
      if (placement === 'top') {
        setPopoverPlacement('top');
      } else if (placement === 'bottom') {
        setPopoverPlacement('bottom');
      } else {
        const rect = containerRef.current.getBoundingClientRect();
        const spaceBelow = window.innerHeight - rect.bottom;
        const spaceAbove = rect.top;
        if (spaceBelow < 330 && spaceAbove > 330) {
          setPopoverPlacement('top');
        } else {
          setPopoverPlacement('bottom');
        }
      }
    }
  }, [aberto, placement]);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (
        containerRef.current &&
        !containerRef.current.contains(event.target as Node)
      ) {
        setAberto(false);
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && aberto) {
        setAberto(false);
      }
    }

    if (aberto) {
      document.addEventListener('mousedown', handleClickOutside);
      document.addEventListener('keydown', handleKeyDown);
      return () => {
        document.removeEventListener('mousedown', handleClickOutside);
        document.removeEventListener('keydown', handleKeyDown);
      };
    }
  }, [aberto]);

  function handleSelectDate(dateISO: string) {
    onChange?.(dateISO);
    setAberto(false);
  }

  function handleClear(e: React.MouseEvent) {
    e.stopPropagation();
    onChange?.('');
  }

  const textoExibicao = formatDisplayDate(value);

  return (
    <div
      ref={containerRef}
      className={`
        ${styles.wrapper}
        ${size === 'sm' ? styles.wrapperSm : ''}
        ${disabled ? styles.disabledWrapper : ''}
        ${className}
      `}
    >
      {label && (
        <label htmlFor={inputId} className={styles.label}>
          {label}
        </label>
      )}

      <div className={styles.inputAnchor}>
        <div className={styles.triggerWrapper}>
          <button
            id={inputId}
            type="button"
            disabled={disabled}
            onClick={() => setAberto((prev) => !prev)}
            className={`
              ${styles.trigger}
              ${size === 'sm' ? styles.triggerSm : ''}
              ${aberto ? styles.triggerOpen : ''}
              ${error ? styles.hasError : ''}
              ${!textoExibicao ? styles.placeholder : ''}
            `}
            aria-haspopup="dialog"
            aria-expanded={aberto}
            aria-label={ariaLabel || label || 'Selecionar data'}
          >
            <span className={styles.triggerValue}>
              {textoExibicao || placeholder}
            </span>
            <div className={styles.triggerIcons}>
              {allowClear && value && !disabled && (
                <span className={styles.clearPlaceholder} aria-hidden="true" />
              )}
              <CalendarIcon size={size === 'sm' ? 14 : 16} className={styles.calendarIcon} />
            </div>
          </button>
          {allowClear && value && !disabled && (
            <button
              type="button"
              className={`${styles.clearIcon} ${size === 'sm' ? styles.clearIconSm : ''}`}
              onClick={handleClear}
              aria-label="Limpar data"
              title="Limpar data"
            >
              <X size={13} />
            </button>
          )}
        </div>

        {aberto && (
          <div
            className={`
              ${styles.popover}
              ${popoverPlacement === 'top' ? styles.popoverTop : styles.popoverBottom}
              ${align === 'right' ? styles.popoverRight : styles.popoverLeft}
            `}
            role="dialog"
            aria-label="Calendário"
          >
            <Calendar
              value={value}
              onChange={handleSelectDate}
              min={min}
              max={max}
              allowClear={allowClear}
            />
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

