import React from 'react';
import clsx from 'clsx';

interface StatCardProps {
  title: string;
  value: number;
  icon: React.ElementType;
  type?: 'primary' | 'success' | 'warning' | 'error' | 'default';
}

const StatCard: React.FC<StatCardProps> = ({ title, value, icon: Icon, type = 'default' }) => {
  const colorStyles = {
    primary: "bg-blue-50 text-blue-600 border-blue-100",
    success: "bg-emerald-50 text-emerald-600 border-emerald-100",
    warning: "bg-amber-50 text-amber-600 border-amber-100",
    error: "bg-red-50 text-red-600 border-red-100",
    default: "bg-slate-50 text-slate-600 border-slate-100"
  };

  const iconStyles = {
    primary: "text-blue-500",
    success: "text-emerald-500",
    warning: "text-amber-500",
    error: "text-red-500",
    default: "text-slate-500"
  };

  return (
    <div className={clsx("rounded-2xl border p-5 flex flex-col gap-3 transition-all hover:shadow-md", colorStyles[type])}>
      <div className="flex justify-between items-start">
        <div className="text-sm font-medium opacity-80">{title}</div>
        <div className={clsx("p-2 rounded-xl bg-white/60", iconStyles[type])}>
          <Icon size={20} />
        </div>
      </div>
      <div className="text-3xl font-bold">{value.toLocaleString()}</div>
    </div>
  );
};

export default StatCard;
