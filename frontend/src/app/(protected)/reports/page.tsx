'use client'

import { useQuery }    from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { type ColumnDef } from '@tanstack/react-table'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, LineChart, Line, CartesianGrid } from 'recharts'
import Header          from '@/components/layout/Header'
import DataTable       from '@/components/ui/DataTable'
import { reportingApi } from '@/lib/api/reporting'
import { useAuth }     from '@/lib/auth/AuthContext'
import { fmtPct }      from '@/lib/utils'
import type { KpiDaily } from '@/types'

function iso(d: Date) { return d.toISOString().slice(0,10) }

export default function ReportsPage() {
  const { user }  = useAuth()
  const storeId   = user?.storeId ?? ''

  const [range, setRange] = useState<'7d' | '30d' | '90d'>('30d')
  const days = range === '7d' ? 7 : range === '30d' ? 30 : 90

  const { data, isLoading } = useQuery({
    queryKey: ['kpi-range', storeId, range],
    queryFn:  () => reportingApi.kpiRange(
      storeId,
      iso(new Date(Date.now() - days * 864e5)),
      iso(new Date())
    ),
    enabled: !!storeId,
  })

  const chartData = (data ?? []).map(k => ({
    date:     k.kpiDate.slice(5),
    accuracy: k.inventoryAccuracyPct ?? 0,
    sessions: k.sohSessionsCount,
    refill:   k.refillCompletionRatePct ?? 0,
    reads:    Math.round(k.totalEpcReads / 1000),
  }))

  const columns = useMemo<ColumnDef<KpiDaily, unknown>[]>(() => [
    { accessorKey: 'kpiDate',               header: 'Date' },
    { accessorKey: 'inventoryAccuracyPct',  header: 'Accuracy', cell: i => fmtPct(i.getValue<number|null>()) },
    { accessorKey: 'sohSessionsCount',      header: 'SOH Sessions' },
    { accessorKey: 'refillTasksCreated',    header: 'Refill Created' },
    { accessorKey: 'refillTasksCompleted',  header: 'Refill Done' },
    { accessorKey: 'refillCompletionRatePct', header: 'Completion', cell: i => fmtPct(i.getValue<number|null>()) },
    { accessorKey: 'totalEpcReads',         header: 'EPC Reads', cell: i => i.getValue<number>().toLocaleString() },
    { accessorKey: 'varianceItemsCount',    header: 'Variances' },
  ], [])

  return (
    <>
      <Header title="Reports" />
      <div className="p-6 space-y-6">

        {/* Range selector */}
        <div className="flex gap-2">
          {(['7d', '30d', '90d'] as const).map(r => (
            <button
              key={r}
              onClick={() => setRange(r)}
              className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                range === r ? 'bg-brand-600 text-white' : 'bg-white border border-gray-200 text-gray-700 hover:bg-gray-50'
              }`}
            >
              {r}
            </button>
          ))}
        </div>

        {/* Charts */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="card">
            <h3 className="text-sm font-semibold text-gray-700 mb-4">Inventory Accuracy</h3>
            <ResponsiveContainer width="100%" height={200}>
              <LineChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="date" tick={{ fontSize: 10 }} interval="preserveStartEnd" />
                <YAxis domain={[0, 100]} unit="%" tick={{ fontSize: 10 }} />
                <Tooltip formatter={(v: number) => [`${v.toFixed(1)}%`]} />
                <Line type="monotone" dataKey="accuracy" stroke="#2563eb" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>

          <div className="card">
            <h3 className="text-sm font-semibold text-gray-700 mb-4">Refill Completion Rate</h3>
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="date" tick={{ fontSize: 10 }} interval="preserveStartEnd" />
                <YAxis domain={[0, 100]} unit="%" tick={{ fontSize: 10 }} />
                <Tooltip formatter={(v: number) => [`${v.toFixed(1)}%`]} />
                <Bar dataKey="refill" fill="#16a34a" radius={[3, 3, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Raw KPI table */}
        <div className="card">
          <h3 className="text-sm font-semibold text-gray-700 mb-4">Daily KPI Detail</h3>
          <DataTable
            data={data ?? []}
            columns={columns}
            isLoading={isLoading}
            pageSize={15}
          />
        </div>

      </div>
    </>
  )
}
