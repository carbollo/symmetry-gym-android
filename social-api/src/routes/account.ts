import { Hono } from 'hono'
import { eq } from 'drizzle-orm'
import { db } from '../db/client.js'
import { users } from '../db/schema.js'
import { requireAuth, type AuthUser } from '../middleware/auth.js'
import { hashPassword, verifyPassword } from '../lib/password.js'
import { issueToken } from '../lib/jwt.js'
import { passwordChangeSchema, accountUpdateSchema } from '../validation/schemas.js'

export const accountRoutes = new Hono()

// POST /account/password — cambiar contraseña (requiere la actual). Invalida las OTRAS sesiones
// (sube token_version) y devuelve un token nuevo para que la sesión actual siga válida.
accountRoutes.post('/password', requireAuth, async (c) => {
  const me = c.get('user') as AuthUser
  const body = await c.req.json().catch(() => null)
  const parsed = passwordChangeSchema.safeParse(body)
  if (!parsed.success) return c.json({ error: 'invalid_input' }, 400)

  const [row] = await db.select({ passwordHash: users.passwordHash }).from(users).where(eq(users.id, me.id)).limit(1)
  if (!row || !(await verifyPassword(parsed.data.currentPassword, row.passwordHash))) {
    return c.json({ error: 'wrong_password' }, 403)
  }

  const newTv = me.tokenVersion + 1
  const passwordHash = await hashPassword(parsed.data.newPassword)
  await db.update(users).set({ passwordHash, tokenVersion: newTv }).where(eq(users.id, me.id))
  const token = await issueToken(me.id, newTv)
  return c.json({ token })
})

// POST /account/profile — editar el nombre visible. (POST y no PATCH: HttpURLConnection
// del cliente Android no siempre admite el método PATCH.)
accountRoutes.post('/profile', requireAuth, async (c) => {
  const me = c.get('user') as AuthUser
  const body = await c.req.json().catch(() => null)
  const parsed = accountUpdateSchema.safeParse(body)
  if (!parsed.success) return c.json({ error: 'invalid_input' }, 400)

  const [row] = await db
    .update(users)
    .set({ displayName: parsed.data.displayName })
    .where(eq(users.id, me.id))
    .returning({ id: users.id, email: users.email, username: users.username, displayName: users.displayName })

  return c.json({ user: row })
})

// DELETE /account — borrar la cuenta (soft delete) e invalidar todos los tokens.
accountRoutes.delete('/', requireAuth, async (c) => {
  const me = c.get('user') as AuthUser
  await db
    .update(users)
    .set({ deletedAt: new Date(), tokenVersion: me.tokenVersion + 1 })
    .where(eq(users.id, me.id))
  return c.json({ ok: true })
})
