'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { use }               from 'react'
import { useRouter }         from 'next/navigation'
import { CheckCircle2, XCircle, ScanLine } from 'lucide-react'
import Header                from '@/components/layout/Header'
import { statusBadge }       from '@/components/ui/Badge'
import { sohApi }            from '@/lib/api/soh'
import { fmtDateTime, fmtPct } from '@/lib/utils'

export default function CycleCountDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id }  = use(params)
  const router  = useRouter()
  const qc      = useQueryClient()

  const { data: session } = useQuery({
    queryKey: ['soh-session', id],
    queryFn:  () => sohApi.getSession(id),
  })

  const completeMut = useMutation({
    mutationFn: () => sohApi.completeSession(id),
    onSuccess:  () => { qc.invalidateQueries({ queryKey: ['soh-sessions'] }); router.push('/cycle-count') },
  })

  const cancelMut = useMutation({
    mutationFn: () => sohApi.cancelSession(id, 'Manually cancelled'),
    onSuccess:  () => { qc.invalidateQueries({ queryKey: ['soh-sessions'] }); router.push('/cycle-count') },
  })

  if (!session) return null

  const canEdit = session.status === 'in_progress' || session.status === 'created'

  return (
    <>
      <Header title={`Count Session — ${id.slice(-8)}`} />
      <div className="p-6 space-y-6">

        <div className="card">
          <div className="flex items-start justify-between mb-4">
            <div>
              <h2 className="text-lg font-semibold text-gray-900">
                {session.sessionType.replace(/_/g, ' ')} Count
              </h2>
              <p className="text-sm text-gray-400 mt-1">Started {fmtDateTime(session.startedAt)}</p>
            </div>
            {statusBadge(session.status)}
          </div>

          <dl className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            {[
              { label: 'Total Reads',   value: session.totalEpcReads.toLocaleString() },
              { label: 'Unique EPCs',   value: session.uniqueEpcCount.toLocaleString() },
              { label: 'Session Type',  value: session.sessionType },
              { label: 'Completed',     value: fmtDateTime(session.completedAt) },
            ].map(({ label, value }) => (
              <div key={label} className="bg-gray-50 rounded-lg p-3">
                <dt className="text-xs text-gray-500">{label}</dt>
                <dd className="text-sm font-semibold text-gray-900 mt-1">{value}</dd>
              </div>
            ))}
          </dl>

          {session.notes && (
            <p className="mt-4 text-sm text-gray-600 bg-blue-50 px-3 py-2 rounded-lg">{session.notes}</p>
          )}
        </div>

        {canEdit && (
          <div className="flex gap-3">
            <button
              onClick={() => completeMut.mutate()}
              disabled={completeMut.isPending}
              className="btn-primary"
            >
              <CheckCircle2 size={16} />
              {completeMut.isPending ? 'Completing…' : 'Complete Session'}
            </button>
            <button
              onClick={() => cancelMut.mutate()}
              disabled={cancelMut.isPending}
              className="flex items-center gap-2 px-4 py-2 bg-white text-red-600 border border-red-200 rounded-lg text-sm font-medium hover:bg-red-50 transition-colors"
            >
              <XCircle size={16} />
              Cancel Session
            </button>
          </div>
        )}

        <div className="card">
          <p className="text-sm text-gray-500">
            EPC reads are processed in real-time via the RFID pipeline.
            Complete the session to generate the SOH result and variance report.
          </p>
        </div>

      </div>
    </>
  )
}
