import { useId, useState } from 'react';

/**
 * Single-series histogram (students per score band). Thin bars with 4px rounded tops, 2px gaps,
 * recessive grid, hover/focus tooltip per bar, and a hidden table for screen readers.
 */
export function DistributionChart({ buckets }: { buckets: { from: number; to: number; count: number }[] }) {
  const id = useId();
  const [active, setActive] = useState<number | null>(null);
  const width = 600;
  const height = 200;
  const pad = { top: 12, right: 8, bottom: 26, left: 30 };
  const innerW = width - pad.left - pad.right;
  const innerH = height - pad.top - pad.bottom;
  const max = Math.max(1, ...buckets.map((b) => b.count));
  const slot = innerW / buckets.length;
  const barW = Math.max(4, Math.min(slot * 0.6, 28)); // thin bars, centred in their slot
  const ticks = [...new Set([0, Math.ceil(max / 2), max])];

  return (
    <div className="relative">
      <svg viewBox={`0 0 ${width} ${height}`} className="h-auto w-full" role="img" aria-labelledby={`${id}-d`}>
        <desc id={`${id}-d`}>Number of students in each 10% score band.</desc>
        {ticks.map((t) => {
          const y = pad.top + innerH - (t / max) * innerH;
          return (
            <g key={t}>
              <line x1={pad.left} x2={width - pad.right} y1={y} y2={y} className="stroke-slate-200 dark:stroke-gray-700" strokeWidth={1} />
              <text x={pad.left - 6} y={y + 4} textAnchor="end" className="fill-slate-400 text-[11px] dark:fill-slate-500">
                {t}
              </text>
            </g>
          );
        })}
        {buckets.map((b, i) => {
          const h = (b.count / max) * innerH;
          const x = pad.left + i * slot + (slot - barW) / 2;
          const y = pad.top + innerH - h;
          const r = Math.min(4, barW / 2, h);
          return (
            <g key={b.from}>
              {/* hit area larger than the bar */}
              <rect
                x={pad.left + i * slot}
                y={pad.top}
                width={slot}
                height={innerH}
                fill="transparent"
                tabIndex={0}
                aria-label={`${b.from}–${b.to}%: ${b.count} students`}
                onMouseEnter={() => setActive(i)}
                onMouseLeave={() => setActive(null)}
                onFocus={() => setActive(i)}
                onBlur={() => setActive(null)}
              />
              {b.count > 0 && (
                <path
                  d={`M${x},${y + h} V${y + r} Q${x},${y} ${x + r},${y} H${x + barW - r} Q${x + barW},${y} ${x + barW},${y + r} V${y + h} Z`}
                  className={active === i ? 'fill-brand-700 dark:fill-brand-300' : 'fill-brand-600 dark:fill-brand-400'}
                  pointerEvents="none"
                />
              )}
              <text x={x + barW / 2} y={height - 8} textAnchor="middle" className="fill-slate-400 text-[10px] dark:fill-slate-500">
                {b.from}
              </text>
            </g>
          );
        })}
      </svg>
      {active != null && (
        <div
          role="status"
          className="pointer-events-none absolute top-0 -translate-x-1/2 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs shadow-card dark:border-gray-700 dark:bg-gray-800"
          style={{ left: `${((pad.left + active * slot + slot / 2) / width) * 100}%` }}
        >
          <span className="font-semibold">{buckets[active].count}</span> student{buckets[active].count === 1 ? '' : 's'} ·{' '}
          {buckets[active].from}–{buckets[active].to}%
        </div>
      )}
      <table className="sr-only">
        <caption>Score distribution</caption>
        <thead>
          <tr>
            <th>Score band</th>
            <th>Students</th>
          </tr>
        </thead>
        <tbody>
          {buckets.map((b) => (
            <tr key={b.from}>
              <td>
                {b.from}–{b.to}%
              </td>
              <td>{b.count}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
