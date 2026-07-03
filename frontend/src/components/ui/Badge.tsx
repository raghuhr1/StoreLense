import { cn } from '@/lib/utils'

type Variant = 'green' | 'yellow' | 'red' | 'blue' | 'gray' | 'purple'

const variants: Record<Variant, string> = {
  green:  'bg-green-100 text-green-800',
  yellow: 'bg-yellow-100 text-yellow-800',
  red:    'bg-red-100 text-red-800',
  blue:   'bg-blue-100 text-blue-800',
  gray:   'bg-gray-100 text-gray-600',
  purple: 'bg-purple-100 text-purple-800',
}

interface Props {
  children: React.ReactNode
  variant?: Variant
  className?: string
}

export default function Badge({ children, variant = 'gray', className }: Props) {
  return (
    <span className={cn('badge', variants[variant], className)}>
      {children}
    </span>
  )
}

export function statusBadge(status: string): React.ReactElement {
  const map: Record<string, Variant> = {
    active: 'green', inactive: 'gray', pending: 'gray', assigned: 'blue',
    in_progress: 'yellow', completed: 'green', cancelled: 'red', failed: 'red',
    created: 'gray', open: 'blue', closed: 'gray',
    in_store: 'green', missing: 'red', sold: 'gray', damaged: 'red',
    // session lifecycle extensions
    paused: 'yellow', uploaded: 'blue', reconciled: 'purple',
    // cycle count statuses (uppercase from backend)
    DRAFT: 'gray', RUNNING: 'yellow', COMPLETED: 'green',
    UPLOADED: 'blue', RECONCILED: 'purple', CLOSED: 'gray',
    // reconciliation statuses
    PENDING_APPROVAL: 'yellow', APPROVED: 'green', FAILED: 'red',
  }
  return <Badge variant={map[status] ?? 'gray'}>{status.replace(/_/g, ' ')}</Badge>
}
