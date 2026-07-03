'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState }  from 'react'
import { type ColumnDef }     from '@tanstack/react-table'
import { useForm }            from 'react-hook-form'
import { zodResolver }        from '@hookform/resolvers/zod'
import { z }                  from 'zod'
import { Plus, Pencil }       from 'lucide-react'
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
  storeCode:     z.string().min(1, 'Required').max(20),
  name:          z.string().min(1, 'Required'),
  addressLine1:  z.string().optional(),
  city:          z.string().optional(),
  stateProvince: z.string().optional(),
  postalCode:    z.string().optional(),
  countryCode:   z.string().length(2, 'Must be 2-letter country code'),
  timezone:      z.string().min(1, 'Required'),
  erpStoreCode:  z.string().optional(),
})
type FormValues = z.infer<typeof schema>

export default function StoresPage() {
  const qc = useQueryClient()
  const [open, setOpen]       = useState(false)
  const [editing, setEditing] = useState<Store | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['stores'],
    queryFn:  () => storesApi.list({ size: 500 }),
  })

  const createMut = useMutation({
    mutationFn: (v: FormValues) => storesApi.create(v),
    onSuccess:  () => { qc.invalidateQueries({ queryKey: ['stores'] }); setOpen(false); reset() },
  })

  const updateMut = useMutation({
    mutationFn: ({ id, body }: { id: string; body: FormValues }) => storesApi.update(id, body),
    onSuccess:  () => { qc.invalidateQueries({ queryKey: ['stores'] }); setEditing(null); reset() },
  })

  const deactivateMut = useMutation({
    mutationFn: (id: string) => storesApi.deactivate(id),
    onSuccess:  () => { qc.invalidateQueries({ queryKey: ['stores'] }); closeForm() },
  })

  const activateMut = useMutation({
    mutationFn: (id: string) => storesApi.update(id, { active: true } as Partial<Store>),
    onSuccess:  () => { qc.invalidateQueries({ queryKey: ['stores'] }); closeForm() },
  })

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { countryCode: 'IN', timezone: 'Asia/Kolkata' },
  })

  const openEdit = (s: Store) => {
    setEditing(s)
    reset({
      storeCode:     s.storeCode,
      name:          s.name,
      addressLine1:  s.addressLine1 ?? '',
      city:          s.city ?? '',
      stateProvince: s.stateProvince ?? '',
      postalCode:    s.postalCode ?? '',
      countryCode:   s.countryCode ?? 'IN',
      timezone:      s.timezone ?? 'Asia/Kolkata',
      erpStoreCode:  s.erpStoreCode ?? '',
    })
  }

  const closeForm = () => { setOpen(false); setEditing(null); reset(); createMut.reset(); updateMut.reset() }

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
    { accessorKey: 'active',        header: 'Status',   cell: i => statusBadge(i.getValue<boolean>() ? 'active' : 'inactive') },
    { accessorKey: 'erpStoreCode',  header: 'ERP Code', cell: i => i.getValue<string | null>() ?? '—' },
    { accessorKey: 'createdAt',     header: 'Created',  cell: i => fmt(i.getValue<string>()) },
    {
      id: 'actions',
      header: '',
      cell: ({ row }) => (
        <button
          onClick={() => openEdit(row.original)}
          className="p-1.5 text-gray-400 hover:text-brand-600 hover:bg-gray-100 rounded transition-colors"
          title="Edit store"
        >
          <Pencil size={15} />
        </button>
      ),
    },
  ], [])

  const isOpen = open || !!editing
  const isEdit = !!editing

  const StoreForm = () => (
    <form onSubmit={handleSubmit(v => isEdit ? updateMut.mutate({ id: editing!.id, body: v }) : createMut.mutate(v))} className="space-y-3">
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-medium text-gray-700 mb-1">Store Code *</label>
          <input {...register('storeCode')} className="input-field disabled:bg-gray-50 disabled:text-gray-500" placeholder="STORE01" disabled={isEdit} />
          {errors.storeCode && <p className="text-xs text-red-500 mt-0.5">{errors.storeCode.message}</p>}
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-700 mb-1">ERP Code</label>
          <input {...register('erpStoreCode')} className="input-field" placeholder="Optional" />
        </div>
      </div>

      <div>
        <label className="block text-xs font-medium text-gray-700 mb-1">Store Name *</label>
        <input {...register('name')} className="input-field" placeholder="e.g. Mumbai Linking Road" />
        {errors.name && <p className="text-xs text-red-500 mt-0.5">{errors.name.message}</p>}
      </div>

      <div>
        <label className="block text-xs font-medium text-gray-700 mb-1">Address</label>
        <input {...register('addressLine1')} className="input-field" placeholder="Street address" />
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-medium text-gray-700 mb-1">City</label>
          <input {...register('city')} className="input-field" placeholder="Mumbai" />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-700 mb-1">State / Province</label>
          <input {...register('stateProvince')} className="input-field" placeholder="Maharashtra" />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-medium text-gray-700 mb-1">Country Code *</label>
          <input {...register('countryCode')} className="input-field" placeholder="IN" maxLength={2} />
          {errors.countryCode && <p className="text-xs text-red-500 mt-0.5">{errors.countryCode.message}</p>}
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-700 mb-1">Postal Code</label>
          <input {...register('postalCode')} className="input-field" placeholder="400050" />
        </div>
      </div>

      <div>
        <label className="block text-xs font-medium text-gray-700 mb-1">Timezone *</label>
        <select {...register('timezone')} className="input-field">
          {TIMEZONES.map(tz => <option key={tz} value={tz}>{tz}</option>)}
        </select>
      </div>

      {(createMut.isError || updateMut.isError) && (
        <p className="text-xs text-red-500 bg-red-50 border border-red-200 rounded px-3 py-2">
          Failed to {isEdit ? 'update' : 'create'} store. Check your input and try again.
        </p>
      )}

      <div className="flex items-center gap-3 pt-2">
        {isEdit && (
          editing!.active ? (
            <button
              type="button"
              onClick={() => { if (confirm(`Deactivate ${editing!.name}? Staff will lose access.`)) deactivateMut.mutate(editing!.id) }}
              disabled={deactivateMut.isPending}
              className="text-xs text-red-600 hover:text-red-700 hover:bg-red-50 border border-red-200 rounded-lg px-3 py-1.5 transition-colors disabled:opacity-50"
            >
              {deactivateMut.isPending ? 'Deactivating…' : 'Deactivate Store'}
            </button>
          ) : (
            <button
              type="button"
              onClick={() => activateMut.mutate(editing!.id)}
              disabled={activateMut.isPending}
              className="text-xs text-green-700 hover:text-green-800 hover:bg-green-50 border border-green-200 rounded-lg px-3 py-1.5 transition-colors disabled:opacity-50"
            >
              {activateMut.isPending ? 'Activating…' : 'Activate Store'}
            </button>
          )
        )}
        <div className="flex gap-3 ml-auto">
          <button type="button" onClick={closeForm} className="btn-secondary">Cancel</button>
          <button type="submit" disabled={createMut.isPending || updateMut.isPending} className="btn-primary">
            {(createMut.isPending || updateMut.isPending) ? 'Saving…' : isEdit ? 'Save Changes' : 'Create Store'}
          </button>
        </div>
      </div>
    </form>
  )

  return (
    <>
      <Header title="Stores" />
      <div className="p-6 space-y-4">

        <div className="flex justify-end">
          <button onClick={() => { reset({ countryCode: 'IN', timezone: 'Asia/Kolkata' }); setOpen(true) }} className="btn-primary">
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

        {isOpen && (
          <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-white rounded-2xl p-6 w-[520px] shadow-xl max-h-[90vh] overflow-y-auto">
              <h3 className="font-semibold text-gray-900 mb-4">{isEdit ? 'Edit Store' : 'Add Store'}</h3>
              <StoreForm />
            </div>
          </div>
        )}

      </div>
    </>
  )
}
