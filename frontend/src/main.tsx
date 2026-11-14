import React from 'react';
import ReactDOM from 'react-dom/client';
import { App } from './App';
import { aplicarTema, temaInicial } from '@/shared/design-system/Theme/useTheme';
import '@/shared/styles/index.css';

// aplica o cache de tema por usuário ANTES do primeiro paint. A CSP proíbe script
// inline no index.html, então a leitura síncrona acontece aqui, antes de `createRoot`.
aplicarTema(temaInicial(null));

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);

