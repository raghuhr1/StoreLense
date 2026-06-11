import client from './client'
import type { ApiResponse, InventoryState, SkuLedgerRow } from '@/types'

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
}
