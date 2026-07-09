'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { use, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { type ColumnDef }    from '@tanstack/react-table'
import {
  Download, Play, CheckCircle, AlertTriangle, Loader2,
  BarChart3, MapPin, ThumbsUp,
} from 'lucide-react'
import Link                  from 'next/link'
import Header                from '@/components/layout/Header'
import DataTable             from '@/components/ui/DataTable'
import StatCard              from '@/components/ui/StatCard'
import Badge, { statusBadge } from '@/components/ui/Badge'
import { reconciliationApi } from '@/lib/api/reconciliation'
import { cycleCountApi }     from '@/lib/api/cycle-count'
import { fmtDateTime, fmtPct, fmt } from '@/lib/utils'
import type { CycleCountReconciliation, ReconciliationItemWithLocation } from '@/types'

const LOCATION_OPTS = [
  { value: '',            label: 'All Locations' },
  { value: 'SALES_FLOOR', label: 'Sales Floor'   },
  { value: 'BACKROOM',    label: 'Backroom'       },
]

export default function CycleCountReconcilePage({ params }: { params: Promise<{ id: string }> }) {
  const { id }   = use(params)
  const qc       = useQueryClient()
  const [locFilter, setLocFilter] = useState('')

  const { data: count } = useQuery({
    queryKey: ['cycle-count', id],
    queryFn:  () => cycleCountApi.get(id),
  })

  const { data: result, isLoading: resultLoading } = useQuery({
    queryKey: ['cc-reconciliation', id],
    queryFn:  () => reconciliationApi.getCountResult(id),
    retry:    false,
  })

  const { data: items, isLoading: itemsLoading } = useQuery({
    queryKey: ['cc-reconciliation-items', id, locFilter],
    queryFn:  () => reconciliationApi.getCountItems(id, locFilter ? { location: locFilter } : undefined),
    enabled:  !!result && result.status !== 'RUNNING',
  })

  const runMut = useMutation({
    mutationFn: () => reconciliationApi.runForCount(id),
    onSuccess:  () => {
      qc.invalidateQueries({ queryKey: ['cc-reconciliation', id] })
      qc.invalidateQueries({ queryKey: ['cc-reconciliation-items', id] })
      qc.invalidateQueries({ queryKey: ['cycle-count', id] })
    },
  })

  const approveMut = useMutation({
    mutationFn: () => reconciliationApi.approveCount(id),
    onSuccess:  () => qc.invalidateQueries({ queryKey: ['cc-reconciliation', id] }),
  })

  const handleCsvDownload = useCallback(async () => {
    const blob = await reconciliationApi.downloadCountCsv(id)
    const url  = URL.createObjectURL(blob)
    const a    = document.createElement('a')
    a.href     = url
    a.download = `cycle-count-${id.slice(-8)}.csv`
    a.click()
    URL.revokeObjectURL(url)
  }, [id])

  // Auto-run reconciliation the first time this page is opened with no prior result
  const autoRan = useRef(false)
  useEffect(() => {
    if (autoRan.current || resultLoading || result !== undefined) return
    autoRan.current = true
    runMut.mutate()
  }, [resultLoading, result]) // eslint-disable-line react-hooks/exhaustive-deps

  const canApprove = result?.status === 'PENDING_APPROVAL' || result?.status === 'COMPLETED'
  const hasResult  = !!result && result.status !== 'RUNNING'

  const columns = useMemo<ColumnDef<ReconciliationItemWithLocation, unknown>[]>(() => [
    {
      accessorKey: 'epc',
      header: 'EPC',
      cell: i => (
        <span className="font-mono text-xs text-gray-700 break-all">{i.getValue<string>()}</span>
      ),
    },
    {
      accessorKey: 'ean',
      header: 'SKU / EAN',
      cell: i => i.getValue<string | null>() ?? <span className="text-gray-300">—</span>,
    },
    {
      accessorKey: 'locationCode',
      header: 'Location',
      cell: i => {
        const lc = i.getValue<string | null>()
        const sc = (i.row.original as ReconciliationItemWithLocation).sectionCode
        if (!lc) return <span className="text-gray-300">—</span>
        return (
          <span className="flex items-center gap-1 text-xs">
            <MapPin size={11} className="text-gray-400" />
            {lc === 'SALES_FLOOR' ? (sc ? `Floor / ${sc}` : 'Floor') : 'Backroom'}
          </span>
        )
      },
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: i => {
        const s = i.getValue<string>()
        const v = s === 'MATCH' ? 'green' : s === 'MISSING' ? 'red' : 'yellow'
        return <Badge variant={v}>{s}</Badge>
      },
    },
    {
      accessorKey: 'expectedQty',
      header: 'Expected',
    },
    {
      accessorKey: 'scannedQty',
      header: 'Scanned',
      cell: i => {
        const exp = (i.row.original as ReconciliationItemWithLocation).expectedQty
        const sc  = i.getValue<number>()
        const cls = sc === exp ? '' : sc > exp ? 'text-yellow-600' : 'text-red-600'
        return <span className={`font-medium ${cls}`}>{sc}</span>
      },
    },
  ], [])

  return (
    <>
      <Header title="Cycle Count Reconciliation" />
      <div className="p-6 space-y-6">

        <div className="flex items-center gap-2 text-sm">
          <Link href="/cycle-count" className="text-brand-600 hover:underline">Cycle Counts</Link>
          <span className="text-gray-400">/</span>
          <Link href={`/cycle-count/${id}`} className="text-brand-600 hover:underline">
            {count ? fmt(count.countDate) : id.slice(-8)}
          </Link>
          <span className="text-gray-400">/</span>
          <span className="text-gray-700">Reconciliation</span>
        </div>

        {/* Run + status */}
        <div className="card flex flex-wrap items-center justify-between gap-4">
          <div>
            <h2 className="text-sm font-semibold text-gray-900">ERP Reconciliation</h2>
            {result ? (
              <p className="text-xs text-gray-500 mt-0.5">
                Last run: {fmtDateTime(result.runAt)}
                {' · '}{statusBadge(result.status)}
              </p>
            ) : (
              <p className="text-xs text-gray-500 mt-0.5">No reconciliation run yet.</p>
            )}
          </div>

          <div className="flex gap-2">
            {hasResult && (
              <button
                onClick={handleCsvDownload}
                className="flex items-center gap-2 text-sm px-3 py-1.5 border border-gray-200 rounded-lg bg-white hover:bg-gray-50 transition-colors text-gray-700"
              >
                <Download size={14} /> Download CSV
              </button>
            )}

            {canApprove && (
              <button
                onClick={() => approveMut.mutate()}
                disabled={approveMut.isPending}
                className="flex items-center gap-2 text-sm px-4 py-1.5 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors disabled:opacity-40"
              >
                {approveMut.isPending
                  ? <Loader2 size={14} className="animate-spin" />
                  : <ThumbsUp size={14} />}
                {approveMut.isPending ? 'Approving…' : 'Approve'}
              </button>
            )}

            <button
              onClick={() => runMut.mutate()}
              disabled={runMut.isPending}
              className="flex items-center gap-2 text-sm px-4 py-1.5 bg-brand-600 text-white rounded-lg hover:bg-brand-700 transition-colors disabled:opacity-40"
            >
              {runMut.isPending
                ? <Loader2 size={14} className="animate-spin" />
                : <Play size={14} />}
              {runMut.isPending ? 'Running…' : result ? 'Re-run' : 'Run Reconciliation'}
            </button>
          </div>
        </div>

        {runMut.isError && (
          <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-4 py-2">
            Reconciliation failed. Ensure the cycle count is in UPLOADED status and an ERP snapshot exists.
          </p>
        )}

        {approveMut.isSuccess && (
          <p className="text-sm text-green-700 bg-green-50 border border-green-200 rounded-lg px-4 py-2 flex items-center gap-2">
            <CheckCircle size={15} /> Reconciliation approved.
          </p>
        )}

        {/* Overall stats */}
        {hasResult && result && (
          <>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <StatCard
                title="Match Rate"
                value={fmtPct(result.accuracyPct)}
                icon={CheckCircle}
                color={result.accuracyPct == null ? 'blue' : result.accuracyPct >= 95 ? 'green' : result.accuracyPct >= 80 ? 'yellow' : 'red'}
                sub={`${result.matchedCount.toLocaleString()} of ${result.totalExpected.toLocaleString()} expected`}
              />
              <StatCard
                title="Missing"
                value={result.missingCount.toLocaleString()}
                icon={AlertTriangle}
                color={result.missingCount === 0 ? 'green' : 'red'}
                sub="Expected but not scanned"
              />
              <StatCard
                title="Extra"
                value={result.extraCount.toLocaleString()}
                icon={BarChart3}
                color={result.extraCount === 0 ? 'green' : 'yellow'}
                sub="Scanned but not expected"
              />
            </div>

            {/* Floor / Backroom breakdown */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="card">
                <h3 className="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-2">
                  <MapPin size={14} className="text-brand-500" /> Sales Floor
                </h3>
                <div className="grid grid-cols-3 gap-3">
                  {[
                    { label: 'Expected', value: result.floorExpected },
                    { label: 'Scanned',  value: result.floorScanned  },
                    { label: 'Missing',  value: result.floorMissing, red: result.floorMissing > 0 },
                  ].map(({ label, value, red }) => (
                    <div key={label} className="bg-blue-50 rounded-lg p-3 text-center">
                      <p className="text-[10px] text-blue-400 uppercase tracking-wide">{label}</p>
                      <p className={`text-lg font-bold mt-0.5 ${red ? 'text-red-600' : 'text-blue-900'}`}>
                        {value.toLocaleString()}
                      </p>
                    </div>
                  ))}
                </div>
              </div>

              <div className="card">
                <h3 className="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-2">
                  <MapPin size={14} className="text-amber-500" /> Backroom
                </h3>
                <div className="grid grid-cols-3 gap-3">
                  {[
                    { label: 'Expected', value: result.backroomExpected },
                    { label: 'Scanned',  value: result.backroomScanned  },
                    { label: 'Missing',  value: result.backroomMissing, red: result.backroomMissing > 0 },
                  ].map(({ label, value, red }) => (
                    <div key={label} className="bg-amber-50 rounded-lg p-3 text-center">
                      <p className="text-[10px] text-amber-400 uppercase tracking-wide">{label}</p>
                      <p className={`text-lg font-bold mt-0.5 ${red ? 'text-red-600' : 'text-amber-900'}`}>
                        {value.toLocaleString()}
                      </p>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* Item table */}
            <div className="card">
              <div className="flex items-center justify-between mb-4 flex-wrap gap-3">
                <h2 className="text-sm font-semibold text-gray-700">
                  Line Items
                  {items && <span className="ml-1.5 text-gray-400 font-normal">({items.length.toLocaleString()})</span>}
                </h2>
                <select
                  value={locFilter}
                  onChange={e => setLocFilter(e.target.value)}
                  className="text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500"
                >
                  {LOCATION_OPTS.map(o => (
                    <option key={o.value} value={o.value}>{o.label}</option>
                  ))}
                </select>
              </div>

              <DataTable
                data={items ?? []}
                columns={columns}
                isLoading={itemsLoading}
                searchable
                searchPlaceholder="Filter by EPC or EAN…"
              />
            </div>
          </>
        )}

        {resultLoading && (
          <div className="flex justify-center py-12">
            <div className="w-8 h-8 border-4 border-brand-600 border-t-transparent rounded-full animate-spin" />
          </div>
        )}

      </div>
    </>
  )
}
