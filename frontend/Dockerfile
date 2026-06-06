# ─── StoreLense Web Portal — Next.js 15 Dockerfile ───────────────────────────

# ── Stage 1: install ALL dependencies (including devDeps for build) ───────────
# next build needs typescript, tailwindcss, postcss, eslint-config-next, etc.
# NODE_OPTIONS limits Node heap so the OOM killer does not terminate npm ci
# on low-RAM servers (< 4GB). Add swap if build is still killed: see DEPLOY.md.
FROM node:20-alpine AS deps
WORKDIR /app
ENV NODE_OPTIONS="--max-old-space-size=1536"
COPY package.json package-lock.json* ./
RUN npm ci --prefer-offline 2>/dev/null || npm ci

# ── Stage 2: build ────────────────────────────────────────────────────────────
FROM node:20-alpine AS build
WORKDIR /app

ENV NEXT_TELEMETRY_DISABLED=1
ENV NODE_OPTIONS="--max-old-space-size=1536"

COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Ensure public/ exists — Next.js requires it and COPY in runtime stage will fail without it
RUN mkdir -p /app/public

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
