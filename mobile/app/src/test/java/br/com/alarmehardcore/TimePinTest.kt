package br.com.alarmehardcore

import org.junit.Assert.*
import org.junit.Test

class TimePinTest {
    private val key = "12345678901234567890".toByteArray()

    @Test fun rfc6238Vectors() {
        val vectors = mapOf(59L to "94287082", 1111111109L to "07081804",
            1111111111L to "14050471", 1234567890L to "89005924",
            2000000000L to "69279037", 20000000000L to "65353130")
        vectors.forEach { (time, pin) -> assertEquals(pin, TimePin.generate(key, time, 30, 8)) }
    }

    @Test fun productionWindowMatchesPython() {
        assertEquals("287082", TimePin.generate(key, 119))
        val pin = TimePin.generate(key, 6000)
        assertTrue(TimePin.validate(key, pin, 5940))
        assertTrue(TimePin.validate(key, pin, 6000))
        assertTrue(TimePin.validate(key, pin, 6060))
        assertFalse(TimePin.validate(key, pin, 6120))
        assertFalse(TimePin.validate(key, "12345", 6000))
    }
}
