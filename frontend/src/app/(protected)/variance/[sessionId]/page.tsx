'use client'

import { useQuery }                  from '@tanstack/react-query'
import { use, useCallback }          from 'react'
import { type ColumnDef }            from '@tanstack/react-table'
import { useMemo }                   from 'react'
import { Download, CheckCircle2, AlertTriangle, Plus } from 'lucide-react'
import Link                          from 'next/link'
import Header                        from '@/components/layout/Header'
import DataTable                     from '@/components/ui/DataTable'
import StatCard                      from '@/components/ui/StatCard'
import Badge                         from '@/components/ui/Badge'
import { reconciliationApi }         from '@/lib/api/reconciliation'
import { fmtDateTime, fmtPct }       from '@/lib/utils'
import type { ReconciliationItem }   from '@/types'

export default function VarianceDetailPage({ params }: { params: Promise<{ sessionId: string }> }) {
  const { sessionId } = use(params)

  const { data: result, isLoading: resultLoading } = useQuery({
    queryKey: ['reconciliation-result', sessionId],
    queryFn:  () => reconciliationApi.getResult(sessionId),
  })

  const { data: items, isLoading: itemsLoading } = useQuery({
    queryKey: ['reconciliation-items', sessionId],
    queryFn:  () => reconciliationApi.getItems(sessionId),
    enabled:  !!result,
  })

  const handleCsvDownload = useCallback(async () => {
    const blob = await reconciliationApi.downloadCsv(sessionId)
    const url  = URL.createObjectURL(blob)
    const a    = document.createElement('a')
    a.href     = url
    a.download = `reconciliation-${sessionId.slice(-8)}.csv`
    a.click()
    URL.revokeObjectURL(url)
  }, [sessionId])

  const columns = useMemo<ColumnDef<ReconciliationItem, unknown>[]>(() => [
    {
      accessorKey: 'epc',
      header: 'EPC',
      cell: i => (
        <span className="font-mono text-xs text-gray-700 break-all">
          {i.getValue<string>()}
        </span>
      ),
    },
    {
      accessorKey: 'ean',
      header: 'SKU / EAN',
      cell: i => i.getValue<string | null>() ?? <span className="text-gray-300">—</span>,
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: i => {
        const s = i.getValue<string>()
        const variant = s === 'MATCH' ? 'green' : s === 'MISSING' ? 'red' : 'yellow'
        return <Badge variant={variant}>{s}</Badge>
      },
    },
    {
      accessorKey: 'expectedQty',
      header: 'Expected Qty',
      cell: i => i.getValue<number>(),
    },
    {
      accessorKey: 'scannedQty',
      header: 'Scanned Qty',
      cell: i => {
        const expected = (i.row.original as ReconciliationItem).expectedQty
        const scanned  = i.getValue<number>()
        const color    = scanned === expected ? '' : scanned > expected ? 'text-yellow-600' : 'text-red-600'
        return <span className={`font-medium ${color}`}>{scanned}</span>
      },
    },
  ], [])

  if (resultLoading) {
    return (
      <>
        <Header title="Variance Detail" />
        <div className="p-6 flex justify-center">
          <div className="w-8 h-8 border-4 border-brand-600 border-t-transparent rounded-full animate-spin" />
        </div>
      </>
    )
  }

  if (!result) return null

  const matchPct  = result.accuracyPct
  const statusOk  = result.status === 'COMPLETED'

  return (
    <>
      <Header title={`Variance — session ${sessionId.slice(-8)}`} />
      <div className="p-6 space-y-6">

        {/* Back link */}
        <Link href="/variance" className="text-sm text-brand-600 hover:underline">
          ← All variance runs
        </Link>

        {/* Status banner for non-completed runs */}
        {!statusOk && (
          <div className="flex items-center gap-2 bg-yellow-50 border border-yellow-200 text-yellow-800 rounded-lg px-4 py-3 text-sm">
            <AlertTriangle size={16} />
            Reconciliation status: <span className="font-semibold ml-1">{result.status}</span>
          </div>
        )}

        {/* Stat cards */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <StatCard
            title="Matched %"
            value={fmtPct(matchPct)}
            icon={CheckCircle2}
            color={matchPct == null ? 'blue' : matchPct >= 95 ? 'green' : matchPct >= 80 ? 'yellow' : 'red'}
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
            icon={Plus}
            color={result.extraCount === 0 ? 'green' : 'yellow'}
            sub="Scanned but not expected"
          />
        </div>

        {/* Items table */}
        <div className="card">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-semibold text-gray-700">
              Line Items
              {items && (
                <span className="ml-2 text-gray-400 font-normal">
                  ({items.length.toLocaleString()})
                </span>
              )}
            </h2>
            <button
              onClick={handleCsvDownload}
              className="flex items-center gap-2 text-sm px-3 py-1.5 border border-gray-200 rounded-lg bg-white hover:bg-gray-50 transition-colors text-gray-700"
            >
              <Download size={14} />
              Download CSV
            </button>
          </div>

          <DataTable
            data={items ?? []}
            columns={columns}
            isLoading={itemsLoading}
            searchable
            searchPlaceholder="Filter by EPC or EAN…"
          />
        </div>

      </div>
    </>
  )
}
