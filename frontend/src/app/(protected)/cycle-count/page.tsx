'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState }  from 'react'
import { type ColumnDef }     from '@tanstack/react-table'
import { Plus, ClipboardList } from 'lucide-react'
import Link                   from 'next/link'
import Header                 from '@/components/layout/Header'
import DataTable              from '@/components/ui/DataTable'
import { statusBadge }        from '@/components/ui/Badge'
import { cycleCountApi }      from '@/lib/api/cycle-count'
import { storesApi }          from '@/lib/api/stores'
import { useAuth }            from '@/lib/auth/AuthContext'
import { fmt }                from '@/lib/utils'
import type { CycleCount }    from '@/types'

export default function CycleCountListPage() {
  const { user, isAdmin }      = useAuth()
  const [selectedStoreId, setSelectedStoreId] = useState('')
  const [filterStatus, setFilterStatus]       = useState('')
  const [creating, setCreating]               = useState(false)
  const [newNotes,  setNewNotes]              = useState('')
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
    queryKey: ['cycle-counts', storeId],
    queryFn:  () => cycleCountApi.list(storeId, { size: 100 }),
    enabled:  !!storeId,
  })

  const createMut = useMutation({
    mutationFn: () => cycleCountApi.create({ storeId, notes: newNotes || undefined }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cycle-counts', storeId] })
      setCreating(false)
      setNewNotes('')
    },
  })

  const filtered = useMemo(() => {
    const rows = data?.content ?? []
    return filterStatus ? rows.filter(c => c.status === filterStatus) : rows
  }, [data, filterStatus])

  const columns = useMemo<ColumnDef<CycleCount, unknown>[]>(() => [
    {
      accessorKey: 'countDate',
      header: 'Count Date',
      cell: i => (
        <Link href={`/cycle-count/${i.row.original.id}`}
              className="font-medium text-brand-600 hover:underline">
          {fmt(i.getValue<string>())}
        </Link>
      ),
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: i => statusBadge(i.getValue<string>()),
    },
    {
      id: 'sessions',
      header: 'Sessions',
      cell: ({ row }) => {
        const sessions = row.original.sessions ?? []
        const done  = sessions.filter(s => ['completed','uploaded','reconciled','closed'].includes(s.status)).length
        return (
          <span className="text-sm text-gray-700">
            {done} / {sessions.length}
          </span>
        )
      },
    },
    {
      id: 'locations',
      header: 'Locations',
      cell: ({ row }) => {
        const locs = [...new Set(
          (row.original.sessions ?? [])
            .map(s => s.locationCode)
            .filter(Boolean)
        )]
        if (locs.length === 0) return <span className="text-gray-400 text-xs">—</span>
        return (
          <span className="text-xs text-gray-600">{locs.join(', ')}</span>
        )
      },
    },
    {
      accessorKey: 'notes',
      header: 'Notes',
      cell: i => i.getValue<string | null>()
        ? <span className="text-xs text-gray-600 truncate max-w-[180px] block">{i.getValue<string>()}</span>
        : <span className="text-gray-300 text-xs">—</span>,
    },
    {
      id: 'action',
      header: '',
      cell: ({ row }) => (
        <Link
          href={`/cycle-count/${row.original.id}`}
          className="text-xs text-brand-600 hover:underline"
        >
          View →
        </Link>
      ),
    },
  ], [])

  const selectCls = 'text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500'

  return (
    <>
      <Header title="Cycle Count" />
      <div className="p-6 space-y-4">

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

            <select value={filterStatus} onChange={e => setFilterStatus(e.target.value)} className={selectCls}>
              <option value="">All Status</option>
              <option value="DRAFT">Draft</option>
              <option value="RUNNING">Running</option>
              <option value="COMPLETED">Completed</option>
              <option value="UPLOADED">Uploaded</option>
              <option value="RECONCILED">Reconciled</option>
              <option value="CLOSED">Closed</option>
            </select>
            {filterStatus && (
              <button onClick={() => setFilterStatus('')} className="text-xs text-gray-500 hover:text-gray-700 underline">
                Clear
              </button>
            )}
          </div>

          <button
            onClick={() => setCreating(true)}
            disabled={!storeId}
            className="btn-primary disabled:opacity-40"
          >
            <Plus size={16} /> New Cycle Count
          </button>
        </div>

        {!isLoading && (data?.content ?? []).length === 0 && (
          <div className="flex flex-col items-center gap-3 py-16 text-gray-400">
            <ClipboardList size={40} strokeWidth={1.2} />
            <p className="text-sm">No cycle counts yet. Start a new one to begin Floor + Backroom counting.</p>
          </div>
        )}

        {(data?.content ?? []).length > 0 && (
          <div className="card">
            <DataTable
              data={filtered}
              columns={columns}
              isLoading={isLoading}
              searchable
              searchPlaceholder="Search by date or notes…"
            />
          </div>
        )}

      </div>

      {/* Create modal */}
      {creating && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl p-6 w-96 shadow-xl space-y-4">
            <h3 className="font-semibold text-gray-900">New Cycle Count</h3>
            <p className="text-sm text-gray-500">
              A new count will start in DRAFT status. Add location sessions from the detail page.
            </p>
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Notes (optional)</label>
              <textarea
                value={newNotes}
                onChange={e => setNewNotes(e.target.value)}
                rows={3}
                placeholder="e.g. End of season full count"
                className="input-field w-full resize-none"
              />
            </div>
            {createMut.isError && (
              <p className="text-xs text-red-500 bg-red-50 border border-red-200 rounded px-3 py-2">
                Failed to create cycle count. Try again.
              </p>
            )}
            <div className="flex gap-3 justify-end">
              <button
                onClick={() => { setCreating(false); setNewNotes('') }}
                className="btn-secondary"
              >
                Cancel
              </button>
              <button
                onClick={() => createMut.mutate()}
                disabled={createMut.isPending || !storeId}
                className="btn-primary disabled:opacity-40"
              >
                {createMut.isPending ? 'Creating…' : 'Create'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
