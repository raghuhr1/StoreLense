'use client'

import { useQuery, useQueries, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ShieldAlert, CheckCircle2, Radio } from 'lucide-react'
import { gateApi }      from '@/lib/api/gate'
import { inventoryApi } from '@/lib/api/inventory'
import { storesApi }    from '@/lib/api/stores'
import { useAuth }      from '@/lib/auth/AuthContext'
import { groupEpcs, isKnownOrPending } from '@/lib/epcGrouping'
import type { GateCheck, GateCheckResolution } from '@/types'

const RESOLUTION_OPTS: { value: GateCheckResolution; label: string }[] = [
  { value: 'REVIEWED_FALSE_ALARM', label: 'False alarm' },
  { value: 'CONFIRMED_THEFT',      label: 'Confirmed theft' },
  { value: 'ESCALATED',            label: 'Escalate' },
]

// The device re-alarms the same standing item roughly every report_interval_s
// while it's still in the portal, so the live feed can hold several separate
// gate_checks rows for one physical item. Collapse those into a single card
// keyed by its EPC set — the image and buzzer only need to register once,
// not restack every time the device re-fires.
function dedupeAlarms(alarms: GateCheck[]): { primary: GateCheck; allIds: string[] }[] {
  const groups = new Map<string, GateCheck[]>()
  for (const alarm of alarms) {
    const key = [...alarm.epcsExtra].sort().join('|')
    const existing = groups.get(key)
    if (existing) existing.push(alarm)
    else groups.set(key, [alarm])
  }
  return Array.from(groups.values()).map(group => {
    // Earliest occurrence — the moment this item first triggered — is what
    // the guard should see as "when," not the latest re-fire.
    const primary = group.reduce((a, b) => (a.checkedAt < b.checkedAt ? a : b))
    return { primary, allIds: group.map(a => a.id) }
  })
}

function fmtTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-AU', {
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  })
}

// ── One alert card — never swapped out for a newer one, only removed when
//    a guard clears it, so nothing already on screen ever disappears on its
//    own. ───────────────────────────────────────────────────────────────────
function AlertCard({
  alarm, storeId, onResolve, isResolving,
}: {
  alarm: GateCheck
  storeId: string
  onResolve: (resolution: GateCheckResolution) => void
  isResolving: boolean
}) {
  const extraEpcQueries = useQueries({
    queries: alarm.epcsExtra.map(epc => ({
      queryKey: ['identify-epc', epc, storeId],
      queryFn:  () => inventoryApi.identifyEpc(epc, storeId),
    })),
  })

  // Foreign/unregistered tags aren't in our inventory — nothing to show, so
  // they're dropped from display. Still-loading AND failed-lookup groups are
  // kept: a network/auth hiccup must never make a real item look "foreign"
  // and vanish from the guard's screen — only a confirmed 404 counts.
  const epcGroups = groupEpcs(alarm.epcsExtra, extraEpcQueries)
    .filter(isKnownOrPending)

  // Every tag on this alarm resolved and none are inventory — nothing for a
  // guard to act on, so the card itself disappears rather than showing an
  // empty "not in our system" box. Cards with a still-loading/failed lookup
  // stay on screen until that settles.
  if (epcGroups.length === 0) return null

  return (
    <div className="w-full bg-slate-900 border-2 border-red-500/40 rounded-2xl p-5">
      <div className="flex items-center gap-3 mb-4">
        <ShieldAlert size={22} className="text-red-500 shrink-0" />
        <span className="text-lg font-bold text-red-500">UNBILLED ITEM DETECTED</span>
        <span className="ml-auto font-mono text-slate-400 text-sm">{fmtTime(alarm.checkedAt)}</span>
      </div>

      <div className={`grid gap-3 mb-4 ${epcGroups.length > 1 ? 'grid-cols-2 md:grid-cols-4' : 'grid-cols-2 md:grid-cols-3'}`}>
        {epcGroups.map(group => (
          <div key={group.key} className="relative bg-slate-800/60 border border-red-500/30 rounded-xl p-3 flex flex-col items-center">
            {group.epcs.length > 1 && (
              <span className="absolute -top-2 -right-2 bg-red-600 text-white text-xs font-bold w-6 h-6 rounded-full flex items-center justify-center shadow-lg">
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
            {group.info?.sku && <p className="text-[11px] text-slate-400 font-mono">{group.info.sku}</p>}
          </div>
        ))}
      </div>

      <div className="flex items-center justify-center gap-2">
        {RESOLUTION_OPTS.map(o => (
          <button
            key={o.value}
            onClick={() => onResolve(o.value)}
            disabled={isResolving}
            className="px-4 py-2 rounded-lg font-medium text-xs bg-slate-800 hover:bg-slate-700 border border-slate-700 disabled:opacity-50 transition-colors"
          >
            {o.label}
          </button>
        ))}
      </div>
    </div>
  )
}

export default function LiveGateAlarmsPage() {
  const qc = useQueryClient()
  const { user, isAdmin } = useAuth()
  const [selectedStoreId, setSelectedStoreId] = useState('')

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
    // A dedup group can span several underlying gate_checks rows (the device
    // re-firing on the same standing item) — resolving the card must clear
    // all of them, or the merged duplicates reappear as "new" alarms next poll.
    mutationFn: ({ ids, resolution }: { ids: string[]; resolution: GateCheckResolution }) =>
      Promise.all(ids.map(id => gateApi.resolve(id, resolution))),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['gate-alarms-live', storeId] }),
  })

  const dedupedAlarms = dedupeAlarms(alarms ?? [])

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
        {dedupedAlarms.length > 0 && (
          <span className="text-xs font-semibold text-amber-400">
            {dedupedAlarms.length} unresolved
          </span>
        )}
      </div>

      {/* Alert wall — every unresolved alarm stays visible until cleared;
          new ones only ever get added, never swap out what's on screen. */}
      <div className="flex-1 p-6">
        {dedupedAlarms.length === 0 ? (
          <div className="h-full flex items-center justify-center">
            <div className="text-center text-slate-500">
              <CheckCircle2 size={64} className="mx-auto mb-4 opacity-30" />
              <p className="text-xl font-medium text-slate-400">No active alerts</p>
              <p className="text-sm mt-1">Watching the exit gate…</p>
            </div>
          </div>
        ) : (
          <div className="max-w-3xl mx-auto space-y-4">
            {dedupedAlarms.map(({ primary, allIds }) => (
              <AlertCard
                key={primary.id}
                alarm={primary}
                storeId={storeId}
                onResolve={resolution => resolveMut.mutate({ ids: allIds, resolution })}
                isResolving={resolveMut.isPending && resolveMut.variables?.ids.join(',') === allIds.join(',')}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
