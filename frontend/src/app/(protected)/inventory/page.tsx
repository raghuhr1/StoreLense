'use client'

import { useQuery }      from '@tanstack/react-query'
import { useMemo }       from 'react'
import { type ColumnDef } from '@tanstack/react-table'
import Link              from 'next/link'
import Header            from '@/components/layout/Header'
import DataTable         from '@/components/ui/DataTable'
import StatCard          from '@/components/ui/StatCard'
import { Package, AlertTriangle, TrendingDown } from 'lucide-react'
import { inventoryApi } from '@/lib/api/inventory'
import { useAuth }      from '@/lib/auth/AuthContext'
import { fmtPct, fmtDateTime, accuracyColor } from '@/lib/utils'
import type { InventoryState } from '@/types'

export default function InventoryPage() {
  const { user }   = useAuth()
  const storeId    = user?.storeId ?? ''

  const { data: items, isLoading } = useQuery({
    queryKey: ['inventory', storeId],
    queryFn:  () => inventoryApi.getState(storeId),
    enabled:  !!storeId,
  })

  const { data: summary } = useQuery({
    queryKey: ['epc-summary', storeId],
    queryFn:  () => inventoryApi.epcSummary(storeId),
    enabled:  !!storeId,
  })

  const { data: lowAcc } = useQuery({
    queryKey: ['low-accuracy', storeId],
    queryFn:  () => inventoryApi.getLowAccuracy(storeId, 95),
    enabled:  !!storeId,
  })

  const columns = useMemo<ColumnDef<InventoryState, unknown>[]>(() => [
    { accessorKey: 'productId',       header: 'Product ID', cell: i => <Link href={`/inventory/${i.getValue<string>()}`} className="font-mono text-xs text-blue-600 hover:underline">{i.getValue<string>().slice(-8)}</Link> },
    { accessorKey: 'quantityOnHand',  header: 'On Hand',   cell: i => <span className="font-semibold">{i.getValue<number>()}</span> },
    { accessorKey: 'quantityExpected', header: 'Expected' },
    {
      accessorKey: 'accuracyPct',
      header: 'Accuracy',
      cell: i => {
        const v = i.getValue<number | null>()
        return <span className={accuracyColor(v)}>{fmtPct(v)}</span>
      },
    },
    { accessorKey: 'lastCountedAt', header: 'Last Counted', cell: i => fmtDateTime(i.getValue<string|null>()) },
  ], [])

  return (
    <>
      <Header title="Inventory" />
      <div className="p-6 space-y-6">

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard title="Total SKUs" value={items?.length ?? 0}       icon={Package}       color="blue" />
          <StatCard title="In Store (EPC)"  value={summary?.in_store ?? 0}  icon={Package}       color="green" />
          <StatCard title="Missing (EPC)"   value={summary?.missing ?? 0}   icon={AlertTriangle} color="red" />
          <StatCard title="Low Accuracy"    value={lowAcc?.length ?? 0}     icon={TrendingDown}  color="yellow" />
        </div>

        <div className="card">
          <h2 className="text-sm font-semibold text-gray-700 mb-4">Inventory State by Product</h2>
          <DataTable
            data={items ?? []}
            columns={columns}
            isLoading={isLoading}
            searchable
            searchPlaceholder="Search products…"
          />
        </div>

      </div>
    </>
  )
}
