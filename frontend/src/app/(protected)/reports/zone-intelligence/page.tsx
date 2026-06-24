'use client'

import { useQuery }    from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid,
  ResponsiveContainer, Legend, LineChart, Line,
} from 'recharts'
import { AlertTriangle, TrendingDown, CheckCircle2, TrendingUp, ArrowLeft } from 'lucide-react'
import Link              from 'next/link'
import Header            from '@/components/layout/Header'
import { storesApi }     from '@/lib/api/stores'
import { zoneIntelligenceApi } from '@/lib/api/inventory'
import { useAuth }       from '@/lib/auth/AuthContext'
import { fmtDateTime }   from '@/lib/utils'
import type { Zone, ZoneHealthSummary } from '@/types'

const selectCls = 'text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500'

// ── Zone health card ──────────────────────────────────────────────────────────
function ZoneCard({ z }: { z: ZoneHealthSummary }) {
  const total = z.totalProducts || 1
  const critPct    = Math.round(z.criticalCount / total * 100)
  const lowPct     = Math.round(z.lowCount / total * 100)
  const okPct      = Math.round(z.okCount / total * 100)
  const surplusPct = Math.round(z.surplusCount / total * 100)

  const healthScore = okPct + surplusPct
  const scoreCls = healthScore === 100
    ? 'text-green-600' : healthScore >= 70
    ? 'text-amber-500' : 'text-red-600'

  return (
    <div className="card space-y-3">
      <div className="flex items-start justify-between">
        <div>
          <h3 className="text-sm font-semibold text-gray-900">{z.zoneName ?? z.zoneId}</h3>
          <p className="text-xs text-gray-400">{z.totalProducts} product{z.totalProducts !== 1 ? 's' : ''} tracked</p>
        </div>
        <span className={`text-2xl font-bold ${scoreCls}`}>{healthScore}%</span>
      </div>

      {/* Stacked progress bar */}
      <div className="flex h-2 rounded-full overflow-hidden gap-0.5">
        {critPct    > 0 && <div style={{ width: `${critPct}%` }}    className="bg-red-500" title={`Critical: ${z.criticalCount}`} />}
        {lowPct     > 0 && <div style={{ width: `${lowPct}%` }}     className="bg-amber-400" title={`Low: ${z.lowCount}`} />}
        {okPct      > 0 && <div style={{ width: `${okPct}%` }}      className="bg-green-400" title={`OK: ${z.okCount}`} />}
        {surplusPct > 0 && <div style={{ width: `${surplusPct}%` }} className="bg-blue-300" title={`Surplus: ${z.surplusCount}`} />}
      </div>

      <div className="grid grid-cols-4 text-center text-[11px] gap-1">
        {[
          { icon: AlertTriangle, count: z.criticalCount, label: 'Critical', cls: 'text-red-600' },
          { icon: TrendingDown,  count: z.lowCount,      label: 'Low',      cls: 'text-amber-600' },
          { icon: CheckCircle2,  count: z.okCount,       label: 'OK',       cls: 'text-green-600' },
          { icon: TrendingUp,    count: z.surplusCount,  label: 'Surplus',  cls: 'text-blue-500' },
        ].map(({ icon: Icon, count, label, cls }) => (
          <div key={label} className={`${cls} font-semibold`}>
            <Icon size={13} className="mx-auto mb-0.5" />
            <p className="text-base font-bold">{count}</p>
            <p className="text-gray-400 font-normal">{label}</p>
          </div>
        ))}
      </div>
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────
export default function ZoneIntelligencePage() {
  const { user, isAdmin } = useAuth()

  const [selectedStoreId, setSelectedStoreId] = useState('')
  const [selectedZoneId,  setSelectedZoneId]  = useState('')
  const [trendDays,       setTrendDays]       = useState(30)
  const [freqDays,        setFreqDays]        = useState(30)

  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })

  const storeId = isAdmin
    ? (selectedStoreId || allStores?.content[0]?.id || '')
    : (user?.storeId ?? '')

  const { data: zones = [] } = useQuery<Zone[]>({
    queryKey: ['zones', storeId],
    queryFn:  () => storesApi.zones(storeId),
    enabled:  !!storeId,
  })

  const { data: zoneHealth = [], isLoading: healthLoading } = useQuery({
    queryKey: ['zone-health', storeId],
    queryFn:  () => zoneIntelligenceApi.zoneHealth(storeId),
    enabled:  !!storeId,
  })

  const { data: frequency = [], isLoading: freqLoading } = useQuery({
    queryKey: ['product-frequency', storeId, freqDays],
    queryFn:  () => zoneIntelligenceApi.productFrequency(storeId, freqDays, 15),
    enabled:  !!storeId,
  })

  const { data: trend = [], isLoading: trendLoading } = useQuery({
    queryKey: ['zone-trend', storeId, selectedZoneId, trendDays],
    queryFn:  () => zoneIntelligenceApi.zoneTrend(storeId, selectedZoneId || undefined, trendDays),
    enabled:  !!storeId,
  })

  // Store-wide totals
  const totals = useMemo(() => ({
    critical: zoneHealth.reduce((s, z) => s + z.criticalCount, 0),
    low:      zoneHealth.reduce((s, z) => s + z.lowCount,      0),
    ok:       zoneHealth.reduce((s, z) => s + z.okCount,       0),
    surplus:  zoneHealth.reduce((s, z) => s + z.surplusCount,  0),
  }), [zoneHealth])

  const trendData = trend.map(t => ({
    day:      t.day.slice(5),
    Critical: t.criticalCount,
    Low:      t.lowCount,
    OK:       t.okCount,
    Surplus:  t.surplusCount,
  }))

  const freqData = frequency.map(f => ({
    name:   f.sku ?? f.productId.slice(-8),
    refills: f.refillCount,
    units:   f.totalUnitsRequested,
  }))

  return (
    <>
      <Header title="Zone Intelligence" />
      <div className="p-6 space-y-6">

        <Link href="/reports" className="inline-flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-800">
          <ArrowLeft size={14} /> Back to Reports
        </Link>

        {/* Controls */}
        <div className="flex flex-wrap items-center gap-3">
          {isAdmin && allStores && (
            <>
              <label className="text-sm font-medium text-gray-600 shrink-0">Store</label>
              <select value={storeId} onChange={e => setSelectedStoreId(e.target.value)} className={selectCls}>
                {allStores.content.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </>
          )}
        </div>

        {/* Store-wide health summary */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          {[
            { label: 'Critical SKUs', value: totals.critical, cls: totals.critical > 0 ? 'text-red-600' : 'text-gray-900',   icon: AlertTriangle },
            { label: 'Low Stock SKUs',  value: totals.low,      cls: totals.low > 0      ? 'text-amber-600' : 'text-gray-900', icon: TrendingDown  },
            { label: 'At Par / OK',     value: totals.ok,       cls: 'text-green-600',                                         icon: CheckCircle2  },
            { label: 'Surplus SKUs',    value: totals.surplus,  cls: 'text-blue-500',                                          icon: TrendingUp    },
          ].map(s => {
            const Icon = s.icon
            return (
              <div key={s.label} className="card flex items-center gap-3">
                <Icon size={18} className={s.cls} />
                <div>
                  <p className={`text-2xl font-bold ${s.cls}`}>{s.value}</p>
                  <p className="text-xs text-gray-500">{s.label}</p>
                </div>
              </div>
            )
          })}
        </div>

        {/* Per-zone health grid */}
        <div>
          <h2 className="text-sm font-semibold text-gray-700 mb-3">Zone Health (live)</h2>
          {healthLoading ? (
            <p className="text-sm text-gray-400">Loading…</p>
          ) : zoneHealth.length === 0 ? (
            <div className="card py-8 text-center text-sm text-gray-400">
              No par levels configured. Add par levels in Store Settings to see zone health here.
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {zoneHealth.map(z => <ZoneCard key={z.zoneId} z={z} />)}
            </div>
          )}
        </div>

        {/* Trend chart */}
        <div className="card">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-semibold text-gray-700">Zone Health Trend (from scan snapshots)</h2>
            <div className="flex gap-2">
              <select value={selectedZoneId} onChange={e => setSelectedZoneId(e.target.value)} className={selectCls}>
                <option value="">All Zones</option>
                {zones.map(z => <option key={z.id} value={z.id}>{z.name}</option>)}
              </select>
              {([30, 60, 90] as const).map(d => (
                <button key={d} onClick={() => setTrendDays(d)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-medium ${
                    trendDays === d ? 'bg-brand-600 text-white' : 'border border-gray-200 text-gray-600'
                  }`}>{d}d</button>
              ))}
            </div>
          </div>
          {trendLoading ? (
            <div className="h-[220px] flex items-center justify-center text-sm text-gray-400">Loading…</div>
          ) : trendData.length === 0 ? (
            <div className="h-[220px] flex items-center justify-center text-sm text-gray-400">
              No snapshot history yet. Run "Compute & Save" from the Zone Stock Comparison page after each scan session.
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <LineChart data={trendData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="day" tick={{ fontSize: 10 }} interval="preserveStartEnd" />
                <YAxis tick={{ fontSize: 10 }} allowDecimals={false} />
                <Tooltip />
                <Legend wrapperStyle={{ fontSize: 11 }} />
                <Line type="monotone" dataKey="Critical" stroke="#ef4444" strokeWidth={2} dot={false} />
                <Line type="monotone" dataKey="Low"      stroke="#f59e0b" strokeWidth={2} dot={false} />
                <Line type="monotone" dataKey="OK"       stroke="#22c55e" strokeWidth={1.5} dot={false} strokeDasharray="4 2" />
              </LineChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* Product replenishment frequency */}
        <div className="card">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-sm font-semibold text-gray-700">Top Products by Replenishment Frequency</h2>
              <p className="text-xs text-gray-400 mt-0.5">Auto-triggered refill tasks only (source = RFID Alert)</p>
            </div>
            <div className="flex gap-2">
              {([30, 60, 90] as const).map(d => (
                <button key={d} onClick={() => setFreqDays(d)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-medium ${
                    freqDays === d ? 'bg-brand-600 text-white' : 'border border-gray-200 text-gray-600'
                  }`}>{d}d</button>
              ))}
            </div>
          </div>

          {freqLoading ? (
            <div className="h-[200px] flex items-center justify-center text-sm text-gray-400">Loading…</div>
          ) : freqData.length === 0 ? (
            <div className="h-[200px] flex items-center justify-center text-sm text-gray-400">
              No RFID-triggered refill tasks in the selected period.
            </div>
          ) : (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <ResponsiveContainer width="100%" height={200}>
                <BarChart data={freqData} layout="vertical">
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis type="number" tick={{ fontSize: 10 }} allowDecimals={false} />
                  <YAxis type="category" dataKey="name" tick={{ fontSize: 10 }} width={70} />
                  <Tooltip />
                  <Bar dataKey="refills" fill="#f59e0b" radius={[0, 3, 3, 0]} name="Refill count" />
                </BarChart>
              </ResponsiveContainer>

              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-100">
                      <th className="text-left py-2 text-xs font-semibold text-gray-500 uppercase tracking-wide">Product</th>
                      <th className="text-right py-2 text-xs font-semibold text-gray-500 uppercase tracking-wide">Refills</th>
                      <th className="text-right py-2 text-xs font-semibold text-gray-500 uppercase tracking-wide">Units</th>
                      <th className="text-right py-2 text-xs font-semibold text-gray-500 uppercase tracking-wide">Last</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-50">
                    {frequency.map(f => (
                      <tr key={f.productId} className="hover:bg-gray-50">
                        <td className="py-2">
                          <p className="font-medium text-gray-900">{f.productName ?? '—'}</p>
                          <p className="font-mono text-xs text-gray-400">{f.sku ?? '—'}</p>
                        </td>
                        <td className="py-2 text-right font-mono font-bold text-amber-600">{f.refillCount}</td>
                        <td className="py-2 text-right font-mono text-gray-700">{f.totalUnitsRequested}</td>
                        <td className="py-2 text-right text-xs text-gray-400">{fmtDateTime(f.lastRefillAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>

      </div>
    </>
  )
}
