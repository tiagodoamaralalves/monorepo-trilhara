import React, { useMemo } from 'react';
import { AppSidebar } from '@shared/design-system';
import { TrilhaResumoDTO, TrilhaDetalheDTO, PaginaAtiva, UsuarioAutenticado } from '../../types/trilhara';
import { useStudyTimer } from '@shared/timers/estudoTimer';
import { formatarSegundos } from '../../utils/format';
import { NAV_ITEMS } from './navigationConfig';
import { TrilhaPod } from './TrilhaPod';
import { MobileTrilhaControls } from './MobileTrilhaControls';

export interface NavbarProps {
  trilhas: TrilhaResumoDTO[];
  trilhaAtiva?: TrilhaDetalheDTO | null;
  paginaAtiva: PaginaAtiva;
  usuario?: UsuarioAutenticado | null;
  onNavegar: (pagina: PaginaAtiva) => void;
  onSelecionarTrilha: (id: string) => void;
  onAbrirEditarTrilha: () => void;
  onAbrirNovaTrilha?: () => void;
  onSettings?: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({
  trilhas,
  trilhaAtiva,
  paginaAtiva,
  usuario,
  onNavegar,
  onSelecionarTrilha,
  onAbrirEditarTrilha,
  onAbrirNovaTrilha,
  onSettings
}) => {
  const timer = useStudyTimer();
  const isTimerRunning = timer.running || timer.breakSeconds > 0;
  const liveTimerText = useMemo(() => {
    if (timer.breakSeconds > 0) return formatarSegundos(timer.breakSeconds);
    if (timer.running || timer.seconds > 0) return formatarSegundos(timer.seconds);
    return null;
  }, [timer.breakSeconds, timer.running, timer.seconds]);

  const getBadgeForItem = (id: PaginaAtiva): string | number | undefined => {
    if (id === 'hub' && trilhas.length > 0) return trilhas.length;
    if (id === 'disciplinas' && trilhaAtiva) return trilhaAtiva.disciplinas.length;
    return undefined;
  };

  return (
    <AppSidebar<PaginaAtiva>
      brand={{
        title: 'Trilhara',
        subtitle: 'Estudos & Cronogramas',
        logoSrc: '/favicon.svg',
        onClick: () => onNavegar('hub'),
        titleAttr: 'Ir para minhas trilhas'
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
      contextSlot={
        <TrilhaPod
          trilhas={trilhas}
          trilhaAtiva={trilhaAtiva}
          onSelecionarTrilha={onSelecionarTrilha}
          onAbrirEditarTrilha={onAbrirEditarTrilha}
          onAbrirNovaTrilha={onAbrirNovaTrilha}
        />
      }
      mobileControlsSlot={
        <MobileTrilhaControls
          trilhas={trilhas}
          trilhaAtiva={trilhaAtiva}
          onSelecionarTrilha={onSelecionarTrilha}
          onAbrirEditarTrilha={onAbrirEditarTrilha}
        />
      }
      navLabel="Menu de Estudos"
      items={NAV_ITEMS}
      activeId={paginaAtiva}
      onNavigate={onNavegar}
      getBadge={getBadgeForItem}
      getLiveText={(id) => (id === 'estudar' ? liveTimerText : null)}
      getIsRunning={(id) => (id === 'estudar' ? isTimerRunning : false)}
    />
  );
};
