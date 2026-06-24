'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { type ColumnDef }   from '@tanstack/react-table'
import { ArrowLeft, MapPin, PackageCheck, Loader2 } from 'lucide-react'
import Link                 from 'next/link'
import Header               from '@/components/layout/Header'
import DataTable            from '@/components/ui/DataTable'
import { storesApi }        from '@/lib/api/stores'
import { inventoryApi }     from '@/lib/api/inventory'
import { useAuth }          from '@/lib/auth/AuthContext'
import { fmtDateTime }      from '@/lib/utils'
import type { InboundEpcRow, Zone } from '@/types'

export default function PutawayPage() {
  const { user, isAdmin } = useAuth()
  const qc = useQueryClient()

  const [selectedStoreId, setSelectedStoreId] = useState('')
  const [selectedZoneId,  setSelectedZoneId]  = useState('')
  const [selectedEpcs,    setSelectedEpcs]    = useState<Set<string>>(new Set())
  const [showConfirm,     setShowConfirm]     = useState(false)
  const [resultMsg,       setResultMsg]       = useState<string | null>(null)

  const { data: allStores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 100 }),
    enabled:  isAdmin,
  })

  const storeId = isAdmin
    ? (selectedStoreId || allStores?.content[0]?.id || '')
    : (user?.storeId ?? '')

  const { data: zones = [] } = useQuery<Zone[]>({
    queryKey: ['zones', storeId],
    queryFn:  () => storesApi.zones(storeId),
    enabled:  !!storeId,
  })

  const { data: inboundEpcs = [], isLoading } = useQuery<InboundEpcRow[]>({
    queryKey: ['inbound-pending', storeId],
    queryFn:  () => inventoryApi.inboundPending(storeId),
    enabled:  !!storeId,
  })

  const putawayMut = useMutation({
    mutationFn: () => inventoryApi.putaway({
      storeId,
      zoneId: selectedZoneId,
      epcs:   Array.from(selectedEpcs),
    }),
    onSuccess: (res) => {
      setResultMsg(res?.message ?? 'Put away complete')
      setSelectedEpcs(new Set())
      setShowConfirm(false)
      qc.invalidateQueries({ queryKey: ['inbound-pending', storeId] })
    },
  })

  // Group pending EPCs by product for display
  const grouped = useMemo(() => {
    const map = new Map<string, { name: string; sku: string; rows: InboundEpcRow[] }>()
    for (const row of inboundEpcs) {
      const key = row.productId ?? 'unknown'
      if (!map.has(key)) {
        map.set(key, { name: row.productName ?? '(unknown product)', sku: row.sku ?? '—', rows: [] })
      }
      map.get(key)!.rows.push(row)
    }
    return Array.from(map.values()).sort((a, b) => a.name.localeCompare(b.name))
  }, [inboundEpcs])

  const allSelected = inboundEpcs.length > 0 && selectedEpcs.size === inboundEpcs.length

  const toggleAll = () => {
    if (allSelected) setSelectedEpcs(new Set())
    else setSelectedEpcs(new Set(inboundEpcs.map(r => r.epc)))
  }

  const toggleEpc = (epc: string) => {
    setSelectedEpcs(prev => {
      const next = new Set(prev)
      next.has(epc) ? next.delete(epc) : next.add(epc)
      return next
    })
  }

  const columns = useMemo<ColumnDef<InboundEpcRow, unknown>[]>(() => [
    {
      id: 'select',
      header: () => (
        <input type="checkbox" checked={allSelected} onChange={toggleAll}
          className="rounded border-gray-300" />
      ),
      cell: ({ row: r }) => (
        <input type="checkbox"
          checked={selectedEpcs.has(r.original.epc)}
          onChange={() => toggleEpc(r.original.epc)}
          className="rounded border-gray-300" />
      ),
      size: 40,
    },
    {
      id: 'epc',
      header: 'EPC',
      accessorKey: 'epc',
      cell: ({ getValue }) => (
        <span className="font-mono text-xs text-gray-700">{getValue<string>()}</span>
      ),
    },
    {
      id: 'product',
      header: 'Product',
      accessorFn: r => r.productName ?? '—',
      cell: ({ row: r }) => (
        <div>
          <p className="text-sm font-medium text-gray-900">{r.original.productName ?? '—'}</p>
          <p className="font-mono text-xs text-gray-400">{r.original.sku ?? '—'}</p>
        </div>
      ),
    },
    {
      accessorKey: 'firstSeenAt',
      header: 'Received At',
      cell: i => <span className="text-xs text-gray-500">{fmtDateTime(i.getValue<string>())}</span>,
    },
  ], [selectedEpcs, allSelected])  // eslint-disable-line react-hooks/exhaustive-deps

  const selectCls = 'text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-800 focus:outline-none focus:ring-2 focus:ring-brand-500'

  return (
    <>
      <Header title="Put Away — Inbound EPCs" />
      <div className="p-6 space-y-5">

        <Link href="/inbound"
          className="inline-flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-800">
          <ArrowLeft size={14} /> Back to Inbound
        </Link>

        {/* Controls row */}
        <div className="flex flex-wrap items-center gap-3">
          {isAdmin && allStores && (
            <>
              <label className="text-sm font-medium text-gray-600 shrink-0">Store</label>
              <select value={storeId} onChange={e => setSelectedStoreId(e.target.value)} className={selectCls}>
                {allStores.content.map(s => (
                  <option key={s.id} value={s.id}>{s.name}</option>
                ))}
              </select>
            </>
          )}

          <label className="text-sm font-medium text-gray-600 shrink-0">Put-Away Zone</label>
          <select value={selectedZoneId} onChange={e => setSelectedZoneId(e.target.value)} className={selectCls}>
            <option value="">— select zone —</option>
            {zones.map(z => (
              <option key={z.id} value={z.id}>{z.name}</option>
            ))}
          </select>
        </div>

        {/* Summary stats */}
        <div className="grid grid-cols-2 lg:grid-cols-3 gap-4">
          {[
            { label: 'Pending EPCs',    value: inboundEpcs.length,  cls: inboundEpcs.length > 0 ? 'text-amber-600' : 'text-gray-900' },
            { label: 'Selected',        value: selectedEpcs.size,   cls: selectedEpcs.size > 0  ? 'text-blue-700'  : 'text-gray-900' },
            { label: 'Products',        value: grouped.length },
          ].map(s => (
            <div key={s.label} className="card">
              <p className="text-xs text-gray-500">{s.label}</p>
              <p className={`text-2xl font-bold mt-0.5 ${s.cls ?? 'text-gray-900'}`}>{s.value}</p>
            </div>
          ))}
        </div>

        {/* Result banner */}
        {resultMsg && (
          <div className="flex items-center gap-2 p-3 bg-green-50 border border-green-200 rounded-lg text-green-800 text-sm">
            <PackageCheck size={16} />
            {resultMsg}
            <button onClick={() => setResultMsg(null)} className="ml-auto text-green-500 hover:text-green-800">✕</button>
          </div>
        )}

        {/* EPC table */}
        <div className="card">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <MapPin size={16} className="text-gray-400" />
              <h2 className="text-sm font-semibold text-gray-700">
                EPCs Awaiting Put-Away ({inboundEpcs.length})
              </h2>
            </div>
            <button
              onClick={() => setShowConfirm(true)}
              disabled={selectedEpcs.size === 0 || !selectedZoneId || putawayMut.isPending}
              className="btn-primary disabled:opacity-40 disabled:cursor-not-allowed"
            >
              {putawayMut.isPending
                ? <><Loader2 size={14} className="animate-spin" /> Putting away…</>
                : <><PackageCheck size={14} /> Put Away ({selectedEpcs.size})</>}
            </button>
          </div>

          <DataTable
            data={inboundEpcs}
            columns={columns}
            isLoading={isLoading}
            searchable
            searchPlaceholder="Search EPC or product…"
          />
        </div>

        {/* Confirm modal */}
        {showConfirm && (
          <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-white rounded-2xl p-6 w-96 shadow-xl space-y-4">
              <div className="flex items-center gap-2">
                <PackageCheck size={20} className="text-green-600" />
                <h3 className="font-semibold text-gray-900">Confirm Put-Away</h3>
              </div>
              <p className="text-sm text-gray-600">
                Move <strong>{selectedEpcs.size}</strong> EPC{selectedEpcs.size !== 1 ? 's' : ''} to{' '}
                <strong>{zones.find(z => z.id === selectedZoneId)?.name ?? selectedZoneId}</strong>?
              </p>
              <p className="text-xs text-gray-400">
                Each EPC will be marked <code>in_store</code> and position history will be recorded.
              </p>
              <div className="flex gap-3 justify-end pt-2">
                <button onClick={() => setShowConfirm(false)} className="btn-secondary">Cancel</button>
                <button
                  onClick={() => putawayMut.mutate()}
                  disabled={putawayMut.isPending}
                  className="btn-primary"
                >
                  {putawayMut.isPending ? 'Processing…' : 'Confirm'}
                </button>
              </div>
            </div>
          </div>
        )}

      </div>
    </>
  )
}
