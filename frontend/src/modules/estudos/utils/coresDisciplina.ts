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
