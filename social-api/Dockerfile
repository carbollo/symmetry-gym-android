# Build + run del backend social de Zenit.
FROM node:20-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src ./src
RUN npm run build
# Poda dev-deps para la imagen final.
RUN npm prune --omit=dev

FROM node:20-slim
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/dist ./dist
COPY --from=build /app/package.json ./package.json
# Aplica el esquema (idempotente) y arranca el servidor.
CMD ["sh", "-c", "node dist/db/migrate.js && node dist/index.js"]
