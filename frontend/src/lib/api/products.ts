import client from './client'
import type { ApiResponse, PageResponse, Product, BulkImageImportStatus } from '@/types'

export const productsApi = {
  list: (params?: { search?: string; storeId?: string; page?: number; size?: number }) =>
    client.get<ApiResponse<PageResponse<Product>>>('/products', { params }).then(r => r.data.data),

  get: (id: string) =>
    client.get<ApiResponse<Product>>(`/products/${id}`).then(r => r.data.data),

  getBySku: (sku: string) =>
    client.get<ApiResponse<Product>>(`/products/by-sku/${sku}`).then(r => r.data.data),

  create: (body: Partial<Product>) =>
    client.post<ApiResponse<Product>>('/products', body).then(r => r.data.data),

  update: (id: string, body: Partial<Product>) =>
    client.put<ApiResponse<Product>>(`/products/${id}`, body).then(r => r.data.data),

  lookupEpc: (epc: string) =>
    client.get<ApiResponse<{ epc: string; productId: string }>>(`/products/epc/${epc}`)
      .then(r => r.data.data),

  uploadImage: (id: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return client.post<ApiResponse<Product>>(`/products/${id}/image`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }).then(r => r.data.data)
  },

  startBulkImageImport: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return client.post<ApiResponse<string>>('/products/images/bulk-import', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }).then(r => r.data.data)
  },

  bulkImportStatus: (jobId: string) =>
    client.get<ApiResponse<BulkImageImportStatus>>(`/products/images/bulk-import/${jobId}`)
      .then(r => r.data.data),
}
