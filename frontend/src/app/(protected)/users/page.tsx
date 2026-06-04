'use client'

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState }  from 'react'
import { type ColumnDef }     from '@tanstack/react-table'
import { useForm }            from 'react-hook-form'
import { zodResolver }        from '@hookform/resolvers/zod'
import { z }                  from 'zod'
import { Plus, UserX }        from 'lucide-react'
import Header                 from '@/components/layout/Header'
import DataTable              from '@/components/ui/DataTable'
import { statusBadge }        from '@/components/ui/Badge'
import { usersApi }           from '@/lib/api/users'
import { fmtDateTime }        from '@/lib/utils'
import type { User }          from '@/types'

const schema = z.object({
  username:  z.string().min(3),
  email:     z.string().email(),
  password:  z.string().min(8),
  firstName: z.string().min(1),
  lastName:  z.string().min(1),
  storeId:   z.string().optional(),
  role:      z.enum(['ADMIN','STORE_MANAGER','STORE_ASSOCIATE','REFILL_ASSOCIATE']),
})
type FormValues = z.infer<typeof schema>

export default function UsersPage() {
  const qc   = useQueryClient()
  const [open, setOpen] = useState(false)

  const { data, isLoading } = useQuery({
    queryKey: ['users'],
    queryFn:  () => usersApi.list({ size: 100 }),
  })

  const createMut = useMutation({
    mutationFn: (v: FormValues) => usersApi.create({ ...v, roles: [v.role] }),
    onSuccess:  () => { qc.invalidateQueries({ queryKey: ['users'] }); setOpen(false); reset() },
  })

  const deactivateMut = useMutation({
    mutationFn: (id: string) => usersApi.deactivate(id),
    onSuccess:  () => qc.invalidateQueries({ queryKey: ['users'] }),
  })

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { role: 'STORE_ASSOCIATE' },
  })

  const columns = useMemo<ColumnDef<User, unknown>[]>(() => [
    { accessorKey: 'username',    header: 'Username', cell: i => <span className="font-medium">{i.getValue<string>()}</span> },
    { accessorKey: 'firstName',   header: 'Name',     cell: ({ row }) => `${row.original.firstName} ${row.original.lastName}` },
    { accessorKey: 'email',       header: 'Email' },
    { accessorKey: 'roles',       header: 'Role',     cell: i => i.getValue<string[]>().join(', ') },
    { accessorKey: 'active',      header: 'Status',   cell: i => statusBadge(i.getValue<boolean>() ? 'active' : 'inactive') },
    { accessorKey: 'lastLoginAt', header: 'Last Login', cell: i => fmtDateTime(i.getValue<string|null>()) },
    {
      id: 'actions',
      header: '',
      cell: ({ row }) => (
        <button
          onClick={() => deactivateMut.mutate(row.original.id)}
          className="text-red-500 hover:text-red-700 p-1 rounded"
          title="Deactivate"
        >
          <UserX size={16} />
        </button>
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
            data={data?.content ?? []}
            columns={columns}
            isLoading={isLoading}
            searchable
            searchPlaceholder="Search users…"
          />
        </div>

        {open && (
          <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-white rounded-2xl p-6 w-[480px] shadow-xl">
              <h3 className="font-semibold text-gray-900 mb-4">Create User</h3>
              <form onSubmit={handleSubmit(v => createMut.mutate(v))} className="space-y-3">
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">First Name</label>
                    <input {...register('firstName')} className="input-field" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">Last Name</label>
                    <input {...register('lastName')} className="input-field" />
                  </div>
                </div>
                {[
                  { name: 'username' as const, label: 'Username', type: 'text' },
                  { name: 'email' as const,    label: 'Email',    type: 'email' },
                  { name: 'password' as const, label: 'Password', type: 'password' },
                  { name: 'storeId' as const,  label: 'Store ID (optional)', type: 'text' },
                ].map(({ name, label, type }) => (
                  <div key={name}>
                    <label className="block text-xs font-medium text-gray-700 mb-1">{label}</label>
                    <input {...register(name)} type={type} className="input-field" />
                    {errors[name] && <p className="text-xs text-red-500 mt-0.5">{errors[name]?.message}</p>}
                  </div>
                ))}
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Role</label>
                  <select {...register('role')} className="input-field">
                    <option value="STORE_ASSOCIATE">Store Associate</option>
                    <option value="REFILL_ASSOCIATE">Refill Associate</option>
                    <option value="STORE_MANAGER">Store Manager</option>
                    <option value="ADMIN">Admin</option>
                  </select>
                </div>
                <div className="flex gap-3 justify-end pt-2">
                  <button type="button" onClick={() => { setOpen(false); reset() }} className="btn-secondary">Cancel</button>
                  <button type="submit" disabled={createMut.isPending} className="btn-primary">
                    {createMut.isPending ? 'Creating…' : 'Create'}
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
