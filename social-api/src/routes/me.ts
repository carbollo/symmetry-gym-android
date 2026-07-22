import { Hono } from 'hono'
import { requireAuth, type AuthUser } from '../middleware/auth.js'

export const meRoutes = new Hono()

meRoutes.get('/', requireAuth, (c) => {
  const user = c.get('user') as AuthUser
  return c.json({
    user: {
      id: user.id,
      email: user.email,
      username: user.username,
      displayName: user.displayName,
    },
  })
})
