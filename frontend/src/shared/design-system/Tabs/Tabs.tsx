import React from 'react';
import styles from './Tabs.module.css';

export interface TabItem {
  id: string;
  label: string;
  count?: number;
  icon?: React.ReactNode;
}

export interface TabsProps {
  items: TabItem[];
  activeId: string;
  onChange: (id: string) => void;
  className?: string;
}

export const Tabs: React.FC<TabsProps> = ({
  items,
  activeId,
  onChange,
  className = ''
}) => {
  const tabRefs = React.useRef<(HTMLButtonElement | null)[]>([]);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (items.length === 0) return;
    const currentIndex = items.findIndex((item) => item.id === activeId);
    let nextIndex = -1;

    switch (e.key) {
      case 'ArrowRight':
        nextIndex = currentIndex >= 0 ? (currentIndex + 1) % items.length : 0;
        break;
      case 'ArrowLeft':
        nextIndex = currentIndex >= 0 ? (currentIndex - 1 + items.length) % items.length : items.length - 1;
        break;
      case 'Home':
        nextIndex = 0;
        break;
      case 'End':
        nextIndex = items.length - 1;
        break;
      default:
        return;
    }

    if (nextIndex >= 0 && nextIndex !== currentIndex) {
      e.preventDefault();
      const nextItem = items[nextIndex];
      onChange(nextItem.id);
      tabRefs.current[nextIndex]?.focus();
    }
  };

  return (
    <div
      className={`${styles.tabsContainer} ${className}`}
      role="tablist"
      onKeyDown={handleKeyDown}
    >
      {items.map((item, index) => {
        const isActive = item.id === activeId;
        return (
          <button
            key={item.id}
            ref={(el) => {
              tabRefs.current[index] = el;
            }}
            type="button"
            role="tab"
            aria-selected={isActive}
            tabIndex={isActive ? 0 : -1}
            className={`${styles.tab} ${isActive ? styles.active : ''}`}
            onClick={() => onChange(item.id)}
          >
            {item.icon && <span className={styles.icon}>{item.icon}</span>}
            <span className={styles.label}>{item.label}</span>
            {typeof item.count === 'number' && (
              <span className={`${styles.badge} ${isActive ? styles.badgeActive : ''}`}>
                {item.count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
};

