'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState }  from 'react'
import { type ColumnDef }     from '@tanstack/react-table'
import { Plus, Truck }        from 'lucide-react'
import Link                   from 'next/link'
import Header                 from '@/components/layout/Header'
import DataTable              from '@/components/ui/DataTable'
import { statusBadge }        from '@/components/ui/Badge'
import { refillApi }          from '@/lib/api/refill'
import { storesApi }          from '@/lib/api/stores'
import { useAuth }            from '@/lib/auth/AuthContext'
import { fmtDateTime }        from '@/lib/utils'
import type { RefillTask }    from '@/types'

function grnRef(task: RefillTask): string {
  if (task.notes) {
    const m = task.notes.match(/GRN[-:\s]?([A-Z0-9]+)/i)
    if (m) return m[1].toUpperCase()
  }
  return 'GRN-' + task.id.slice(-8).toUpperCase()
}

function totalUnits(task: RefillTask) {
  return task.items.reduce((s, i) => s + i.requestedQuantity, 0)
}

export default function InboundPage() {
  const { user, isAdmin }    = useAuth()
  const [selectedStoreId, setSelectedStoreId] = useState<string>('')
  const [creating, setCreating] = useState(false)
  const qc = useQueryClient()

  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })

  const storeId = isAdmin
    ? (selectedStoreId || allStores?.content[0]?.id || '')
    : (user?.storeId ?? '')

  const { data, isLoading } = useQuery({
    queryKey: ['refill-tasks', storeId, 'erp'],
    queryFn:  () => refillApi.listTasks(storeId, { size: 100 }),
    enabled:  !!storeId,
    select:   d => d.content.filter(t => t.source === 'erp'),
  })

  const createMut = useMutation({
    mutationFn: () => refillApi.createTask({
      storeId, taskType: 'replenishment', source: 'erp',
      notes: `GRN-${Date.now().toString(36).toUpperCase()}`,
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['refill-tasks'] }); setCreating(false) },
  })

  const stats = useMemo(() => {
    const tasks = data ?? []
    return {
      total:     tasks.length,
      pending:   tasks.filter(t => t.status === 'pending').length,
      completed: tasks.filter(t => t.status === 'completed').length,
      units:     tasks.reduce((s, t) => s + totalUnits(t), 0),
    }
  }, [data])

  const selectCls = 'text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500'

  const columns = useMemo<ColumnDef<RefillTask, unknown>[]>(() => [
    {
      id: 'grn',
      header: 'GRN Reference',
      accessorFn: r => grnRef(r),
      cell: ({ row: r }) => (
        <Link href={`/inbound/${r.original.id}`} className="hover:underline">
          <p className="font-mono text-xs font-semibold text-blue-600">{grnRef(r.original)}</p>
          <p className="text-xs text-gray-400 mt-0.5">DC Inbound</p>
        </Link>
      ),
    },
    {
      id: 'destination',
      header: 'Destination',
      cell: ({ row: r }) => {
        const toFloor = r.original.notes?.toLowerCase().includes('floor')
        return (
          <span className={`inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full ${
            toFloor ? 'bg-green-100 text-green-700' : 'bg-blue-100 text-blue-700'
          }`}>
            <Truck size={10} />
            {toFloor ? 'DC → Sales Floor' : 'DC → Backroom'}
          </span>
        )
      },
    },
    {
      id: 'items',
      header: 'Lines / Units',
      cell: ({ row: r }) => (
        <span className="text-sm">
          {r.original.items.length} lines · {totalUnits(r.original)} units
        </span>
      ),
    },
    { accessorKey: 'status',   header: 'Status',   cell: i => statusBadge(i.getValue<string>()) },
    { accessorKey: 'priority', header: 'Priority', cell: i => <span className="font-semibold text-xs">P{i.getValue<number>()}</span> },
    { accessorKey: 'createdAt', header: 'Received On', cell: i => fmtDateTime(i.getValue<string>()) },
    {
      id: 'action',
      header: '',
      cell: ({ row: r }) => (
        <Link href={`/inbound/${r.original.id}`}
          className="text-xs text-brand-600 hover:underline font-medium">
          View GRN →
        </Link>
      ),
    },
  ], [])

  return (
    <>
      <Header title="Inbound / Receive" />
      <div className="p-6 space-y-5">

        {/* Top bar */}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            {isAdmin && allStores && allStores.content.length > 0 && (
              <>
                <label className="text-sm font-medium text-gray-600 shrink-0">Store</label>
                <select value={storeId} onChange={e => setSelectedStoreId(e.target.value)} className={selectCls}>
                  {allStores.content.map(s => (
                    <option key={s.id} value={s.id}>{s.name} ({s.storeCode})</option>
                  ))}
                </select>
              </>
            )}
            {!isAdmin && (
              <p className="text-sm text-gray-500">DC shipments with GRN reference.</p>
            )}
          </div>
          <button onClick={() => setCreating(true)} className="btn-primary" disabled={!storeId}>
            <Plus size={16} /> New GRN Receipt
          </button>
        </div>

        {/* Summary stats */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          {[
            { label: 'Total GRNs',   value: stats.total },
            { label: 'Pending',      value: stats.pending,   cls: stats.pending > 0 ? 'text-amber-600' : 'text-gray-900' },
            { label: 'Completed',    value: stats.completed, cls: 'text-green-700' },
            { label: 'Total Units',  value: stats.units.toLocaleString() },
          ].map(s => (
            <div key={s.label} className="card">
              <p className="text-xs text-gray-500">{s.label}</p>
              <p className={`text-2xl font-bold mt-0.5 ${s.cls ?? 'text-gray-900'}`}>{s.value}</p>
            </div>
          ))}
        </div>

        {/* GRN table */}
        <div className="card">
          <div className="flex items-center gap-2 mb-4">
            <Truck size={16} className="text-gray-400" />
            <h2 className="text-sm font-semibold text-gray-700">GRN Receipts — ERP Inbound</h2>
          </div>
          <DataTable
            data={data ?? []}
            columns={columns}
            isLoading={isLoading}
            searchable
            searchPlaceholder="Search GRN or status…"
          />
        </div>

        {/* Create GRN modal */}
        {creating && (
          <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-white rounded-2xl p-6 w-96 shadow-xl space-y-4">
              <h3 className="font-semibold text-gray-900">Create Manual GRN Receipt</h3>
              <p className="text-sm text-gray-500">
                Creates an inbound task for a DC shipment. A GRN reference will be auto-generated.
              </p>
              <div className="flex gap-3 justify-end">
                <button onClick={() => setCreating(false)} className="btn-secondary">Cancel</button>
                <button onClick={() => createMut.mutate()} disabled={createMut.isPending} className="btn-primary">
                  {createMut.isPending ? 'Creating…' : 'Create GRN'}
                </button>
              </div>
            </div>
          </div>
        )}

      </div>
    </>
  )
}
