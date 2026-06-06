# ─── StoreLense Web Portal — Next.js 15 Dockerfile ───────────────────────────

# ── Stage 1: install ALL dependencies (including devDeps for build) ───────────
# next build needs typescript, tailwindcss, postcss, eslint-config-next, etc.
# These are devDependencies so --omit=dev would cause "npm run build" to fail.
FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci

# ── Stage 2: build ────────────────────────────────────────────────────────────
FROM node:20-alpine AS build
WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY . .

ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# ── Stage 3: production runner (standalone — no node_modules needed) ──────────
# next build with output:'standalone' bundles everything the server needs.
# The runtime image only needs the standalone output + static assets.
FROM node:20-alpine AS runtime
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

RUN addgroup -S nextjs && adduser -S nextjs -G nextjs

COPY --from=build --chown=nextjs:nextjs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nextjs /app/.next/static    ./.next/static
COPY --from=build --chown=nextjs:nextjs /app/public          ./public

USER nextjs
EXPOSE 3000
ENV PORT=3000 HOSTNAME=0.0.0.0

CMD ["node", "server.js"]
