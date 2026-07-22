'use strict';

/**
 * Almacen del servidor: anuncios verificados (nonces) + premium por dispositivo.
 *
 * - Con DATABASE_URL (Postgres de Railway) -> persistente. RECOMENDADO en produccion.
 * - Sin DATABASE_URL -> en memoria (se pierde al reiniciar). Comodo solo para desarrollo.
 *
 * El premium vive AQUI, en el servidor (no en el movil): es el "dueno" de la verdad. Un anuncio
 * verificado por SSV suma horas al deviceId; el movil solo consulta y muestra lo que diga el server.
 */

const usePg = !!process.env.DATABASE_URL;
const MEM_TTL_MS = 60 * 60 * 1000;
const HOUR_MS = 60 * 60 * 1000;

// --- almacen en memoria (fallback de desarrollo) ---
const seenNonces = new Map();  // nonce -> ts (para /reward/status y dedup)
const premiumMem = new Map();  // deviceId -> premiumUntil (epoch ms)

function cleanupMem() {
  const now = Date.now();
  for (const [nonce, ts] of seenNonces) {
    if (now - ts > MEM_TTL_MS) seenNonces.delete(nonce);
  }
  // El premium NO se limpia por TTL: caduca solo por su propio premiumUntil.
}

// --- Postgres ---
let pool = null;

async function init() {
  if (!usePg) {
    console.log('store: EN MEMORIA (define DATABASE_URL para persistir con Postgres).');
    const timer = setInterval(cleanupMem, 10 * 60 * 1000);
    if (typeof timer.unref === 'function') timer.unref();
    return;
  }
  const { Pool } = require('pg');
  pool = new Pool({
    connectionString: process.env.DATABASE_URL,
    ssl: process.env.PGSSL === 'require' ? { rejectUnauthorized: false } : false,
  });
  await pool.query(`
    CREATE TABLE IF NOT EXISTS rewards (
      nonce          TEXT PRIMARY KEY,
      transaction_id TEXT,
      user_id        TEXT,
      verified_at    TIMESTAMPTZ NOT NULL DEFAULT now()
    )
  `);
  await pool.query(`
    CREATE TABLE IF NOT EXISTS premium (
      device_id     TEXT PRIMARY KEY,
      premium_until BIGINT NOT NULL
    )
  `);
  console.log('store: Postgres listo.');
}

/**
 * Registra un anuncio verificado y, si es NUEVO (no un reintento de Google), acredita premium al
 * dispositivo. Idempotente: el mismo nonce nunca acredita dos veces.
 *
 * Regla de acumulacion: premiumUntil = min(ahora + tope, max(ahora, actual) + horas). Asi, ver un
 * anuncio con premium activo lo EXTIENDE, pero con un tope para que nadie farmee premium infinito.
 *
 * @returns {{ credited: boolean, premiumUntil?: number }}
 */
async function grantForVerifiedAd({ nonce, transactionId, deviceId, hoursToAdd, capHours }) {
  if (!nonce) return { credited: false };
  // Defensa en profundidad: forzar strings para que la dedup nunca dependa de identidad de objeto
  // (un array como clave en el Map "en memoria" nunca coincidiria y re-acreditaria en cada replay).
  nonce = String(nonce);
  if (deviceId != null) deviceId = String(deviceId);
  const now = Date.now();
  const addMs = hoursToAdd * HOUR_MS;
  const capMs = capHours * HOUR_MS;

  if (usePg) {
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      const ins = await client.query(
        `INSERT INTO rewards (nonce, transaction_id, user_id) VALUES ($1, $2, $3)
         ON CONFLICT (nonce) DO NOTHING`,
        [nonce, transactionId, deviceId]
      );
      let premiumUntil;
      if (ins.rowCount > 0 && deviceId) {
        const sel = await client.query(
          'SELECT premium_until FROM premium WHERE device_id = $1 FOR UPDATE',
          [deviceId]
        );
        const current = sel.rowCount ? Number(sel.rows[0].premium_until) : 0;
        premiumUntil = Math.min(now + capMs, Math.max(now, current) + addMs);
        await client.query(
          `INSERT INTO premium (device_id, premium_until) VALUES ($1, $2)
           ON CONFLICT (device_id) DO UPDATE SET premium_until = EXCLUDED.premium_until`,
          [deviceId, premiumUntil]
        );
      }
      await client.query('COMMIT');
      return { credited: ins.rowCount > 0, premiumUntil };
    } catch (e) {
      await client.query('ROLLBACK');
      throw e;
    } finally {
      client.release();
    }
  }

  // Memoria
  if (seenNonces.has(nonce)) return { credited: false };
  seenNonces.set(nonce, now);
  if (!deviceId) return { credited: true };
  const current = premiumMem.get(deviceId) || 0;
  const premiumUntil = Math.min(now + capMs, Math.max(now, current) + addMs);
  premiumMem.set(deviceId, premiumUntil);
  return { credited: true, premiumUntil };
}

/** ¿ese nonce ya fue verificado por AdMob? (para /reward/status). */
async function isVerified(nonce) {
  if (usePg) {
    const r = await pool.query('SELECT 1 FROM rewards WHERE nonce = $1 LIMIT 1', [nonce]);
    return r.rowCount > 0;
  }
  return seenNonces.has(nonce);
}

/** premiumUntil (epoch ms) del dispositivo, o 0 si nunca gano premium. */
async function getPremiumUntil(deviceId) {
  if (usePg) {
    const r = await pool.query('SELECT premium_until FROM premium WHERE device_id = $1', [deviceId]);
    return r.rowCount ? Number(r.rows[0].premium_until) : 0;
  }
  return premiumMem.get(deviceId) || 0;
}

module.exports = { init, grantForVerifiedAd, isVerified, getPremiumUntil };
