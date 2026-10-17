# syntax=docker/dockerfile:1
FROM node:24-bookworm-slim

ENV NODE_ENV=production PORT=8080
WORKDIR /app

COPY --chown=node:node package.json package-lock.json .npmrc ./
# Optional public proxy CA for managed development environments; never copied into the image.
# Google Cloud builds need no proxy_ca secret and use the image's normal CA roots.
RUN --mount=type=secret,id=proxy_ca \
    if [ -f /run/secrets/proxy_ca ]; then export NODE_EXTRA_CA_CERTS=/run/secrets/proxy_ca; fi; \
    npm ci --omit=dev --ignore-scripts --no-audit --no-fund && npm cache clean --force

COPY --chown=node:node src/ ./src/
# Built Dua AI website (deploy-cloudrun.sh builds website/ into web/site; may be empty).
COPY --chown=node:node site/ ./site/
USER node
EXPOSE 8080
CMD ["node", "src/server/server.mjs"]
