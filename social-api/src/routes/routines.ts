import { Hono } from 'hono'
import { randomInt } from 'node:crypto'
import { and, eq, isNull } from 'drizzle-orm'
import { db } from '../db/client.js'
import { sharedRoutines, users } from '../db/schema.js'
import { requireAuth, type AuthUser } from '../middleware/auth.js'
import { shareRoutineSchema } from '../validation/schemas.js'

export const routineRoutes = new Hono()

// Crockford-ish alphabet: no 0/O/1/I/L, so codes are easy to read and dictate.
const CODE_ALPHABET = '23456789ABCDEFGHJKMNPQRSTUVWXYZ'
const CODE_LEN = 8

function newCode(): string {
  let s = ''
  for (let i = 0; i < CODE_LEN; i++) s += CODE_ALPHABET[randomInt(CODE_ALPHABET.length)]
  return s
}

function isUniqueViolation(err: unknown): boolean {
  return typeof err === 'object' && err !== null && 'code' in err && (err as { code: unknown }).code === '23505'
}

// POST /routines — publish a routine, get back a share code.
routineRoutes.post('/', requireAuth, async (c) => {
  const user = c.get('user') as AuthUser
  const body = await c.req.json().catch(() => null)
  const parsed = shareRoutineSchema.safeParse(body)
  if (!parsed.success) {
    return c.json({ error: 'invalid_input', details: parsed.error.flatten() }, 400)
  }
  const { name, payload, schemaVersion } = parsed.data

  // Generate a unique code, retrying if we collide (astronomically rare).
  for (let attempt = 0; attempt < 6; attempt++) {
    const code = newCode()
    try {
      const [row] = await db
        .insert(sharedRoutines)
        .values({ ownerId: user.id, name, code, payload, schemaVersion: schemaVersion ?? 1 })
        .returning({ code: sharedRoutines.code, name: sharedRoutines.name })
      return c.json({ code: row.code, name: row.name }, 201)
    } catch (err) {
      if (isUniqueViolation(err)) continue // code clash, try another
      throw err
    }
  }
  return c.json({ error: 'code_generation_failed' }, 500)
})

// GET /routines/:code — PUBLIC: the code is the secret. Powers the shareable link,
// which must work even for someone who isn't signed in.
routineRoutes.get('/:code', async (c) => {
  const code = (c.req.param('code') ?? '').trim().toUpperCase()
  if (!code) return c.json({ error: 'not_found' }, 404)
  const [row] = await db
    .select({
      name: sharedRoutines.name,
      payload: sharedRoutines.payload,
      schemaVersion: sharedRoutines.schemaVersion,
      ownerUsername: users.username,
    })
    .from(sharedRoutines)
    .innerJoin(users, eq(users.id, sharedRoutines.ownerId))
    .where(and(eq(sharedRoutines.code, code), isNull(sharedRoutines.deletedAt)))
    .limit(1)

  if (!row) return c.json({ error: 'not_found' }, 404)
  return c.json({
    name: row.name,
    payload: row.payload,
    schemaVersion: row.schemaVersion,
    ownerUsername: row.ownerUsername,
  })
})
