import React from 'react';
import { SidebarNavItemConfig } from './types';
import { SidebarContext } from './SidebarContext';
import styles from './AppSidebar.module.css';

export interface SidebarNavItemProps<TId extends string = string> {
  item: SidebarNavItemConfig<TId>;
  active: boolean;
  variant?: 'sidebar' | 'bottom';
  collapsed?: boolean;
  badge?: string | number;
  liveText?: string | null;
  isRunning?: boolean;
  onSelect: (id: TId) => void;
}

export const SidebarNavItem = <TId extends string = string>({
  item,
  active,
  variant = 'sidebar',
  collapsed: explicitCollapsed,
  badge,
  liveText,
  isRunning = false,
  onSelect
}: SidebarNavItemProps<TId>): React.ReactElement => {
  const context = React.useContext(SidebarContext);
  const isCollapsed = explicitCollapsed !== undefined ? explicitCollapsed : (context?.collapsed ?? false);
  const Icon = item.icon;

  if (variant === 'bottom') {
    return (
      <button
        type="button"
        className={[
          styles.bottomNavItem,
          active ? styles.bottomNavItemActive : '',
          item.isFocusItem ? styles.bottomNavFocus : '',
          isRunning ? styles.bottomNavRunning : ''
        ]
          .filter(Boolean)
          .join(' ')}
        aria-current={active ? 'page' : undefined}
        title={item.title}
        onClick={() => onSelect(item.id)}
      >
        <span className={styles.bottomNavIconWrap}>
          <Icon size={19} aria-hidden="true" />
          {isRunning && <span className={styles.liveDotMobile} aria-hidden="true" />}
        </span>
        <span className={styles.bottomNavLabel}>
          {liveText && item.isFocusItem ? liveText : (item.mobileLabel || item.label)}
        </span>
      </button>
    );
  }

  return (
    <button
      type="button"
      className={[
        styles.sidebarNavItem,
        active ? styles.sidebarNavItemActive : '',
        isCollapsed ? styles.sidebarNavItemCollapsed : '',
        item.isFocusItem ? styles.sidebarNavFocus : '',
        isRunning ? styles.sidebarNavRunning : ''
      ]
        .filter(Boolean)
        .join(' ')}
      aria-current={active ? 'page' : undefined}
      title={isCollapsed ? `${item.label} — ${item.title}` : item.title}
      onClick={() => onSelect(item.id)}
    >
      <span className={styles.sidebarNavIconWrap}>
        <Icon size={19} aria-hidden="true" />
        {isRunning && isCollapsed && <span className={styles.liveDotCollapsed} aria-hidden="true" />}
      </span>

      {!isCollapsed && (
        <>
          <span className={styles.sidebarNavLabel}>{item.label}</span>

          {liveText ? (
            <span className={`${styles.liveTimerBadge} ${isRunning ? styles.liveTimerRunning : ''}`}>
              <span className={styles.liveDot} aria-hidden="true" />
              <span>{liveText}</span>
            </span>
          ) : badge !== undefined ? (
            <span className={styles.navBadge}>{badge}</span>
          ) : null}
        </>
      )}
    </button>
  );
};

