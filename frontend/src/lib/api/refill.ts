import client from './client'
import type { ApiResponse, PageResponse, RefillTask } from '@/types'

export const refillApi = {
  listTasks: (storeId: string, params?: { status?: string; page?: number; size?: number }) =>
    client.get<ApiResponse<PageResponse<RefillTask>>>('/refill/tasks', { params: { storeId, ...params } })
      .then(r => r.data.data),

  getTask: (id: string) =>
    client.get<ApiResponse<RefillTask>>(`/refill/tasks/${id}`).then(r => r.data.data),

  createTask: (body: {
    storeId: string; taskType?: string; priority?: number; source?: string
    dueDate?: string; notes?: string; items?: { productId: string; zoneId?: string; requestedQuantity: number }[]
  }) => client.post<ApiResponse<RefillTask>>('/refill/tasks', body).then(r => r.data.data),

  assignTask: (id: string, assignedTo: string) =>
    client.post<ApiResponse<RefillTask>>(`/refill/tasks/${id}/assign`, null, { params: { assignedTo } })
      .then(r => r.data.data),

  fulfil: (taskId: string, itemId: string, quantity: number) =>
    client.patch<ApiResponse<RefillTask>>(`/refill/tasks/${taskId}/items/${itemId}/fulfil`, null,
      { params: { quantity } }).then(r => r.data.data),

  cancelTask: (id: string, reason?: string) =>
    client.post(`/refill/tasks/${id}/cancel`, null, { params: { reason } }),
}
