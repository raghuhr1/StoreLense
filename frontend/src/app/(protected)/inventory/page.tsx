'use client'

import { useQuery }       from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { type ColumnDef }  from '@tanstack/react-table'
import Link                from 'next/link'
import Header              from '@/components/layout/Header'
import DataTable           from '@/components/ui/DataTable'
import StatCard            from '@/components/ui/StatCard'
import { Package, AlertTriangle, TrendingDown } from 'lucide-react'
import { inventoryApi }  from '@/lib/api/inventory'
import { productsApi }   from '@/lib/api/products'
import { storesApi }     from '@/lib/api/stores'
import { useAuth }       from '@/lib/auth/AuthContext'
import { fmtPct, fmtDateTime, accuracyColor } from '@/lib/utils'
import type { InventoryState, Product } from '@/types'

type EnrichedRow = InventoryState & {
  sku:         string
  productName: string
  brand:       string | null
}

const ACC_BANDS = [
  { value: '',       label: 'All Accuracy' },
  { value: 'high',   label: 'High  (≥95%)' },
  { value: 'medium', label: 'Medium (80–94%)' },
  { value: 'low',    label: 'Low  (<80%)' },
  { value: 'na',     label: 'N/A (no count)' },
]

const STOCK_OPTS = [
  { value: '',          label: 'All Stock' },
  { value: 'instock',   label: 'In Stock (>0)' },
  { value: 'outstock',  label: 'Out of Stock (0)' },
  { value: 'variance',  label: 'Has Variance' },
]

export default function InventoryPage() {
  const { user, isAdmin }   = useAuth()
  const [selectedStoreId, setSelectedStoreId] = useState<string>('')
  const [filterBrand,    setFilterBrand]    = useState('')
  const [filterAccuracy, setFilterAccuracy] = useState('')
  const [filterStock,    setFilterStock]    = useState('')

  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })

  const storeId = isAdmin
    ? (selectedStoreId || allStores?.content[0]?.id || '')
    : (user?.storeId ?? '')

  const { data: items, isLoading } = useQuery({
    queryKey: ['inventory', storeId],
    queryFn:  () => inventoryApi.getState(storeId),
    enabled:  !!storeId,
  })

  const { data: summary } = useQuery({
    queryKey: ['epc-summary', storeId],
    queryFn:  () => inventoryApi.epcSummary(storeId),
    enabled:  !!storeId,
  })

  const { data: lowAcc } = useQuery({
    queryKey: ['low-accuracy', storeId],
    queryFn:  () => inventoryApi.getLowAccuracy(storeId, 95),
    enabled:  !!storeId,
  })

  // Load all products for brand/name enrichment (cached, background)
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

  const enrichedItems = useMemo((): EnrichedRow[] =>
    (items ?? []).map(item => {
      const p = productMap[item.productId]
      return {
        ...item,
        sku:         p?.sku         ?? item.productId.slice(-8),
        productName: p?.name        ?? '—',
        brand:       p?.brand       ?? null,
      }
    }),
  [items, productMap])

  const brands = useMemo(() => {
    const s = new Set<string>()
    for (const r of enrichedItems) if (r.brand) s.add(r.brand)
    return Array.from(s).sort()
  }, [enrichedItems])

  const filteredItems = useMemo(() => {
    return enrichedItems.filter(r => {
      if (filterBrand && r.brand !== filterBrand) return false
      if (filterAccuracy === 'high'   && (r.accuracyPct === null || r.accuracyPct < 95))  return false
      if (filterAccuracy === 'medium' && (r.accuracyPct === null || r.accuracyPct < 80 || r.accuracyPct >= 95)) return false
      if (filterAccuracy === 'low'    && (r.accuracyPct === null || r.accuracyPct >= 80)) return false
      if (filterAccuracy === 'na'     && r.accuracyPct !== null) return false
      if (filterStock === 'instock'   && r.quantityOnHand === 0) return false
      if (filterStock === 'outstock'  && r.quantityOnHand > 0)   return false
      if (filterStock === 'variance'  && r.quantityOnHand === r.quantityExpected) return false
      return true
    })
  }, [enrichedItems, filterBrand, filterAccuracy, filterStock])

  const hasFilters = filterBrand || filterAccuracy || filterStock

  const columns = useMemo<ColumnDef<EnrichedRow, unknown>[]>(() => [
    {
      id: 'product',
      header: 'Product',
      accessorFn: r => r.sku + ' ' + r.productName,
      cell: ({ row: r }) => (
        <Link href={`/inventory/${r.original.productId}`}
              className="hover:underline block">
          <p className="font-mono text-xs text-blue-600">{r.original.sku}</p>
          <p className="text-xs text-gray-500 truncate max-w-[200px]">{r.original.productName}</p>
        </Link>
      ),
    },
    {
      accessorKey: 'brand',
      header: 'Brand',
      cell: i => <span className="text-sm text-gray-700">{i.getValue<string|null>() ?? '—'}</span>,
    },
    {
      accessorKey: 'quantityOnHand',
      header: 'On Hand',
      cell: i => <span className="font-semibold">{i.getValue<number>()}</span>,
    },
    { accessorKey: 'quantityExpected', header: 'Expected' },
    {
      accessorKey: 'accuracyPct',
      header: 'Accuracy',
      cell: i => {
        const v = i.getValue<number | null>()
        return <span className={accuracyColor(v)}>{fmtPct(v)}</span>
      },
    },
    {
      accessorKey: 'lastCountedAt',
      header: 'Last Counted',
      cell: i => fmtDateTime(i.getValue<string|null>()),
    },
  ], [])

  const selectCls = "text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500"

  return (
    <>
      <Header title="Inventory" />
      <div className="p-6 space-y-6">

        {/* Store selector (admin) */}
        {isAdmin && allStores && allStores.content.length > 0 && (
          <div className="flex items-center gap-3">
            <label className="text-sm font-medium text-gray-600 shrink-0">Store</label>
            <select value={storeId} onChange={e => setSelectedStoreId(e.target.value)} className={selectCls}>
              {allStores.content.map(s => (
                <option key={s.id} value={s.id}>{s.name} ({s.storeCode})</option>
              ))}
            </select>
          </div>
        )}

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard title="Total SKUs"     value={items?.length ?? 0}      icon={Package}       color="blue" />
          <StatCard title="In Store (EPC)" value={summary?.in_store ?? 0}  icon={Package}       color="green" />
          <StatCard title="Missing (EPC)"  value={summary?.missing ?? 0}   icon={AlertTriangle} color="red" />
          <StatCard title="Low Accuracy"   value={lowAcc?.length ?? 0}     icon={TrendingDown}  color="yellow" />
        </div>

        <div className="card">
          <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
            <h2 className="text-sm font-semibold text-gray-700">Inventory State by Product</h2>

            {/* Filters */}
            <div className="flex flex-wrap items-center gap-2">
              {/* Brand */}
              <select value={filterBrand} onChange={e => setFilterBrand(e.target.value)} className={selectCls}>
                <option value="">All Brands</option>
                {brands.map(b => <option key={b} value={b}>{b}</option>)}
              </select>

              {/* Accuracy band */}
              <select value={filterAccuracy} onChange={e => setFilterAccuracy(e.target.value)} className={selectCls}>
                {ACC_BANDS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
              </select>

              {/* Stock status */}
              <select value={filterStock} onChange={e => setFilterStock(e.target.value)} className={selectCls}>
                {STOCK_OPTS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
              </select>

              {hasFilters && (
                <button
                  onClick={() => { setFilterBrand(''); setFilterAccuracy(''); setFilterStock('') }}
                  className="text-xs text-gray-500 hover:text-gray-700 underline"
                >
                  Clear
                </button>
              )}
            </div>
          </div>

          <DataTable
            data={filteredItems}
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
