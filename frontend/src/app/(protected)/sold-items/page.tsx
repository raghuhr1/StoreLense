'use client'

import { useQuery }      from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { type ColumnDef } from '@tanstack/react-table'
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer,
  Cell, PieChart, Pie, Legend,
} from 'recharts'
import Header            from '@/components/layout/Header'
import DataTable         from '@/components/ui/DataTable'
import { inventoryApi } from '@/lib/api/inventory'
import { productsApi }  from '@/lib/api/products'
import { refillApi }    from '@/lib/api/refill'
import { storesApi }    from '@/lib/api/stores'
import { useAuth }      from '@/lib/auth/AuthContext'
import type { InventoryState, Product } from '@/types'

// ── types ──────────────────────────────────────────────────────────────────
interface SaleRow {
  productId:    string
  sku:          string
  name:         string
  brand:        string
  erpExpected:  number   // quantity_expected (ERP)
  rfidOnHand:   number   // quantity_on_hand  (RFID)
  gap:          number   // erpExpected - rfidOnHand  (> 0 = ERP has more → sold/missing)
  rfidExcess:   number   // rfidOnHand - erpExpected  (> 0 = RFID found more → phantom/return)
  accuracyPct:  number | null
  receivedInGrn: number  // units received via completed GRN task items for this product
}

interface Inconsistency {
  type:    'phantom' | 'orphan' | 'untagged_receipt'
  sku:     string
  name:    string
  brand:   string
  detail:  string
  severity: 'high' | 'medium' | 'low'
}

const BRAND_COLORS = [
  '#3b82f6','#10b981','#f59e0b','#ef4444','#8b5cf6',
  '#ec4899','#06b6d4','#84cc16','#f97316','#6366f1',
]

// ── component ──────────────────────────────────────────────────────────────
export default function SoldItemsPage() {
  const { user, isAdmin } = useAuth()
  const [selectedStoreId, setSelectedStoreId] = useState('')
  const [filterBrand, setFilterBrand]         = useState('')
  const [gapMin, setGapMin]                   = useState(0)      // min gap filter

  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })

  const storeId = isAdmin
    ? (selectedStoreId || allStores?.content[0]?.id || '')
    : (user?.storeId ?? '')

  // ── data fetches ──────────────────────────────────────────────────────────
  const { data: invState, isLoading: invLoading } = useQuery({
    queryKey: ['inventory-state', storeId],
    queryFn:  () => inventoryApi.getState(storeId),
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

  // Completed GRN tasks (source=erp, status=completed) to detect untagged receipts
  const { data: grnTasks } = useQuery({
    queryKey: ['refill-tasks', storeId, 'erp'],
    queryFn:  () => refillApi.listTasks(storeId, { size: 200 }),
    enabled:  !!storeId,
    select:   d => d.content.filter(t => t.source === 'erp' && t.status === 'completed'),
  })

  // ── derived data ──────────────────────────────────────────────────────────
  const productMap = useMemo(() => {
    const m: Record<string, Product> = {}
    for (const p of allProducts ?? []) m[p.id] = p
    return m
  }, [allProducts])

  // Per-product GRN received units (sum of fulfilled_quantity across completed tasks)
  const grnReceivedMap = useMemo(() => {
    const m: Record<string, number> = {}
    for (const task of grnTasks ?? []) {
      for (const item of task.items) {
        m[item.productId] = (m[item.productId] ?? 0) + item.fulfilledQuantity
      }
    }
    return m
  }, [grnTasks])

  const rows = useMemo((): SaleRow[] => {
    if (!invState) return []
    // Only store-level rows (zone_id=NULL); zone-specific rows are for zone breakdown only
    return (invState as InventoryState[]).filter(inv => inv.zoneId == null).map(inv => {
      const p    = productMap[inv.productId]
      const gap  = Math.max(0, inv.quantityExpected - inv.quantityOnHand)
      const rcvd = grnReceivedMap[inv.productId] ?? 0
      return {
        productId:    inv.productId,
        sku:          p?.sku ?? inv.productId.slice(-8),
        name:         p?.name ?? '—',
        brand:        p?.brand ?? 'Unknown',
        erpExpected:  inv.quantityExpected,
        rfidOnHand:   inv.quantityOnHand,
        gap,
        rfidExcess:   Math.max(0, inv.quantityOnHand - inv.quantityExpected),
        accuracyPct:  inv.accuracyPct,
        receivedInGrn: rcvd,
      }
    })
  }, [invState, productMap, grnReceivedMap])

  const brands = useMemo(() => [...new Set(rows.map(r => r.brand))].sort(), [rows])

  const filtered = useMemo(() =>
    rows.filter(r =>
      (!filterBrand || r.brand === filterBrand) &&
      r.gap >= gapMin
    ),
  [rows, filterBrand, gapMin])

  // Summary stats — driven by `filtered` so cards respond to dropdowns
  const stats = useMemo(() => ({
    totalSKUs:       filtered.filter(r => r.gap > 0).length,
    totalGap:        filtered.reduce((s, r) => s + r.gap, 0),
    phantomSKUs:     filtered.filter(r => r.rfidExcess > 0).length,
    totalPhantom:    filtered.reduce((s, r) => s + r.rfidExcess, 0),
    perfectMatch:    filtered.filter(r => r.gap === 0 && r.rfidExcess === 0).length,
    avgAccuracy:     (() => {
      const rated = filtered.filter(r => r.accuracyPct != null)
      return rated.length > 0
        ? Math.round(rated.reduce((s, r) => s + (r.accuracyPct as number), 0) / rated.length)
        : 0
    })(),
  }), [filtered])

  // Brand-level gap chart — always shows ALL brands for context (not filtered by brand)
  // but respects the gap-size filter so numbers stay consistent
  const brandGapData = useMemo(() => {
    const src = gapMin > 0 ? rows.filter(r => r.gap >= gapMin) : rows
    const m: Record<string, { gap: number; phantom: number; skus: number }> = {}
    for (const r of src) {
      if (!m[r.brand]) m[r.brand] = { gap: 0, phantom: 0, skus: 0 }
      m[r.brand].gap     += r.gap
      m[r.brand].phantom += r.rfidExcess
      m[r.brand].skus    += 1
    }
    return Object.entries(m)
      .map(([brand, v]) => ({ brand, ...v }))
      .sort((a, b) => b.gap - a.gap)
  }, [rows, gapMin])

  // ERP vs RFID distribution pie — driven by `filtered`
  const pieDist = useMemo(() => [
    { name: 'ERP = RFID (matched)',    value: filtered.filter(r => r.gap === 0 && r.rfidExcess === 0).length, fill: '#10b981' },
    { name: 'ERP > RFID (sold/miss)', value: filtered.filter(r => r.gap > 0).length,      fill: '#f59e0b' },
    { name: 'RFID > ERP (phantom)',   value: filtered.filter(r => r.rfidExcess > 0).length, fill: '#ef4444' },
  ].filter(d => d.value > 0), [filtered])

  // ── inconsistency checks — driven by `filtered` ───────────────────────────
  const inconsistencies = useMemo((): Inconsistency[] => {
    const issues: Inconsistency[] = []
    for (const r of filtered) {
      // Phantom inventory: RFID found more than ERP expects
      if (r.rfidExcess > 0) {
        issues.push({
          type: 'phantom', sku: r.sku, name: r.name, brand: r.brand,
          detail: `RFID scanned ${r.rfidOnHand} but ERP only expects ${r.erpExpected} (+${r.rfidExcess} phantom)`,
          severity: r.rfidExcess > 10 ? 'high' : 'medium',
        })
      }
      // Received via GRN but RFID shows 0 on-hand (received, not RFID-tagged)
      if (r.receivedInGrn > 0 && r.rfidOnHand === 0) {
        issues.push({
          type: 'untagged_receipt', sku: r.sku, name: r.name, brand: r.brand,
          detail: `GRN shows ${r.receivedInGrn} units received but RFID count is 0 — items not yet tagged?`,
          severity: 'high',
        })
      }
      // GRN received > current RFID on-hand (partial tag scan or items sold after receipt)
      if (r.receivedInGrn > 0 && r.rfidOnHand > 0 && r.receivedInGrn > r.rfidOnHand + 5) {
        issues.push({
          type: 'untagged_receipt', sku: r.sku, name: r.name, brand: r.brand,
          detail: `GRN received ${r.receivedInGrn} units but RFID only shows ${r.rfidOnHand} — ${r.receivedInGrn - r.rfidOnHand} may be sold/untagged`,
          severity: 'medium',
        })
      }
      // Orphan stock: RFID shows stock but ERP expects 0
      if (r.erpExpected === 0 && r.rfidOnHand > 0) {
        issues.push({
          type: 'orphan', sku: r.sku, name: r.name, brand: r.brand,
          detail: `RFID shows ${r.rfidOnHand} units but ERP expected 0 — stock not in ERP system`,
          severity: 'medium',
        })
      }
    }
    // Limit to top 50 by severity
    const order = { high: 0, medium: 1, low: 2 }
    return issues.sort((a, b) => order[a.severity] - order[b.severity]).slice(0, 50)
  }, [rows])

  // ── table columns ─────────────────────────────────────────────────────────
  const columns = useMemo<ColumnDef<SaleRow, unknown>[]>(() => [
    {
      id: 'product', header: 'Product', accessorFn: r => r.sku,
      cell: ({ row: r }) => (
        <div>
          <p className="font-mono text-xs text-blue-600">{r.original.sku}</p>
          <p className="text-xs text-gray-500 truncate max-w-[200px]">{r.original.name}</p>
        </div>
      ),
    },
    { accessorKey: 'brand', header: 'Brand', cell: i => <span className="text-xs text-gray-700">{i.getValue<string>()}</span> },
    {
      accessorKey: 'erpExpected', header: 'ERP Qty',
      cell: i => <span className="font-semibold text-blue-700">{i.getValue<number>()}</span>,
    },
    {
      accessorKey: 'rfidOnHand', header: 'RFID Count',
      cell: i => <span className="font-semibold text-gray-800">{i.getValue<number>()}</span>,
    },
    {
      accessorKey: 'gap', header: 'ERP − RFID',
      cell: i => {
        const v = i.getValue<number>()
        if (v === 0) return <span className="text-green-600 text-xs">✓ Match</span>
        return <span className="text-amber-600 font-semibold">−{v}</span>
      },
    },
    {
      accessorKey: 'rfidExcess', header: 'RFID − ERP',
      cell: i => {
        const v = i.getValue<number>()
        if (v === 0) return <span className="text-gray-300 text-xs">—</span>
        return <span className="text-red-500 font-semibold">+{v}</span>
      },
    },
    {
      accessorKey: 'receivedInGrn', header: 'GRN Rcvd',
      cell: i => {
        const v = i.getValue<number>()
        return v > 0
          ? <span className="text-green-700 font-medium">{v}</span>
          : <span className="text-gray-300">—</span>
      },
    },
    {
      accessorKey: 'accuracyPct', header: 'Accuracy',
      cell: i => {
        const v = i.getValue<number | null>()
        if (v == null) return <span className="text-gray-300">—</span>
        const cls = v >= 95 ? 'text-green-700' : v >= 80 ? 'text-amber-600' : 'text-red-600'
        return <span className={`font-semibold text-xs ${cls}`}>{v.toFixed(1)}%</span>
      },
    },
  ], [])

  const inconCols = useMemo<ColumnDef<Inconsistency, unknown>[]>(() => [
    {
      accessorKey: 'severity', header: 'Sev',
      cell: i => {
        const v = i.getValue<string>()
        const cls = v === 'high' ? 'bg-red-100 text-red-700' : v === 'medium' ? 'bg-amber-100 text-amber-700' : 'bg-gray-100 text-gray-600'
        return <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${cls}`}>{v}</span>
      },
    },
    {
      accessorKey: 'type', header: 'Type',
      cell: i => {
        const v = i.getValue<string>()
        const label = v === 'phantom' ? 'Phantom Stock' : v === 'orphan' ? 'Orphan Stock' : 'Untagged Receipt'
        return <span className="text-xs font-medium text-gray-700">{label}</span>
      },
    },
    {
      id: 'product', header: 'Product', accessorFn: r => r.sku,
      cell: ({ row: r }) => (
        <div>
          <p className="font-mono text-xs text-blue-600">{r.original.sku}</p>
          <p className="text-xs text-gray-500">{r.original.brand}</p>
        </div>
      ),
    },
    { accessorKey: 'detail', header: 'Detail', cell: i => <span className="text-xs text-gray-600">{i.getValue<string>()}</span> },
  ], [])

  const selectCls = 'text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500'

  return (
    <>
      <Header title="Sales & ERP Variance" />
      <div className="p-6 space-y-6">

        {/* Explanation banner */}
        <div className="bg-blue-50 border border-blue-200 rounded-xl px-4 py-3 text-sm text-blue-800">
          <strong>ERP vs RFID Gap Analysis</strong> — compares what ERP expects vs what RFID actually scanned.
          A negative gap (ERP &gt; RFID) approximates sold or missing items.
          A positive excess (RFID &gt; ERP) indicates phantom reads or unregistered returns.
        </div>

        {/* Store selector + filters */}
        <div className="flex flex-wrap items-center gap-3">
          {isAdmin && allStores && allStores.content.length > 0 && (
            <>
              <label className="text-sm font-medium text-gray-600 shrink-0">Store</label>
              <select value={storeId} onChange={e => setSelectedStoreId(e.target.value)} className={selectCls}>
                {allStores.content.map(s => (
                  <option key={s.id} value={s.id}>{s.name} ({s.storeCode})</option>
                ))}
              </select>
              <div className="h-5 w-px bg-gray-200" />
            </>
          )}
          <select value={filterBrand} onChange={e => setFilterBrand(e.target.value)} className={selectCls}>
            <option value="">All Brands</option>
            {brands.map(b => <option key={b} value={b}>{b}</option>)}
          </select>
          <select value={gapMin} onChange={e => setGapMin(Number(e.target.value))} className={selectCls}>
            <option value={0}>All Gap Sizes</option>
            <option value={1}>Gap ≥ 1</option>
            <option value={5}>Gap ≥ 5</option>
            <option value={10}>Gap ≥ 10</option>
            <option value={20}>Gap ≥ 20</option>
          </select>
          {(filterBrand || gapMin > 0) && (
            <button onClick={() => { setFilterBrand(''); setGapMin(0) }} className="text-xs text-gray-500 hover:text-gray-700 underline">
              Clear
            </button>
          )}
          <span className="ml-auto text-xs text-gray-400">{filtered.length.toLocaleString()} SKUs shown</span>
        </div>

        {/* Summary cards */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          {[
            { label: 'SKUs w/ ERP Gap',   value: stats.totalSKUs,    sub: `${stats.totalGap.toLocaleString()} units`,       cls: stats.totalGap > 0 ? 'text-amber-600' : 'text-gray-900' },
            { label: 'Phantom Stock SKUs', value: stats.phantomSKUs,  sub: `${stats.totalPhantom.toLocaleString()} units`,    cls: stats.phantomSKUs > 0 ? 'text-red-600' : 'text-gray-900' },
            { label: 'Perfect Matches',   value: stats.perfectMatch,  sub: 'ERP = RFID exactly',                             cls: 'text-green-700' },
            { label: 'Avg Accuracy',       value: `${stats.avgAccuracy}%`, sub: 'across all SKUs',                          cls: stats.avgAccuracy >= 95 ? 'text-green-700' : stats.avgAccuracy >= 80 ? 'text-amber-600' : 'text-red-600' },
          ].map(s => (
            <div key={s.label} className="card">
              <p className="text-xs text-gray-500">{s.label}</p>
              <p className={`text-2xl font-bold mt-0.5 ${s.cls}`}>{s.value}</p>
              <p className="text-xs text-gray-400 mt-0.5">{s.sub}</p>
            </div>
          ))}
        </div>

        {/* Charts row */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">

          {/* Brand gap bar chart */}
          <div className="card lg:col-span-2">
            <h3 className="text-sm font-semibold text-gray-700 mb-3">ERP Gap by Brand (top 10)</h3>
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={brandGapData.slice(0, 10)} layout="vertical" margin={{ left: 80, right: 20 }}>
                <XAxis type="number" tick={{ fontSize: 11 }} />
                <YAxis type="category" dataKey="brand" tick={{ fontSize: 11 }} width={80} />
                <Tooltip formatter={(v: number) => [v, 'units']} />
                <Bar dataKey="gap" name="ERP − RFID (sold/miss)" radius={[0, 4, 4, 0]}>
                  {brandGapData.slice(0, 10).map((_, i) => (
                    <Cell key={i} fill={BRAND_COLORS[i % BRAND_COLORS.length]} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>

          {/* Distribution pie */}
          <div className="card">
            <h3 className="text-sm font-semibold text-gray-700 mb-3">ERP vs RFID Status</h3>
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie data={pieDist} dataKey="value" nameKey="name" cx="50%" cy="45%" outerRadius={70} label={({ value }) => `${value}`}>
                  {pieDist.map((d, i) => <Cell key={i} fill={d.fill} />)}
                </Pie>
                <Legend wrapperStyle={{ fontSize: 11 }} />
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          </div>

        </div>

        {/* Main table */}
        <div className="card">
          <div className="mb-4">
            <h3 className="text-sm font-semibold text-gray-700">SKU-Level ERP vs RFID Detail</h3>
            <p className="text-xs text-gray-400 mt-0.5">
              ERP − RFID = units in ERP not found by RFID (sold / missing).
              RFID − ERP = units RFID found beyond ERP count (phantom / return).
              GRN Rcvd = units received via completed DC inbound tasks.
            </p>
          </div>
          <DataTable
            data={filtered}
            columns={columns}
            isLoading={invLoading}
            searchable
            searchPlaceholder="Search SKU or product name…"
          />
        </div>

        {/* Inconsistency analysis */}
        <div className="card">
          <div className="mb-4">
            <h3 className="text-sm font-semibold text-gray-700 flex items-center gap-2">
              Inconsistency Analysis
              {inconsistencies.filter(i => i.severity === 'high').length > 0 && (
                <span className="inline-flex px-2 py-0.5 rounded-full text-xs font-bold bg-red-100 text-red-700">
                  {inconsistencies.filter(i => i.severity === 'high').length} high
                </span>
              )}
            </h3>
            <p className="text-xs text-gray-400 mt-0.5">
              Cross-checks between GRN inbound data, ERP expected quantities, and RFID scan counts.
            </p>
          </div>
          {inconsistencies.length === 0 ? (
            <p className="text-sm text-green-700 font-medium py-4 text-center">
              No inconsistencies detected — GRN, ERP and RFID data align.
            </p>
          ) : (
            <DataTable
              data={inconsistencies}
              columns={inconCols}
              isLoading={invLoading}
              searchable
              searchPlaceholder="Search by SKU, brand or type…"
            />
          )}
        </div>

      </div>
    </>
  )
}
