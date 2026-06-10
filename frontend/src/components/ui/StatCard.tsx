import { cn } from '@/lib/utils'
import type { LucideIcon } from 'lucide-react'

interface Props {
  title:   string
  value:   string | number
  icon:    LucideIcon
  color?:  'teal' | 'blue' | 'green' | 'yellow' | 'red'
  delta?:  string
  sub?:    string
}

const colors = {
  teal:   'bg-brand-50 text-brand-700',
  blue:   'bg-brand-50 text-brand-700',
  green:  'bg-green-50 text-green-600',
  yellow: 'bg-yellow-50 text-yellow-600',
  red:    'bg-red-50 text-red-600',
}

export default function StatCard({ title, value, icon: Icon, color = 'blue', delta, sub }: Props) {
  return (
    <div className="card flex items-start justify-between">
      <div>
        <p className="text-sm text-gray-500 font-medium">{title}</p>
        <p className="text-2xl font-bold text-gray-900 mt-1">{value}</p>
        {sub && <p className="text-xs text-gray-400 mt-0.5">{sub}</p>}
        {delta && <p className="text-xs text-green-600 mt-1 font-medium">{delta}</p>}
      </div>
      <div className={cn('w-10 h-10 rounded-xl flex items-center justify-center', colors[color])}>
        <Icon size={20} />
      </div>
    </div>
  )
}
