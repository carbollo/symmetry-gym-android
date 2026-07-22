import { Hono } from 'hono'
import { sql } from 'drizzle-orm'
import { db } from '../db/client.js'
import { users } from '../db/schema.js'
import { hashPassword, verifyPassword } from '../lib/password.js'
import { issueToken } from '../lib/jwt.js'
import { registerSchema, loginSchema } from '../validation/schemas.js'

export const authRoutes = new Hono()

function publicUser(row: { id: string; email: string; username: string; displayName: string }) {
  return {
    id: row.id,
    email: row.email,
    username: row.username,
    displayName: row.displayName,
  }
}

authRoutes.post('/register', async (c) => {
  const body = await c.req.json().catch(() => null)
  const parsed = registerSchema.safeParse(body)
  if (!parsed.success) {
    return c.json({ error: 'invalid_input', details: parsed.error.flatten() }, 400)
  }

  const { email, password, username } = parsed.data
  const displayName = parsed.data.displayName ?? username
  const passwordHash = await hashPassword(password)

  try {
    const [row] = await db
      .insert(users)
      .values({ email, passwordHash, username, displayName })
      .returning({
        id: users.id,
        email: users.email,
        username: users.username,
        displayName: users.displayName,
        tokenVersion: users.tokenVersion,
      })

    const token = await issueToken(row.id, row.tokenVersion)
    return c.json({ token, user: publicUser(row) }, 201)
  } catch (err: unknown) {
    // 23505 = unique_violation. The violated index name (and the detail/message)
    // tell us which field collided — no extra query needed.
    if (isUniqueViolation(err)) {
      return c.json({ error: 'already_exists', field: violatedField(err) }, 409)
    }
    throw err
  }
})

authRoutes.post('/login', async (c) => {
  const body = await c.req.json().catch(() => null)
  const parsed = loginSchema.safeParse(body)
  if (!parsed.success) {
    return c.json({ error: 'invalid_input' }, 400)
  }

  const { email, password } = parsed.data
  const [row] = await db
    .select({
      id: users.id,
      email: users.email,
      username: users.username,
      displayName: users.displayName,
      passwordHash: users.passwordHash,
      tokenVersion: users.tokenVersion,
      deletedAt: users.deletedAt,
    })
    .from(users)
    .where(sql`lower(${users.email}) = ${email}`)
    .limit(1)

  // Constant-ish path: always run a compare so timing doesn't leak account existence.
  const hash = row?.passwordHash ?? '$2a$12$0000000000000000000000000000000000000000000000000000'
  const ok = await verifyPassword(password, hash)

  if (!row || row.deletedAt || !ok) {
    return c.json({ error: 'invalid_credentials' }, 401)
  }

  const token = await issueToken(row.id, row.tokenVersion)
  return c.json({ token, user: publicUser(row) }, 200)
})

function isUniqueViolation(err: unknown): boolean {
  return typeof err === 'object' && err !== null && 'code' in err && (err as { code: unknown }).code === '23505'
}

// Which field collided, read from the violated index name (postgres-js exposes it
// as `constraint_name`, PGlite as `constraint`), falling back to detail/message.
function violatedField(err: unknown): 'email' | 'username' {
  const e = err as { constraint_name?: string; constraint?: string; detail?: string; message?: string }
  const haystack = `${e.constraint_name ?? ''} ${e.constraint ?? ''} ${e.detail ?? ''} ${e.message ?? ''}`.toLowerCase()
  return haystack.includes('username') ? 'username' : 'email'
}
