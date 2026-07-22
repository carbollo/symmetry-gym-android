'use strict';

/**
 * Clave con la que el servidor FIRMA los entitlements de premium (ECDSA P-256 + SHA-256).
 * La privada vive en la env ENTITLEMENT_PRIVATE_KEY (PEM PKCS8 en base64). La publica
 * correspondiente va incrustada en la app, que rechaza cualquier entitlement no firmado por ella.
 */

const crypto = require('crypto');

let privateKey = null;
let publicKeyDerB64 = null;

function init() {
  const envB64 = process.env.ENTITLEMENT_PRIVATE_KEY;
  if (envB64) {
    const pem = Buffer.from(envB64, 'base64').toString('utf8');
    privateKey = crypto.createPrivateKey(pem);
  } else {
    // Dev: par EFIMERO. Al reiniciar cambia y caduca los tokens cacheados en los moviles.
    // Para produccion define ENTITLEMENT_PRIVATE_KEY (usa `node gen-keys.js`).
    privateKey = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' }).privateKey;
    console.warn('keys: ENTITLEMENT_PRIVATE_KEY sin definir -> par EFIMERO (solo desarrollo).');
  }
  publicKeyDerB64 = crypto.createPublicKey(privateKey)
    .export({ type: 'spki', format: 'der' })
    .toString('base64');
  console.log('keys: clave publica de entitlement (incrustar en la app si aun no lo hiciste):');
  console.log('  ' + publicKeyDerB64);
}

/** Firma "deviceId.premiumUntil.issuedAt" y devuelve la firma en base64 estandar (facil de
 *  decodificar con android.util.Base64.DEFAULT en el cliente). */
function signEntitlement(deviceId, premiumUntil, issuedAt) {
  const content = `${deviceId}.${premiumUntil}.${issuedAt}`;
  return crypto.sign('sha256', Buffer.from(content, 'utf8'), privateKey).toString('base64');
}

module.exports = { init, signEntitlement, publicKeyB64: () => publicKeyDerB64 };
