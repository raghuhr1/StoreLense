import axios from 'axios'

const SESSION_KEY = 'sl_rt'

// Access token: memory only (XSS mitigation — never hits localStorage)
// Refresh token: sessionStorage (survives F5, wiped when tab/window closes)
let _accessToken: string | null = null

export const tokenStore = {
  getAccess:  ()       => _accessToken,
  getRefresh: ()       => sessionStorage.getItem(SESSION_KEY),
  set: (a: string, r: string) => {
    _accessToken = a
    sessionStorage.setItem(SESSION_KEY, r)
  },
  clear: () => {
    _accessToken = null
    sessionStorage.removeItem(SESSION_KEY)
  },
}

const client = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

// Attach access token to every outgoing request
client.interceptors.request.use((config) => {
  const token = tokenStore.getAccess()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Concurrent-safe 401 handler:
// All concurrent requests that get 401 share one single refresh call.
// Subsequent requests wait in the queue and receive the new token when ready.
let isRefreshing = false
let refreshQueue: Array<(token: string) => void> = []

function flushQueue(newToken: string) {
  refreshQueue.forEach(resolve => resolve(newToken))
  refreshQueue = []
}

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

    // If a refresh is already in flight, queue this request
    if (isRefreshing) {
      return new Promise<string>((resolve) => {
        refreshQueue.push(resolve)
      }).then((newToken) => {
        original.headers.Authorization = `Bearer ${newToken}`
        return client(original)
      })
    }

    isRefreshing = true
    try {
      const { data } = await axios.post<{ data: { accessToken: string; refreshToken: string } }>(
        '/api/auth/refresh',
        { refreshToken }
      )
      const newToken = data.data.accessToken
      tokenStore.set(newToken, data.data.refreshToken)
      flushQueue(newToken)
      original.headers.Authorization = `Bearer ${newToken}`
      return client(original)
    } catch {
      refreshQueue = []
      tokenStore.clear()
      window.location.href = '/login'
      return Promise.reject(error)
    } finally {
      isRefreshing = false
    }
  }
)

export default client
