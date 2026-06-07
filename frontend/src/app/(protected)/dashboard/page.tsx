'use client'

import { useQuery }     from '@tanstack/react-query'
import { useMemo, useState }     from 'react'
import { BarChart3, ScanLine, CheckCircle2, AlertTriangle }  from 'lucide-react'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, LineChart, Line, CartesianGrid } from 'recharts'
import Link             from 'next/link'
import Header           from '@/components/layout/Header'
import StatCard         from '@/components/ui/StatCard'
import { reportingApi } from '@/lib/api/reporting'
import { storesApi }    from '@/lib/api/stores'
import { sohApi }       from '@/lib/api/soh'
import { refillApi }    from '@/lib/api/refill'
import { useAuth }      from '@/lib/auth/AuthContext'
import { fmtPct, fmtDateTime } from '@/lib/utils'

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
  const [range, setRange] = useState<Range>('90d')

  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })

  const storeId = isAdmin
    ? (selectedStoreId || allStores?.content[0]?.id || '')
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

  const sessionCount = kpi?.reduce((s, k) => s + k.sohSessionsCount, 0) ?? 0
  const selectedStore = allStores?.content.find(s => s.id === storeId)

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
        </div>

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
                  <YAxis domain={[80, 100]} tick={{ fontSize: 11 }} unit="%" />
                  <Tooltip formatter={(v: number) => [`${v.toFixed(1)}%`, 'Accuracy']} />
                  <Line type="monotone" dataKey="accuracy" stroke="#2563eb" strokeWidth={2} dot={false} />
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

        {/* Recent sessions + pending tasks */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="card">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-semibold text-gray-700">Recent SOH Sessions</h3>
              <Link href="/cycle-count" className="text-xs text-brand-600 hover:underline">View all</Link>
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

      </div>
    </>
  )
}
