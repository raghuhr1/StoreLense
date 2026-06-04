'use client'

import { useQuery }     from '@tanstack/react-query'
import { BarChart3, ScanLine, Package, CheckCircle2, AlertTriangle } from 'lucide-react'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, LineChart, Line, CartesianGrid } from 'recharts'
import Header           from '@/components/layout/Header'
import StatCard         from '@/components/ui/StatCard'
import { reportingApi } from '@/lib/api/reporting'
import { sohApi }       from '@/lib/api/soh'
import { refillApi }    from '@/lib/api/refill'
import { useAuth }      from '@/lib/auth/AuthContext'
import { fmtPct }       from '@/lib/utils'

export default function DashboardPage() {
  const { user } = useAuth()
  const storeId  = user?.storeId ?? ''

  const { data: kpi } = useQuery({
    queryKey: ['kpi-daily', storeId],
    queryFn:  () => reportingApi.kpiRange(storeId,
      new Date(Date.now() - 7 * 864e5).toISOString().slice(0,10),
      new Date().toISOString().slice(0,10)),
    enabled: !!storeId,
  })

  const { data: sessions } = useQuery({
    queryKey: ['soh-sessions-recent', storeId],
    queryFn:  () => sohApi.listSessions(storeId, { size: 5 }),
    enabled: !!storeId,
  })

  const { data: tasks } = useQuery({
    queryKey: ['refill-tasks-pending', storeId],
    queryFn:  () => refillApi.listTasks(storeId, { status: 'pending', size: 5 }),
    enabled: !!storeId,
  })

  const latest = kpi?.[kpi.length - 1]

  const chartData = (kpi ?? []).map(k => ({
    date:     k.kpiDate.slice(5),
    accuracy: k.inventoryAccuracyPct ?? 0,
    sessions: k.sohSessionsCount,
    refill:   k.refillCompletionRatePct ?? 0,
  }))

  return (
    <>
      <Header title="Dashboard" />
      <div className="p-6 space-y-6">

        {/* KPI cards */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard
            title="Inventory Accuracy"
            value={fmtPct(latest?.inventoryAccuracyPct)}
            icon={BarChart3}
            color={latest?.inventoryAccuracyPct != null && latest.inventoryAccuracyPct >= 98 ? 'green' : 'yellow'}
          />
          <StatCard
            title="SOH Sessions (7d)"
            value={kpi?.reduce((s, k) => s + k.sohSessionsCount, 0) ?? 0}
            icon={ScanLine}
            color="blue"
          />
          <StatCard
            title="Refill Completion"
            value={fmtPct(latest?.refillCompletionRatePct)}
            icon={CheckCircle2}
            color="green"
          />
          <StatCard
            title="Variance Items"
            value={latest?.varianceItemsCount ?? 0}
            icon={AlertTriangle}
            color={latest?.varianceItemsCount ? 'red' : 'green'}
            sub="Today"
          />
        </div>

        {/* Charts */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="card">
            <h3 className="text-sm font-semibold text-gray-700 mb-4">Inventory Accuracy — 7 Days</h3>
            <ResponsiveContainer width="100%" height={200}>
              <LineChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                <YAxis domain={[80, 100]} tick={{ fontSize: 11 }} unit="%" />
                <Tooltip formatter={(v: number) => [`${v.toFixed(1)}%`, 'Accuracy']} />
                <Line type="monotone" dataKey="accuracy" stroke="#2563eb" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>

          <div className="card">
            <h3 className="text-sm font-semibold text-gray-700 mb-4">Refill Completion Rate — 7 Days</h3>
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                <YAxis domain={[0, 100]} tick={{ fontSize: 11 }} unit="%" />
                <Tooltip formatter={(v: number) => [`${v.toFixed(1)}%`, 'Completion']} />
                <Bar dataKey="refill" fill="#16a34a" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Recent sessions + pending tasks */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="card">
            <h3 className="text-sm font-semibold text-gray-700 mb-4">Recent SOH Sessions</h3>
            {sessions?.content.length === 0 ? (
              <p className="text-sm text-gray-400">No sessions yet.</p>
            ) : (
              <ul className="divide-y divide-gray-50">
                {sessions?.content.map(s => (
                  <li key={s.id} className="py-2.5 flex items-center justify-between">
                    <div>
                      <p className="text-sm font-medium text-gray-900">{s.sessionType}</p>
                      <p className="text-xs text-gray-400">{new Date(s.startedAt).toLocaleString()}</p>
                    </div>
                    <span className={`badge ${
                      s.status === 'completed' ? 'badge-green' :
                      s.status === 'in_progress' ? 'badge-yellow' : 'badge-gray'
                    }`}>{s.status}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="card">
            <h3 className="text-sm font-semibold text-gray-700 mb-4">Pending Refill Tasks</h3>
            {tasks?.content.length === 0 ? (
              <p className="text-sm text-gray-400">No pending tasks.</p>
            ) : (
              <ul className="divide-y divide-gray-50">
                {tasks?.content.map(t => (
                  <li key={t.id} className="py-2.5 flex items-center justify-between">
                    <div>
                      <p className="text-sm font-medium text-gray-900">{t.taskType}</p>
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
