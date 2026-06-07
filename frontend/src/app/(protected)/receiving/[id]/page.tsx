'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo }          from 'react'
import { useParams }        from 'next/navigation'
import Link                 from 'next/link'
import { ArrowLeft, Package } from 'lucide-react'
import { type ColumnDef }   from '@tanstack/react-table'
import Header               from '@/components/layout/Header'
import DataTable            from '@/components/ui/DataTable'
import { statusBadge }      from '@/components/ui/Badge'
import { refillApi }        from '@/lib/api/refill'
import { productsApi }      from '@/lib/api/products'
import { useAuth }          from '@/lib/auth/AuthContext'
import { fmtDateTime }      from '@/lib/utils'
import type { TaskItem, Product } from '@/types'

type EnrichedItem = TaskItem & { sku: string; productName: string }

const STATUS_COLOR: Record<string, string> = {
  pending:   'bg-gray-100 text-gray-700',
  partial:   'bg-yellow-100 text-yellow-800',
  fulfilled: 'bg-green-100 text-green-700',
  skipped:   'bg-red-100 text-red-700',
}

export default function ReceivingDetailPage() {
  const { id }  = useParams<{ id: string }>()
  const { user } = useAuth()
  const qc = useQueryClient()

  const { data: task, isLoading } = useQuery({
    queryKey: ['refill-task', id],
    queryFn:  () => refillApi.getTask(id),
    enabled:  !!id,
  })

  // Fetch all products to resolve productId → name/sku
  const { data: allProducts } = useQuery({
    queryKey: ['products-all-lookup'],
    queryFn: async () => {
      const all: Product[] = []
      let page = 0
      while (true) {
        const resp = await productsApi.list({ size: 500, page })
        if (!resp?.content?.length) break
        all.push(...resp.content)
        if (resp.last || all.length >= resp.totalElements) break
        page++
      }
      return all
    },
    staleTime: 5 * 60 * 1000,
  })

  const productMap = useMemo(() => {
    const m: Record<string, Product> = {}
    for (const p of allProducts ?? []) m[p.id] = p
    return m
  }, [allProducts])

  const enrichedItems = useMemo((): EnrichedItem[] =>
    (task?.items ?? []).map(item => {
      const p = productMap[item.productId]
      return {
        ...item,
        sku:         p?.sku  ?? item.productId.slice(-8),
        productName: p?.name ?? '—',
      }
    }),
  [task, productMap])

  const fulfilMut = useMutation({
    mutationFn: ({ itemId, qty }: { itemId: string; qty: number }) =>
      refillApi.fulfil(id, itemId, qty),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['refill-task', id] }),
  })

  const columns = useMemo<ColumnDef<EnrichedItem, unknown>[]>(() => [
    {
      id: 'product',
      header: 'Product',
      accessorFn: r => r.sku,
      cell: ({ row: r }) => (
        <Link href={`/inventory/${r.original.productId}`} className="hover:underline block">
          <p className="font-mono text-xs text-blue-600">{r.original.sku}</p>
          <p className="text-xs text-gray-500 truncate max-w-[220px]">{r.original.productName}</p>
        </Link>
      ),
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: i => {
        const v = i.getValue<string>()
        return (
          <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_COLOR[v] ?? ''}`}>
            {v}
          </span>
        )
      },
    },
    {
      accessorKey: 'requestedQuantity',
      header: 'Requested',
      cell: i => <span className="font-semibold">{i.getValue<number>()}</span>,
    },
    {
      accessorKey: 'fulfilledQuantity',
      header: 'Fulfilled',
      cell: i => {
        const v = i.getValue<number>()
        return <span className={v > 0 ? 'text-green-700 font-semibold' : 'text-gray-400'}>{v}</span>
      },
    },
    {
      id: 'variance',
      header: 'Shortfall',
      cell: ({ row: r }) => {
        const diff = r.original.requestedQuantity - r.original.fulfilledQuantity
        return diff > 0
          ? <span className="text-red-600 font-medium">−{diff}</span>
          : <span className="text-green-600">✓</span>
      },
    },
  ], [])

  if (isLoading) {
    return (
      <>
        <Header title="Refill Task" />
        <div className="p-6 text-gray-400">Loading…</div>
      </>
    )
  }

  if (!task) {
    return (
      <>
        <Header title="Refill Task" />
        <div className="p-6 text-red-500">Task not found.</div>
      </>
    )
  }

  const fulfilled  = task.items.filter(i => i.status === 'fulfilled').length
  const pending    = task.items.filter(i => i.status === 'pending').length
  const totalReq   = task.items.reduce((s, i) => s + i.requestedQuantity,  0)
  const totalFulfd = task.items.reduce((s, i) => s + i.fulfilledQuantity, 0)

  return (
    <>
      <Header title="Refill Task Detail" />
      <div className="p-6 space-y-6">

        {/* Back link */}
        <Link href="/receiving" className="inline-flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-800">
          <ArrowLeft size={14} /> Back to Receiving
        </Link>

        {/* Task metadata */}
        <div className="card space-y-4">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <p className="text-xs text-gray-400 font-mono mb-1">{task.id}</p>
              <div className="flex items-center gap-3">
                <span className="text-lg font-semibold capitalize">{task.taskType.replace(/_/g, ' ')}</span>
                {statusBadge(task.status)}
              </div>
            </div>
            <div className="text-right text-sm text-gray-500 space-y-0.5">
              <p>Source: <span className="font-medium text-gray-800 capitalize">{task.source}</span></p>
              <p>Priority: <span className="font-medium text-gray-800">P{task.priority}</span></p>
              <p>Created: <span className="font-medium text-gray-800">{fmtDateTime(task.createdAt)}</span></p>
              {task.completedAt && (
                <p>Completed: <span className="font-medium text-gray-800">{fmtDateTime(task.completedAt)}</span></p>
              )}
            </div>
          </div>

          {/* Summary stats */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-2 border-t border-gray-100">
            {[
              { label: 'Total Lines',  value: task.items.length },
              { label: 'Fulfilled',    value: fulfilled },
              { label: 'Pending',      value: pending },
              { label: 'Units Moved',  value: `${totalFulfd} / ${totalReq}` },
            ].map(s => (
              <div key={s.label} className="bg-gray-50 rounded-lg p-3">
                <p className="text-xs text-gray-500">{s.label}</p>
                <p className="text-xl font-bold text-gray-900 mt-0.5">{s.value}</p>
              </div>
            ))}
          </div>

          {task.notes && (
            <p className="text-sm text-gray-600 border-l-4 border-brand-300 pl-3">{task.notes}</p>
          )}
        </div>

        {/* Product line items */}
        <div className="card">
          <div className="flex items-center gap-2 mb-4">
            <Package size={16} className="text-gray-400" />
            <h3 className="text-sm font-semibold text-gray-700">Product Lines ({task.items.length})</h3>
          </div>
          <DataTable
            data={enrichedItems}
            columns={columns}
            isLoading={!allProducts}
            searchable
            searchPlaceholder="Search SKU or product…"
          />
        </div>

      </div>
    </>
  )
}
