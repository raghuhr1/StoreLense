'use client'

import { useState, useMemo }   from 'react'
import { type ColumnDef }      from '@tanstack/react-table'
import { ArrowLeftRight, Plus } from 'lucide-react'
import Header                  from '@/components/layout/Header'
import DataTable               from '@/components/ui/DataTable'
import { statusBadge }         from '@/components/ui/Badge'
import { fmtDateTime }         from '@/lib/utils'
import type { Transfer }       from '@/types'

// Transfers are not yet a backend endpoint — placeholder with empty data
// When the backend transfer API is implemented, replace with useQuery()
export default function TransfersPage() {
  const [showModal, setShowModal] = useState(false)

  const data: Transfer[] = []   // wire to transfersApi.list() when available

  const columns = useMemo<ColumnDef<Transfer, unknown>[]>(() => [
    { accessorKey: 'id',          header: 'ID',        cell: i => <span className="font-mono text-xs">{i.getValue<string>().slice(-8)}</span> },
    { accessorKey: 'fromStoreId', header: 'From Store', cell: i => i.getValue<string>().slice(-8) },
    { accessorKey: 'toStoreId',   header: 'To Store',   cell: i => i.getValue<string>().slice(-8) },
    { accessorKey: 'quantity',    header: 'Qty' },
    { accessorKey: 'status',      header: 'Status',    cell: i => statusBadge(i.getValue<string>()) },
    { accessorKey: 'initiatedAt', header: 'Initiated', cell: i => fmtDateTime(i.getValue<string>()) },
    { accessorKey: 'receivedAt',  header: 'Received',  cell: i => fmtDateTime(i.getValue<string|null>()) },
  ], [])

  return (
    <>
      <Header title="Transfers" />
      <div className="p-6 space-y-4">

        <div className="flex items-center justify-between">
          <p className="text-sm text-gray-500">Inter-store stock transfers tracked via EPC tag status changes.</p>
          <button onClick={() => setShowModal(true)} className="btn-primary">
            <Plus size={16} /> New Transfer
          </button>
        </div>

        <div className="card">
          {data.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-gray-400">
              <ArrowLeftRight size={40} className="mb-3 opacity-30" />
              <p className="text-sm">No transfers recorded yet.</p>
              <p className="text-xs mt-1">Transfers are created when EPCs change store.</p>
            </div>
          ) : (
            <DataTable data={data} columns={columns} searchable />
          )}
        </div>

        {showModal && (
          <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-white rounded-2xl p-6 w-96 shadow-xl space-y-4">
              <h3 className="font-semibold text-gray-900">New Transfer</h3>
              <p className="text-sm text-gray-500">Transfer API is pending backend implementation.</p>
              <button onClick={() => setShowModal(false)} className="btn-secondary w-full">Close</button>
            </div>
          </div>
        )}

      </div>
    </>
  )
}
