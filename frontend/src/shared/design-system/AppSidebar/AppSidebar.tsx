import React, { useState } from 'react';
import {
  LogIn,
  LogOut,
  Moon,
  PanelLeftClose,
  PanelLeftOpen,
  Sun
} from 'lucide-react';
import { useTheme } from '../Theme/useTheme';
import { ThemeToggle } from '../ThemeToggle/ThemeToggle';
import { SidebarBrand, SidebarUser, SidebarNavItemConfig } from './types';
import { SidebarContext } from './SidebarContext';
import { SidebarNavItem } from './SidebarNavItem';
import { AppSwitcher } from '../AppSwitcher/AppSwitcher';
import { ContaMenuButton } from '../ContaMenuButton/ContaMenuButton';
import { ModuloApp } from '../../auth/sso';
import styles from './AppSidebar.module.css';

export interface AppSidebarProps<TId extends string = string> {
  brand: SidebarBrand;
  user?: SidebarUser | null;
  currentApp?: ModuloApp;
  showAppSwitcher?: boolean;
  onLogout?: () => void;
  onLogin?: () => void;
  storageKey?: string;
  contextSlot?: React.ReactNode;
  navLabel?: string;
  items?: readonly SidebarNavItemConfig<TId>[];
  activeId?: TId;
  onNavigate?: (id: TId) => void;
  getBadge?: (id: TId) => string | number | undefined;
  getLiveText?: (id: TId) => string | null;
  getIsRunning?: (id: TId) => boolean;
  mobileControlsSlot?: React.ReactNode;
  mobileBottomNavSlot?: React.ReactNode;
  children?: React.ReactNode;
  className?: string;
  onCollapsedChange?: (collapsed: boolean) => void;
}

export const AppSidebar = <TId extends string = string>({
  brand,
  user,
  currentApp,
  showAppSwitcher = true,
  onLogout,
  onLogin,
  storageKey = 'trilhara_sidebar_collapsed',
  contextSlot,
  navLabel,
  items,
  activeId,
  onNavigate,
  getBadge,
  getLiveText,
  getIsRunning,
  mobileControlsSlot,
  mobileBottomNavSlot,
  children,
  className = '',
  onCollapsedChange
}: AppSidebarProps<TId>): React.ReactElement => {
  const [collapsed, setCollapsed] = useState<boolean>(() => {
    try {
      return window.localStorage.getItem(storageKey) === 'true';
    } catch {
      return false;
    }
  });

  const { isLight, toggleTheme } = useTheme();

  const handleToggleCollapsed = () => {
    setCollapsed((prev) => {
      const next = !prev;
      try {
        window.localStorage.setItem(storageKey, String(next));
      } catch {
        // Ignora erro de localStorage
      }
      onCollapsedChange?.(next);
      return next;
    });
  };

  const contextValue = {
    collapsed,
    toggleCollapsed: handleToggleCollapsed,
    setCollapsed
  };

  return (
    <SidebarContext.Provider value={contextValue}>
      {/* ── 1. DESKTOP: Painel Lateral (Sidebar) ── */}
      <aside
        data-collapsed={collapsed}
        className={`${styles.sidebar} ${collapsed ? styles.sidebarCollapsed : ''} ${className}`.trim()}
        aria-label="Painel lateral de navegação"
      >
        {/* Topo: Marca e Recolhimento */}
        <div className={styles.sidebarHeader}>
          <button
            type="button"
            className={styles.brandButton}
            onClick={brand.onClick}
            title={brand.titleAttr || brand.title}
          >
            <img className={styles.brandLogo} src={brand.logoSrc} alt={brand.logoAlt || ''} />
            {!collapsed && (
              <div className={styles.brandInfo}>
                <span className={styles.brandTitle}>{brand.title}</span>
                {brand.subtitle && <span className={styles.brandSubtitle}>{brand.subtitle}</span>}
              </div>
            )}
          </button>

          <button
            type="button"
            className={styles.collapseToggle}
            onClick={handleToggleCollapsed}
            aria-label={collapsed ? 'Expandir painel lateral' : 'Recolher painel lateral'}
            title={collapsed ? 'Expandir menu lateral' : 'Recolher menu lateral'}
          >
            {collapsed ? <PanelLeftOpen size={16} /> : <PanelLeftClose size={16} />}
          </button>
        </div>

        {/* ── App Switcher rápido entre os 4 módulos ── */}
        {showAppSwitcher && (
          <div className={styles.appSwitcherArea}>
            <AppSwitcher moduloAtual={currentApp} compacto={collapsed} />
          </div>
        )}

        {/* Slot Livre de Contexto (ex: TrilhaPod no app de estudos, Filtro de Listas em Tarefas, etc) */}
        {contextSlot && <div className={styles.sidebarContextArea}>{contextSlot}</div>}

        {/* Navegação Principal */}
        <nav className={styles.sidebarNav} aria-label="Navegação principal">
          {!collapsed && navLabel && <span className={styles.navSectionLabel}>{navLabel}</span>}

          {children ? (
            children
          ) : items && onNavigate && activeId !== undefined ? (
            <div className={styles.navList}>
              {items.map((item) => (
                <SidebarNavItem
                  key={item.id}
                  item={item}
                  active={activeId === item.id}
                  variant="sidebar"
                  collapsed={collapsed}
                  badge={getBadge ? getBadge(item.id) : undefined}
                  liveText={getLiveText ? getLiveText(item.id) : null}
                  isRunning={getIsRunning ? getIsRunning(item.id) : false}
                  onSelect={onNavigate}
                />
              ))}
            </div>
          ) : null}
        </nav>

        {/* Rodapé: Tema e Usuário */}
        <div className={styles.sidebarFooter}>
          <ThemeToggle
            className={`${styles.themeToggleBtn} ${collapsed ? styles.themeToggleBtnCollapsed : ''}`}
            size="sm"
            showLabel={!collapsed}
            label={isLight ? 'Modo Escuro' : 'Modo Claro'}
          />

          {user ? (
            <div className={`${styles.userPod} ${collapsed ? styles.userPodCollapsed : ''}`}>
              <ContaMenuButton
                variant="sidebar"
                nome={user.nome}
                collapsed={collapsed}
              />
              {!collapsed && (
                <div className={styles.userInfo}>
                  <span className={styles.userName} title={user.nome}>
                    {user.nome}
                  </span>
                  {user.email && (
                    <span className={styles.userEmail} title={user.email}>
                      {user.email}
                    </span>
                  )}
                </div>
              )}
              {onLogout && (
                <button
                  type="button"
                  className={styles.logoutBtn}
                  onClick={onLogout}
                  title="Sair da conta"
                  aria-label="Sair da conta"
                >
                  <LogOut size={15} />
                </button>
              )}
            </div>
          ) : onLogin ? (
            <button
              type="button"
              className={`${styles.loginPromptBtn} ${collapsed ? styles.loginPromptBtnCollapsed : ''}`}
              onClick={onLogin}
              title="Entrar na conta"
              aria-label="Entrar na conta"
            >
              <LogIn size={15} />
              {!collapsed && <span>Entrar na conta</span>}
            </button>
          ) : null}
        </div>
      </aside>

      {/* ── 2. MOBILE: Barra Superior de Cabeçalho ── */}
      <header className={styles.mobileHeader}>
        <button
          type="button"
          className={styles.mobileBrand}
          onClick={brand.onClick}
          aria-label={brand.title}
        >
          <img className={styles.mobileBrandLogo} src={brand.logoSrc} alt={brand.logoAlt || ''} />
          <span className={styles.mobileBrandTitle}>{brand.title}</span>
        </button>

        <div className={styles.mobileControls}>
          {showAppSwitcher && (
            <AppSwitcher moduloAtual={currentApp} compacto={true} className={styles.mobileAppSwitcher} />
          )}

          {mobileControlsSlot}

          {user && <ContaMenuButton variant="header" />}

          {!user && onLogin && (
            <button
              type="button"
              className={styles.mobileActionBtn}
              onClick={onLogin}
              title="Entrar na conta"
              aria-label="Entrar na conta"
            >
              <LogIn size={14} />
            </button>
          )}

          <button
            type="button"
            className={styles.mobileActionBtn}
            onClick={toggleTheme}
            title={isLight ? 'Alternar para o modo escuro' : 'Alternar para o modo claro'}
            aria-label={isLight ? 'Alternar para o modo escuro' : 'Alternar para o modo claro'}
          >
            {isLight ? <Moon size={14} /> : <Sun size={14} />}
          </button>

          {onLogout && (
            <button
              type="button"
              className={styles.mobileActionBtn}
              onClick={onLogout}
              title="Sair da conta"
              aria-label="Sair da conta"
            >
              <LogOut size={14} />
            </button>
          )}
        </div>
      </header>

      {/* ── 3. MOBILE: Barra Inferior Fixa (Bottom Stack Navigation) ── */}
      <nav className={styles.mobileBottomNav} aria-label="Navegação principal mobile">
        {mobileBottomNavSlot ? (
          mobileBottomNavSlot
        ) : items && onNavigate && activeId !== undefined ? (
          <div
            className={styles.mobileBottomTrack}
            style={{ '--mobile-nav-columns': items.length } as React.CSSProperties}
          >
            {items.map((item) => (
              <SidebarNavItem
                key={item.id}
                item={item}
                active={activeId === item.id}
                variant="bottom"
                liveText={getLiveText ? getLiveText(item.id) : null}
                isRunning={getIsRunning ? getIsRunning(item.id) : false}
                onSelect={onNavigate}
              />
            ))}
          </div>
        ) : null}
      </nav>
    </SidebarContext.Provider>
  );
};

