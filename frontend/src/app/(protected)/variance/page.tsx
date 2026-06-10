'use client'

import { useQuery }          from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { type ColumnDef }    from '@tanstack/react-table'
import { GitCompare }        from 'lucide-react'
import Link                  from 'next/link'
import Header                from '@/components/layout/Header'
import DataTable             from '@/components/ui/DataTable'
import Badge                 from '@/components/ui/Badge'
import { reconciliationApi } from '@/lib/api/reconciliation'
import { storesApi }         from '@/lib/api/stores'
import { useAuth }           from '@/lib/auth/AuthContext'
import { fmtDateTime, fmtPct } from '@/lib/utils'
import type { ReconciliationSession } from '@/types'

export default function VariancePage() {
  const { user, isAdmin } = useAuth()
  const [selectedStoreId, setSelectedStoreId] = useState('')

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

        <div className="flex flex-wrap items-center gap-3">
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

          {!isLoading && data?.content.length === 0 && (
            <p className="text-sm text-gray-400 flex items-center gap-2">
              <GitCompare size={14} />
              No reconciliation runs yet — complete a cycle count and run reconciliation.
            </p>
          )}
        </div>

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
