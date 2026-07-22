import { sign, verify } from 'hono/jwt'
import type { JWTPayload } from 'hono/utils/jwt/types'
import { env } from '../env.js'

const ALG = 'HS256' as const

export interface TokenClaims extends JWTPayload {
  sub: string // user id
  tv: number // token version (global revocation)
  exp: number // expiry (seconds since epoch)
}

export async function issueToken(userId: string, tokenVersion: number): Promise<string> {
  const now = Math.floor(Date.now() / 1000)
  const payload: TokenClaims = {
    sub: userId,
    tv: tokenVersion,
    exp: now + env.jwtExpiresIn,
  }
  return sign(payload, env.jwtSecret, ALG)
}

export async function verifyToken(token: string): Promise<TokenClaims | null> {
  try {
    const payload = (await verify(token, env.jwtSecret, ALG)) as unknown as TokenClaims
    if (typeof payload.sub !== 'string' || typeof payload.tv !== 'number') return null
    return payload
  } catch {
    return null
  }
}
