const ZONE_PALETTE = [
  { text: 'text-sky-300', border: 'border-sky-500/60', bg: 'bg-sky-500/10', dot: 'bg-sky-400' },
  { text: 'text-rose-300', border: 'border-rose-500/60', bg: 'bg-rose-500/10', dot: 'bg-rose-400' },
  {
    text: 'text-emerald-300',
    border: 'border-emerald-500/60',
    bg: 'bg-emerald-500/10',
    dot: 'bg-emerald-400',
  },
  {
    text: 'text-amber-300',
    border: 'border-amber-500/60',
    bg: 'bg-amber-500/10',
    dot: 'bg-amber-400',
  },
  {
    text: 'text-violet-300',
    border: 'border-violet-500/60',
    bg: 'bg-violet-500/10',
    dot: 'bg-violet-400',
  },
] as const;

export function getZoneStyle(index: number) {
  return ZONE_PALETTE[index % ZONE_PALETTE.length];
}