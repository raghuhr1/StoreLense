'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { type ColumnDef }    from '@tanstack/react-table'
import { GitCompare, Play, Loader2 } from 'lucide-react'
import Link                  from 'next/link'
import Header                from '@/components/layout/Header'
import DataTable             from '@/components/ui/DataTable'
import Badge                 from '@/components/ui/Badge'
import { reconciliationApi } from '@/lib/api/reconciliation'
import { sohApi }            from '@/lib/api/soh'
import { storesApi }         from '@/lib/api/stores'
import { useAuth }           from '@/lib/auth/AuthContext'
import { fmtDateTime, fmtPct } from '@/lib/utils'
import type { ReconciliationSession } from '@/types'

export default function VariancePage() {
  const { user, isAdmin } = useAuth()
  const [selectedStoreId, setSelectedStoreId] = useState('')
  const [runSessionId, setRunSessionId]       = useState('')
  const [runError, setRunError]               = useState<string | null>(null)
  const queryClient = useQueryClient()

  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })

  const storeId = isAdmin
    ? (selectedStoreId || allStores?.content[0]?.id || '')
    : (user?.storeId ?? '')

  const { data, isLoading } = useQuery({
    queryKey: ['reconciliation-sessions', storeId],
    queryFn:  () => reconciliationApi.listByStore(storeId, { size: 100 }),
    enabled:  !!storeId,
  })

  // Completed SOH sessions not yet reconciled — for the manual "Run" dropdown
  const { data: completedSessions } = useQuery({
    queryKey: ['soh-completed', storeId],
    queryFn:  () => sohApi.listSessions(storeId, { status: 'completed', size: 50 }),
    enabled:  !!storeId,
  })

  const runMutation = useMutation({
    mutationFn: (id: string) => reconciliationApi.run(id),
    onSuccess: () => {
      setRunError(null)
      setRunSessionId('')
      queryClient.invalidateQueries({ queryKey: ['reconciliation-sessions', storeId] })
    },
    onError: (e: Error) => setRunError(e.message ?? 'Reconciliation failed'),
  })

  const columns = useMemo<ColumnDef<ReconciliationSession, unknown>[]>(() => [
    {
      accessorKey: 'sessionId',
      header: 'Session',
      cell: i => (
        <Link
          href={`/variance/${i.getValue<string>()}`}
          className="font-mono text-xs text-blue-600 hover:underline"
        >
          {i.getValue<string>().slice(-8)}
        </Link>
      ),
    },
    {
      accessorKey: 'runAt',
      header: 'Run Date',
      cell: i => fmtDateTime(i.getValue<string>()),
    },
    {
      accessorKey: 'accuracyPct',
      header: 'Matched %',
      cell: i => {
        const pct = i.getValue<number | null>()
        const color = pct == null ? 'text-gray-400'
          : pct >= 95 ? 'text-green-600'
          : pct >= 80 ? 'text-yellow-600'
          : 'text-red-600'
        return <span className={`font-semibold ${color}`}>{fmtPct(pct)}</span>
      },
    },
    {
      accessorKey: 'matchedCount',
      header: 'Matched',
      cell: i => i.getValue<number>().toLocaleString(),
    },
    {
      accessorKey: 'missingCount',
      header: 'Missing',
      cell: i => {
        const n = i.getValue<number>()
        return <span className={n > 0 ? 'text-red-600 font-medium' : 'text-gray-500'}>{n}</span>
      },
    },
    {
      accessorKey: 'extraCount',
      header: 'Extra',
      cell: i => {
        const n = i.getValue<number>()
        return <span className={n > 0 ? 'text-yellow-600 font-medium' : 'text-gray-500'}>{n}</span>
      },
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: i => {
        const s = i.getValue<string>()
        const variant = s === 'COMPLETED' ? 'green' : s === 'FAILED' ? 'red' : 'yellow'
        return <Badge variant={variant}>{s}</Badge>
      },
    },
  ], [])

  return (
    <>
      <Header title="Variance Report" />
      <div className="p-6 space-y-4">

        {/* Top bar — store picker + manual run trigger */}
        <div className="flex flex-wrap items-center gap-3">
          {isAdmin && allStores && allStores.content.length > 0 && (
            <>
              <label className="text-sm font-medium text-gray-600 shrink-0">Store</label>
              <select
                value={storeId}
                onChange={e => { setSelectedStoreId(e.target.value); setRunSessionId('') }}
                className="text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500"
              >
                {allStores.content.map(s => (
                  <option key={s.id} value={s.id}>{s.name} ({s.storeCode})</option>
                ))}
              </select>
            </>
          )}

          {/* Manual reconciliation trigger */}
          {completedSessions && completedSessions.content.length > 0 && (
            <div className="flex items-center gap-2 ml-auto">
              <label className="text-sm font-medium text-gray-600 shrink-0">Run for session</label>
              <select
                value={runSessionId}
                onChange={e => { setRunSessionId(e.target.value); setRunError(null) }}
                className="text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500 max-w-[220px]"
              >
                <option value="">— pick a completed session —</option>
                {completedSessions.content.map(s => (
                  <option key={s.id} value={s.id}>
                    {s.id.slice(-8)} · {fmtDateTime(s.startedAt)}
                  </option>
                ))}
              </select>
              <button
                disabled={!runSessionId || runMutation.isPending}
                onClick={() => runSessionId && runMutation.mutate(runSessionId)}
                className="flex items-center gap-1.5 text-sm px-3 py-1.5 rounded-lg bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-40 transition-colors"
              >
                {runMutation.isPending
                  ? <Loader2 size={14} className="animate-spin" />
                  : <Play size={14} />}
                Run
              </button>
            </div>
          )}
        </div>

        {/* Error from manual run */}
        {runError && (
          <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-4 py-2">
            {runError}
          </p>
        )}

        {/* Success message */}
        {runMutation.isSuccess && (
          <p className="text-sm text-green-700 bg-green-50 border border-green-200 rounded-lg px-4 py-2">
            Reconciliation complete — results added to the table below.
          </p>
        )}

        {!isLoading && data?.content.length === 0 && (
          <p className="text-sm text-gray-400 flex items-center gap-2">
            <GitCompare size={14} />
            No reconciliation runs yet. Complete a SOH session — reconciliation runs automatically,
            or pick a session above and click Run.
          </p>
        )}

        <div className="card">
          <DataTable
            data={data?.content ?? []}
            columns={columns}
            isLoading={isLoading}
            searchable
            searchPlaceholder="Search sessions…"
          />
        </div>

      </div>
    </>
  )
}
