import { Ciclo, Disciplina, Trilha } from '../types/trilhara';

export function limitarPeso(peso: number): number {
  return Math.min(5, Math.max(1, Math.trunc(peso) || 1));
}

export function obterSequenciaCiclo(
  ciclo: Ciclo | undefined,
  disciplinas: Disciplina[]
): Disciplina[] {
  if (!ciclo) return [];
  const disciplinasPorId = new Map(disciplinas.map((d) => [d.id, d]));
  const participantes = (ciclo.disciplinasConfig || [])
    .filter((config) => config.participa && disciplinasPorId.has(config.disciplinaId))
    .map((config) => disciplinasPorId.get(config.disciplinaId)!);

  if (participantes.length === 0) return [];

  const pesos = participantes.map((disciplina) =>
    ciclo.tipo === 'sequencial' ? 1 : limitarPeso(disciplina.peso)
  );
  const total = pesos.reduce((acc, peso) => acc + peso, 0);
  const resultado: Disciplina[] = [];
  const alocacoes = new Array<number>(pesos.length).fill(0);

  for (let passo = 0; passo < total; passo++) {
    let selecionado = 0;
    let maiorDeficit = Number.NEGATIVE_INFINITY;
    for (let indice = 0; indice < pesos.length; indice++) {
      const deficit = ((passo + 1) * pesos[indice]) / total - alocacoes[indice];
      if (deficit > maiorDeficit) {
        maiorDeficit = deficit;
        selecionado = indice;
      }
    }
    resultado.push(participantes[selecionado]);
    alocacoes[selecionado]++;
  }

  return resultado;
}

export function obterDisciplinaAtualDoCiclo(trilha: Trilha, ciclo: Ciclo, indice: number): Disciplina | undefined {
  const sequencia = obterSequenciaCiclo(ciclo, trilha.disciplinas);
  if (sequencia.length === 0) return undefined;
  const indiceNormalizado = ((indice % sequencia.length) + sequencia.length) % sequencia.length;
  return sequencia[indiceNormalizado];
}
