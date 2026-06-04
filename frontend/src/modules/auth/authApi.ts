import { baseApi }                         from '@/shared/api/baseApi'
import { loginSuccess, logout }           from './authSlice'
import type { ApiResponse, LoginRequest, LoginResponse } from '@/types'

export const authApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    login: build.mutation<ApiResponse<LoginResponse>, LoginRequest>({
      queryFn: async (body, api, _extraOptions, baseQuery) => {
        const result = await baseQuery({ url: '/api/auth/login', method: 'POST', body })
        if (result.data) {
          const resp = result.data as ApiResponse<LoginResponse>
          const d    = resp.data
          api.dispatch(loginSuccess({
            userId: d.userId, username: d.username,
            role: d.role, storeId: d.storeId,
            accessToken: d.accessToken, refreshToken: d.refreshToken,
          }))
        }
        return result as { data: ApiResponse<LoginResponse> }
      },
    }),

    logoutMutation: build.mutation<void, { refreshToken?: string }>({
      queryFn: async (body, api, _extraOptions, baseQuery) => {
        await baseQuery({ url: '/api/auth/logout', method: 'POST', body })
        api.dispatch(logout())
        return { data: undefined }
      },
    }),
  }),
})

export const { useLoginMutation, useLogoutMutationMutation } = authApi
