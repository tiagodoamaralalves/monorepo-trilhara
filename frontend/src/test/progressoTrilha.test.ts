import { describe, expect, it } from 'vitest';
import {
  calcularProgressoTrilha,
  formatarPercentualEdital,
  isDisciplinaConcluida
} from '../modules/estudos/utils/progressoTrilha';
import type { DisciplinaDTO } from '@shared/contracts';

function criarDisciplina(
  peso: number,
  concluida: boolean,
  topicosIds: string[] = []
): DisciplinaDTO {
  return {
    id: `d-${Math.random().toString(36).slice(2)}`,
    nome: 'Disciplina',
    peso,
    concluida,
    topicos: topicosIds.map((id, idx) => ({ id, nome: id, ordem: idx + 1 }))
  };
}

describe('progressoTrilha — cálculo ponderado pelo peso da disciplina', () => {
  it('calcula 0,5% ao concluir 50% de 1 disciplina peso 1 em trilha com 100 disciplinas peso 1', () => {
    const disciplinas: DisciplinaDTO[] = [
      criarDisciplina(1, false, ['top-1', 'top-2']),
      ...Array.from({ length: 99 }, () => criarDisciplina(1, false))
    ];

    const resumo = calcularProgressoTrilha(disciplinas, new Set(['top-1']));

    expect(resumo.percentualProgresso).toBeCloseTo(0.5, 4);
    expect(formatarPercentualEdital(resumo.percentualProgresso)).toBe('0,5%');
    expect(resumo.totalTopicos).toBe(2);
    expect(resumo.topicosConcluidos).toBe(1);
    expect(resumo.disciplinasConcluidas).toBe(0);
    expect(resumo.temDisciplinaSemTopico).toBe(true);
  });

  it('calcula 90% quando 9 de 10 disciplinas sem tópicos estão concluídas', () => {
    const disciplinas: DisciplinaDTO[] = [
      ...Array.from({ length: 9 }, () => criarDisciplina(1, true)),
      criarDisciplina(1, false)
    ];

    const resumo = calcularProgressoTrilha(disciplinas, new Set());

    expect(resumo.percentualProgresso).toBeCloseTo(90, 4);
    expect(formatarPercentualEdital(resumo.percentualProgresso)).toBe('90%');
    expect(resumo.disciplinasConcluidas).toBe(9);
    expect(resumo.disciplinasSemTopicoPendentes).toBe(1);
    expect(resumo.totalTopicos).toBe(0);
  });

  it('não exibe 100% quando há 10 disciplinas sem tópicos pendentes e apenas 1 com tópicos concluída', () => {
    const disciplinas: DisciplinaDTO[] = [
      criarDisciplina(5, false, ['t1', 't2']),
      ...Array.from({ length: 10 }, () => criarDisciplina(1, false))
    ];

    const resumo = calcularProgressoTrilha(disciplinas, new Set(['t1', 't2']));

    // peso 5 / (10 * 1 + 5) = 33.333%
    expect(resumo.percentualProgresso).toBeCloseTo(100 / 3, 4);
    expect(formatarPercentualEdital(resumo.percentualProgresso)).toBe('33%');
    expect(resumo.disciplinasConcluidas).toBe(1);
    expect(resumo.disciplinasSemTopicoPendentes).toBe(10);
    expect(resumo.temDisciplinaSemTopico).toBe(true);
  });

  it('pondera corretamente quando todas as disciplinas possuem tópicos', () => {
    const disciplinas: DisciplinaDTO[] = [
      criarDisciplina(2, false, ['a1', 'a2']),
      criarDisciplina(3, false, ['b1'])
    ];

    // (2 * 0.5 + 3 * 1) / 5 = 80%
    const resumo = calcularProgressoTrilha(disciplinas, new Set(['a1', 'b1']));

    expect(resumo.percentualProgresso).toBeCloseTo(80, 4);
    expect(resumo.temDisciplinaSemTopico).toBe(false);
    expect(isDisciplinaConcluida(disciplinas[0], new Set(['a1', 'b1']))).toBe(false);
    expect(isDisciplinaConcluida(disciplinas[1], new Set(['a1', 'b1']))).toBe(true);
  });

  it('formata extremos de percentual com segurança', () => {
    expect(formatarPercentualEdital(0)).toBe('0%');
    expect(formatarPercentualEdital(-5)).toBe('0%');
    expect(formatarPercentualEdital(0.02)).toBe('0,1%');
    expect(formatarPercentualEdital(100)).toBe('100%');
  });
});
