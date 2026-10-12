package com.geotagcamera.geotagginglocationonphoto.security

import java.security.MessageDigest
import java.util.Base64

/**
 * A short, human-comparable fingerprint of a signing public key: the first 8
 * bytes of SHA-256 over the key's encoded form, as four groups of four hex
 * digits ("3F2A 91C0 7B44 E1D8"). Two photos showing the same fingerprint were
 * signed by the same key; a verifier can read it out over the phone. Pure JVM.
 */
object KeyFingerprint {
    fun of(publicKeyBase64: String): String? {
        val bytes = runCatching { Base64.getDecoder().decode(publicKeyBase64.trim()) }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.take(8)
            .joinToString("") { "%02X".format(it) }
            .chunked(4)
            .joinToString(" ")
    }
}
