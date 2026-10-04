import type { HTMLAttributes, ReactNode } from 'react';
import clsx from 'clsx';

interface ContentCardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
}

const ContentCard = ({ children, className, ...props }: ContentCardProps) => (
  <section className={clsx('overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm', className)} {...props}>
    {children}
  </section>
);

export default ContentCard;
