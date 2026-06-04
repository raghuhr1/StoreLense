'use client'

import { QueryClientProvider } from '@tanstack/react-query'
import { ReactQueryDevtools }   from '@tanstack/react-query-devtools'
import { AuthProvider }         from '@/lib/auth/AuthContext'
import { queryClient }          from '@/lib/query/queryClient'

export default function Providers({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        {children}
      </AuthProvider>
      {process.env.NODE_ENV === 'development' && <ReactQueryDevtools initialIsOpen={false} />}
    </QueryClientProvider>
  )
}
