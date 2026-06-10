'use client'

import { useQuery }          from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { type ColumnDef }    from '@tanstack/react-table'
import Link                  from 'next/link'
import Header                from '@/components/layout/Header'
import DataTable             from '@/components/ui/DataTable'
import Badge                 from '@/components/ui/Badge'
import { erpImportApi }      from '@/lib/api/erp'
import { storesApi }         from '@/lib/api/stores'
import { useAuth }           from '@/lib/auth/AuthContext'
import { fmtDateTime }       from '@/lib/utils'
import type { ErpImportBatch } from '@/types'

export default function ErpImportsPage() {
  const { isAdmin } = useAuth()
  const [selectedStoreId, setSelectedStoreId] = useState('')

  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })

  const storeId = selectedStoreId || undefined

  const { data, isLoading } = useQuery({
    queryKey: ['erp-batches', storeId],
    queryFn:  () => erpImportApi.listBatches({ storeId, size: 100 }),
    enabled:  isAdmin,
  })

  const columns = useMemo<ColumnDef<ErpImportBatch, unknown>[]>(() => [
    {
      accessorKey: 'id',
      header: 'Batch',
      cell: i => (
        <Link
          href={`/erp-imports/${i.getValue<string>()}`}
          className="font-mono text-xs text-blue-600 hover:underline"
        >
          {i.getValue<string>().slice(-8)}
        </Link>
      ),
    },
    {
      accessorKey: 'createdAt',
      header: 'Date',
      cell: i => fmtDateTime(i.getValue<string>()),
    },
    {
      accessorKey: 'sourceType',
      header: 'Source',
      cell: i => (
        <Badge variant={i.getValue<string>() === 'S3' ? 'blue' : 'gray'}>
          {i.getValue<string>()}
        </Badge>
      ),
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: i => {
        const s = i.getValue<string>()
        const variant = s === 'COMPLETED' ? 'green' : s === 'FAILED' ? 'red' : s === 'PROCESSING' ? 'yellow' : 'gray'
        return <Badge variant={variant}>{s}</Badge>
      },
    },
    {
      accessorKey: 'totalRows',
      header: 'Total Rows',
      cell: i => i.getValue<number>().toLocaleString(),
    },
    {
      accessorKey: 'resolvedRows',
      header: 'Resolved',
      cell: i => i.getValue<number>().toLocaleString(),
    },
    {
      accessorKey: 'unresolvedRows',
      header: 'Unresolved',
      cell: i => {
        const n = i.getValue<number>()
        return (
          <span className={n > 0 ? 'font-semibold text-red-600' : 'text-gray-400'}>
            {n.toLocaleString()}
          </span>
        )
      },
    },
  ], [])

  if (!isAdmin) {
    return (
      <>
        <Header title="ERP Imports" />
        <div className="p-6">
          <p className="text-sm text-gray-500">Admin access required.</p>
        </div>
      </>
    )
  }

  return (
    <>
      <Header title="ERP Import Batches" />
      <div className="p-6 space-y-4">

        <div className="flex flex-wrap items-center gap-3">
          {allStores && allStores.content.length > 0 && (
            <>
              <label className="text-sm font-medium text-gray-600 shrink-0">Filter by Store</label>
              <select
                value={selectedStoreId}
                onChange={e => setSelectedStoreId(e.target.value)}
                className="text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500"
              >
                <option value="">All Stores</option>
                {allStores.content.map(s => (
                  <option key={s.id} value={s.id}>{s.name} ({s.storeCode})</option>
                ))}
              </select>
              {selectedStoreId && (
                <button
                  onClick={() => setSelectedStoreId('')}
                  className="text-xs text-gray-500 hover:text-gray-700 underline"
                >
                  Clear
                </button>
              )}
            </>
          )}
        </div>

        <div className="card">
          <DataTable
            data={data?.content ?? []}
            columns={columns}
            isLoading={isLoading}
            searchable
            searchPlaceholder="Search batches…"
          />
        </div>

      </div>
    </>
  )
}
