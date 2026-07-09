'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState }  from 'react'
import { type ColumnDef }     from '@tanstack/react-table'
import { Plus, Trash2, Radio } from 'lucide-react'
import Header                 from '@/components/layout/Header'
import DataTable              from '@/components/ui/DataTable'
import { statusBadge }        from '@/components/ui/Badge'
import { storesApi }          from '@/lib/api/stores'
import { useAuth }            from '@/lib/auth/AuthContext'
import type { AntennaLocationMapping, RfidReader } from '@/types'

const LOCATION_CODES = ['SALES_FLOOR', 'BACKROOM'] as const
const SECTION_CODES  = ['MENS', 'WOMENS', 'KIDS', 'FOOTWEAR', 'ACCESSORIES'] as const

export default function AntennaMappingPage() {
  const { user, isAdmin }      = useAuth()
  const [selectedStoreId, setSelectedStoreId] = useState('')
  const [addOpen, setAddOpen]  = useState(false)
  const [form, setForm]        = useState({
    readerId:    '',
    antennaPort: '1',
    locationCode: 'SALES_FLOOR',
    sectionCode:  '',
    displayName:  '',
  })
  const qc = useQueryClient()

  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })

  const storeId = isAdmin
    ? (selectedStoreId || allStores?.content[0]?.id || '')
    : (user?.storeId ?? '')

  const { data: mappings, isLoading } = useQuery({
    queryKey: ['antenna-mappings', storeId],
    queryFn:  () => storesApi.antennaMappings(storeId),
    enabled:  !!storeId,
  })

  const { data: readers } = useQuery({
    queryKey: ['readers', storeId],
    queryFn:  () => storesApi.readers(storeId),
    enabled:  !!storeId,
  })

  const readerById = useMemo(
    () => new Map((readers ?? []).map((r: RfidReader) => [r.id, r])),
    [readers]
  )

  const createMut = useMutation({
    mutationFn: () => storesApi.createAntennaMapping(storeId, {
      readerId:    form.readerId,
      antennaPort: Number(form.antennaPort),
      locationCode: form.locationCode,
      sectionCode:  form.locationCode === 'SALES_FLOOR' && form.sectionCode ? form.sectionCode : undefined,
      displayName:  form.displayName || undefined,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['antenna-mappings', storeId] })
      setAddOpen(false)
      setForm({ readerId: '', antennaPort: '1', locationCode: 'SALES_FLOOR', sectionCode: '', displayName: '' })
    },
  })

  const deactivateMut = useMutation({
    mutationFn: (mappingId: string) => storesApi.deactivateAntennaMapping(storeId, mappingId),
    onSuccess:  () => qc.invalidateQueries({ queryKey: ['antenna-mappings', storeId] }),
  })

  const columns = useMemo<ColumnDef<AntennaLocationMapping, unknown>[]>(() => [
    {
      accessorKey: 'readerId',
      header: 'Reader',
      cell: i => {
        const rid = i.getValue<string>()
        const r   = readerById.get(rid)
        return (
          <div>
            <p className="font-mono text-xs text-gray-800">{r?.readerCode ?? rid}</p>
            {r && <p className="text-xs text-gray-400">{r.readerType}</p>}
          </div>
        )
      },
    },
    {
      accessorKey: 'antennaPort',
      header: 'Port',
      cell: i => <span className="font-mono text-sm text-gray-700">#{i.getValue<number>()}</span>,
    },
    {
      accessorKey: 'locationCode',
      header: 'Location',
      cell: i => {
        const lc = i.getValue<string>()
        const sc = (i.row.original as AntennaLocationMapping).sectionCode
        return (
          <span className="text-sm text-gray-800">
            {lc === 'SALES_FLOOR' ? (sc ? `Floor / ${sc}` : 'Sales Floor') : 'Backroom'}
          </span>
        )
      },
    },
    {
      accessorKey: 'displayName',
      header: 'Label',
      cell: i => i.getValue<string | null>() ?? <span className="text-gray-300 text-xs">—</span>,
    },
    {
      accessorKey: 'isActive',
      header: 'Status',
      cell: i => statusBadge(i.getValue<boolean>() ? 'active' : 'inactive'),
    },
    {
      id: 'actions',
      header: '',
      cell: ({ row }) => row.original.isActive ? (
        <button
          onClick={() => deactivateMut.mutate(row.original.id)}
          disabled={deactivateMut.isPending}
          className="p-1.5 rounded-lg text-gray-400 hover:text-red-500 hover:bg-red-50 transition-colors"
          title="Deactivate mapping"
        >
          <Trash2 size={14} />
        </button>
      ) : null,
    },
  ], [readerById, deactivateMut])

  const inputCls = 'input-field'
  const selectCls = 'text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500'

  return (
    <>
      <Header title="Antenna Location Mapping" />
      <div className="p-6 space-y-6">

        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            {isAdmin && allStores && allStores.content.length > 0 && (
              <>
                <label className="text-sm font-medium text-gray-600 shrink-0">Store</label>
                <select
                  value={storeId}
                  onChange={e => setSelectedStoreId(e.target.value)}
                  className={selectCls}
                >
                  {allStores.content.map(s => (
                    <option key={s.id} value={s.id}>{s.name} ({s.storeCode})</option>
                  ))}
                </select>
              </>
            )}
          </div>

          <button
            onClick={() => setAddOpen(true)}
            disabled={!storeId}
            className="btn-primary disabled:opacity-40"
          >
            <Plus size={16} /> Add Mapping
          </button>
        </div>

        <div className="flex items-start gap-3 p-4 bg-blue-50 border border-blue-100 rounded-xl text-sm text-blue-800">
          <Radio size={16} className="flex-shrink-0 mt-0.5 text-blue-500" />
          <p>
            Each antenna port on an RFID reader can be mapped to a physical store location (Sales Floor or Backroom).
            When an EPC is read, the antenna mapping determines which location to attribute the read to — used by the
            Cycle Count floor/backroom breakdown.
          </p>
        </div>

        <div className="card">
          <DataTable
            data={mappings ?? []}
            columns={columns}
            isLoading={isLoading}
            searchable
            searchPlaceholder="Search by reader code or location…"
          />
        </div>

      </div>

      {/* Add mapping modal */}
      {addOpen && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl p-6 w-[460px] shadow-xl space-y-4">
            <h3 className="font-semibold text-gray-900">Add Antenna Mapping</h3>

            <div className="space-y-3">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Reader *</label>
                  {readers && readers.length > 0 ? (
                    <select
                      value={form.readerId}
                      onChange={e => setForm(f => ({ ...f, readerId: e.target.value }))}
                      className={inputCls}
                    >
                      <option value="">— select reader —</option>
                      {readers.map((r: RfidReader) => (
                        <option key={r.id} value={r.id}>{r.readerCode}</option>
                      ))}
                    </select>
                  ) : (
                    <input
                      value={form.readerId}
                      onChange={e => setForm(f => ({ ...f, readerId: e.target.value }))}
                      placeholder="Reader ID / code"
                      className={inputCls}
                    />
                  )}
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Antenna Port *</label>
                  <input
                    type="number"
                    min={1}
                    max={32}
                    value={form.antennaPort}
                    onChange={e => setForm(f => ({ ...f, antennaPort: e.target.value }))}
                    className={inputCls}
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Location *</label>
                  <select
                    value={form.locationCode}
                    onChange={e => setForm(f => ({ ...f, locationCode: e.target.value, sectionCode: '' }))}
                    className={inputCls}
                  >
                    {LOCATION_CODES.map(lc => (
                      <option key={lc} value={lc}>{lc === 'SALES_FLOOR' ? 'Sales Floor' : 'Backroom'}</option>
                    ))}
                  </select>
                </div>
                {form.locationCode === 'SALES_FLOOR' && (
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">Section (optional)</label>
                    <select
                      value={form.sectionCode}
                      onChange={e => setForm(f => ({ ...f, sectionCode: e.target.value }))}
                      className={inputCls}
                    >
                      <option value="">— none (whole floor) —</option>
                      {SECTION_CODES.map(sc => (
                        <option key={sc} value={sc}>{sc.charAt(0) + sc.slice(1).toLowerCase()}</option>
                      ))}
                    </select>
                  </div>
                )}
              </div>

              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Display Label (optional)</label>
                <input
                  value={form.displayName}
                  onChange={e => setForm(f => ({ ...f, displayName: e.target.value }))}
                  placeholder="e.g. North entrance antenna 2"
                  className={inputCls}
                />
              </div>
            </div>

            {createMut.isError && (
              <p className="text-xs text-red-500 bg-red-50 border border-red-200 rounded px-3 py-2">
                Failed to create mapping. Check for duplicate reader / port combinations.
              </p>
            )}

            <div className="flex gap-3 justify-end">
              <button
                onClick={() => {
                  setAddOpen(false)
                  setForm({ readerId: '', antennaPort: '1', locationCode: 'SALES_FLOOR', sectionCode: '', displayName: '' })
                }}
                className="btn-secondary"
              >
                Cancel
              </button>
              <button
                onClick={() => createMut.mutate()}
                disabled={!form.readerId || createMut.isPending}
                className="btn-primary disabled:opacity-40"
              >
                {createMut.isPending ? 'Adding…' : 'Add Mapping'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
