package com.aesthetic.gym.premium

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Identidad de este dispositivo, respaldada por hardware.
 *
 * En el primer arranque se genera un par de claves EC P-256 dentro del **Android Keystore**: la
 * clave privada NO es extraible (ni con el movil rooteado), así que nadie puede clonar esta
 * identidad ni reclamar su premium en otro dispositivo.
 *
 * El [deviceId] es la huella de la clave publica; el servidor lo recalcula igual y comprueba, en
 * cada peticion, que quien llama posee la privada (firmando un reto). Sin cuentas ni contrasenas.
 */
object DeviceIdentity {

    private const val ALIAS = "zenit_device_key"
    private const val KEYSTORE = "AndroidKeyStore"

    // La clave publica en DER no cambia: se cachea tras el primer acceso (el Keystore es lento).
    @Volatile private var cachedPubDer: ByteArray? = null
    @Volatile private var cachedDeviceId: String? = null

    /** Clave publica (X.509/SPKI, DER) en base64 estandar. La app la envia al servidor. */
    fun publicKeyDerBase64(): String =
        Base64.encodeToString(publicKeyDer(), Base64.NO_WRAP)

    /** deviceId = base64url(sha256(clave publica DER)). Coincide con el calculo del servidor. */
    fun deviceId(): String = cachedDeviceId ?: run {
        val hash = MessageDigest.getInstance("SHA-256").digest(publicKeyDer())
        val id = Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        cachedDeviceId = id
        id
    }

    /** Firma [data] con la clave privada del Keystore (SHA256withECDSA), en base64 estandar. */
    fun sign(data: String): String {
        val privateKey = (keyStore().getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry).privateKey
        val sig = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
            update(data.toByteArray(Charsets.UTF_8))
        }.sign()
        return Base64.encodeToString(sig, Base64.NO_WRAP)
    }

    /** Fuerza la creacion de la clave en el primer arranque (llamar en un hilo de fondo). */
    fun ensureCreated() {
        publicKeyDer()
    }

    private fun publicKeyDer(): ByteArray = cachedPubDer ?: run {
        val cert = ensureKeyPair()
        val der = cert.encoded
        cachedPubDer = der
        der
    }

    /** Crea el par si no existe y devuelve la clave publica (como certificado autofirmado). */
    private fun ensureKeyPair(): java.security.PublicKey {
        val ks = keyStore()
        if (!ks.containsAlias(ALIAS)) {
            val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).apply {
                initialize(spec)
                generateKeyPair()
            }
        }
        return (ks.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry).certificate.publicKey
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
}
