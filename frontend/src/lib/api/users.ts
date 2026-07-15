import client from './client'
import type { ApiResponse, PageResponse, User } from '@/types'

export const usersApi = {
  list: (params?: { storeId?: string; page?: number; size?: number; includeInactive?: boolean }) =>
    client.get<ApiResponse<PageResponse<User>>>('/users', { params }).then(r => r.data.data),

  get: (id: string) =>
    client.get<ApiResponse<User>>(`/users/${id}`).then(r => r.data.data),

  create: (body: {
    username: string; email: string; password: string
    firstName: string; lastName: string; storeId?: string; roles: string[]
  }) => client.post<ApiResponse<User>>('/users', body).then(r => r.data.data),

  update: (id: string, body: Partial<User & { roles: string[] }>) =>
    client.put<ApiResponse<User>>(`/users/${id}`, body).then(r => r.data.data),

  deactivate: (id: string) =>
    client.delete(`/users/${id}`),
}
