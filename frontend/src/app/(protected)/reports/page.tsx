'use client'

import { useQuery }        from '@tanstack/react-query'
import { useMemo, useState, useCallback } from 'react'
import { type ColumnDef }  from '@tanstack/react-table'
import {
  LineChart, Line, BarChart, Bar,
  XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer,
} from 'recharts'
import {
  Download, BarChart3, Package, Truck, RefreshCw,
  ShoppingCart, TrendingDown, Loader2, CheckCircle2, MapPin, Zap,
} from 'lucide-react'
import Link                from 'next/link'
import Header              from '@/components/layout/Header'
import DataTable           from '@/components/ui/DataTable'
import { reportingApi }    from '@/lib/api/reporting'
import { inventoryApi }    from '@/lib/api/inventory'
import { productsApi }     from '@/lib/api/products'
import { refillApi }       from '@/lib/api/refill'
import { storesApi }       from '@/lib/api/stores'
import { useAuth }         from '@/lib/auth/AuthContext'
import { fmtPct }          from '@/lib/utils'
import type { KpiDaily, InventoryState, Product, RefillTask, Store } from '@/types'

// ── CSV utility ──────────────────────────────────────────────────────────────
function esc(v: string | number | null | undefined): string {
  if (v == null) return ''
  const s = String(v)
  return s.includes(',') || s.includes('"') || s.includes('\n')
    ? `"${s.replace(/"/g, '""')}"`
    : s
}

function downloadCsv(filename: string, headers: string[], rows: (string | number | null | undefined)[][]) {
  const csv = [headers, ...rows].map(r => r.map(esc).join(',')).join('\r\n')
  const blob = new Blob(['﻿' + csv, { type: 'text/csv;charset=utf-8;' }] as BlobPart[])
  const url  = URL.createObjectURL(blob)
  const a    = document.createElement('a')
  a.href = url;  a.download = filename;  a.click()
  URL.revokeObjectURL(url)
}

function todayStr() { return new Date().toISOString().slice(0, 10) }
function daysAgo(n: number) { return new Date(Date.now() - n * 864e5).toISOString().slice(0, 10) }

// ── Product lookup helper ─────────────────────────────────────────────────────
async function fetchAllProducts(): Promise<Product[]> {
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
}

// ── GRN ref helper ────────────────────────────────────────────────────────────
function grnRef(task: RefillTask) {
  if (task.notes) {
    const m = task.notes.match(/GRN[-:\s]?([A-Z0-9]+)/i)
    if (m) return m[1].toUpperCase()
  }
  return 'GRN-' + task.id.slice(-8).toUpperCase()
}

// ── Report card UI ────────────────────────────────────────────────────────────
interface ReportCardProps {
  icon:        React.ElementType
  title:       string
  description: string
  columns:     string[]
  onDownload:  () => Promise<void>
  loading:     boolean
  done:        boolean
}

function ReportCard({ icon: Icon, title, description, columns, onDownload, loading, done }: ReportCardProps) {
  return (
    <div className="card flex flex-col gap-3">
      <div className="flex items-start gap-3">
        <div className="w-9 h-9 rounded-lg bg-brand-50 flex items-center justify-center shrink-0">
          <Icon size={18} className="text-brand-600" />
        </div>
        <div className="flex-1 min-w-0">
          <h3 className="text-sm font-semibold text-gray-900">{title}</h3>
          <p className="text-xs text-gray-500 mt-0.5">{description}</p>
        </div>
      </div>
      <div className="flex flex-wrap gap-1">
        {columns.map(c => (
          <span key={c} className="inline-flex px-1.5 py-0.5 rounded text-[10px] font-medium bg-gray-100 text-gray-600">{c}</span>
        ))}
      </div>
      <button
        onClick={onDownload}
        disabled={loading}
        className={`mt-auto flex items-center justify-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
          done
            ? 'bg-green-50 text-green-700 border border-green-200'
            : loading
              ? 'bg-gray-100 text-gray-400 cursor-not-allowed'
              : 'bg-brand-600 text-white hover:bg-brand-700 active:bg-brand-800'
        }`}
      >
        {loading ? (
          <><Loader2 size={14} className="animate-spin" /> Generating…</>
        ) : done ? (
          <><CheckCircle2 size={14} /> Downloaded</>
        ) : (
          <><Download size={14} /> Download CSV</>
        )}
      </button>
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────
export default function ReportsPage() {
  const { user, isAdmin }  = useAuth()
  const [range, setRange]  = useState<'7d' | '30d' | '90d'>('30d')
  const [selectedStoreId, setSelectedStoreId] = useState('')
  const [downloading, setDownloading]         = useState<string | null>(null)
  const [done, setDone]                       = useState<string | null>(null)

  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })

  const storeId = isAdmin
    ? (selectedStoreId || allStores?.content[0]?.id || '')
    : (user?.storeId ?? '')

  const storeName = useMemo(() => {
    if (!isAdmin) return user?.storeId ?? 'store'
    return allStores?.content.find(s => s.id === storeId)?.storeCode ?? 'store'
  }, [isAdmin, storeId, allStores, user])

  const days = range === '7d' ? 7 : range === '30d' ? 30 : 90

  // Existing KPI chart query
  const { data: kpiData, isLoading: kpiLoading } = useQuery({
    queryKey: ['kpi-range', storeId, range],
    queryFn:  () => reportingApi.kpiRange(storeId, daysAgo(days), todayStr()),
    enabled:  !!storeId,
  })

  const chartData = (kpiData ?? []).map(k => ({
    date:     k.kpiDate.slice(5),
    accuracy: k.inventoryAccuracyPct ?? 0,
    sessions: k.sohSessionsCount,
    refill:   k.refillCompletionRatePct ?? 0,
    reads:    Math.round(k.totalEpcReads / 1000),
  }))

  const kpiColumns = useMemo<ColumnDef<KpiDaily, unknown>[]>(() => [
    { accessorKey: 'kpiDate',                 header: 'Date' },
    { accessorKey: 'inventoryAccuracyPct',    header: 'Accuracy',      cell: i => fmtPct(i.getValue<number|null>()) },
    { accessorKey: 'sohSessionsCount',        header: 'SOH Sessions' },
    { accessorKey: 'refillTasksCreated',      header: 'Refill Created' },
    { accessorKey: 'refillTasksCompleted',    header: 'Refill Done' },
    { accessorKey: 'refillCompletionRatePct', header: 'Completion',    cell: i => fmtPct(i.getValue<number|null>()) },
    { accessorKey: 'totalEpcReads',           header: 'EPC Reads',     cell: i => i.getValue<number>().toLocaleString() },
    { accessorKey: 'varianceItemsCount',      header: 'Variances' },
  ], [])

  // ── Download handlers ─────────────────────────────────────────────────────
  const withDownload = useCallback(async (id: string, fn: () => Promise<void>) => {
    setDownloading(id); setDone(null)
    try {
      await fn()
      setDone(id)
      setTimeout(() => setDone(d => d === id ? null : d), 3000)
    } catch (e) {
      console.error(e)
    } finally {
      setDownloading(null)
    }
  }, [])

  // 1. KPI Summary
  const downloadKpi = () => withDownload('kpi', async () => {
    const data = await reportingApi.kpiRange(storeId, daysAgo(days), todayStr())
    downloadCsv(
      `KPI_Summary_${storeName}_${todayStr()}.csv`,
      ['Date','Accuracy %','SOH Sessions','Refill Created','Refill Completed','Completion %','EPC Reads','Unique SKUs','Variance Items'],
      data.map(k => [
        k.kpiDate,
        k.inventoryAccuracyPct?.toFixed(2) ?? '',
        k.sohSessionsCount,
        k.refillTasksCreated,
        k.refillTasksCompleted,
        k.refillCompletionRatePct?.toFixed(2) ?? '',
        k.totalEpcReads,
        k.uniqueSkusCounted,
        k.varianceItemsCount,
      ])
    )
  })

  // 2. Inventory Accuracy
  const downloadInventory = () => withDownload('inventory', async () => {
    const [inv, products] = await Promise.all([
      inventoryApi.getState(storeId),
      fetchAllProducts(),
    ])
    const pm: Record<string, Product> = {}
    for (const p of products) pm[p.id] = p
    const rows = (inv as InventoryState[]).map(i => {
      const p = pm[i.productId]
      const gap = i.quantityExpected - i.quantityOnHand
      return [
        p?.sku ?? i.productId.slice(-8),
        p?.name ?? '—',
        p?.brand ?? '—',
        i.quantityExpected,
        i.quantityOnHand,
        i.accuracyPct?.toFixed(2) ?? '',
        gap,
        gap > 0 ? 'ERP>RFID' : gap < 0 ? 'RFID>ERP' : 'Match',
      ]
    }).sort((a, b) => (b[6] as number) - (a[6] as number))
    downloadCsv(
      `Inventory_Accuracy_${storeName}_${todayStr()}.csv`,
      ['SKU','Product Name','Brand','ERP Expected','RFID Count','Accuracy %','Gap','Status'],
      rows
    )
  })

  // 3. Inbound / GRN
  const downloadInbound = () => withDownload('inbound', async () => {
    const resp = await refillApi.listTasks(storeId, { size: 500 })
    const tasks = resp.content.filter(t => t.source === 'erp')
    downloadCsv(
      `Inbound_GRN_${storeName}_${todayStr()}.csv`,
      ['GRN Reference','Task ID','Status','Priority','Lines','Units Expected','Units Received','Shortfall','Created At','Completed At','Notes'],
      tasks.map(t => {
        const totalReq  = t.items.reduce((s, i) => s + i.requestedQuantity, 0)
        const totalRcvd = t.items.reduce((s, i) => s + i.fulfilledQuantity, 0)
        return [
          grnRef(t),
          t.id,
          t.status,
          t.priority,
          t.items.length,
          totalReq,
          totalRcvd,
          totalReq - totalRcvd,
          t.createdAt,
          t.completedAt ?? '',
          t.notes ?? '',
        ]
      })
    )
  })

  // 4. Replenishment
  const downloadReplenishment = () => withDownload('replenishment', async () => {
    const resp  = await refillApi.listTasks(storeId, { size: 500 })
    const tasks = resp.content.filter(t =>
      t.source !== 'erp' || (t.notes ?? '').toLowerCase().includes('floor')
    )
    downloadCsv(
      `Replenishment_${storeName}_${todayStr()}.csv`,
      ['Task ID','Movement','Source','Status','Priority','Lines','Units','Created At','Completed At','Notes'],
      tasks.map(t => {
        const n   = (t.notes ?? '').toLowerCase()
        const mv  = (t.source === 'erp' || (n.includes('dc') && n.includes('floor')))
          ? 'DC → Sales Floor' : 'Backroom → Floor'
        const src = { manual: 'Manual', soh_trigger: 'RFID Alert', scheduled: 'Scheduled', erp: 'ERP/DC' }[t.source] ?? t.source
        return [
          t.id,
          mv,
          src,
          t.status,
          t.priority,
          t.items.length,
          t.items.reduce((s, i) => s + i.requestedQuantity, 0),
          t.createdAt,
          t.completedAt ?? '',
          t.notes ?? '',
        ]
      })
    )
  })

  // 5. Sales & Variance
  const downloadSales = () => withDownload('sales', async () => {
    const [inv, products] = await Promise.all([
      inventoryApi.getState(storeId),
      fetchAllProducts(),
    ])
    const pm: Record<string, Product> = {}
    for (const p of products) pm[p.id] = p
    const rows = (inv as InventoryState[])
      .map(i => {
        const p   = pm[i.productId]
        const gap = i.quantityExpected - i.quantityOnHand
        return [
          p?.sku ?? i.productId.slice(-8),
          p?.name ?? '—',
          p?.brand ?? '—',
          i.quantityExpected,
          i.quantityOnHand,
          gap > 0 ? gap : 0,
          gap < 0 ? Math.abs(gap) : 0,
          i.accuracyPct?.toFixed(2) ?? '',
        ]
      })
      .filter(r => (r[5] as number) > 0 || (r[6] as number) > 0)
      .sort((a, b) => (b[5] as number) - (a[5] as number))
    downloadCsv(
      `Sales_Variance_${storeName}_${todayStr()}.csv`,
      ['SKU','Product Name','Brand','ERP Expected','RFID Count','Sold/Missing (ERP>RFID)','Phantom (RFID>ERP)','Accuracy %'],
      rows
    )
  })

  // 6. Admin: All-stores KPI snapshot
  const downloadAllStores = () => withDownload('all-stores', async () => {
    const stores = allStores?.content ?? []
    const allRows: (string | number | null | undefined)[][] = []
    for (const s of stores) {
      const kpi = await reportingApi.kpiRange(s.id, daysAgo(days), todayStr())
      for (const k of kpi) {
        allRows.push([
          s.storeCode, s.name, s.city ?? '',
          k.kpiDate,
          k.inventoryAccuracyPct?.toFixed(2) ?? '',
          k.sohSessionsCount,
          k.refillTasksCreated,
          k.refillTasksCompleted,
          k.refillCompletionRatePct?.toFixed(2) ?? '',
          k.totalEpcReads,
          k.varianceItemsCount,
        ])
      }
    }
    downloadCsv(
      `All_Stores_KPI_${todayStr()}.csv`,
      ['Store Code','Store Name','City','Date','Accuracy %','SOH Sessions','Refill Created','Refill Done','Completion %','EPC Reads','Variances'],
      allRows
    )
  })

  const selectCls = 'text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500'

  return (
    <>
      <Header title="Reports" />
      <div className="p-6 space-y-6">

        {/* Filters */}
        <div className="flex flex-wrap items-center gap-3">
          {isAdmin && allStores && allStores.content.length > 0 && (
            <>
              <label className="text-sm font-medium text-gray-600 shrink-0">Store</label>
              <select value={storeId} onChange={e => setSelectedStoreId(e.target.value)} className={selectCls}>
                {allStores.content.map((s: Store) => (
                  <option key={s.id} value={s.id}>{s.name} ({s.storeCode})</option>
                ))}
              </select>
              <div className="h-5 w-px bg-gray-200" />
            </>
          )}
          {(['7d','30d','90d'] as const).map(r => (
            <button key={r} onClick={() => setRange(r)}
              className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                range === r ? 'bg-brand-600 text-white' : 'bg-white border border-gray-200 text-gray-700 hover:bg-gray-50'
              }`}
            >{r}</button>
          ))}
          {(() => {
            if (!kpiData?.length) return null
            const actualDays = kpiData.length === 1 ? 1
              : Math.round((new Date(kpiData[kpiData.length - 1].kpiDate).getTime() - new Date(kpiData[0].kpiDate).getTime()) / 864e5) + 1
            const rangeDays  = range === '7d' ? 7 : range === '30d' ? 30 : 90
            return actualDays < rangeDays ? (
              <span className="text-xs font-medium text-amber-700 bg-amber-50 border border-amber-200 px-2.5 py-1 rounded-full">
                {actualDays} day{actualDays !== 1 ? 's' : ''} of data available
              </span>
            ) : (
              <span className="text-xs text-gray-400">{actualDays} days</span>
            )
          })()}
          <span className="ml-auto text-xs text-gray-400">Reports include data for selected range &amp; store</span>
        </div>

        {/* Downloadable report cards */}
        <div>
          <h2 className="text-sm font-semibold text-gray-700 mb-3">Downloadable Reports</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">

            <ReportCard
              icon={BarChart3}
              title="KPI Summary"
              description={`Daily key metrics for ${range} — accuracy, sessions, refill rates, EPC reads`}
              columns={['Date','Accuracy %','SOH Sessions','Refill Created','Refill Done','Completion %','EPC Reads','Variances']}
              onDownload={downloadKpi}
              loading={downloading === 'kpi'}
              done={done === 'kpi'}
            />

            <ReportCard
              icon={Package}
              title="Inventory Accuracy"
              description="Per-SKU ERP expected vs RFID count — current snapshot sorted by gap"
              columns={['SKU','Product','Brand','ERP Qty','RFID Count','Accuracy %','Gap','Status']}
              onDownload={downloadInventory}
              loading={downloading === 'inventory'}
              done={done === 'inventory'}
            />

            <ReportCard
              icon={Truck}
              title="Inbound / GRN"
              description="All DC inbound receipts (ERP source) with GRN reference and shortfall"
              columns={['GRN Ref','Status','Lines','Expected','Received','Shortfall','Date']}
              onDownload={downloadInbound}
              loading={downloading === 'inbound'}
              done={done === 'inbound'}
            />

            <ReportCard
              icon={RefreshCw}
              title="Replenishment Activity"
              description="Floor replenishment tasks — backroom-to-floor and DC-to-floor movements"
              columns={['Task ID','Movement','Source','Status','Lines','Units','Date']}
              onDownload={downloadReplenishment}
              loading={downloading === 'replenishment'}
              done={done === 'replenishment'}
            />

            <ReportCard
              icon={ShoppingCart}
              title="Sales & ERP Variance"
              description="Products where ERP expected differs from RFID count — potential sold or phantom"
              columns={['SKU','Brand','ERP Qty','RFID Count','Sold/Miss','Phantom','Accuracy %']}
              onDownload={downloadSales}
              loading={downloading === 'sales'}
              done={done === 'sales'}
            />

            {isAdmin && (
              <ReportCard
                icon={TrendingDown}
                title="All Stores KPI"
                description={`Admin: combined KPI for ALL stores over ${range} — one row per store per day`}
                columns={['Store Code','Store','City','Date','Accuracy %','Sessions','Refill','Completion %']}
                onDownload={downloadAllStores}
                loading={downloading === 'all-stores'}
                done={done === 'all-stores'}
              />
            )}

          </div>
        </div>


        {/* Charts */}
        <div>
          <h2 className="text-sm font-semibold text-gray-700 mb-3">KPI Trend — {range}</h2>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="card">
              <h3 className="text-sm font-semibold text-gray-700 mb-4">Inventory Accuracy</h3>
              <ResponsiveContainer width="100%" height={200}>
                <LineChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis dataKey="date" tick={{ fontSize: 10 }} interval="preserveStartEnd" />
                  <YAxis domain={[0, 100]} unit="%" tick={{ fontSize: 10 }} />
                  <Tooltip formatter={(v: number) => [`${v.toFixed(1)}%`]} />
                  <Line type="monotone" dataKey="accuracy" stroke="#2563eb" strokeWidth={2} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>
            <div className="card">
              <h3 className="text-sm font-semibold text-gray-700 mb-4">Refill Completion Rate</h3>
              <ResponsiveContainer width="100%" height={200}>
                <BarChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis dataKey="date" tick={{ fontSize: 10 }} interval="preserveStartEnd" />
                  <YAxis domain={[0, 100]} unit="%" tick={{ fontSize: 10 }} />
                  <Tooltip formatter={(v: number) => [`${v.toFixed(1)}%`]} />
                  <Bar dataKey="refill" fill="#16a34a" radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>

        {/* KPI table */}
        <div className="card">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-semibold text-gray-700">Daily KPI Detail</h3>
          </div>
          <DataTable data={kpiData ?? []} columns={kpiColumns} isLoading={kpiLoading} pageSize={15} />
        </div>

      </div>
    </>
  )
}
