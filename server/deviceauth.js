'use strict';

/**
 * Autenticacion de dispositivo sin cuentas.
 *
 * El movil genera un par de claves en el Android Keystore (privada NO extraible). Su identidad
 * (deviceId) es la huella de su clave publica: base64url(sha256(publicKeyDER)). Para probar que
 * es el dueno, firma un reto "deviceId.timestamp" con su clave privada. Asi:
 *   - nadie puede reclamar el premium de un deviceId sin su clave privada (que no sale del movil);
 *   - el deviceId es autocertificante: no hay registro de claves que mantener.
 */

const crypto = require('crypto');

/** deviceId = base64url(sha256(clave publica en DER)). Debe coincidir con el calculo del cliente. */
function deviceIdFromDer(der) {
  return crypto.createHash('sha256').update(der).digest('base64url');
}

/**
 * Verifica que quien llama posee la clave privada del deviceId, con un reto reciente (anti-replay).
 * @returns {{ ok: boolean, deviceId?: string }}
 */
function verifyDevice({ publicKey, timestamp, signature }, maxSkewMs = 5 * 60 * 1000) {
  // Comprobacion de TIPOS estricta: sin esto, un publicKey no-string (p. ej. un numero en el JSON)
  // haria que Buffer.from lanzara un TypeError fuera del try del handler -> unhandledRejection ->
  // el proceso muere. Un atacante podria tumbar el servidor con una sola peticion sin autenticar.
  if (typeof publicKey !== 'string' || typeof signature !== 'string' ||
      (typeof timestamp !== 'string' && typeof timestamp !== 'number')) {
    return { ok: false };
  }

  let der;
  let pubKey;
  try {
    der = Buffer.from(publicKey, 'base64');
    pubKey = crypto.createPublicKey({ key: der, format: 'der', type: 'spki' });
  } catch (_e) {
    return { ok: false };
  }

  const deviceId = deviceIdFromDer(der);

  const ts = Number(timestamp);
  if (!Number.isFinite(ts) || Math.abs(Date.now() - ts) > maxSkewMs) return { ok: false, deviceId };

  const content = `${deviceId}.${ts}`;
  let ok = false;
  try {
    ok = crypto.verify('sha256', Buffer.from(content, 'utf8'), pubKey, Buffer.from(signature, 'base64'));
  } catch (_e) {
    ok = false;
  }
  return { ok, deviceId };
}

module.exports = { deviceIdFromDer, verifyDevice };
