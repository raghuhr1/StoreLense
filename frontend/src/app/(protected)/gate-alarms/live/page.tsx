'use client'

import { useQuery, useQueries, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ShieldAlert, CheckCircle2, Radio } from 'lucide-react'
import { gateApi }      from '@/lib/api/gate'
import { inventoryApi } from '@/lib/api/inventory'
import { storesApi }    from '@/lib/api/stores'
import { useAuth }      from '@/lib/auth/AuthContext'
import { groupEpcs }    from '@/lib/epcGrouping'
import type { GateCheck, GateCheckResolution } from '@/types'

const RESOLUTION_OPTS: { value: GateCheckResolution; label: string }[] = [
  { value: 'REVIEWED_FALSE_ALARM', label: 'False alarm' },
  { value: 'CONFIRMED_THEFT',      label: 'Confirmed theft' },
  { value: 'ESCALATED',            label: 'Escalate' },
]

function fmtTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-AU', {
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  })
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

  const { data: alarms } = useQuery({
    queryKey: ['gate-alarms-live', storeId],
    queryFn:  () => gateApi.liveAlarms(storeId, 5),
    enabled:  !!storeId,
    refetchInterval: 3000,
  })

  const resolveMut = useMutation({
    mutationFn: ({ id, resolution }: { id: string; resolution: GateCheckResolution }) =>
      gateApi.resolve(id, resolution),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['gate-alarms-live', storeId] }),
  })

  const current = alarms?.[0]
  const queued  = alarms?.slice(1) ?? []

  const extraEpcQueries = useQueries({
    queries: (current?.epcsExtra ?? []).map(epc => ({
      queryKey: ['identify-epc', epc, storeId],
      queryFn:  () => inventoryApi.identifyEpc(epc, storeId),
    })),
  })

  // Foreign/unregistered tags aren't in our inventory at all — nothing to show
  // a guard, so they're dropped from display (but still counted/logged; the
  // alarm itself and its extraCount are unaffected). Still-loading groups are
  // kept so a real item doesn't flash and disappear while resolving.
  const epcGroups = groupEpcs(current?.epcsExtra ?? [], extraEpcQueries)
    .filter(g => g.loading || g.info)

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
        {queued.length > 0 && (
          <span className="text-xs font-semibold text-amber-400">
            +{queued.length} more waiting
          </span>
        )}
      </div>

      {/* Main stage */}
      <div className="flex-1 flex items-center justify-center p-8">
        {!current ? (
          <div className="text-center text-slate-500">
            <CheckCircle2 size={64} className="mx-auto mb-4 opacity-30" />
            <p className="text-xl font-medium text-slate-400">No active alerts</p>
            <p className="text-sm mt-1">Watching the exit gate…</p>
          </div>
        ) : (
          <div className="w-full max-w-4xl">
            <div className="flex items-center gap-3 mb-6 animate-pulse">
              <ShieldAlert size={28} className="text-red-500" />
              <span className="text-2xl font-bold text-red-500">UNBILLED ITEM DETECTED</span>
              <span className="ml-auto font-mono text-slate-400 text-lg">{fmtTime(current.checkedAt)}</span>
            </div>

            {epcGroups.length === 0 ? (
              <div className="text-center text-slate-500 mb-8 py-10 border-2 border-dashed border-slate-700 rounded-2xl">
                <ShieldAlert size={32} className="mx-auto mb-2 opacity-40" />
                <p className="text-sm">No recognized inventory item — tag not in our system.</p>
              </div>
            ) : (
              <div className={`grid gap-4 mb-8 ${epcGroups.length > 1 ? 'grid-cols-2 md:grid-cols-3' : 'grid-cols-1'}`}>
                {epcGroups.map(group => (
                  <div key={group.key} className="relative bg-slate-900 border-2 border-red-500/50 rounded-2xl p-4 flex flex-col items-center">
                    {group.epcs.length > 1 && (
                      <span className="absolute -top-2 -right-2 bg-red-600 text-white text-xs font-bold w-7 h-7 rounded-full flex items-center justify-center shadow-lg">
                        ×{group.epcs.length}
                      </span>
                    )}
                    <div className="w-full aspect-square bg-slate-800 rounded-xl overflow-hidden flex items-center justify-center mb-3">
                      {group.info?.imageUrl ? (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img src={group.info.imageUrl} alt="" className="w-full h-full object-cover" />
                      ) : (
                        <ShieldAlert size={40} className="text-slate-600" />
                      )}
                    </div>
                    <p className="text-lg font-semibold text-center">
                      {group.loading ? 'Looking up…' : group.info?.productName}
                    </p>
                    {group.info?.sku && <p className="text-xs text-slate-400 font-mono">{group.info.sku}</p>}
                    <p className="text-[11px] text-slate-500 font-mono mt-1 text-center break-all">
                      {group.epcs.length > 1 ? `${group.epcs.length} tags` : group.epcs[0]}
                    </p>
                  </div>
                ))}
              </div>
            )}

            <div className="flex items-center justify-center gap-3">
              {RESOLUTION_OPTS.map(o => (
                <button
                  key={o.value}
                  onClick={() => resolveMut.mutate({ id: current.id, resolution: o.value })}
                  disabled={resolveMut.isPending}
                  className="px-6 py-3 rounded-xl font-semibold text-sm bg-slate-800 hover:bg-slate-700 border border-slate-700 disabled:opacity-50 transition-colors"
                >
                  {o.label}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Queue strip */}
      {queued.length > 0 && (
        <div className="border-t border-slate-800 px-6 py-3 flex items-center gap-3 overflow-x-auto">
          <span className="text-[11px] text-slate-500 shrink-0">STILL PENDING:</span>
          {queued.map((a: GateCheck) => (
            <span key={a.id} className="text-[11px] font-mono bg-slate-800 text-amber-400 px-2 py-1 rounded shrink-0">
              {fmtTime(a.checkedAt)} · {a.extraCount} tag{a.extraCount !== 1 ? 's' : ''}
            </span>
          ))}
        </div>
      )}
    </div>
  )
}
