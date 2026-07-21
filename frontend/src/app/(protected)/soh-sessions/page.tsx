'use client'

import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { MapPin, RotateCw, ClipboardList } from 'lucide-react'
import Link              from 'next/link'
import Header            from '@/components/layout/Header'
import { statusBadge }   from '@/components/ui/Badge'
import { sohApi }        from '@/lib/api/soh'
import { storesApi }     from '@/lib/api/stores'
import { useAuth }       from '@/lib/auth/AuthContext'
import { fmtDateTime }   from '@/lib/utils'
import type { SohSession } from '@/types'

const selectCls = 'text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500'

function titleCase(s: string) {
  return s.split(' ').map(w => w.charAt(0) + w.slice(1).toLowerCase()).join(' ')
}

function locationLabel(locationCode: string | null, sectionCode: string | null, zoneRegion: string | null): string {
  if (locationCode === 'SALES_FLOOR') {
    return sectionCode ? `Sales Floor – ${titleCase(sectionCode)}` : 'Sales Floor'
  }
  if (locationCode === 'BACKROOM') return 'Backroom'
  if (locationCode) return titleCase(locationCode)
  // Sessions created without locationCode (e.g. ERP-triggered) still carry zoneRegion —
  // normalize its enum-style text ("SALES_FLOOR"/"BACK_ROOM") to a readable label.
  if (zoneRegion) return titleCase(zoneRegion.replace(/_/g, ' '))
  return 'Full Store'
}

// Groups sessions that share a cycleCountId (an ERP-triggered audit split across
// Sales Floor / Backroom, plus the cancelled Full Store placeholder) into one
// nested card, instead of showing all three as unrelated flat cards. Sessions
// with no cycleCountId (plain manual scans) still render standalone.
type RenderItem =
  | { kind: 'group';  cycleCountId: string; sessions: SohSession[] }
  | { kind: 'single'; session: SohSession }

function groupSessions(sessions: SohSession[]): RenderItem[] {
  const items: RenderItem[] = []
  const seenGroups = new Set<string>()

  for (const s of sessions) {
    if (!s.cycleCountId) {
      items.push({ kind: 'single', session: s })
      continue
    }
    if (seenGroups.has(s.cycleCountId)) continue
    seenGroups.add(s.cycleCountId)
    items.push({
      kind: 'group',
      cycleCountId: s.cycleCountId,
      sessions: sessions.filter(x => x.cycleCountId === s.cycleCountId),
    })
  }
  return items
}

function AuditGroupCard({ cycleCountId, sessions }: { cycleCountId: string; sessions: SohSession[] }) {
  const earliestStart = sessions.reduce((min, s) => s.startedAt < min ? s.startedAt : min, sessions[0].startedAt)

  return (
    <div className="border border-brand-200 bg-brand-50/40 rounded-xl p-4 space-y-3 sm:col-span-2 lg:col-span-3">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <ClipboardList size={15} className="text-brand-600 flex-shrink-0" />
          <p className="text-sm font-semibold text-gray-900">ERP Audit</p>
          <span className="text-xs text-gray-400">{fmtDateTime(earliestStart)}</span>
        </div>
        <Link
          href={`/cycle-count/${cycleCountId}/reconcile`}
          className="text-xs font-medium text-brand-600 hover:underline"
        >
          View Reconciliation →
        </Link>
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
        {sessions.map(s => <SessionCard key={s.id} session={s} />)}
      </div>
    </div>
  )
}

function SessionCard({ session }: { session: SohSession }) {
  const label    = locationLabel(session.locationCode, session.sectionCode, session.zoneRegion)
  const isActive = session.status === 'in_progress' || session.status === 'paused'

  return (
    <div className="border border-gray-200 rounded-xl p-4 space-y-3 bg-white hover:border-brand-300 transition-colors">
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2">
          <MapPin size={15} className="text-brand-500 flex-shrink-0 mt-0.5" />
          <p className="text-sm font-semibold text-gray-900">{label}</p>
        </div>
        {statusBadge(session.status)}
      </div>

      <dl className="grid grid-cols-3 gap-2 text-center">
        <div className="bg-gray-50 rounded-lg py-2">
          <dt className="text-[10px] text-gray-400 uppercase tracking-wide">Reads</dt>
          <dd className="text-sm font-bold text-gray-900">{session.totalEpcReads.toLocaleString()}</dd>
        </div>
        <div className="bg-gray-50 rounded-lg py-2">
          <dt className="text-[10px] text-gray-400 uppercase tracking-wide">Unique</dt>
          <dd className="text-sm font-bold text-gray-900">{session.uniqueEpcCount.toLocaleString()}</dd>
        </div>
        <div className="bg-gray-50 rounded-lg py-2">
          <dt className="text-[10px] text-gray-400 uppercase tracking-wide">Started</dt>
          <dd className="text-xs font-medium text-gray-700">{fmtDateTime(session.startedAt)}</dd>
        </div>
      </dl>

      {session.result && (
        <div className="grid grid-cols-2 gap-2 pt-1">
          <div className="bg-blue-50 rounded-lg p-2 text-center">
            <p className="text-[10px] text-blue-500 uppercase tracking-wide">Floor</p>
            <p className="text-xs font-semibold text-blue-800">
              {session.result.floorUnitsCounted} / {session.result.floorUnitsExpected}
            </p>
            <p className={`text-[10px] font-medium ${session.result.floorVariance < 0 ? 'text-red-600' : 'text-green-600'}`}>
              {session.result.floorVariance >= 0 ? '+' : ''}{session.result.floorVariance}
            </p>
          </div>
          <div className="bg-amber-50 rounded-lg p-2 text-center">
            <p className="text-[10px] text-amber-500 uppercase tracking-wide">Backroom</p>
            <p className="text-xs font-semibold text-amber-800">
              {session.result.backroomUnitsCounted} / {session.result.backroomUnitsExpected}
            </p>
            <p className={`text-[10px] font-medium ${session.result.backroomVariance < 0 ? 'text-red-600' : 'text-green-600'}`}>
              {session.result.backroomVariance >= 0 ? '+' : ''}{session.result.backroomVariance}
            </p>
          </div>
        </div>
      )}

      {isActive && (
        <div className="flex items-center gap-2 pt-1">
          <div className="w-2 h-2 rounded-full bg-yellow-400 animate-pulse" />
          <span className="text-xs text-yellow-700 font-medium">
            {session.status === 'paused' ? 'Paused — resume on device' : 'Scanning in progress…'}
          </span>
        </div>
      )}
    </div>
  )
}

export default function SohSessionsPage() {
  const { user, isAdmin } = useAuth()
  const [selectedStoreId, setSelectedStoreId] = useState('')
  const [statusFilter, setStatusFilter] = useState('')

  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })

  const storeId = isAdmin
    ? (selectedStoreId || allStores?.content[0]?.id || '')
    : (user?.storeId ?? '')

  const { data: page, isLoading } = useQuery({
    queryKey: ['soh-sessions', storeId, statusFilter],
    queryFn:  () => sohApi.listSessions(storeId, { status: statusFilter || undefined, page: 0, size: 50 }),
    enabled:  !!storeId,
  })

  const sessions   = useMemo(() => page?.content ?? [], [page])
  const renderList = useMemo(() => groupSessions(sessions), [sessions])

  return (
    <>
      <Header title="SOH Sessions" />
      <div className="p-6 space-y-5">

        <div className="flex items-center gap-3 flex-wrap">
          {isAdmin && allStores && (
            <div className="flex items-center gap-2">
              <label className="text-sm font-medium text-gray-600 shrink-0">Store</label>
              <select value={storeId} onChange={e => setSelectedStoreId(e.target.value)} className={selectCls}>
                {allStores.content.map(s => (
                  <option key={s.id} value={s.id}>{s.name}</option>
                ))}
              </select>
            </div>
          )}
          <div className="flex items-center gap-2">
            <label className="text-sm font-medium text-gray-600 shrink-0">Status</label>
            <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)} className={selectCls}>
              <option value="">All</option>
              <option value="in_progress">In Progress</option>
              <option value="paused">Paused</option>
              <option value="completed">Completed</option>
              <option value="cancelled">Cancelled</option>
            </select>
          </div>
        </div>

        <div className="card">
          <div className="flex items-center gap-2 mb-4">
            <RotateCw size={16} className="text-brand-500" />
            <h2 className="text-sm font-semibold text-gray-700">Sessions ({sessions.length})</h2>
          </div>

          {isLoading ? (
            <p className="py-8 text-center text-sm text-gray-400">Loading…</p>
          ) : sessions.length === 0 ? (
            <p className="py-8 text-center text-sm text-gray-400">
              No SOH sessions yet. Scans started from the handheld (Sales Floor, Backroom, or Full Store) will show up here.
            </p>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {renderList.map(item => item.kind === 'group'
                ? <AuditGroupCard key={item.cycleCountId} cycleCountId={item.cycleCountId} sessions={item.sessions} />
                : <SessionCard key={item.session.id} session={item.session} />
              )}
            </div>
          )}
        </div>

      </div>
    </>
  )
}
