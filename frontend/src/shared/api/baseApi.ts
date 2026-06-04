import { createApi, fetchBaseQuery, BaseQueryFn, FetchArgs, FetchBaseQueryError } from '@reduxjs/toolkit/query/react'
import { tokenStore } from '@/utils/storage'
import { logout, setCredentials } from '@/modules/auth/authSlice'

const rawBaseQuery = fetchBaseQuery({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
  prepareHeaders: (headers) => {
    const token = tokenStore.getAccess()
    if (token) headers.set('Authorization', `Bearer ${token}`)
    headers.set('Content-Type', 'application/json')
    return headers
  },
})

// Re-authentication on 401 using the in-memory refresh token
const baseQueryWithReauth: BaseQueryFn<string | FetchArgs, unknown, FetchBaseQueryError> =
  async (args, api, extraOptions) => {
    let result = await rawBaseQuery(args, api, extraOptions)

    if (result.error?.status === 401) {
      const refreshToken = tokenStore.getRefresh()
      if (!refreshToken) {
        api.dispatch(logout())
        return result
      }

      const refreshResult = await rawBaseQuery(
        { url: '/api/auth/refresh', method: 'POST', body: { refreshToken } },
        api,
        extraOptions
      )

      if (refreshResult.data) {
        const data = (refreshResult.data as { data: { accessToken: string; refreshToken: string } }).data
        tokenStore.set(data.accessToken, data.refreshToken)
        api.dispatch(setCredentials({ accessToken: data.accessToken, refreshToken: data.refreshToken }))
        result = await rawBaseQuery(args, api, extraOptions)
      } else {
        api.dispatch(logout())
      }
    }

    return result
  }

export const baseApi = createApi({
  reducerPath: 'api',
  baseQuery: baseQueryWithReauth,
  tagTypes: ['Users', 'Stores', 'Zones', 'Products', 'SohSessions', 'RefillTasks', 'KpiDaily', 'Inventory'],
  endpoints: () => ({}),
})
