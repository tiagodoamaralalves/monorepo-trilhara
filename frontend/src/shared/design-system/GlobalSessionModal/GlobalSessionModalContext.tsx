import React, { createContext, useContext, useState, useCallback, useMemo } from 'react';
import { GlobalSessionModalOpenParams } from '../../contracts/domainContracts';

export interface GlobalSessionModalContextValue {
  isOpen: boolean;
  params: GlobalSessionModalOpenParams | null;
  abrirModal: (params: GlobalSessionModalOpenParams) => void;
  fecharModal: () => void;
}

const GlobalSessionModalContext = createContext<GlobalSessionModalContextValue | null>(null);

export const GlobalSessionModalProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
  const [params, setParams] = useState<GlobalSessionModalOpenParams | null>(null);

  const abrirModal = useCallback((novosParams: GlobalSessionModalOpenParams) => {
    setParams(novosParams);
  }, []);

  const fecharModal = useCallback(() => {
    setParams(null);
  }, []);

  const value = useMemo<GlobalSessionModalContextValue>(
    () => ({
      isOpen: params !== null,
      params,
      abrirModal,
      fecharModal
    }),
    [params, abrirModal, fecharModal]
  );

  return (
    <GlobalSessionModalContext.Provider value={value}>
      {children}
    </GlobalSessionModalContext.Provider>
  );
};

export function useGlobalSessionModal(): GlobalSessionModalContextValue {
  const ctx = useContext(GlobalSessionModalContext);
  if (!ctx) {
    throw new Error('useGlobalSessionModal deve ser utilizado dentro de um GlobalSessionModalProvider');
  }
  return ctx;
}
