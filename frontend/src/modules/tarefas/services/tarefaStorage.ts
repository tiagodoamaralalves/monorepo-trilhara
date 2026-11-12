import { Tarefa, Prioridade } from '../types/tarefa';
import { hojeISO } from '../utils/date';
import { TrilhaService } from './trilhaService';

const STORAGE_KEY = 'trilhara_tarefas_v1';

function isTarefa(value: unknown): value is Tarefa {
  if (typeof value !== 'object' || value === null) return false;
  const tarefa = value as Record<string, unknown>;
  return (
    typeof tarefa.id === 'string' &&
    typeof tarefa.titulo === 'string' &&
    typeof tarefa.data === 'string' &&
    typeof tarefa.concluida === 'boolean' &&
    ['baixa', 'media', 'alta', 'urgente'].includes(String(tarefa.prioridade)) &&
    ['manual', 'ciclo'].includes(String(tarefa.origem))
  );
}

function gerarTarefasIniciais(): Tarefa[] {
  const hoje = hojeISO();
  return [
    {
      id: 'task-ini-1',
      titulo: 'Planejar metas da semana e revisar ciclo',
      descricao: 'Verificar horários livres para blocos de estudo e compromissos.',
      data: hoje,
      horario: '08:30',
      duracaoMin: 30,
      concluida: false,
      prioridade: 'ALTA' as Prioridade,
      origem: 'manual'
    },
    {
      id: 'task-ini-2',
      titulo: 'Comprar caderno para resumos e questões',
      data: hoje,
      horario: '18:00',
      duracaoMin: 20,
      concluida: false,
      prioridade: 'BAIXA' as Prioridade,
      origem: 'manual'
    }
  ];
}

export const TarefaStorage = {
  listar(): Tarefa[] {
    try {
      const raw = window.localStorage.getItem(STORAGE_KEY);
      if (!raw) {
        const iniciais = gerarTarefasIniciais();
        this.salvar(iniciais);
        return iniciais;
      }

      const tarefas: unknown = JSON.parse(raw);
      if (!Array.isArray(tarefas) || !tarefas.every(isTarefa)) {
        throw new Error('Os dados locais das tarefas possuem formato inválido.');
      }
      return tarefas;
    } catch (error) {
      if (error instanceof SyntaxError) {
        throw new Error('Os dados locais das tarefas estão corrompidos.');
      }
      if (error instanceof Error) throw error;
      throw new Error('Não foi possível ler as tarefas do armazenamento local.');
    }
  },

  salvar(tarefas: Tarefa[]): void {
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(tarefas));
    } catch {
      throw new Error('Não foi possível salvar as tarefas no armazenamento local.');
    }
  },

  adicionar(tarefa: Omit<Tarefa, 'id' | 'concluida'>): Tarefa {
    const lista = this.listar();
    const nova: Tarefa = {
      ...tarefa,
      id: 'task_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
      concluida: false,
      concluidaEm: null
    };
    lista.unshift(nova);
    this.salvar(lista);
    return nova;
  },

  atualizar(id: string, updates: Partial<Tarefa>): Tarefa | null {
    const lista = this.listar();
    const idx = lista.findIndex((t) => t.id === id);
    if (idx === -1) return null;

    const atualizada = { ...lista[idx], ...updates };
    lista[idx] = atualizada;
    this.salvar(lista);
    return atualizada;
  },

  async alternarConclusao(id: string): Promise<Tarefa | null> {
    const lista = this.listar();
    const idx = lista.findIndex((t) => t.id === id);
    if (idx === -1) return null;

    const tarefa = lista[idx];
    const novoStatus = !tarefa.concluida;
    const concluidaEm = novoStatus ? new Date().toISOString() : null;

    const atualizada: Tarefa = {
      ...tarefa,
      concluida: novoStatus,
      concluidaEm
    };

    // Tarefas de ciclo atualizam a conclusão remota; concluir também registra a sessão.
    if (tarefa.origem === 'ciclo' && tarefa.trilhaId && tarefa.cicloId && tarefa.ocorrenciaId) {
      if (novoStatus && tarefa.disciplinaId) {
        await TrilhaService.registrarSessaoEstudo(
          tarefa.trilhaId,
          {
            disciplinaId: tarefa.disciplinaId,
            inicio: new Date().toISOString(),
            minutos: tarefa.duracaoMin || 50,
            tipo: 'ESTUDO'
          },
          tarefa.ocorrenciaId
        );
      }
      await TrilhaService.atualizarConclusaoBloco(
        tarefa.trilhaId,
        tarefa.cicloId,
        tarefa.ocorrenciaId,
        tarefa.data,
        novoStatus
      );
    }

    lista[idx] = atualizada;
    this.salvar(lista);
    return atualizada;
  },

  remover(id: string): boolean {
    const lista = this.listar();
    const filtrada = lista.filter((t) => t.id !== id);
    if (filtrada.length === lista.length) return false;
    this.salvar(filtrada);
    return true;
  }
};
