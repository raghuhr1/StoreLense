import client from './client'
import type {
  ApiResponse, PageResponse,
  CycleCount, SohSession, StoreLocation,
  CycleCountReconciliation, ReconciliationItemWithLocation,
} from '@/types'

export const cycleCountApi = {
  list: (storeId: string, params?: { page?: number; size?: number }) =>
    client.get<ApiResponse<PageResponse<CycleCount>>>('/cycle-counts', { params: { storeId, ...params } })
      .then(r => r.data.data),

  get: (id: string) =>
    client.get<ApiResponse<CycleCount>>(`/cycle-counts/${id}`).then(r => r.data.data),

  create: (body: { storeId: string; countDate?: string; notes?: string }) =>
    client.post<ApiResponse<CycleCount>>('/cycle-counts', body).then(r => r.data.data),

  upload: (id: string) =>
    client.post<ApiResponse<CycleCount>>(`/cycle-counts/${id}/upload`).then(r => r.data.data),

  close: (id: string) =>
    client.post<ApiResponse<CycleCount>>(`/cycle-counts/${id}/close`).then(r => r.data.data),

  startSession: (
    countId: string,
    body: { storeId: string; locationCode: string; sectionCode?: string | null },
  ) =>
    client.post<ApiResponse<SohSession>>('/soh/sessions', {
      storeId:      body.storeId,
      sessionType:  'cycle_count',
      cycleCountId: countId,
      locationCode: body.locationCode,
      sectionCode:  body.sectionCode ?? null,
    }).then(r => r.data.data),

  reconcile: (id: string) =>
    client.post<ApiResponse<CycleCountReconciliation>>(
      `/reconciliation/cycle-counts/${id}/run`
    ).then(r => r.data.data),

  getReconciliation: (id: string) =>
    client.get<ApiResponse<CycleCountReconciliation>>(
      `/reconciliation/cycle-counts/${id}/result`
    ).then(r => r.data.data),

  getReconciliationItems: (id: string, params?: { location?: string }) =>
    client.get<ApiResponse<ReconciliationItemWithLocation[]>>(
      `/reconciliation/cycle-counts/${id}/result/items`, { params }
    ).then(r => r.data.data),

  approve: (id: string) =>
    client.post<ApiResponse<CycleCountReconciliation>>(
      `/reconciliation/cycle-counts/${id}/approve`
    ).then(r => r.data.data),

  downloadCsv: (id: string) =>
    client.get<Blob>(`/reconciliation/cycle-counts/${id}/result/csv`, { responseType: 'blob' })
      .then(r => r.data),
}
