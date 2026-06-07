'use client'

import { useQuery }                  from '@tanstack/react-query'
import { useMemo }                   from 'react'
import { useParams }                 from 'next/navigation'
import Link                          from 'next/link'
import { ArrowLeft, ArrowRight, Package } from 'lucide-react'
import { type ColumnDef }            from '@tanstack/react-table'
import Header                        from '@/components/layout/Header'
import DataTable                     from '@/components/ui/DataTable'
import { statusBadge }               from '@/components/ui/Badge'
import { refillApi }                 from '@/lib/api/refill'
import { productsApi }               from '@/lib/api/products'
import { fmtDateTime }               from '@/lib/utils'
import type { TaskItem, Product, RefillTask } from '@/types'

type EnrichedItem = TaskItem & { sku: string; productName: string }

const ITEM_STATUS_COLOR: Record<string, string> = {
  pending:   'bg-gray-100 text-gray-700',
  partial:   'bg-yellow-100 text-yellow-800',
  fulfilled: 'bg-green-100 text-green-700',
  skipped:   'bg-red-100 text-red-700',
}

function movementLabel(task: RefillTask) {
  const n = (task.notes ?? '').toLowerCase()
  if (n.includes('dc') && n.includes('floor')) return { label: 'DC → Sales Floor', cls: 'bg-green-100 text-green-700' }
  if (task.source === 'erp')                   return { label: 'DC → Sales Floor', cls: 'bg-green-100 text-green-700' }
  return { label: 'Backroom → Sales Floor', cls: 'bg-purple-100 text-purple-700' }
}

const SOURCE_LABEL: Record<string, string> = {
  manual:      'Manual',
  soh_trigger: 'RFID Alert',
  scheduled:   'Scheduled',
  erp:         'ERP / DC Direct',
}

export default function ReplenishmentDetailPage() {
  const { id } = useParams<{ id: string }>()

  const { data: task, isLoading } = useQuery({
    queryKey: ['refill-task', id],
    queryFn:  () => refillApi.getTask(id),
    enabled:  !!id,
  })

  const { data: allProducts } = useQuery({
    queryKey: ['products-all-lookup'],
    queryFn:  async () => {
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
      return { ...item, sku: p?.sku ?? item.productId.slice(-8), productName: p?.name ?? '—' }
    }),
  [task, productMap])

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
          <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${ITEM_STATUS_COLOR[v] ?? ''}`}>
            {v}
          </span>
        )
      },
    },
    {
      accessorKey: 'requestedQuantity',
      header: 'To Move',
      cell: i => <span className="font-semibold">{i.getValue<number>()}</span>,
    },
    {
      accessorKey: 'fulfilledQuantity',
      header: 'Moved',
      cell: i => {
        const v = i.getValue<number>()
        return <span className={v > 0 ? 'text-green-700 font-semibold' : 'text-gray-400'}>{v}</span>
      },
    },
    {
      id: 'remaining',
      header: 'Remaining',
      cell: ({ row: r }) => {
        const rem = r.original.requestedQuantity - r.original.fulfilledQuantity
        return rem > 0
          ? <span className="text-amber-600 font-medium">{rem} left</span>
          : <span className="text-green-600 text-sm">✓ Done</span>
      },
    },
  ], [])

  if (isLoading) return <><Header title="Replenishment Task" /><div className="p-6 text-gray-400">Loading…</div></>
  if (!task)     return <><Header title="Replenishment Task" /><div className="p-6 text-red-500">Task not found.</div></>

  const mv          = movementLabel(task)
  const totalReq    = task.items.reduce((s, i) => s + i.requestedQuantity, 0)
  const totalMoved  = task.items.reduce((s, i) => s + i.fulfilledQuantity, 0)
  const fulfilled   = task.items.filter(i => i.status === 'fulfilled').length
  const pending     = task.items.filter(i => i.status === 'pending').length

  return (
    <>
      <Header title="Replenishment Task" />
      <div className="p-6 space-y-6">

        <Link href="/replenishment" className="inline-flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-800">
          <ArrowLeft size={14} /> Back to Replenishment
        </Link>

        {/* Task metadata */}
        <div className="card space-y-4">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="space-y-2">
              <p className="text-xs text-gray-400 font-mono">{task.id}</p>
              <div className="flex items-center gap-3 flex-wrap">
                <span className={`inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-1 rounded-full ${mv.cls}`}>
                  <ArrowRight size={11} /> {mv.label}
                </span>
                {statusBadge(task.status)}
                <span className="text-xs text-gray-500">
                  Source: <span className="font-medium text-gray-700">{SOURCE_LABEL[task.source] ?? task.source}</span>
                </span>
              </div>
            </div>
            <div className="text-right text-sm text-gray-500 space-y-0.5">
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
              { label: 'Fulfilled',    value: fulfilled, green: fulfilled > 0 },
              { label: 'Pending',      value: pending,   amber: pending > 0 },
              { label: 'Units Moved',  value: `${totalMoved} / ${totalReq}` },
            ].map(s => (
              <div key={s.label} className="bg-gray-50 rounded-lg p-3">
                <p className="text-xs text-gray-500">{s.label}</p>
                <p className={`text-xl font-bold mt-0.5 ${s.green ? 'text-green-700' : s.amber ? 'text-amber-600' : 'text-gray-900'}`}>
                  {s.value}
                </p>
              </div>
            ))}
          </div>

          {task.notes && (
            <p className="text-sm text-gray-600 border-l-4 border-brand-300 pl-3">{task.notes}</p>
          )}
        </div>

        {/* Product lines */}
        <div className="card">
          <div className="flex items-center gap-2 mb-4">
            <Package size={16} className="text-gray-400" />
            <h3 className="text-sm font-semibold text-gray-700">
              Product Lines ({task.items.length} lines · {pending} pending)
            </h3>
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
