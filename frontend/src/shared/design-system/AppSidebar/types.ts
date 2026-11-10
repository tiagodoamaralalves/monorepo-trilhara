import React from 'react';
import type { LucideIcon } from 'lucide-react';

export interface SidebarBrand {
  title: string;
  subtitle?: React.ReactNode;
  logoSrc: string;
  logoAlt?: string;
  onClick?: () => void;
  titleAttr?: string;
}

export interface SidebarUser {
  nome: string;
  email?: string;
  avatarUrl?: string;
}

export interface SidebarNavItemConfig<TId extends string = string> {
  id: TId;
  label: string;
  mobileLabel?: string;
  title: string;
  icon: LucideIcon | React.ComponentType<{ size?: number | string; className?: string; 'aria-hidden'?: boolean | 'true' | 'false' }>;
  isFocusItem?: boolean;
}

export interface SidebarContextValue {
  collapsed: boolean;
  toggleCollapsed: () => void;
  setCollapsed: (collapsed: boolean) => void;
}
