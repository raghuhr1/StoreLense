import client from './client'
import type { ApiResponse, PageResponse, KpiDaily } from '@/types'

export const reportingApi = {
  kpiDaily: (storeId: string, params?: { page?: number; size?: number }) =>
    client.get<ApiResponse<PageResponse<KpiDaily>>>('/reporting/kpi/daily', { params: { storeId, ...params } })
      .then(r => r.data.data),

  kpiRange: (storeId: string, from: string, to: string) =>
    client.get<ApiResponse<KpiDaily[]>>('/reporting/kpi/range', { params: { storeId, from, to } })
      .then(r => r.data.data),
}
