import { Hono } from 'hono'
import { and, eq, sql } from 'drizzle-orm'
import { db } from '../db/client.js'
import { users } from '../db/schema.js'
import { requireAuth, type AuthUser } from '../middleware/auth.js'
import { createCheckoutUrl, whopConfigured, verifyWebhook, parseWhopEvent, webhookTimestampMs } from '../lib/whop.js'

export const premiumRoutes = new Hono()

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const DEFAULT_GRANT_MS = 35 * 24 * 60 * 60 * 1000 // sin fecha de expiración en el payload: 35 días

// POST /premium/checkout — crea el enlace de pago de Whop para el usuario y lo devuelve.
premiumRoutes.post('/checkout', requireAuth, async (c) => {
  const me = c.get('user') as AuthUser
  if (!whopConfigured()) return c.json({ error: 'not_configured' }, 503)
  const origin = new URL(c.req.url).origin
  const url = await createCheckoutUrl(me.id, `${origin}/premium/success`)
  if (!url) return c.json({ error: 'checkout_failed' }, 502)
  return c.json({ url })
})

// GET /premium/status — hasta cuándo tiene premium de pago esta cuenta.
premiumRoutes.get('/status', requireAuth, async (c) => {
  const me = c.get('user') as AuthUser
  const [row] = await db.select({ premiumUntil: users.premiumUntil }).from(users).where(eq(users.id, me.id)).limit(1)
  return c.json({ premiumUntil: row?.premiumUntil ? row.premiumUntil.toISOString() : null })
})

// GET /premium/success — página a la que Whop redirige tras pagar (dentro del navegador/Custom Tab).
premiumRoutes.get('/success', (c) =>
  c.html(`<!doctype html><html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>Premium activado · Zenit</title>
<style>:root{color-scheme:dark}body{margin:0;min-height:100vh;display:grid;place-items:center;background:#0B0B10;color:#F5F6F2;font-family:system-ui,sans-serif;text-align:center;padding:24px}h1{color:#7C5CFF}p{color:#9A9AA8}</style>
</head><body><div><h1>¡Premium activado! 🎉</h1><p>Ya puedes volver a Zenit.<br>Tu premium aparecerá en la app en unos segundos.</p></div></body></html>`)
)

// POST /premium/webhook — Whop nos avisa de compras/cancelaciones (público, con firma).
premiumRoutes.post('/webhook', async (c) => {
  const raw = await c.req.text()
  if (!verifyWebhook(c.req.raw.headers, raw)) return c.json({ error: 'bad_signature' }, 401)

  let body: unknown
  try {
    body = JSON.parse(raw)
  } catch {
    return c.json({ error: 'bad_json' }, 400)
  }

  const ev = parseWhopEvent(body)
  const userId = await findUser(ev.appUserId, ev.membershipId)
  if (!userId) return c.json({ ok: true, note: 'user_not_found' }) // 200: no reintentar en bucle

  // Solo actuamos ante señales EXPLÍCITAS. Un evento ambiguo (p.ej. payment.failed/pending) NO
  // debe quitarle el premium a alguien que pagó: lo ignoramos.
  const action = ev.action.toLowerCase()
  const isInvalidation = /invalid|deactivat|expired|cancel|refund/.test(action)
  const isGrant = ev.valid || /valid|activat|succeed/.test(action)
  if (!isInvalidation && !isGrant) return c.json({ ok: true, note: 'ignored' })

  const eventAt = new Date(webhookTimestampMs(c.req.raw.headers))
  // Solo aplica si este evento es MÁS NUEVO que el último aplicado: neutraliza replays y entregas
  // fuera de orden (un grant reintentado tras una cancelación no vuelve a conceder premium).
  const fresher = sql`(${users.premiumEventAt} is null or ${users.premiumEventAt} < ${eventAt})`

  if (isInvalidation) {
    await db.update(users)
      .set({ premiumUntil: new Date(), premiumEventAt: eventAt })
      .where(and(eq(users.id, userId), fresher))
  } else {
    const until = ev.expiresAtMs ? new Date(ev.expiresAtMs) : new Date(Date.now() + DEFAULT_GRANT_MS)
    const base = { premiumUntil: until, premiumEventAt: eventAt }
    await db.update(users)
      .set(ev.membershipId ? { ...base, whopMembershipId: ev.membershipId } : base)
      .where(and(eq(users.id, userId), fresher))
  }
  return c.json({ ok: true })
})

async function findUser(appUserId: string | null, membershipId: string | null): Promise<string | null> {
  if (appUserId && UUID_RE.test(appUserId)) {
    const [u] = await db.select({ id: users.id }).from(users).where(eq(users.id, appUserId)).limit(1)
    if (u) return u.id
  }
  if (membershipId) {
    const [u] = await db.select({ id: users.id }).from(users).where(eq(users.whopMembershipId, membershipId)).limit(1)
    if (u) return u.id
  }
  return null
}
