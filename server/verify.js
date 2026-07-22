'use strict';

/**
 * Logica PURA de verificacion de la firma SSV de AdMob (sin HTTP, para poder testearla).
 *
 * Google firma cada callback con ECDSA (curva secp256r1) sobre SHA-256. El contenido firmado es
 * la cadena de consulta cruda que hay ANTES de "&signature="; signature y key_id son siempre los
 * dos ultimos parametros, en ese orden. La firma llega en base64 URL-safe, DER por dentro.
 */

const crypto = require('crypto');

/**
 * Extrae el contenido firmado de la URL cruda (req.originalUrl). Hay que usar la cruda, no
 * re-serializar los parametros, o los bytes no coincidiran con lo que firmo Google.
 * @returns {string|null} el trozo antes de "&signature=", o null si la URL no tiene el formato.
 */
function contentToVerify(originalUrl) {
  const qIndex = originalUrl.indexOf('?');
  if (qIndex < 0) return null;
  const query = originalUrl.substring(qIndex + 1);
  const sigIndex = query.indexOf('&signature=');
  if (sigIndex < 0) return null;
  return query.substring(0, sigIndex);
}

/**
 * @param {string} content  cadena devuelta por contentToVerify.
 * @param {string} signatureB64Url  el parametro signature tal cual (base64 URL-safe).
 * @param {string} pem  clave publica de Google en PEM.
 * @returns {boolean} true solo si la firma es autentica.
 */
function verifySignature(content, signatureB64Url, pem) {
  const verifier = crypto.createVerify('sha256');
  verifier.update(content, 'utf8');
  verifier.end();
  return verifier.verify(pem, Buffer.from(signatureB64Url, 'base64url'));
}

module.exports = { contentToVerify, verifySignature };
