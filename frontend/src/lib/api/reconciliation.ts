import client from './client'
import type {
  ApiResponse, PageResponse,
  ReconciliationSession, ReconciliationItem,
  CycleCountReconciliation, ReconciliationItemWithLocation,
} from '@/types'

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

  run: (sessionId: string) =>
    client.post<ApiResponse<ReconciliationSession>>(
      `/reconciliation/sessions/${sessionId}/run`
    ).then(r => r.data.data),

  // Cycle-count reconciliation
  runForCount: (cycleCountId: string) =>
    client.post<ApiResponse<CycleCountReconciliation>>(
      `/reconciliation/cycle-counts/${cycleCountId}/run`
    ).then(r => r.data.data),

  getCountResult: (cycleCountId: string) =>
    client.get<ApiResponse<CycleCountReconciliation>>(
      `/reconciliation/cycle-counts/${cycleCountId}/result`
    ).then(r => r.data.data),

  getCountItems: (cycleCountId: string, params?: { location?: string }) =>
    client.get<ApiResponse<ReconciliationItemWithLocation[]>>(
      `/reconciliation/cycle-counts/${cycleCountId}/result/items`, { params }
    ).then(r => r.data.data),

  approveCount: (cycleCountId: string) =>
    client.post<ApiResponse<CycleCountReconciliation>>(
      `/reconciliation/cycle-counts/${cycleCountId}/approve`
    ).then(r => r.data.data),

  downloadCountCsv: (cycleCountId: string) =>
    client.get<Blob>(
      `/reconciliation/cycle-counts/${cycleCountId}/result/csv`,
      { responseType: 'blob' }
    ).then(r => r.data),
}
