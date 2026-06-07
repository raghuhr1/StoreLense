'use client'

import { useQuery, useQueries }    from '@tanstack/react-query'
import { useMemo, useState }        from 'react'
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer,
  CartesianGrid, PieChart, Pie, Cell, Legend,
} from 'recharts'
import { TrendingUp, Package, AlertTriangle, CheckCircle2, BarChart3 } from 'lucide-react'
import Header           from '@/components/layout/Header'
import { inventoryApi } from '@/lib/api/inventory'
import { storesApi }    from '@/lib/api/stores'
import { sohApi }       from '@/lib/api/soh'
import { refillApi }    from '@/lib/api/refill'
import { productsApi }  from '@/lib/api/products'
import { reportingApi } from '@/lib/api/reporting'
import { useAuth }      from '@/lib/auth/AuthContext'
import { fmtPct, cn }  from '@/lib/utils'
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
  const [tab, setTab]               = useState<'store' | 'network'>('store')
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

  const stockPosition = useMemo(() => {
    const list = items ?? []
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
  }, [items])

  const brandAccuracy = useMemo(() => {
    const brandMap: Record<string, { sum: number; count: number }> = {}
    for (const item of items ?? []) {
      if (item.accuracyPct == null) continue
      const brand = productMap[item.productId]?.brand ?? 'Unknown'
      if (!brandMap[brand]) brandMap[brand] = { sum: 0, count: 0 }
      brandMap[brand].sum += item.accuracyPct
      brandMap[brand].count++
    }
    return Object.entries(brandMap)
      .map(([brand, { sum, count }]) => ({ brand, accuracy: sum / count }))
      .sort((a, b) => b.accuracy - a.accuracy)
      .slice(0, 12)
  }, [items, productMap])

  const varianceTop10 = useMemo(() => {
    return (items ?? [])
      .map(item => {
        const variance    = item.quantityOnHand - item.quantityExpected
        const absVariance = Math.abs(variance)
        const p = productMap[item.productId]
        return { ...item, variance, absVariance, sku: p?.sku ?? item.productId.slice(-8), name: p?.name ?? '—' }
      })
      .filter(item => item.absVariance > 0)
      .sort((a, b) => b.absVariance - a.absVariance)
      .slice(0, 10)
  }, [items, productMap])

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

  // ── Inline bar color helper ──────────────────────────────────────────────────
  function barAccuracyColor(val: number | null): string {
    if (val == null) return '#6b7280'
    if (val >= 95) return '#16a34a'
    if (val >= 80) return '#d97706'
    return '#dc2626'
  }

  const inStockCount  = stockPosition[0].value + stockPosition[1].value
  const outStockCount = stockPosition[2].value
  const totalItems    = items?.length ?? 0
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
          {isAdmin && (
            <>
              <button onClick={() => setTab('store')}   className={tabCls(tab === 'store')}>Store View</button>
              <button onClick={() => setTab('network')} className={tabCls(tab === 'network')}>Network View</button>
            </>
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

              {/* Accuracy by Brand horizontal bar */}
              <div className="card">
                <h3 className="text-sm font-semibold text-gray-700 mb-3">Accuracy by Brand</h3>
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
