import {
  BarChart2,
  BookOpen,
  Calendar,
  Route,
  Timer
} from 'lucide-react';
import { SidebarNavItemConfig } from '@shared/design-system';
import { PaginaAtiva } from '../../types/trilhara';

export type NavItemConfig = SidebarNavItemConfig<PaginaAtiva>;

export const NAV_ITEMS: readonly NavItemConfig[] = [
  {
    id: 'hub',
    label: 'Trilhas',
    mobileLabel: 'Trilhas',
    title: 'Minhas trilhas',
    icon: Route
  },
  {
    id: 'planejamento',
    label: 'Planejamento',
    mobileLabel: 'Planejar',
    title: 'Cronogramas e Planejamento',
    icon: Calendar
  },
  {
    id: 'disciplinas',
    label: 'Disciplinas',
    mobileLabel: 'Disciplinas',
    title: 'Disciplinas e Edital',
    icon: BookOpen
  },
  {
    id: 'estudar',
    label: 'Estudar',
    mobileLabel: 'Estudar',
    title: 'Sessão de Foco e Cronômetro',
    icon: Timer,
    isFocusItem: true
  },
  {
    id: 'dashboard',
    label: 'Dashboard',
    mobileLabel: 'Dashboard',
    title: 'Dashboard & Desempenho',
    icon: BarChart2
  }
];
