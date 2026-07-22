// Idempotent bootstrap DDL. Single source of truth for the social schema.
// Expression indexes (lower(), least/greatest) are fiddly with drizzle-kit
// codegen, so the schema is applied as hand-written, re-runnable DDL. Safe to
// run on every deploy. Does NOT touch the ads server's own `rewards` table —
// that service manages its own schema in the shared DB.
// gen_random_uuid() is a core function on Postgres 13+ (Railway runs 16), so no
// pgcrypto extension is needed — one less privilege to depend on.
export const DDL = /* sql */ `
DO $$ BEGIN
  CREATE TYPE friendship_status AS ENUM ('pending', 'accepted', 'blocked');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE routine_visibility AS ENUM ('private', 'friends', 'public');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS users (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email          varchar(254) NOT NULL,
  password_hash  text NOT NULL,
  username       varchar(30) NOT NULL,
  display_name   varchar(60) NOT NULL,
  token_version  integer NOT NULL DEFAULT 0,
  created_at     timestamptz NOT NULL DEFAULT now(),
  deleted_at     timestamptz
);
CREATE UNIQUE INDEX IF NOT EXISTS users_email_lower_uniq ON users (lower(email));
CREATE UNIQUE INDEX IF NOT EXISTS users_username_lower_uniq ON users (lower(username));

-- Premium de pago (Whop), atado a la cuenta. premium_until = hasta cuándo hay premium pagado.
ALTER TABLE users ADD COLUMN IF NOT EXISTS premium_until timestamptz;
ALTER TABLE users ADD COLUMN IF NOT EXISTS whop_membership_id text;
-- Timestamp del último evento de Whop aplicado: solo se aplica un evento MÁS NUEVO (orden/idempotencia).
ALTER TABLE users ADD COLUMN IF NOT EXISTS premium_event_at timestamptz;
CREATE INDEX IF NOT EXISTS users_whop_membership_idx ON users (whop_membership_id);

CREATE TABLE IF NOT EXISTS friendships (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  requester_id  uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  addressee_id  uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status        friendship_status NOT NULL DEFAULT 'pending',
  blocked_by    uuid REFERENCES users(id) ON DELETE CASCADE,
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT friendships_no_self CHECK (requester_id <> addressee_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS friendships_pair_uniq
  ON friendships (least(requester_id, addressee_id), greatest(requester_id, addressee_id));
CREATE INDEX IF NOT EXISTS friendships_requester_idx ON friendships (requester_id);
CREATE INDEX IF NOT EXISTS friendships_addressee_idx ON friendships (addressee_id);

CREATE TABLE IF NOT EXISTS shared_routines (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id        uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name            varchar(120) NOT NULL,
  payload         jsonb NOT NULL,
  schema_version  integer NOT NULL DEFAULT 1,
  visibility      routine_visibility NOT NULL DEFAULT 'friends',
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  deleted_at      timestamptz
);
CREATE INDEX IF NOT EXISTS shared_routines_owner_idx ON shared_routines (owner_id);

-- Share code for public links (added after the table shipped, hence ADD COLUMN IF NOT EXISTS).
-- Unique index over a nullable column: existing NULLs stay distinct in Postgres.
ALTER TABLE shared_routines ADD COLUMN IF NOT EXISTS code varchar(16);
CREATE UNIQUE INDEX IF NOT EXISTS shared_routines_code_uniq ON shared_routines (code);

-- Directed shares: A sends a routine straight to B, who accepts it into their gallery.
DO $$ BEGIN
  CREATE TYPE routine_share_status AS ENUM ('pending', 'accepted', 'declined');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS routine_shares (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  from_user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  to_user_id      uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name            varchar(120) NOT NULL,
  payload         jsonb NOT NULL,
  schema_version  integer NOT NULL DEFAULT 1,
  status          routine_share_status NOT NULL DEFAULT 'pending',
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT routine_shares_no_self CHECK (from_user_id <> to_user_id)
);
CREATE INDEX IF NOT EXISTS routine_shares_to_idx ON routine_shares (to_user_id, status);

-- Galaxia global: snapshot del planeta de cada usuario (upsert, una fila por usuario).
-- El servidor deriva planet_index/xp_in_planet/stage desde total_xp (misma fórmula que la
-- app); los CHECKs validan aunque un endpoint futuro se salte zod (defensa en profundidad).
-- progressed_at solo avanza cuando total_xp crece: mide progreso real, no aperturas de app.
CREATE TABLE IF NOT EXISTS galaxy_planets (
  user_id        uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  total_xp       integer NOT NULL,
  planet_index   integer NOT NULL,
  xp_in_planet   integer NOT NULL,
  stage          integer NOT NULL,
  species        integer NOT NULL DEFAULT 0,
  seed           integer NOT NULL,
  algo_version   integer NOT NULL DEFAULT 1,
  created_at     timestamptz NOT NULL DEFAULT now(),
  updated_at     timestamptz NOT NULL DEFAULT now(),
  progressed_at  timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT galaxy_total_xp_range CHECK (total_xp BETWEEN 0 AND 120000),
  CONSTRAINT galaxy_planet_range   CHECK (planet_index BETWEEN 0 AND 200),
  CONSTRAINT galaxy_stage_range    CHECK (stage BETWEEN 0 AND 6),
  CONSTRAINT galaxy_species_range  CHECK (species BETWEEN 0 AND 20),
  CONSTRAINT galaxy_xp_in_range    CHECK (xp_in_planet >= 0 AND xp_in_planet < 1200)
);
CREATE INDEX IF NOT EXISTS galaxy_planets_progressed_idx ON galaxy_planets (progressed_at DESC);
`
