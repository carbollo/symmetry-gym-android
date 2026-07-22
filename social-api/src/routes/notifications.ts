import { Hono } from 'hono'
import { and, eq, sql } from 'drizzle-orm'
import { db } from '../db/client.js'
import { friendships, routineShares, users } from '../db/schema.js'
import { requireAuth, type AuthUser } from '../middleware/auth.js'

export const notificationRoutes = new Hono()

// GET /notifications/summary — cuántas novedades tengo pendientes (para el aviso local).
notificationRoutes.get('/summary', requireAuth, async (c) => {
  const me = c.get('user') as AuthUser

  const [fr] = await db
    .select({ n: sql<number>`count(*)::int` })
    .from(friendships)
    .innerJoin(users, eq(users.id, friendships.requesterId))
    .where(
      and(
        eq(friendships.addresseeId, me.id),
        eq(friendships.status, 'pending'),
        sql`${users.deletedAt} is null`
      )
    )

  const [sr] = await db
    .select({ n: sql<number>`count(*)::int` })
    .from(routineShares)
    .where(and(eq(routineShares.toUserId, me.id), eq(routineShares.status, 'pending')))

  return c.json({ friendRequests: fr?.n ?? 0, sharedRoutines: sr?.n ?? 0 })
})
