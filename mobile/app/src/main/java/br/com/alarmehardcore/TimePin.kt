package br.com.alarmehardcore

import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TimePin {
    fun decode(value: String): ByteArray {
        val cleaned = value.filterNot { it.isWhitespace() }
        require(cleaned.matches(Regex("[0-9a-fA-F]{64}"))) { "Digite a chave de 64 caracteres do computador." }
        return cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    fun generate(secret: ByteArray, seconds: Long, period: Long = 60, digits: Int = 6): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(seconds / period).array())
        val offset = hash.last().toInt() and 15
        val binary = ByteBuffer.wrap(hash, offset, 4).int and 0x7fffffff
        val modulus = if (digits == 8) 100_000_000 else 1_000_000
        return (binary % modulus).toString().padStart(digits, '0')
    }

    fun validate(secret: ByteArray, code: String, seconds: Long): Boolean {
        if (!code.matches(Regex("[0-9]{6}"))) return false
        return (-1L..1L).any { delta ->
            MessageDigest.isEqual(generate(secret, seconds + delta * 60).toByteArray(), code.toByteArray())
        }
    }
}
