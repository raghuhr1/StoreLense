'use client'

import { useEffect, useState } from 'react'
import { useQuery, useQueries, useMutation, useQueryClient } from '@tanstack/react-query'
import { ShieldAlert, CheckCircle2, Radio } from 'lucide-react'
import { gateApi }      from '@/lib/api/gate'
import { inventoryApi } from '@/lib/api/inventory'
import { storesApi }    from '@/lib/api/stores'
import { useAuth }      from '@/lib/auth/AuthContext'
import type { GateCheck, GateCheckResolution, IdentifyEpcResponse } from '@/types'

const RESOLUTION_OPTS: { value: GateCheckResolution; label: string }[] = [
  { value: 'REVIEWED_FALSE_ALARM', label: 'False alarm' },
  { value: 'CONFIRMED_THEFT',      label: 'Confirmed theft' },
  { value: 'ESCALATED',            label: 'Escalate' },
]

// The device re-reports a standing item roughly every report_interval_s while
// it's still in the portal. An EPC that hasn't been re-reported inside this
// window has left the portal — its alarm rows stay open in the backend for
// audit/admin review, but the live screen doesn't need to keep showing its
// card once the item (and the buzzer) have moved on.
const RECENT_WINDOW_MS = 90_000

function fmtTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-AU', {
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  })
}

interface EpcActivity {
  alarmIds:    Set<string>
  firstSeenAt: string
  lastSeenAt:  string
}

// Flattens every currently-unresolved alarm row into one entry per physical
// EPC — several gate_checks rows can (and do) cover the same standing item,
// since the device re-fires on it every report cycle. Anything not seen
// recently is dropped: it's left the reader even though its alarm rows stay
// open in the backend.
function activeEpcs(alarms: GateCheck[], now: number): Map<string, EpcActivity> {
  const perEpc = new Map<string, EpcActivity>()
  for (const alarm of alarms) {
    for (const epc of alarm.epcsExtra) {
      const existing = perEpc.get(epc)
      if (existing) {
        existing.alarmIds.add(alarm.id)
        if (alarm.checkedAt < existing.firstSeenAt) existing.firstSeenAt = alarm.checkedAt
        if (alarm.checkedAt > existing.lastSeenAt)  existing.lastSeenAt  = alarm.checkedAt
      } else {
        perEpc.set(epc, { alarmIds: new Set([alarm.id]), firstSeenAt: alarm.checkedAt, lastSeenAt: alarm.checkedAt })
      }
    }
  }
  for (const [epc, activity] of perEpc) {
    if (now - new Date(activity.lastSeenAt).getTime() > RECENT_WINDOW_MS) perEpc.delete(epc)
  }
  return perEpc
}

interface LiveGroup {
  key:         string
  info:        IdentifyEpcResponse | null
  loading:     boolean
  failed:      boolean
  epcs:        string[]
  alarmIds:    Set<string>
  firstSeenAt: string
}

function LiveWall({ storeId, alarms, now, onResolve, resolvingIdsKey }: {
  storeId: string
  alarms: GateCheck[]
  now: number
  onResolve: (alarmIds: string[], resolution: GateCheckResolution) => void
  resolvingIdsKey: string | null
}) {
  const epcActivity = activeEpcs(alarms, now)
  const epcs = [...epcActivity.keys()]

  const queries = useQueries({
    queries: epcs.map(epc => ({
      queryKey: ['identify-epc', epc, storeId],
      queryFn:  () => inventoryApi.identifyEpc(epc, storeId),
    })),
  })

  // Group the still-active EPCs by resolved product — several identical items
  // at the gate together show one photo with a quantity badge, not one card
  // per raw tag or per report batch.
  const groups = new Map<string, LiveGroup>()
  epcs.forEach((epc, i) => {
    const q       = queries[i]
    const info    = q?.data ?? null
    const loading = !!q?.isLoading
    const failed  = !!q?.isError
    const activity = epcActivity.get(epc)!
    const key = info?.productId ?? (loading ? `loading-${epc}` : failed ? `failed-${epc}` : `unknown-${epc}`)
    const existing = groups.get(key)
    if (existing) {
      existing.epcs.push(epc)
      activity.alarmIds.forEach(id => existing.alarmIds.add(id))
      if (activity.firstSeenAt < existing.firstSeenAt) existing.firstSeenAt = activity.firstSeenAt
      if (info) { existing.info = info; existing.loading = false; existing.failed = false }
    } else {
      groups.set(key, {
        key, info, loading, failed,
        epcs: [epc], alarmIds: new Set(activity.alarmIds), firstSeenAt: activity.firstSeenAt,
      })
    }
  })

  // Confirmed-not-inventory groups (settled, no result) have nothing for a
  // guard to act on and are dropped rather than shown as an empty box. A
  // still-loading or failed lookup is kept — a transient hiccup must never
  // make a real item look foreign and vanish.
  const visible = [...groups.values()]
    .filter(g => g.loading || g.failed || !!g.info)
    .sort((a, b) => (a.firstSeenAt < b.firstSeenAt ? 1 : -1))

  if (visible.length === 0) {
    return (
      <div className="h-full flex items-center justify-center">
        <div className="text-center text-slate-500">
          <CheckCircle2 size={64} className="mx-auto mb-4 opacity-30" />
          <p className="text-xl font-medium text-slate-400">No active alerts</p>
          <p className="text-sm mt-1">Watching the exit gate…</p>
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-5xl mx-auto grid gap-4 grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
      {visible.map(group => {
        const alarmIds = [...group.alarmIds]
        const idsKey   = alarmIds.slice().sort().join(',')
        return (
          <div key={group.key} className="relative bg-slate-900 border-2 border-red-500/40 rounded-2xl p-4 flex flex-col items-center">
            <div className="w-full flex items-center gap-2 mb-2">
              <ShieldAlert size={16} className="text-red-500 shrink-0" />
              <span className="text-[11px] font-bold text-red-500 tracking-wide">UNBILLED</span>
              <span className="ml-auto font-mono text-slate-500 text-[10px]">{fmtTime(group.firstSeenAt)}</span>
            </div>
            {group.epcs.length > 1 && (
              <span className="absolute top-9 right-3 bg-red-600 text-white text-xs font-bold w-6 h-6 rounded-full flex items-center justify-center shadow-lg">
                ×{group.epcs.length}
              </span>
            )}
            <div className="w-full aspect-square bg-slate-800 rounded-lg overflow-hidden flex items-center justify-center mb-2">
              {group.info?.imageUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={group.info.imageUrl} alt="" className="w-full h-full object-cover" />
              ) : (
                <ShieldAlert size={28} className="text-slate-600" />
              )}
            </div>
            <p className="text-sm font-semibold text-center leading-tight">
              {group.loading ? 'Looking up…' : group.failed ? 'Lookup failed — retrying…' : group.info?.productName}
            </p>
            {group.info?.sku && <p className="text-[11px] text-slate-400 font-mono mb-2">{group.info.sku}</p>}
            <div className="flex flex-wrap items-center justify-center gap-1 mt-auto">
              {RESOLUTION_OPTS.map(o => (
                <button
                  key={o.value}
                  onClick={() => onResolve(alarmIds, o.value)}
                  disabled={resolvingIdsKey === idsKey}
                  className="px-2 py-1 rounded-md font-medium text-[10px] bg-slate-800 hover:bg-slate-700 border border-slate-700 disabled:opacity-50 transition-colors"
                >
                  {o.label}
                </button>
              ))}
            </div>
          </div>
        )
      })}
    </div>
  )
}

export default function LiveGateAlarmsPage() {
  const qc = useQueryClient()
  const { user, isAdmin } = useAuth()
  const [selectedStoreId, setSelectedStoreId] = useState('')
  const [now, setNow] = useState(() => Date.now())
  const [resolvingIdsKey, setResolvingIdsKey] = useState<string | null>(null)

  // Ticks independent of polling so the "seen recently" window keeps moving
  // even when react-query's structural sharing reuses the same data — the
  // fade-out is a function of the clock, not of new data arriving.
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 5000)
    return () => clearInterval(t)
  }, [])

  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })
  const storeId = isAdmin
    ? (selectedStoreId || allStores?.content[0]?.id || '')
    : (user?.storeId ?? '')

  // Polled frequently since the gate-guard device itself batches alarm
  // reports (see report_interval_s in its config) — this is the fastest the
  // web screen can possibly learn about a new alarm; it can't outrun that
  // upstream batching delay.
  const { data: alarms } = useQuery({
    queryKey: ['gate-alarms-live', storeId],
    queryFn:  () => gateApi.liveAlarms(storeId, 10),
    enabled:  !!storeId,
    refetchInterval: 2000,
  })

  const resolveMut = useMutation({
    // A live card can span several underlying gate_checks rows (the device
    // re-firing on the same standing item) — resolving it must clear all of
    // them, or the merged duplicates reappear as "new" alarms next poll.
    mutationFn: ({ ids, resolution }: { ids: string[]; resolution: GateCheckResolution }) =>
      Promise.all(ids.map(id => gateApi.resolve(id, resolution))),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['gate-alarms-live', storeId] }); setResolvingIdsKey(null) },
    onError:   () => setResolvingIdsKey(null),
  })

  const alarmList = alarms ?? []
  const activeCount = activeEpcs(alarmList, now).size

  return (
    <div className="min-h-screen bg-slate-950 text-white flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between px-6 py-4 border-b border-slate-800">
        <div className="flex items-center gap-2">
          <Radio size={18} className="text-teal-400" />
          <span className="font-semibold text-sm tracking-wide text-slate-300">GATE ALARM — LIVE</span>
        </div>
        {isAdmin && allStores && allStores.content.length > 0 && (
          <select
            value={storeId}
            onChange={e => setSelectedStoreId(e.target.value)}
            className="text-xs bg-slate-800 border border-slate-700 rounded px-2 py-1 text-slate-200"
          >
            {allStores.content.map(s => (
              <option key={s.id} value={s.id}>{s.name} ({s.storeCode})</option>
            ))}
          </select>
        )}
        {activeCount > 0 && (
          <span className="text-xs font-semibold text-amber-400">
            {activeCount} item{activeCount > 1 ? 's' : ''} at gate
          </span>
        )}
      </div>

      {/* Live wall — one persistent card per physical item currently at the
          gate; it fades away once the device stops re-reporting it (item has
          left the reader), independent of when a guard clears it. */}
      <div className="flex-1 p-6">
        {storeId && (
          <LiveWall
            storeId={storeId}
            alarms={alarmList}
            now={now}
            resolvingIdsKey={resolvingIdsKey}
            onResolve={(ids, resolution) => {
              setResolvingIdsKey(ids.slice().sort().join(','))
              resolveMut.mutate({ ids, resolution })
            }}
          />
        )}
      </div>
    </div>
  )
}
