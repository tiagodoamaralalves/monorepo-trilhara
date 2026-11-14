import type { Disciplina } from '../types/trilhara';

export interface ProgressoTrilhaResumo {
  totalDisciplinas: number;
  disciplinasConcluidas: number;
  disciplinasSemTopico: number;
  disciplinasSemTopicoPendentes: number;
  temDisciplinaSemTopico: boolean;
  totalTopicos: number;
  topicosConcluidos: number;
  percentualProgresso: number;
}

type DisciplinaProgressoRef = Pick<Disciplina, 'peso' | 'concluida' | 'topicos'>;

export function isDisciplinaConcluida(
  disciplina: Pick<Disciplina, 'concluida' | 'topicos'>,
  concluidos: ReadonlySet<string>
): boolean {
  return (
    Boolean(disciplina.concluida) ||
    (disciplina.topicos.length > 0 && disciplina.topicos.every((topico) => concluidos.has(topico.id)))
  );
}

/**
 * Calcula o progresso ponderado do edital usando o `peso` (1..5) de cada disciplina.
 * Disciplinas com assuntos contribuem pela fração `concluídos / total`; disciplinas sem
 * assuntos contribuem integralmente (`1` se concluída, `0` se pendente).
 */
export function calcularProgressoTrilha(
  disciplinas: ReadonlyArray<DisciplinaProgressoRef>,
  concluidos: ReadonlySet<string>
): ProgressoTrilhaResumo {
  let disciplinasConcluidas = 0;
  let disciplinasSemTopico = 0;
  let disciplinasSemTopicoPendentes = 0;
  let totalTopicos = 0;
  let topicosConcluidos = 0;
  let somaPesos = 0;
  let somaProgressoPonderado = 0;

  for (const disciplina of disciplinas) {
    const dTotal = disciplina.topicos.length;
    const dConcluidos = disciplina.topicos.filter((topico) => concluidos.has(topico.id)).length;
    const concluida = isDisciplinaConcluida(disciplina, concluidos);

    totalTopicos += dTotal;
    topicosConcluidos += dConcluidos;

    if (dTotal === 0) {
      disciplinasSemTopico += 1;
      if (!concluida) {
        disciplinasSemTopicoPendentes += 1;
      }
    }
    if (concluida) {
      disciplinasConcluidas += 1;
    }

    const pesoBruto = Number.isFinite(disciplina.peso) ? Math.round(disciplina.peso) : 1;
    const peso = Math.min(5, Math.max(1, pesoBruto));
    const progressoDisciplina = dTotal > 0 ? dConcluidos / dTotal : (concluida ? 1 : 0);

    somaPesos += peso;
    somaProgressoPonderado += peso * progressoDisciplina;
  }

  const percentualProgresso = somaPesos > 0 ? (somaProgressoPonderado / somaPesos) * 100 : 0;

  return {
    totalDisciplinas: disciplinas.length,
    disciplinasConcluidas,
    disciplinasSemTopico,
    disciplinasSemTopicoPendentes,
    temDisciplinaSemTopico: disciplinasSemTopico > 0,
    totalTopicos,
    topicosConcluidos,
    percentualProgresso
  };
}

/**
 * Formata percentuais mantendo 1 casa decimal quando `0 < pct < 1` (ex.: `0,5%`)
 * para que progressos pequenos em trilhas extensas não colapsem para `0%` ou `1%`.
 */
export function formatarPercentualEdital(percentual: number): string {
  if (!Number.isFinite(percentual) || percentual <= 0) {
    return '0%';
  }
  if (percentual >= 100) {
    return '100%';
  }
  if (percentual < 1) {
    const arredondado = Math.max(0.1, Math.round(percentual * 10) / 10);
    if (arredondado >= 1) {
      return '1%';
    }
    return `${arredondado.toLocaleString('pt-BR', {
      minimumFractionDigits: 1,
      maximumFractionDigits: 1
    })}%`;
  }
  return `${Math.round(percentual)}%`;
}
