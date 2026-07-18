'use client'

import { useQuery }          from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { type ColumnDef }    from '@tanstack/react-table'
import Link                  from 'next/link'
import Header                from '@/components/layout/Header'
import DataTable             from '@/components/ui/DataTable'
import StatCard              from '@/components/ui/StatCard'
import {
  Package, AlertTriangle, ScanLine, Target, MapPin,
  ShoppingCart, Tag, RefreshCw, ChevronLeft, ChevronRight,
} from 'lucide-react'
import { inventoryApi }      from '@/lib/api/inventory'
import { productsApi }       from '@/lib/api/products'
import { storesApi }         from '@/lib/api/stores'
import { useAuth }           from '@/lib/auth/AuthContext'
import { fmtPct, fmtDateTime, accuracyColor } from '@/lib/utils'
import type { InventoryState, EpcLedgerRow, SkuLedgerRow, Product } from '@/types'

// ── Types ─────────────────────────────────────────────────────────────────────

type MainTab    = 'stock' | 'ledger'
type LedgerTab  = 'epcs'  | 'sku'
type StockRow   = InventoryState & { sku: string; productName: string; brand: string | null }
type EnrichedSkuRow = SkuLedgerRow & { sku: string; productName: string }
type StatusFilter = 'all' | 'in_store' | 'sold' | 'missing' | 'damaged'

// ── Constants ─────────────────────────────────────────────────────────────────

const ACC_BANDS = [
  { value: '',       label: 'All Accuracy' },
  { value: 'high',   label: 'High  (≥95%)' },
  { value: 'medium', label: 'Medium (80–94%)' },
  { value: 'low',    label: 'Low  (<80%)' },
  { value: 'na',     label: 'N/A (no count)' },
]
const STOCK_OPTS = [
  { value: '',         label: 'All Stock' },
  { value: 'instock',  label: 'In Stock (>0)' },
  { value: 'outstock', label: 'Out of Stock (0)' },
  { value: 'variance', label: 'Has Variance' },
]
const STATUS_BADGE: Record<string, string> = {
  in_store:    'bg-teal-50 text-teal-700 ring-teal-200',
  sold:        'bg-green-50 text-green-700 ring-green-200',
  missing:     'bg-red-50 text-red-600 ring-red-200',
  damaged:     'bg-orange-50 text-orange-600 ring-orange-200',
  transferred: 'bg-blue-50 text-blue-600 ring-blue-200',
}
const STATUS_LABEL: Record<string, string> = {
  in_store: 'In Store', sold: 'Sold', missing: 'Missing',
  damaged: 'Damaged', transferred: 'Transferred',
}
const SKU_STATUS_COLORS: Record<string, string> = {
  inStore:     'text-teal-700 font-semibold',
  sold:        'text-green-600 font-semibold',
  missing:     'text-red-600 font-semibold',
  damaged:     'text-orange-600 font-semibold',
  transferred: 'text-blue-600',
}

// ── Main component ────────────────────────────────────────────────────────────

export default function InventoryPage() {
  const { user, isAdmin } = useAuth()

  // ── Shared state ──────────────────────────────────────────────────────────
  const [selectedStoreId, setSelectedStoreId] = useState('')
  const [mainTab,  setMainTab]  = useState<MainTab>('stock')

  // ── Stock Levels state ────────────────────────────────────────────────────
  const [filterBrand,    setFilterBrand]    = useState('')
  const [filterZone,     setFilterZone]     = useState('')
  const [filterAccuracy, setFilterAccuracy] = useState('')
  const [filterStock,    setFilterStock]    = useState('')

  // ── RFID Ledger state ─────────────────────────────────────────────────────
  const [ledgerTab,    setLedgerTab]    = useState<LedgerTab>('epcs')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all')
  const [skuFilter,    setSkuFilter]    = useState<'all' | 'missing' | 'sold' | 'damaged'>('all')
  const [epcPage,      setEpcPage]      = useState(0)

  // ── Derived store id ──────────────────────────────────────────────────────
  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })
  const storeId = isAdmin
    ? (selectedStoreId || allStores?.content[0]?.id || '')
    : (user?.storeId ?? '')

  const handleStoreChange = (id: string) => {
    setSelectedStoreId(id)
    setFilterZone('')
    setEpcPage(0)
  }

  // ── Shared queries ────────────────────────────────────────────────────────
  const { data: summary } = useQuery({
    queryKey: ['epc-summary', storeId],
    queryFn:  () => inventoryApi.epcSummary(storeId),
    enabled:  !!storeId,
    refetchInterval: mainTab === 'ledger' ? 30_000 : false,
  })

  // ── Stock Levels queries ──────────────────────────────────────────────────
  const { data: inventoryItems, isLoading: stockLoading } = useQuery({
    queryKey: ['inventory', storeId],
    queryFn:  () => inventoryApi.getState(storeId),
    enabled:  !!storeId && mainTab === 'stock',
  })
  const { data: zones } = useQuery({
    queryKey: ['store-zones', storeId],
    queryFn:  () => storesApi.zones(storeId),
    enabled:  !!storeId && mainTab === 'stock',
  })
  const { data: allProducts } = useQuery({
    queryKey: ['products-store-lookup', storeId],
    queryFn:  async () => {
      const all: Product[] = []
      let page = 0
      while (true) {
        const resp = await productsApi.list({ size: 500, page, storeId: storeId || undefined })
        if (!resp?.content?.length) break
        all.push(...resp.content)
        if (resp.last || all.length >= resp.totalElements) break
        page++
      }
      return all
    },
    enabled:   !!storeId,
    staleTime: 5 * 60 * 1000,
  })

  // ── RFID Ledger queries ───────────────────────────────────────────────────
  const {
    data: epcPageData, isLoading: epcLoading,
    dataUpdatedAt: epcUpdatedAt, refetch: refetchEpc,
  } = useQuery({
    queryKey: ['epc-ledger', storeId, statusFilter, epcPage],
    queryFn:  () => inventoryApi.epcLedger(storeId, statusFilter === 'all' ? '' : statusFilter, epcPage),
    enabled:  !!storeId && mainTab === 'ledger' && ledgerTab === 'epcs',
    refetchInterval: 30_000,
  })
  const {
    data: skuLedger, isLoading: skuLoading,
    dataUpdatedAt: skuUpdatedAt, refetch: refetchSku,
  } = useQuery({
    queryKey: ['sku-ledger', storeId],
    queryFn:  () => inventoryApi.skuLedger(storeId),
    enabled:  !!storeId && mainTab === 'ledger' && ledgerTab === 'sku',
    refetchInterval: 30_000,
  })

  // ── Stock Levels derived data ─────────────────────────────────────────────
  const productMap = useMemo(() => {
    const map: Record<string, Product> = {}
    for (const p of allProducts ?? []) map[p.id] = p
    return map
  }, [allProducts])

  const enrichedStock = useMemo((): StockRow[] =>
    (inventoryItems ?? []).map(item => {
      const p = productMap[item.productId]
      return { ...item, sku: p?.sku ?? item.productId.slice(-8), productName: p?.name ?? '—', brand: p?.brand ?? null }
    }),
  [inventoryItems, productMap])

  const brands = useMemo(() => {
    const s = new Set<string>()
    for (const r of enrichedStock) if (r.brand) s.add(r.brand)
    return Array.from(s).sort()
  }, [enrichedStock])

  const filteredStock = useMemo(() => enrichedStock.filter(r => {
    if (filterZone === 'store' && r.zoneId !== null) return false
    if (filterZone && filterZone !== 'store' && r.zoneId !== filterZone) return false
    if (filterBrand && r.brand !== filterBrand) return false
    if (filterAccuracy === 'high'   && (r.accuracyPct === null || r.accuracyPct < 95))                return false
    if (filterAccuracy === 'medium' && (r.accuracyPct === null || r.accuracyPct < 80 || r.accuracyPct >= 95)) return false
    if (filterAccuracy === 'low'    && (r.accuracyPct === null || r.accuracyPct >= 80))               return false
    if (filterAccuracy === 'na'     && r.accuracyPct !== null)                                        return false
    if (filterStock === 'instock'  && r.quantityOnHand === 0)                          return false
    if (filterStock === 'outstock' && r.quantityOnHand > 0)                            return false
    if (filterStock === 'variance' && r.quantityOnHand === r.quantityExpected)         return false
    return true
  }), [enrichedStock, filterZone, filterBrand, filterAccuracy, filterStock])

  const inventoryStats = useMemo(() => {
    const storeLevel = (inventoryItems ?? []).filter(i => i.zoneId == null)
    const totalExpected = storeLevel.reduce((s, i) => s + i.quantityExpected, 0)
    const totalScanned  = storeLevel.reduce((s, i) => s + i.quantityOnHand, 0)
    // Average each product's own accuracy rather than dividing raw unit totals — the latter
    // conflates "% of total units scanned so far" with per-product accuracy. Computed live
    // (not read from the stored accuracyPct) so legacy rows never backfilled with a real
    // value don't get silently excluded from the average — a product ERP expects but that
    // has never been scanned is a real 0%, not a data point to skip.
    const accuracyPct = storeLevel.length > 0
      ? storeLevel.reduce((s, i) => {
          const acc = i.quantityExpected === 0
            ? (i.quantityOnHand === 0 ? 100 : 0)
            : Math.min(100, 100 * i.quantityOnHand / i.quantityExpected)
          return s + acc
        }, 0) / storeLevel.length
      : null
    return { totalSkus: storeLevel.length, totalExpected, totalScanned, accuracyPct }
  }, [inventoryItems])

  const zoneLabel = useMemo(() => {
    if (!filterZone || filterZone === 'store') return null
    return (zones ?? []).find(({ id }) => id === filterZone)?.name ?? null
  }, [filterZone, zones])

  const stockSuggestions = useMemo(() => [
    ...enrichedStock.map(r => ({ id: `sku-${r.productId}`, label: r.sku, sublabel: r.productName, category: 'SKU', value: r.sku })),
    ...enrichedStock.filter((r, i, arr) => arr.findIndex(x => x.productName === r.productName) === i && r.productName !== '—')
      .map(r => ({ id: `name-${r.productId}`, label: r.productName, sublabel: r.sku, category: 'Product', value: r.productName })),
    ...brands.map(b => ({ id: `brand-${b}`, label: b, sublabel: 'Department', category: 'Brand', value: b })),
  ], [enrichedStock, brands])

  // ── RFID Ledger derived data ──────────────────────────────────────────────
  const enrichedSkuRows = useMemo((): EnrichedSkuRow[] =>
    (skuLedger ?? []).map(r => {
      const p = productMap[r.productId]
      return { ...r, sku: p?.sku ?? r.productId.slice(-8), productName: p?.name ?? '—' }
    }),
  [skuLedger, productMap])

  const filteredSkuRows = useMemo(() => {
    if (skuFilter === 'missing') return enrichedSkuRows.filter(r => r.missing > 0)
    if (skuFilter === 'sold')    return enrichedSkuRows.filter(r => r.sold > 0)
    if (skuFilter === 'damaged') return enrichedSkuRows.filter(r => r.damaged > 0)
    return enrichedSkuRows
  }, [enrichedSkuRows, skuFilter])

  const epcRows    = epcPageData?.content      ?? []
  const totalPages = epcPageData?.totalPages   ?? 1
  const totalEpcs  = epcPageData?.totalElements ?? 0

  const lastUpdated = ledgerTab === 'epcs'
    ? (epcUpdatedAt ? new Date(epcUpdatedAt).toLocaleTimeString() : null)
    : (skuUpdatedAt ? new Date(skuUpdatedAt).toLocaleTimeString() : null)

  const totals = {
    inStore: summary?.in_store ?? 0,
    sold:    summary?.sold     ?? 0,
    missing: summary?.missing  ?? 0,
    damaged: summary?.damaged  ?? 0,
  }

  // ── Columns ───────────────────────────────────────────────────────────────
  const stockColumns = useMemo<ColumnDef<StockRow, unknown>[]>(() => [
    {
      id: 'product',
      header: 'Product',
      accessorFn: r => r.sku + ' ' + r.productName,
      cell: ({ row: r }) => (
        <Link href={`/inventory/${r.original.productId}`} className="hover:underline block">
          <p className="font-mono text-xs text-blue-600">{r.original.sku}</p>
          <p className="text-xs text-gray-500 truncate max-w-[200px]">{r.original.productName}</p>
        </Link>
      ),
    },
    { accessorKey: 'brand',            header: 'Department',   cell: i => <span className="text-sm text-gray-700">{i.getValue<string|null>() ?? '—'}</span> },
    { accessorKey: 'quantityOnHand',   header: 'On Hand',      cell: i => <span className="font-semibold">{i.getValue<number>()}</span> },
    { accessorKey: 'quantityExpected', header: 'Expected' },
    {
      accessorKey: 'accuracyPct',
      header: 'Accuracy',
      cell: i => { const v = i.getValue<number | null>(); return <span className={accuracyColor(v)}>{fmtPct(v)}</span> },
    },
    { accessorKey: 'lastCountedAt', header: 'Last Counted', cell: i => fmtDateTime(i.getValue<string|null>()) },
  ], [])

  const skuColumns = useMemo<ColumnDef<EnrichedSkuRow, unknown>[]>(() => [
    {
      id: 'product',
      header: 'Product / SKU',
      accessorFn: r => r.sku + ' ' + r.productName,
      cell: ({ row: r }) => (
        <div>
          <p className="font-mono text-xs text-teal-700 font-semibold">{r.original.sku}</p>
          <p className="text-xs text-gray-500 truncate max-w-[200px]">{r.original.productName}</p>
        </div>
      ),
    },
    { accessorKey: 'inStore',     header: 'In Store',    cell: i => <span className={SKU_STATUS_COLORS.inStore}>{i.getValue<number>()}</span> },
    { accessorKey: 'sold',        header: 'Sold',        cell: i => { const v = i.getValue<number>(); return <span className={v > 0 ? SKU_STATUS_COLORS.sold        : 'text-gray-400'}>{v}</span> } },
    { accessorKey: 'missing',     header: 'Missing',     cell: i => { const v = i.getValue<number>(); return <span className={v > 0 ? SKU_STATUS_COLORS.missing     : 'text-gray-400'}>{v}</span> } },
    { accessorKey: 'damaged',     header: 'Damaged',     cell: i => { const v = i.getValue<number>(); return <span className={v > 0 ? SKU_STATUS_COLORS.damaged     : 'text-gray-400'}>{v}</span> } },
    { accessorKey: 'transferred', header: 'Transferred', cell: i => { const v = i.getValue<number>(); return <span className={v > 0 ? SKU_STATUS_COLORS.transferred : 'text-gray-400'}>{v}</span> } },
    { accessorKey: 'total',       header: 'Total EPCs',  cell: i => <span className="font-medium">{i.getValue<number>()}</span> },
    { accessorKey: 'lastSeenAt',  header: 'Last Seen',   cell: i => fmtDateTime(i.getValue<string | null>()) },
  ], [])

  // ── Shared style ──────────────────────────────────────────────────────────
  const selectCls = "text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500"
  const hasStockFilters = filterZone || filterBrand || filterAccuracy || filterStock

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <>
      <Header title="Inventory" />
      <div className="p-6 space-y-6">

        {/* Store selector (admin) */}
        {isAdmin && allStores && allStores.content.length > 0 && (
          <div className="flex flex-wrap items-center gap-3">
            <label className="text-sm font-medium text-gray-600 shrink-0">Store</label>
            <select value={storeId} onChange={e => handleStoreChange(e.target.value)} className={selectCls}>
              {allStores.content.map(s => (
                <option key={s.id} value={s.id}>{s.name} ({s.storeCode})</option>
              ))}
            </select>
          </div>
        )}

        {/* Main tab bar */}
        <div className="flex items-center gap-1 p-1 bg-gray-100 rounded-xl w-fit">
          {([['stock', 'Stock Levels'], ['ledger', 'RFID Ledger']] as const).map(([tab, label]) => (
            <button
              key={tab}
              onClick={() => setMainTab(tab)}
              className={`px-5 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                mainTab === tab ? 'bg-white shadow text-gray-900' : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        {/* ── STOCK LEVELS TAB ─────────────────────────────────────────────── */}
        {mainTab === 'stock' && (
          <>
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
              <StatCard title="Total SKUs"   value={inventoryStats.totalSkus.toLocaleString()}    icon={Package}       color="blue"  />
              <StatCard title="RFID Scanned" value={inventoryStats.totalScanned.toLocaleString()}
                sub={`of ${inventoryStats.totalExpected.toLocaleString()} ERP expected`}           icon={ScanLine}      color="green" />
              <StatCard title="Missing EPC"  value={(summary?.missing ?? 0).toLocaleString()}
                sub="not detected by RFID"                                                          icon={AlertTriangle} color="red"   />
              <StatCard
                title="Accuracy"
                value={inventoryStats.accuracyPct != null ? `${inventoryStats.accuracyPct.toFixed(1)}%` : '—'}
                sub={inventoryStats.accuracyPct != null && inventoryStats.accuracyPct < 95 ? 'Below 95% target' : 'On target'}
                icon={Target}
                color={
                  inventoryStats.accuracyPct == null ? 'blue'   :
                  inventoryStats.accuracyPct >= 95   ? 'green'  :
                  inventoryStats.accuracyPct >= 80   ? 'yellow' : 'red'
                }
              />
            </div>

            <div className="card">
              <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
                <div className="flex items-center gap-2">
                  <h2 className="text-sm font-semibold text-gray-700">Inventory State by Product</h2>
                  {zoneLabel && (
                    <span className="inline-flex items-center gap-1 text-xs bg-blue-50 text-blue-700 border border-blue-200 rounded-full px-2 py-0.5">
                      <MapPin size={10} /> {zoneLabel}
                    </span>
                  )}
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  {zones && zones.length > 0 && (
                    <select value={filterZone} onChange={e => setFilterZone(e.target.value)} className={selectCls}>
                      <option value="">All Zones</option>
                      {zones.map(({ id, name }) => <option key={id} value={id}>{name}</option>)}
                      <option value="store">Store Level</option>
                    </select>
                  )}
                  <select value={filterBrand} onChange={e => setFilterBrand(e.target.value)} className={selectCls}>
                    <option value="">All Departments</option>
                    {brands.map(b => <option key={b} value={b}>{b}</option>)}
                  </select>
                  <select value={filterAccuracy} onChange={e => setFilterAccuracy(e.target.value)} className={selectCls}>
                    {ACC_BANDS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                  </select>
                  <select value={filterStock} onChange={e => setFilterStock(e.target.value)} className={selectCls}>
                    {STOCK_OPTS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                  </select>
                  {hasStockFilters && (
                    <button
                      onClick={() => { setFilterZone(''); setFilterBrand(''); setFilterAccuracy(''); setFilterStock('') }}
                      className="text-xs text-gray-500 hover:text-gray-700 underline"
                    >
                      Clear
                    </button>
                  )}
                </div>
              </div>
              <DataTable
                data={filteredStock}
                columns={stockColumns}
                isLoading={stockLoading}
                searchable
                searchPlaceholder="Search SKU or product name…"
                suggestions={stockSuggestions}
              />
            </div>
          </>
        )}

        {/* ── RFID LEDGER TAB ──────────────────────────────────────────────── */}
        {mainTab === 'ledger' && (
          <>
            {/* Refresh + last updated */}
            <div className="flex flex-wrap items-center gap-3">
              <button
                onClick={() => ledgerTab === 'epcs' ? refetchEpc() : refetchSku()}
                className="btn-secondary flex items-center gap-1.5"
              >
                <RefreshCw size={14} /> Refresh
              </button>
              {lastUpdated && (
                <span className="text-xs text-gray-400">Updated {lastUpdated} · auto-refreshes every 30s</span>
              )}
            </div>

            {/* Stat cards */}
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
              <StatCard title="In Store (RFID)" value={totals.inStore.toLocaleString()} icon={Package}       color="teal"   />
              <StatCard title="Sold (Gate)"      value={totals.sold.toLocaleString()}    icon={ShoppingCart}  color="green"  />
              <StatCard title="Missing"          value={totals.missing.toLocaleString()} icon={AlertTriangle} color={totals.missing > 0 ? 'red'    : 'green'} />
              <StatCard title="Damaged"          value={totals.damaged.toLocaleString()} icon={Tag}           color={totals.damaged > 0 ? 'yellow' : 'green'} />
            </div>

            {/* Sub-tab bar */}
            <div className="flex items-center gap-1 p-1 bg-gray-100 rounded-xl w-fit">
              {([['epcs', 'EPC Tags'], ['sku', 'SKU Summary']] as const).map(([tab, label]) => (
                <button
                  key={tab}
                  onClick={() => setLedgerTab(tab)}
                  className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                    ledgerTab === tab ? 'bg-white shadow text-gray-900' : 'text-gray-500 hover:text-gray-700'
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>

            {/* EPC Tags */}
            {ledgerTab === 'epcs' && (
              <div className="card">
                <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
                  <h2 className="text-sm font-semibold text-gray-700">
                    Individual EPC Tags
                    {totalEpcs > 0 && (
                      <span className="ml-2 text-xs font-normal text-gray-400">
                        — {totalEpcs.toLocaleString()} tag{totalEpcs !== 1 ? 's' : ''}
                        {statusFilter !== 'all' ? ` · status: ${STATUS_LABEL[statusFilter]}` : ''}
                      </span>
                    )}
                  </h2>
                  <div className="flex items-center gap-1 p-1 bg-gray-100 rounded-lg flex-wrap">
                    {(['all', 'in_store', 'sold', 'missing', 'damaged'] as const).map(f => (
                      <button
                        key={f}
                        onClick={() => { setStatusFilter(f); setEpcPage(0) }}
                        className={`px-3 py-1 rounded-md text-xs font-medium transition-colors ${
                          statusFilter === f ? 'bg-white shadow text-gray-900' : 'text-gray-500 hover:text-gray-700'
                        }`}
                      >
                        {f === 'all' ? 'All' : STATUS_LABEL[f]}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="bg-white rounded-xl border border-gray-100 overflow-hidden shadow-sm">
                  <div className="overflow-x-auto">
                    <table className="min-w-full divide-y divide-gray-100">
                      <thead className="bg-gray-50">
                        <tr>
                          <th className="table-th">EPC Tag</th>
                          <th className="table-th">Product / SKU</th>
                          <th className="table-th">Zone</th>
                          <th className="table-th">Status</th>
                          <th className="table-th">Last Seen</th>
                          <th className="table-th">First Seen</th>
                        </tr>
                      </thead>
                      <tbody className="bg-white divide-y divide-gray-50">
                        {epcLoading ? (
                          <tr><td colSpan={6} className="table-td text-center text-gray-400 py-12">Loading…</td></tr>
                        ) : epcRows.length === 0 ? (
                          <tr><td colSpan={6} className="table-td text-center text-gray-400 py-12">No EPC tags found</td></tr>
                        ) : epcRows.map((row: EpcLedgerRow) => (
                          <tr key={row.epc} className="hover:bg-gray-50 transition-colors">
                            <td className="table-td"><span className="font-mono text-xs text-gray-700 select-all">{row.epc}</span></td>
                            <td className="table-td">
                              <p className="font-mono text-xs text-teal-700 font-semibold">{row.sku ?? '—'}</p>
                              <p className="text-xs text-gray-500 truncate max-w-[180px]">{row.productName ?? '—'}</p>
                            </td>
                            <td className="table-td"><span className="text-xs text-gray-600">{row.zoneName ?? '—'}</span></td>
                            <td className="table-td">
                              <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ring-1 ring-inset ${STATUS_BADGE[row.status] ?? ''}`}>
                                {STATUS_LABEL[row.status] ?? row.status}
                              </span>
                            </td>
                            <td className="table-td text-xs text-gray-500">{fmtDateTime(row.lastSeenAt)}</td>
                            <td className="table-td text-xs text-gray-500">{fmtDateTime(row.firstSeenAt)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100 bg-gray-50">
                    <p className="text-xs text-gray-500">
                      Page {epcPage + 1} of {totalPages} · {totalEpcs.toLocaleString()} total tags
                    </p>
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => setEpcPage(p => Math.max(0, p - 1))}
                        disabled={epcPage === 0}
                        className="btn-secondary py-1 px-2 text-xs disabled:opacity-40 flex items-center gap-1"
                      >
                        <ChevronLeft size={14} /> Prev
                      </button>
                      <button
                        onClick={() => setEpcPage(p => Math.min(totalPages - 1, p + 1))}
                        disabled={epcPage >= totalPages - 1}
                        className="btn-secondary py-1 px-2 text-xs disabled:opacity-40 flex items-center gap-1"
                      >
                        Next <ChevronRight size={14} />
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* SKU Summary */}
            {ledgerTab === 'sku' && (
              <div className="card">
                <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
                  <h2 className="text-sm font-semibold text-gray-700">
                    EPC Count by SKU
                    <span className="ml-2 text-xs font-normal text-gray-400">— aggregated totals per product</span>
                  </h2>
                  <div className="flex items-center gap-1 p-1 bg-gray-100 rounded-lg">
                    {(['all', 'missing', 'sold', 'damaged'] as const).map(f => (
                      <button
                        key={f}
                        onClick={() => setSkuFilter(f)}
                        className={`px-3 py-1 rounded-md text-xs font-medium transition-colors ${
                          skuFilter === f ? 'bg-white shadow text-gray-900' : 'text-gray-500 hover:text-gray-700'
                        }`}
                      >
                        {f === 'all' ? 'All' : f.charAt(0).toUpperCase() + f.slice(1)}
                      </button>
                    ))}
                  </div>
                </div>
                <DataTable
                  data={filteredSkuRows}
                  columns={skuColumns}
                  isLoading={skuLoading}
                  searchable
                  searchPlaceholder="Search SKU or product name…"
                />
              </div>
            )}
          </>
        )}

      </div>
    </>
  )
}
