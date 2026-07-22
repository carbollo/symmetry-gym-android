import type { AuthUser } from './middleware/auth.js'

// Makes c.set('user', ...) / c.get('user') type-safe across the app.
declare module 'hono' {
  interface ContextVariableMap {
    user: AuthUser
  }
}
