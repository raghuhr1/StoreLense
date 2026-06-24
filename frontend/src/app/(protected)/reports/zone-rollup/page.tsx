'use client'

import { useQuery, useMutation } from '@tanstack/react-query'
import { useMemo, useState }     from 'react'
import { type ColumnDef }        from '@tanstack/react-table'
import { RefreshCw, Loader2, AlertTriangle, TrendingDown, CheckCircle2, TrendingUp } from 'lucide-react'
import Header        from '@/components/layout/Header'
import DataTable     from '@/components/ui/DataTable'
import { storesApi } from '@/lib/api/stores'
import { scanRollupApi } from '@/lib/api/inventory'
import { useAuth }   from '@/lib/auth/AuthContext'
import type { ZoneScanRollupRow, RollupStatus, Zone } from '@/types'

// ── Status helpers ─────────────────────────────────────────────────────────────
const STATUS_META: Record<RollupStatus, { label: string; cls: string; icon: React.ElementType }> = {
  critical: { label: 'Critical',  cls: 'bg-red-100 text-red-800',       icon: AlertTriangle   },
  low:      { label: 'Low Stock', cls: 'bg-amber-100 text-amber-800',   icon: TrendingDown    },
  ok:       { label: 'OK',        cls: 'bg-green-100 text-green-700',   icon: CheckCircle2    },
  surplus:  { label: 'Surplus',   cls: 'bg-blue-100 text-blue-700',     icon: TrendingUp      },
}

function StatusBadge({ status }: { status: RollupStatus }) {
  const m = STATUS_META[status] ?? STATUS_META.ok
  const Icon = m.icon
  return (
    <span className={`inline-flex items-center gap-1 text-xs font-semibold px-2 py-0.5 rounded-full ${m.cls}`}>
      <Icon size={11} />
      {m.label}
    </span>
  )
}

function varianceCls(v: number) {
  if (v < 0) return 'text-red-600 font-semibold'
  if (v > 0) return 'text-blue-600'
  return 'text-gray-500'
}

const selectCls = 'text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500'

// ── Summary counts ─────────────────────────────────────────────────────────────
function SummaryBar({ rows }: { rows: ZoneScanRollupRow[] }) {
  const counts = useMemo(() => {
    const c = { critical: 0, low: 0, ok: 0, surplus: 0 }
    rows.forEach(r => { c[r.status] = (c[r.status] ?? 0) + 1 })
    return c
  }, [rows])

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
      {(Object.entries(STATUS_META) as [RollupStatus, typeof STATUS_META[RollupStatus]][]).map(([s, m]) => {
        const Icon = m.icon
        return (
          <div key={s} className="card flex items-center gap-3">
            <Icon size={18} className={counts[s] > 0 && s !== 'ok'
              ? (s === 'critical' ? 'text-red-500' : s === 'low' ? 'text-amber-500' : 'text-blue-500')
              : 'text-green-500'} />
            <div>
              <p className="text-2xl font-bold text-gray-900">{counts[s]}</p>
              <p className="text-xs text-gray-500">{m.label}</p>
            </div>
          </div>
        )
      })}
    </div>
  )
}

// ── Main Page ──────────────────────────────────────────────────────────────────
export default function ZoneRollupPage() {
  const { user, isAdmin } = useAuth()

  const [selectedStoreId, setSelectedStoreId] = useState('')
  const [selectedZoneId,  setSelectedZoneId]  = useState('')
  const [statusFilter,    setStatusFilter]    = useState<RollupStatus | ''>('')

  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })

  const storeId = isAdmin
    ? (selectedStoreId || allStores?.content[0]?.id || '')
    : (user?.storeId ?? '')

  const { data: zones = [] } = useQuery<Zone[]>({
    queryKey: ['zones', storeId],
    queryFn:  () => storesApi.zones(storeId),
    enabled:  !!storeId,
  })

  const { data: rows = [], isLoading, refetch, isFetching } = useQuery<ZoneScanRollupRow[]>({
    queryKey: ['scan-rollup-live', storeId, selectedZoneId],
    queryFn:  () => scanRollupApi.live(storeId, selectedZoneId || undefined),
    enabled:  !!storeId,
  })

  const computeMut = useMutation({
    mutationFn: () => scanRollupApi.compute(storeId),
    onSuccess:  () => refetch(),
  })

  const filtered = useMemo(() =>
    statusFilter ? rows.filter(r => r.status === statusFilter) : rows,
  [rows, statusFilter])

  const columns = useMemo<ColumnDef<ZoneScanRollupRow, unknown>[]>(() => [
    {
      accessorFn: r => r.zoneName ?? r.zoneId,
      id: 'zone',
      header: 'Zone',
      cell: ({ row: r }) => (
        <p className="text-sm font-medium text-gray-900">{r.original.zoneName ?? r.original.zoneId}</p>
      ),
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
      accessorKey: 'scannedQty',
      header: 'Scanned',
      cell: i => <span className="font-mono font-semibold text-gray-900">{i.getValue<number>()}</span>,
    },
    {
      accessorKey: 'parQty',
      header: 'Par Qty',
      cell: i => <span className="font-mono text-gray-500">{i.getValue<number>()}</span>,
    },
    {
      accessorKey: 'minQty',
      header: 'Min Qty',
      cell: i => <span className="font-mono text-gray-400">{i.getValue<number>()}</span>,
    },
    {
      accessorKey: 'variance',
      header: 'Variance',
      cell: ({ getValue }) => {
        const v = getValue<number>()
        return (
          <span className={`font-mono ${varianceCls(v)}`}>
            {v > 0 ? `+${v}` : v}
          </span>
        )
      },
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: ({ getValue }) => <StatusBadge status={getValue<RollupStatus>()} />,
    },
  ], [])

  return (
    <>
      <Header title="Zone Stock Comparison" />
      <div className="p-6 space-y-5">

        {/* Controls */}
        <div className="flex flex-wrap items-center gap-3">
          {isAdmin && allStores && (
            <>
              <label className="text-sm font-medium text-gray-600 shrink-0">Store</label>
              <select value={storeId} onChange={e => setSelectedStoreId(e.target.value)} className={selectCls}>
                {allStores.content.map(s => (
                  <option key={s.id} value={s.id}>{s.name}</option>
                ))}
              </select>
            </>
          )}

          <label className="text-sm font-medium text-gray-600 shrink-0">Zone</label>
          <select value={selectedZoneId} onChange={e => setSelectedZoneId(e.target.value)} className={selectCls}>
            <option value="">All Zones</option>
            {zones.map(z => <option key={z.id} value={z.id}>{z.name}</option>)}
          </select>

          <label className="text-sm font-medium text-gray-600 shrink-0">Status</label>
          <select value={statusFilter} onChange={e => setStatusFilter(e.target.value as RollupStatus | '')} className={selectCls}>
            <option value="">All</option>
            {(Object.entries(STATUS_META) as [RollupStatus, typeof STATUS_META[RollupStatus]][]).map(([s, m]) => (
              <option key={s} value={s}>{m.label}</option>
            ))}
          </select>

          <div className="ml-auto flex gap-2">
            <button
              onClick={() => refetch()}
              disabled={isFetching}
              className="btn-secondary"
              title="Refresh live view"
            >
              {isFetching ? <Loader2 size={14} className="animate-spin" /> : <RefreshCw size={14} />}
              Refresh
            </button>
            <button
              onClick={() => computeMut.mutate()}
              disabled={computeMut.isPending || !storeId}
              className="btn-primary"
              title="Save snapshot of current position"
            >
              {computeMut.isPending ? <Loader2 size={14} className="animate-spin" /> : <RefreshCw size={14} />}
              Compute &amp; Save
            </button>
          </div>
        </div>

        {/* Summary */}
        <SummaryBar rows={rows} />

        {/* Table */}
        <div className="card">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-semibold text-gray-700">
              Zone vs Par Level Comparison
              {statusFilter && (
                <span className="ml-2 text-xs font-normal text-gray-400">
                  — filtered: {STATUS_META[statusFilter]?.label}
                </span>
              )}
            </h2>
            <span className="text-xs text-gray-400">Live — reflects current RFID scan state</span>
          </div>

          {rows.length === 0 && !isLoading && (
            <div className="py-10 text-center">
              <p className="text-sm text-gray-500">No par levels configured for this store.</p>
              <p className="text-xs text-gray-400 mt-1">
                Add par levels in <strong>Store Settings → Zone Par Levels</strong> to see comparisons here.
              </p>
            </div>
          )}

          <DataTable
            data={filtered}
            columns={columns}
            isLoading={isLoading}
            searchable
            searchPlaceholder="Search product or zone…"
          />
        </div>

      </div>
    </>
  )
}
