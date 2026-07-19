'use client'

import { useQuery, useQueries } from '@tanstack/react-query'
import { useMemo } from 'react'
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Cell,
} from 'recharts'
import { TrendingUp, BarChart3, CheckCircle2, Package } from 'lucide-react'
import { storesApi }    from '@/lib/api/stores'
import { reportingApi } from '@/lib/api/reporting'
import { fmtPct } from '@/lib/utils'
import type { KpiDaily } from '@/types'

function iso(d: Date) { return d.toISOString().slice(0, 10) }
const TODAY = iso(new Date())
const AGO30 = iso(new Date(Date.now() - 30 * 864e5))

function barAccuracyColor(val: number | null): string {
  if (val == null) return '#6b7280'
  if (val >= 95) return '#16a34a'
  if (val >= 80) return '#d97706'
  return '#dc2626'
}

/**
 * Cross-store network overview — shared between the Dashboard's "no store picked yet"
 * landing state and Analytics' Network tab, so both render identically off one
 * implementation instead of maintaining two copies of the same cards/charts.
 */
export default function NetworkOverview() {
  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
  })
  const storeList = allStores?.content ?? []

  const storeKpis = useQueries({
    queries: storeList.map(s => ({
      queryKey: ['kpi-range-network', s.id],
      queryFn:  () => reportingApi.kpiRange(s.id, AGO30, TODAY),
    })),
  })

  const networkData = useMemo(() => {
    return storeList.map((store, i) => {
      const kpis: KpiDaily[] = storeKpis[i]?.data ?? []
      const withAcc  = kpis.filter(k => k.inventoryAccuracyPct != null)
      const accuracy = withAcc.length > 0
        ? withAcc.reduce((s, k) => s + (k.inventoryAccuracyPct ?? 0), 0) / withAcc.length
        : null
      const totalSessions = kpis.reduce((s, k) => s + k.sohSessionsCount, 0)
      const totalVariance = kpis.reduce((s, k) => s + k.varianceItemsCount, 0)
      const reads         = Math.round(kpis.reduce((s, k) => s + k.totalEpcReads, 0) / 1000)
      const storeName     = store.name.length > 18 ? store.name.slice(0, 16) + '…' : store.name
      return { store: storeName, accuracy, sessions: totalSessions, variance: totalVariance, reads }
    }).sort((a, b) => (b.accuracy ?? 0) - (a.accuracy ?? 0))
  }, [storeList, storeKpis])

  const networkLoading = storeKpis.some(q => q.isLoading)

  const storesAbove95 = useMemo(
    () => networkData.filter(d => (d.accuracy ?? 0) >= 95).length,
    [networkData],
  )

  const avgNetAccuracy = useMemo(() => {
    const withVal = networkData.filter(d => d.accuracy != null)
    return withVal.length > 0
      ? withVal.reduce((s, d) => s + (d.accuracy ?? 0), 0) / withVal.length
      : null
  }, [networkData])

  const totalEpcReads30d = useMemo(
    () => networkData.reduce((s, d) => s + d.reads, 0),
    [networkData],
  )

  const chartHeight = Math.max(200, networkData.length * 36)

  return (
    <div className="space-y-6">
      {/* Row 0 — 4 summary cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="card flex items-center gap-3">
          <div className="p-2 rounded-lg bg-blue-50"><TrendingUp size={18} className="text-blue-600" /></div>
          <div>
            <p className="text-xs text-gray-500">Active Stores</p>
            <p className="text-xl font-bold text-gray-900">{storeList.length}</p>
          </div>
        </div>
        <div className="card flex items-center gap-3">
          <div className="p-2 rounded-lg bg-green-50"><BarChart3 size={18} className="text-green-600" /></div>
          <div>
            <p className="text-xs text-gray-500">Avg Network Accuracy</p>
            <p className="text-xl font-bold text-gray-900">{fmtPct(avgNetAccuracy)}</p>
          </div>
        </div>
        <div className="card flex items-center gap-3">
          <div className="p-2 rounded-lg bg-green-50"><CheckCircle2 size={18} className="text-green-600" /></div>
          <div>
            <p className="text-xs text-gray-500">Stores ≥ 95%</p>
            <p className="text-xl font-bold text-gray-900">{storesAbove95} / {storeList.length}</p>
          </div>
        </div>
        <div className="card flex items-center gap-3">
          <div className="p-2 rounded-lg bg-cyan-50"><Package size={18} className="text-cyan-600" /></div>
          <div>
            <p className="text-xs text-gray-500">Total EPC Reads 30d</p>
            <p className="text-xl font-bold text-gray-900">{totalEpcReads30d}K</p>
          </div>
        </div>
      </div>

      {/* Row 1 — accuracy + sessions */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="card">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">Store Accuracy Ranking</h3>
          {networkLoading ? (
            <div className="flex items-center justify-center h-[200px] text-sm text-gray-400">Loading…</div>
          ) : networkData.length === 0 ? (
            <div className="flex items-center justify-center h-[200px] text-sm text-gray-400">No data</div>
          ) : (
            <ResponsiveContainer width="100%" height={chartHeight}>
              <BarChart layout="vertical" data={networkData} margin={{ left: 8, right: 24 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" horizontal={false} />
                <XAxis type="number" domain={[0, 100]} unit="%" tick={{ fontSize: 10 }} />
                <YAxis type="category" dataKey="store" tick={{ fontSize: 10 }} width={90} />
                <Tooltip formatter={(v: number) => [`${v.toFixed(1)}%`, 'Accuracy']} />
                <Bar dataKey="accuracy" radius={[0, 3, 3, 0]}>
                  {networkData.map((entry, i) => (
                    <Cell key={i} fill={barAccuracyColor(entry.accuracy)} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="card">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">SOH Sessions per Store</h3>
          {networkLoading ? (
            <div className="flex items-center justify-center h-[200px] text-sm text-gray-400">Loading…</div>
          ) : networkData.length === 0 ? (
            <div className="flex items-center justify-center h-[200px] text-sm text-gray-400">No data</div>
          ) : (
            <ResponsiveContainer width="100%" height={chartHeight}>
              <BarChart layout="vertical" data={networkData} margin={{ left: 8, right: 24 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" horizontal={false} />
                <XAxis type="number" tick={{ fontSize: 10 }} />
                <YAxis type="category" dataKey="store" tick={{ fontSize: 10 }} width={90} />
                <Tooltip />
                <Bar dataKey="sessions" fill="#7c3aed" radius={[0, 3, 3, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      {/* Row 2 — variance + EPC reads */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="card">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">Variance Items per Store</h3>
          {networkLoading ? (
            <div className="flex items-center justify-center h-[200px] text-sm text-gray-400">Loading…</div>
          ) : networkData.length === 0 ? (
            <div className="flex items-center justify-center h-[200px] text-sm text-gray-400">No data</div>
          ) : (
            <ResponsiveContainer width="100%" height={chartHeight}>
              <BarChart layout="vertical" data={networkData} margin={{ left: 8, right: 24 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" horizontal={false} />
                <XAxis type="number" tick={{ fontSize: 10 }} />
                <YAxis type="category" dataKey="store" tick={{ fontSize: 10 }} width={90} />
                <Tooltip />
                <Bar dataKey="variance" fill="#dc2626" radius={[0, 3, 3, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="card">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">EPC Reads per Store (K)</h3>
          {networkLoading ? (
            <div className="flex items-center justify-center h-[200px] text-sm text-gray-400">Loading…</div>
          ) : networkData.length === 0 ? (
            <div className="flex items-center justify-center h-[200px] text-sm text-gray-400">No data</div>
          ) : (
            <ResponsiveContainer width="100%" height={chartHeight}>
              <BarChart layout="vertical" data={networkData} margin={{ left: 8, right: 24 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" horizontal={false} />
                <XAxis type="number" tick={{ fontSize: 10 }} unit="K" />
                <YAxis type="category" dataKey="store" tick={{ fontSize: 10 }} width={90} />
                <Tooltip formatter={(v: number) => [`${v}K`, 'EPC Reads']} />
                <Bar dataKey="reads" fill="#0891b2" radius={[0, 3, 3, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>
    </div>
  )
}
