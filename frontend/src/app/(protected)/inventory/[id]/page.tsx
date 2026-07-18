'use client'

import { useQuery }       from '@tanstack/react-query'
import { use, useMemo }   from 'react'
import { type ColumnDef } from '@tanstack/react-table'
import { Package, Tag, ArrowLeft, AlertTriangle } from 'lucide-react'
import Link               from 'next/link'
import Header             from '@/components/layout/Header'
import DataTable          from '@/components/ui/DataTable'
import StatCard           from '@/components/ui/StatCard'
import { inventoryApi }   from '@/lib/api/inventory'
import { productsApi }    from '@/lib/api/products'
import { useAuth }        from '@/lib/auth/AuthContext'
import { fmtPct, fmtDateTime, accuracyColor } from '@/lib/utils'
import type { InventoryState } from '@/types'

export default function InventoryDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id }   = use(params)
  const { user } = useAuth()
  const storeId  = user?.storeId ?? ''

  const { data: product } = useQuery({
    queryKey: ['product', id],
    queryFn:  () => productsApi.get(id),
  })

  const { data: stateItems, isLoading } = useQuery({
    queryKey: ['inventory', storeId],
    queryFn:  () => inventoryApi.getState(storeId),
    enabled:  !!storeId,
    select:   items => items.filter(i => i.productId === id),
  })

  const totalOnHand   = stateItems?.reduce((s, i) => s + i.quantityOnHand, 0) ?? 0
  const totalExpected = stateItems?.reduce((s, i) => s + i.quantityExpected, 0) ?? 0
  // Average each zone row's own accuracy rather than summing raw units — a units ratio
  // conflates "% scanned so far" with true accuracy. Computed live rather than read from
  // the stored accuracyPct, so legacy rows never backfilled with a real value aren't
  // silently excluded — a zone ERP expects but that's never been scanned is a real 0%.
  const overallAcc = stateItems && stateItems.length > 0
    ? stateItems.reduce((s, i) => {
        const acc = i.quantityExpected === 0
          ? (i.quantityOnHand === 0 ? 100 : 0)
          : Math.min(100, 100 * i.quantityOnHand / i.quantityExpected)
        return s + acc
      }, 0) / stateItems.length
    : null
  const lowAccZones   = stateItems?.filter(i => (i.accuracyPct ?? 100) < 95).length ?? 0

  const columns = useMemo<ColumnDef<InventoryState, unknown>[]>(() => [
    {
      accessorKey: 'zoneId',
      header: 'Zone',
      cell: i => {
        const v = i.getValue<string | null>()
        return v ? <span className="font-mono text-xs">{v.slice(-8)}</span> : <span className="text-gray-400">Unzoned</span>
      },
    },
    {
      accessorKey: 'quantityOnHand',
      header: 'On Hand',
      cell: i => <span className="font-semibold">{i.getValue<number>()}</span>,
    },
    { accessorKey: 'quantityExpected', header: 'Expected' },
    {
      accessorKey: 'accuracyPct',
      header: 'Accuracy',
      cell: i => {
        const v = i.getValue<number | null>()
        return <span className={accuracyColor(v)}>{fmtPct(v)}</span>
      },
    },
    {
      accessorKey: 'lastCountedAt',
      header: 'Last Counted',
      cell: i => fmtDateTime(i.getValue<string | null>()),
    },
  ], [])

  return (
    <>
      <Header title={product ? `${product.sku} — ${product.name}` : 'Product Detail'} />
      <div className="p-6 space-y-6">

        <Link href="/inventory" className="inline-flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-700 transition-colors">
          <ArrowLeft size={15} /> Back to Inventory
        </Link>

        {product && (
          <div className="card">
            <h2 className="text-sm font-semibold text-gray-700 mb-4">Product Details</h2>
            <dl className="grid grid-cols-2 sm:grid-cols-4 gap-4">
              {[
                ['SKU',          product.sku],
                ['Name',         product.name],
                ['Brand',        product.brand ?? '—'],
                ['Unit',         product.unitOfMeasure],
                ['RFID Enabled', product.rfidEnabled ? 'Yes' : 'No'],
                ['Status',       product.active ? 'Active' : 'Inactive'],
                ['Description',  product.description ?? '—'],
              ].map(([label, value]) => (
                <div key={label} className="bg-gray-50 rounded-lg p-3">
                  <dt className="text-xs text-gray-500">{label}</dt>
                  <dd className="text-sm font-medium text-gray-900 mt-0.5">{value}</dd>
                </div>
              ))}
            </dl>
          </div>
        )}

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard
            title="Total On Hand"
            value={totalOnHand}
            icon={Package}
            color="blue"
          />
          <StatCard
            title="Total Expected"
            value={totalExpected}
            icon={Package}
            color="green"
          />
          <StatCard
            title="Overall Accuracy"
            value={fmtPct(overallAcc)}
            icon={Tag}
            color={overallAcc != null && overallAcc >= 98 ? 'green' : overallAcc != null && overallAcc >= 95 ? 'yellow' : 'red'}
          />
          <StatCard
            title="Low Accuracy Zones"
            value={lowAccZones}
            icon={AlertTriangle}
            color={lowAccZones > 0 ? 'red' : 'green'}
          />
        </div>

        <div className="card">
          <h3 className="text-sm font-semibold text-gray-700 mb-4">Inventory by Zone</h3>
          <DataTable
            data={stateItems ?? []}
            columns={columns}
            isLoading={isLoading}
          />
        </div>

      </div>
    </>
  )
}
