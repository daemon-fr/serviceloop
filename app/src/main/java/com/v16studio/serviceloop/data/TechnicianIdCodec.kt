package com.v16studio.serviceloop.data

import java.security.MessageDigest
import java.security.SecureRandom

object TechnicianIdCodec {
    private const val PREFIX = "SLT"
    private const val PAYLOAD_LENGTH = 12
    private const val CHECKSUM_LENGTH = 2
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val canonical = Regex("^SLT-([0-9A-HJKMNP-TV-Z]{4})-([0-9A-HJKMNP-TV-Z]{4})-([0-9A-HJKMNP-TV-Z]{4})-([0-9A-HJKMNP-TV-Z]{2})$")
    private val uuidLegacy = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
    private val hexLegacy = Regex("^[0-9a-fA-F]{32}$")

    fun generate(random: SecureRandom = SecureRandom()): String {
        val payload = buildString(PAYLOAD_LENGTH) {
            repeat(PAYLOAD_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
        return format(payload, checksum(payload))
    }

    fun normalize(value: String): String? {
        val compact = value.trim().uppercase().replace("-", "")
        if (compact.length != PREFIX.length + PAYLOAD_LENGTH + CHECKSUM_LENGTH || !compact.startsWith(PREFIX)) return null
        val payload = compact.substring(PREFIX.length, PREFIX.length + PAYLOAD_LENGTH)
        val check = compact.takeLast(CHECKSUM_LENGTH)
        if (payload.any { it !in ALPHABET } || check.any { it !in ALPHABET } || checksum(payload) != check) return null
        return format(payload, check)
    }

    fun isValidCanonical(value: String) = normalize(value) != null
    fun isSupportedLegacy(value: String) = uuidLegacy.matches(value) || hexLegacy.matches(value)
    fun display(value: String) = normalize(value) ?: value

    internal fun checksum(payload: String): String {
        require(payload.length == PAYLOAD_LENGTH && payload.all { it in ALPHABET })
        val digest = MessageDigest.getInstance("SHA-256").digest("SLT:$payload".toByteArray(Charsets.US_ASCII))
        val tenBits = ((digest[0].toInt() and 0xff) shl 2) or ((digest[1].toInt() and 0xc0) ushr 6)
        return "${ALPHABET[(tenBits ushr 5) and 31]}${ALPHABET[tenBits and 31]}"
    }

    private fun format(payload: String, check: String) =
        "$PREFIX-${payload.substring(0, 4)}-${payload.substring(4, 8)}-${payload.substring(8, 12)}-$check"
}
