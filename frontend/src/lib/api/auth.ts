import client, { tokenStore } from './client'
import type { ApiResponse, LoginRequest, LoginResponse } from '@/types'

export async function login(body: LoginRequest): Promise<LoginResponse> {
  const { data } = await client.post<ApiResponse<LoginResponse>>('/auth/login', body)
  const d = data.data
  tokenStore.set(d.accessToken, d.refreshToken)
  return d
}

export async function logout(): Promise<void> {
  const refreshToken = tokenStore.getRefresh()
  await client.post('/auth/logout', { refreshToken }).catch(() => {})
  tokenStore.clear()
}
