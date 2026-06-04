import client from './client'
import type { ApiResponse, PageResponse, Product } from '@/types'

export const productsApi = {
  list: (params?: { search?: string; page?: number; size?: number }) =>
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
}
