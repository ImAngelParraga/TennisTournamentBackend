package bros.parraga.services

import java.security.SecureRandom

object InviteCodes {
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val LENGTH = 8
    private val random = SecureRandom()

    fun generate(): String = buildString(LENGTH) {
        repeat(LENGTH) {
            append(ALPHABET[random.nextInt(ALPHABET.length)])
        }
    }
}

