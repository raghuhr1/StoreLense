'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { use, useMemo, useState }  from 'react'
import { useForm }        from 'react-hook-form'
import { zodResolver }    from '@hookform/resolvers/zod'
import { z }              from 'zod'
import { Plus, Trash2, Zap, MapPin } from 'lucide-react'
import Header             from '@/components/layout/Header'
import { storesApi }      from '@/lib/api/stores'
import { productsApi }    from '@/lib/api/products'
import { parLevelsApi, replenishmentRulesApi } from '@/lib/api/inventory'
import { statusBadge }    from '@/components/ui/Badge'
import type { ReplenishmentRule } from '@/types'

// ── Zone create form schema ────────────────────────────────────────────────
const zoneSchema = z.object({
  zoneCode:     z.string().min(1, 'Required').max(50),
  name:         z.string().min(1, 'Required'),
  zoneType:     z.enum(['floor','backroom','fitting_room','stockroom','display','entrance']),
  displayOrder: z.coerce.number().int().min(0).default(0),
})
type ZoneForm = z.infer<typeof zoneSchema>

// ── Par level form schema ──────────────────────────────────────────────────
const parSchema = z.object({
  zoneId:    z.string().uuid('Required'),
  productId: z.string().uuid('Required'),
  parQty:    z.coerce.number().int().min(0, 'Min 0'),
  minQty:    z.coerce.number().int().min(0, 'Min 0'),
})
type ParForm = z.infer<typeof parSchema>

export default function StoreDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const qc     = useQueryClient()
  const [zoneOpen,    setZoneOpen]    = useState(false)
  const [parOpen,     setParOpen]     = useState(false)
  const [locOpen,     setLocOpen]     = useState(false)
  const [locCode,     setLocCode]     = useState('SALES_FLOOR')
  const [locSection,  setLocSection]  = useState('')
  const [locDisplay,  setLocDisplay]  = useState('')
  const [productSearch, setProductSearch] = useState('')

  const { data: store }       = useQuery({ queryKey: ['store', id],          queryFn: () => storesApi.get(id) })
  const { data: zones }       = useQuery({ queryKey: ['zones', id],          queryFn: () => storesApi.zones(id) })
  const { data: readers }     = useQuery({ queryKey: ['readers', id],        queryFn: () => storesApi.readers(id) })
  const { data: storeLocations } = useQuery({ queryKey: ['store-locations', id], queryFn: () => storesApi.locations(id) })
  const { data: parLvls }     = useQuery({ queryKey: ['par-levels', id],     queryFn: () => parLevelsApi.list(id) })
  const { data: rules }   = useQuery<ReplenishmentRule[]>({
    queryKey: ['replenishment-rules', id],
    queryFn:  () => replenishmentRulesApi.list(id),
  })
  const { data: productsPage } = useQuery({
    queryKey: ['products-store', id],
    queryFn:  () => productsApi.list({ storeId: id, size: 200 }),
  })

  const products = productsPage?.content ?? []

  const filteredProducts = useMemo(() => {
    const q = productSearch.toLowerCase()
    return q ? products.filter(p => p.name.toLowerCase().includes(q) || p.sku.toLowerCase().includes(q)) : products
  }, [products, productSearch])

  const zoneById    = useMemo(() => new Map((zones    ?? []).map(z => [z.id, z])),    [zones])
  const productById = useMemo(() => new Map(products.map(p => [p.id, p])),             [products])

  // Zone create
  const zoneMut = useMutation({
    mutationFn: (v: ZoneForm) => storesApi.createZone(id, v),
    onSuccess:  () => { qc.invalidateQueries({ queryKey: ['zones', id] }); setZoneOpen(false); zoneReset() },
  })

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

  // Par level upsert
  const parMut = useMutation({
    mutationFn: (v: ParForm) =>
      parLevelsApi.upsert({ storeId: id, zoneId: v.zoneId, productId: v.productId, parQty: v.parQty, minQty: v.minQty }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['par-levels', id] }); setParOpen(false); parReset() },
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

  // Par level delete
  const parDelMut = useMutation({
    mutationFn: (parId: string) => parLevelsApi.delete(parId, id),
    onSuccess:  () => qc.invalidateQueries({ queryKey: ['par-levels', id] }),
  })

  const { register: zoneReg, handleSubmit: zoneSubmit, reset: zoneReset,
    formState: { errors: zoneErr } } =
    useForm<ZoneForm>({ resolver: zodResolver(zoneSchema), defaultValues: { zoneType: 'floor', displayOrder: 0 } })

  const { register: parReg, handleSubmit: parSubmit, reset: parReset,
    formState: { errors: parErr } } =
    useForm<ParForm>({ resolver: zodResolver(parSchema), defaultValues: { parQty: 1, minQty: 0 } })

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

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">

          {/* Zones */}
          <div className="card">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-sm font-semibold text-gray-700">Zones ({zones?.length ?? 0})</h2>
              <button onClick={() => setZoneOpen(true)} className="btn-primary text-xs py-1.5 px-3">
                <Plus size={14} /> Add Zone
              </button>
            </div>
            <ul className="divide-y divide-gray-50">
              {zones?.length === 0 && (
                <li className="py-4 text-center text-sm text-gray-400">No zones yet</li>
              )}
              {zones?.map(z => (
                <li key={z.id} className="py-2.5 flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-gray-900">{z.name}</p>
                    <p className="text-xs text-gray-400">{z.zoneCode} · {z.zoneType}</p>
                  </div>
                  {statusBadge(z.active ? 'active' : 'inactive')}
                </li>
              ))}
            </ul>
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

        {/* Zone Par Levels */}
        <div className="card">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-sm font-semibold text-gray-700">Zone Par Levels ({parLvls?.length ?? 0})</h2>
              <p className="text-xs text-gray-400 mt-0.5">
                Minimum floor quantities per product per zone — used to trigger replenishment
              </p>
            </div>
            <button onClick={() => setParOpen(v => !v)} className="btn-primary text-xs py-1.5 px-3">
              <Plus size={14} /> Add Par Level
            </button>
          </div>

          {/* Inline add form */}
          {parOpen && (
            <form onSubmit={parSubmit(v => parMut.mutate(v))}
              className="mb-5 p-4 bg-gray-50 border border-gray-100 rounded-xl space-y-3">
              <p className="text-xs font-semibold text-gray-600 uppercase tracking-wide">New / Update Par Level</p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Zone *</label>
                  <select {...parReg('zoneId')} className={inputCls}>
                    <option value="">— select zone —</option>
                    {(zones ?? []).map(z => (
                      <option key={z.id} value={z.id}>{z.name}</option>
                    ))}
                  </select>
                  {parErr.zoneId && <p className="text-xs text-red-500 mt-0.5">{parErr.zoneId.message}</p>}
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Product *</label>
                  <input
                    type="text"
                    placeholder="Search product…"
                    value={productSearch}
                    onChange={e => setProductSearch(e.target.value)}
                    className={inputCls + ' mb-1'}
                  />
                  <select {...parReg('productId')} className={inputCls} size={4}>
                    <option value="">— select —</option>
                    {filteredProducts.slice(0, 50).map(p => (
                      <option key={p.id} value={p.id}>{p.name} ({p.sku})</option>
                    ))}
                  </select>
                  {parErr.productId && <p className="text-xs text-red-500 mt-0.5">{parErr.productId.message}</p>}
                </div>
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Par Qty *</label>
                  <input {...parReg('parQty')} type="number" min={0} className={inputCls} />
                  {parErr.parQty && <p className="text-xs text-red-500 mt-0.5">{parErr.parQty.message}</p>}
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Min Qty (urgent)</label>
                  <input {...parReg('minQty')} type="number" min={0} className={inputCls} />
                  {parErr.minQty && <p className="text-xs text-red-500 mt-0.5">{parErr.minQty.message}</p>}
                </div>
              </div>
              {parMut.isError && (
                <p className="text-xs text-red-500 bg-red-50 border border-red-200 rounded px-3 py-2">
                  Failed to save par level. Try again.
                </p>
              )}
              <div className="flex gap-2 justify-end">
                <button type="button" onClick={() => { setParOpen(false); parReset(); setProductSearch('') }}
                  className="btn-secondary text-xs">Cancel</button>
                <button type="submit" disabled={parMut.isPending} className="btn-primary text-xs">
                  {parMut.isPending ? 'Saving…' : 'Save Par Level'}
                </button>
              </div>
            </form>
          )}

          {/* Par levels table */}
          {(!parLvls || parLvls.length === 0) ? (
            <p className="py-6 text-center text-sm text-gray-400">
              No par levels configured. Add one to enable replenishment triggers.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-100">
                    <th className="text-left py-2 px-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">Product</th>
                    <th className="text-left py-2 px-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">Zone</th>
                    <th className="text-right py-2 px-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">Par Qty</th>
                    <th className="text-right py-2 px-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">Min Qty</th>
                    <th className="py-2 px-3" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {parLvls.map(pl => {
                    const product = productById.get(pl.productId)
                    const zone    = zoneById.get(pl.zoneId)
                    return (
                      <tr key={pl.id} className="hover:bg-gray-50 transition-colors">
                        <td className="py-2.5 px-3">
                          <p className="font-medium text-gray-900">{product?.name ?? pl.productId}</p>
                          <p className="font-mono text-xs text-gray-400">{product?.sku ?? '—'}</p>
                        </td>
                        <td className="py-2.5 px-3 text-gray-700">{zone?.name ?? pl.zoneId}</td>
                        <td className="py-2.5 px-3 text-right font-mono font-semibold text-gray-900">{pl.parQty}</td>
                        <td className="py-2.5 px-3 text-right font-mono text-amber-700">{pl.minQty}</td>
                        <td className="py-2.5 px-3 text-right">
                          <button
                            onClick={() => parDelMut.mutate(pl.id)}
                            disabled={parDelMut.isPending}
                            className="p-1.5 rounded-lg text-gray-400 hover:text-red-500 hover:bg-red-50 transition-colors"
                            title="Remove par level"
                          >
                            <Trash2 size={14} />
                          </button>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
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
                When a zone scan shows stock below par, automatically suggest refill tasks.
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

      {/* Add Zone modal */}
      {zoneOpen && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl p-6 w-[420px] shadow-xl">
            <h3 className="font-semibold text-gray-900 mb-4">Add Zone</h3>
            <form onSubmit={zoneSubmit(v => zoneMut.mutate(v))} className="space-y-3">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Zone Code *</label>
                  <input {...zoneReg('zoneCode')} className={inputCls} placeholder="FLOOR-A" />
                  {zoneErr.zoneCode && <p className="text-xs text-red-500 mt-0.5">{zoneErr.zoneCode.message}</p>}
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Display Order</label>
                  <input {...zoneReg('displayOrder')} type="number" className={inputCls} min={0} />
                </div>
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Zone Name *</label>
                <input {...zoneReg('name')} className={inputCls} placeholder="e.g. Ground Floor" />
                {zoneErr.name && <p className="text-xs text-red-500 mt-0.5">{zoneErr.name.message}</p>}
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Zone Type</label>
                <select {...zoneReg('zoneType')} className={inputCls}>
                  <option value="floor">Floor</option>
                  <option value="backroom">Backroom</option>
                  <option value="stockroom">Stockroom</option>
                  <option value="fitting_room">Fitting Room</option>
                  <option value="display">Display</option>
                  <option value="entrance">Entrance</option>
                </select>
              </div>
              {zoneMut.isError && (
                <p className="text-xs text-red-500 bg-red-50 border border-red-200 rounded px-3 py-2">
                  Failed to create zone. Try again.
                </p>
              )}
              <div className="flex gap-3 justify-end pt-2">
                <button type="button" onClick={() => { setZoneOpen(false); zoneReset() }} className="btn-secondary">
                  Cancel
                </button>
                <button type="submit" disabled={zoneMut.isPending} className="btn-primary">
                  {zoneMut.isPending ? 'Adding…' : 'Add Zone'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  )
}
