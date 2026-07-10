import client from './client'
import type { ApiResponse, PageResponse, Store, Zone, RfidReader, StoreLocation, AntennaLocationMapping, StoreFeature } from '@/types'

export const storesApi = {
  list: (params?: { page?: number; size?: number }) =>
    client.get<ApiResponse<PageResponse<Store>>>('/stores', { params }).then(r => r.data.data),

  get: (id: string) =>
    client.get<ApiResponse<Store>>(`/stores/${id}`).then(r => r.data.data),

  create: (body: Partial<Store>) =>
    client.post<ApiResponse<Store>>('/stores', body).then(r => r.data.data),

  update: (id: string, body: Partial<Store>) =>
    client.put<ApiResponse<Store>>(`/stores/${id}`, body).then(r => r.data.data),

  deactivate: (id: string) =>
    client.delete(`/stores/${id}`),

  zones: (storeId: string) =>
    client.get<ApiResponse<Zone[]>>(`/stores/${storeId}/zones`).then(r => r.data.data),

  createZone: (storeId: string, body: Partial<Zone>) =>
    client.post<ApiResponse<Zone>>(`/stores/${storeId}/zones`, body).then(r => r.data.data),

  readers: (storeId: string) =>
    client.get<ApiResponse<RfidReader[]>>(`/stores/${storeId}/readers`).then(r => r.data.data),

  locations: (storeId: string) =>
    client.get<ApiResponse<StoreLocation[]>>(`/stores/${storeId}/locations`).then(r => r.data.data),

  createLocation: (storeId: string, body: {
    locationCode: string; sectionCode?: string | null
    displayName: string; sortOrder?: number
  }) =>
    client.post<ApiResponse<StoreLocation>>(`/stores/${storeId}/locations`, body).then(r => r.data.data),

  deactivateLocation: (storeId: string, locationId: string) =>
    client.delete(`/stores/${storeId}/locations/${locationId}`),

  antennaMappings: (storeId: string) =>
    client.get<ApiResponse<AntennaLocationMapping[]>>(`/stores/${storeId}/antenna-mappings`).then(r => r.data.data),

  createAntennaMapping: (storeId: string, body: {
    readerId: string; antennaPort: number
    locationCode: string; sectionCode?: string | null; displayName?: string
  }) =>
    client.post<ApiResponse<AntennaLocationMapping>>(`/stores/${storeId}/antenna-mappings`, body).then(r => r.data.data),

  deactivateAntennaMapping: (storeId: string, mappingId: string) =>
    client.delete(`/stores/${storeId}/antenna-mappings/${mappingId}`),

  getFeatures: (storeId: string) =>
    client.get<ApiResponse<StoreFeature[]>>(`/stores/${storeId}/features`).then(r => r.data.data),

  updateFeatures: (storeId: string, features: Record<string, boolean>) =>
    client.put<ApiResponse<StoreFeature[]>>(`/stores/${storeId}/features`, { features }).then(r => r.data.data),
}
