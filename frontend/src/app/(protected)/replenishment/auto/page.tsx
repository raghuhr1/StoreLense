'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { type ColumnDef }   from '@tanstack/react-table'
import { Zap, Loader2, CheckCircle2, AlertTriangle, TrendingDown, ArrowLeft } from 'lucide-react'
import Link                 from 'next/link'
import Header               from '@/components/layout/Header'
import DataTable            from '@/components/ui/DataTable'
import { storesApi }        from '@/lib/api/stores'
import { refillApi }        from '@/lib/api/refill'
import { replenishmentRulesApi } from '@/lib/api/inventory'
import { useAuth }          from '@/lib/auth/AuthContext'
import type { ReplenishmentSuggestion } from '@/types'

const selectCls = 'text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500'

function StatusBadge({ status }: { status: 'critical' | 'low' }) {
  return status === 'critical'
    ? <span className="inline-flex items-center gap-1 text-xs font-semibold px-2 py-0.5 rounded-full bg-red-100 text-red-800"><AlertTriangle size={10} />Critical</span>
    : <span className="inline-flex items-center gap-1 text-xs font-semibold px-2 py-0.5 rounded-full bg-amber-100 text-amber-800"><TrendingDown size={10} />Low Stock</span>
}

export default function AutoReplenishmentPage() {
  const { user, isAdmin } = useAuth()
  const qc = useQueryClient()

  const [selectedStoreId, setSelectedStoreId] = useState('')
  const [selected,        setSelected]        = useState<Set<string>>(new Set())
  const [createdCount,    setCreatedCount]    = useState<number | null>(null)
  const [createError,     setCreateError]     = useState<string | null>(null)

  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })

  const storeId = isAdmin
    ? (selectedStoreId || allStores?.content[0]?.id || '')
    : (user?.storeId ?? '')

  const { data: suggestions = [], isLoading, refetch, isFetching } = useQuery<ReplenishmentSuggestion[]>({
    queryKey: ['replenishment-suggestions', storeId],
    queryFn:  () => replenishmentRulesApi.suggest(storeId),
    enabled:  !!storeId,
  })

  const actionable = useMemo(() => suggestions.filter(s => !s.hasOpenTask), [suggestions])
  const alreadyCovered = useMemo(() => suggestions.filter(s => s.hasOpenTask), [suggestions])

  const rowKey = (s: ReplenishmentSuggestion) => `${s.locationCode}:${s.productId}`

  const toggleAll = () => {
    if (selected.size === actionable.length) setSelected(new Set())
    else setSelected(new Set(actionable.map(rowKey)))
  }

  const toggle = (s: ReplenishmentSuggestion) => {
    const k = rowKey(s)
    setSelected(prev => {
      const next = new Set(prev)
      next.has(k) ? next.delete(k) : next.add(k)
      return next
    })
  }

  const allChecked = actionable.length > 0 && selected.size === actionable.length

  // Create one refill task per selected suggestion
  const createMut = useMutation({
    mutationFn: async () => {
      const toCreate = actionable.filter(s => selected.has(rowKey(s)))
      let created = 0
      for (const s of toCreate) {
        await refillApi.createTask({
          storeId,
          taskType:  s.status === 'critical' ? 'urgency' : 'replenishment',
          priority:  s.priority,
          source:    'soh_trigger',
          notes:     `Auto-triggered: ${s.status} stock on ${s.locationCode}. Scanned ${s.scannedQty} vs par ${s.parQty}.`,
          items: [{ productId: s.productId, requestedQuantity: s.shortage }],
        })
        created++
      }
      return created
    },
    onSuccess: (count) => {
      setCreatedCount(count)
      setSelected(new Set())
      setCreateError(null)
      qc.invalidateQueries({ queryKey: ['replenishment-suggestions', storeId] })
      qc.invalidateQueries({ queryKey: ['refill-tasks'] })
    },
    onError: (err: Error) => setCreateError(err.message ?? 'Failed to create tasks'),
  })

  const columns = useMemo<ColumnDef<ReplenishmentSuggestion, unknown>[]>(() => [
    {
      id: 'select',
      header: () => (
        <input type="checkbox" checked={allChecked} onChange={toggleAll}
          className="rounded border-gray-300" disabled={actionable.length === 0} />
      ),
      cell: ({ row: r }) => (
        <input type="checkbox"
          checked={selected.has(rowKey(r.original))}
          onChange={() => toggle(r.original)}
          disabled={r.original.hasOpenTask}
          className="rounded border-gray-300 disabled:opacity-40"
        />
      ),
      size: 40,
    },
    {
      accessorFn: r => r.productName ?? r.productId,
      id: 'product',
      header: 'Product',
      cell: ({ row: r }) => (
        <div>
          <p className="text-sm font-medium text-gray-900">{r.original.productName ?? '—'}</p>
          <p className="font-mono text-xs text-gray-400">{r.original.sku ?? '—'}</p>
        </div>
      ),
    },
    {
      accessorFn: r => r.locationCode,
      id: 'location',
      header: 'Location',
      cell: ({ row: r }) => (
        <span className="text-sm text-gray-700">{r.original.locationCode === 'SALES_FLOOR' ? 'Sales Floor' : r.original.locationCode}</span>
      ),
    },
    {
      accessorKey: 'scannedQty',
      header: 'Scanned',
      cell: i => <span className="font-mono font-semibold text-gray-900">{i.getValue<number>()}</span>,
    },
    {
      accessorKey: 'parQty',
      header: 'Par',
      cell: i => <span className="font-mono text-gray-500">{i.getValue<number>()}</span>,
    },
    {
      accessorKey: 'shortage',
      header: 'Shortage',
      cell: i => <span className="font-mono font-bold text-red-600">-{i.getValue<number>()}</span>,
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: ({ getValue }) => <StatusBadge status={getValue<'critical' | 'low'>()} />,
    },
    {
      accessorKey: 'priority',
      header: 'Priority',
      cell: i => <span className="font-mono text-xs text-gray-500">P{i.getValue<number>()}</span>,
    },
    {
      accessorKey: 'hasOpenTask',
      header: 'Open Task',
      cell: ({ getValue }) => getValue<boolean>()
        ? <span className="text-xs text-green-700 bg-green-50 px-2 py-0.5 rounded-full font-medium">Covered</span>
        : <span className="text-xs text-gray-400">—</span>,
    },
  ], [selected, allChecked, actionable])  // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <>
      <Header title="Auto-Replenishment" />
      <div className="p-6 space-y-5">

        <div className="flex items-center gap-3">
          <Link href="/replenishment" className="inline-flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-800">
            <ArrowLeft size={14} /> Back to Replenishment
          </Link>
        </div>

        {/* Store selector (admin only) */}
        {isAdmin && allStores && (
          <div className="flex items-center gap-3">
            <label className="text-sm font-medium text-gray-600 shrink-0">Store</label>
            <select value={storeId} onChange={e => setSelectedStoreId(e.target.value)} className={selectCls}>
              {allStores.content.map(s => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
          </div>
        )}

        {/* Stats */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          {[
            { label: 'Total Suggestions',  value: suggestions.length,    cls: '' },
            { label: 'Actionable',         value: actionable.length,     cls: actionable.length > 0 ? 'text-red-600' : '' },
            { label: 'Already Covered',    value: alreadyCovered.length, cls: 'text-green-600' },
            { label: 'Selected',           value: selected.size,         cls: selected.size > 0 ? 'text-blue-700' : '' },
          ].map(s => (
            <div key={s.label} className="card">
              <p className="text-xs text-gray-500">{s.label}</p>
              <p className={`text-2xl font-bold mt-0.5 ${s.cls || 'text-gray-900'}`}>{s.value}</p>
            </div>
          ))}
        </div>

        {/* Banners */}
        {createdCount !== null && (
          <div className="flex items-center gap-2 p-3 bg-green-50 border border-green-200 rounded-lg text-green-800 text-sm">
            <CheckCircle2 size={16} />
            Created <strong>{createdCount}</strong> refill task{createdCount !== 1 ? 's' : ''}. They are now visible in the Replenishment queue.
            <button onClick={() => setCreatedCount(null)} className="ml-auto text-green-500">✕</button>
          </div>
        )}
        {createError && (
          <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-800 text-sm">
            {createError}
          </div>
        )}
        {suggestions.length === 0 && !isLoading && (
          <div className="card py-8 text-center">
            <CheckCircle2 size={32} className="mx-auto text-green-400 mb-2" />
            <p className="text-sm font-medium text-gray-700">Sales Floor is at or above par level</p>
            <p className="text-xs text-gray-400 mt-1">
              Based on the store&rsquo;s most recent completed SOH session. Configure Sales Floor par levels
              and replenishment rules in Store Settings to see suggestions here.
            </p>
          </div>
        )}

        {/* Suggestions table */}
        {suggestions.length > 0 && (
          <div className="card">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <Zap size={16} className="text-amber-500" />
                <h2 className="text-sm font-semibold text-gray-700">
                  Replenishment Suggestions ({suggestions.length})
                </h2>
              </div>
              <div className="flex gap-2">
                <button
                  onClick={() => refetch()}
                  disabled={isFetching}
                  className="btn-secondary text-xs"
                >
                  {isFetching ? <Loader2 size={12} className="animate-spin" /> : null}
                  Refresh
                </button>
                <button
                  onClick={() => createMut.mutate()}
                  disabled={selected.size === 0 || createMut.isPending}
                  className="btn-primary disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  {createMut.isPending
                    ? <><Loader2 size={14} className="animate-spin" /> Creating…</>
                    : <><Zap size={14} /> Create {selected.size} Task{selected.size !== 1 ? 's' : ''}</>}
                </button>
              </div>
            </div>

            <DataTable
              data={suggestions}
              columns={columns}
              isLoading={isLoading}
              searchable
              searchPlaceholder="Search product or zone…"
            />

            {alreadyCovered.length > 0 && (
              <p className="mt-3 text-xs text-gray-400">
                <span className="font-medium text-green-700">{alreadyCovered.length}</span> line{alreadyCovered.length !== 1 ? 's' : ''} already covered by open refill tasks — shown greyed out above.
              </p>
            )}
          </div>
        )}

      </div>
    </>
  )
}
