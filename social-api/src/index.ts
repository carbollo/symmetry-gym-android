import { serve } from '@hono/node-server'
import { Hono } from 'hono'
import { cors } from 'hono/cors'
import { logger } from 'hono/logger'
import { env } from './env.js'
import { authRoutes } from './routes/auth.js'
import { meRoutes } from './routes/me.js'
import { routineRoutes } from './routes/routines.js'
import { routineShareRoutes } from './routes/routineShares.js'
import { linkRoutes } from './routes/links.js'
import { userRoutes } from './routes/users.js'
import { friendRoutes } from './routes/friends.js'
import { accountRoutes } from './routes/account.js'
import { notificationRoutes } from './routes/notifications.js'
import { premiumRoutes } from './routes/premium.js'
import { galaxyRoutes } from './routes/galaxy.js'

const app = new Hono()

app.use('*', logger())
app.use('*', cors())

app.get('/health', (c) => c.json({ ok: true, service: 'zenit-social-api' }))

app.route('/auth', authRoutes)
app.route('/me', meRoutes)
app.route('/routines', routineRoutes)
app.route('/routine-shares', routineShareRoutes)
app.route('/users', userRoutes)
app.route('/friends', friendRoutes)
app.route('/account', accountRoutes)
app.route('/notifications', notificationRoutes)
app.route('/premium', premiumRoutes)
app.route('/galaxy', galaxyRoutes)
app.route('/', linkRoutes) // /r/:code landing + /.well-known/assetlinks.json

app.notFound((c) => c.json({ error: 'not_found' }, 404))
app.onError((err, c) => {
  console.error('[unhandled]', err)
  return c.json({ error: 'internal_error' }, 500)
})

serve({ fetch: app.fetch, port: env.port }, (info) => {
  console.log(`zenit-social-api listening on :${info.port}`)
})
