'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { type ColumnDef }   from '@tanstack/react-table'
import { useForm }          from 'react-hook-form'
import { zodResolver }      from '@hookform/resolvers/zod'
import { z }                from 'zod'
import { Plus, Pencil, Trash2, ShieldAlert } from 'lucide-react'
import Header               from '@/components/layout/Header'
import DataTable            from '@/components/ui/DataTable'
import { rolesApi, type RoleDto } from '@/lib/api/roles'
import { fmtDateTime }      from '@/lib/utils'

const SYSTEM_ROLES = ['ADMIN', 'STORE_MANAGER', 'STORE_ASSOCIATE', 'REFILL_ASSOCIATE', 'SECURITY_GUARD']

const schema = z.object({
  name:        z.string()
    .min(1, 'Required')
    .max(50)
    .regex(/^[A-Z][A-Z0-9_]*$/, 'Uppercase letters, digits and underscores only (e.g. FLOOR_SUPERVISOR)'),
  description: z.string().max(500).optional(),
})
type FormValues = z.infer<typeof schema>

export default function RolesPage() {
  const qc = useQueryClient()
  const [open, setOpen]       = useState(false)
  const [editing, setEditing] = useState<RoleDto | null>(null)
  const [deleting, setDeleting] = useState<RoleDto | null>(null)

  const { data: roles = [], isLoading } = useQuery({
    queryKey: ['roles'],
    queryFn:  rolesApi.list,
  })

  const form = useForm<FormValues>({ resolver: zodResolver(schema) })

  const createMut = useMutation({
    mutationFn: (v: FormValues) => rolesApi.create(v),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['roles'] }); closeModal() },
  })

  const updateMut = useMutation({
    mutationFn: ({ id, body }: { id: string; body: FormValues }) => rolesApi.update(id, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['roles'] }); closeModal() },
  })

  const deleteMut = useMutation({
    mutationFn: (id: string) => rolesApi.remove(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['roles'] }); setDeleting(null) },
  })

  const openCreate = () => { form.reset({ name: '', description: '' }); setOpen(true) }

  const openEdit = (r: RoleDto) => {
    setEditing(r)
    form.reset({ name: r.name, description: r.description ?? '' })
  }

  const closeModal = () => { setOpen(false); setEditing(null); form.reset() }

  const isSystem = (name: string) => SYSTEM_ROLES.includes(name)

  const columns = useMemo<ColumnDef<RoleDto, unknown>[]>(() => [
    {
      accessorKey: 'name',
      header: 'Role Name',
      cell: ({ row }) => (
        <div className="flex items-center gap-2">
          <span className="font-mono text-sm font-semibold text-gray-900">{row.original.name}</span>
          {isSystem(row.original.name) && (
            <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-semibold bg-blue-50 text-blue-700 border border-blue-100">
              <ShieldAlert size={10} /> system
            </span>
          )}
        </div>
      ),
    },
    {
      accessorKey: 'description',
      header: 'Description',
      cell: i => <span className="text-gray-500">{i.getValue<string | null>() ?? '—'}</span>,
    },
    {
      accessorKey: 'userCount',
      header: 'Users',
      cell: i => (
        <span className={`font-semibold tabular-nums ${i.getValue<number>() > 0 ? 'text-gray-900' : 'text-gray-400'}`}>
          {i.getValue<number>()}
        </span>
      ),
    },
    {
      accessorKey: 'createdAt',
      header: 'Created',
      cell: i => fmtDateTime(i.getValue<string>()),
    },
    {
      id: 'actions',
      header: '',
      cell: ({ row }) => {
        const system = isSystem(row.original.name)
        return (
          <div className="flex items-center gap-1">
            <button
              onClick={() => openEdit(row.original)}
              disabled={system}
              title={system ? 'System roles cannot be renamed' : 'Edit role'}
              className="p-1.5 text-gray-400 hover:text-brand-600 hover:bg-gray-100 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <Pencil size={15} />
            </button>
            <button
              onClick={() => setDeleting(row.original)}
              disabled={system || row.original.userCount > 0}
              title={
                system ? 'System roles cannot be deleted'
                : row.original.userCount > 0 ? `${row.original.userCount} user(s) assigned — reassign first`
                : 'Delete role'
              }
              className="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <Trash2 size={15} />
            </button>
          </div>
        )
      },
    },
  ], [])

  const isEdit = !!editing

  return (
    <>
      <Header title="Roles" />
      <div className="p-6 space-y-4">

        <div className="flex items-start justify-between gap-4">
          <p className="text-sm text-gray-500 max-w-xl">
            System roles (<span className="font-mono text-xs">ADMIN</span>, <span className="font-mono text-xs">STORE_MANAGER</span>, etc.) cannot be renamed or deleted.
            Custom roles appear in user forms and JWTs but share base permissions until backend annotations are extended.
          </p>
          <button onClick={openCreate} className="btn-primary shrink-0">
            <Plus size={16} /> New Role
          </button>
        </div>

        <div className="card">
          <DataTable
            data={roles}
            columns={columns}
            isLoading={isLoading}
            searchable
            searchPlaceholder="Search roles…"
          />
        </div>

        {/* Create / Edit modal */}
        {(open || isEdit) && (
          <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-white rounded-2xl p-6 w-[440px] shadow-xl">
              <h3 className="font-semibold text-gray-900 mb-4">
                {isEdit ? `Edit Role — ${editing!.name}` : 'New Role'}
              </h3>
              <form
                onSubmit={form.handleSubmit(v =>
                  isEdit ? updateMut.mutate({ id: editing!.id, body: v }) : createMut.mutate(v)
                )}
                className="space-y-3"
              >
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">
                    Role Name *{' '}
                    <span className="font-normal text-gray-400">(UPPERCASE_SNAKE_CASE)</span>
                  </label>
                  <input
                    {...form.register('name')}
                    className="input-field font-mono"
                    placeholder="FLOOR_SUPERVISOR"
                    disabled={isEdit && isSystem(editing!.name)}
                  />
                  {form.formState.errors.name && (
                    <p className="text-xs text-red-500 mt-0.5">{form.formState.errors.name.message}</p>
                  )}
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Description</label>
                  <textarea
                    {...form.register('description')}
                    className="input-field resize-none"
                    rows={3}
                    placeholder="What this role is allowed to do…"
                  />
                </div>

                {(createMut.isError || updateMut.isError) && (
                  <p className="text-xs text-red-500 bg-red-50 border border-red-200 rounded px-3 py-2">
                    {(createMut.error as Error)?.message ?? (updateMut.error as Error)?.message ?? 'Failed to save role.'}
                  </p>
                )}

                <div className="flex gap-3 justify-end pt-2">
                  <button type="button" onClick={closeModal} className="btn-secondary">Cancel</button>
                  <button
                    type="submit"
                    disabled={createMut.isPending || updateMut.isPending}
                    className="btn-primary"
                  >
                    {(createMut.isPending || updateMut.isPending) ? 'Saving…' : isEdit ? 'Save Changes' : 'Create Role'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* Delete confirmation */}
        {deleting && (
          <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-white rounded-2xl p-6 w-[400px] shadow-xl">
              <h3 className="font-semibold text-gray-900 mb-2">Delete Role</h3>
              <p className="text-sm text-gray-600 mb-5">
                Permanently delete <span className="font-mono font-semibold">{deleting.name}</span>?
                This cannot be undone.
              </p>
              {deleteMut.isError && (
                <p className="text-xs text-red-500 bg-red-50 border border-red-200 rounded px-3 py-2 mb-3">
                  {(deleteMut.error as Error)?.message ?? 'Failed to delete role.'}
                </p>
              )}
              <div className="flex gap-3 justify-end">
                <button onClick={() => setDeleting(null)} className="btn-secondary">Cancel</button>
                <button
                  onClick={() => deleteMut.mutate(deleting.id)}
                  disabled={deleteMut.isPending}
                  className="px-4 py-2 text-sm font-medium rounded-lg bg-red-600 text-white hover:bg-red-700 disabled:opacity-50"
                >
                  {deleteMut.isPending ? 'Deleting…' : 'Delete'}
                </button>
              </div>
            </div>
          </div>
        )}

      </div>
    </>
  )
}
