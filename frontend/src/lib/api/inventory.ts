import client from './client'
import type {
  ApiResponse, EpcLedgerRow, InventoryState, InboundEpcRow,
  PageResponse, PutawayResponse, SkuLedgerRow, ZoneParLevel,
} from '@/types'

export const inventoryApi = {
  getState: (storeId: string) =>
    client.get<ApiResponse<InventoryState[]>>('/inventory/state', { params: { storeId } })
      .then(r => r.data.data),

  getLowAccuracy: (storeId: string, threshold = 95) =>
    client.get<ApiResponse<InventoryState[]>>('/inventory/low-accuracy', { params: { storeId, threshold } })
      .then(r => r.data.data),

  epcSummary: (storeId: string) =>
    client.get<ApiResponse<Record<string, number>>>('/inventory/epc-summary', { params: { storeId } })
      .then(r => r.data.data),

  skuLedger: (storeId: string) =>
    client.get<ApiResponse<SkuLedgerRow[]>>('/inventory/sku-ledger', { params: { storeId } })
      .then(r => r.data.data),

  epcLedger: (storeId: string, status: string, page: number, size = 50) =>
    client.get<ApiResponse<PageResponse<EpcLedgerRow>>>('/inventory/epc-ledger', {
      params: { storeId, ...(status ? { status } : {}), page, size },
    }).then(r => r.data.data),

  inboundPending: (storeId: string) =>
    client.get<ApiResponse<InboundEpcRow[]>>('/inventory/inbound-pending', { params: { storeId } })
      .then(r => r.data.data ?? []),

  putaway: (body: { storeId: string; zoneId: string; epcs: string[] }) =>
    client.post<ApiResponse<PutawayResponse>>('/inventory/putaway', body)
      .then(r => r.data.data),
}

export const parLevelsApi = {
  list: (storeId: string, zoneId?: string) =>
    client.get<ApiResponse<ZoneParLevel[]>>('/par-levels', {
      params: { storeId, ...(zoneId ? { zoneId } : {}) },
    }).then(r => r.data.data ?? []),

  upsert: (body: { storeId: string; zoneId: string; productId: string; parQty: number; minQty: number }) =>
    client.post<ApiResponse<ZoneParLevel>>('/par-levels', body)
      .then(r => r.data.data),

  delete: (id: string, storeId: string) =>
    client.delete(`/par-levels/${id}`, { params: { storeId } }),
}
