'use client'

import { useQuery, useQueries } from '@tanstack/react-query'
import { Fragment, useEffect, useState } from 'react'
import { useRouter }         from 'next/navigation'
import {
  Radio, AlertTriangle, PackageOpen, RefreshCw,
  ChevronLeft, ChevronRight, ChevronDown, ChevronUp,
} from 'lucide-react'
import Header                from '@/components/layout/Header'
import StatCard              from '@/components/ui/StatCard'
import { gateApi }           from '@/lib/api/gate'
import { inventoryApi }      from '@/lib/api/inventory'
import { storesApi }         from '@/lib/api/stores'
import { useAuth }           from '@/lib/auth/AuthContext'
import type { GateCheck }    from '@/types'

// ── Helpers ───────────────────────────────────────────────────────────────────

function todayIso(): string {
  return new Date().toISOString().split('T')[0]
}

function fmtTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-AU', {
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  })
}

const OUTCOME_BADGE: Record<string, string> = {
  RELEASED:  'bg-green-50  text-green-700  ring-green-200',
  FLAGGED:   'bg-red-50    text-red-700    ring-red-200',
  ABANDONED: 'bg-gray-100  text-gray-600   ring-gray-200',
}

const PAGE_SIZE = 50
const selectCls = 'text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500'

// ── Expanded row — which tags triggered this alarm ──────────────────────────

function ExpandedAlarmDetails({ row, storeId }: { row: GateCheck; storeId: string }) {
  const extraEpcQueries = useQueries({
    queries: row.epcsExtra.map(epc => ({
      queryKey: ['identify-epc', epc, storeId],
      queryFn:  () => inventoryApi.identifyEpc(epc, storeId),
    })),
  })

  return (
    <div>
      <p className="text-xs font-semibold text-gray-500 mb-2">
        Tags that triggered this alarm ({row.epcsExtra.length})
      </p>
      {row.epcsExtra.length === 0 ? (
        <p className="text-xs text-gray-400">None</p>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
          {row.epcsExtra.map((epc, i) => {
            const info = extraEpcQueries[i]?.data
            const loading = extraEpcQueries[i]?.isLoading
            return (
              <div key={epc} className="flex items-center gap-3 bg-red-50 rounded-lg border border-red-100 px-3 py-2">
                {info?.imageUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={info.imageUrl} alt="" className="w-9 h-9 rounded object-cover bg-white shrink-0" />
                ) : (
                  <div className="w-9 h-9 rounded bg-white shrink-0" />
                )}
                <div className="min-w-0 flex-1">
                  <p className="text-xs font-medium text-red-800 truncate">
                    {loading ? 'Looking up…' : (info?.productName || 'Unknown / foreign tag')}
                  </p>
                  <p className="text-[11px] text-red-600 font-mono truncate">{epc}</p>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

// ── Main component ────────────────────────────────────────────────────────────

export default function GateAlarmsPage() {
  const router             = useRouter()
  const { user, isAdmin, isManager } = useAuth()

  // ── Access control — same tier as Guard Dashboard ─────────────────────────
  useEffect(() => {
    if (user && !isManager) router.replace('/dashboard')
  }, [user, isManager, router])

  // ── Local state ───────────────────────────────────────────────────────────
  const [selectedStoreId, setSelectedStoreId] = useState('')
  const [selectedDate, setSelectedDate]       = useState(todayIso)
  const [page, setPage]                       = useState(0)
  const [expandedId, setExpandedId]           = useState<string | null>(null)

  const handleDateChange  = (d: string)  => { setSelectedDate(d);  setPage(0) }
  const handleStoreChange = (id: string) => { setSelectedStoreId(id); setPage(0) }

  // ── Store resolution ──────────────────────────────────────────────────────
  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })
  const storeId = isAdmin
    ? (selectedStoreId || allStores?.content[0]?.id || '')
    : (user?.storeId ?? '')

  // ── Summary query ─────────────────────────────────────────────────────────
  const {
    data:    summary,
    isLoading: summaryLoading,
    refetch: refetchSummary,
  } = useQuery({
    queryKey: ['gate-alarm-summary', storeId, selectedDate],
    queryFn:  () => gateApi.alarmSummary(storeId, selectedDate),
    enabled:  !!storeId,
  })

  // ── Alarms list query ─────────────────────────────────────────────────────
  const from = `${selectedDate}T00:00:00Z`
  const to   = new Date(new Date(`${selectedDate}T00:00:00Z`).getTime() + 24 * 60 * 60 * 1000).toISOString()

  const {
    data:    alarmsPage,
    isLoading: alarmsLoading,
    refetch: refetchAlarms,
  } = useQuery({
    queryKey: ['gate-alarms', storeId, selectedDate, page],
    queryFn:  () => gateApi.listAlarms({ storeId, from, to, page, size: PAGE_SIZE }),
    enabled: !!storeId,
  })

  const alarms      = alarmsPage?.content       ?? []
  const totalPages  = alarmsPage?.totalPages    ?? 1
  const totalItems  = alarmsPage?.totalElements ?? 0

  const handleRefresh = () => { refetchSummary(); refetchAlarms() }

  // ── Render ────────────────────────────────────────────────────────────────
  if (user && !isManager) return null

  return (
    <>
      <Header title="Gate Alarms (FX9600)" />
      <div className="p-6 space-y-6">

        {/* Explanation banner */}
        <div className="bg-blue-50 border border-blue-200 rounded-xl px-4 py-3 text-sm text-blue-800">
          <strong>Unattended exit-portal alarms</strong> — tags detected leaving the store
          by the fixed FX9600 reader with no bill scan and no guard check. These are raw
          sensor events, separate from the Guard Dashboard's guard-app bill checks.
        </div>

        {/* Store selector (admin) */}
        {isAdmin && allStores && allStores.content.length > 0 && (
          <div className="flex flex-wrap items-center gap-3">
            <label className="text-sm font-medium text-gray-600 shrink-0">Store</label>
            <select value={storeId} onChange={e => handleStoreChange(e.target.value)} className={selectCls}>
              {allStores.content.map(s => (
                <option key={s.id} value={s.id}>{s.name} ({s.storeCode})</option>
              ))}
            </select>
          </div>
        )}

        {/* KPI tiles */}
        <div className="grid grid-cols-2 lg:grid-cols-3 gap-4">
          <StatCard
            title="Total Alarms Today"
            value={summaryLoading ? '…' : (summary?.totalChecks ?? 0).toLocaleString()}
            icon={Radio}
            color="blue"
          />
          <StatCard
            title="Flagged"
            value={summaryLoading ? '…' : (summary?.flagged ?? 0).toLocaleString()}
            sub={summary?.flagRate != null ? `${summary.flagRate.toFixed(1)}% flag rate` : undefined}
            icon={AlertTriangle}
            color="red"
          />
          <StatCard
            title="Tags Detected"
            value={summaryLoading ? '…' : (summary?.totalExtraItems ?? 0).toLocaleString()}
            icon={PackageOpen}
            color="yellow"
          />
        </div>

        {/* Filters bar */}
        <div className="flex flex-wrap items-center gap-3">
          <label className="text-sm font-medium text-gray-600 shrink-0">Date</label>
          <input
            type="date"
            value={selectedDate}
            onChange={e => handleDateChange(e.target.value)}
            className={selectCls}
          />
          <button onClick={handleRefresh} className="btn-secondary flex items-center gap-1.5">
            <RefreshCw size={14} /> Refresh
          </button>
        </div>

        {/* Alarm log */}
        <div className="card">
          <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
            <h2 className="text-sm font-semibold text-gray-700">
              Alarm Log
              {totalItems > 0 && (
                <span className="ml-2 text-xs font-normal text-gray-400">
                  — {totalItems.toLocaleString()} alarm{totalItems !== 1 ? 's' : ''}
                </span>
              )}
            </h2>
          </div>

          <div className="bg-white rounded-xl border border-gray-100 overflow-hidden shadow-sm">
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-100">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="table-th" />
                    <th className="table-th">Time</th>
                    <th className="table-th text-right">Tags Detected</th>
                    <th className="table-th">Outcome</th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-50">
                  {alarmsLoading ? (
                    <tr>
                      <td colSpan={4} className="table-td text-center text-gray-400 py-12">
                        Loading…
                      </td>
                    </tr>
                  ) : alarms.length === 0 ? (
                    <tr>
                      <td colSpan={4} className="table-td text-center text-gray-400 py-12">
                        <Radio size={24} className="mx-auto mb-2 opacity-30" />
                        No FX9600 alarms for this date.
                      </td>
                    </tr>
                  ) : (
                    alarms.map((row: GateCheck) => {
                      const isExpanded = expandedId === row.id
                      const canExpand = row.epcsExtra.length > 0
                      return (
                        <Fragment key={row.id}>
                          <tr
                            className={`hover:bg-gray-50 transition-colors ${canExpand ? 'cursor-pointer' : ''}`}
                            onClick={() => canExpand && setExpandedId(isExpanded ? null : row.id)}
                          >
                            <td className="table-td w-6">
                              {canExpand && (
                                isExpanded
                                  ? <ChevronUp size={14} className="text-gray-400" />
                                  : <ChevronDown size={14} className="text-gray-400" />
                              )}
                            </td>
                            <td className="table-td">
                              <span className="font-mono text-xs text-gray-700">{fmtTime(row.checkedAt)}</span>
                            </td>
                            <td className="table-td text-right">
                              {row.extraCount > 0
                                ? <span className="text-sm font-semibold text-red-600">{row.extraCount}</span>
                                : <span className="text-sm text-gray-400">—</span>
                              }
                            </td>
                            <td className="table-td">
                              <span
                                className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ring-1 ring-inset ${
                                  OUTCOME_BADGE[row.outcome] ?? 'bg-gray-100 text-gray-600 ring-gray-200'
                                }`}
                              >
                                {row.outcome}
                              </span>
                            </td>
                          </tr>
                          {isExpanded && (
                            <tr className="bg-gray-50">
                              <td />
                              <td colSpan={3} className="table-td py-4">
                                <ExpandedAlarmDetails row={row} storeId={storeId} />
                              </td>
                            </tr>
                          )}
                        </Fragment>
                      )
                    })
                  )}
                </tbody>
              </table>
            </div>

            {/* Pagination footer */}
            <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100 bg-gray-50">
              <p className="text-xs text-gray-500">
                Page {page + 1} of {totalPages} · {totalItems.toLocaleString()} total
              </p>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setPage(p => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="btn-secondary py-1 px-2 text-xs disabled:opacity-40 flex items-center gap-1"
                >
                  <ChevronLeft size={14} /> Prev
                </button>
                <button
                  onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                  className="btn-secondary py-1 px-2 text-xs disabled:opacity-40 flex items-center gap-1"
                >
                  Next <ChevronRight size={14} />
                </button>
              </div>
            </div>
          </div>
        </div>

      </div>
    </>
  )
}
