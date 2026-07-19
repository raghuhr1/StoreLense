'use client'

import { useQuery, useQueryClient, useMutation } from '@tanstack/react-query'
import { useMemo, useState }     from 'react'
import { BarChart3, ScanLine, CheckCircle2, AlertTriangle, PackageOpen, MapPin, TrendingDown, RefreshCw } from 'lucide-react'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, LineChart, Line, CartesianGrid } from 'recharts'
import Link             from 'next/link'
import Header           from '@/components/layout/Header'
import StatCard         from '@/components/ui/StatCard'
import NetworkOverview  from '@/components/dashboard/NetworkOverview'
import { reportingApi }       from '@/lib/api/reporting'
import { storesApi }          from '@/lib/api/stores'
import { sohApi }             from '@/lib/api/soh'
import { refillApi }          from '@/lib/api/refill'
import { erpImportApi }       from '@/lib/api/erp'
import { replenishmentRulesApi } from '@/lib/api/inventory'
import { useAuth }            from '@/lib/auth/AuthContext'
import { fmtPct, fmtDateTime } from '@/lib/utils'
import Badge                  from '@/components/ui/Badge'

function iso(d: Date) { return d.toISOString().slice(0, 10) }

const RANGES = [
  { label: '7d',  days: 7 },
  { label: '30d', days: 30 },
  { label: '90d', days: 90 },
] as const
type Range = typeof RANGES[number]['label']

export default function DashboardPage() {
  const { user, isAdmin } = useAuth()
  const [selectedStoreId, setSelectedStoreId] = useState<string>('')
  const [range, setRange] = useState<Range>('30d')

  const queryClient = useQueryClient()

  const refreshKpi = useMutation({
    mutationFn: async (sid: string) => {
      const today = new Date()
      for (let i = 6; i >= 0; i--) {
        const d = new Date(today)
        d.setDate(d.getDate() - i)
        const date = d.toISOString().slice(0, 10)
        // Uses the shared, authenticated API client (attaches the Bearer token via
        // interceptor) instead of a raw fetch() — a raw fetch here silently sent no
        // Authorization header, got rejected before reaching reporting-service, and
        // never surfaced as an error since fetch() doesn't reject on non-2xx status.
        await reportingApi.aggregateKpi(sid, date)
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['kpi-range-dash'] })
      queryClient.invalidateQueries({ queryKey: ['soh-sessions-recent'] })
    },
  })

  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })

  // Admins start with no store selected — they land on the network-wide overview
  // first, and pick a store explicitly to drill into its own dashboard.
  const storeId = isAdmin
    ? selectedStoreId
    : (user?.storeId ?? '')

  const days = RANGES.find(r => r.label === range)!.days

  const { data: kpi } = useQuery({
    queryKey: ['kpi-range-dash', storeId, range],
    queryFn:  () => reportingApi.kpiRange(
      storeId,
      iso(new Date(Date.now() - days * 864e5)),
      iso(new Date()),
    ),
    enabled: !!storeId,
  })

  const { data: sessions } = useQuery({
    queryKey: ['soh-sessions-recent', storeId],
    queryFn:  () => sohApi.listSessions(storeId, { size: 5 }),
    enabled:  !!storeId,
  })

  const { data: tasks } = useQuery({
    queryKey: ['refill-tasks-pending', storeId],
    queryFn:  () => refillApi.listTasks(storeId, { status: 'pending', size: 5 }),
    enabled:  !!storeId,
  })

  const { data: erpBatches } = useQuery({
    queryKey: ['erp-batches-latest'],
    queryFn:  () => erpImportApi.listBatches({ size: 1 }),
    enabled:  isAdmin,
  })
  const latestBatch = erpBatches?.content[0]

  // Pick the most recent row that has non-null accuracy
  const latest = useMemo(() => {
    if (!kpi?.length) return undefined
    return [...kpi].reverse().find(k => k.inventoryAccuracyPct != null) ?? kpi[kpi.length - 1]
  }, [kpi])

  const chartData = (kpi ?? []).map(k => ({
    date:     k.kpiDate.slice(5),
    accuracy: k.inventoryAccuracyPct ?? 0,
    sessions: k.sohSessionsCount,
    refill:   k.refillCompletionRatePct ?? 0,
  }))

  const accMin = chartData.length
    ? Math.max(0, Math.floor(Math.min(...chartData.map(d => d.accuracy)) / 10) * 10)
    : 0

  const { data: floorSuggestions = [] } = useQuery({
    queryKey: ['replenishment-suggestions', storeId],
    queryFn:  () => replenishmentRulesApi.suggest(storeId),
    enabled:  !!storeId,
  })

  const floorTotals = useMemo(() => ({
    critical: floorSuggestions.filter(s => s.status === 'critical').length,
    low:      floorSuggestions.filter(s => s.status === 'low').length,
  }), [floorSuggestions])

  const sessionCount = kpi?.reduce((s, k) => s + k.sohSessionsCount, 0) ?? 0
  const selectedStore = allStores?.content.find(s => s.id === storeId)

  // Actual data span (may be less than selected range)
  const actualDays = useMemo(() => {
    if (!kpi?.length) return 0
    if (kpi.length === 1) return 1
    const ms = new Date(kpi[kpi.length - 1].kpiDate).getTime() - new Date(kpi[0].kpiDate).getTime()
    return Math.round(ms / 864e5) + 1
  }, [kpi])

  const selectCls = "text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500"

  return (
    <>
      <Header title="Dashboard" />
      <div className="p-6 space-y-6">

        {/* Top bar: store selector + range picker */}
        <div className="flex flex-wrap items-center gap-3">
          {isAdmin && allStores && allStores.content.length > 0 && (
            <>
              <label className="text-sm font-medium text-gray-600 shrink-0">Store</label>
              <select value={storeId} onChange={e => setSelectedStoreId(e.target.value)} className={selectCls}>
                <option value="">— All Stores (Network) —</option>
                {allStores.content.map(s => (
                  <option key={s.id} value={s.id}>{s.name} ({s.storeCode})</option>
                ))}
              </select>
              {selectedStore && <span className="text-xs text-gray-400">{selectedStore.city ?? ''}</span>}
              <div className="h-5 w-px bg-gray-200" />
            </>
          )}

          {/* Range selector */}
          {RANGES.map(r => (
            <button
              key={r.label}
              onClick={() => setRange(r.label)}
              className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                range === r.label
                  ? 'bg-brand-600 text-white'
                  : 'bg-white border border-gray-200 text-gray-700 hover:bg-gray-50'
              }`}
            >
              {r.label}
            </button>
          ))}

          {isAdmin && storeId && (
            <button
              onClick={() => refreshKpi.mutate(storeId)}
              disabled={refreshKpi.isPending}
              className="ml-auto flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium bg-white border border-gray-200 text-gray-700 hover:bg-gray-50 disabled:opacity-50"
              title="Recalculate KPI for last 7 days"
            >
              <RefreshCw size={13} className={refreshKpi.isPending ? 'animate-spin' : ''} />
              {refreshKpi.isPending ? 'Refreshing…' : 'Refresh KPI'}
            </button>
          )}

          {refreshKpi.isError && (
            <span className="text-xs font-medium text-red-700 bg-red-50 border border-red-200 px-2.5 py-1 rounded-full">
              Refresh failed — check permissions and try again
            </span>
          )}

          {/* Actual data span badge */}
          {actualDays > 0 && actualDays < days && (
            <span className="ml-1 text-xs font-medium text-amber-700 bg-amber-50 border border-amber-200 px-2.5 py-1 rounded-full">
              {actualDays} day{actualDays !== 1 ? 's' : ''} of data available
            </span>
          )}
          {actualDays > 0 && actualDays >= days && (
            <span className="ml-1 text-xs text-gray-400">{actualDays} days</span>
          )}
        </div>

        {/* Admins land here before picking a store — network-wide picture first,
            then drill into a specific store's dashboard below once one is selected. */}
        {isAdmin && !storeId ? (
          <NetworkOverview />
        ) : (
        <>
        {/* KPI cards */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard
            title="Inventory Accuracy"
            value={fmtPct(latest?.inventoryAccuracyPct)}
            icon={BarChart3}
            color={latest?.inventoryAccuracyPct != null && latest.inventoryAccuracyPct >= 95 ? 'green' : 'yellow'}
            sub={latest?.kpiDate ? `as of ${latest.kpiDate}` : undefined}
          />
          <StatCard
            title={`SOH Sessions (${range})`}
            value={sessionCount}
            icon={ScanLine}
            color="blue"
          />
          <StatCard
            title="Refill Completion"
            value={fmtPct(latest?.refillCompletionRatePct)}
            icon={CheckCircle2}
            color="green"
            sub={latest?.kpiDate ? `as of ${latest.kpiDate}` : undefined}
          />
          <StatCard
            title="Variance Items"
            value={latest?.varianceItemsCount ?? 0}
            icon={AlertTriangle}
            color={latest?.varianceItemsCount ? 'red' : 'green'}
            sub={latest?.kpiDate ?? 'Today'}
          />
        </div>

        {/* Charts */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="card">
            <h3 className="text-sm font-semibold text-gray-700 mb-4">
              Inventory Accuracy — {range}
            </h3>
            {chartData.length === 0 ? (
              <div className="flex items-center justify-center h-[200px] text-sm text-gray-400">
                No data in selected range
              </div>
            ) : (
              <ResponsiveContainer width="100%" height={200}>
                <LineChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis dataKey="date" tick={{ fontSize: 11 }} interval="preserveStartEnd" />
                  <YAxis domain={[accMin, 100]} tick={{ fontSize: 11 }} unit="%" />
                  <Tooltip formatter={(v: number) => [`${v.toFixed(1)}%`, 'Accuracy']} />
                  <Line type="monotone" dataKey="accuracy" stroke="#0F766E" strokeWidth={2} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            )}
          </div>

          <div className="card">
            <h3 className="text-sm font-semibold text-gray-700 mb-4">
              Refill Completion Rate — {range}
            </h3>
            {chartData.length === 0 ? (
              <div className="flex items-center justify-center h-[200px] text-sm text-gray-400">
                No data in selected range
              </div>
            ) : (
              <ResponsiveContainer width="100%" height={200}>
                <BarChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis dataKey="date" tick={{ fontSize: 11 }} interval="preserveStartEnd" />
                  <YAxis domain={[0, 100]} tick={{ fontSize: 11 }} unit="%" />
                  <Tooltip formatter={(v: number) => [`${v.toFixed(1)}%`, 'Completion']} />
                  <Bar dataKey="refill" fill="#16a34a" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>
        </div>

        {/* Sales Floor Stock Health Widget — sourced from the latest completed SOH session */}
        {floorSuggestions.length > 0 && (
          <div className="card">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <MapPin size={16} className="text-amber-500" />
                <h3 className="text-sm font-semibold text-gray-700">Sales Floor Stock Health</h3>
              </div>
              <Link href="/replenishment/auto" className="text-xs text-brand-600 hover:underline">
                View suggestions →
              </Link>
            </div>

            {(floorTotals.critical > 0 || floorTotals.low > 0) ? (
              <div className="flex items-center gap-4 mb-4 p-3 bg-amber-50 border border-amber-100 rounded-xl">
                {floorTotals.critical > 0 && (
                  <div className="flex items-center gap-1.5 text-red-700">
                    <AlertTriangle size={15} />
                    <span className="text-sm font-bold">{floorTotals.critical}</span>
                    <span className="text-xs">critical</span>
                  </div>
                )}
                {floorTotals.low > 0 && (
                  <div className="flex items-center gap-1.5 text-amber-700">
                    <TrendingDown size={15} />
                    <span className="text-sm font-bold">{floorTotals.low}</span>
                    <span className="text-xs">low stock</span>
                  </div>
                )}
                <Link href="/replenishment/auto" className="ml-auto text-xs font-semibold text-amber-700 hover:underline">
                  Auto-trigger refill →
                </Link>
              </div>
            ) : (
              <div className="flex items-center gap-2 mb-4 p-3 bg-green-50 border border-green-100 rounded-xl text-green-700 text-sm">
                <CheckCircle2 size={15} />
                Sales Floor is at or above par level
              </div>
            )}

            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
              {floorSuggestions.slice(0, 8).map(s => (
                <div key={s.productId} className={`rounded-xl p-3 border ${
                  s.status === 'critical' ? 'border-red-200 bg-red-50' : 'border-amber-200 bg-amber-50'
                }`}>
                  <p className="text-xs font-medium text-gray-700 truncate">{s.productName ?? s.sku ?? s.productId}</p>
                  <div className="flex items-center gap-1.5 mt-1.5">
                    {s.status === 'critical'
                      ? <span className="text-xs font-bold text-red-600">-{s.shortage}⚠</span>
                      : <span className="text-xs font-semibold text-amber-600">-{s.shortage}↓</span>}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Recent sessions + pending tasks + ERP import */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="card">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-semibold text-gray-700">Recent SOH Sessions</h3>
              <Link href="/soh-sessions" className="text-xs text-brand-600 hover:underline">View all</Link>
            </div>
            {!sessions || sessions.content.length === 0 ? (
              <p className="text-sm text-gray-400">No sessions yet.</p>
            ) : (
              <ul className="divide-y divide-gray-50">
                {sessions.content.map(s => (
                  <li key={s.id} className="py-2.5 flex items-center justify-between">
                    <div>
                      <Link href={`/cycle-count/${s.id}`} className="text-sm font-medium text-gray-900 hover:text-brand-600">
                        {s.sessionType.replace(/_/g, ' ')}
                      </Link>
                      <p className="text-xs text-gray-400">{fmtDateTime(s.startedAt)}</p>
                    </div>
                    <span className={`badge ${
                      s.status === 'completed'   ? 'badge-green' :
                      s.status === 'in_progress' ? 'badge-yellow' : 'badge-gray'
                    }`}>{s.status}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="card">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-semibold text-gray-700">Pending Refill Tasks</h3>
              <Link href="/receiving" className="text-xs text-brand-600 hover:underline">View all</Link>
            </div>
            {!tasks || tasks.content.length === 0 ? (
              <p className="text-sm text-gray-400">No pending tasks.</p>
            ) : (
              <ul className="divide-y divide-gray-50">
                {tasks.content.map(t => (
                  <li key={t.id} className="py-2.5 flex items-center justify-between">
                    <div>
                      <Link href={`/receiving/${t.id}`} className="text-sm font-medium text-gray-900 hover:text-brand-600">
                        {t.taskType.replace(/_/g, ' ')}
                      </Link>
                      <p className="text-xs text-gray-400">Priority {t.priority} · {t.source}</p>
                    </div>
                    <span className="badge badge-gray">{t.items.length} items</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>

        {/* ERP Import Status (admin only) */}
        {isAdmin && (
          <div className="card">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <PackageOpen size={16} className="text-gray-500" />
                <h3 className="text-sm font-semibold text-gray-700">ERP Import Status</h3>
              </div>
              <Link href="/erp-imports" className="text-xs text-brand-600 hover:underline">View all</Link>
            </div>

            {!latestBatch ? (
              <p className="text-sm text-gray-400">No import batches found.</p>
            ) : (
              <div className="space-y-2.5">
                <div className="grid grid-cols-3 gap-4">
                  <div className="bg-gray-50 rounded-lg p-3">
                    <p className="text-xs text-gray-500">Last Import</p>
                    <p className="text-sm font-semibold text-gray-900 mt-0.5">
                      {fmtDateTime(latestBatch.createdAt)}
                    </p>
                  </div>
                  <div className="bg-gray-50 rounded-lg p-3">
                    <p className="text-xs text-gray-500">Status</p>
                    <div className="mt-0.5">
                      <Badge variant={
                        latestBatch.status === 'COMPLETED'  ? 'green'  :
                        latestBatch.status === 'FAILED'     ? 'red'    :
                        latestBatch.status === 'PROCESSING' ? 'yellow' : 'gray'
                      }>
                        {latestBatch.status}
                      </Badge>
                    </div>
                  </div>
                  <div className="bg-gray-50 rounded-lg p-3">
                    <p className="text-xs text-gray-500">Unresolved EANs</p>
                    <p className={`text-sm font-semibold mt-0.5 ${
                      latestBatch.unresolvedRows > 0 ? 'text-red-600' : 'text-green-600'
                    }`}>
                      {latestBatch.unresolvedRows.toLocaleString()}
                    </p>
                  </div>
                </div>

                <div className="flex items-center justify-between text-xs text-gray-400">
                  <span>
                    {latestBatch.resolvedRows.toLocaleString()} / {latestBatch.totalRows.toLocaleString()} rows resolved
                    · Source: {latestBatch.sourceType}
                  </span>
                  <Link href={`/erp-imports/${latestBatch.id}`} className="text-brand-600 hover:underline">
                    View detail →
                  </Link>
                </div>

                {latestBatch.errorMessage && (
                  <p className="text-xs text-red-600 bg-red-50 rounded px-2 py-1 truncate">
                    {latestBatch.errorMessage}
                  </p>
                )}
              </div>
            )}
          </div>
        )}
        </>
        )}

      </div>
    </>
  )
}
