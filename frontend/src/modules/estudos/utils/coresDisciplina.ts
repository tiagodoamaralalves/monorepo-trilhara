import {
  obterCorDisponivel as sharedObterCorDisponivel,
  obterCoresEmUso as sharedObterCoresEmUso
} from '@shared/design-system';
import { Disciplina } from '../types/trilhara';

export function obterCoresEmUso(
  disciplinas: readonly Disciplina[],
  ignorarDisciplinaId?: string
): Map<string, string> {
  return sharedObterCoresEmUso(disciplinas, ignorarDisciplinaId);
}

export function obterCorDisponivel(
  disciplinas: readonly Disciplina[],
  ignorarDisciplinaId?: string
): string {
  return sharedObterCorDisponivel(disciplinas, ignorarDisciplinaId);
}

export function criarIdDisciplina(prefixo: string, idsUsados: Set<string>): string {
  const base = `${prefixo}_${Date.now().toString(36)}`;
  let sufixo = 1;
  while (idsUsados.has(`${base}_${sufixo}`)) sufixo += 1;
  const id = `${base}_${sufixo}`;
  idsUsados.add(id);
  return id;
}
