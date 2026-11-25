import { useId, useMemo, useRef, useState } from 'react';

export interface TrendPoint {
  date: string;
  label: string;
  value: number;
}

/**
 * Single-series line chart of score % over time (0–100 axis).
 * Thin 2px line, 8px markers, recessive grid, crosshair + tooltip on hover/focus,
 * and a visually hidden table for screen readers. One series → no legend box; the card title names it.
 */
export function TrendChart({ points, height = 180 }: { points: TrendPoint[]; height?: number }) {
  const id = useId();
  const svgRef = useRef<SVGSVGElement>(null);
  const [active, setActive] = useState<number | null>(null);
  const width = 600;
  const pad = { top: 12, right: 16, bottom: 26, left: 34 };
  const innerW = width - pad.left - pad.right;
  const innerH = height - pad.top - pad.bottom;

  const coords = useMemo(() => {
    const n = points.length;
    return points.map((p, i) => ({
      x: pad.left + (n === 1 ? innerW / 2 : (i / (n - 1)) * innerW),
      y: pad.top + innerH - (Math.max(0, Math.min(100, p.value)) / 100) * innerH,
    }));
  }, [points, innerW, innerH, pad.left, pad.top]);

  if (points.length === 0) return null;

  const path = coords.map((c, i) => `${i === 0 ? 'M' : 'L'}${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(' ');
  const fmtDate = (iso: string) => new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short' });

  const nearest = (clientX: number) => {
    const rect = svgRef.current?.getBoundingClientRect();
    if (!rect) return null;
    const x = ((clientX - rect.left) / rect.width) * width;
    let best = 0;
    coords.forEach((c, i) => Math.abs(c.x - x) < Math.abs(coords[best].x - x) && (best = i));
    return best;
  };

  const a = active != null ? coords[active] : null;
  const tipLeftPct = a ? (a.x / width) * 100 : 0;

  return (
    <div className="relative">
      <svg
        ref={svgRef}
        viewBox={`0 0 ${width} ${height}`}
        className="h-auto w-full overflow-visible"
        role="img"
        aria-labelledby={`${id}-desc`}
        tabIndex={0}
        onMouseMove={(e) => setActive(nearest(e.clientX))}
        onMouseLeave={() => setActive(null)}
        onFocus={() => setActive(points.length - 1)}
        onBlur={() => setActive(null)}
        onKeyDown={(e) => {
          if (e.key === 'ArrowLeft') setActive((v) => Math.max(0, (v ?? 0) - 1));
          if (e.key === 'ArrowRight') setActive((v) => Math.min(points.length - 1, (v ?? -1) + 1));
        }}
      >
        <desc id={`${id}-desc`}>Score percentage of your last {points.length} tests, oldest to newest.</desc>
        {[0, 50, 100].map((t) => {
          const y = pad.top + innerH - (t / 100) * innerH;
          return (
            <g key={t}>
              <line x1={pad.left} x2={width - pad.right} y1={y} y2={y} className="stroke-slate-200 dark:stroke-gray-700" strokeWidth={1} />
              <text x={pad.left - 8} y={y + 4} textAnchor="end" className="fill-slate-400 text-[11px] dark:fill-slate-500">
                {t}%
              </text>
            </g>
          );
        })}
        <text x={pad.left} y={height - 6} className="fill-slate-400 text-[11px] dark:fill-slate-500">
          {fmtDate(points[0].date)}
        </text>
        {points.length > 1 && (
          <text x={width - pad.right} y={height - 6} textAnchor="end" className="fill-slate-400 text-[11px] dark:fill-slate-500">
            {fmtDate(points[points.length - 1].date)}
          </text>
        )}
        {a && <line x1={a.x} x2={a.x} y1={pad.top} y2={pad.top + innerH} className="stroke-slate-300 dark:stroke-gray-600" strokeWidth={1} strokeDasharray="3 3" />}
        <path d={path} fill="none" strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" className="stroke-brand-600 dark:stroke-brand-400" />
        {coords.map((c, i) => (
          <circle
            key={i}
            cx={c.x}
            cy={c.y}
            r={i === active ? 6 : 4}
            strokeWidth={2}
            className="fill-brand-600 stroke-white dark:fill-brand-400 dark:stroke-gray-800"
          />
        ))}
      </svg>

      {a && active != null && (
        <div
          role="status"
          className="pointer-events-none absolute top-0 z-10 -translate-x-1/2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs shadow-card dark:border-gray-700 dark:bg-gray-800"
          style={{ left: `clamp(70px, ${tipLeftPct}%, calc(100% - 70px))` }}
        >
          <div className="font-semibold text-slate-900 dark:text-slate-100">{points[active].value}%</div>
          <div className="max-w-[12rem] truncate muted">{points[active].label}</div>
          <div className="muted">{fmtDate(points[active].date)}</div>
        </div>
      )}

      <table className="sr-only">
        <caption>Score percentage by test</caption>
        <thead>
          <tr>
            <th>Date</th>
            <th>Test</th>
            <th>Score %</th>
          </tr>
        </thead>
        <tbody>
          {points.map((p, i) => (
            <tr key={i}>
              <td>{fmtDate(p.date)}</td>
              <td>{p.label}</td>
              <td>{p.value}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
