import { sql } from 'drizzle-orm'
import {
  pgTable,
  pgEnum,
  uuid,
  varchar,
  text,
  integer,
  timestamp,
  jsonb,
  index,
  uniqueIndex,
  check,
} from 'drizzle-orm/pg-core'

export const friendshipStatus = pgEnum('friendship_status', ['pending', 'accepted', 'blocked'])
export const routineVisibility = pgEnum('routine_visibility', ['private', 'friends', 'public'])
export const routineShareStatus = pgEnum('routine_share_status', ['pending', 'accepted', 'declined'])

export const users = pgTable(
  'users',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    email: varchar('email', { length: 254 }).notNull(), // stored lowercased
    passwordHash: text('password_hash').notNull(),
    username: varchar('username', { length: 30 }).notNull(),
    displayName: varchar('display_name', { length: 60 }).notNull(),
    // Bumping this invalidates every token in circulation (claim 'tv'): global revocation.
    tokenVersion: integer('token_version').notNull().default(0),
    // Premium de pago (Whop). premium_until en el futuro = premium activo.
    premiumUntil: timestamp('premium_until', { withTimezone: true }),
    whopMembershipId: text('whop_membership_id'),
    premiumEventAt: timestamp('premium_event_at', { withTimezone: true }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    deletedAt: timestamp('deleted_at', { withTimezone: true }),
  },
  (t) => ({
    emailLowerUniq: uniqueIndex('users_email_lower_uniq').on(sql`lower(${t.email})`),
    usernameLowerUniq: uniqueIndex('users_username_lower_uniq').on(sql`lower(${t.username})`),
  })
)

export const friendships = pgTable(
  'friendships',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    requesterId: uuid('requester_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    addresseeId: uuid('addressee_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    status: friendshipStatus('status').notNull().default('pending'),
    blockedBy: uuid('blocked_by').references(() => users.id, { onDelete: 'cascade' }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => ({
    // One row per pair, regardless of who requested: least/greatest makes the order irrelevant.
    pairUniq: uniqueIndex('friendships_pair_uniq').on(
      sql`least(${t.requesterId}, ${t.addresseeId})`,
      sql`greatest(${t.requesterId}, ${t.addresseeId})`
    ),
    requesterIdx: index('friendships_requester_idx').on(t.requesterId),
    addresseeIdx: index('friendships_addressee_idx').on(t.addresseeId),
    noSelf: check('friendships_no_self', sql`${t.requesterId} <> ${t.addresseeId}`),
  })
)

export const sharedRoutines = pgTable(
  'shared_routines',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    ownerId: uuid('owner_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    name: varchar('name', { length: 120 }).notNull(),
    // Short, unguessable share code. Whoever has it can import the routine.
    code: varchar('code', { length: 16 }),
    // The portable routine JSON, stored verbatim. The server validates its SHAPE only.
    payload: jsonb('payload').notNull(),
    schemaVersion: integer('schema_version').notNull().default(1),
    visibility: routineVisibility('visibility').notNull().default('friends'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
    deletedAt: timestamp('deleted_at', { withTimezone: true }), // soft delete = "stop sharing"
  },
  (t) => ({
    ownerIdx: index('shared_routines_owner_idx').on(t.ownerId),
  })
)

// Snapshot del planeta de cada usuario para la galaxia global. Una fila por usuario
// (upsert). El cliente solo publica totalXp+species+seed: el servidor DERIVA planeta,
// etapa y progreso con la misma fórmula que la app, así no existen estados incoherentes
// y solo hay un número que acotar. La semilla es int32 para sobrevivir al JSON de JS.
// progressed_at solo avanza cuando total_xp CRECE: la galaxia lista planetas que han
// crecido recientemente, no aperturas de app.
export const galaxyPlanets = pgTable(
  'galaxy_planets',
  {
    userId: uuid('user_id').primaryKey().references(() => users.id, { onDelete: 'cascade' }),
    totalXp: integer('total_xp').notNull(),
    planetIndex: integer('planet_index').notNull(),
    xpInPlanet: integer('xp_in_planet').notNull(),
    stage: integer('stage').notNull(),
    species: integer('species').notNull().default(0),
    seed: integer('seed').notNull(),
    algoVersion: integer('algo_version').notNull().default(1),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
    progressedAt: timestamp('progressed_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => ({
    progressedIdx: index('galaxy_planets_progressed_idx').on(t.progressedAt),
  })
)

export const routineShares = pgTable(
  'routine_shares',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    fromUserId: uuid('from_user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    toUserId: uuid('to_user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    name: varchar('name', { length: 120 }).notNull(),
    payload: jsonb('payload').notNull(),
    schemaVersion: integer('schema_version').notNull().default(1),
    status: routineShareStatus('status').notNull().default('pending'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => ({
    toIdx: index('routine_shares_to_idx').on(t.toUserId, t.status),
    noSelf: check('routine_shares_no_self', sql`${t.fromUserId} <> ${t.toUserId}`),
  })
)
