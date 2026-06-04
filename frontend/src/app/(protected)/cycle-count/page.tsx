'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState }  from 'react'
import { type ColumnDef }     from '@tanstack/react-table'
import { Plus, PlayCircle, StopCircle } from 'lucide-react'
import Link                   from 'next/link'
import Header                 from '@/components/layout/Header'
import DataTable              from '@/components/ui/DataTable'
import { statusBadge }        from '@/components/ui/Badge'
import { sohApi }             from '@/lib/api/soh'
import { useAuth }            from '@/lib/auth/AuthContext'
import { fmtDateTime, fmtPct } from '@/lib/utils'
import type { SohSession }    from '@/types'

export default function CycleCountPage() {
  const { user }  = useAuth()
  const storeId   = user?.storeId ?? ''
  const qc        = useQueryClient()
  const [starting, setStarting] = useState(false)

  const { data, isLoading } = useQuery({
    queryKey: ['soh-sessions', storeId],
    queryFn:  () => sohApi.listSessions(storeId, { size: 50 }),
    enabled:  !!storeId,
  })

  const startMut = useMutation({
    mutationFn: (sessionType: string) =>
      sohApi.startSession({ storeId, sessionType }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['soh-sessions'] }); setStarting(false) },
  })

  const columns = useMemo<ColumnDef<SohSession, unknown>[]>(() => [
    { accessorKey: 'id',           header: 'Session',
      cell: i => <Link href={`/cycle-count/${i.getValue<string>()}`} className="font-mono text-xs text-blue-600 hover:underline">{i.getValue<string>().slice(-8)}</Link> },
    { accessorKey: 'sessionType',  header: 'Type' },
    { accessorKey: 'status',       header: 'Status',    cell: i => statusBadge(i.getValue<string>()) },
    { accessorKey: 'totalEpcReads', header: 'EPC Reads', cell: i => i.getValue<number>().toLocaleString() },
    { accessorKey: 'uniqueEpcCount', header: 'Unique EPCs' },
    { accessorKey: 'startedAt',    header: 'Started',   cell: i => fmtDateTime(i.getValue<string>()) },
    { accessorKey: 'completedAt',  header: 'Completed', cell: i => fmtDateTime(i.getValue<string|null>()) },
  ], [])

  const activeSession = data?.content.find(s => s.status === 'in_progress')

  return (
    <>
      <Header title="Cycle Count" />
      <div className="p-6 space-y-4">

        <div className="flex items-center justify-between">
          {activeSession ? (
            <div className="flex items-center gap-2 text-yellow-700 bg-yellow-50 border border-yellow-200 px-3 py-2 rounded-lg text-sm">
              <PlayCircle size={16} />
              Session in progress — <Link href={`/cycle-count/${activeSession.id}`} className="font-semibold underline">Resume</Link>
            </div>
          ) : (
            <p className="text-sm text-gray-500">No active session. Start a new count below.</p>
          )}

          <button
            onClick={() => setStarting(true)}
            disabled={!!activeSession}
            className="btn-primary disabled:opacity-40"
          >
            <Plus size={16} /> Start Count
          </button>
        </div>

        <div className="card">
          <DataTable
            data={data?.content ?? []}
            columns={columns}
            isLoading={isLoading}
            searchable={false}
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
