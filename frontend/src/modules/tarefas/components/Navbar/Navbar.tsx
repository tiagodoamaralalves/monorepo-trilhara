import React from 'react';
import { AppSidebar } from '@shared/design-system';
import { UsuarioAutenticado } from '@shared/auth';
import { FiltroVisualizacao, ListaTarefas } from '../../types/tarefa';
import { TarefaContextPod } from './TarefaContextPod';
import { MinhasListasNav, TrilhaComCronogramaInfo } from './MinhasListasNav';
import { MobileTarefaControls } from './MobileTarefaControls';
import { NAV_ITEMS_TAREFAS, NAV_ITEMS_TAREFAS_SIDEBAR } from './navigationConfig';

export interface NavbarProps {
  filtroAtivo: FiltroVisualizacao;
  usuario?: UsuarioAutenticado | null;
  listas: ListaTarefas[];
  listaSelecionadaId?: string;
  onSelecionarLista: (id?: string) => void;
  onAbrirNovaLista: () => void;
  onAbrirEditarLista?: (lista: ListaTarefas) => void;
  onExcluirLista?: (id: string) => void;
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
    noFluxo?: number;
  };
  contadoresPorLista?: Record<string, number>;
  onNavegar: (filtro: FiltroVisualizacao) => void;
  onAbrirNovaTarefa: () => void;
  onAbrirLogin?: () => void;
  onSettings?: () => void;
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
  trilhasComCronograma = [],
  trilhaSelecionadaId,
  onSelecionarTrilha,
  contadores,
  contadoresPorLista,
  onNavegar,
  onAbrirNovaTarefa,
  onAbrirLogin,
  onSettings
}) => {
  const handleNavegarFiltro = (filtro: FiltroVisualizacao) => {
    onSelecionarTrilha?.(undefined);
    onSelecionarLista(undefined);
    onNavegar(filtro);
  };

  const getBadgeForItem = (id: FiltroVisualizacao): number | undefined => {
    if (id === 'proximas') return contadores.proximas > 0 ? contadores.proximas : undefined;
    if (id === 'concluidas') return contadores.concluidas > 0 ? contadores.concluidas : undefined;
    if (id === 'todas') return contadores.total > 0 ? contadores.total : undefined;
    return undefined;
  };

  return (
    <AppSidebar<FiltroVisualizacao>
      brand={{
        title: 'Trilhara',
        subtitle: 'Tarefas & Agenda',
        logoSrc: '/favicon.svg',
        onClick: () => handleNavegarFiltro('hoje'),
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
      onSettings={onSettings}
      settingsLabel="Configurar lembretes"
      onLogin={onAbrirLogin}
      contextSlot={
        <TarefaContextPod
          onAbrirNovaTarefa={onAbrirNovaTarefa}
          filtroAtivo={filtroAtivo}
          temListaOuTrilhaAtiva={Boolean(listaSelecionadaId || trilhaSelecionadaId)}
          onNavegar={handleNavegarFiltro}
          contadores={contadores}
        />
      }
      navLabel="Filtros"
      items={NAV_ITEMS_TAREFAS_SIDEBAR}
      mobileItems={NAV_ITEMS_TAREFAS}
      activeId={listaSelecionadaId || trilhaSelecionadaId ? null : filtroAtivo}
      onNavigate={handleNavegarFiltro}
      getBadge={getBadgeForItem}
      mobileControlsSlot={
        <MobileTarefaControls
          onAbrirNovaTarefa={onAbrirNovaTarefa}
        />
      }
    >
      {/* ── ÁREA DE MINHAS LISTAS E TRILHAS DE ESTUDO ── */}
      <MinhasListasNav
        listas={listas}
        listaSelecionadaId={listaSelecionadaId}
        onSelecionarLista={onSelecionarLista}
        onAbrirNovaLista={onAbrirNovaLista}
        onAbrirEditarLista={onAbrirEditarLista}
        onExcluirLista={onExcluirLista}
        contadoresPorLista={contadoresPorLista}
        trilhasComCronograma={trilhasComCronograma}
        trilhaSelecionadaId={trilhaSelecionadaId}
        onSelecionarTrilha={onSelecionarTrilha}
      />
    </AppSidebar>
  );
};
