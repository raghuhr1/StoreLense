'use client'

import { useQuery }          from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { type ColumnDef }    from '@tanstack/react-table'
import { ShoppingCart, Package, AlertTriangle, Tag, RefreshCw, ChevronLeft, ChevronRight } from 'lucide-react'
import Header                from '@/components/layout/Header'
import DataTable             from '@/components/ui/DataTable'
import StatCard              from '@/components/ui/StatCard'
import { inventoryApi }      from '@/lib/api/inventory'
import { productsApi }       from '@/lib/api/products'
import { storesApi }         from '@/lib/api/stores'
import { useAuth }           from '@/lib/auth/AuthContext'
import { fmtDateTime }       from '@/lib/utils'
import type { EpcLedgerRow, SkuLedgerRow, Product } from '@/types'

type EnrichedSkuRow  = SkuLedgerRow & { sku: string; productName: string }
type StatusFilter    = 'all' | 'in_store' | 'sold' | 'missing' | 'damaged'

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

export default function RfidLedgerPage() {
  const { user, isAdmin }    = useAuth()
  const [selectedStoreId, setSelectedStoreId] = useState('')
  const [activeTab, setActiveTab]    = useState<'epcs' | 'sku'>('epcs')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all')
  const [skuFilter, setSkuFilter]    = useState<'all' | 'missing' | 'sold' | 'damaged'>('all')
  const [page, setPage]              = useState(0)

  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })

  const storeId = isAdmin
    ? (selectedStoreId || allStores?.content[0]?.id || '')
    : (user?.storeId ?? '')

  const handleStatusFilter = (s: StatusFilter) => { setStatusFilter(s); setPage(0) }
  const handleStoreChange  = (id: string)       => { setSelectedStoreId(id); setPage(0) }

  // EPC-level (paginated, server-side)
  const { data: epcPage, isLoading: epcLoading, dataUpdatedAt: epcUpdatedAt, refetch: refetchEpc } = useQuery({
    queryKey: ['epc-ledger', storeId, statusFilter, page],
    queryFn:  () => inventoryApi.epcLedger(storeId, statusFilter === 'all' ? '' : statusFilter, page),
    enabled:  !!storeId && activeTab === 'epcs',
    refetchInterval: 30_000,
  })

  // SKU-level summary
  const { data: skuLedger, isLoading: skuLoading, dataUpdatedAt: skuUpdatedAt, refetch: refetchSku } = useQuery({
    queryKey: ['sku-ledger', storeId],
    queryFn:  () => inventoryApi.skuLedger(storeId),
    enabled:  !!storeId && activeTab === 'sku',
    refetchInterval: 30_000,
  })

  // Summary cards — always loaded
  const { data: summary } = useQuery({
    queryKey: ['epc-summary', storeId],
    queryFn:  () => inventoryApi.epcSummary(storeId),
    enabled:  !!storeId,
    refetchInterval: 30_000,
  })

  // Product lookup for SKU tab enrichment
  const { data: allProducts } = useQuery({
    queryKey: ['products-all-lookup'],
    queryFn:  async () => {
      const all: Product[] = []
      let p = 0
      while (true) {
        const resp = await productsApi.list({ size: 500, page: p })
        if (!resp?.content?.length) break
        all.push(...resp.content)
        if (resp.last || all.length >= resp.totalElements) break
        p++
      }
      return all
    },
    staleTime: 5 * 60 * 1000,
    enabled:   activeTab === 'sku',
  })

  const productMap = useMemo(() => {
    const map: Record<string, Product> = {}
    for (const p of allProducts ?? []) map[p.id] = p
    return map
  }, [allProducts])

  const enrichedSkuRows = useMemo((): EnrichedSkuRow[] =>
    (skuLedger ?? []).map(r => {
      const p = productMap[r.productId]
      return { ...r, sku: p?.sku ?? r.productId.slice(-8), productName: p?.name ?? '—' }
    }),
  [skuLedger, productMap])

  const filteredSkuRows = useMemo(() => {
    if (skuFilter === 'all')     return enrichedSkuRows
    if (skuFilter === 'missing') return enrichedSkuRows.filter(r => r.missing > 0)
    if (skuFilter === 'sold')    return enrichedSkuRows.filter(r => r.sold > 0)
    if (skuFilter === 'damaged') return enrichedSkuRows.filter(r => r.damaged > 0)
    return enrichedSkuRows
  }, [enrichedSkuRows, skuFilter])

  const totals = {
    inStore:  summary?.in_store ?? 0,
    sold:     summary?.sold     ?? 0,
    missing:  summary?.missing  ?? 0,
    damaged:  summary?.damaged  ?? 0,
  }


  // ── SKU summary columns ────────────────────────────────────────────────────
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

  const selectCls  = "text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500"
  const lastUpdated = activeTab === 'epcs'
    ? (epcUpdatedAt ? new Date(epcUpdatedAt).toLocaleTimeString() : null)
    : (skuUpdatedAt ? new Date(skuUpdatedAt).toLocaleTimeString() : null)

  const epcRows    = epcPage?.content      ?? []
  const totalPages = epcPage?.totalPages   ?? 1
  const totalEpcs  = epcPage?.totalElements ?? 0

  return (
    <>
      <Header title="RFID Ledger" />
      <div className="p-6 space-y-6">

        {/* Store selector + refresh */}
        <div className="flex flex-wrap items-center gap-3">
          {isAdmin && allStores && (
            <>
              <label className="text-sm font-medium text-gray-600 shrink-0">Store</label>
              <select value={storeId} onChange={e => handleStoreChange(e.target.value)} className={selectCls}>
                {allStores.content.map(s => (
                  <option key={s.id} value={s.id}>{s.name} ({s.storeCode})</option>
                ))}
              </select>
            </>
          )}
          <button
            onClick={() => activeTab === 'epcs' ? refetchEpc() : refetchSku()}
            className="btn-secondary flex items-center gap-1.5"
          >
            <RefreshCw size={14} /> Refresh
          </button>
          {lastUpdated && (
            <span className="text-xs text-gray-400">Updated {lastUpdated} · auto-refreshes every 30s</span>
          )}
        </div>

        {/* Summary cards */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard title="In Store (RFID)" value={totals.inStore.toLocaleString()} icon={Package}      color="teal"  />
          <StatCard title="Sold (Gate)"     value={totals.sold.toLocaleString()}    icon={ShoppingCart} color="green" />
          <StatCard title="Missing"         value={totals.missing.toLocaleString()} icon={AlertTriangle}
            color={totals.missing > 0 ? 'red' : 'green'} />
          <StatCard title="Damaged"         value={totals.damaged.toLocaleString()} icon={Tag}
            color={totals.damaged > 0 ? 'yellow' : 'green'} />
        </div>

        {/* Tab bar */}
        <div className="flex items-center gap-1 p-1 bg-gray-100 rounded-xl w-fit">
          {(['epcs', 'sku'] as const).map(tab => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                activeTab === tab ? 'bg-white shadow text-gray-900' : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              {tab === 'epcs' ? 'EPC Tags' : 'SKU Summary'}
            </button>
          ))}
        </div>

        {/* ── EPC Tags tab ─────────────────────────────────────────────────── */}
        {activeTab === 'epcs' && (
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

              {/* Status filter tabs */}
              <div className="flex items-center gap-1 p-1 bg-gray-100 rounded-lg flex-wrap">
                {(['all', 'in_store', 'sold', 'missing', 'damaged'] as const).map(f => (
                  <button
                    key={f}
                    onClick={() => handleStatusFilter(f)}
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
                    ) : epcRows.map(row => (
                      <tr key={row.epc} className="hover:bg-gray-50 transition-colors">
                        <td className="table-td">
                          <span className="font-mono text-xs text-gray-700 select-all">{row.epc}</span>
                        </td>
                        <td className="table-td">
                          <p className="font-mono text-xs text-teal-700 font-semibold">{row.sku ?? '—'}</p>
                          <p className="text-xs text-gray-500 truncate max-w-[180px]">{row.productName ?? '—'}</p>
                        </td>
                        <td className="table-td">
                          <span className="text-xs text-gray-600">{row.zoneName ?? '—'}</span>
                        </td>
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

              {/* Server-side pagination */}
              <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100 bg-gray-50">
                <p className="text-xs text-gray-500">
                  Page {page + 1} of {totalPages} · {totalEpcs.toLocaleString()} total tags
                </p>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setPage(p => Math.max(0, p - 1))}
                    disabled={page === 0}
                    className="btn-secondary py-1 px-2 text-xs disabled:opacity-40 flex items-center gap-1"
                  >
                    <ChevronLeft size={14} /> Prev
                  </button>
                  <button
                    onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                    disabled={page >= totalPages - 1}
                    className="btn-secondary py-1 px-2 text-xs disabled:opacity-40 flex items-center gap-1"
                  >
                    Next <ChevronRight size={14} />
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ── SKU Summary tab ───────────────────────────────────────────────── */}
        {activeTab === 'sku' && (
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

      </div>
    </>
  )
}
