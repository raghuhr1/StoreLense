import client from './client'
import type { ApiResponse, PageResponse, SohSession, SohResult } from '@/types'

export const sohApi = {
  listSessions: (storeId: string, params?: { status?: string; page?: number; size?: number }) =>
    client.get<ApiResponse<PageResponse<SohSession>>>('/soh/sessions', { params: { storeId, ...params } })
      .then(r => r.data.data),

  getSession: (id: string) =>
    client.get<ApiResponse<SohSession>>(`/soh/sessions/${id}`).then(r => r.data.data),

  startSession: (body: { storeId: string; zoneId?: string; sessionType?: string; notes?: string }) =>
    client.post<ApiResponse<SohSession>>('/soh/sessions', body).then(r => r.data.data),

  completeSession: (id: string) =>
    client.post<ApiResponse<SohResult>>(`/soh/sessions/${id}/complete`).then(r => r.data.data),

  cancelSession: (id: string, reason?: string) =>
    client.post(`/soh/sessions/${id}/cancel`, null, { params: { reason } }),
}
