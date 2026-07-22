'use strict';

/**
 * Genera el par de claves con el que el servidor FIRMA los entitlements de premium.
 *
 *   node gen-keys.js
 *
 * Pega la primera salida en Railway como variable ENTITLEMENT_PRIVATE_KEY, y la segunda
 * (clave publica) en la app: constante SERVER_ENTITLEMENT_PUBLIC_KEY de Premium.kt.
 *
 * Guarda la privada en un sitio seguro: quien la tenga puede falsificar premium.
 */

const crypto = require('crypto');

const { publicKey, privateKey } = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });

const privPem = privateKey.export({ type: 'pkcs8', format: 'pem' });
const privB64 = Buffer.from(privPem, 'utf8').toString('base64');
const pubDerB64 = publicKey.export({ type: 'spki', format: 'der' }).toString('base64');

console.log('=== ENTITLEMENT_PRIVATE_KEY (variable de entorno en Railway) ===');
console.log(privB64);
console.log('\n=== Clave publica (incrustar en la app: Premium.SERVER_ENTITLEMENT_PUBLIC_KEY) ===');
console.log(pubDerB64);
