import { clsx, type ClassValue } from 'clsx'
import { twMerge }               from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function fmt(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('en-AU', { day: '2-digit', month: 'short', year: 'numeric' })
}

export function fmtDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('en-AU', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })
}

export function fmtPct(n: number | null | undefined): string {
  return n != null ? `${n.toFixed(1)}%` : '—'
}

export function accuracyColor(pct: number | null | undefined): string {
  if (pct == null) return 'badge-gray'
  if (pct >= 98)   return 'badge-green'
  if (pct >= 95)   return 'badge-yellow'
  return 'badge-red'
}

export function taskStatusBadge(status: string): string {
  return ({ pending: 'badge-gray', assigned: 'badge-blue',
            in_progress: 'badge-yellow', completed: 'badge-green',
            cancelled: 'badge-red' }[status] ?? 'badge-gray')
}
