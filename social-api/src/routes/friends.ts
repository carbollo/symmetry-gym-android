import { Hono } from 'hono'
import { and, desc, eq, or, sql } from 'drizzle-orm'
import { db } from '../db/client.js'
import { users, friendships } from '../db/schema.js'
import { requireAuth, type AuthUser } from '../middleware/auth.js'
import { friendRequestSchema } from '../validation/schemas.js'

export const friendRoutes = new Hono()

function pairWhere(a: string, b: string) {
  return or(
    and(eq(friendships.requesterId, a), eq(friendships.addresseeId, b)),
    and(eq(friendships.requesterId, b), eq(friendships.addresseeId, a))
  )
}

function isUniqueViolation(err: unknown): boolean {
  return typeof err === 'object' && err !== null && 'code' in err && (err as { code: unknown }).code === '23505'
}

// POST /friends/requests — enviar solicitud (o aceptar si el otro ya te la mandó).
friendRoutes.post('/requests', requireAuth, async (c) => {
  const me = c.get('user') as AuthUser
  const body = await c.req.json().catch(() => null)
  const parsed = friendRequestSchema.safeParse(body)
  if (!parsed.success) return c.json({ error: 'invalid_input' }, 400)

  const [other] = await db
    .select({ id: users.id, deletedAt: users.deletedAt })
    .from(users)
    .where(sql`lower(${users.username}) = lower(${parsed.data.toUsername})`)
    .limit(1)
  if (!other || other.deletedAt) return c.json({ error: 'user_not_found' }, 404)
  if (other.id === me.id) return c.json({ error: 'cannot_add_self' }, 400)

  const [existing] = await db
    .select({
      id: friendships.id,
      requesterId: friendships.requesterId,
      addresseeId: friendships.addresseeId,
      status: friendships.status,
    })
    .from(friendships)
    .where(pairWhere(me.id, other.id))
    .limit(1)

  if (existing) {
    if (existing.status === 'blocked') return c.json({ error: 'blocked' }, 403)
    if (existing.status === 'accepted') return c.json({ status: 'accepted' })
    // pending: si soy el destinatario de su solicitud, la acepto; si no, ya la envié yo.
    if (existing.addresseeId === me.id) {
      await db
        .update(friendships)
        .set({ status: 'accepted', updatedAt: new Date() })
        .where(eq(friendships.id, existing.id))
      return c.json({ status: 'accepted' })
    }
    return c.json({ status: 'pending', direction: 'outgoing' })
  }

  try {
    await db.insert(friendships).values({ requesterId: me.id, addresseeId: other.id, status: 'pending' })
    return c.json({ status: 'pending', direction: 'outgoing' }, 201)
  } catch (err) {
    if (!isUniqueViolation(err)) throw err
    // Carrera: el otro creó la fila entre nuestro SELECT y el INSERT. Re-leemos y resolvemos
    // igual que en la rama `existing` (puede tocar auto-aceptar si yo soy el destinatario).
    const [row] = await db
      .select({ id: friendships.id, addresseeId: friendships.addresseeId, status: friendships.status })
      .from(friendships)
      .where(pairWhere(me.id, other.id))
      .limit(1)
    if (row) {
      if (row.status === 'blocked') return c.json({ error: 'blocked' }, 403)
      if (row.status === 'accepted') return c.json({ status: 'accepted' })
      if (row.addresseeId === me.id) {
        await db
          .update(friendships)
          .set({ status: 'accepted', updatedAt: new Date() })
          .where(eq(friendships.id, row.id))
        return c.json({ status: 'accepted' })
      }
    }
    return c.json({ status: 'pending', direction: 'outgoing' })
  }
})

// GET /friends/requests — solicitudes entrantes pendientes.
friendRoutes.get('/requests', requireAuth, async (c) => {
  const me = c.get('user') as AuthUser
  const rows = await db
    .select({
      id: friendships.id,
      fromUsername: users.username,
      fromDisplayName: users.displayName,
      createdAt: friendships.createdAt,
    })
    .from(friendships)
    .innerJoin(users, eq(users.id, friendships.requesterId))
    .where(
      and(
        eq(friendships.addresseeId, me.id),
        eq(friendships.status, 'pending'),
        sql`${users.deletedAt} is null`
      )
    )
    .orderBy(desc(friendships.createdAt))

  return c.json({ requests: rows })
})

// POST /friends/requests/:id/accept — aceptar (solo el destinatario, y estando pendiente).
friendRoutes.post('/requests/:id/accept', requireAuth, async (c) => {
  const me = c.get('user') as AuthUser
  const id = c.req.param('id')
  if (!id) return c.json({ error: 'not_found' }, 404)

  const rows = await db
    .update(friendships)
    .set({ status: 'accepted', updatedAt: new Date() })
    .where(and(eq(friendships.id, id), eq(friendships.addresseeId, me.id), eq(friendships.status, 'pending')))
    .returning({ id: friendships.id })

  if (rows.length === 0) return c.json({ error: 'not_found' }, 404)
  return c.json({ ok: true })
})

// POST /friends/requests/:id/reject — rechazar una solicitud entrante (borra la fila).
friendRoutes.post('/requests/:id/reject', requireAuth, async (c) => {
  const me = c.get('user') as AuthUser
  const id = c.req.param('id')
  if (!id) return c.json({ error: 'not_found' }, 404)

  const rows = await db
    .delete(friendships)
    .where(and(eq(friendships.id, id), eq(friendships.addresseeId, me.id), eq(friendships.status, 'pending')))
    .returning({ id: friendships.id })

  if (rows.length === 0) return c.json({ error: 'not_found' }, 404)
  return c.json({ ok: true })
})

// GET /friends — mis amigos (amistades aceptadas).
friendRoutes.get('/', requireAuth, async (c) => {
  const me = c.get('user') as AuthUser
  const rows = await db
    .select({ id: users.id, username: users.username, displayName: users.displayName })
    .from(friendships)
    .innerJoin(
      users,
      sql`${users.id} = case when ${friendships.requesterId} = ${me.id} then ${friendships.addresseeId} else ${friendships.requesterId} end`
    )
    .where(
      and(
        eq(friendships.status, 'accepted'),
        or(eq(friendships.requesterId, me.id), eq(friendships.addresseeId, me.id)),
        sql`${users.deletedAt} is null`
      )
    )
    .orderBy(users.username)

  return c.json({ friends: rows })
})

// DELETE /friends/:username — eliminar amigo o cancelar solicitud enviada.
friendRoutes.delete('/:username', requireAuth, async (c) => {
  const me = c.get('user') as AuthUser
  const username = (c.req.param('username') ?? '').trim()
  if (!username) return c.json({ error: 'not_found' }, 404)

  const [other] = await db
    .select({ id: users.id })
    .from(users)
    .where(sql`lower(${users.username}) = lower(${username})`)
    .limit(1)
  if (!other) return c.json({ error: 'not_found' }, 404)

  await db.delete(friendships).where(pairWhere(me.id, other.id))
  return c.json({ ok: true })
})
