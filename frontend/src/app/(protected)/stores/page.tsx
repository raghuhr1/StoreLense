'use client'

import { useQuery }   from '@tanstack/react-query'
import { useMemo }    from 'react'
import { type ColumnDef } from '@tanstack/react-table'
import Link           from 'next/link'
import Header         from '@/components/layout/Header'
import DataTable      from '@/components/ui/DataTable'
import { statusBadge } from '@/components/ui/Badge'
import { storesApi }  from '@/lib/api/stores'
import { fmt }        from '@/lib/utils'
import type { Store } from '@/types'

export default function StoresPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['stores'],
    queryFn:  () => storesApi.list({ size: 500 }),
  })

  const columns = useMemo<ColumnDef<Store, unknown>[]>(() => [
    {
      accessorKey: 'storeCode',
      header: 'Code',
      cell: i => (
        <Link href={`/stores/${i.row.original.id}`} className="font-semibold text-brand-600 hover:underline">
          {i.getValue<string>()}
        </Link>
      ),
    },
    { accessorKey: 'name',          header: 'Name' },
    { accessorKey: 'city',          header: 'City' },
    { accessorKey: 'stateProvince', header: 'State' },
    { accessorKey: 'countryCode',   header: 'Country' },
    { accessorKey: 'timezone',      header: 'Timezone' },
    { accessorKey: 'active',        header: 'Status',  cell: i => statusBadge(i.getValue<boolean>() ? 'active' : 'inactive') },
    { accessorKey: 'erpStoreCode',  header: 'ERP Code' },
    { accessorKey: 'createdAt',     header: 'Created', cell: i => fmt(i.getValue<string>()) },
  ], [])

  return (
    <>
      <Header title="Stores" />
      <div className="p-6">
        <div className="card">
          <DataTable
            data={data?.content ?? []}
            columns={columns}
            isLoading={isLoading}
            searchable
            searchPlaceholder="Search stores…"
          />
        </div>
      </div>
    </>
  )
}
