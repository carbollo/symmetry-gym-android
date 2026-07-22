import { z } from 'zod'

// Usernames: 3-30 chars, letters/numbers/._-, must start with a letter or digit.
const usernameRegex = /^[a-zA-Z0-9][a-zA-Z0-9._-]{2,29}$/

export const registerSchema = z.object({
  email: z.string().trim().toLowerCase().email().max(254),
  password: z.string().min(8).max(200),
  username: z
    .string()
    .trim()
    .regex(usernameRegex, 'username must be 3-30 chars: letters, digits, . _ -'),
  displayName: z.string().trim().min(1).max(60).optional(),
})

export const loginSchema = z.object({
  email: z.string().trim().toLowerCase().email().max(254),
  password: z.string().min(1).max(200),
})

// A shared routine: a name + the portable JSON payload. The payload shape is the
// app's concern; the server only bounds its size and confirms it's an object.
export const shareRoutineSchema = z.object({
  name: z.string().trim().min(1).max(120),
  schemaVersion: z.number().int().positive().max(1000).optional(),
  payload: z
    .record(z.any())
    .refine((p) => JSON.stringify(p).length <= 100_000, 'payload too large (max 100KB)'),
})

// Directed share: same routine payload, addressed to a recipient by username.
export const sendRoutineSchema = shareRoutineSchema.extend({
  toUsername: z.string().trim().min(1).max(30),
})

// Amigos: solicitar por nombre de usuario.
export const friendRequestSchema = z.object({
  toUsername: z.string().trim().min(1).max(30),
})

// Cuenta: cambiar contraseña / editar nombre visible.
export const passwordChangeSchema = z.object({
  currentPassword: z.string().min(1).max(200),
  newPassword: z.string().min(8).max(200),
})

export const accountUpdateSchema = z.object({
  displayName: z.string().trim().min(1).max(60),
})

export type RegisterInput = z.infer<typeof registerSchema>
export type LoginInput = z.infer<typeof loginSchema>
export type ShareRoutineInput = z.infer<typeof shareRoutineSchema>
export type SendRoutineInput = z.infer<typeof sendRoutineSchema>
export type FriendRequestInput = z.infer<typeof friendRequestSchema>
