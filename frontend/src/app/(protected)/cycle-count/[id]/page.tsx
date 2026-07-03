'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { use, useState, useMemo } from 'react'
import { useRouter }  from 'next/navigation'
import {
  Plus, Upload, XCircle, GitCompare, MapPin,
  CheckCircle2, PauseCircle, RotateCcw, AlertTriangle,
} from 'lucide-react'
import Link             from 'next/link'
import Header           from '@/components/layout/Header'
import { statusBadge }  from '@/components/ui/Badge'
import { cycleCountApi } from '@/lib/api/cycle-count'
import { storesApi }    from '@/lib/api/stores'
import { useAuth }      from '@/lib/auth/AuthContext'
import { fmt, fmtDateTime } from '@/lib/utils'
import type { SohSession, StoreLocation } from '@/types'

function locationLabel(locationCode: string | null, sectionCode: string | null): string {
  if (!locationCode) return 'Unknown'
  if (locationCode === 'SALES_FLOOR') {
    return sectionCode ? `Sales Floor – ${titleCase(sectionCode)}` : 'Sales Floor'
  }
  if (locationCode === 'BACKROOM') return 'Backroom'
  return titleCase(locationCode)
}

function titleCase(s: string) {
  return s.charAt(0) + s.slice(1).toLowerCase()
}

const FALLBACK_LOCATIONS: Array<{ locationCode: string; sectionCode: null; displayName: string }> = [
  { locationCode: 'SALES_FLOOR', sectionCode: null, displayName: 'Sales Floor' },
  { locationCode: 'BACKROOM',    sectionCode: null, displayName: 'Backroom'    },
]

function SessionCard({ session }: { session: SohSession }) {
  const label   = locationLabel(session.locationCode, session.sectionCode)
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

export default function CycleCountDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id }     = use(params)
  const router     = useRouter()
  const { user }   = useAuth()
  const qc         = useQueryClient()

  const [showPicker, setShowPicker] = useState(false)
  const [pickerLoc,  setPickerLoc]  = useState('')
  const [pickerSec,  setPickerSec]  = useState('')
  const [pickerErr,  setPickerErr]  = useState<string | null>(null)

  const { data: count, isLoading } = useQuery({
    queryKey: ['cycle-count', id],
    queryFn:  () => cycleCountApi.get(id),
  })

  const storeId = count?.storeId ?? user?.storeId ?? ''

  const { data: storeLocations } = useQuery({
    queryKey: ['store-locations', storeId],
    queryFn:  () => storesApi.locations(storeId),
    enabled:  !!storeId,
  })

  const availableLocations = useMemo(() => {
    if (!storeLocations || storeLocations.length === 0) return FALLBACK_LOCATIONS
    return storeLocations.filter(l => l.isActive)
  }, [storeLocations])

  const usedLocKeys = useMemo(() => {
    return new Set(
      (count?.sessions ?? []).map(s => `${s.locationCode}|${s.sectionCode ?? ''}`)
    )
  }, [count?.sessions])

  const unusedLocations = useMemo(() => {
    return availableLocations.filter(
      l => !usedLocKeys.has(`${l.locationCode}|${(l as StoreLocation).sectionCode ?? ''}`)
    )
  }, [availableLocations, usedLocKeys])

  const startSessionMut = useMutation({
    mutationFn: () => {
      const [lc, sc] = pickerLoc.split('|')
      return cycleCountApi.startSession(id, {
        storeId,
        locationCode: lc,
        sectionCode:  sc || null,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cycle-count', id] })
      setShowPicker(false)
      setPickerLoc('')
      setPickerSec('')
      setPickerErr(null)
    },
    onError: (e: Error) => setPickerErr(e.message ?? 'Failed to start session'),
  })

  const uploadMut = useMutation({
    mutationFn: () => cycleCountApi.upload(id),
    onSuccess:  () => qc.invalidateQueries({ queryKey: ['cycle-count', id] }),
  })

  const closeMut = useMutation({
    mutationFn: () => cycleCountApi.close(id),
    onSuccess:  () => { qc.invalidateQueries({ queryKey: ['cycle-count', id] }); router.push('/cycle-count') },
  })

  if (isLoading || !count) {
    return (
      <>
        <Header title="Cycle Count" />
        <div className="p-6 flex justify-center">
          <div className="w-8 h-8 border-4 border-brand-600 border-t-transparent rounded-full animate-spin" />
        </div>
      </>
    )
  }

  const sessions       = count.sessions ?? []
  const canAddLocation = ['DRAFT', 'RUNNING'].includes(count.status) && unusedLocations.length > 0
  const canUpload      = count.status === 'COMPLETED'
  const canReconcile   = ['UPLOADED', 'RECONCILED'].includes(count.status)
  const canClose       = !['CLOSED'].includes(count.status)
  const isEditable     = ['DRAFT', 'RUNNING'].includes(count.status)

  const totalReads  = sessions.reduce((a, s) => a + s.totalEpcReads, 0)
  const totalUnique = sessions.reduce((a, s) => a + s.uniqueEpcCount, 0)
  const doneCount   = sessions.filter(s => ['completed','uploaded','reconciled','closed'].includes(s.status)).length

  return (
    <>
      <Header title={`Cycle Count — ${fmt(count.countDate)}`} />
      <div className="p-6 space-y-6">

        <Link href="/cycle-count" className="text-sm text-brand-600 hover:underline">
          ← All cycle counts
        </Link>

        {/* Header card */}
        <div className="card space-y-4">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h2 className="text-lg font-semibold text-gray-900">{fmt(count.countDate)}</h2>
              {count.notes && (
                <p className="text-sm text-gray-500 mt-0.5">{count.notes}</p>
              )}
            </div>
            {statusBadge(count.status)}
          </div>

          <dl className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            {[
              { label: 'Sessions',    value: `${doneCount} / ${sessions.length} done` },
              { label: 'Total Reads', value: totalReads.toLocaleString() },
              { label: 'Unique EPCs', value: totalUnique.toLocaleString() },
              { label: 'Created',     value: fmtDateTime(count.createdAt) },
            ].map(({ label, value }) => (
              <div key={label} className="bg-gray-50 rounded-lg p-3">
                <dt className="text-xs text-gray-500">{label}</dt>
                <dd className="text-sm font-semibold text-gray-900 mt-0.5">{value}</dd>
              </div>
            ))}
          </dl>

          {/* Action buttons */}
          <div className="flex flex-wrap gap-2 pt-1">
            {canAddLocation && (
              <button onClick={() => { setShowPicker(true); setPickerErr(null) }} className="btn-primary">
                <Plus size={15} /> Add Location
              </button>
            )}
            {canUpload && (
              <button
                onClick={() => uploadMut.mutate()}
                disabled={uploadMut.isPending}
                className="btn-primary"
              >
                <Upload size={15} />
                {uploadMut.isPending ? 'Uploading…' : 'Upload to ERP'}
              </button>
            )}
            {canReconcile && (
              <Link
                href={`/cycle-count/${id}/reconcile`}
                className="flex items-center gap-2 px-4 py-2 bg-purple-600 text-white rounded-lg text-sm font-medium hover:bg-purple-700 transition-colors"
              >
                <GitCompare size={15} />
                {count.status === 'RECONCILED' ? 'View Reconciliation' : 'Run Reconciliation'}
              </Link>
            )}
            {canClose && (
              <button
                onClick={() => { if (confirm('Close this cycle count?')) closeMut.mutate() }}
                disabled={closeMut.isPending}
                className="flex items-center gap-2 px-4 py-2 bg-white text-gray-600 border border-gray-200 rounded-lg text-sm font-medium hover:bg-gray-50 transition-colors"
              >
                <XCircle size={15} />
                {closeMut.isPending ? 'Closing…' : 'Close'}
              </button>
            )}
          </div>

          {uploadMut.isError && (
            <p className="text-xs text-red-500 bg-red-50 border border-red-200 rounded px-3 py-2">
              Upload failed. Ensure all sessions are completed before uploading.
            </p>
          )}
        </div>

        {/* Sessions by location */}
        <div>
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-sm font-semibold text-gray-700">
              Location Sessions ({sessions.length})
            </h2>
            {isEditable && unusedLocations.length === 0 && sessions.length > 0 && (
              <span className="text-xs text-green-600 flex items-center gap-1">
                <CheckCircle2 size={13} /> All locations covered
              </span>
            )}
          </div>

          {sessions.length === 0 ? (
            <div className="border-2 border-dashed border-gray-200 rounded-xl p-10 text-center text-gray-400 space-y-2">
              <MapPin size={32} className="mx-auto" strokeWidth={1.2} />
              <p className="text-sm">No sessions yet. Click "Add Location" to start scanning a location.</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {sessions.map(s => (
                <SessionCard key={s.id} session={s} />
              ))}
            </div>
          )}
        </div>

        {/* Status legend */}
        {sessions.length > 0 && (
          <div className="flex flex-wrap gap-4 text-xs text-gray-500">
            <span className="flex items-center gap-1.5">
              <PauseCircle size={12} className="text-yellow-500" /> Paused — resume via device
            </span>
            <span className="flex items-center gap-1.5">
              <CheckCircle2 size={12} className="text-green-500" /> Completed
            </span>
            <span className="flex items-center gap-1.5">
              <RotateCcw size={12} className="text-blue-500" /> Uploaded / Reconciled
            </span>
            <span className="flex items-center gap-1.5">
              <AlertTriangle size={12} className="text-red-500" /> Cancelled / Failed
            </span>
          </div>
        )}

      </div>

      {/* Location picker modal */}
      {showPicker && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl p-6 w-[400px] shadow-xl space-y-4">
            <h3 className="font-semibold text-gray-900">Select Location to Count</h3>

            {unusedLocations.length === 0 ? (
              <p className="text-sm text-gray-500">All configured locations are already covered.</p>
            ) : (
              <div className="space-y-2 max-h-60 overflow-y-auto">
                {unusedLocations.map(loc => {
                  const lc = loc.locationCode
                  const sc = (loc as StoreLocation).sectionCode ?? null
                  const key = `${lc}|${sc ?? ''}`
                  return (
                    <button
                      key={key}
                      onClick={() => setPickerLoc(key)}
                      className={`w-full text-left px-4 py-3 border rounded-xl transition-colors text-sm ${
                        pickerLoc === key
                          ? 'border-brand-500 bg-brand-50 text-brand-800 font-medium'
                          : 'border-gray-200 hover:bg-gray-50 text-gray-700'
                      }`}
                    >
                      <div className="flex items-center gap-2">
                        <MapPin size={14} className={pickerLoc === key ? 'text-brand-500' : 'text-gray-400'} />
                        {(loc as StoreLocation).displayName ?? locationLabel(lc, sc)}
                      </div>
                      <p className="text-xs text-gray-400 mt-0.5 ml-5">
                        {lc}{sc ? ` / ${sc}` : ''}
                      </p>
                    </button>
                  )
                })}
              </div>
            )}

            {pickerErr && (
              <p className="text-xs text-red-500 bg-red-50 border border-red-200 rounded px-3 py-2">
                {pickerErr}
              </p>
            )}

            <div className="flex gap-3 justify-end">
              <button
                onClick={() => { setShowPicker(false); setPickerLoc(''); setPickerErr(null) }}
                className="btn-secondary"
              >
                Cancel
              </button>
              <button
                onClick={() => startSessionMut.mutate()}
                disabled={!pickerLoc || startSessionMut.isPending}
                className="btn-primary disabled:opacity-40"
              >
                {startSessionMut.isPending ? 'Starting…' : 'Start Session'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
