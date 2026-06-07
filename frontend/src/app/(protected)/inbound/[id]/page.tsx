'use client'

import { useQuery }             from '@tanstack/react-query'
import { useMemo }              from 'react'
import { useParams }            from 'next/navigation'
import Link                     from 'next/link'
import { ArrowLeft, Truck, Package } from 'lucide-react'
import { type ColumnDef }       from '@tanstack/react-table'
import Header                   from '@/components/layout/Header'
import DataTable                from '@/components/ui/DataTable'
import { statusBadge }          from '@/components/ui/Badge'
import { refillApi }            from '@/lib/api/refill'
import { productsApi }          from '@/lib/api/products'
import { fmtDateTime }          from '@/lib/utils'
import type { TaskItem, Product, RefillTask } from '@/types'

type EnrichedItem = TaskItem & { sku: string; productName: string }

const STATUS_COLOR: Record<string, string> = {
  pending:   'bg-gray-100 text-gray-700',
  partial:   'bg-yellow-100 text-yellow-800',
  fulfilled: 'bg-green-100 text-green-700',
  skipped:   'bg-red-100 text-red-700',
}

function grnRef(task: RefillTask): string {
  if (task.notes) {
    const m = task.notes.match(/GRN[-:\s]?([A-Z0-9]+)/i)
    if (m) return m[1].toUpperCase()
  }
  return 'GRN-' + task.id.slice(-8).toUpperCase()
}

export default function InboundDetailPage() {
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
          <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_COLOR[v] ?? ''}`}>
            {v}
          </span>
        )
      },
    },
    {
      accessorKey: 'requestedQuantity',
      header: 'Expected',
      cell: i => <span className="font-semibold">{i.getValue<number>()}</span>,
    },
    {
      accessorKey: 'fulfilledQuantity',
      header: 'Received',
      cell: i => {
        const v = i.getValue<number>()
        return <span className={v > 0 ? 'text-green-700 font-semibold' : 'text-gray-400'}>{v}</span>
      },
    },
    {
      id: 'variance',
      header: 'Short / Over',
      cell: ({ row: r }) => {
        const diff = r.original.requestedQuantity - r.original.fulfilledQuantity
        if (diff === 0) return <span className="text-green-600 text-sm">✓</span>
        if (diff > 0)  return <span className="text-red-600 font-medium">−{diff} short</span>
        return <span className="text-amber-600 font-medium">+{Math.abs(diff)} over</span>
      },
    },
  ], [])

  if (isLoading) return <><Header title="GRN Detail" /><div className="p-6 text-gray-400">Loading…</div></>
  if (!task)     return <><Header title="GRN Detail" /><div className="p-6 text-red-500">GRN not found.</div></>

  const grn         = grnRef(task)
  const toFloor     = task.notes?.toLowerCase().includes('floor')
  const totalExp    = task.items.reduce((s, i) => s + i.requestedQuantity, 0)
  const totalRcvd   = task.items.reduce((s, i) => s + i.fulfilledQuantity, 0)
  const shortfall   = totalExp - totalRcvd
  const fulfilled   = task.items.filter(i => i.status === 'fulfilled').length
  const pending     = task.items.filter(i => i.status === 'pending').length

  return (
    <>
      <Header title="GRN Detail" />
      <div className="p-6 space-y-6">

        <Link href="/inbound" className="inline-flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-800">
          <ArrowLeft size={14} /> Back to Inbound
        </Link>

        {/* GRN metadata */}
        <div className="card space-y-4">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <div className="flex items-center gap-2 mb-1">
                <Truck size={18} className="text-blue-600" />
                <span className="font-mono text-lg font-bold text-gray-900">{grn}</span>
                {statusBadge(task.status)}
              </div>
              <span className={`inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full ${
                toFloor ? 'bg-green-100 text-green-700' : 'bg-blue-100 text-blue-700'
              }`}>
                <Truck size={10} />
                {toFloor ? 'DC → Sales Floor' : 'DC → Backroom'}
              </span>
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
              { label: 'Total Lines',      value: task.items.length },
              { label: 'Units Expected',   value: totalExp },
              { label: 'Units Received',   value: totalRcvd },
              { label: 'Shortfall',        value: shortfall > 0 ? `−${shortfall}` : '✓ None', red: shortfall > 0 },
            ].map(s => (
              <div key={s.label} className="bg-gray-50 rounded-lg p-3">
                <p className="text-xs text-gray-500">{s.label}</p>
                <p className={`text-xl font-bold mt-0.5 ${s.red ? 'text-red-600' : 'text-gray-900'}`}>{s.value}</p>
              </div>
            ))}
          </div>

          {task.notes && (
            <p className="text-sm text-gray-600 border-l-4 border-brand-300 pl-3">{task.notes}</p>
          )}
        </div>

        {/* Line items */}
        <div className="card">
          <div className="flex items-center gap-2 mb-4">
            <Package size={16} className="text-gray-400" />
            <h3 className="text-sm font-semibold text-gray-700">
              Items Received ({fulfilled} / {task.items.length} lines · {pending} pending)
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
