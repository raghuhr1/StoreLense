'use client'

import { useEffect }        from 'react'
import { useRouter }        from 'next/navigation'
import Sidebar              from '@/components/layout/Sidebar'
import { useAuth }          from '@/lib/auth/AuthContext'
import { FeaturesProvider } from '@/lib/features/FeaturesContext'

export default function ProtectedLayout({ children }: { children: React.ReactNode }) {
  const router        = useRouter()
  const { isAuthed, isLoading, user, isAdmin } = useAuth()

  useEffect(() => {
    if (!isLoading && !isAuthed) router.replace('/login')
  }, [isAuthed, isLoading, router])

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="w-8 h-8 border-4 border-brand-600 border-t-transparent rounded-full animate-spin" />
      </div>
    )
  }

  if (!isAuthed) return null

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
