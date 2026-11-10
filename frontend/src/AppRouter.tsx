import React, { Suspense, useEffect } from 'react';

const EstudosApp = React.lazy(() => import('@/modules/estudos/EstudosApp'));
const PomodoroApp = React.lazy(() => import('@/modules/pomodoro/PomodoroApp'));
const NotasApp = React.lazy(() => import('@/modules/notas/NotasApp'));
const TarefasApp = React.lazy(() => import('@/modules/tarefas/TarefasApp'));

type ModuloAtivo = 'tarefas' | 'notas' | 'pomodoro' | 'estudos';

const TITULOS_MODULO: Record<ModuloAtivo, string> = {
  tarefas: 'Trilhara Tarefas & Calendário',
  notas: 'Trilhara Notas & Cadernos',
  pomodoro: 'Trilhara — Pomodoro',
  estudos: 'Trilhara — Gestão e Ciclos de Estudo'
};

function resolverModulo(hostname: string, pathname: string): ModuloAtivo {
  if (hostname.startsWith('tarefas.') || pathname.startsWith('/tarefas')) {
    return 'tarefas';
  }
  if (hostname.startsWith('notas.') || pathname.startsWith('/notas')) {
    return 'notas';
  }
  if (hostname.startsWith('pomodoro.') || pathname.startsWith('/pomodoro')) {
    return 'pomodoro';
  }
  return 'estudos';
}

function LoadingFallback() {
  return (
    <div
      style={{
        display: 'flex',
        minHeight: '100vh',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--bg)',
        color: 'var(--text-mid)',
        fontFamily: 'Inter, sans-serif',
        fontSize: '0.95rem'
      }}
    >
      <span>Carregando...</span>
    </div>
  );
}

export function AppRouter() {
  const hostname = typeof window !== 'undefined' ? window.location.hostname.toLowerCase() : '';
  const pathname = typeof window !== 'undefined' ? window.location.pathname.toLowerCase() : '';
  const modulo = resolverModulo(hostname, pathname);

  useEffect(() => {
    if (typeof document !== 'undefined') {
      document.title = TITULOS_MODULO[modulo];
    }
  }, [modulo]);

  return (
    <Suspense fallback={<LoadingFallback />}>
      {modulo === 'tarefas' && <TarefasApp />}
      {modulo === 'notas' && <NotasApp />}
      {modulo === 'pomodoro' && <PomodoroApp />}
      {modulo === 'estudos' && <EstudosApp />}
    </Suspense>
  );
}

