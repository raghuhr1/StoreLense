'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState }  from 'react'
import { type ColumnDef }     from '@tanstack/react-table'
import { useForm }            from 'react-hook-form'
import { zodResolver }        from '@hookform/resolvers/zod'
import { z }                  from 'zod'
import { Plus, UserX, Pencil } from 'lucide-react'
import Header                 from '@/components/layout/Header'
import DataTable              from '@/components/ui/DataTable'
import { statusBadge }        from '@/components/ui/Badge'
import { usersApi }           from '@/lib/api/users'
import { storesApi }          from '@/lib/api/stores'
import { fmtDateTime }        from '@/lib/utils'
import type { User }          from '@/types'

const createSchema = z.object({
  username:  z.string().min(3, 'Min 3 characters'),
  email:     z.string().email('Invalid email'),
  password:  z.string().min(8, 'Min 8 characters'),
  firstName: z.string().min(1, 'Required'),
  lastName:  z.string().min(1, 'Required'),
  storeId:   z.string().optional(),
  role:      z.enum(['ADMIN', 'STORE_MANAGER', 'STORE_ASSOCIATE', 'REFILL_ASSOCIATE', 'SECURITY_GUARD']),
}).superRefine((data, ctx) => {
  if (['STORE_MANAGER', 'STORE_ASSOCIATE', 'SECURITY_GUARD'].includes(data.role) && !data.storeId) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, message: 'A store must be selected for this role', path: ['storeId'] })
  }
})

const editSchema = z.object({
  firstName: z.string().min(1, 'Required'),
  lastName:  z.string().min(1, 'Required'),
  email:     z.string().email('Invalid email'),
  role:      z.enum(['ADMIN', 'STORE_MANAGER', 'STORE_ASSOCIATE', 'REFILL_ASSOCIATE', 'SECURITY_GUARD']),
  storeId:   z.string().optional(),
}).superRefine((data, ctx) => {
  if (['STORE_MANAGER', 'STORE_ASSOCIATE', 'SECURITY_GUARD'].includes(data.role) && !data.storeId) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, message: 'A store must be selected for this role', path: ['storeId'] })
  }
})

type CreateValues = z.infer<typeof createSchema>
type EditValues   = z.infer<typeof editSchema>

export default function UsersPage() {
  const qc   = useQueryClient()
  const [open, setOpen]       = useState(false)
  const [editing, setEditing] = useState<User | null>(null)

  const { data: users, isLoading } = useQuery({
    queryKey: ['users'],
    queryFn:  () => usersApi.list({ size: 100 }),
  })

  const { data: storesPage } = useQuery({
    queryKey: ['stores'],
    queryFn:  () => storesApi.list({ size: 500 }),
    enabled:  open || !!editing,
  })
  const stores = storesPage?.content ?? []

  const createMut = useMutation({
    mutationFn: (v: CreateValues) => usersApi.create({ ...v, roles: [v.role], storeId: v.storeId || undefined }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['users'] }); setOpen(false); createForm.reset() },
  })

  const updateMut = useMutation({
    mutationFn: ({ id, body }: { id: string; body: EditValues }) =>
      usersApi.update(id, { ...body, roles: [body.role], storeId: body.storeId || undefined }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['users'] }); setEditing(null); editForm.reset() },
  })

  const deactivateMut = useMutation({
    mutationFn: (id: string) => usersApi.deactivate(id),
    onSuccess:  () => qc.invalidateQueries({ queryKey: ['users'] }),
  })

  const createForm = useForm<CreateValues>({
    resolver: zodResolver(createSchema),
    defaultValues: { role: 'STORE_ASSOCIATE' },
  })

  const editForm = useForm<EditValues>({
    resolver: zodResolver(editSchema),
    defaultValues: { role: 'STORE_ASSOCIATE' },
  })

  const openEdit = (u: User) => {
    setEditing(u)
    editForm.reset({
      firstName: u.firstName,
      lastName:  u.lastName,
      email:     u.email,
      role:      (u.roles?.[0] ?? 'STORE_ASSOCIATE') as EditValues['role'],
      storeId:   u.storeId ?? '',
    })
  }

  const selectedCreateRole = createForm.watch('role')
  const selectedEditRole   = editForm.watch('role')

  const columns = useMemo<ColumnDef<User, unknown>[]>(() => [
    { accessorKey: 'username',  header: 'Username', cell: i => <span className="font-medium">{i.getValue<string>()}</span> },
    { accessorKey: 'firstName', header: 'Name',     cell: ({ row }) => `${row.original.firstName} ${row.original.lastName}` },
    { accessorKey: 'email',     header: 'Email' },
    { accessorKey: 'roles',     header: 'Role',     cell: i => i.getValue<string[]>().join(', ') },
    { accessorKey: 'active',    header: 'Status',   cell: i => statusBadge(i.getValue<boolean>() ? 'active' : 'inactive') },
    { accessorKey: 'lastLoginAt', header: 'Last Login', cell: i => fmtDateTime(i.getValue<string | null>()) },
    {
      id: 'actions',
      header: '',
      cell: ({ row }) => (
        <div className="flex items-center gap-1">
          <button
            onClick={() => openEdit(row.original)}
            className="p-1.5 text-gray-400 hover:text-brand-600 hover:bg-gray-100 rounded transition-colors"
            title="Edit user"
          >
            <Pencil size={15} />
          </button>
          <button
            onClick={() => deactivateMut.mutate(row.original.id)}
            disabled={row.original.username === 'admin'}
            className="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors disabled:opacity-30"
            title="Deactivate"
          >
            <UserX size={15} />
          </button>
        </div>
      ),
    },
  ], [deactivateMut])

  return (
    <>
      <Header title="Users" />
      <div className="p-6 space-y-4">

        <div className="flex justify-end">
          <button onClick={() => setOpen(true)} className="btn-primary">
            <Plus size={16} /> Create User
          </button>
        </div>

        <div className="card">
          <DataTable
            data={users?.content ?? []}
            columns={columns}
            isLoading={isLoading}
            searchable
            searchPlaceholder="Search users…"
          />
        </div>

        {/* Create modal */}
        {open && (
          <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-white rounded-2xl p-6 w-[480px] shadow-xl">
              <h3 className="font-semibold text-gray-900 mb-4">Create User</h3>
              <form onSubmit={createForm.handleSubmit(v => createMut.mutate(v))} className="space-y-3">
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">First Name *</label>
                    <input {...createForm.register('firstName')} className="input-field" />
                    {createForm.formState.errors.firstName && <p className="text-xs text-red-500 mt-0.5">{createForm.formState.errors.firstName.message}</p>}
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">Last Name *</label>
                    <input {...createForm.register('lastName')} className="input-field" />
                    {createForm.formState.errors.lastName && <p className="text-xs text-red-500 mt-0.5">{createForm.formState.errors.lastName.message}</p>}
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Username *</label>
                  <input {...createForm.register('username')} className="input-field" />
                  {createForm.formState.errors.username && <p className="text-xs text-red-500 mt-0.5">{createForm.formState.errors.username.message}</p>}
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Email *</label>
                  <input {...createForm.register('email')} type="email" className="input-field" />
                  {createForm.formState.errors.email && <p className="text-xs text-red-500 mt-0.5">{createForm.formState.errors.email.message}</p>}
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Password *</label>
                  <input {...createForm.register('password')} type="password" className="input-field" />
                  {createForm.formState.errors.password && <p className="text-xs text-red-500 mt-0.5">{createForm.formState.errors.password.message}</p>}
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Role *</label>
                  <select {...createForm.register('role')} className="input-field">
                    <option value="STORE_ASSOCIATE">Store Associate</option>
                    <option value="REFILL_ASSOCIATE">Refill Associate</option>
                    <option value="STORE_MANAGER">Store Manager</option>
                    <option value="SECURITY_GUARD">Security Guard</option>
                    <option value="ADMIN">Admin</option>
                  </select>
                </div>
                {selectedCreateRole !== 'ADMIN' && (
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">
                      Store {selectedCreateRole !== 'REFILL_ASSOCIATE' ? '*' : '(optional)'}
                    </label>
                    <select {...createForm.register('storeId')} className="input-field">
                      <option value="">— Select a store —</option>
                      {stores.map(s => <option key={s.id} value={s.id}>{s.storeCode} — {s.name}</option>)}
                    </select>
                    {createForm.formState.errors.storeId && <p className="text-xs text-red-500 mt-0.5">{createForm.formState.errors.storeId.message}</p>}
                    {stores.length === 0 && <p className="text-xs text-amber-600 mt-0.5">No stores yet — create a store first.</p>}
                  </div>
                )}
                {createMut.isError && (
                  <p className="text-xs text-red-500 bg-red-50 border border-red-200 rounded px-3 py-2">
                    Failed to create user. Check your input and try again.
                  </p>
                )}
                <div className="flex gap-3 justify-end pt-2">
                  <button type="button" onClick={() => { setOpen(false); createForm.reset() }} className="btn-secondary">Cancel</button>
                  <button type="submit" disabled={createMut.isPending} className="btn-primary">
                    {createMut.isPending ? 'Creating…' : 'Create'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* Edit modal */}
        {editing && (
          <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-white rounded-2xl p-6 w-[480px] shadow-xl">
              <h3 className="font-semibold text-gray-900 mb-1">Edit User</h3>
              <p className="text-xs text-gray-400 mb-4 font-mono">@{editing.username}</p>
              <form onSubmit={editForm.handleSubmit(v => updateMut.mutate({ id: editing.id, body: v }))} className="space-y-3">
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">First Name *</label>
                    <input {...editForm.register('firstName')} className="input-field" />
                    {editForm.formState.errors.firstName && <p className="text-xs text-red-500 mt-0.5">{editForm.formState.errors.firstName.message}</p>}
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">Last Name *</label>
                    <input {...editForm.register('lastName')} className="input-field" />
                    {editForm.formState.errors.lastName && <p className="text-xs text-red-500 mt-0.5">{editForm.formState.errors.lastName.message}</p>}
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Email *</label>
                  <input {...editForm.register('email')} type="email" className="input-field" />
                  {editForm.formState.errors.email && <p className="text-xs text-red-500 mt-0.5">{editForm.formState.errors.email.message}</p>}
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Role *</label>
                  <select {...editForm.register('role')} className="input-field">
                    <option value="STORE_ASSOCIATE">Store Associate</option>
                    <option value="REFILL_ASSOCIATE">Refill Associate</option>
                    <option value="STORE_MANAGER">Store Manager</option>
                    <option value="SECURITY_GUARD">Security Guard</option>
                    <option value="ADMIN">Admin</option>
                  </select>
                </div>
                {selectedEditRole !== 'ADMIN' && (
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">
                      Store {selectedEditRole !== 'REFILL_ASSOCIATE' ? '*' : '(optional)'}
                    </label>
                    <select {...editForm.register('storeId')} className="input-field">
                      <option value="">— Select a store —</option>
                      {stores.map(s => <option key={s.id} value={s.id}>{s.storeCode} — {s.name}</option>)}
                    </select>
                    {editForm.formState.errors.storeId && <p className="text-xs text-red-500 mt-0.5">{editForm.formState.errors.storeId.message}</p>}
                  </div>
                )}
                {updateMut.isError && (
                  <p className="text-xs text-red-500 bg-red-50 border border-red-200 rounded px-3 py-2">
                    Failed to update user. Check your input and try again.
                  </p>
                )}
                <div className="flex gap-3 justify-end pt-2">
                  <button type="button" onClick={() => { setEditing(null); editForm.reset() }} className="btn-secondary">Cancel</button>
                  <button type="submit" disabled={updateMut.isPending} className="btn-primary">
                    {updateMut.isPending ? 'Saving…' : 'Save Changes'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

      </div>
    </>
  )
}
