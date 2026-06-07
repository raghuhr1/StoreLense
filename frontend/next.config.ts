import type { NextConfig } from 'next'

// In Docker/production the nginx gateway sits at NEXT_PUBLIC_API_BASE_URL (port 8080).
// In local dev (npm run dev) the service map below routes directly to each service port.
const isDev = process.env.NODE_ENV !== 'production'

const GATEWAY = process.env.INTERNAL_API_URL ?? 'http://nginx-gateway:8080'

const DEV_SERVICE_MAP: [string, string][] = [
  ['/api/auth',      'http://localhost:8081'],
  ['/api/users',     'http://localhost:8081'],
  ['/api/stores',    'http://localhost:8082'],
  ['/api/products',  'http://localhost:8083'],
  ['/api/inventory', 'http://localhost:8084'],
  ['/api/soh',       'http://localhost:8085'],
  ['/api/refill',    'http://localhost:8086'],
  ['/api/rfid',      'http://localhost:8087'],
  ['/api/reporting', 'http://localhost:8089'],
  ['/api/erp',       'http://localhost:8090'],
]

const config: NextConfig = {
  output: 'standalone',        // Required for Docker production image
  // Stamp every build with a unique ID so AuthContext can detect a new deploy
  // and clear stale sessionStorage refresh tokens automatically.
  env: {
    NEXT_PUBLIC_BUILD_ID: new Date().toISOString(),
  },
  async rewrites() {
    if (isDev) {
      return DEV_SERVICE_MAP.map(([prefix, dest]) => ({
        source:      `${prefix}/:path*`,
        destination: `${dest}${prefix}/:path*`,
      }))
    }
    // In Docker: forward everything to nginx gateway
    return [{
      source:      '/api/:path*',
      destination: `${GATEWAY}/api/:path*`,
    }]
  },
}

export default config
