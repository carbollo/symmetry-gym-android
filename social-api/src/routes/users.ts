import { Hono } from 'hono'
import { and, eq, ilike, ne, or, sql } from 'drizzle-orm'
import { db } from '../db/client.js'
import { users, friendships } from '../db/schema.js'
import { requireAuth, type AuthUser } from '../middleware/auth.js'

export const userRoutes = new Hono()

// Condición SQL: la fila de amistad de la pareja (me, other), en cualquier dirección.
function pairWhere(a: string, b: string) {
  return or(
    and(eq(friendships.requesterId, a), eq(friendships.addresseeId, b)),
    and(eq(friendships.requesterId, b), eq(friendships.addresseeId, a))
  )
}

// GET /users/search?q= — busca usuarios por nombre (excluye a uno mismo).
userRoutes.get('/search', requireAuth, async (c) => {
  const me = c.get('user') as AuthUser
  const q = (c.req.query('q') ?? '').trim()
  if (q.length < 2) return c.json({ users: [] })

  const rows = await db
    .select({ id: users.id, username: users.username, displayName: users.displayName })
    .from(users)
    .where(and(ilike(users.username, `%${q}%`), ne(users.id, me.id), sql`${users.deletedAt} is null`))
    .limit(20)

  return c.json({ users: rows })
})

// GET /users/:username — perfil público + estado de amistad respecto a mí.
userRoutes.get('/:username', requireAuth, async (c) => {
  const me = c.get('user') as AuthUser
  const username = (c.req.param('username') ?? '').trim()
  if (!username) return c.json({ error: 'not_found' }, 404)

  const [u] = await db
    .select({
      id: users.id,
      username: users.username,
      displayName: users.displayName,
      createdAt: users.createdAt,
      deletedAt: users.deletedAt,
    })
    .from(users)
    .where(sql`lower(${users.username}) = lower(${username})`)
    .limit(1)

  if (!u || u.deletedAt) return c.json({ error: 'not_found' }, 404)

  let friendship: { status: string; direction: 'incoming' | 'outgoing' | null } = {
    status: 'none',
    direction: null,
  }
  if (u.id !== me.id) {
    const [f] = await db
      .select({ requesterId: friendships.requesterId, status: friendships.status })
      .from(friendships)
      .where(pairWhere(me.id, u.id))
      .limit(1)
    if (f) {
      friendship = { status: f.status, direction: f.requesterId === me.id ? 'outgoing' : 'incoming' }
    }
  } else {
    friendship = { status: 'self', direction: null }
  }

  return c.json({
    user: { id: u.id, username: u.username, displayName: u.displayName, createdAt: u.createdAt },
    friendship,
  })
})
