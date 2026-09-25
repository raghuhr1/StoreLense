'use client'

import { useEffect }        from 'react'
import { usePathname, useRouter } from 'next/navigation'
import Sidebar              from '@/components/layout/Sidebar'
import { useAuth }          from '@/lib/auth/AuthContext'
import { FeaturesProvider } from '@/lib/features/FeaturesContext'

// The live gate-alarm screen — a full-bleed kiosk view with its own header,
// not the standard admin Header component, hence no top padding below.
const GUARD_HOME = '/gate-alarms/live'

export default function ProtectedLayout({ children }: { children: React.ReactNode }) {
  const router        = useRouter()
  const pathname       = usePathname()
  const { isAuthed, isLoading, user, isAdmin } = useAuth()
  const isGuard = user?.role === 'SECURITY_GUARD'

  useEffect(() => {
    if (!isLoading && !isAuthed) router.replace('/login')
  }, [isAuthed, isLoading, router])

  // Security guards get a single-purpose view — no sidebar, no other pages.
  // Any attempt to navigate elsewhere (typed URL, back button, stale link)
  // bounces straight back to the live gate-alarm screen.
  useEffect(() => {
    if (!isLoading && isAuthed && isGuard && pathname !== GUARD_HOME) {
      router.replace(GUARD_HOME)
    }
  }, [isLoading, isAuthed, isGuard, pathname, router])

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="w-8 h-8 border-4 border-brand-600 border-t-transparent rounded-full animate-spin" />
      </div>
    )
  }

  if (!isAuthed) return null
  if (isGuard && pathname !== GUARD_HOME) return null

  if (isGuard) {
    return (
      <FeaturesProvider storeId={user?.storeId ?? null} isAdmin={false}>
        <div style={{ ['--sidebar-width' as string]: '0px' }}>
          {children}
        </div>
      </FeaturesProvider>
    )
  }

  return (
    <FeaturesProvider storeId={user?.storeId ?? null} isAdmin={isAdmin}>
      <div className="min-h-screen">
        <Sidebar />
        <div className="pl-[var(--sidebar-width)]">
          <main className="pt-16 min-h-screen bg-gray-50">
            {children}
          </main>
        </div>
      </div>
    </FeaturesProvider>
  )
}
