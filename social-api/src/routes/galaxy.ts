import { Hono } from 'hono'
import { desc, eq, sql } from 'drizzle-orm'
import { z } from 'zod'
import { db } from '../db/client.js'
import { galaxyPlanets, users } from '../db/schema.js'
import { requireAuth, type AuthUser } from '../middleware/auth.js'

// El cliente publica UN número de progreso (totalXp) más la semilla visual y las especies;
// el servidor deriva planeta/etapa/progreso con la MISMA fórmula entera que la app
// (PlanetEngine.kt). Así no existen snapshots incoherentes y solo hay un número que acotar.
// La galaxia es cosmética (no hay premios), así que verificar el progreso server-side no
// compensa: exigiría subir el historial de entrenos entero, que es justo lo que NO queremos.

// ---- Fórmula compartida con la app (matemática entera pura: cero drift) ----
const BASE_THRESHOLDS = [0, 10, 110, 210, 330, 460]
const BASE_COST = 600
const costPct = (i: number) => Math.min(100 + 20 * i, 200)
const planetCost = (i: number) => Math.floor((BASE_COST * costPct(i)) / 100)

function derive(totalXp: number) {
  let index = 0
  let xp = totalXp
  while (xp >= planetCost(index)) {
    xp -= planetCost(index)
    index++
  }
  const pct = costPct(index)
  let stage = 0
  for (let k = 0; k < BASE_THRESHOLDS.length; k++) {
    if (xp >= Math.floor((BASE_THRESHOLDS[k] * pct) / 100)) stage = k
  }
  return { planetIndex: index, xpInPlanet: xp, stage, planetCost: planetCost(index) }
}

// totalXp máx: 25 pts/día × 365 × ~13 años ≈ 118k. Nadie legítimo lo supera; a nadie castiga.
const publishSchema = z
  .object({
    totalXp: z.number().int().min(0).max(120_000),
    species: z.number().int().min(0).max(20),
    seed: z.number().int().min(-2_147_483_648).max(2_147_483_647),
    algoVersion: z.literal(1),
  })
  .strict()

export const galaxyRoutes = new Hono()

// POST /galaxy/me — publica (upsert) el snapshot del planeta. Reglas:
//  - El planeta público nunca encoge: un totalXp menor que el guardado es un no-op.
//  - progressed_at solo avanza cuando totalXp CRECE: la galaxia lista mundos que crecieron,
//    no aperturas de app.
//  - Rate limit respaldado en DB: si la fila cambió hace <60s y el payload difiere → 429.
galaxyRoutes.post('/me', requireAuth, async (c) => {
  const me = c.get('user') as AuthUser
  const parsed = publishSchema.safeParse(await c.req.json().catch(() => null))
  if (!parsed.success) return c.json({ error: 'invalid_body' }, 400)
  const s = parsed.data
  const d = derive(s.totalXp)

  const [existing] = await db
    .select({
      totalXp: galaxyPlanets.totalXp,
      species: galaxyPlanets.species,
      seed: galaxyPlanets.seed,
      updatedAt: galaxyPlanets.updatedAt,
    })
    .from(galaxyPlanets)
    .where(eq(galaxyPlanets.userId, me.id))

  if (existing) {
    const identical =
      existing.totalXp === s.totalXp && existing.species === s.species && existing.seed === s.seed
    if (identical || s.totalXp < existing.totalXp) {
      const e = derive(existing.totalXp)
      return c.json({ ok: true, ...e })
    }
    if (Date.now() - existing.updatedAt.getTime() < 60_000) {
      return c.json({ error: 'too_many_requests' }, 429)
    }
  }

  // Upsert atómico y MONÓTONO: las guardas van dentro del propio UPDATE (CASE sobre la fila
  // real en conflicto), no sobre el SELECT previo — dos requests concurrentes del mismo
  // usuario no pueden encoger el snapshot ni bumpear progressed_at sin crecimiento real.
  const grows = sql`${s.totalXp} > ${galaxyPlanets.totalXp}`
  await db
    .insert(galaxyPlanets)
    .values({
      userId: me.id,
      totalXp: s.totalXp,
      planetIndex: d.planetIndex,
      xpInPlanet: d.xpInPlanet,
      stage: d.stage,
      species: s.species,
      seed: s.seed,
      algoVersion: s.algoVersion,
    })
    .onConflictDoUpdate({
      target: galaxyPlanets.userId,
      set: {
        totalXp: sql`CASE WHEN ${grows} THEN ${s.totalXp} ELSE ${galaxyPlanets.totalXp} END`,
        planetIndex: sql`CASE WHEN ${grows} THEN ${d.planetIndex} ELSE ${galaxyPlanets.planetIndex} END`,
        xpInPlanet: sql`CASE WHEN ${grows} THEN ${d.xpInPlanet} ELSE ${galaxyPlanets.xpInPlanet} END`,
        stage: sql`CASE WHEN ${grows} THEN ${d.stage} ELSE ${galaxyPlanets.stage} END`,
        species: sql`CASE WHEN ${grows} THEN ${s.species} ELSE ${galaxyPlanets.species} END`,
        seed: sql`CASE WHEN ${grows} THEN ${s.seed} ELSE ${galaxyPlanets.seed} END`,
        algoVersion: s.algoVersion,
        updatedAt: sql`now()`,
        progressedAt: sql`CASE WHEN ${grows} THEN now() ELSE ${galaxyPlanets.progressedAt} END`,
      },
    })
  // Eco de lo derivado: si el cliente calcula otra cosa con el mismo totalXp, hay drift de fórmula.
  return c.json({ ok: true, ...d })
})

// GET /galaxy — mundos que han crecido en los últimos 30 días, los más recientes primero.
// Con JWT: solo se ve desde la app con sesión (no scrapeable anónimamente).
galaxyRoutes.get('/', requireAuth, async (c) => {
  const rows = await db
    .select({
      username: users.username,
      displayName: users.displayName,
      seed: galaxyPlanets.seed,
      planetIndex: galaxyPlanets.planetIndex,
      stage: galaxyPlanets.stage,
      xpInPlanet: galaxyPlanets.xpInPlanet,
      totalXp: galaxyPlanets.totalXp,
      species: galaxyPlanets.species,
      progressedAt: galaxyPlanets.progressedAt,
    })
    .from(galaxyPlanets)
    .innerJoin(users, eq(users.id, galaxyPlanets.userId))
    .where(
      sql`${users.deletedAt} is null and ${galaxyPlanets.progressedAt} >= now() - interval '30 days'`
    )
    .orderBy(desc(galaxyPlanets.progressedAt))
    .limit(100)
  return c.json({ planets: rows })
})
