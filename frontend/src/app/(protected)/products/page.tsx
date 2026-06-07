'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState }  from 'react'
import { type ColumnDef }     from '@tanstack/react-table'
import { useForm }            from 'react-hook-form'
import { zodResolver }        from '@hookform/resolvers/zod'
import { z }                  from 'zod'
import { Plus }               from 'lucide-react'
import Header                 from '@/components/layout/Header'
import DataTable              from '@/components/ui/DataTable'
import { statusBadge }        from '@/components/ui/Badge'
import { productsApi }        from '@/lib/api/products'
import { fmt }                from '@/lib/utils'
import type { Product }       from '@/types'

const schema = z.object({
  sku:           z.string().min(1, 'Required').max(100),
  name:          z.string().min(1, 'Required'),
  description:   z.string().optional(),
  brand:         z.string().optional(),
  categoryId:    z.string().optional(),
  unitOfMeasure: z.string().min(1, 'Required'),
  rfidEnabled:   z.boolean(),
})
type FormValues = z.infer<typeof schema>

export default function ProductsPage() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [search, setSearch]       = useState('')
  const [filterBrand, setFilterBrand]   = useState('')
  const [filterRfid,  setFilterRfid]    = useState('')   // '' | 'enabled' | 'disabled'
  const [filterStatus, setFilterStatus] = useState('')   // '' | 'active' | 'inactive'

  const { data, isLoading } = useQuery({
    queryKey: ['products', search],
    queryFn:  () => productsApi.list({ search: search || undefined, size: 500 }),
  })

  const createMut = useMutation({
    mutationFn: (v: FormValues) => productsApi.create(v),
    onSuccess:  () => { qc.invalidateQueries({ queryKey: ['products'] }); setOpen(false); reset() },
  })

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { unitOfMeasure: 'EACH', rfidEnabled: true },
  })

  const brands = useMemo(() => {
    const s = new Set<string>()
    for (const p of data?.content ?? []) if (p.brand) s.add(p.brand)
    return Array.from(s).sort()
  }, [data])

  const filtered = useMemo(() => {
    return (data?.content ?? []).filter(p => {
      if (filterBrand  && p.brand !== filterBrand)                         return false
      if (filterRfid === 'enabled'  && !p.rfidEnabled)                    return false
      if (filterRfid === 'disabled' && p.rfidEnabled)                     return false
      if (filterStatus === 'active'   && p.active === false)              return false
      if (filterStatus === 'inactive' && p.active !== false)              return false
      return true
    })
  }, [data, filterBrand, filterRfid, filterStatus])

  const hasFilters = filterBrand || filterRfid || filterStatus

  const columns = useMemo<ColumnDef<Product, unknown>[]>(() => [
    { accessorKey: 'sku',           header: 'SKU',    cell: i => <span className="font-mono text-xs font-semibold">{i.getValue<string>()}</span> },
    { accessorKey: 'name',          header: 'Name',   cell: i => <span className="font-medium">{i.getValue<string>()}</span> },
    { accessorKey: 'brand',         header: 'Brand',  cell: i => i.getValue<string | null>() ?? '—' },
    { accessorKey: 'unitOfMeasure', header: 'UOM' },
    {
      accessorKey: 'rfidEnabled',
      header: 'RFID',
      cell: i => (
        <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${i.getValue<boolean>() ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'}`}>
          {i.getValue<boolean>() ? 'Enabled' : 'Disabled'}
        </span>
      ),
    },
    { accessorKey: 'active',     header: 'Status',  cell: i => statusBadge(i.getValue<boolean>() ? 'active' : 'inactive') },
    { accessorKey: 'createdAt',  header: 'Created', cell: i => fmt(i.getValue<string>()) },
  ], [])

  const selectCls = "text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500"

  return (
    <>
      <Header title="Products" />
      <div className="p-6 space-y-4">

        <div className="flex flex-wrap items-center justify-between gap-3">
          {/* Search */}
          <input
            className="input-field max-w-xs"
            placeholder="Search by SKU or name…"
            value={search}
            onChange={e => setSearch(e.target.value)}
          />

          {/* Filters */}
          <div className="flex flex-wrap items-center gap-2">
            <select value={filterBrand} onChange={e => setFilterBrand(e.target.value)} className={selectCls}>
              <option value="">All Brands</option>
              {brands.map(b => <option key={b} value={b}>{b}</option>)}
            </select>

            <select value={filterRfid} onChange={e => setFilterRfid(e.target.value)} className={selectCls}>
              <option value="">All RFID</option>
              <option value="enabled">RFID Enabled</option>
              <option value="disabled">RFID Disabled</option>
            </select>

            <select value={filterStatus} onChange={e => setFilterStatus(e.target.value)} className={selectCls}>
              <option value="">All Status</option>
              <option value="active">Active</option>
              <option value="inactive">Inactive</option>
            </select>

            {hasFilters && (
              <button
                onClick={() => { setFilterBrand(''); setFilterRfid(''); setFilterStatus('') }}
                className="text-xs text-gray-500 hover:text-gray-700 underline"
              >
                Clear
              </button>
            )}

            <button onClick={() => setOpen(true)} className="btn-primary">
              <Plus size={16} /> Add Product
            </button>
          </div>
        </div>

        <div className="card">
          <p className="text-xs text-gray-400 mb-3">{filtered.length.toLocaleString()} products</p>
          <DataTable
            data={filtered}
            columns={columns}
            isLoading={isLoading}
            searchable={false}
          />
        </div>

        {open && (
          <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-white rounded-2xl p-6 w-[480px] shadow-xl">
              <h3 className="font-semibold text-gray-900 mb-4">Add Product</h3>
              <form onSubmit={handleSubmit(v => createMut.mutate(v))} className="space-y-3">

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">SKU *</label>
                    <input {...register('sku')} className="input-field" placeholder="e.g. SHIRT-RED-M" />
                    {errors.sku && <p className="text-xs text-red-500 mt-0.5">{errors.sku.message}</p>}
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">Unit of Measure *</label>
                    <select {...register('unitOfMeasure')} className="input-field">
                      <option value="EACH">Each</option>
                      <option value="PAIR">Pair</option>
                      <option value="PACK">Pack</option>
                      <option value="KG">KG</option>
                      <option value="LTR">Litre</option>
                    </select>
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Product Name *</label>
                  <input {...register('name')} className="input-field" placeholder="e.g. Classic Red T-Shirt Medium" />
                  {errors.name && <p className="text-xs text-red-500 mt-0.5">{errors.name.message}</p>}
                </div>

                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Brand</label>
                  <input {...register('brand')} className="input-field" placeholder="e.g. Nike" />
                </div>

                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Description</label>
                  <textarea {...register('description')} className="input-field" rows={2} placeholder="Optional product description" />
                </div>

                <div className="flex items-center gap-3">
                  <input {...register('rfidEnabled')} type="checkbox" id="rfidEnabled" className="w-4 h-4 accent-blue-600" />
                  <label htmlFor="rfidEnabled" className="text-sm text-gray-700">RFID-enabled product</label>
                </div>

                {createMut.isError && (
                  <p className="text-xs text-red-500 bg-red-50 border border-red-200 rounded px-3 py-2">
                    Failed to create product. Check SKU is unique and try again.
                  </p>
                )}

                <div className="flex gap-3 justify-end pt-2">
                  <button type="button" onClick={() => { setOpen(false); reset() }} className="btn-secondary">Cancel</button>
                  <button type="submit" disabled={createMut.isPending} className="btn-primary">
                    {createMut.isPending ? 'Adding…' : 'Add Product'}
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
