import type { ElementType } from 'react';
import clsx from 'clsx';

interface StatCardProps {
  title: string;
  value: number | string;
  icon: ElementType;
  type?: 'primary' | 'success' | 'warning' | 'error' | 'default';
  /** Unit hint rendered next to the value, e.g. "chứng từ". */
  unit?: string;
  /** One-line context rendered under the value, e.g. "So với kỳ trước". */
  hint?: string;
}

const toneStyles: Record<NonNullable<StatCardProps['type']>, { icon: string; value: string }> = {
  primary: { icon: 'border-brand-100 bg-brand-50 text-brand-600', value: 'text-ink' },
  success: { icon: 'border-parsed/25 bg-parsed-surface text-parsed', value: 'text-ink' },
  warning: { icon: 'border-review/30 bg-review-surface text-review', value: 'text-review' },
  error: { icon: 'border-failed/25 bg-failed-surface text-failed', value: 'text-failed' },
  default: { icon: 'border-hairline bg-subtle text-ink-muted', value: 'text-ink' },
};

/** KPI tile: hairline white container, tabular value, pastel status glyph. */
const StatCard = ({ title, value, icon: Icon, type = 'default', unit, hint }: StatCardProps) => {
  const tone = toneStyles[type];
  const formatted = typeof value === 'number' ? value.toLocaleString('vi-VN') : value;

  return (
    <div className="si-card flex flex-col justify-between gap-3 p-6 transition-shadow hover:shadow-elev2">
      <div className="flex items-start justify-between gap-3">
        <span className="text-[19px] leading-[28px] font-semibold text-ink-soft">{title}</span>
        <span className={clsx('flex h-11 w-11 shrink-0 items-center justify-center rounded-control border', tone.icon)}>
          <Icon size={24} />
        </span>
      </div>
      <div className="mt-1">
        <div className="flex flex-wrap items-baseline gap-2">
          <span className={clsx('si-num text-[40px] leading-[48px] font-bold tracking-tight', tone.value)}>{formatted}</span>
          {unit && <span className="text-[18px] text-ink-soft">{unit}</span>}
        </div>
        {hint && <p className="mt-1.5 text-2xs text-ink-muted">{hint}</p>}
      </div>
    </div>
  );
};

export default StatCard;
