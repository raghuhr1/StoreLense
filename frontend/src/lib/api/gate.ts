import client from './client'
import type { ApiResponse, PageResponse, GateCheckSummary, GateCheck } from '@/types'

export const gateApi = {
  summary: (storeId: string, date: string) =>
    client
      .get<ApiResponse<GateCheckSummary>>('/gate/checks/summary', { params: { storeId, date } })
      .then(r => r.data.data),

  list: (params: {
    storeId: string
    from:    string
    to:      string
    outcome?: string
    page?:   number
    size?:   number
  }) =>
    client
      .get<ApiResponse<PageResponse<GateCheck>>>('/gate/checks', { params })
      .then(r => r.data.data),
}
