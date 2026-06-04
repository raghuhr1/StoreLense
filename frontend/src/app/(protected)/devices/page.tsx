'use client'

import { useQuery }   from '@tanstack/react-query'
import { useMemo }    from 'react'
import { type ColumnDef } from '@tanstack/react-table'
import { Cpu, Wifi, WifiOff } from 'lucide-react'
import Header         from '@/components/layout/Header'
import DataTable      from '@/components/ui/DataTable'
import StatCard       from '@/components/ui/StatCard'
import { statusBadge } from '@/components/ui/Badge'
import { storesApi }  from '@/lib/api/stores'
import { fmtDateTime } from '@/lib/utils'
import { useAuth }    from '@/lib/auth/AuthContext'
import type { RfidReader } from '@/types'

const ONLINE_THRESHOLD_MS = 5 * 60 * 1000   // 5 minutes

export default function DevicesPage() {
  const { user }    = useAuth()
  const storeId     = user?.storeId ?? ''
  const isAdmin     = user?.role === 'ADMIN'

  // For admin: fetch readers across all stores. For managers: own store only.
  const { data: stores } = useQuery({
    queryKey: ['stores-all'],
    queryFn:  () => storesApi.list({ size: 500 }),
    enabled:  isAdmin,
  })

  const { data: ownReaders, isLoading } = useQuery({
    queryKey: ['readers', storeId],
    queryFn:  () => storesApi.readers(storeId),
    enabled:  !!storeId,
  })

  const readers: RfidReader[] = ownReaders ?? []

  const now = Date.now()
  const online  = readers.filter(r => r.lastHeartbeatAt && now - new Date(r.lastHeartbeatAt).getTime() < ONLINE_THRESHOLD_MS)
  const offline = readers.filter(r => !r.lastHeartbeatAt || now - new Date(r.lastHeartbeatAt).getTime() >= ONLINE_THRESHOLD_MS)

  const columns = useMemo<ColumnDef<RfidReader, unknown>[]>(() => [
    { accessorKey: 'readerCode',    header: 'Reader Code', cell: i => <span className="font-mono text-sm">{i.getValue<string>()}</span> },
    { accessorKey: 'readerType',    header: 'Type' },
    { accessorKey: 'ipAddress',     header: 'IP Address',  cell: i => i.getValue<string|null>() ?? '—' },
    { accessorKey: 'antennaCount',  header: 'Antennas' },
    { accessorKey: 'txPowerDbm',    header: 'Tx Power',   cell: i => i.getValue<number|null>() != null ? `${i.getValue<number>()}dBm` : '—' },
    { accessorKey: 'firmwareVersion', header: 'Firmware',  cell: i => i.getValue<string|null>() ?? '—' },
    {
      id: 'online',
      header: 'Status',
      cell: ({ row }) => {
        const hb   = row.original.lastHeartbeatAt
        const isOn = hb && now - new Date(hb).getTime() < ONLINE_THRESHOLD_MS
        return isOn
          ? <span className="flex items-center gap-1 text-green-600 text-xs"><Wifi size={14}/> Online</span>
          : <span className="flex items-center gap-1 text-gray-400 text-xs"><WifiOff size={14}/> Offline</span>
      },
    },
    { accessorKey: 'lastHeartbeatAt', header: 'Last Seen', cell: i => fmtDateTime(i.getValue<string|null>()) },
    { accessorKey: 'active',          header: 'Enabled',   cell: i => statusBadge(i.getValue<boolean>() ? 'active' : 'inactive') },
  ], [now])

  return (
    <>
      <Header title="Devices" />
      <div className="p-6 space-y-6">

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard title="Total Readers"  value={readers.length}  icon={Cpu}    color="blue" />
          <StatCard title="Online"         value={online.length}   icon={Wifi}   color="green" />
          <StatCard title="Offline"        value={offline.length}  icon={WifiOff} color={offline.length ? 'red' : 'green'} />
          <StatCard title="Fixed Readers"  value={readers.filter(r => r.readerType === 'fixed').length} icon={Cpu} color="blue" />
        </div>

        <div className="card">
          <DataTable
            data={readers}
            columns={columns}
            isLoading={isLoading}
            searchable
            searchPlaceholder="Search devices…"
          />
        </div>

      </div>
    </>
  )
}
