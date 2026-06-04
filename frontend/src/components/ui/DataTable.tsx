'use client'

import {
  useReactTable, getCoreRowModel, getSortedRowModel,
  getPaginationRowModel, getFilteredRowModel,
  flexRender, ColumnDef, SortingState, ColumnFiltersState,
} from '@tanstack/react-table'
import { useState }       from 'react'
import { ChevronUp, ChevronDown, ChevronsUpDown } from 'lucide-react'
import { cn }             from '@/lib/utils'

interface Props<T> {
  data:     T[]
  columns:  ColumnDef<T, unknown>[]
  pageSize?: number
  searchable?: boolean
  searchPlaceholder?: string
  isLoading?: boolean
}

export default function DataTable<T>({
  data, columns, pageSize = 20, searchable, searchPlaceholder = 'Search…', isLoading,
}: Props<T>) {
  const [sorting, setSorting]       = useState<SortingState>([])
  const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([])
  const [globalFilter, setGlobal]   = useState('')

  const table = useReactTable({
    data,
    columns,
    state: { sorting, columnFilters, globalFilter },
    onSortingChange:       setSorting,
    onColumnFiltersChange: setColumnFilters,
    onGlobalFilterChange:  setGlobal,
    getCoreRowModel:       getCoreRowModel(),
    getSortedRowModel:     getSortedRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    getFilteredRowModel:   getFilteredRowModel(),
    initialState: { pagination: { pageSize } },
  })

  return (
    <div className="space-y-3">
      {searchable && (
        <input
          value={globalFilter}
          onChange={e => setGlobal(e.target.value)}
          placeholder={searchPlaceholder}
          className="input-field max-w-xs"
        />
      )}

      <div className="bg-white rounded-xl border border-gray-100 overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-100">
            <thead className="bg-gray-50">
              {table.getHeaderGroups().map(hg => (
                <tr key={hg.id}>
                  {hg.headers.map(header => (
                    <th key={header.id} className="table-th select-none">
                      {header.isPlaceholder ? null : (
                        <div
                          className={cn('flex items-center gap-1', header.column.getCanSort() && 'cursor-pointer')}
                          onClick={header.column.getToggleSortingHandler()}
                        >
                          {flexRender(header.column.columnDef.header, header.getContext())}
                          {header.column.getCanSort() && (
                            header.column.getIsSorted() === 'asc'  ? <ChevronUp size={14} /> :
                            header.column.getIsSorted() === 'desc' ? <ChevronDown size={14} /> :
                            <ChevronsUpDown size={14} className="text-gray-300" />
                          )}
                        </div>
                      )}
                    </th>
                  ))}
                </tr>
              ))}
            </thead>

            <tbody className="bg-white divide-y divide-gray-50">
              {isLoading ? (
                <tr><td colSpan={columns.length} className="table-td text-center text-gray-400 py-12">Loading…</td></tr>
              ) : table.getRowModel().rows.length === 0 ? (
                <tr><td colSpan={columns.length} className="table-td text-center text-gray-400 py-12">No records found</td></tr>
              ) : (
                table.getRowModel().rows.map(row => (
                  <tr key={row.id} className="hover:bg-gray-50 transition-colors">
                    {row.getVisibleCells().map(cell => (
                      <td key={cell.id} className="table-td">
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </td>
                    ))}
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100 bg-gray-50">
          <p className="text-xs text-gray-500">
            {table.getFilteredRowModel().rows.length} record{table.getFilteredRowModel().rows.length !== 1 ? 's' : ''}
          </p>
          <div className="flex items-center gap-2">
            <button onClick={() => table.previousPage()} disabled={!table.getCanPreviousPage()}
              className="btn-secondary py-1 px-2 text-xs disabled:opacity-40">Prev</button>
            <span className="text-xs text-gray-600">
              {table.getState().pagination.pageIndex + 1} / {table.getPageCount() || 1}
            </span>
            <button onClick={() => table.nextPage()} disabled={!table.getCanNextPage()}
              className="btn-secondary py-1 px-2 text-xs disabled:opacity-40">Next</button>
          </div>
        </div>
      </div>
    </div>
  )
}
