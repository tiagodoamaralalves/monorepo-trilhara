import React from 'react';
import { AppSidebar } from '@shared/design-system';
import { UsuarioAutenticado } from '@shared/auth';
import { FiltroVisualizacao, ListaTarefas, CicloAtivoInfo } from '../../types/tarefa';
import { TarefaContextPod } from './TarefaContextPod';
import { MinhasListasNav, TrilhaComCronogramaInfo } from './MinhasListasNav';
import { MobileTarefaControls } from './MobileTarefaControls';

export interface NavbarProps {
  filtroAtivo: FiltroVisualizacao;
  usuario?: UsuarioAutenticado | null;
  listas: ListaTarefas[];
  listaSelecionadaId?: string;
  onSelecionarLista: (id?: string) => void;
  onAbrirNovaLista: () => void;
  onAbrirEditarLista?: (lista: ListaTarefas) => void;
  onExcluirLista?: (id: string) => void;
  ciclosAtivos?: CicloAtivoInfo[];
  mostrarCicloEstudos?: boolean;
  onToggleMostrarCiclo?: () => void;
  trilhasComCronograma?: TrilhaComCronogramaInfo[];
  trilhaSelecionadaId?: string;
  onSelecionarTrilha?: (id?: string) => void;
  contadores: {
    hoje: number;
    concluidasHoje?: number;
    totalHoje?: number;
    proximas: number;
    concluidas: number;
    total: number;
  };
  contadoresPorLista?: Record<string, number>;
  onNavegar: (filtro: FiltroVisualizacao) => void;
  onAbrirNovaTarefa: () => void;
  onAbrirLogin?: () => void;
  onLogout?: () => void;
  onAbrirConta?: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({
  filtroAtivo,
  usuario,
  listas,
  listaSelecionadaId,
  onSelecionarLista,
  onAbrirNovaLista,
  onAbrirEditarLista,
  onExcluirLista,
  ciclosAtivos = [],
  mostrarCicloEstudos = false,
  onToggleMostrarCiclo = () => {},
  trilhasComCronograma = [],
  trilhaSelecionadaId,
  onSelecionarTrilha,
  contadores,
  contadoresPorLista,
  onNavegar,
  onAbrirNovaTarefa,
  onAbrirLogin,
  onLogout,
  onAbrirConta
}) => {
  return (
    <AppSidebar<FiltroVisualizacao>
      brand={{
        title: 'Trilhara',
        subtitle: 'Tarefas & Agenda',
        logoSrc: '/favicon.svg',
        onClick: () => onNavegar('hoje'),
        titleAttr: 'Ir para tarefas de hoje'
      }}
      user={
        usuario
          ? {
              nome: usuario.nome,
              email: usuario.email
            }
          : null
      }
      onLogout={onLogout}
      onLogin={onAbrirLogin}
      onOpenAccount={onAbrirConta}
      contextSlot={
        <TarefaContextPod
          onAbrirNovaTarefa={onAbrirNovaTarefa}
          filtroAtivo={filtroAtivo}
          listaSelecionadaId={listaSelecionadaId}
          onNavegar={onNavegar}
          onSelecionarLista={onSelecionarLista}
          contadores={contadores}
        />
      }
      mobileControlsSlot={
        <MobileTarefaControls
          onAbrirNovaTarefa={onAbrirNovaTarefa}
        />
      }
    >
      {/* ── ÁREA DE MINHAS LISTAS E TRILHAS DE ESTUDO (FASE 7) ── */}
      <MinhasListasNav
        listas={listas}
        listaSelecionadaId={listaSelecionadaId}
        onSelecionarLista={onSelecionarLista}
        onAbrirNovaLista={onAbrirNovaLista}
        onAbrirEditarLista={onAbrirEditarLista}
        onExcluirLista={onExcluirLista}
        ciclosAtivos={ciclosAtivos}
        mostrarCicloEstudos={mostrarCicloEstudos}
        onToggleMostrarCiclo={onToggleMostrarCiclo}
        contadoresPorLista={contadoresPorLista}
        trilhasComCronograma={trilhasComCronograma}
        trilhaSelecionadaId={trilhaSelecionadaId}
        onSelecionarTrilha={onSelecionarTrilha}
      />
    </AppSidebar>
  );
};
