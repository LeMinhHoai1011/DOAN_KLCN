import type { HTMLAttributes, ReactNode } from 'react';
import clsx from 'clsx';

interface ContentCardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
}

const ContentCard = ({ children, className, ...props }: ContentCardProps) => (
  <section className={clsx('rounded-card border-hairline bg-surface shadow-elev1 overflow-hidden border', className)} {...props}>
    {children}
  </section>
);

export default ContentCard;
