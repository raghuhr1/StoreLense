'use client'

import Link              from 'next/link'
import { usePathname }   from 'next/navigation'
import {
  type LucideIcon,
  LayoutDashboard, Package, RotateCw,
  ArrowLeftRight, BarChart3, Users, Store, Cpu, ScanLine, Tag, TrendingUp,
  Truck, RefreshCw, ShoppingCart, GitCompare, PackageOpen, Radio, ShieldCheck,
} from 'lucide-react'
import { useAuth }       from '@/lib/auth/AuthContext'
import { useFeatures }  from '@/lib/features/FeaturesContext'
import { cn }            from '@/lib/utils'
import type { Feature }  from '@/types'

type NavItem = {
  href: string; label: string; icon: LucideIcon
  roles: readonly string[]
  feature?: Feature
}

const allNavItems: NavItem[] = [
  { href: '/dashboard',            label: 'Dashboard',       icon: LayoutDashboard, roles: ['ADMIN','STORE_MANAGER','STORE_ASSOCIATE','REFILL_ASSOCIATE'] },
  { href: '/inventory',            label: 'Inventory',       icon: Package,         roles: ['ADMIN','STORE_MANAGER'],                          feature: 'INVENTORY' },
  { href: '/inbound',              label: 'Inbound',         icon: Truck,           roles: ['ADMIN','STORE_MANAGER','REFILL_ASSOCIATE'],        feature: 'INBOUND' },
  { href: '/replenishment',        label: 'Replenishment',   icon: RefreshCw,       roles: ['ADMIN','STORE_MANAGER','REFILL_ASSOCIATE'],        feature: 'REPLENISHMENT' },
  { href: '/cycle-count',          label: 'Cycle Count',     icon: RotateCw,        roles: ['ADMIN','STORE_MANAGER','STORE_ASSOCIATE'],         feature: 'CYCLE_COUNT' },
  { href: '/transfers',            label: 'Transfers',       icon: ArrowLeftRight,  roles: ['ADMIN','STORE_MANAGER'],                          feature: 'TRANSFERS' },
  { href: '/reports',              label: 'Reports',         icon: BarChart3,       roles: ['ADMIN','STORE_MANAGER'],                          feature: 'ANALYTICS' },
  { href: '/analytics',            label: 'Analytics',       icon: TrendingUp,      roles: ['ADMIN','STORE_MANAGER'],                          feature: 'ANALYTICS' },
  { href: '/sold-items',           label: 'Sales',           icon: ShoppingCart,    roles: ['ADMIN','STORE_MANAGER'],                          feature: 'SALES' },
  { href: '/variance',             label: 'Variance',        icon: GitCompare,      roles: ['ADMIN','STORE_MANAGER'],                          feature: 'CYCLE_COUNT' },
  { href: '/erp-imports',          label: 'ERP Imports',     icon: PackageOpen,     roles: ['ADMIN'],                                          feature: 'ERP_INTEGRATION' },
  { href: '/products',             label: 'Products',        icon: Tag,             roles: ['ADMIN'] },
  { href: '/users',                label: 'Users',           icon: Users,           roles: ['ADMIN'] },
  { href: '/stores',               label: 'Stores',          icon: Store,           roles: ['ADMIN'] },
  { href: '/devices',              label: 'Devices',         icon: Cpu,             roles: ['ADMIN','STORE_MANAGER'],                          feature: 'DEVICES' },
  { href: '/devices/antenna-mapping', label: 'Antenna Mapping', icon: Radio,        roles: ['ADMIN'],                                          feature: 'DEVICES' },
  { href: '/guard-dashboard',     label: 'Guard Dashboard', icon: ShieldCheck,     roles: ['ADMIN','STORE_MANAGER'] },
]

export default function Sidebar() {
  const pathname       = usePathname()
  const { user }       = useAuth()
  const { hasFeature } = useFeatures()

  const navItems = allNavItems.filter(item =>
    user &&
    (item.roles as readonly string[]).includes(user.role) &&
    (!item.feature || hasFeature(item.feature))
  )

  return (
    <aside className="fixed inset-y-0 left-0 w-[var(--sidebar-width)] bg-[#0F172A] flex flex-col z-10">
      {/* Brand */}
      <div className="flex items-center gap-3 px-6 h-16 border-b border-[#1E293B]">
        <div className="w-8 h-8 bg-brand-600 rounded-lg flex items-center justify-center flex-shrink-0">
          <ScanLine size={16} className="text-white" />
        </div>
        <span className="text-white font-semibold text-base">StoreLense</span>
      </div>

      {/* Nav */}
      <nav className="flex-1 overflow-y-auto py-4 px-3">
        <ul className="space-y-0.5">
          {navItems.map(({ href, label, icon: Icon }) => {
            const active = pathname === href || pathname.startsWith(href + '/')
            return (
              <li key={href}>
                <Link
                  href={href}
                  className={cn(
                    'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors',
                    active
                      ? 'bg-brand-600 text-white'
                      : 'text-gray-400 hover:text-white hover:bg-[#1E293B]'
                  )}
                >
                  <Icon size={18} />
                  {label}
                </Link>
              </li>
            )
          })}
        </ul>
      </nav>

      {/* User info */}
      <div className="px-4 py-4 border-t border-[#1E293B]">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 bg-brand-700 rounded-full flex items-center justify-center">
            <span className="text-white text-xs font-bold">
              {user?.username?.charAt(0).toUpperCase()}
            </span>
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-white truncate">{user?.username}</p>
            <p className="text-xs text-gray-400 truncate">{user?.role}</p>
          </div>
        </div>
      </div>
    </aside>
  )
}
