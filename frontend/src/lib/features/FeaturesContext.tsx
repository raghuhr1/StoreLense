'use client'

import { createContext, useContext, useState, useEffect, ReactNode } from 'react'
import { storesApi } from '@/lib/api/stores'
import type { Feature } from '@/types'

interface FeaturesContextType {
  enabledFeatures: Set<Feature>
  isLoading: boolean
  hasFeature: (f: Feature) => boolean
  reload: (storeId: string) => Promise<void>
}

const FeaturesContext = createContext<FeaturesContextType | null>(null)

// ADMIN users have all features enabled — they should never be blocked
const ALL_FEATURES = new Set<Feature>([
  'INVENTORY', 'INBOUND', 'REPLENISHMENT', 'CYCLE_COUNT',
  'TRANSFERS', 'ANALYTICS', 'SALES', 'DEVICES', 'ERP_INTEGRATION',
])

interface Props {
  children: ReactNode
  storeId: string | null
  isAdmin: boolean
}

export function FeaturesProvider({ children, storeId, isAdmin }: Props) {
  const [enabledFeatures, setEnabledFeatures] = useState<Set<Feature>>(
    isAdmin ? ALL_FEATURES : new Set()
  )
  const [isLoading, setIsLoading] = useState(!isAdmin && !!storeId)

  const load = async (sid: string) => {
    try {
      setIsLoading(true)
      const list = await storesApi.getFeatures(sid)
      setEnabledFeatures(
        new Set(list.filter(f => f.enabled).map(f => f.feature))
      )
    } catch {
      // On error, default to all features enabled so users aren't locked out
      setEnabledFeatures(ALL_FEATURES)
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    if (isAdmin) {
      setEnabledFeatures(ALL_FEATURES)
      setIsLoading(false)
      return
    }
    if (storeId) {
      load(storeId)
    }
  }, [storeId, isAdmin])

  const hasFeature = (f: Feature) => isAdmin || enabledFeatures.has(f)

  const reload = async (sid: string) => { await load(sid) }

  return (
    <FeaturesContext.Provider value={{ enabledFeatures, isLoading, hasFeature, reload }}>
      {children}
    </FeaturesContext.Provider>
  )
}

export function useFeatures() {
  const ctx = useContext(FeaturesContext)
  if (!ctx) throw new Error('useFeatures must be used within FeaturesProvider')
  return ctx
}
