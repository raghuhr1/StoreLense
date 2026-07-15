import client from './client'
import type { ApiResponse } from '@/types'

export interface RoleDto {
  id: string
  name: string
  description: string | null
  userCount: number
  createdAt: string
}

export interface CreateRoleBody {
  name: string
  description?: string
}

export const rolesApi = {
  list: () =>
    client.get<ApiResponse<RoleDto[]>>('/roles')
      .then(r => r.data.data),

  create: (body: CreateRoleBody) =>
    client.post<ApiResponse<RoleDto>>('/roles', body)
      .then(r => r.data.data),

  update: (id: string, body: CreateRoleBody) =>
    client.put<ApiResponse<RoleDto>>(`/roles/${id}`, body)
      .then(r => r.data.data),

  remove: (id: string) =>
    client.delete<ApiResponse<void>>(`/roles/${id}`)
      .then(r => r.data),
}
