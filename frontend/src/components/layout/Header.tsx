'use client'

import { useRouter }  from 'next/navigation'
import { Bell, LogOut } from 'lucide-react'
import { useAuth }    from '@/lib/auth/AuthContext'

interface Props { title: string }

export default function Header({ title }: Props) {
  const router      = useRouter()
  const { user, logout } = useAuth()

  const handleLogout = async () => {
    await logout()
    router.replace('/login')
  }

  return (
    <header className="fixed top-0 right-0 left-[var(--sidebar-width)] h-16 bg-white border-b border-gray-200 flex items-center justify-between px-6 z-10">
      <h1 className="text-lg font-semibold text-gray-900">{title}</h1>

      <div className="flex items-center gap-3">
        {user?.storeId && (
          <span className="text-xs text-gray-500 bg-gray-100 px-2 py-1 rounded">
            Store: {user.storeId.slice(-8)}
          </span>
        )}
        <button className="p-2 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-lg">
          <Bell size={18} />
        </button>
        <button
          onClick={handleLogout}
          className="flex items-center gap-2 px-3 py-1.5 text-sm text-gray-600
                     hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
        >
          <LogOut size={16} />
          Sign out
        </button>
      </div>
    </header>
  )
}
