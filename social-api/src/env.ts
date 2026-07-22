/** Reads and validates the environment once at startup. Fails fast on a bad config. */

function required(name: string): string {
  const value = process.env[name]
  if (!value || value.trim() === '') {
    throw new Error(`Missing required env var: ${name}`)
  }
  return value
}

const jwtSecret = required('JWT_SECRET')
if (jwtSecret.length < 32) {
  throw new Error('JWT_SECRET must be at least 32 characters')
}

function optional(name: string): string | null {
  const value = process.env[name]
  return value && value.trim() !== '' ? value : null
}

export const env = {
  port: Number(process.env.PORT ?? 3001),
  databaseUrl: required('DATABASE_URL'),
  jwtSecret,
  // Token lifetime in seconds. The phone stores one long-lived token, no refresh flow.
  jwtExpiresIn: Number(process.env.JWT_EXPIRES_IN ?? 60 * 60 * 24 * 30),
  isProd: process.env.NODE_ENV === 'production',
  // Whop (premium de pago). Opcionales: sin ellas el checkout responde "no configurado".
  whop: {
    apiKey: optional('WHOP_API_KEY'),
    webhookSecret: optional('WHOP_WEBHOOK_SECRET'),
    planId: optional('WHOP_PLAN_ID'),
  },
}
