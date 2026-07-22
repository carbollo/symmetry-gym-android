import type { Context, Next } from 'hono'
import { eq } from 'drizzle-orm'
import { db } from '../db/client.js'
import { users } from '../db/schema.js'
import { verifyToken } from '../lib/jwt.js'

export interface AuthUser {
  id: string
  email: string
  username: string
  displayName: string
  tokenVersion: number
}

// Reads Bearer token, verifies signature+expiry, then confirms the user still
// exists, isn't soft-deleted, and the token's version matches (not revoked).
export async function requireAuth(c: Context, next: Next) {
  const header = c.req.header('Authorization') ?? ''
  const match = header.match(/^Bearer\s+(.+)$/i)
  if (!match) {
    return c.json({ error: 'missing_token' }, 401)
  }

  const claims = await verifyToken(match[1])
  if (!claims) {
    return c.json({ error: 'invalid_token' }, 401)
  }

  const [row] = await db
    .select({
      id: users.id,
      email: users.email,
      username: users.username,
      displayName: users.displayName,
      tokenVersion: users.tokenVersion,
      deletedAt: users.deletedAt,
    })
    .from(users)
    .where(eq(users.id, claims.sub))
    .limit(1)

  if (!row || row.deletedAt || row.tokenVersion !== claims.tv) {
    return c.json({ error: 'invalid_token' }, 401)
  }

  const authUser: AuthUser = {
    id: row.id,
    email: row.email,
    username: row.username,
    displayName: row.displayName,
    tokenVersion: row.tokenVersion,
  }
  c.set('user', authUser)
  await next()
}
