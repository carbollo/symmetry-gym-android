import { drizzle } from 'drizzle-orm/postgres-js'
import postgres from 'postgres'
import { env } from '../env.js'
import * as schema from './schema.js'

// Railway's internal Postgres connection does not require SSL; an external one might.
const needsSsl = /[?&]sslmode=require/.test(env.databaseUrl) || process.env.PGSSL === 'require'

const queryClient = postgres(env.databaseUrl, {
  max: Number(process.env.PGPOOL_MAX ?? 10),
  ssl: needsSsl ? 'require' : undefined,
})

export const db = drizzle(queryClient, { schema })
export { schema }
