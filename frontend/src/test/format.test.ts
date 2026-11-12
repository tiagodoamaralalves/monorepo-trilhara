import { describe, it, expect } from 'vitest';
import { formatarDuracao, formatarMinutos, formatarRelogio } from '../shared/format/duracoes';
import { formatarDataCurta, formatarDataLonga, formatarDataCompacta, hojeISO, diasAte } from '../shared/format/datas';
import { formatarPercentual } from '../shared/format/numeros';

describe('R1 — formatarDuracao (D-42)', () => {
  it('45 segundos vira "45 s"', () => {
    expect(formatarDuracao(45)).toBe('45 s');
  });

  it('720 segundos vira "12 min"', () => {
    expect(formatarDuracao(720)).toBe('12 min');
  });

  it('3900 segundos vira "1 h 05 min"', () => {
    expect(formatarDuracao(3900)).toBe('1 h 05 min');
  });

  it('hora exata e zero', () => {
    expect(formatarDuracao(3600)).toBe('1 h');
    expect(formatarDuracao(0)).toBe('0 min');
    expect(formatarDuracao(null)).toBe('0 min');
  });

  it('formatarMinutos usa a mesma regra', () => {
    expect(formatarMinutos(12)).toBe('12 min');
    expect(formatarMinutos(65)).toBe('1 h 05 min');
  });

  it('formatarRelogio produz mm:ss e hh:mm:ss', () => {
    expect(formatarRelogio(59)).toBe('00:59');
    expect(formatarRelogio(605)).toBe('10:05');
    expect(formatarRelogio(3661)).toBe('01:01:01');
  });
});

describe('R1 — datas de calendário sem deslocamento de fuso (D-42)', () => {
  it('2026-03-01 continua 01/03/2026 (não vira 28/02)', () => {
    expect(formatarDataCurta('2026-03-01')).toBe('01/03/2026');
    expect(formatarDataLonga('2026-03-01')).toBe('1 de março de 2026');
    expect(formatarDataCompacta('2026-03-01')).toBe('01 mar');
  });

  it('datas inválidas caem no texto alternativo', () => {
    expect(formatarDataCurta(null)).toBe('Não definida');
    expect(formatarDataCurta('2026-13-01')).toBe('Não definida');
    expect(formatarDataCompacta('')).toBe('—');
  });

  it('hojeISO usa a data local e diasAte compara calendários', () => {
    const hoje = hojeISO(new Date(2026, 2, 1, 23, 30));
    expect(hoje).toBe('2026-03-01');
    expect(diasAte('2026-03-03', hoje)).toBe(2);
    expect(diasAte('2026-02-28', hoje)).toBe(-1);
    expect(diasAte(null, hoje)).toBeNull();
  });
});

describe('R1 — percentuais', () => {
  it('usa 0 casas decimais', () => {
    expect(formatarPercentual(72.5)).toBe('73%');
    expect(formatarPercentual(0)).toBe('0%');
    expect(formatarPercentual(undefined)).toBe('0%');
  });
});
