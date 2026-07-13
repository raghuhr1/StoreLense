'use client'

import { useQuery }          from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useRouter }         from 'next/navigation'
import {
  ShieldCheck, CheckCircle2, AlertTriangle, PackageOpen, RefreshCw,
  ChevronLeft, ChevronRight,
} from 'lucide-react'
import Header                from '@/components/layout/Header'
import StatCard              from '@/components/ui/StatCard'
import { gateApi }           from '@/lib/api/gate'
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

const OUTCOME_OPTS = [
  { value: '',          label: 'All Outcomes' },
  { value: 'RELEASED',  label: 'Released'     },
  { value: 'FLAGGED',   label: 'Flagged'      },
  { value: 'ABANDONED', label: 'Abandoned'    },
]

const PAGE_SIZE = 50
const selectCls = 'text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500'

// ── Main component ────────────────────────────────────────────────────────────

export default function GuardDashboardPage() {
  const router             = useRouter()
  const { user, isAdmin, isManager } = useAuth()

  // ── Access control ────────────────────────────────────────────────────────
  useEffect(() => {
    if (user && !isManager) router.replace('/dashboard')
  }, [user, isManager, router])

  // ── Local state ───────────────────────────────────────────────────────────
  const [selectedStoreId, setSelectedStoreId] = useState('')
  const [selectedDate, setSelectedDate]       = useState(todayIso)
  const [outcomeFilter, setOutcomeFilter]     = useState('')
  const [page, setPage]                       = useState(0)

  // Reset page when filters change
  const handleDateChange    = (d: string)  => { setSelectedDate(d);  setPage(0) }
  const handleOutcomeChange = (o: string)  => { setOutcomeFilter(o); setPage(0) }
  const handleStoreChange   = (id: string) => { setSelectedStoreId(id); setPage(0) }

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
    queryKey: ['gate-summary', storeId, selectedDate],
    queryFn:  () => gateApi.summary(storeId, selectedDate),
    enabled:  !!storeId,
  })

  // ── Checks list query ─────────────────────────────────────────────────────
  const from = `${selectedDate}T00:00:00`
  const to   = `${selectedDate}T23:59:59`

  const {
    data:    checksPage,
    isLoading: checksLoading,
    refetch: refetchChecks,
  } = useQuery({
    queryKey: ['gate-checks', storeId, selectedDate, outcomeFilter, page],
    queryFn:  () => gateApi.list({
      storeId,
      from,
      to,
      outcome: outcomeFilter || undefined,
      page,
      size: PAGE_SIZE,
    }),
    enabled: !!storeId,
  })

  const checks     = checksPage?.content      ?? []
  const totalPages = checksPage?.totalPages   ?? 1
  const totalItems = checksPage?.totalElements ?? 0

  const handleRefresh = () => { refetchSummary(); refetchChecks() }

  // ── Render ────────────────────────────────────────────────────────────────
  if (user && !isManager) return null

  return (
    <>
      <Header title="Guard Dashboard" />
      <div className="p-6 space-y-6">

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

        {/* Section A — KPI tiles */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard
            title="Total Checks Today"
            value={summaryLoading ? '…' : (summary?.totalChecks ?? 0).toLocaleString()}
            icon={ShieldCheck}
            color="blue"
          />
          <StatCard
            title="Released"
            value={summaryLoading ? '…' : (summary?.released ?? 0).toLocaleString()}
            icon={CheckCircle2}
            color="green"
          />
          <StatCard
            title="Flagged"
            value={summaryLoading ? '…' : (summary?.flagged ?? 0).toLocaleString()}
            sub={summary?.flagRate != null ? `${(summary.flagRate * 100).toFixed(1)}% flag rate` : undefined}
            icon={AlertTriangle}
            color="red"
          />
          <StatCard
            title="Extra Items Found"
            value={summaryLoading ? '…' : (summary?.totalExtraItems ?? 0).toLocaleString()}
            icon={PackageOpen}
            color="yellow"
          />
        </div>

        {/* Section C — Filters bar */}
        <div className="flex flex-wrap items-center gap-3">
          <label className="text-sm font-medium text-gray-600 shrink-0">Date</label>
          <input
            type="date"
            value={selectedDate}
            onChange={e => handleDateChange(e.target.value)}
            className={selectCls}
          />

          <select value={outcomeFilter} onChange={e => handleOutcomeChange(e.target.value)} className={selectCls}>
            {OUTCOME_OPTS.map(o => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>

          <button onClick={handleRefresh} className="btn-secondary flex items-center gap-1.5">
            <RefreshCw size={14} /> Refresh
          </button>
        </div>

        {/* Section B — Gate Check Log */}
        <div className="card">
          <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
            <h2 className="text-sm font-semibold text-gray-700">
              Gate Check Log
              {totalItems > 0 && (
                <span className="ml-2 text-xs font-normal text-gray-400">
                  — {totalItems.toLocaleString()} check{totalItems !== 1 ? 's' : ''}
                  {outcomeFilter ? ` · ${outcomeFilter.toLowerCase()}` : ''}
                </span>
              )}
            </h2>
          </div>

          <div className="bg-white rounded-xl border border-gray-100 overflow-hidden shadow-sm">
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-100">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="table-th">Time</th>
                    <th className="table-th">Guard</th>
                    <th className="table-th">Bill Ref</th>
                    <th className="table-th text-right">Expected</th>
                    <th className="table-th text-right">Matched</th>
                    <th className="table-th text-right">Extra</th>
                    <th className="table-th">Outcome</th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-50">
                  {checksLoading ? (
                    <tr>
                      <td colSpan={7} className="table-td text-center text-gray-400 py-12">
                        Loading…
                      </td>
                    </tr>
                  ) : checks.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="table-td text-center text-gray-400 py-12">
                        <ShieldCheck size={24} className="mx-auto mb-2 opacity-30" />
                        No gate checks found for this date
                        {outcomeFilter ? ` with outcome "${outcomeFilter.toLowerCase()}"` : ''}.
                      </td>
                    </tr>
                  ) : (
                    checks.map((row: GateCheck) => (
                      <tr key={row.id} className="hover:bg-gray-50 transition-colors">
                        <td className="table-td">
                          <span className="font-mono text-xs text-gray-700">{fmtTime(row.checkedAt)}</span>
                        </td>
                        <td className="table-td">
                          <span className="text-xs text-gray-400">—</span>
                        </td>
                        <td className="table-td">
                          {row.billRef
                            ? <span className="font-mono text-xs text-blue-600">{row.billRef}</span>
                            : <span className="text-xs text-gray-400">—</span>
                          }
                        </td>
                        <td className="table-td text-right">
                          <span className="text-sm text-gray-700">{row.expectedCount}</span>
                        </td>
                        <td className="table-td text-right">
                          <span className="text-sm text-gray-700">{row.matchedCount}</span>
                        </td>
                        <td className="table-td text-right">
                          {row.extraCount > 0
                            ? <span className="text-sm font-semibold text-amber-600">{row.extraCount}</span>
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
                    ))
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
