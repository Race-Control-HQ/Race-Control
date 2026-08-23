# RaceControl Web — container image for Coolify (or any Docker host).
#
# Talks to the RaceControl backend (../backend) over RACECONTROL_API_BASE_URL,
# authorised with RACECONTROL_API_TOKEN. Both are server-only env vars — never
# exposed to the browser bundle. See .env.example.

FROM node:20-slim AS base

# --- dependencies ------------------------------------------------------------
FROM base AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# --- build --------------------------------------------------------------------
FROM base AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# --- runtime --------------------------------------------------------------------
FROM base AS runner
WORKDIR /app
ENV NODE_ENV=production \
    PORT=3000 \
    HOSTNAME=0.0.0.0

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Next.js "standalone" output: a minimal server bundle with only the
# dependencies it actually needs, so the runtime image doesn't carry node_modules.
# node:20-slim already ships a non-root "node" user (uid 1000) — reuse it.
COPY --from=builder --chown=node:node /app/.next/standalone ./
COPY --from=builder --chown=node:node /app/.next/static ./.next/static
COPY --from=builder --chown=node:node /app/public ./public

USER node

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD curl -fsS "http://127.0.0.1:${PORT}/api/health" || exit 1

CMD ["node", "server.js"]
