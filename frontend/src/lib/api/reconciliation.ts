import client from './client'
import type { ApiResponse, PageResponse, ReconciliationSession, ReconciliationItem } from '@/types'

export const reconciliationApi = {
  listByStore: (storeId: string, params?: { page?: number; size?: number }) =>
    client.get<ApiResponse<PageResponse<ReconciliationSession>>>(
      '/reconciliation/sessions',
      { params: { storeId, ...params } }
    ).then(r => r.data.data),

  getResult: (sessionId: string) =>
    client.get<ApiResponse<ReconciliationSession>>(
      `/reconciliation/sessions/${sessionId}/result`
    ).then(r => r.data.data),

  getItems: (sessionId: string) =>
    client.get<ApiResponse<ReconciliationItem[]>>(
      `/reconciliation/sessions/${sessionId}/result/items`
    ).then(r => r.data.data),

  downloadCsv: (sessionId: string) =>
    client.get<Blob>(
      `/reconciliation/sessions/${sessionId}/result/csv`,
      { responseType: 'blob' }
    ).then(r => r.data),
}
