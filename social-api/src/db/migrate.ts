import postgres from 'postgres'
import { env } from '../env.js'
import { DDL } from './ddl.js'

async function main() {
  const needsSsl = /[?&]sslmode=require/.test(env.databaseUrl) || process.env.PGSSL === 'require'
  const client = postgres(env.databaseUrl, { max: 1, ssl: needsSsl ? 'require' : undefined })
  try {
    await client.unsafe(DDL)
    console.log('[migrate] schema applied')
  } finally {
    await client.end()
  }
}

main().catch((err) => {
  console.error('[migrate] failed', err)
  process.exit(1)
})
