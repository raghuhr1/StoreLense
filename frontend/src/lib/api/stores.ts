import client from './client'
import type { ApiResponse, PageResponse, Store, Zone, RfidReader } from '@/types'

export const storesApi = {
  list: (params?: { page?: number; size?: number }) =>
    client.get<ApiResponse<PageResponse<Store>>>('/stores', { params }).then(r => r.data.data),

  get: (id: string) =>
    client.get<ApiResponse<Store>>(`/stores/${id}`).then(r => r.data.data),

  create: (body: Partial<Store>) =>
    client.post<ApiResponse<Store>>('/stores', body).then(r => r.data.data),

  update: (id: string, body: Partial<Store>) =>
    client.put<ApiResponse<Store>>(`/stores/${id}`, body).then(r => r.data.data),

  deactivate: (id: string) =>
    client.delete(`/stores/${id}`),

  zones: (storeId: string) =>
    client.get<ApiResponse<Zone[]>>(`/stores/${storeId}/zones`).then(r => r.data.data),

  createZone: (storeId: string, body: Partial<Zone>) =>
    client.post<ApiResponse<Zone>>(`/stores/${storeId}/zones`, body).then(r => r.data.data),

  readers: (storeId: string) =>
    client.get<ApiResponse<RfidReader[]>>(`/stores/${storeId}/readers`).then(r => r.data.data),
}
