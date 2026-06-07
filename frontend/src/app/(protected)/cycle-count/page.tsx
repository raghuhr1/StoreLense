'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState }  from 'react'
import { type ColumnDef }     from '@tanstack/react-table'
import { Plus, PlayCircle }  from 'lucide-react'
import Link                   from 'next/link'
import Header                 from '@/components/layout/Header'
import DataTable              from '@/components/ui/DataTable'
import { statusBadge }        from '@/components/ui/Badge'
import { sohApi }             from '@/lib/api/soh'
import { storesApi }          from '@/lib/api/stores'
import { useAuth }            from '@/lib/auth/AuthContext'
import { fmtDateTime }       from '@/lib/utils'
import type { SohSession }    from '@/types'

export default function CycleCountPage() {
  const { user, isAdmin }   = useAuth()
  const [selectedStoreId, setSelectedStoreId] = useState<string>('')
  const qc                  = useQueryClient()
  const [starting, setStarting] = useState(false)
  const [filterStatus, setFilterStatus] = useState('')   // '' | 'completed' | 'in_progress' | 'pending'
  const [filterType,   setFilterType]   = useState('')   // '' | 'full_store' | 'spot_check' | 'manual'

  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })

  const storeId = isAdmin
    ? (selectedStoreId || allStores?.content[0]?.id || '')
    : (user?.storeId ?? '')

  const { data, isLoading } = useQuery({
    queryKey: ['soh-sessions', storeId],
    queryFn:  () => sohApi.listSessions(storeId, { size: 100 }),
    enabled:  !!storeId,
  })

  const startMut = useMutation({
    mutationFn: (sessionType: string) =>
      sohApi.startSession({ storeId, sessionType }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['soh-sessions'] }); setStarting(false) },
  })

  const columns = useMemo<ColumnDef<SohSession, unknown>[]>(() => [
    {
      accessorKey: 'id',
      header: 'Session',
      cell: i => (
        <Link href={`/cycle-count/${i.getValue<string>()}`}
              className="font-mono text-xs text-blue-600 hover:underline">
          {i.getValue<string>().slice(-8)}
        </Link>
      ),
    },
    { accessorKey: 'sessionType',   header: 'Type' },
    { accessorKey: 'status',        header: 'Status',      cell: i => statusBadge(i.getValue<string>()) },
    { accessorKey: 'totalEpcReads', header: 'EPC Reads',   cell: i => i.getValue<number>().toLocaleString() },
    { accessorKey: 'uniqueEpcCount', header: 'Unique EPCs' },
    { accessorKey: 'startedAt',     header: 'Started',     cell: i => fmtDateTime(i.getValue<string>()) },
    { accessorKey: 'completedAt',   header: 'Completed',   cell: i => fmtDateTime(i.getValue<string|null>()) },
  ], [])

  const activeSession = data?.content.find(s => s.status === 'in_progress')

  const filtered = useMemo(() => {
    return (data?.content ?? []).filter(s => {
      if (filterStatus && s.status      !== filterStatus) return false
      if (filterType   && s.sessionType !== filterType)   return false
      return true
    })
  }, [data, filterStatus, filterType])

  const hasFilters = filterStatus || filterType

  const selectCls = "text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500"

  return (
    <>
      <Header title="Cycle Count" />
      <div className="p-6 space-y-4">

        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            {/* Admin store selector */}
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

            {activeSession && (
              <div className="flex items-center gap-2 text-yellow-700 bg-yellow-50 border border-yellow-200 px-3 py-2 rounded-lg text-sm">
                <PlayCircle size={16} />
                Session in progress —{' '}
                <Link href={`/cycle-count/${activeSession.id}`} className="font-semibold underline">Resume</Link>
              </div>
            )}

            {!activeSession && !isAdmin && (
              <p className="text-sm text-gray-500">No active session. Start a new count below.</p>
            )}
          </div>

          {/* Filters + action */}
          <div className="flex flex-wrap items-center gap-2">
            <select value={filterStatus} onChange={e => setFilterStatus(e.target.value)} className={selectCls}>
              <option value="">All Status</option>
              <option value="completed">Completed</option>
              <option value="in_progress">In Progress</option>
              <option value="pending">Pending</option>
            </select>

            <select value={filterType} onChange={e => setFilterType(e.target.value)} className={selectCls}>
              <option value="">All Types</option>
              <option value="full_store">Full Store</option>
              <option value="spot_check">Spot Check</option>
              <option value="manual">Manual</option>
            </select>

            {hasFilters && (
              <button
                onClick={() => { setFilterStatus(''); setFilterType('') }}
                className="text-xs text-gray-500 hover:text-gray-700 underline"
              >
                Clear
              </button>
            )}

            <button
              onClick={() => setStarting(true)}
              disabled={!!activeSession || !storeId}
              className="btn-primary disabled:opacity-40"
            >
              <Plus size={16} /> Start Count
            </button>
          </div>
        </div>

        <div className="card">
          <DataTable
            data={filtered}
            columns={columns}
            isLoading={isLoading}
            searchable
            searchPlaceholder="Search sessions…"
          />
        </div>

        {starting && (
          <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-white rounded-2xl p-6 w-96 shadow-xl space-y-4">
              <h3 className="font-semibold text-gray-900">Start Cycle Count</h3>
              <div className="space-y-2">
                {(['full_store', 'spot_check', 'manual'] as const).map(type => (
                  <button
                    key={type}
                    onClick={() => startMut.mutate(type)}
                    disabled={startMut.isPending}
                    className="w-full text-left px-4 py-3 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
                  >
                    <p className="text-sm font-medium capitalize">{type.replace(/_/g, ' ')}</p>
                  </button>
                ))}
              </div>
              <button onClick={() => setStarting(false)} className="btn-secondary w-full">Cancel</button>
            </div>
          </div>
        )}

      </div>
    </>
  )
}
