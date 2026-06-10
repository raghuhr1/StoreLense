import client from './client'
import type { ApiResponse, PageResponse, ErpImportBatch, ErpBatchDetail, ErpSohSnapshot } from '@/types'

export const erpImportApi = {
  listBatches: (params?: { storeId?: string; page?: number; size?: number }) =>
    client.get<ApiResponse<PageResponse<ErpImportBatch>>>(
      '/erp/admin/import/batches', { params }
    ).then(r => r.data.data),

  getBatch: (batchId: string) =>
    client.get<ApiResponse<ErpBatchDetail>>(
      `/erp/admin/import/batches/${batchId}`
    ).then(r => r.data.data),

  getUnresolved: (batchId: string) =>
    client.get<ApiResponse<ErpSohSnapshot[]>>(
      `/erp/admin/import/batches/${batchId}/unresolved`
    ).then(r => r.data.data),

  reResolve: (batchId: string) =>
    client.post<ApiResponse<{ batchId: string; resolved: number; unresolved: number }>>(
      `/erp/admin/import/batches/${batchId}/re-resolve`
    ).then(r => r.data.data),
}
