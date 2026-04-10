import type { ReactNode } from 'react';

interface PillProps {
  tone?: 'green' | 'red' | 'blue' | 'amber' | 'slate';
  children: ReactNode;
}

const toneClassMap: Record<NonNullable<PillProps['tone']>, string> = {
  green: 'bg-lime-100 text-lime-800',
  red: 'bg-orange-100 text-orange-800',
  blue: 'bg-emerald-100 text-emerald-800',
  amber: 'bg-yellow-100 text-yellow-800',
  slate: 'bg-stone-100 text-stone-700',
};

export function Pill({ tone = 'slate', children }: PillProps) {
  return (
    <span className={`inline-flex rounded-[2px] px-2 py-0.5 text-xs font-semibold tracking-[0.08em] ${toneClassMap[tone]}`}>
      {children}
    </span>
  );
}
