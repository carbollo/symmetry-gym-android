'use strict';

/**
 * Servidor de recompensas premium con verificacion por servidor (SSV) de AdMob.
 *
 * Modelo "server-authoritative": el premium vive en ESTE servidor, no en el movil. Un anuncio
 * verificado por AdMob acredita 24 h de premium al dispositivo; el movil solo consulta su estado
 * (firmado) y pide el contenido premium, que se sirve solo si el servidor ve premium activo.
 *
 * Endpoints:
 *   GET  /admob/ssv       -> lo llaman los servidores de AdMob; verifica la firma y acredita premium.
 *   GET  /reward/status   -> la app: { verified } para un nonce (confirmar que ESE anuncio conto).
 *   POST /premium/claim   -> la app (autenticada por dispositivo): entitlement { premiumUntil } FIRMADO.
 *   POST /premium/content -> la app (autenticada + con premium activo): contenido premium.
 *   GET  /health          -> salud.
 *
 * Docs SSV: https://developers.google.com/admob/android/rewarded-video-ssv
 */

const express = require('express');
const store = require('./store');
const keys = require('./keys');
const { contentToVerify, verifySignature } = require('./verify');
const { verifyDevice } = require('./deviceauth');

// Reglas de premium.
const PREMIUM_HOURS_PER_AD = 24;   // cada anuncio verificado
const PREMIUM_CAP_HOURS = 24 * 7;  // tope acumulable, para que nadie farmee premium infinito

// ID de TU unidad de anuncio recompensada (= RewardedAds.LIVE_UNIT). Las claves de verificacion de
// Google son GLOBALES (las mismas para todos los publishers), asi que sin esta comprobacion
// cualquiera podria apuntar la URL SSV de SU propia unidad a este servidor y acreditarse premium
// sin ver TUS anuncios. El ad_unit incluye el publisher id y es unico global: no se puede falsificar.
const EXPECTED_AD_UNIT = process.env.ADMOB_AD_UNIT || null;

const app = express();
app.set('trust proxy', true);
app.use(express.json({ limit: '16kb' }));

// Claves publicas con las que Google firma los callbacks SSV. Rotan: se cachean y se refrescan
// cuando aparece un key_id que no tenemos.
const VERIFIER_KEYS_URL = 'https://www.gstatic.com/admob/reward/verifier-keys.json';
const KEY_TTL_MS = 24 * 60 * 60 * 1000;
const REFETCH_THROTTLE_MS = 60 * 1000; // no rebuscar por un key_id desconocido mas de 1 vez/min
let keyCache = { fetchedAt: 0, keys: {} };
let lastFetchAttempt = 0;
let inflightFetch = null;

async function fetchKeys() {
  const res = await fetch(VERIFIER_KEYS_URL);
  if (!res.ok) throw new Error(`verifier-keys HTTP ${res.status}`);
  const json = await res.json();
  const map = {};
  for (const k of json.keys || []) map[String(k.keyId)] = k.pem;
  keyCache = { fetchedAt: Date.now(), keys: map };
}

async function getPublicKey(keyId) {
  const now = Date.now();
  if (keyCache.keys[keyId] && now - keyCache.fetchedAt < KEY_TTL_MS) return keyCache.keys[keyId];
  // Cache miss (o TTL vencido). Refrescar con DOS defensas contra un flood de key_id inventados:
  //  - throttle: como mucho un fetch a gstatic por minuto ante misses;
  //  - coalescencia: N peticiones concurrentes comparten un unico fetch en vuelo.
  if (!inflightFetch && now - lastFetchAttempt >= REFETCH_THROTTLE_MS) {
    lastFetchAttempt = now;
    inflightFetch = fetchKeys().finally(() => { inflightFetch = null; });
  }
  if (inflightFetch) {
    try { await inflightFetch; } catch (_e) { /* si gstatic falla, devolvemos null abajo */ }
  }
  return keyCache.keys[keyId] || null;
}

// ---- SSV: lo llama AdMob cuando alguien completa un anuncio recompensado ----
app.get('/admob/ssv', async (req, res) => {
  try {
    // signature y key_id deben ser strings unicos. Si se duplican, Express los parsea como array;
    // rechazarlo evita comportamientos raros en la verificacion.
    const signature = req.query.signature;
    const keyId = req.query.key_id;
    if (typeof signature !== 'string' || typeof keyId !== 'string') {
      return res.status(400).send('missing/duplicate signature or key_id');
    }

    const content = contentToVerify(req.originalUrl);
    if (!content) return res.status(400).send('bad query');

    const pem = await getPublicKey(keyId);
    if (!pem) return res.status(400).send('unknown key_id');

    if (!verifySignature(content, signature, pem)) return res.status(403).send('invalid signature');

    // CRITICO: leer los datos de la recompensa del MISMO contenido que se firmo, NO de req.query.
    // req.query incluye parametros anadidos DESPUES de "&signature=" (fuera de la firma) y los
    // duplicados como arrays: usarlos permitiria "parameter pollution" y romper la idempotencia.
    // URLSearchParams.get() devuelve siempre el primer valor como string.
    const p = new URLSearchParams(content);

    // Vincular el callback a TU unidad: sin esto se acepta cualquier callback firmado por Google.
    if (EXPECTED_AD_UNIT && p.get('ad_unit') !== EXPECTED_AD_UNIT) {
      return res.status(403).send('unexpected ad_unit');
    }

    await store.grantForVerifiedAd({
      nonce: p.get('custom_data'),
      transactionId: p.get('transaction_id'),
      deviceId: p.get('user_id'),
      hoursToAdd: PREMIUM_HOURS_PER_AD,
      capHours: PREMIUM_CAP_HOURS,
    });
    return res.status(200).send('ok');
  } catch (err) {
    console.error('SSV error:', err);
    return res.status(500).send('error'); // 500 -> Google reintentara
  }
});

// ---- La app confirma que ESE anuncio (nonce) fue verificado ----
app.get('/reward/status', async (req, res) => {
  const nonce = req.query.nonce;
  if (!nonce) return res.status(400).json({ error: 'missing nonce' });
  try {
    return res.json({ verified: await store.isVerified(String(nonce)) });
  } catch (err) {
    console.error('status error:', err);
    return res.status(500).json({ error: 'error' });
  }
});

// ---- La app pide su entitlement de premium, FIRMADO por el servidor ----
app.post('/premium/claim', async (req, res) => {
  try {
    const auth = verifyDevice(req.body || {});
    if (!auth.ok) return res.status(401).json({ error: 'device auth failed' });
    const premiumUntil = await store.getPremiumUntil(auth.deviceId);
    const issuedAt = Date.now();
    const signature = keys.signEntitlement(auth.deviceId, premiumUntil, issuedAt);
    return res.json({ deviceId: auth.deviceId, premiumUntil, issuedAt, signature });
  } catch (err) {
    console.error('claim error:', err);
    return res.status(500).json({ error: 'error' });
  }
});

// ---- Contenido premium: se sirve SOLO si el servidor ve premium activo (totalmente blindado) ----
app.post('/premium/content', async (req, res) => {
  try {
    const auth = verifyDevice(req.body || {});
    if (!auth.ok) return res.status(401).json({ error: 'device auth failed' });
    const premiumUntil = await store.getPremiumUntil(auth.deviceId);
    if (premiumUntil <= Date.now()) return res.status(402).json({ error: 'premium required' });

    // TODO(contenido): sustituir por el contenido premium real (rutinas/planes). Como el acceso
    // se decide AQUI y el payload solo existe en el servidor, un APK recompilado no puede fabricarlo.
    return res.json({
      premiumUntil,
      content: {
        routines: [
          { id: 'pro-ppl-6', title: 'Push/Pull/Legs Pro (6 dias)', weeks: 8 },
          { id: 'pro-ul-4', title: 'Upper/Lower Elite (4 dias)', weeks: 6 },
        ],
      },
    });
  } catch (err) {
    console.error('content error:', err);
    return res.status(500).json({ error: 'error' });
  }
});

app.get('/health', (_req, res) => res.json({ ok: true }));

// Middleware de error: cualquier throw imprevisto en un handler devuelve 500 en vez de propagarse.
// eslint-disable-next-line no-unused-vars
app.use((err, _req, res, _next) => {
  console.error('unhandled route error:', err);
  if (!res.headersSent) res.status(500).send('error');
});

// Red de seguridad: que un rechazo/excepcion imprevisto NO tumbe el proceso (DoS). Node >=18 mata
// el proceso ante un unhandledRejection por defecto; aqui solo lo registramos.
process.on('unhandledRejection', (e) => console.error('unhandledRejection:', e));
process.on('uncaughtException', (e) => console.error('uncaughtException:', e));

const port = process.env.PORT || 3000;
keys.init();
if (!EXPECTED_AD_UNIT) {
  console.warn('AVISO: ADMOB_AD_UNIT sin definir -> /admob/ssv acepta cualquier unidad de anuncio. ' +
    'Define ADMOB_AD_UNIT con el ID de tu unidad recompensada para blindar el crediting.');
}
store.init()
  .then(() => app.listen(port, () => console.log(`Premium/SSV server escuchando en :${port}`)))
  .catch((err) => {
    console.error('No se pudo iniciar el store:', err);
    process.exit(1);
  });
