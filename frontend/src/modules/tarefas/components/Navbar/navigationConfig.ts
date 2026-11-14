import { Calendar, CalendarDays, CheckCircle2, Clock, ListTodo, Route, Trash2 } from 'lucide-react';
import { SidebarNavItemConfig } from '@shared/design-system';
import { FiltroVisualizacao } from '../../types/tarefa';

export type NavItemConfigTarefas = SidebarNavItemConfig<FiltroVisualizacao>;

export const NAV_ITEMS_TAREFAS: readonly NavItemConfigTarefas[] = [
  {
    id: 'hoje',
    label: 'Hoje',
    mobileLabel: 'Hoje',
    title: 'Tarefas de hoje',
    icon: Clock
  },
  {
    id: 'proximas',
    label: 'Próximos Dias',
    mobileLabel: 'Próximos',
    title: 'Tarefas para os próximos dias',
    icon: Calendar
  },
  {
    id: 'concluidas',
    label: 'Concluídas',
    mobileLabel: 'Concluídas',
    title: 'Tarefas concluídas',
    icon: CheckCircle2
  },
  {
    id: 'calendario',
    label: 'Visão Semanal e Mensal',
    mobileLabel: 'Calendário',
    title: 'Visão semanal e mensal do calendário',
    icon: CalendarDays
  },
  {
    id: 'fluxo',
    label: 'Fluxo de Tarefas',
    mobileLabel: 'Fluxo',
    title: 'Mapa visual de conexões entre tarefas',
    icon: Route
  },
  {
    id: 'todas',
    label: 'Todas as Tarefas',
    mobileLabel: 'Todas',
    title: 'Todas as tarefas cadastradas',
    icon: ListTodo
  },
  {
    id: 'lixeira',
    label: 'Lixeira (30 dias)',
    mobileLabel: 'Lixeira',
    title: 'Itens excluídos nos últimos 30 dias',
    icon: Trash2
  }
];

/**
 * Itens exibidos na lista "Filtros" da sidebar desktop (exclui `hoje` e `fluxo`,
 * já destacados nos cartões no topo do pod para evitar redundância).
 */
export const NAV_ITEMS_TAREFAS_SIDEBAR: readonly NavItemConfigTarefas[] =
  NAV_ITEMS_TAREFAS.filter((item) => item.id !== 'hoje' && item.id !== 'fluxo');

