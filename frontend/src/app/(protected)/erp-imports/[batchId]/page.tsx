'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { use, useMemo, useState }    from 'react'
import { type ColumnDef }            from '@tanstack/react-table'
import { RefreshCw, CheckCircle2 }   from 'lucide-react'
import Link                          from 'next/link'
import Header                        from '@/components/layout/Header'
import DataTable                     from '@/components/ui/DataTable'
import Badge                         from '@/components/ui/Badge'
import { erpImportApi }              from '@/lib/api/erp'
import { fmtDateTime }               from '@/lib/utils'
import type { ErpSohSnapshot }       from '@/types'

export default function ErpImportDetailPage({ params }: { params: Promise<{ batchId: string }> }) {
  const { batchId }  = use(params)
  const qc           = useQueryClient()
  const [reResolveResult, setReResolveResult] = useState<{ resolved: number; unresolved: number } | null>(null)

  const { data: detail, isLoading } = useQuery({
    queryKey: ['erp-batch', batchId],
    queryFn:  () => erpImportApi.getBatch(batchId),
  })

  const { data: unresolved, isLoading: unresolvedLoading } = useQuery({
    queryKey: ['erp-unresolved', batchId],
    queryFn:  () => erpImportApi.getUnresolved(batchId),
    enabled:  !!detail,
  })

  const reResolveMut = useMutation({
    mutationFn: () => erpImportApi.reResolve(batchId),
    onSuccess: (data) => {
      setReResolveResult({ resolved: data.resolved, unresolved: data.unresolved })
      qc.invalidateQueries({ queryKey: ['erp-batch', batchId] })
      qc.invalidateQueries({ queryKey: ['erp-unresolved', batchId] })
    },
  })

  const columns = useMemo<ColumnDef<ErpSohSnapshot, unknown>[]>(() => [
    {
      accessorKey: 'ean',
      header: 'EAN',
      cell: i => <span className="font-mono text-xs">{i.getValue<string>()}</span>,
    },
    {
      accessorKey: 'expectedQty',
      header: 'Expected Qty',
      cell: i => i.getValue<number>(),
    },
    {
      accessorKey: 'zoneRegion',
      header: 'Zone Region',
      cell: i => i.getValue<string | null>() ?? <span className="text-gray-300">—</span>,
    },
    {
      accessorKey: 'resolutionStatus',
      header: 'Resolution',
      cell: i => {
        const s = i.getValue<string>()
        const variant = s === 'RESOLVED' ? 'green' : s === 'UNRESOLVED' ? 'red' : s === 'PARTIAL' ? 'yellow' : 'gray'
        return <Badge variant={variant}>{s}</Badge>
      },
    },
    {
      accessorKey: 'createdAt',
      header: 'Created',
      cell: i => fmtDateTime(i.getValue<string>()),
    },
  ], [])

  if (isLoading) {
    return (
      <>
        <Header title="ERP Import Detail" />
        <div className="p-6 flex justify-center">
          <div className="w-8 h-8 border-4 border-brand-600 border-t-transparent rounded-full animate-spin" />
        </div>
      </>
    )
  }

  if (!detail) return null

  const { batch, unresolvedCount } = detail

  return (
    <>
      <Header title={`ERP Import — ${batchId.slice(-8)}`} />
      <div className="p-6 space-y-6">

        <Link href="/erp-imports" className="text-sm text-brand-600 hover:underline">
          ← All import batches
        </Link>

        {/* Batch stats */}
        <div className="card">
          <div className="flex items-start justify-between mb-4">
            <div>
              <p className="text-xs text-gray-400 font-mono">{batchId}</p>
              <p className="text-sm text-gray-500 mt-1">
                Source: <span className="font-medium text-gray-700">{batch.sourceType}</span>
                {batch.filePath && (
                  <span className="ml-2 text-gray-400 truncate max-w-xs inline-block align-bottom">
                    {batch.filePath}
                  </span>
                )}
              </p>
            </div>
            <Badge variant={
              batch.status === 'COMPLETED'  ? 'green'  :
              batch.status === 'FAILED'     ? 'red'    :
              batch.status === 'PROCESSING' ? 'yellow' : 'gray'
            }>
              {batch.status}
            </Badge>
          </div>

          <dl className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            {[
              { label: 'Total Rows',   value: batch.totalRows.toLocaleString() },
              { label: 'Resolved',     value: batch.resolvedRows.toLocaleString() },
              { label: 'Unresolved',   value: batch.unresolvedRows.toLocaleString() },
              { label: 'Imported At',  value: fmtDateTime(batch.importedAt) },
            ].map(({ label, value }) => (
              <div key={label} className="bg-gray-50 rounded-lg p-3">
                <dt className="text-xs text-gray-500">{label}</dt>
                <dd className="text-sm font-semibold text-gray-900 mt-1">{value}</dd>
              </div>
            ))}
          </dl>

          {batch.errorMessage && (
            <p className="mt-4 text-xs text-red-700 bg-red-50 px-3 py-2 rounded-lg">
              {batch.errorMessage}
            </p>
          )}
        </div>

        {/* RE-RESOLVE action */}
        {unresolvedCount > 0 && (
          <div className="flex items-center gap-4">
            <button
              onClick={() => reResolveMut.mutate()}
              disabled={reResolveMut.isPending}
              className="btn-primary disabled:opacity-40"
            >
              <RefreshCw size={15} className={reResolveMut.isPending ? 'animate-spin' : ''} />
              {reResolveMut.isPending ? 'Re-resolving…' : `RE-RESOLVE ${unresolvedCount} EANs`}
            </button>

            {reResolveResult && (
              <div className="flex items-center gap-2 text-sm text-green-700 bg-green-50 border border-green-200 px-3 py-2 rounded-lg">
                <CheckCircle2 size={15} />
                {reResolveResult.resolved} resolved · {reResolveResult.unresolved} still unresolved
              </div>
            )}

            {reResolveMut.isError && (
              <p className="text-sm text-red-600">Re-resolve failed — check logs.</p>
            )}
          </div>
        )}

        {/* Unresolved EAN table */}
        <div className="card">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-semibold text-gray-700">
              Unresolved EANs
              {unresolved && (
                <span className="ml-2 text-gray-400 font-normal">({unresolved.length})</span>
              )}
            </h2>
          </div>

          <DataTable
            data={unresolved ?? []}
            columns={columns}
            isLoading={unresolvedLoading}
            searchable
            searchPlaceholder="Filter by EAN or zone…"
          />
        </div>

      </div>
    </>
  )
}
