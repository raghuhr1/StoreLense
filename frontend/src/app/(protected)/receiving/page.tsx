'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState }  from 'react'
import { type ColumnDef }     from '@tanstack/react-table'
import { Plus }               from 'lucide-react'
import Link                   from 'next/link'
import Header                 from '@/components/layout/Header'
import DataTable              from '@/components/ui/DataTable'
import { statusBadge }        from '@/components/ui/Badge'
import { refillApi }          from '@/lib/api/refill'
import { storesApi }          from '@/lib/api/stores'
import { useAuth }            from '@/lib/auth/AuthContext'
import { fmtDateTime }        from '@/lib/utils'
import type { RefillTask }    from '@/types'

export default function ReceivingPage() {
  const { user, isAdmin }    = useAuth()
  const [selectedStoreId, setSelectedStoreId] = useState<string>('')
  const qc                   = useQueryClient()
  const [creating, setCreating] = useState(false)

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
    select:   d => d.content.filter(t => t.source === 'erp' || t.taskType === 'replenishment'),
  })

  const createMut = useMutation({
    mutationFn: () => refillApi.createTask({ storeId, taskType: 'replenishment', source: 'manual' }),
    onSuccess:  () => { qc.invalidateQueries({ queryKey: ['refill-tasks'] }); setCreating(false) },
  })

  const columns = useMemo<ColumnDef<RefillTask, unknown>[]>(() => [
    {
      accessorKey: 'id',
      header: 'ID',
      cell: i => (
        <Link href={`/receiving/${i.getValue<string>()}`}
              className="font-mono text-xs text-blue-600 hover:underline">
          {i.getValue<string>().slice(-8)}
        </Link>
      ),
    },
    { accessorKey: 'taskType', header: 'Type' },
    { accessorKey: 'status',   header: 'Status',   cell: i => statusBadge(i.getValue<string>()) },
    { accessorKey: 'priority', header: 'Priority', cell: i => <span className="font-semibold">P{i.getValue<number>()}</span> },
    {
      id: 'items',
      header: 'Items',
      cell: ({ row }) => `${row.original.items.length} line${row.original.items.length !== 1 ? 's' : ''}`,
    },
    { accessorKey: 'createdAt', header: 'Created', cell: i => fmtDateTime(i.getValue<string>()) },
    { accessorKey: 'source',    header: 'Source' },
  ], [])

  return (
    <>
      <Header title="Receiving" />
      <div className="p-6 space-y-4">

        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            {isAdmin && allStores && allStores.content.length > 0 && (
              <>
                <label className="text-sm font-medium text-gray-600 shrink-0">Store</label>
                <select
                  value={storeId}
                  onChange={e => setSelectedStoreId(e.target.value)}
                  className="text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500"
                >
                  {allStores.content.map(s => (
                    <option key={s.id} value={s.id}>{s.name} ({s.storeCode})</option>
                  ))}
                </select>
              </>
            )}
            {!isAdmin && (
              <p className="text-sm text-gray-500">Inbound stock tasks from ERP and manual creation.</p>
            )}
          </div>
          <button onClick={() => setCreating(true)} className="btn-primary" disabled={!storeId}>
            <Plus size={16} /> New Task
          </button>
        </div>

        <div className="card">
          <DataTable
            data={data ?? []}
            columns={columns}
            isLoading={isLoading}
            searchable
            searchPlaceholder="Search tasks…"
          />
        </div>

        {creating && (
          <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-white rounded-2xl p-6 w-96 shadow-xl space-y-4">
              <h3 className="font-semibold text-gray-900">Create Receiving Task</h3>
              <p className="text-sm text-gray-500">Creates a manual replenishment task for this store.</p>
              <div className="flex gap-3 justify-end">
                <button onClick={() => setCreating(false)} className="btn-secondary">Cancel</button>
                <button onClick={() => createMut.mutate()} disabled={createMut.isPending} className="btn-primary">
                  {createMut.isPending ? 'Creating…' : 'Create'}
                </button>
              </div>
            </div>
          </div>
        )}

      </div>
    </>
  )
}
