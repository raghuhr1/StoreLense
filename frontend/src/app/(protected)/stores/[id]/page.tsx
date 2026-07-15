'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { use, useMemo, useState }  from 'react'
import { Plus, Trash2, Zap, MapPin, Layers } from 'lucide-react'
import Header             from '@/components/layout/Header'
import { storesApi }      from '@/lib/api/stores'
import { productsApi }    from '@/lib/api/products'
import { replenishmentRulesApi, storeLocationParLevelsApi } from '@/lib/api/inventory'
import { statusBadge }    from '@/components/ui/Badge'
import type { ReplenishmentRule } from '@/types'

export default function StoreDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const qc     = useQueryClient()
  const [locOpen,     setLocOpen]     = useState(false)
  const [locCode,     setLocCode]     = useState('SALES_FLOOR')
  const [locSection,  setLocSection]  = useState('')
  const [locDisplay,  setLocDisplay]  = useState('')
  const [parOpen,       setParOpen]       = useState(false)
  const [parProductId,  setParProductId]  = useState('')
  const [parProductSearch, setParProductSearch] = useState('')
  const [parQty,        setParQty]        = useState(1)
  const [parMinQty,     setParMinQty]     = useState(0)

  const { data: store }       = useQuery({ queryKey: ['store', id],          queryFn: () => storesApi.get(id) })
  const { data: readers }     = useQuery({ queryKey: ['readers', id],        queryFn: () => storesApi.readers(id) })
  const { data: storeLocations } = useQuery({ queryKey: ['store-locations', id], queryFn: () => storesApi.locations(id) })
  const { data: rules }   = useQuery<ReplenishmentRule[]>({
    queryKey: ['replenishment-rules', id],
    queryFn:  () => replenishmentRulesApi.list(id),
  })
  const { data: floorParLevels } = useQuery({
    queryKey: ['store-location-par-levels', id],
    queryFn:  () => storeLocationParLevelsApi.list(id, 'SALES_FLOOR'),
  })
  const { data: productsPage } = useQuery({
    queryKey: ['products-all'],
    queryFn:  () => productsApi.list({ size: 200 }),
  })

  const products = productsPage?.content ?? []
  const productById = useMemo(() => new Map(products.map(p => [p.id, p])), [products])
  const filteredProducts = useMemo(() => {
    const q = parProductSearch.toLowerCase()
    return q ? products.filter(p => p.name.toLowerCase().includes(q) || p.sku.toLowerCase().includes(q)) : products
  }, [products, parProductSearch])

  // Store location create / deactivate
  const locCreateMut = useMutation({
    mutationFn: () => storesApi.createLocation(id, {
      locationCode: locCode,
      sectionCode:  locCode === 'SALES_FLOOR' && locSection ? locSection : null,
      displayName:  locDisplay || (locCode === 'BACKROOM' ? 'Backroom' : locSection ? `Sales Floor – ${locSection}` : 'Sales Floor'),
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['store-locations', id] })
      setLocOpen(false); setLocCode('SALES_FLOOR'); setLocSection(''); setLocDisplay('')
    },
  })
  const locDeactivateMut = useMutation({
    mutationFn: (locId: string) => storesApi.deactivateLocation(id, locId),
    onSuccess:  () => qc.invalidateQueries({ queryKey: ['store-locations', id] }),
  })

  // Sales Floor par level create / delete
  const parCreateMut = useMutation({
    mutationFn: () => storeLocationParLevelsApi.upsert({
      storeId: id, locationCode: 'SALES_FLOOR', productId: parProductId, parQty, minQty: parMinQty,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['store-location-par-levels', id] })
      setParOpen(false); setParProductId(''); setParProductSearch(''); setParQty(1); setParMinQty(0)
    },
  })
  const parDeleteMut = useMutation({
    mutationFn: (parId: string) => storeLocationParLevelsApi.delete(parId, id),
    onSuccess:  () => qc.invalidateQueries({ queryKey: ['store-location-par-levels', id] }),
  })

  // Replenishment rule mutations
  const ruleUpsertMut = useMutation({
    mutationFn: (body: { triggerStatus: 'low' | 'critical'; priority: number }) =>
      replenishmentRulesApi.upsert({ storeId: id, ...body }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['replenishment-rules', id] }),
  })
  const ruleDelMut = useMutation({
    mutationFn: (ruleId: string) => replenishmentRulesApi.delete(ruleId, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['replenishment-rules', id] }),
  })

  if (!store) return null

  const inputCls = 'input-field'

  return (
    <>
      <Header title={`${store.storeCode} — ${store.name}`} />
      <div className="p-6 space-y-6">

        {/* Store details */}
        <div className="card">
          <h2 className="text-sm font-semibold text-gray-700 mb-4">Store Details</h2>
          <dl className="grid grid-cols-2 sm:grid-cols-3 gap-4">
            {([
              ['Code',     store.storeCode],
              ['Name',     store.name],
              ['City',     store.city ?? '—'],
              ['State',    store.stateProvince ?? '—'],
              ['Country',  store.countryCode],
              ['Timezone', store.timezone],
              ['ERP Code', store.erpStoreCode ?? '—'],
              ['Status',   store.active ? 'Active' : 'Inactive'],
            ] as [string, string][]).map(([label, value]) => (
              <div key={label} className="bg-gray-50 rounded-lg p-3">
                <dt className="text-xs text-gray-500">{label}</dt>
                <dd className="text-sm font-medium text-gray-900 mt-0.5">{value}</dd>
              </div>
            ))}
          </dl>
        </div>

        {/* RFID Readers */}
        <div className="card">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-semibold text-gray-700">RFID Readers ({readers?.length ?? 0})</h2>
            <span className="text-xs text-gray-400">Auto-registered by devices</span>
          </div>
          <ul className="divide-y divide-gray-50">
            {readers?.length === 0 && (
              <li className="py-4 text-center text-sm text-gray-400">No readers registered yet</li>
            )}
            {readers?.map(r => (
              <li key={r.id} className="py-2.5 flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-gray-900">{r.readerCode}</p>
                  <p className="text-xs text-gray-400">{r.readerType} · {r.ipAddress ?? 'no IP'}</p>
                </div>
                <div className="flex items-center gap-2">
                  {statusBadge(r.active ? 'active' : 'inactive')}
                  {r.lastHeartbeatAt && <span className="w-2 h-2 rounded-full bg-green-400" title="Online" />}
                </div>
              </li>
            ))}
          </ul>
        </div>

        {/* Store Locations (Cycle Count taxonomy) */}
        <div className="card">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-sm font-semibold text-gray-700 flex items-center gap-1.5">
                <MapPin size={14} className="text-brand-500" />
                Cycle Count Locations ({storeLocations?.length ?? 0})
              </h2>
              <p className="text-xs text-gray-400 mt-0.5">
                Locations available for cycle count sessions (SALES_FLOOR / BACKROOM + optional sections)
              </p>
            </div>
            <button onClick={() => setLocOpen(true)} className="btn-primary text-xs py-1.5 px-3">
              <Plus size={14} /> Add Location
            </button>
          </div>

          {locOpen && (
            <div className="mb-5 p-4 bg-gray-50 border border-gray-100 rounded-xl space-y-3">
              <p className="text-xs font-semibold text-gray-600 uppercase tracking-wide">New Location</p>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Location *</label>
                  <select
                    value={locCode}
                    onChange={e => { setLocCode(e.target.value); setLocSection('') }}
                    className={inputCls}
                  >
                    <option value="SALES_FLOOR">Sales Floor</option>
                    <option value="BACKROOM">Backroom</option>
                  </select>
                </div>
                {locCode === 'SALES_FLOOR' && (
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">Section (optional)</label>
                    <select
                      value={locSection}
                      onChange={e => setLocSection(e.target.value)}
                      className={inputCls}
                    >
                      <option value="">— None (whole floor) —</option>
                      <option value="MENS">Mens</option>
                      <option value="WOMENS">Womens</option>
                      <option value="KIDS">Kids</option>
                      <option value="FOOTWEAR">Footwear</option>
                      <option value="ACCESSORIES">Accessories</option>
                    </select>
                  </div>
                )}
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Display Name (optional)</label>
                  <input
                    value={locDisplay}
                    onChange={e => setLocDisplay(e.target.value)}
                    placeholder="Auto-generated if blank"
                    className={inputCls}
                  />
                </div>
              </div>
              {locCreateMut.isError && (
                <p className="text-xs text-red-500 bg-red-50 border border-red-200 rounded px-3 py-2">
                  Failed to create location. Check for duplicates.
                </p>
              )}
              <div className="flex gap-2 justify-end">
                <button type="button" onClick={() => { setLocOpen(false); setLocCode('SALES_FLOOR'); setLocSection(''); setLocDisplay('') }}
                  className="btn-secondary text-xs">Cancel</button>
                <button
                  type="button"
                  onClick={() => locCreateMut.mutate()}
                  disabled={locCreateMut.isPending}
                  className="btn-primary text-xs"
                >
                  {locCreateMut.isPending ? 'Adding…' : 'Add Location'}
                </button>
              </div>
            </div>
          )}

          {(!storeLocations || storeLocations.length === 0) ? (
            <p className="py-6 text-center text-sm text-gray-400">
              No locations configured. Add locations to enable cycle count sessions.
            </p>
          ) : (
            <ul className="divide-y divide-gray-50">
              {storeLocations.map(loc => (
                <li key={loc.id} className="py-2.5 flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-gray-900">{loc.displayName}</p>
                    <p className="text-xs text-gray-400">
                      {loc.locationCode}{loc.sectionCode ? ` / ${loc.sectionCode}` : ''} · sort {loc.sortOrder}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    {statusBadge(loc.isActive ? 'active' : 'inactive')}
                    {loc.isActive && (
                      <button
                        onClick={() => locDeactivateMut.mutate(loc.id)}
                        disabled={locDeactivateMut.isPending}
                        className="p-1.5 rounded-lg text-gray-400 hover:text-red-500 hover:bg-red-50 transition-colors"
                        title="Deactivate location"
                      >
                        <Trash2 size={13} />
                      </button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* Sales Floor Par Levels */}
        <div className="card">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-sm font-semibold text-gray-700 flex items-center gap-1.5">
                <Layers size={14} className="text-brand-500" />
                Sales Floor Par Levels ({floorParLevels?.length ?? 0})
              </h2>
              <p className="text-xs text-gray-400 mt-0.5">
                Minimum Sales Floor quantities per product — compared against the Sales Floor count
                from each completed SOH session to auto-trigger replenishment from Backroom.
              </p>
            </div>
            <button onClick={() => setParOpen(v => !v)} className="btn-primary text-xs py-1.5 px-3">
              <Plus size={14} /> Add Par Level
            </button>
          </div>

          {parOpen && (
            <div className="mb-5 p-4 bg-gray-50 border border-gray-100 rounded-xl space-y-3">
              <p className="text-xs font-semibold text-gray-600 uppercase tracking-wide">New / Update Par Level</p>
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Product *</label>
                <input
                  type="text"
                  placeholder="Search product…"
                  value={parProductSearch}
                  onChange={e => setParProductSearch(e.target.value)}
                  className={inputCls + ' mb-1'}
                />
                <select
                  value={parProductId}
                  onChange={e => setParProductId(e.target.value)}
                  className={inputCls}
                  size={4}
                >
                  <option value="">— select —</option>
                  {filteredProducts.slice(0, 50).map(p => (
                    <option key={p.id} value={p.id}>{p.name} ({p.sku})</option>
                  ))}
                </select>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Par Qty *</label>
                  <input
                    type="number" min={0} value={parQty}
                    onChange={e => setParQty(Number(e.target.value))}
                    className={inputCls}
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Min Qty (urgent)</label>
                  <input
                    type="number" min={0} value={parMinQty}
                    onChange={e => setParMinQty(Number(e.target.value))}
                    className={inputCls}
                  />
                </div>
              </div>
              {parCreateMut.isError && (
                <p className="text-xs text-red-500 bg-red-50 border border-red-200 rounded px-3 py-2">
                  Failed to save par level. Try again.
                </p>
              )}
              <div className="flex gap-2 justify-end">
                <button type="button" onClick={() => { setParOpen(false); setParProductId(''); setParProductSearch('') }}
                  className="btn-secondary text-xs">Cancel</button>
                <button
                  type="button"
                  onClick={() => parCreateMut.mutate()}
                  disabled={parCreateMut.isPending || !parProductId}
                  className="btn-primary text-xs"
                >
                  {parCreateMut.isPending ? 'Saving…' : 'Save Par Level'}
                </button>
              </div>
            </div>
          )}

          {(!floorParLevels || floorParLevels.length === 0) ? (
            <p className="py-6 text-center text-sm text-gray-400">
              No par levels configured. Add one to enable SOH-based replenishment.
            </p>
          ) : (
            <ul className="divide-y divide-gray-50">
              {floorParLevels.map(pl => {
                const product = productById.get(pl.productId)
                return (
                  <li key={pl.id} className="py-2.5 flex items-center justify-between">
                    <div>
                      <p className="text-sm font-medium text-gray-900">{product?.name ?? pl.productId}</p>
                      <p className="font-mono text-xs text-gray-400">{product?.sku ?? '—'}</p>
                    </div>
                    <div className="flex items-center gap-3">
                      <span className="text-xs text-gray-500">par <b className="text-gray-900">{pl.parQty}</b> · min <b className="text-amber-700">{pl.minQty}</b></span>
                      <button
                        onClick={() => parDeleteMut.mutate(pl.id)}
                        disabled={parDeleteMut.isPending}
                        className="p-1.5 rounded-lg text-gray-400 hover:text-red-500 hover:bg-red-50 transition-colors"
                        title="Remove par level"
                      >
                        <Trash2 size={13} />
                      </button>
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
        </div>

        {/* Replenishment Rules */}
        <div className="card">
          <div className="flex items-start justify-between mb-4">
            <div>
              <h2 className="text-sm font-semibold text-gray-700 flex items-center gap-1.5">
                <Zap size={14} className="text-amber-500" />
                Replenishment Auto-Trigger Rules ({rules?.length ?? 0} / 2)
              </h2>
              <p className="text-xs text-gray-400 mt-0.5">
                Applies to both live zone scans and completed SOH sessions.
                &lsquo;low&rsquo; fires for both low and critical; &lsquo;critical&rsquo; fires only for critical.
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-4">
            {(['low', 'critical'] as const).map(status => {
              const existing = rules?.find(r => r.triggerStatus === status)
              return (
                <div key={status} className={`p-4 rounded-xl border ${
                  existing ? 'border-green-200 bg-green-50' : 'border-gray-100 bg-gray-50'
                }`}>
                  <div className="flex items-center justify-between mb-3">
                    <div>
                      <p className="text-sm font-semibold text-gray-900 capitalize">{status} Stock Rule</p>
                      <p className="text-xs text-gray-400 mt-0.5">
                        {status === 'low'
                          ? 'Triggers for low + critical zones'
                          : 'Triggers only for critical zones'}
                      </p>
                    </div>
                    {existing && (
                      <button
                        onClick={() => ruleDelMut.mutate(existing.id)}
                        disabled={ruleDelMut.isPending}
                        className="p-1.5 rounded-lg text-gray-400 hover:text-red-500 hover:bg-red-50 transition-colors"
                        title="Remove rule"
                      >
                        <Trash2 size={13} />
                      </button>
                    )}
                  </div>
                  {existing ? (
                    <div className="flex items-center gap-3">
                      <span className="text-xs text-gray-500">Priority</span>
                      <input
                        type="number" min={1} max={10}
                        defaultValue={existing.priority}
                        onBlur={e => ruleUpsertMut.mutate({
                          triggerStatus: status,
                          priority: Number(e.target.value),
                        })}
                        className="w-16 text-sm border border-gray-200 rounded-lg px-2 py-1 text-center font-mono"
                      />
                      <span className="text-xs text-gray-400">(1 = urgent, 10 = low)</span>
                    </div>
                  ) : (
                    <button
                      onClick={() => ruleUpsertMut.mutate({
                        triggerStatus: status,
                        priority: status === 'critical' ? 3 : 6,
                      })}
                      disabled={ruleUpsertMut.isPending}
                      className="btn-primary text-xs py-1.5 w-full"
                    >
                      <Plus size={12} /> Enable Rule
                    </button>
                  )}
                </div>
              )
            })}
          </div>

          {(rules?.length ?? 0) > 0 && (
            <p className="text-xs text-gray-400">
              Once rules are active, go to{' '}
              <a href="/replenishment/auto" className="text-brand-600 hover:underline font-medium">
                Replenishment → Auto-Trigger
              </a>{' '}
              to review and create tasks.
            </p>
          )}
        </div>

      </div>
    </>
  )
}
