# Zenit — Social API

Backend de las funciones sociales de Zenit (cuentas, amigos, compartir rutinas).
El núcleo de la app funciona **sin cuenta**; esto solo se usa para lo social (modelo híbrido).

- **Stack:** Node 20+ · TypeScript · Hono · Drizzle · postgres-js · bcryptjs · zod
- **Auth:** JWT HS256, 30 días, con claim `tv` (token version) para revocación global.
- Comparte el **mismo Postgres de Railway** que el server de anuncios (`server/`), pero
  gestiona sus propias tablas (`users`, `friendships`, `shared_routines`). No toca `rewards`.

## Variables de entorno

Copia `.env.example` a `.env` y rellena:

| Var | Obligatoria | Descripción |
|-----|-------------|-------------|
| `DATABASE_URL` | sí | Cadena de conexión Postgres. En Railway: referencia `${{Postgres.DATABASE_URL}}`. |
| `JWT_SECRET` | sí | Secreto para firmar tokens. **Mínimo 32 caracteres.** |
| `PORT` | no | Puerto HTTP (Railway lo inyecta; por defecto 3001). |
| `JWT_EXPIRES_IN` | no | Segundos de validez del token (por defecto 2592000 = 30 días). |
| `NODE_ENV` | no | `production` en Railway. |
| `PGPOOL_MAX` | no | Tamaño del pool de conexiones (por defecto 10). |

## Desarrollo local

```bash
npm install
npm run db:migrate   # aplica el esquema (idempotente) contra DATABASE_URL
npm run dev          # servidor con recarga en caliente
```

## Endpoints (Fase A)

| Método | Ruta | Auth | Respuesta |
|--------|------|------|-----------|
| GET  | `/health` | — | `{ ok: true }` |
| POST | `/auth/register` | — | 201 `{ token, user }` · 409 `{ error:"already_exists", field }` · 400 |
| POST | `/auth/login` | — | 200 `{ token, user }` · 401 `{ error:"invalid_credentials" }` |
| GET  | `/me` | Bearer | 200 `{ user }` · 401 |

## Desplegar en Railway

1. En tu proyecto de Railway (el mismo donde ya tienes el server de anuncios y el Postgres):
   **New → GitHub Repo** → este repo.
2. En **Settings → Root Directory** del servicio pon: `social-api`
   (así Railway construye solo esta carpeta, no la app Android ni `server/`).
3. En **Variables** añade:
   - `DATABASE_URL = ${{Postgres.DATABASE_URL}}` (referencia al Postgres existente)
   - `JWT_SECRET = <cadena aleatoria de 32+ caracteres>`
   - `NODE_ENV = production`
4. Railway detecta `railway.json`: build `npm ci && npm run build`, start `npm run start`
   (que aplica la migración y arranca el server). Healthcheck en `/health`.
5. En **Settings → Networking → Generate Domain** para obtener la URL pública.
   Esa URL es la que consumirá la app Android (Fase B).

> `npm run start` ejecuta `dist/db/migrate.js` (crea las tablas si no existen, es idempotente)
> y luego `dist/index.js`. Seguro de correr en cada despliegue.
