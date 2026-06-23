'use client'

import { useQuery }       from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { type ColumnDef }  from '@tanstack/react-table'
import Link                from 'next/link'
import Header              from '@/components/layout/Header'
import DataTable           from '@/components/ui/DataTable'
import StatCard            from '@/components/ui/StatCard'
import { Package, AlertTriangle, ScanLine, Target, MapPin } from 'lucide-react'
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
  { value: '',         label: 'All Stock' },
  { value: 'instock',  label: 'In Stock (>0)' },
  { value: 'outstock', label: 'Out of Stock (0)' },
  { value: 'variance', label: 'Has Variance' },
]

export default function InventoryPage() {
  const { user, isAdmin }   = useAuth()
  const [selectedStoreId, setSelectedStoreId] = useState<string>('')
  const [filterBrand,    setFilterBrand]    = useState('')
  const [filterZone,     setFilterZone]     = useState('')   // '' = all zones, 'store' = zone_id IS NULL
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


  // Store zones (Sales Floor, Backroom, etc.)
  const { data: zones } = useQuery({
    queryKey: ['store-zones', storeId],
    queryFn:  () => storesApi.zones(storeId),
    enabled:  !!storeId,
  })

  // Load store-scoped products for brand/name enrichment (cached 5 min)
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
        sku:         p?.sku   ?? item.productId.slice(-8),
        productName: p?.name  ?? '—',
        brand:       p?.brand ?? null,
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
      // Zone filter
      if (filterZone === 'store' && r.zoneId !== null) return false
      if (filterZone && filterZone !== 'store' && r.zoneId !== filterZone) return false
      // Brand
      if (filterBrand && r.brand !== filterBrand) return false
      // Accuracy band
      if (filterAccuracy === 'high'   && (r.accuracyPct === null || r.accuracyPct < 95))               return false
      if (filterAccuracy === 'medium' && (r.accuracyPct === null || r.accuracyPct < 80 || r.accuracyPct >= 95)) return false
      if (filterAccuracy === 'low'    && (r.accuracyPct === null || r.accuracyPct >= 80))               return false
      if (filterAccuracy === 'na'     && r.accuracyPct !== null)                                        return false
      // Stock status
      if (filterStock === 'instock'   && r.quantityOnHand === 0)                        return false
      if (filterStock === 'outstock'  && r.quantityOnHand > 0)                          return false
      if (filterStock === 'variance'  && r.quantityOnHand === r.quantityExpected)        return false
      return true
    })
  }, [enrichedItems, filterZone, filterBrand, filterAccuracy, filterStock])

  const hasFilters = filterZone || filterBrand || filterAccuracy || filterStock

  const suggestions = useMemo(() => {
    const skuSuggestions = enrichedItems.map(r => ({
      id:       `sku-${r.productId}`,
      label:    r.sku,
      sublabel: r.productName,
      category: 'SKU',
      value:    r.sku,
    }))
    const nameSuggestions = enrichedItems
      .filter((r, i, arr) => arr.findIndex(x => x.productName === r.productName) === i && r.productName !== '—')
      .map(r => ({
        id:       `name-${r.productId}`,
        label:    r.productName,
        sublabel: r.sku,
        category: 'Product',
        value:    r.productName,
      }))
    const brandSuggestions = brands.map(b => ({
      id:       `brand-${b}`,
      label:    b,
      sublabel: 'Department',
      category: 'Brand',
      value:    b,
    }))
    return [...skuSuggestions, ...nameSuggestions, ...brandSuggestions]
  }, [enrichedItems, brands])

  const inventoryStats = useMemo(() => {
    const storeLevel = (items ?? []).filter(i => i.zoneId == null)
    const totalExpected = storeLevel.reduce((s, i) => s + i.quantityExpected, 0)
    const totalScanned  = storeLevel.reduce((s, i) => s + i.quantityOnHand,   0)
    const accuracyPct   = totalExpected > 0 ? (totalScanned / totalExpected) * 100 : null
    return { totalSkus: storeLevel.length, totalExpected, totalScanned, accuracyPct }
  }, [items])

  const zoneLabel = useMemo(() => {
    if (!filterZone || filterZone === 'store') return null
    return (zones ?? []).find(({ id }) => id === filterZone)?.name ?? null
  }, [filterZone, zones])

  const columns = useMemo<ColumnDef<EnrichedRow, unknown>[]>(() => [
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
    {
      accessorKey: 'brand',
      header: 'Department',
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
            <select value={storeId} onChange={e => { setSelectedStoreId(e.target.value); setFilterZone('') }} className={selectCls}>
              {allStores.content.map(s => (
                <option key={s.id} value={s.id}>{s.name} ({s.storeCode})</option>
              ))}
            </select>
          </div>
        )}

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard
            title="Total SKUs"
            value={inventoryStats.totalSkus.toLocaleString()}
            icon={Package}
            color="blue"
          />
          <StatCard
            title="RFID Scanned"
            value={inventoryStats.totalScanned.toLocaleString()}
            sub={`of ${inventoryStats.totalExpected.toLocaleString()} ERP expected`}
            icon={ScanLine}
            color="green"
          />
          <StatCard
            title="Missing EPC"
            value={(summary?.missing ?? 0).toLocaleString()}
            sub="not detected by RFID"
            icon={AlertTriangle}
            color="red"
          />
          <StatCard
            title="Accuracy"
            value={inventoryStats.accuracyPct != null ? `${inventoryStats.accuracyPct.toFixed(1)}%` : '—'}
            sub={inventoryStats.accuracyPct != null && inventoryStats.accuracyPct < 95 ? 'Below 95% target' : 'On target'}
            icon={Target}
            color={
              inventoryStats.accuracyPct == null  ? 'blue'   :
              inventoryStats.accuracyPct >= 95    ? 'green'  :
              inventoryStats.accuracyPct >= 80    ? 'yellow' : 'red'
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

            {/* Filters */}
            <div className="flex flex-wrap items-center gap-2">
              {/* Zone (Sales Floor / Backroom) */}
              {zones && zones.length > 0 && (
                <select value={filterZone} onChange={e => setFilterZone(e.target.value)} className={selectCls}>
                  <option value="">All Zones</option>
                  {zones.map(({ id, name }) => (
                    <option key={id} value={id}>{name}</option>
                  ))}
                  <option value="store">Store Level</option>
                </select>
              )}

              {/* Department */}
              <select value={filterBrand} onChange={e => setFilterBrand(e.target.value)} className={selectCls}>
                <option value="">All Departments</option>
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
                  onClick={() => { setFilterZone(''); setFilterBrand(''); setFilterAccuracy(''); setFilterStock('') }}
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
            suggestions={suggestions}
          />
        </div>

      </div>
    </>
  )
}
