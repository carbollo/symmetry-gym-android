import { Hono } from 'hono'
import { and, desc, eq, or, sql } from 'drizzle-orm'
import { db } from '../db/client.js'
import { routineShares, users, friendships } from '../db/schema.js'
import { requireAuth, type AuthUser } from '../middleware/auth.js'
import { sendRoutineSchema } from '../validation/schemas.js'

export const routineShareRoutes = new Hono()

/** ¿Son [a] y [b] amigos aceptados? */
async function areFriends(a: string, b: string): Promise<boolean> {
  const [row] = await db
    .select({ id: friendships.id })
    .from(friendships)
    .where(
      and(
        eq(friendships.status, 'accepted'),
        or(
          and(eq(friendships.requesterId, a), eq(friendships.addresseeId, b)),
          and(eq(friendships.requesterId, b), eq(friendships.addresseeId, a))
        )
      )
    )
    .limit(1)
  return !!row
}

// POST /routine-shares — send a routine straight to a user by username.
routineShareRoutes.post('/', requireAuth, async (c) => {
  const me = c.get('user') as AuthUser
  const body = await c.req.json().catch(() => null)
  const parsed = sendRoutineSchema.safeParse(body)
  if (!parsed.success) {
    return c.json({ error: 'invalid_input', details: parsed.error.flatten() }, 400)
  }
  const { toUsername, name, payload, schemaVersion } = parsed.data

  const [recipient] = await db
    .select({ id: users.id, username: users.username, deletedAt: users.deletedAt })
    .from(users)
    .where(sql`lower(${users.username}) = lower(${toUsername})`)
    .limit(1)

  if (!recipient || recipient.deletedAt) return c.json({ error: 'user_not_found' }, 404)
  if (recipient.id === me.id) return c.json({ error: 'cannot_share_self' }, 400)
  // Solo se pueden enviar rutinas a amigos (los enlaces públicos siguen siendo abiertos).
  if (!(await areFriends(me.id, recipient.id))) return c.json({ error: 'not_friends' }, 403)

  const [row] = await db
    .insert(routineShares)
    .values({
      fromUserId: me.id,
      toUserId: recipient.id,
      name,
      payload,
      schemaVersion: schemaVersion ?? 1,
    })
    .returning({ id: routineShares.id })

  return c.json({ id: row.id, toUsername: recipient.username }, 201)
})

// GET /routine-shares/incoming — pending routines waiting for me to accept.
routineShareRoutes.get('/incoming', requireAuth, async (c) => {
  const me = c.get('user') as AuthUser
  const rows = await db
    .select({
      id: routineShares.id,
      name: routineShares.name,
      createdAt: routineShares.createdAt,
      fromUsername: users.username,
    })
    .from(routineShares)
    .innerJoin(users, eq(users.id, routineShares.fromUserId))
    .where(and(eq(routineShares.toUserId, me.id), eq(routineShares.status, 'pending')))
    .orderBy(desc(routineShares.createdAt))

  return c.json({ shares: rows })
})

// POST /routine-shares/:id/accept — returns the payload so the app can import it.
// Atomic: the UPDATE guards on status='pending' and RETURNs the row, so two concurrent
// accepts of the same share can't both succeed (only the first flips it and gets the payload).
routineShareRoutes.post('/:id/accept', requireAuth, async (c) => {
  const me = c.get('user') as AuthUser
  const id = c.req.param('id')
  if (!id) return c.json({ error: 'not_found' }, 404)

  const [share] = await db
    .update(routineShares)
    .set({ status: 'accepted', updatedAt: new Date() })
    .where(
      and(
        eq(routineShares.id, id),
        eq(routineShares.toUserId, me.id),
        eq(routineShares.status, 'pending')
      )
    )
    .returning({ name: routineShares.name, payload: routineShares.payload })

  if (!share) return c.json({ error: 'not_found' }, 404)
  return c.json({ name: share.name, payload: share.payload })
})

// POST /routine-shares/:id/decline — dismiss a pending share.
routineShareRoutes.post('/:id/decline', requireAuth, async (c) => {
  const me = c.get('user') as AuthUser
  const id = c.req.param('id')
  if (!id) return c.json({ error: 'not_found' }, 404)

  const result = await db
    .update(routineShares)
    .set({ status: 'declined', updatedAt: new Date() })
    .where(
      and(
        eq(routineShares.id, id),
        eq(routineShares.toUserId, me.id),
        eq(routineShares.status, 'pending')
      )
    )
    .returning({ id: routineShares.id })

  if (result.length === 0) return c.json({ error: 'not_found' }, 404)
  return c.json({ ok: true })
})
