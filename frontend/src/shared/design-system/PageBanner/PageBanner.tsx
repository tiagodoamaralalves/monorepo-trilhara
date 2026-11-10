import React from 'react';
import styles from './PageBanner.module.css';

export interface PageBannerProps {
  title: React.ReactNode;
  description?: string;
  icon?: React.ReactNode;
  actions?: React.ReactNode;
  toolbar?: React.ReactNode;
  className?: string;
}

export const PageBanner: React.FC<PageBannerProps> = ({
  title,
  description,
  icon,
  actions,
  toolbar,
  className = ''
}) => {
  return (
    <div className={`${styles.banner} ${className}`}>
      <div className={styles.top}>
        <div className={styles.titleGroup}>
          <h1 className={styles.title}>
            {icon && <span className={styles.icon}>{icon}</span>}
            <span>{title}</span>
          </h1>
          {description && <p className={styles.desc}>{description}</p>}
        </div>
        {actions && <div className={styles.actions}>{actions}</div>}
      </div>
      {toolbar && <div className={styles.toolbar}>{toolbar}</div>}
    </div>
  );
};

