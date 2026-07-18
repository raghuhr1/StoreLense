'use client'

import { useQuery, useQueries }    from '@tanstack/react-query'
import { useMemo, useState }        from 'react'
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer,
  CartesianGrid, PieChart, Pie, Cell, Legend, LabelList,
} from 'recharts'
import { TrendingUp, Package, AlertTriangle, CheckCircle2, BarChart3, Layers, GitCompare } from 'lucide-react'
import Link              from 'next/link'
import Header           from '@/components/layout/Header'
import { inventoryApi } from '@/lib/api/inventory'
import { storesApi }    from '@/lib/api/stores'
import { sohApi }       from '@/lib/api/soh'
import { refillApi }    from '@/lib/api/refill'
import { productsApi }  from '@/lib/api/products'
import { reportingApi } from '@/lib/api/reporting'
import { reconciliationApi } from '@/lib/api/reconciliation'
import { useAuth }      from '@/lib/auth/AuthContext'
import { fmtPct, fmtDateTime, cn } from '@/lib/utils'
import type { Product, KpiDaily } from '@/types'

const PIE_COLORS = ['#2563eb', '#16a34a', '#d97706', '#dc2626', '#7c3aed', '#0891b2']

function iso(d: Date) { return d.toISOString().slice(0, 10) }
const TODAY = iso(new Date())
const AGO30 = iso(new Date(Date.now() - 30 * 864e5))

const selectCls = 'text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500'
const tabCls    = (active: boolean) =>
  cn('px-4 py-1.5 rounded-lg text-sm font-medium transition-colors',
    active ? 'bg-brand-600 text-white' : 'bg-white border border-gray-200 text-gray-700 hover:bg-gray-50')

export default function AnalyticsPage() {
  const { user, isAdmin } = useAuth()
  const [tab, setTab]               = useState<'store' | 'network' | 'breakdown'>('store')
  const [selStoreId, setSelStoreId] = useState<string>('')

  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })

  const storeId = isAdmin
    ? (selStoreId || allStores?.content[0]?.id || '')
    : (user?.storeId ?? '')

  const { data: items } = useQuery({
    queryKey: ['inventory', storeId],
    queryFn:  () => inventoryApi.getState(storeId),
    enabled:  !!storeId,
  })

  const { data: epcSummary } = useQuery({
    queryKey: ['epc-summary', storeId],
    queryFn:  () => inventoryApi.epcSummary(storeId),
    enabled:  !!storeId,
  })

  const { data: zones } = useQuery({
    queryKey: ['store-zones', storeId],
    queryFn:  () => storesApi.zones(storeId),
    enabled:  !!storeId,
  })

  const { data: sessions } = useQuery({
    queryKey: ['soh-sessions-analytics', storeId],
    queryFn:  () => sohApi.listSessions(storeId, { size: 100 }),
    enabled:  !!storeId,
  })

  const { data: tasks } = useQuery({
    queryKey: ['refill-tasks-analytics', storeId],
    queryFn:  () => refillApi.listTasks(storeId, { size: 100 }),
    enabled:  !!storeId,
  })

  const { data: reconciliations } = useQuery({
    queryKey: ['reconciliation-sessions', storeId],
    queryFn:  () => reconciliationApi.listByStore(storeId, { size: 8 }),
    enabled:  !!storeId,
  })

  const { data: allProducts } = useQuery({
    queryKey: ['products-all-lookup'],
    queryFn:  async () => {
      const all: Product[] = []
      let page = 0
      while (true) {
        const resp = await productsApi.list({ size: 500, page })
        if (!resp?.content?.length) break
        all.push(...resp.content)
        if (resp.last || all.length >= resp.totalElements) break
        page++
      }
      return all
    },
    staleTime: 5 * 60 * 1000,
  })

  const storeList = allStores?.content ?? []

  const storeKpis = useQueries({
    queries: tab === 'network' && isAdmin
      ? storeList.map(s => ({
          queryKey: ['kpi-range-network', s.id],
          queryFn:  () => reportingApi.kpiRange(s.id, AGO30, TODAY),
        }))
      : [],
  })

  // ── Computed values ──────────────────────────────────────────────────────────

  const productMap = useMemo(() => {
    const map: Record<string, Product> = {}
    for (const p of allProducts ?? []) map[p.id] = p
    return map
  }, [allProducts])

  // Only store-level rows (zone_id=null) for aggregate stats; zone rows used only in breakdown tab
  const storeItems = useMemo(() => (items ?? []).filter(i => i.zoneId == null), [items])

  const stockPosition = useMemo(() => {
    const list = storeItems
    let healthy = 0, overstock = 0, outOfStock = 0
    for (const item of list) {
      if (item.quantityOnHand === 0 && item.quantityExpected > 0) outOfStock++
      else if (item.quantityOnHand > item.quantityExpected && item.quantityExpected > 0) overstock++
      else if (item.quantityOnHand > 0 && item.quantityOnHand <= item.quantityExpected) healthy++
    }
    return [
      { name: 'Healthy',       value: healthy,    color: '#16a34a' },
      { name: 'Overstock',     value: overstock,  color: '#d97706' },
      { name: 'Out of Stock',  value: outOfStock, color: '#dc2626' },
    ]
  }, [storeItems])

  const brandAccuracy = useMemo(() => {
    const brandMap: Record<string, { sum: number; count: number }> = {}
    for (const item of storeItems) {
      const brand = productMap[item.productId]?.brand ?? 'Unknown'
      if (!brandMap[brand]) brandMap[brand] = { sum: 0, count: 0 }
      brandMap[brand].sum += item.quantityExpected === 0
        ? (item.quantityOnHand === 0 ? 100 : 0)
        : Math.min(100, 100 * item.quantityOnHand / item.quantityExpected)
      brandMap[brand].count++
    }
    return Object.entries(brandMap)
      .map(([brand, { sum, count }]) => ({ brand, accuracy: sum / count }))
      .sort((a, b) => b.accuracy - a.accuracy)
      .slice(0, 12)
  }, [storeItems, productMap])

  const varianceTop10 = useMemo(() => {
    return storeItems
      .map(item => {
        const variance    = item.quantityOnHand - item.quantityExpected
        const absVariance = Math.abs(variance)
        const p = productMap[item.productId]
        return { ...item, variance, absVariance, sku: p?.sku ?? item.productId.slice(-8), name: p?.name ?? '—' }
      })
      .filter(item => item.absVariance > 0)
      .sort((a, b) => b.absVariance - a.absVariance)
      .slice(0, 10)
  }, [storeItems, productMap])

  const zoneStock = useMemo(() => {
    const zoneMap: Record<string, string> = {}
    for (const z of zones ?? []) zoneMap[z.id] = z.name
    const qtyMap: Record<string, number> = {}
    for (const item of items ?? []) {
      const key = item.zoneId ? (zoneMap[item.zoneId] ?? item.zoneId) : 'Store Level'
      qtyMap[key] = (qtyMap[key] ?? 0) + item.quantityOnHand
    }
    return Object.entries(qtyMap)
      .map(([zone, qty]) => ({ zone, qty }))
      .sort((a, b) => b.qty - a.qty)
  }, [items, zones])

  const sessionsByWeek = useMemo(() => {
    const weekMap: Record<string, number> = {}
    for (const s of sessions?.content ?? []) {
      const d    = new Date(s.startedAt)
      const jan1 = new Date(d.getFullYear(), 0, 1)
      const wk   = Math.ceil(((d.getTime() - jan1.getTime()) / 864e5 + jan1.getDay() + 1) / 7)
      const key  = `W${String(wk).padStart(2, '0')}`
      weekMap[key] = (weekMap[key] ?? 0) + 1
    }
    const sorted = Object.entries(weekMap)
      .map(([week, count]) => ({ week, count }))
      .sort((a, b) => a.week.localeCompare(b.week))
      .slice(-12)
    return sorted
  }, [sessions])

  const refillStats = useMemo(() => {
    const content = tasks?.content ?? []
    const completed = content.filter(t => t.status === 'completed').length
    const pending   = content.filter(t => ['pending', 'in_progress', 'assigned'].includes(t.status)).length
    const total     = content.length
    const allItems  = content.flatMap(t => t.items)
    const totalReq  = allItems.reduce((s, i) => s + i.requestedQuantity, 0)
    const totalFul  = allItems.reduce((s, i) => s + i.fulfilledQuantity, 0)
    const fillRate  = totalReq > 0 ? (totalFul / totalReq) * 100 : 0

    const srcMap: Record<string, number> = {}
    for (const t of content) srcMap[t.source] = (srcMap[t.source] ?? 0) + 1
    const sourcePie = Object.entries(srcMap).map(([name, value]) => ({ name, value }))

    return { completed, pending, total, totalReq, totalFul, fillRate, sourcePie }
  }, [tasks])

  const networkData = useMemo(() => {
    if (!isAdmin) return []
    return storeList.map((store, i) => {
      const kpis: KpiDaily[] = storeKpis[i]?.data ?? []
      const withAcc   = kpis.filter(k => k.inventoryAccuracyPct != null)
      const accuracy  = withAcc.length > 0
        ? withAcc.reduce((s, k) => s + (k.inventoryAccuracyPct ?? 0), 0) / withAcc.length
        : null
      const totalSessions = kpis.reduce((s, k) => s + k.sohSessionsCount, 0)
      const totalVariance = kpis.reduce((s, k) => s + k.varianceItemsCount, 0)
      const reads         = Math.round(kpis.reduce((s, k) => s + k.totalEpcReads, 0) / 1000)
      const storeName     = store.name.length > 18 ? store.name.slice(0, 16) + '…' : store.name
      return { store: storeName, accuracy, sessions: totalSessions, variance: totalVariance, reads }
    }).sort((a, b) => (b.accuracy ?? 0) - (a.accuracy ?? 0))
  }, [storeList, storeKpis, isAdmin])

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

  // ── Breakdown: brand + zone metrics ─────────────────────────────────────────

  // Live per-item accuracy — not read from the stored accuracyPct, so legacy rows never
  // backfilled with a real value don't get silently excluded from averages. A product ERP
  // expects but that has never been scanned is a real 0%, not a data point to skip.
  const liveAccuracy = (onHand: number, expected: number) =>
    expected === 0 ? (onHand === 0 ? 100 : 0) : Math.min(100, 100 * onHand / expected)

  const brandMetrics = useMemo(() => {
    const map: Record<string, { sku: number; onHand: number; expected: number; accSum: number; accCount: number }> = {}
    for (const item of storeItems) {
      const brand = productMap[item.productId]?.brand ?? 'Unknown'
      if (!map[brand]) map[brand] = { sku: 0, onHand: 0, expected: 0, accSum: 0, accCount: 0 }
      map[brand].sku++
      map[brand].onHand    += item.quantityOnHand
      map[brand].expected  += item.quantityExpected
      map[brand].accSum += liveAccuracy(item.quantityOnHand, item.quantityExpected)
      map[brand].accCount++
    }
    return Object.entries(map)
      .map(([brand, m]) => ({
        brand,
        sku:      m.sku,
        onHand:   m.onHand,
        expected: m.expected,
        gap:      Math.max(0, m.expected - m.onHand),
        // Average each product's own accuracy rather than summing raw units —
        // a units ratio conflates "% scanned so far" with true accuracy.
        accuracy: m.accCount > 0 ? Math.round(m.accSum / m.accCount) : null,
      }))
      .sort((a, b) => (a.accuracy ?? 0) - (b.accuracy ?? 0))
  }, [storeItems, productMap])

  const zoneMetrics = useMemo(() => {
    const zoneMap: Record<string, { name: string; type: string }> = {}
    for (const z of zones ?? []) zoneMap[z.id] = { name: z.name, type: z.zoneType }
    const byType: Record<string, { label: string; onHand: number; expected: number; sku: number; accSum: number; accCount: number }> = {}
    for (const item of items ?? []) {  // use ALL items (zone-specific rows are what we want here)
      if (!item.zoneId) continue
      const z = zoneMap[item.zoneId]
      if (!z) continue
      if (!byType[z.type]) byType[z.type] = { label: z.name, onHand: 0, expected: 0, sku: 0, accSum: 0, accCount: 0 }
      byType[z.type].onHand   += item.quantityOnHand
      byType[z.type].expected += item.quantityExpected
      byType[z.type].sku++
      byType[z.type].accSum += liveAccuracy(item.quantityOnHand, item.quantityExpected)
      byType[z.type].accCount++
    }
    const LABELS: Record<string, string> = {
      floor: 'Sales Floor', backroom: 'Backroom', stockroom: 'Stockroom',
      fitting_room: 'Fitting Room', display: 'Display', entrance: 'Entrance',
    }
    return Object.entries(byType).map(([type, m]) => ({
      zone:     LABELS[type] ?? type,
      type,
      onHand:   m.onHand,
      expected: m.expected,
      sku:      m.sku,
      accuracy: m.accCount > 0 ? Math.round(m.accSum / m.accCount) : null,
    }))
  }, [items, zones])

  // ── Inline bar color helper ──────────────────────────────────────────────────
  function barAccuracyColor(val: number | null): string {
    if (val == null) return '#6b7280'
    if (val >= 95) return '#16a34a'
    if (val >= 80) return '#d97706'
    return '#dc2626'
  }

  const inStockCount  = stockPosition[0].value + stockPosition[1].value
  const outStockCount = stockPosition[2].value
  const totalItems    = storeItems.length
  const inStockPct    = totalItems > 0 ? Math.round((inStockCount / totalItems) * 100) : 0

  const epcEntries = Object.entries(epcSummary ?? []).map(([name, value], i) => ({
    name, value, color: PIE_COLORS[i % PIE_COLORS.length],
  }))

  const selectedStore = allStores?.content.find(s => s.id === storeId)

  return (
    <>
      <Header title="Analytics" />
      <div className="p-6 space-y-6">

        {/* Top bar */}
        <div className="flex flex-wrap items-center gap-3">
          {isAdmin && allStores && allStores.content.length > 0 && (
            <>
              <label className="text-sm font-medium text-gray-600 shrink-0">Store</label>
              <select value={storeId} onChange={e => setSelStoreId(e.target.value)} className={selectCls}>
                {allStores.content.map(s => (
                  <option key={s.id} value={s.id}>{s.name} ({s.storeCode})</option>
                ))}
              </select>
              {selectedStore && <span className="text-xs text-gray-400">{selectedStore.city ?? ''}</span>}
              <div className="h-5 w-px bg-gray-200" />
            </>
          )}
          <button onClick={() => setTab('store')}     className={tabCls(tab === 'store')}>Store View</button>
          <button onClick={() => setTab('breakdown')} className={tabCls(tab === 'breakdown')}>Breakdown</button>
          {isAdmin && (
            <button onClick={() => setTab('network')} className={tabCls(tab === 'network')}>Network View</button>
          )}
        </div>

        {/* ── STORE TAB ─────────────────────────────────────────────────────── */}
        {tab === 'store' && (
          <div className="space-y-6">

            {/* Row 0 — 4 mini stat cards */}
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
              <div className="card flex items-center gap-3">
                <div className="p-2 rounded-lg bg-blue-50"><Package size={18} className="text-blue-600" /></div>
                <div>
                  <p className="text-xs text-gray-500">Total SKUs</p>
                  <p className="text-xl font-bold text-gray-900">{totalItems}</p>
                </div>
              </div>
              <div className="card flex items-center gap-3">
                <div className="p-2 rounded-lg bg-green-50"><CheckCircle2 size={18} className="text-green-600" /></div>
                <div>
                  <p className="text-xs text-gray-500">In Stock %</p>
                  <p className="text-xl font-bold text-gray-900">{inStockPct}%</p>
                </div>
              </div>
              <div className="card flex items-center gap-3">
                <div className="p-2 rounded-lg bg-red-50"><AlertTriangle size={18} className="text-red-600" /></div>
                <div>
                  <p className="text-xs text-gray-500">Out of Stock</p>
                  <p className="text-xl font-bold text-gray-900">{outStockCount}</p>
                </div>
              </div>
              <div className="card flex items-center gap-3">
                <div className="p-2 rounded-lg bg-amber-50"><BarChart3 size={18} className="text-amber-600" /></div>
                <div>
                  <p className="text-xs text-gray-500">Variance Items</p>
                  <p className="text-xl font-bold text-gray-900">{varianceTop10.length}</p>
                </div>
              </div>
            </div>

            {/* Row 1 — 3 cols */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

              {/* Stock Position donut */}
              <div className="card">
                <h3 className="text-sm font-semibold text-gray-700 mb-3">Stock Position</h3>
                {stockPosition.every(d => d.value === 0) ? (
                  <div className="flex items-center justify-center h-[180px] text-sm text-gray-400">No data</div>
                ) : (
                  <ResponsiveContainer width="100%" height={180}>
                    <PieChart>
                      <Pie data={stockPosition} dataKey="value" nameKey="name"
                        innerRadius={55} outerRadius={80} paddingAngle={2}>
                        {stockPosition.map((entry, i) => (
                          <Cell key={i} fill={entry.color} />
                        ))}
                      </Pie>
                      <Tooltip />
                      <Legend iconSize={10} wrapperStyle={{ fontSize: 11 }} />
                    </PieChart>
                  </ResponsiveContainer>
                )}
              </div>

              {/* EPC Tag Status donut */}
              <div className="card">
                <h3 className="text-sm font-semibold text-gray-700 mb-3">EPC Tag Status</h3>
                {epcEntries.length === 0 ? (
                  <div className="flex items-center justify-center h-[180px] text-sm text-gray-400">No data</div>
                ) : (
                  <ResponsiveContainer width="100%" height={180}>
                    <PieChart>
                      <Pie data={epcEntries} dataKey="value" nameKey="name"
                        innerRadius={55} outerRadius={80} paddingAngle={2}>
                        {epcEntries.map((entry, i) => (
                          <Cell key={i} fill={entry.color} />
                        ))}
                      </Pie>
                      <Tooltip />
                      <Legend iconSize={10} wrapperStyle={{ fontSize: 11 }} />
                    </PieChart>
                  </ResponsiveContainer>
                )}
              </div>

              {/* Accuracy by Department horizontal bar */}
              <div className="card">
                <h3 className="text-sm font-semibold text-gray-700 mb-3">Accuracy by Department</h3>
                {brandAccuracy.length === 0 ? (
                  <div className="flex items-center justify-center h-[180px] text-sm text-gray-400">No data</div>
                ) : (
                  <ResponsiveContainer width="100%" height={Math.max(180, brandAccuracy.length * 28)}>
                    <BarChart layout="vertical" data={brandAccuracy} margin={{ left: 8, right: 16 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" horizontal={false} />
                      <XAxis type="number" domain={[0, 100]} unit="%" tick={{ fontSize: 10 }} />
                      <YAxis type="category" dataKey="brand" tick={{ fontSize: 10 }} width={80} />
                      <Tooltip formatter={(v: number) => [`${v.toFixed(1)}%`, 'Accuracy']} />
                      <Bar dataKey="accuracy" radius={[0, 3, 3, 0]}>
                        {brandAccuracy.map((entry, i) => (
                          <Cell key={i} fill={
                            entry.accuracy >= 95 ? '#16a34a' :
                            entry.accuracy >= 80 ? '#d97706' : '#dc2626'
                          } />
                        ))}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                )}
              </div>
            </div>

            {/* Row 2 — 2 cols */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">

              {/* Variance Top 10 table */}
              <div className="card">
                <h3 className="text-sm font-semibold text-gray-700 mb-3">Variance Top 10</h3>
                {varianceTop10.length === 0 ? (
                  <p className="text-sm text-gray-400">No variance items.</p>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-xs">
                      <thead>
                        <tr className="border-b border-gray-100">
                          <th className="text-left py-2 pr-2 font-medium text-gray-500">SKU / Product</th>
                          <th className="text-right py-2 px-2 font-medium text-gray-500">Expected</th>
                          <th className="text-right py-2 px-2 font-medium text-gray-500">On Hand</th>
                          <th className="text-right py-2 pl-2 font-medium text-gray-500">Gap</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-50">
                        {varianceTop10.map(item => (
                          <tr key={item.id}>
                            <td className="py-2 pr-2">
                              <p className="font-mono text-blue-600">{item.sku}</p>
                              <p className="text-gray-400 truncate max-w-[160px]">{item.name}</p>
                            </td>
                            <td className="text-right py-2 px-2 text-gray-700">{item.quantityExpected}</td>
                            <td className="text-right py-2 px-2 text-gray-700">{item.quantityOnHand}</td>
                            <td className={cn(
                              'text-right py-2 pl-2 font-semibold',
                              item.variance < 0 ? 'text-red-600' : 'text-amber-600',
                            )}>
                              {item.variance > 0 ? '+' : ''}{item.variance}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              {/* Stock by Zone */}
              <div className="card">
                <h3 className="text-sm font-semibold text-gray-700 mb-3">Stock by Zone</h3>
                {zoneStock.length === 0 ? (
                  <div className="flex items-center justify-center h-[200px] text-sm text-gray-400">No data</div>
                ) : (
                  <ResponsiveContainer width="100%" height={Math.max(200, zoneStock.length * 40)}>
                    <BarChart layout="vertical" data={zoneStock} margin={{ left: 8, right: 16 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" horizontal={false} />
                      <XAxis type="number" tick={{ fontSize: 10 }} />
                      <YAxis type="category" dataKey="zone" tick={{ fontSize: 10 }} width={90} />
                      <Tooltip />
                      <Bar dataKey="qty" fill="#2563eb" radius={[0, 3, 3, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                )}
              </div>
            </div>

            {/* Row 3 — 2 cols */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">

              {/* Sessions by Week */}
              <div className="card">
                <h3 className="text-sm font-semibold text-gray-700 mb-3">Cycle Count Sessions by Week</h3>
                {sessionsByWeek.length === 0 ? (
                  <div className="flex items-center justify-center h-[200px] text-sm text-gray-400">No data</div>
                ) : (
                  <ResponsiveContainer width="100%" height={200}>
                    <BarChart data={sessionsByWeek}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                      <XAxis dataKey="week" tick={{ fontSize: 11 }} />
                      <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                      <Tooltip />
                      <Bar dataKey="count" fill="#7c3aed" radius={[4, 4, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                )}
              </div>

              {/* Refill Performance */}
              <div className="card space-y-4">
                <h3 className="text-sm font-semibold text-gray-700">Refill Performance</h3>

                <div className="grid grid-cols-3 gap-3">
                  <div className="rounded-lg bg-gray-50 p-3 text-center">
                    <p className="text-xs text-gray-500">Total Tasks</p>
                    <p className="text-lg font-bold text-gray-900">{refillStats.total}</p>
                  </div>
                  <div className="rounded-lg bg-green-50 p-3 text-center">
                    <p className="text-xs text-green-700">Completed</p>
                    <p className="text-lg font-bold text-green-800">{refillStats.completed}</p>
                  </div>
                  <div className={cn('rounded-lg p-3 text-center', refillStats.pending > 0 ? 'bg-amber-50' : 'bg-gray-50')}>
                    <p className={cn('text-xs', refillStats.pending > 0 ? 'text-amber-700' : 'text-gray-500')}>Pending</p>
                    <p className={cn('text-lg font-bold', refillStats.pending > 0 ? 'text-amber-800' : 'text-gray-900')}>
                      {refillStats.pending}
                    </p>
                  </div>
                </div>

                <div>
                  <p className="text-xs text-gray-500 mb-1">Fill Rate</p>
                  <p className={cn(
                    'text-3xl font-bold',
                    refillStats.fillRate >= 90 ? 'text-green-600' :
                    refillStats.fillRate >= 70 ? 'text-amber-600' : 'text-red-600',
                  )}>
                    {refillStats.totalReq > 0 ? `${refillStats.fillRate.toFixed(1)}%` : '—'}
                  </p>
                </div>

                <div>
                  <p className="text-xs text-gray-500">
                    Units Fulfilled / Requested:{' '}
                    <span className="font-medium text-gray-700">
                      {refillStats.totalFul.toLocaleString()} / {refillStats.totalReq.toLocaleString()}
                    </span>
                  </p>
                </div>

                {refillStats.sourcePie.length > 0 && (
                  <div className="flex flex-wrap gap-2">
                    {refillStats.sourcePie.map((s, i) => (
                      <span
                        key={s.name}
                        className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium text-white"
                        style={{ backgroundColor: PIE_COLORS[i % PIE_COLORS.length] }}
                      >
                        {s.name} <span className="opacity-80">({s.value})</span>
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {/* ── BREAKDOWN TAB ────────────────────────────────────────────────── */}
        {tab === 'breakdown' && (
          <div className="space-y-8">

            {/* ─ Department Performance ───────────────────────────────────── */}
            <section>
              <div className="flex items-center gap-2 mb-4">
                <Layers size={16} className="text-brand-600" />
                <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">Department Performance</h2>
              </div>

              {brandMetrics.length === 0 ? (
                <div className="card flex items-center justify-center h-32 text-sm text-gray-400">No inventory data</div>
              ) : (
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">

                  {/* Accuracy by department */}
                  <div className="card">
                    <h3 className="text-sm font-semibold text-gray-700 mb-3">Accuracy by Department</h3>
                    <ResponsiveContainer width="100%" height={Math.max(200, brandMetrics.length * 30)}>
                      <BarChart layout="vertical" data={brandMetrics} margin={{ left: 8, right: 48 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" horizontal={false} />
                        <XAxis type="number" domain={[0, 100]} unit="%" tick={{ fontSize: 10 }} />
                        <YAxis type="category" dataKey="brand" tick={{ fontSize: 10 }} width={90} />
                        <Tooltip formatter={(v: number) => [`${v}%`, 'Accuracy']} />
                        <Bar dataKey="accuracy" radius={[0, 3, 3, 0]}>
                          <LabelList dataKey="accuracy" position="right" formatter={(v: number) => `${v}%`} style={{ fontSize: 10, fill: '#6b7280' }} />
                          {brandMetrics.map((entry, i) => (
                            <Cell key={i} fill={
                              entry.accuracy == null ? '#6b7280' :
                              entry.accuracy >= 95 ? '#16a34a' :
                              entry.accuracy >= 80 ? '#d97706' : '#dc2626'
                            } />
                          ))}
                        </Bar>
                      </BarChart>
                    </ResponsiveContainer>
                  </div>

                  {/* ERP gap by department */}
                  <div className="card">
                    <h3 className="text-sm font-semibold text-gray-700 mb-3">ERP Gap by Department (units missing)</h3>
                    <ResponsiveContainer width="100%" height={Math.max(200, brandMetrics.length * 30)}>
                      <BarChart layout="vertical" data={[...brandMetrics].sort((a, b) => b.gap - a.gap)} margin={{ left: 8, right: 48 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" horizontal={false} />
                        <XAxis type="number" tick={{ fontSize: 10 }} />
                        <YAxis type="category" dataKey="brand" tick={{ fontSize: 10 }} width={90} />
                        <Tooltip formatter={(v: number) => [v, 'Gap (units)']} />
                        <Bar dataKey="gap" fill="#dc2626" radius={[0, 3, 3, 0]}>
                          <LabelList dataKey="gap" position="right" style={{ fontSize: 10, fill: '#6b7280' }} />
                        </Bar>
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              )}

              {/* Department summary table */}
              {brandMetrics.length > 0 && (
                <div className="card mt-6 overflow-x-auto">
                  <h3 className="text-sm font-semibold text-gray-700 mb-3">Department Summary</h3>
                  <table className="w-full text-xs">
                    <thead>
                      <tr className="border-b border-gray-100">
                        <th className="text-left py-2 pr-3 font-medium text-gray-500">Department</th>
                        <th className="text-right py-2 px-2 font-medium text-gray-500">SKUs</th>
                        <th className="text-right py-2 px-2 font-medium text-gray-500">ERP Expected</th>
                        <th className="text-right py-2 px-2 font-medium text-gray-500">RFID On Hand</th>
                        <th className="text-right py-2 px-2 font-medium text-gray-500">Gap</th>
                        <th className="text-right py-2 pl-2 font-medium text-gray-500">Accuracy</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-50">
                      {[...brandMetrics].sort((a, b) => (a.accuracy ?? 0) - (b.accuracy ?? 0)).map(m => (
                        <tr key={m.brand}>
                          <td className="py-2 pr-3 font-medium text-gray-800">{m.brand}</td>
                          <td className="text-right py-2 px-2 text-gray-600">{m.sku}</td>
                          <td className="text-right py-2 px-2 text-gray-600">{m.expected.toLocaleString()}</td>
                          <td className="text-right py-2 px-2 text-gray-600">{m.onHand.toLocaleString()}</td>
                          <td className={`text-right py-2 px-2 font-semibold ${m.gap > 0 ? 'text-red-600' : 'text-gray-400'}`}>
                            {m.gap > 0 ? `-${m.gap}` : '0'}
                          </td>
                          <td className="text-right py-2 pl-2">
                            <span className={`px-1.5 py-0.5 rounded text-xs font-semibold ${
                              m.accuracy == null ? 'text-gray-400' :
                              m.accuracy >= 95 ? 'bg-green-50 text-green-700' :
                              m.accuracy >= 80 ? 'bg-amber-50 text-amber-700' : 'bg-red-50 text-red-700'
                            }`}>
                              {m.accuracy != null ? `${m.accuracy}%` : '—'}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                    <tfoot className="border-t border-gray-200">
                      <tr>
                        <td className="py-2 pr-3 font-semibold text-gray-700">Total</td>
                        <td className="text-right py-2 px-2 font-semibold text-gray-700">
                          {brandMetrics.reduce((s, m) => s + m.sku, 0)}
                        </td>
                        <td className="text-right py-2 px-2 font-semibold text-gray-700">
                          {brandMetrics.reduce((s, m) => s + m.expected, 0).toLocaleString()}
                        </td>
                        <td className="text-right py-2 px-2 font-semibold text-gray-700">
                          {brandMetrics.reduce((s, m) => s + m.onHand, 0).toLocaleString()}
                        </td>
                        <td className="text-right py-2 px-2 font-semibold text-red-600">
                          -{brandMetrics.reduce((s, m) => s + m.gap, 0).toLocaleString()}
                        </td>
                        <td className="text-right py-2 pl-2 font-semibold text-gray-700">
                          {(() => {
                            const rated = brandMetrics.filter(m => m.accuracy != null)
                            return rated.length > 0
                              ? `${Math.round(rated.reduce((s, m) => s + (m.accuracy as number), 0) / rated.length)}%`
                              : '—'
                          })()}
                        </td>
                      </tr>
                    </tfoot>
                  </table>
                </div>
              )}
            </section>

            {/* ─ Zone / Location Breakdown ────────────────────────────────── */}
            <section>
              <div className="flex items-center gap-2 mb-4">
                <Package size={16} className="text-brand-600" />
                <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">Zone Breakdown — Backroom vs Sales Floor</h2>
              </div>

              {zoneMetrics.length === 0 ? (
                <div className="card flex flex-col items-center justify-center h-32 gap-1 text-sm text-gray-400">
                  <span>No zone-level data available.</span>
                  <span className="text-xs">Re-run the seeder to populate Backroom / Sales Floor quantities.</span>
                </div>
              ) : (
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">

                  {/* Units on hand per zone */}
                  <div className="card">
                    <h3 className="text-sm font-semibold text-gray-700 mb-3">Units On Hand by Zone</h3>
                    <ResponsiveContainer width="100%" height={Math.max(160, zoneMetrics.length * 52)}>
                      <BarChart layout="vertical" data={zoneMetrics} margin={{ left: 8, right: 48 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" horizontal={false} />
                        <XAxis type="number" tick={{ fontSize: 10 }} />
                        <YAxis type="category" dataKey="zone" tick={{ fontSize: 11, fontWeight: 500 }} width={90} />
                        <Tooltip formatter={(v: number) => [v.toLocaleString(), 'Units']} />
                        <Bar dataKey="onHand" radius={[0, 3, 3, 0]}>
                          <LabelList dataKey="onHand" position="right" formatter={(v: number) => v.toLocaleString()} style={{ fontSize: 10, fill: '#6b7280' }} />
                          {zoneMetrics.map((entry, i) => (
                            <Cell key={i} fill={
                              entry.type === 'floor' ? '#2563eb' :
                              entry.type === 'backroom' ? '#7c3aed' :
                              entry.type === 'fitting_room' ? '#0891b2' : '#16a34a'
                            } />
                          ))}
                        </Bar>
                      </BarChart>
                    </ResponsiveContainer>
                  </div>

                  {/* SKU count per zone */}
                  <div className="card">
                    <h3 className="text-sm font-semibold text-gray-700 mb-3">SKU Count by Zone</h3>
                    <ResponsiveContainer width="100%" height={Math.max(160, zoneMetrics.length * 52)}>
                      <BarChart layout="vertical" data={zoneMetrics} margin={{ left: 8, right: 48 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" horizontal={false} />
                        <XAxis type="number" tick={{ fontSize: 10 }} />
                        <YAxis type="category" dataKey="zone" tick={{ fontSize: 11, fontWeight: 500 }} width={90} />
                        <Tooltip formatter={(v: number) => [v, 'SKUs']} />
                        <Bar dataKey="sku" fill="#d97706" radius={[0, 3, 3, 0]}>
                          <LabelList dataKey="sku" position="right" style={{ fontSize: 10, fill: '#6b7280' }} />
                        </Bar>
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              )}

              {/* Zone summary table */}
              {zoneMetrics.length > 0 && (
                <div className="card mt-6 overflow-x-auto">
                  <h3 className="text-sm font-semibold text-gray-700 mb-3">Zone Summary</h3>
                  <table className="w-full text-xs">
                    <thead>
                      <tr className="border-b border-gray-100">
                        <th className="text-left py-2 pr-3 font-medium text-gray-500">Zone</th>
                        <th className="text-right py-2 px-2 font-medium text-gray-500">SKUs</th>
                        <th className="text-right py-2 px-2 font-medium text-gray-500">On Hand</th>
                        <th className="text-right py-2 px-2 font-medium text-gray-500">Expected</th>
                        <th className="text-right py-2 pl-2 font-medium text-gray-500">Accuracy</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-50">
                      {zoneMetrics.map(z => (
                        <tr key={z.zone}>
                          <td className="py-2 pr-3">
                            <span className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-medium ${
                              z.type === 'floor'        ? 'bg-blue-50 text-blue-700' :
                              z.type === 'backroom'     ? 'bg-purple-50 text-purple-700' :
                              z.type === 'fitting_room' ? 'bg-cyan-50 text-cyan-700' : 'bg-gray-100 text-gray-600'
                            }`}>{z.zone}</span>
                          </td>
                          <td className="text-right py-2 px-2 text-gray-600">{z.sku}</td>
                          <td className="text-right py-2 px-2 text-gray-600">{z.onHand.toLocaleString()}</td>
                          <td className="text-right py-2 px-2 text-gray-600">{z.expected.toLocaleString()}</td>
                          <td className="text-right py-2 pl-2">
                            <span className={`px-1.5 py-0.5 rounded text-xs font-semibold ${
                              z.accuracy == null ? 'text-gray-400' :
                              z.accuracy >= 95 ? 'bg-green-50 text-green-700' :
                              z.accuracy >= 80 ? 'bg-amber-50 text-amber-700' : 'bg-red-50 text-red-700'
                            }`}>{z.accuracy != null ? `${z.accuracy}%` : '—'}</span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

            {/* ─ Recent Reconciliations ───────────────────────────────────── */}
            <section>
              <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-2">
                  <GitCompare size={16} className="text-brand-600" />
                  <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">Recent Reconciliations</h2>
                </div>
                <Link href="/variance" className="text-xs font-medium text-brand-600 hover:underline">
                  View All →
                </Link>
              </div>

              {!reconciliations || reconciliations.content.length === 0 ? (
                <div className="card flex items-center justify-center h-32 text-sm text-gray-400">
                  No reconciliation runs yet — completing a SOH session runs one automatically.
                </div>
              ) : (
                <div className="card overflow-x-auto">
                  <table className="w-full text-xs">
                    <thead>
                      <tr className="border-b border-gray-100">
                        <th className="text-left py-2 pr-3 font-medium text-gray-500">Session / Count</th>
                        <th className="text-left py-2 px-2 font-medium text-gray-500">Run Date</th>
                        <th className="text-right py-2 px-2 font-medium text-gray-500">Matched %</th>
                        <th className="text-right py-2 px-2 font-medium text-gray-500">Missing</th>
                        <th className="text-right py-2 px-2 font-medium text-gray-500">Extra</th>
                        <th className="text-left py-2 pl-2 font-medium text-gray-500">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-50">
                      {reconciliations.content.map(r => (
                        <tr key={r.id}>
                          <td className="py-2 pr-3">
                            {r.sessionId ? (
                              <Link href={`/variance/${r.sessionId}`} className="font-mono text-brand-600 hover:underline">
                                {r.sessionId.slice(-8)}
                              </Link>
                            ) : (
                              <Link href={`/cycle-count/${r.cycleCountId}/reconcile`} className="font-mono text-purple-600 hover:underline">
                                CC·{r.cycleCountId?.slice(-8)}
                              </Link>
                            )}
                          </td>
                          <td className="py-2 px-2 text-gray-600">{fmtDateTime(r.runAt)}</td>
                          <td className="text-right py-2 px-2 font-semibold">
                            <span className={
                              r.accuracyPct == null ? 'text-gray-400' :
                              r.accuracyPct >= 95 ? 'text-green-600' :
                              r.accuracyPct >= 80 ? 'text-yellow-600' : 'text-red-600'
                            }>{fmtPct(r.accuracyPct)}</span>
                          </td>
                          <td className="text-right py-2 px-2">
                            <span className={r.missingCount > 0 ? 'text-red-600 font-medium' : 'text-gray-500'}>
                              {r.missingCount}
                            </span>
                          </td>
                          <td className="text-right py-2 px-2">
                            <span className={r.extraCount > 0 ? 'text-yellow-600 font-medium' : 'text-gray-500'}>
                              {r.extraCount}
                            </span>
                          </td>
                          <td className="py-2 pl-2">
                            <span className={`px-1.5 py-0.5 rounded text-xs font-semibold ${
                              r.status === 'COMPLETED' || r.status === 'APPROVED' ? 'bg-green-50 text-green-700' :
                              r.status === 'FAILED' ? 'bg-red-50 text-red-700' : 'bg-amber-50 text-amber-700'
                            }`}>{r.status}</span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

          </div>
        )}

        {/* ── NETWORK TAB ───────────────────────────────────────────────────── */}
        {tab === 'network' && isAdmin && (
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
                  <ResponsiveContainer width="100%" height={Math.max(200, networkData.length * 36)}>
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
                  <ResponsiveContainer width="100%" height={Math.max(200, networkData.length * 36)}>
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
                  <ResponsiveContainer width="100%" height={Math.max(200, networkData.length * 36)}>
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
                  <ResponsiveContainer width="100%" height={Math.max(200, networkData.length * 36)}>
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
        )}

      </div>
    </>
  )
}
