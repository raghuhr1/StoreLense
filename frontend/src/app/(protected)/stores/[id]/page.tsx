'use client'

import { useQuery }  from '@tanstack/react-query'
import { use }       from 'react'
import Header        from '@/components/layout/Header'
import { storesApi } from '@/lib/api/stores'
import { statusBadge } from '@/components/ui/Badge'

export default function StoreDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)

  const { data: store }   = useQuery({ queryKey: ['store', id], queryFn: () => storesApi.get(id) })
  const { data: zones }   = useQuery({ queryKey: ['zones', id],   queryFn: () => storesApi.zones(id) })
  const { data: readers } = useQuery({ queryKey: ['readers', id], queryFn: () => storesApi.readers(id) })

  if (!store) return null

  return (
    <>
      <Header title={`${store.storeCode} — ${store.name}`} />
      <div className="p-6 space-y-6">

        <div className="card">
          <h2 className="text-sm font-semibold text-gray-700 mb-4">Store Details</h2>
          <dl className="grid grid-cols-2 sm:grid-cols-3 gap-4">
            {[
              ['Code',        store.storeCode],
              ['Name',        store.name],
              ['City',        store.city ?? '—'],
              ['State',       store.stateProvince ?? '—'],
              ['Country',     store.countryCode],
              ['Timezone',    store.timezone],
              ['ERP Code',    store.erpStoreCode ?? '—'],
              ['Status',      store.active ? 'Active' : 'Inactive'],
            ].map(([label, value]) => (
              <div key={label} className="bg-gray-50 rounded-lg p-3">
                <dt className="text-xs text-gray-500">{label}</dt>
                <dd className="text-sm font-medium text-gray-900 mt-0.5">{value}</dd>
              </div>
            ))}
          </dl>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="card">
            <h2 className="text-sm font-semibold text-gray-700 mb-4">Zones ({zones?.length ?? 0})</h2>
            <ul className="divide-y divide-gray-50">
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

          <div className="card">
            <h2 className="text-sm font-semibold text-gray-700 mb-4">RFID Readers ({readers?.length ?? 0})</h2>
            <ul className="divide-y divide-gray-50">
              {readers?.map(r => (
                <li key={r.id} className="py-2.5 flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-gray-900">{r.readerCode}</p>
                    <p className="text-xs text-gray-400">{r.readerType} · {r.ipAddress ?? 'no IP'}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    {statusBadge(r.active ? 'active' : 'inactive')}
                    {r.lastHeartbeatAt && (
                      <span className="w-2 h-2 rounded-full bg-green-400" title="Online" />
                    )}
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </div>

      </div>
    </>
  )
}
