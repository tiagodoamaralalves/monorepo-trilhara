import React, { createContext, useContext } from 'react';
import { SidebarContextValue } from './types';

export const SidebarContext = createContext<SidebarContextValue | undefined>(undefined);

export const useSidebar = (): SidebarContextValue => {
  const context = useContext(SidebarContext);
  if (!context) {
    throw new Error('useSidebar deve ser utilizado dentro de um AppSidebar');
  }
  return context;
};

