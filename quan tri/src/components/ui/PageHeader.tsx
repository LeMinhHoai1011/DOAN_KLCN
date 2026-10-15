import type { ReactNode } from 'react';

interface PageHeaderProps {
  title: string;
  description?: string;
  /** Optional micro-label rendered above the headline (e.g. module / section name). */
  eyebrow?: string;
  actions?: ReactNode;
}

const PageHeader = ({ title, description, eyebrow, actions }: PageHeaderProps) => (
  <div className="flex flex-col gap-5 sm:flex-row sm:flex-wrap sm:items-start sm:justify-between">
    {/* flex-1 + min-w để tiêu đề giữ bề ngang đọc được; khi quá hẹp, vùng action tự rớt xuống dòng dưới. */}
    <div className="min-w-0 sm:min-w-[24rem] sm:flex-1">
      {eyebrow && <p className="mb-2 text-2xs font-bold tracking-[0.08em] text-ink-muted uppercase">{eyebrow}</p>}
      <h1 className="font-sans text-[36px] leading-[44px] font-bold tracking-tight text-ink">{title}</h1>
      {description && <p className="mt-2 max-w-4xl text-[19px] leading-[30px] text-ink-soft">{description}</p>}
    </div>
    {/*
      Vùng action luôn nằm bên phải tiêu đề: `sm:ml-auto` + `sm:justify-end` giữ lề phải kể cả khi
      phải xuống dòng ở cửa sổ hẹp, `sm:shrink-0` giữ các nút trên cùng một dòng, `sm:max-w-full`
      chặn tràn ngang.
    */}
    {actions && <div className="flex flex-wrap items-center gap-3 sm:ml-auto sm:max-w-full sm:shrink-0 sm:justify-end">{actions}</div>}
  </div>
);

export default PageHeader;
