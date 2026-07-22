import crypto from 'node:crypto'
import { env } from '../env.js'

const WHOP_API = 'https://api.whop.com/api/v1'
const WHOP_CHECKOUT_HOST = 'https://whop.com'

export function whopConfigured(): boolean {
  return !!(env.whop.apiKey && env.whop.planId)
}

/**
 * Crea una checkout session en Whop con nuestro id de usuario en metadata y devuelve la URL de pago.
 * El metadata vuelve en el webhook, así sabemos a qué cuenta darle premium. null si falla o no configurado.
 */
export async function createCheckoutUrl(appUserId: string, redirectUrl: string): Promise<string | null> {
  if (!env.whop.apiKey || !env.whop.planId) return null
  try {
    const res = await fetch(`${WHOP_API}/checkout_configurations`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${env.whop.apiKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        mode: 'payment',
        plan_id: env.whop.planId,
        redirect_url: redirectUrl,
        metadata: { app_user_id: appUserId },
      }),
    })
    if (!res.ok) {
      console.error('[whop] checkout create failed', res.status, await res.text().catch(() => ''))
      return null
    }
    const json: any = await res.json()
    const purchaseUrl: string | undefined = json.purchase_url ?? json.purchaseUrl
    if (!purchaseUrl) return null
    return purchaseUrl.startsWith('http') ? purchaseUrl : `${WHOP_CHECKOUT_HOST}${purchaseUrl}`
  } catch (err) {
    console.error('[whop] checkout create error', err)
    return null
  }
}

/**
 * Verifica la firma del webhook (spec Standard Webhooks: headers webhook-id/timestamp/signature,
 * HMAC-SHA256 sobre `${id}.${timestamp}.${body}` con el secreto base64-decodificado).
 */
const WEBHOOK_TOLERANCE_S = 5 * 60 // ventana anti-replay

export function verifyWebhook(headers: Headers, rawBody: string): boolean {
  const secret = env.whop.webhookSecret
  if (!secret) return false
  const id = headers.get('webhook-id')
  const timestamp = headers.get('webhook-timestamp')
  const sigHeader = headers.get('webhook-signature')
  if (!id || !timestamp || !sigHeader) return false

  // Frescura: rechaza timestamps muy viejos o del futuro (evita reproducir un webhook capturado).
  const ts = Number(timestamp)
  const nowS = Math.floor(Date.now() / 1000)
  if (Number.isNaN(ts) || Math.abs(nowS - ts) > WEBHOOK_TOLERANCE_S) return false

  const keyMaterial = secret.startsWith('whsec_') ? secret.slice(6) : secret
  const key = Buffer.from(keyMaterial, 'base64')
  const signedContent = `${id}.${timestamp}.${rawBody}`
  const expected = crypto.createHmac('sha256', key).update(signedContent).digest('base64')

  // El header trae una lista separada por espacios de "v1,<firma>". Basta con que una cuadre.
  const provided = sigHeader.split(' ').map((part) => part.split(',')[1] ?? part)
  return provided.some((sig) => safeEqual(sig, expected))
}

/** Instante del evento (ms) según el header webhook-timestamp, para ordenar/idempotencia. */
export function webhookTimestampMs(headers: Headers): number {
  const ts = Number(headers.get('webhook-timestamp'))
  return Number.isNaN(ts) ? Date.now() : ts * 1000
}

function safeEqual(a: string, b: string): boolean {
  const ba = Buffer.from(a)
  const bb = Buffer.from(b)
  return ba.length === bb.length && crypto.timingSafeEqual(ba, bb)
}

export interface WhopEvent {
  action: string
  appUserId: string | null
  membershipId: string | null
  /** ms epoch hasta cuándo vale la membership, o null si no viene. */
  expiresAtMs: number | null
  valid: boolean
}

/** Extrae de forma defensiva los campos que necesitamos del payload del webhook. */
export function parseWhopEvent(body: any): WhopEvent {
  const action: string = body?.action ?? body?.type ?? body?.event ?? ''
  const data = body?.data ?? body?.membership ?? body ?? {}
  const metadata = data?.metadata ?? data?.membership?.metadata ?? {}
  const appUserId: string | null = metadata?.app_user_id ?? metadata?.appUserId ?? null
  const membershipId: string | null = data?.id ?? data?.membership_id ?? data?.membership?.id ?? null

  const valid: boolean =
    data?.valid === true ||
    data?.status === 'active' ||
    data?.status === 'completed' ||
    /valid|activated|succeeded/i.test(action)

  const rawExpiry =
    data?.renewal_period_end ?? data?.expires_at ?? data?.valid_until ?? data?.membership?.expires_at ?? null
  let expiresAtMs: number | null = null
  if (rawExpiry != null) {
    const n = typeof rawExpiry === 'number' ? rawExpiry : Number(rawExpiry)
    if (!Number.isNaN(n)) expiresAtMs = n < 1e12 ? n * 1000 : n // segundos -> ms si hace falta
    else {
      const d = Date.parse(String(rawExpiry))
      if (!Number.isNaN(d)) expiresAtMs = d
    }
  }

  return { action, appUserId, membershipId, expiresAtMs, valid }
}
