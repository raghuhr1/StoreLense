import axios from 'axios'

// Token stored in memory only — never localStorage (XSS mitigation)
let _accessToken:  string | null = null
let _refreshToken: string | null = null

export const tokenStore = {
  getAccess:  ()       => _accessToken,
  getRefresh: ()       => _refreshToken,
  set: (a: string, r: string) => { _accessToken = a; _refreshToken = r },
  clear:      ()       => { _accessToken = null; _refreshToken = null },
}

const client = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

// Attach access token to every request
client.interceptors.request.use((config) => {
  const token = tokenStore.getAccess()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// On 401, attempt silent refresh then retry once
client.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config
    if (error.response?.status !== 401 || original._retry) {
      return Promise.reject(error)
    }

    const refreshToken = tokenStore.getRefresh()
    if (!refreshToken) {
      tokenStore.clear()
      window.location.href = '/login'
      return Promise.reject(error)
    }

    original._retry = true
    try {
      const { data } = await axios.post<{ data: { accessToken: string; refreshToken: string } }>(
        '/api/auth/refresh',
        { refreshToken }
      )
      tokenStore.set(data.data.accessToken, data.data.refreshToken)
      original.headers.Authorization = `Bearer ${data.data.accessToken}`
      return client(original)
    } catch {
      tokenStore.clear()
      window.location.href = '/login'
      return Promise.reject(error)
    }
  }
)

export default client
