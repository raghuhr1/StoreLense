'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState }  from 'react'
import { type ColumnDef }     from '@tanstack/react-table'
import { useForm }            from 'react-hook-form'
import { zodResolver }        from '@hookform/resolvers/zod'
import { z }                  from 'zod'
import { Plus }               from 'lucide-react'
import Link                   from 'next/link'
import Header                 from '@/components/layout/Header'
import DataTable              from '@/components/ui/DataTable'
import { statusBadge }        from '@/components/ui/Badge'
import { storesApi }          from '@/lib/api/stores'
import { fmt }                from '@/lib/utils'
import type { Store }         from '@/types'

const TIMEZONES = [
  'UTC', 'Australia/Sydney', 'Australia/Melbourne', 'Australia/Brisbane',
  'Australia/Perth', 'Asia/Kolkata', 'Asia/Singapore', 'Asia/Tokyo',
  'Europe/London', 'Europe/Paris', 'America/New_York', 'America/Los_Angeles',
]

const schema = z.object({
  storeCode:    z.string().min(1, 'Required').max(20),
  name:         z.string().min(1, 'Required'),
  addressLine1: z.string().optional(),
  city:         z.string().optional(),
  stateProvince: z.string().optional(),
  postalCode:   z.string().optional(),
  countryCode:  z.string().length(2, 'Must be 2-letter country code'),
  timezone:     z.string().min(1, 'Required'),
  erpStoreCode: z.string().optional(),
})
type FormValues = z.infer<typeof schema>

export default function StoresPage() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)

  const { data, isLoading } = useQuery({
    queryKey: ['stores'],
    queryFn:  () => storesApi.list({ size: 500 }),
  })

  const createMut = useMutation({
    mutationFn: (v: FormValues) => storesApi.create(v),
    onSuccess:  () => { qc.invalidateQueries({ queryKey: ['stores'] }); setOpen(false); reset() },
  })

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { countryCode: 'AU', timezone: 'UTC' },
  })

  const columns = useMemo<ColumnDef<Store, unknown>[]>(() => [
    {
      accessorKey: 'storeCode',
      header: 'Code',
      cell: i => (
        <Link href={`/stores/${i.row.original.id}`} className="font-semibold text-blue-600 hover:underline">
          {i.getValue<string>()}
        </Link>
      ),
    },
    { accessorKey: 'name',          header: 'Name' },
    { accessorKey: 'city',          header: 'City' },
    { accessorKey: 'stateProvince', header: 'State' },
    { accessorKey: 'countryCode',   header: 'Country' },
    { accessorKey: 'timezone',      header: 'Timezone' },
    { accessorKey: 'active',        header: 'Status',  cell: i => statusBadge(i.getValue<boolean>() ? 'active' : 'inactive') },
    { accessorKey: 'erpStoreCode',  header: 'ERP Code', cell: i => i.getValue<string | null>() ?? '—' },
    { accessorKey: 'createdAt',     header: 'Created', cell: i => fmt(i.getValue<string>()) },
  ], [])

  return (
    <>
      <Header title="Stores" />
      <div className="p-6 space-y-4">

        <div className="flex justify-end">
          <button onClick={() => setOpen(true)} className="btn-primary">
            <Plus size={16} /> Add Store
          </button>
        </div>

        <div className="card">
          <DataTable
            data={data?.content ?? []}
            columns={columns}
            isLoading={isLoading}
            searchable
            searchPlaceholder="Search stores…"
          />
        </div>

        {open && (
          <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-white rounded-2xl p-6 w-[520px] shadow-xl max-h-[90vh] overflow-y-auto">
              <h3 className="font-semibold text-gray-900 mb-4">Add Store</h3>
              <form onSubmit={handleSubmit(v => createMut.mutate(v))} className="space-y-3">

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">Store Code *</label>
                    <input {...register('storeCode')} className="input-field" placeholder="STORE01" />
                    {errors.storeCode && <p className="text-xs text-red-500 mt-0.5">{errors.storeCode.message}</p>}
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">ERP Code</label>
                    <input {...register('erpStoreCode')} className="input-field" placeholder="Optional" />
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Store Name *</label>
                  <input {...register('name')} className="input-field" placeholder="e.g. Sydney CBD" />
                  {errors.name && <p className="text-xs text-red-500 mt-0.5">{errors.name.message}</p>}
                </div>

                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Address</label>
                  <input {...register('addressLine1')} className="input-field" placeholder="Street address" />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">City</label>
                    <input {...register('city')} className="input-field" placeholder="Sydney" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">State / Province</label>
                    <input {...register('stateProvince')} className="input-field" placeholder="NSW" />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">Country Code *</label>
                    <input {...register('countryCode')} className="input-field" placeholder="AU" maxLength={2} />
                    {errors.countryCode && <p className="text-xs text-red-500 mt-0.5">{errors.countryCode.message}</p>}
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">Postal Code</label>
                    <input {...register('postalCode')} className="input-field" placeholder="2000" />
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Timezone *</label>
                  <select {...register('timezone')} className="input-field">
                    {TIMEZONES.map(tz => <option key={tz} value={tz}>{tz}</option>)}
                  </select>
                </div>

                {createMut.isError && (
                  <p className="text-xs text-red-500 bg-red-50 border border-red-200 rounded px-3 py-2">
                    Failed to create store. Check your input and try again.
                  </p>
                )}

                <div className="flex gap-3 justify-end pt-2">
                  <button type="button" onClick={() => { setOpen(false); reset(); createMut.reset() }} className="btn-secondary">
                    Cancel
                  </button>
                  <button type="submit" disabled={createMut.isPending} className="btn-primary">
                    {createMut.isPending ? 'Creating…' : 'Create Store'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

      </div>
    </>
  )
}
