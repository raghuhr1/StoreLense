'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState }  from 'react'
import { type ColumnDef }     from '@tanstack/react-table'
import { Plus, ArrowRight }   from 'lucide-react'
import Link                   from 'next/link'
import Header                 from '@/components/layout/Header'
import DataTable              from '@/components/ui/DataTable'
import { statusBadge }        from '@/components/ui/Badge'
import { refillApi }          from '@/lib/api/refill'
import { storesApi }          from '@/lib/api/stores'
import { useAuth }            from '@/lib/auth/AuthContext'
import { fmtDateTime }        from '@/lib/utils'
import type { RefillTask }    from '@/types'

type MovementType = 'backroom_to_floor' | 'dc_to_floor'

function movementType(task: RefillTask): MovementType {
  const n = (task.notes ?? '').toLowerCase()
  if (n.includes('dc') && n.includes('floor')) return 'dc_to_floor'
  if (task.source === 'erp') return 'dc_to_floor'
  return 'backroom_to_floor'
}

const MOVEMENT_LABEL: Record<MovementType, { label: string; cls: string }> = {
  backroom_to_floor: { label: 'Backroom → Floor',  cls: 'bg-purple-100 text-purple-700' },
  dc_to_floor:       { label: 'DC → Sales Floor',  cls: 'bg-green-100  text-green-700'  },
}

const SOURCE_LABEL: Record<string, string> = {
  manual:      'Manual',
  soh_trigger: 'RFID Alert',
  scheduled:   'Scheduled',
  erp:         'ERP / DC Direct',
}

function totalUnits(task: RefillTask) {
  return task.items.reduce((s, i) => s + i.requestedQuantity, 0)
}

export default function ReplenishmentPage() {
  const { user, isAdmin }    = useAuth()
  const [selectedStoreId, setSelectedStoreId] = useState<string>('')
  const [filterStatus, setFilterStatus]       = useState('')
  const [filterMovement, setFilterMovement]   = useState('')
  const [creating, setCreating]               = useState(false)
  const [dcToFloor, setDcToFloor]             = useState(false)
  const qc = useQueryClient()

  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })

  const storeId = isAdmin
    ? (selectedStoreId || allStores?.content[0]?.id || '')
    : (user?.storeId ?? '')

  const { data: rawTasks, isLoading } = useQuery({
    queryKey: ['refill-tasks', storeId, 'replenishment'],
    queryFn:  () => refillApi.listTasks(storeId, { size: 100 }),
    enabled:  !!storeId,
    // Replenishment = all non-ERP tasks PLUS ERP tasks explicitly DC→Floor
    select:   d => d.content.filter(t =>
      t.source !== 'erp' ||
      (t.notes ?? '').toLowerCase().includes('floor')
    ),
  })

  const createMut = useMutation({
    mutationFn: () => refillApi.createTask({
      storeId,
      taskType: 'replenishment',
      source:   'manual',
      notes:    dcToFloor ? 'DC → Sales Floor direct replenishment' : 'Backroom → Sales Floor',
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['refill-tasks'] }); setCreating(false) },
  })

  const data = useMemo(() => {
    return (rawTasks ?? []).filter(t => {
      if (filterStatus   && t.status !== filterStatus)                          return false
      if (filterMovement && movementType(t) !== filterMovement)                 return false
      return true
    })
  }, [rawTasks, filterStatus, filterMovement])

  const stats = useMemo(() => {
    const tasks = rawTasks ?? []
    return {
      total:    tasks.length,
      pending:  tasks.filter(t => ['pending', 'in_progress', 'assigned'].includes(t.status)).length,
      rfid:     tasks.filter(t => t.source === 'soh_trigger').length,
      units:    tasks.reduce((s, t) => s + totalUnits(t), 0),
    }
  }, [rawTasks])

  const hasFilters = filterStatus || filterMovement
  const selectCls  = 'text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500'

  const columns = useMemo<ColumnDef<RefillTask, unknown>[]>(() => [
    {
      accessorKey: 'id',
      header: 'Task',
      cell: i => (
        <Link href={`/replenishment/${i.getValue<string>()}`}
              className="font-mono text-xs text-blue-600 hover:underline">
          {i.getValue<string>().slice(-8)}
        </Link>
      ),
    },
    {
      id: 'movement',
      header: 'Movement',
      cell: ({ row: r }) => {
        const mv = movementType(r.original)
        const { label, cls } = MOVEMENT_LABEL[mv]
        return (
          <span className={`inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full ${cls}`}>
            <ArrowRight size={10} /> {label}
          </span>
        )
      },
    },
    {
      id: 'source',
      header: 'Source',
      cell: ({ row: r }) => (
        <span className="text-xs text-gray-600">{SOURCE_LABEL[r.original.source] ?? r.original.source}</span>
      ),
    },
    {
      accessorKey: 'priority',
      header: 'Priority',
      cell: i => {
        const v = i.getValue<number>()
        return (
          <span className={`text-xs font-bold ${v <= 2 ? 'text-red-600' : v <= 4 ? 'text-amber-600' : 'text-gray-600'}`}>
            P{v}
          </span>
        )
      },
    },
    {
      id: 'items',
      header: 'Lines / Units',
      cell: ({ row: r }) => (
        <span className="text-sm">{r.original.items.length} lines · {totalUnits(r.original)} units</span>
      ),
    },
    { accessorKey: 'status',    header: 'Status',  cell: i => statusBadge(i.getValue<string>()) },
    { accessorKey: 'createdAt', header: 'Created', cell: i => fmtDateTime(i.getValue<string>()) },
    {
      id: 'action',
      header: '',
      cell: ({ row: r }) => (
        <Link href={`/replenishment/${r.original.id}`}
              className="text-xs text-brand-600 hover:underline font-medium">
          View →
        </Link>
      ),
    },
  ], [])

  return (
    <>
      <Header title="Replenishment" />
      <div className="p-6 space-y-5">

        {/* Top bar */}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-3">
            {isAdmin && allStores && allStores.content.length > 0 && (
              <>
                <label className="text-sm font-medium text-gray-600 shrink-0">Store</label>
                <select value={storeId} onChange={e => setSelectedStoreId(e.target.value)} className={selectCls}>
                  {allStores.content.map(s => (
                    <option key={s.id} value={s.id}>{s.name} ({s.storeCode})</option>
                  ))}
                </select>
                <div className="h-5 w-px bg-gray-200" />
              </>
            )}

            {/* Filters */}
            <select value={filterStatus} onChange={e => setFilterStatus(e.target.value)} className={selectCls}>
              <option value="">All Status</option>
              <option value="pending">Pending</option>
              <option value="in_progress">In Progress</option>
              <option value="completed">Completed</option>
              <option value="cancelled">Cancelled</option>
            </select>

            <select value={filterMovement} onChange={e => setFilterMovement(e.target.value)} className={selectCls}>
              <option value="">All Movements</option>
              <option value="backroom_to_floor">Backroom → Floor</option>
              <option value="dc_to_floor">DC → Sales Floor</option>
            </select>

            {hasFilters && (
              <button
                onClick={() => { setFilterStatus(''); setFilterMovement('') }}
                className="text-xs text-gray-500 hover:text-gray-700 underline"
              >
                Clear
              </button>
            )}
          </div>

          <button onClick={() => setCreating(true)} className="btn-primary" disabled={!storeId}>
            <Plus size={16} /> New Task
          </button>
        </div>

        {/* Summary cards */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          {[
            { label: 'Total Tasks',      value: stats.total },
            { label: 'Pending / Active', value: stats.pending, amber: stats.pending > 0 },
            { label: 'RFID Triggered',   value: stats.rfid, blue: true },
            { label: 'Total Units',      value: stats.units.toLocaleString() },
          ].map(s => (
            <div key={s.label} className="card">
              <p className="text-xs text-gray-500">{s.label}</p>
              <p className={`text-2xl font-bold mt-0.5 ${s.amber ? 'text-amber-600' : s.blue ? 'text-blue-600' : 'text-gray-900'}`}>
                {s.value}
              </p>
            </div>
          ))}
        </div>

        {/* Task table */}
        <div className="card">
          <DataTable
            data={data}
            columns={columns}
            isLoading={isLoading}
            searchable
            searchPlaceholder="Search tasks…"
          />
        </div>

        {/* Create modal */}
        {creating && (
          <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-white rounded-2xl p-6 w-96 shadow-xl space-y-4">
              <h3 className="font-semibold text-gray-900">New Replenishment Task</h3>
              <p className="text-sm text-gray-500">Select the movement type:</p>
              <div className="space-y-2">
                <button
                  onClick={() => setDcToFloor(false)}
                  className={`w-full text-left px-4 py-3 border-2 rounded-lg transition-colors ${
                    !dcToFloor ? 'border-brand-500 bg-brand-50' : 'border-gray-200 hover:bg-gray-50'
                  }`}
                >
                  <p className="text-sm font-semibold text-gray-900">Backroom → Sales Floor</p>
                  <p className="text-xs text-gray-500 mt-0.5">Move stock from backroom to sales floor shelves</p>
                </button>
                <button
                  onClick={() => setDcToFloor(true)}
                  className={`w-full text-left px-4 py-3 border-2 rounded-lg transition-colors ${
                    dcToFloor ? 'border-brand-500 bg-brand-50' : 'border-gray-200 hover:bg-gray-50'
                  }`}
                >
                  <p className="text-sm font-semibold text-gray-900">DC → Sales Floor</p>
                  <p className="text-xs text-gray-500 mt-0.5">Received from DC and placed directly on floor</p>
                </button>
              </div>
              <div className="flex gap-3 justify-end pt-2">
                <button onClick={() => setCreating(false)} className="btn-secondary">Cancel</button>
                <button onClick={() => createMut.mutate()} disabled={createMut.isPending} className="btn-primary">
                  {createMut.isPending ? 'Creating…' : 'Create Task'}
                </button>
              </div>
            </div>
          </div>
        )}

      </div>
    </>
  )
}
