'use client'

import { useQuery }          from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { type ColumnDef }    from '@tanstack/react-table'
import { ShoppingCart, Package, AlertTriangle, Tag, RefreshCw } from 'lucide-react'
import Header                from '@/components/layout/Header'
import DataTable             from '@/components/ui/DataTable'
import StatCard              from '@/components/ui/StatCard'
import { inventoryApi }      from '@/lib/api/inventory'
import { productsApi }       from '@/lib/api/products'
import { storesApi }         from '@/lib/api/stores'
import { useAuth }           from '@/lib/auth/AuthContext'
import { fmtDateTime }       from '@/lib/utils'
import type { SkuLedgerRow, Product } from '@/types'

type EnrichedRow = SkuLedgerRow & { sku: string; productName: string }

const STATUS_COLORS: Record<string, string> = {
  inStore:     'text-teal-700 font-semibold',
  sold:        'text-green-600 font-semibold',
  missing:     'text-red-600 font-semibold',
  damaged:     'text-orange-600 font-semibold',
  transferred: 'text-blue-600',
}

export default function RfidLedgerPage() {
  const { user, isAdmin }    = useAuth()
  const [selectedStoreId, setSelectedStoreId] = useState('')
  const [filter, setFilter]  = useState<'all' | 'missing' | 'sold' | 'damaged'>('all')

  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })

  const storeId = isAdmin
    ? (selectedStoreId || allStores?.content[0]?.id || '')
    : (user?.storeId ?? '')

  const { data: ledger, isLoading, refetch, dataUpdatedAt } = useQuery({
    queryKey: ['sku-ledger', storeId],
    queryFn:  () => inventoryApi.skuLedger(storeId),
    enabled:  !!storeId,
    refetchInterval: 30_000,  // auto-refresh every 30 s
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

  const productMap = useMemo(() => {
    const map: Record<string, Product> = {}
    for (const p of allProducts ?? []) map[p.id] = p
    return map
  }, [allProducts])

  const rows = useMemo((): EnrichedRow[] =>
    (ledger ?? []).map(r => {
      const p = productMap[r.productId]
      return { ...r, sku: p?.sku ?? r.productId.slice(-8), productName: p?.name ?? '—' }
    }),
  [ledger, productMap])

  const filtered = useMemo(() => {
    if (filter === 'all')      return rows
    if (filter === 'missing')  return rows.filter(r => r.missing > 0)
    if (filter === 'sold')     return rows.filter(r => r.sold > 0)
    if (filter === 'damaged')  return rows.filter(r => r.damaged > 0)
    return rows
  }, [rows, filter])

  // Summary totals
  const totals = useMemo(() => ({
    inStore:     rows.reduce((s, r) => s + r.inStore, 0),
    sold:        rows.reduce((s, r) => s + r.sold, 0),
    missing:     rows.reduce((s, r) => s + r.missing, 0),
    damaged:     rows.reduce((s, r) => s + r.damaged, 0),
  }), [rows])

  const columns = useMemo<ColumnDef<EnrichedRow, unknown>[]>(() => [
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
    {
      accessorKey: 'inStore',
      header: 'In Store',
      cell: i => <span className={STATUS_COLORS.inStore}>{i.getValue<number>()}</span>,
    },
    {
      accessorKey: 'sold',
      header: 'Sold',
      cell: i => {
        const v = i.getValue<number>()
        return <span className={v > 0 ? STATUS_COLORS.sold : 'text-gray-400'}>{v}</span>
      },
    },
    {
      accessorKey: 'missing',
      header: 'Missing',
      cell: i => {
        const v = i.getValue<number>()
        return <span className={v > 0 ? STATUS_COLORS.missing : 'text-gray-400'}>{v}</span>
      },
    },
    {
      accessorKey: 'damaged',
      header: 'Damaged',
      cell: i => {
        const v = i.getValue<number>()
        return <span className={v > 0 ? STATUS_COLORS.damaged : 'text-gray-400'}>{v}</span>
      },
    },
    {
      accessorKey: 'transferred',
      header: 'Transferred',
      cell: i => {
        const v = i.getValue<number>()
        return <span className={v > 0 ? STATUS_COLORS.transferred : 'text-gray-400'}>{v}</span>
      },
    },
    {
      accessorKey: 'total',
      header: 'Total EPCs',
      cell: i => <span className="font-medium">{i.getValue<number>()}</span>,
    },
    {
      accessorKey: 'lastSeenAt',
      header: 'Last Seen',
      cell: i => fmtDateTime(i.getValue<string | null>()),
    },
  ], [])

  const selectCls = "text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500"

  const lastUpdated = dataUpdatedAt ? new Date(dataUpdatedAt).toLocaleTimeString() : null

  return (
    <>
      <Header title="RFID Ledger" />
      <div className="p-6 space-y-6">

        {/* Store selector + refresh */}
        <div className="flex flex-wrap items-center gap-3">
          {isAdmin && allStores && (
            <>
              <label className="text-sm font-medium text-gray-600 shrink-0">Store</label>
              <select value={storeId} onChange={e => setSelectedStoreId(e.target.value)} className={selectCls}>
                {allStores.content.map(s => (
                  <option key={s.id} value={s.id}>{s.name} ({s.storeCode})</option>
                ))}
              </select>
            </>
          )}
          <button onClick={() => refetch()} className="btn-secondary flex items-center gap-1.5">
            <RefreshCw size={14} /> Refresh
          </button>
          {lastUpdated && (
            <span className="text-xs text-gray-400">Updated {lastUpdated} · auto-refreshes every 30s</span>
          )}
        </div>

        {/* Summary cards */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard title="In Store (RFID)" value={totals.inStore.toLocaleString()} icon={Package}  color="teal" />
          <StatCard title="Sold (Gate)"      value={totals.sold.toLocaleString()}    icon={ShoppingCart} color="green" />
          <StatCard title="Missing"          value={totals.missing.toLocaleString()} icon={AlertTriangle}
            color={totals.missing > 0 ? 'red' : 'green'} />
          <StatCard title="Damaged"          value={totals.damaged.toLocaleString()} icon={Tag}
            color={totals.damaged > 0 ? 'yellow' : 'green'} />
        </div>

        {/* Table */}
        <div className="card">
          <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
            <h2 className="text-sm font-semibold text-gray-700">
              EPC Status by SKU
              <span className="ml-2 text-xs font-normal text-gray-400">
                — live view of every tag in the RFID ledger
              </span>
            </h2>

            {/* Filter tabs */}
            <div className="flex items-center gap-1 p-1 bg-gray-100 rounded-lg">
              {(['all', 'missing', 'sold', 'damaged'] as const).map(f => (
                <button
                  key={f}
                  onClick={() => setFilter(f)}
                  className={`px-3 py-1 rounded-md text-xs font-medium transition-colors ${
                    filter === f
                      ? 'bg-white shadow text-gray-900'
                      : 'text-gray-500 hover:text-gray-700'
                  }`}
                >
                  {f === 'all' ? 'All' : f.charAt(0).toUpperCase() + f.slice(1)}
                  {f !== 'all' && (
                    <span className={`ml-1 ${
                      f === 'missing' && totals.missing > 0 ? 'text-red-500' :
                      f === 'sold'    ? 'text-green-600' :
                      f === 'damaged' && totals.damaged > 0 ? 'text-orange-500' : ''
                    }`}>
                      ({f === 'missing' ? totals.missing : f === 'sold' ? totals.sold : totals.damaged})
                    </span>
                  )}
                </button>
              ))}
            </div>
          </div>

          <DataTable
            data={filtered}
            columns={columns}
            isLoading={isLoading}
            searchable
            searchPlaceholder="Search SKU or product name…"
          />
        </div>

      </div>
    </>
  )
}
