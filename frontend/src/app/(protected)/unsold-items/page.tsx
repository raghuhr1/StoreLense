'use client'

import { useQuery }              from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { ImageOff, Search }      from 'lucide-react'
import Header                    from '@/components/layout/Header'
import { inventoryApi }          from '@/lib/api/inventory'
import { productsApi }           from '@/lib/api/products'
import { storesApi }             from '@/lib/api/stores'
import { useAuth }               from '@/lib/auth/AuthContext'
import type { InventoryState, Product } from '@/types'

// Product photo is only shown for this long after the item's last RFID
// sighting — after that it reverts to a placeholder, but the item itself
// stays listed. Ties to the real sighting timestamp (not a per-browser
// timer) so every viewer sees the same thing regardless of when they opened
// the page.
const IMAGE_VISIBLE_MS = 10 * 60 * 1000

function isToday(iso: string | null): boolean {
  if (!iso) return false
  const d = new Date(iso)
  const now = new Date()
  return d.getFullYear() === now.getFullYear()
    && d.getMonth() === now.getMonth()
    && d.getDate() === now.getDate()
}

interface UnsoldItem {
  productId:     string
  sku:           string
  name:          string
  brand:         string | null
  primaryEan:    string | null
  imageUrl:      string | null
  qtyOnHand:     number
  qtyExpected:   number
  lastCountedAt: string | null
}

export default function UnsoldItemsPage() {
  const { user, isAdmin } = useAuth()
  const [selectedStoreId, setSelectedStoreId] = useState('')
  const [search, setSearch]         = useState('')
  const [filterBrand, setFilterBrand] = useState('')
  const [onlyWithImage, setOnlyWithImage] = useState(false)

  // Ticks every 15s so images silently revert to a placeholder the moment
  // their 10-minute window elapses, without needing a page reload.
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 15_000)
    return () => clearInterval(id)
  }, [])

  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })

  const storeId = isAdmin
    ? (selectedStoreId || allStores?.content[0]?.id || '')
    : (user?.storeId ?? '')

  const { data: invState, isLoading: invLoading } = useQuery({
    queryKey: ['inventory-state', storeId],
    queryFn:  () => inventoryApi.getState(storeId),
    enabled:  !!storeId,
  })

  const { data: allProducts, isLoading: productsLoading } = useQuery({
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
    const m: Record<string, Product> = {}
    for (const p of allProducts ?? []) m[p.id] = p
    return m
  }, [allProducts])

  // "Unsold" = currently on hand per RFID, last sighted today (store-level
  // rows only, not per-zone breakdowns) — stale/older inventory drops off
  // automatically at midnight rather than lingering indefinitely.
  const items = useMemo((): UnsoldItem[] => {
    if (!invState) return []
    return (invState as InventoryState[])
      .filter(inv => inv.zoneId == null && inv.quantityOnHand > 0 && isToday(inv.lastCountedAt))
      .map(inv => {
        const p = productMap[inv.productId]
        return {
          productId:     inv.productId,
          sku:           p?.sku ?? inv.productId.slice(-8),
          name:          p?.name ?? '—',
          brand:         p?.brand ?? null,
          primaryEan:    p?.primaryEan ?? null,
          imageUrl:      p?.imageUrl ?? null,
          qtyOnHand:     inv.quantityOnHand,
          qtyExpected:   inv.quantityExpected,
          lastCountedAt: inv.lastCountedAt,
        }
      })
      .sort((a, b) => b.qtyOnHand - a.qtyOnHand)
  }, [invState, productMap])

  // Whether an item's photo is still within its 10-minute post-sighting window.
  const imageVisible = (item: UnsoldItem) =>
    !!item.imageUrl && !!item.lastCountedAt
      && (now - new Date(item.lastCountedAt).getTime()) < IMAGE_VISIBLE_MS

  const brands = useMemo(() => {
    const s = new Set<string>()
    for (const i of items) if (i.brand) s.add(i.brand)
    return Array.from(s).sort()
  }, [items])

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    return items.filter(i => {
      if (filterBrand && i.brand !== filterBrand) return false
      if (onlyWithImage && !imageVisible(i)) return false
      if (q && !i.name.toLowerCase().includes(q) && !i.sku.toLowerCase().includes(q)) return false
      return true
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [items, filterBrand, onlyWithImage, search, now])

  const totalUnits = filtered.reduce((s, i) => s + i.qtyOnHand, 0)
  const withImages = filtered.filter(imageVisible).length
  const isLoading  = invLoading || productsLoading

  const selectCls = 'text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500'

  return (
    <>
      <Header title="Unsold Items" />
      <div className="p-6 space-y-5">

        {/* Explanation banner */}
        <div className="bg-blue-50 border border-blue-200 rounded-xl px-4 py-3 text-sm text-blue-800">
          Showing items last RFID-sighted <strong>today</strong> — yesterday's items drop off automatically at midnight.
          Each product's photo is visible for <strong>10 minutes</strong> after its sighting, then reverts to a placeholder;
          the item itself stays listed either way.
        </div>

        {/* Filters */}
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

          <div className="relative">
            <Search size={15} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Search name or SKU…"
              className="text-sm border border-gray-200 rounded-lg pl-8 pr-3 py-1.5 w-56 focus:outline-none focus:ring-2 focus:ring-brand-500"
            />
          </div>

          <select value={filterBrand} onChange={e => setFilterBrand(e.target.value)} className={selectCls}>
            <option value="">All Brands</option>
            {brands.map(b => <option key={b} value={b}>{b}</option>)}
          </select>

          <label className="flex items-center gap-1.5 text-sm text-gray-600 cursor-pointer select-none">
            <input type="checkbox" checked={onlyWithImage} onChange={e => setOnlyWithImage(e.target.checked)} className="w-3.5 h-3.5 accent-blue-600" />
            With image only
          </label>

          {(filterBrand || onlyWithImage || search) && (
            <button
              onClick={() => { setFilterBrand(''); setOnlyWithImage(false); setSearch('') }}
              className="text-xs text-gray-500 hover:text-gray-700 underline"
            >
              Clear
            </button>
          )}

          <span className="ml-auto text-xs text-gray-400">
            {filtered.length.toLocaleString()} SKUs · {totalUnits.toLocaleString()} units · {withImages} with image
          </span>
        </div>

        {/* Gallery */}
        {isLoading ? (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
            {Array.from({ length: 12 }).map((_, i) => (
              <div key={i} className="card animate-pulse">
                <div className="aspect-square bg-gray-100 rounded-lg mb-3" />
                <div className="h-3 bg-gray-100 rounded w-3/4 mb-2" />
                <div className="h-3 bg-gray-100 rounded w-1/2" />
              </div>
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <div className="card text-center py-16 text-gray-400 text-sm">
            No items sighted today match the current filters.
          </div>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
            {filtered.map(item => {
              const showImage = imageVisible(item)
              return (
                <div key={item.productId} className="card p-3 hover:shadow-md transition-shadow">
                  <div className="aspect-square bg-gray-50 rounded-lg mb-3 overflow-hidden flex items-center justify-center border border-gray-100">
                    {showImage ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={item.imageUrl!} alt={item.name} className="w-full h-full object-cover" />
                    ) : (
                      <ImageOff size={28} className="text-gray-300" />
                    )}
                  </div>
                  <p className="text-sm font-medium text-gray-900 leading-snug line-clamp-2" title={item.name}>
                    {item.name}
                  </p>
                  <div className="flex items-center justify-between mt-1.5">
                    <span className="font-mono text-[11px] text-gray-500">{item.sku}</span>
                    <span className="text-xs font-semibold text-teal-700 bg-teal-50 px-2 py-0.5 rounded-full">
                      {item.qtyOnHand} on hand
                    </span>
                  </div>
                  {item.brand && <p className="text-[11px] text-gray-400 mt-1">{item.brand}</p>}
                  {item.primaryEan && <p className="font-mono text-[10px] text-gray-300 mt-0.5">{item.primaryEan}</p>}
                </div>
              )
            })}
          </div>
        )}

      </div>
    </>
  )
}
