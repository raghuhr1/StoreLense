import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'

dayjs.extend(relativeTime)

export const fmt = {
  date:     (iso: string | null | undefined) => iso ? dayjs(iso).format('DD MMM YYYY') : '—',
  dateTime: (iso: string | null | undefined) => iso ? dayjs(iso).format('DD MMM YYYY HH:mm') : '—',
  relative: (iso: string | null | undefined) => iso ? dayjs(iso).fromNow() : '—',
  pct:      (n: number | null | undefined)   => n != null ? `${n.toFixed(1)}%` : '—',
  count:    (n: number | null | undefined)   => n != null ? n.toLocaleString() : '—',
}

export function accuracyColor(pct: number | null | undefined): string {
  if (pct == null) return 'default'
  if (pct >= 98) return 'success'
  if (pct >= 95) return 'warning'
  return 'error'
}

export function taskStatusColor(status: string): string {
  return (
    { pending: 'default', assigned: 'blue', in_progress: 'processing',
      completed: 'success', cancelled: 'error' }[status] ?? 'default'
  )
}
