import { useEffect, useState } from 'react';

/** Milliseconds until `target`, using the server clock (serverNow) to correct the device clock. */
export function useServerCountdown(target: string | null | undefined, serverNow: string | undefined) {
  const offset = serverNow ? new Date(serverNow).getTime() - Date.now() : 0;
  const calc = () => (target ? new Date(target).getTime() - (Date.now() + offset) : 0);
  const [ms, setMs] = useState(calc);
  useEffect(() => {
    setMs(calc());
    const t = window.setInterval(() => setMs(calc()), 1000);
    return () => window.clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target, serverNow]);
  return ms;
}

/** "2d 4h 05m", "1:04:09" or "04:09". */
export function formatCountdown(ms: number) {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const d = Math.floor(total / 86400);
  const h = Math.floor((total % 86400) / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const pad = (n: number) => String(n).padStart(2, '0');
  if (d > 0) return `${d}d ${h}h ${pad(m)}m`;
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}

export function Countdown({ to, serverNow, done = 'now' }: { to: string | null; serverNow: string; done?: string }) {
  const ms = useServerCountdown(to, serverNow);
  return <span className="font-mono tabular-nums">{ms > 0 ? formatCountdown(ms) : done}</span>;
}
