import bcrypt from 'bcryptjs'

// bcryptjs (pure JS) over a native argon2 build: zero node-gyp risk on Railway.
const ROUNDS = 12

export function hashPassword(plain: string): Promise<string> {
  return bcrypt.hash(plain, ROUNDS)
}

export function verifyPassword(plain: string, hash: string): Promise<boolean> {
  return bcrypt.compare(plain, hash)
}
