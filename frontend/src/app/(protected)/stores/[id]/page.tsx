'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { use, useState }  from 'react'
import { useForm }        from 'react-hook-form'
import { zodResolver }    from '@hookform/resolvers/zod'
import { z }              from 'zod'
import { Plus }           from 'lucide-react'
import Header             from '@/components/layout/Header'
import { storesApi }      from '@/lib/api/stores'
import { statusBadge }    from '@/components/ui/Badge'

// ── Zone create form schema ────────────────────────────────────────────────
const zoneSchema = z.object({
  zoneCode:     z.string().min(1, 'Required').max(50),
  name:         z.string().min(1, 'Required'),
  zoneType:     z.enum(['floor','backroom','fitting_room','stockroom','display','entrance']),
  displayOrder: z.coerce.number().int().min(0).default(0),
})
type ZoneForm = z.infer<typeof zoneSchema>

export default function StoreDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const qc     = useQueryClient()
  const [zoneOpen, setZoneOpen] = useState(false)

  const { data: store }   = useQuery({ queryKey: ['store', id],   queryFn: () => storesApi.get(id) })
  const { data: zones }   = useQuery({ queryKey: ['zones', id],   queryFn: () => storesApi.zones(id) })
  const { data: readers } = useQuery({ queryKey: ['readers', id], queryFn: () => storesApi.readers(id) })

  const zoneMut = useMutation({
    mutationFn: (v: ZoneForm) => storesApi.createZone(id, v),
    onSuccess:  () => { qc.invalidateQueries({ queryKey: ['zones', id] }); setZoneOpen(false); zoneReset() },
  })

  const { register: zoneReg, handleSubmit: zoneSubmit, reset: zoneReset, formState: { errors: zoneErr } } =
    useForm<ZoneForm>({ resolver: zodResolver(zoneSchema), defaultValues: { zoneType: 'floor', displayOrder: 0 } })

  if (!store) return null

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
                  <input {...zoneReg('zoneCode')} className="input-field" placeholder="FLOOR-A" />
                  {zoneErr.zoneCode && <p className="text-xs text-red-500 mt-0.5">{zoneErr.zoneCode.message}</p>}
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Display Order</label>
                  <input {...zoneReg('displayOrder')} type="number" className="input-field" min={0} />
                </div>
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Zone Name *</label>
                <input {...zoneReg('name')} className="input-field" placeholder="e.g. Ground Floor" />
                {zoneErr.name && <p className="text-xs text-red-500 mt-0.5">{zoneErr.name.message}</p>}
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Zone Type</label>
                <select {...zoneReg('zoneType')} className="input-field">
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
