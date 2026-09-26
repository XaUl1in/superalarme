package br.com.alarmehardcore

import org.junit.Assert.*
import org.junit.Test

class ManualEscapeSyncTest {
    @Test fun anyZoneBlocksEscapeNotJustFirstOne() {
        assertTrue(AlarmRules.inAnySafeZone(listOf(900f, 149f, 300f)))
        assertFalse(AlarmRules.inAnySafeZone(listOf(150f, 900f)))
        assertFalse(AlarmRules.inAnySafeZone(emptyList()))
    }
    @Test fun syncAcceptsOnlyLiteralPrivateIpv4() {
        listOf("10.0.2.2", "172.16.1.1", "172.31.255.254", "192.168.1.20").forEach { assertTrue(SyncProtocol.isLocalAddress(it)) }
        listOf("8.8.8.8", "172.32.0.1", "localhost", "192.168.1.300", "010.0.0.1", "192.168.1.1:80", "https://example.com").forEach {
            assertFalse(SyncProtocol.isLocalAddress(it))
        }
    }
    @Test fun signatureMatchesPythonProtocolVector() {
        val body = """{"streak":3,"best":8,"revision":10,"sentAt":6000}""".toByteArray(Charsets.UTF_8)
        val key = ByteArray(32) { 0xab.toByte() }
        assertEquals("e816e8e7383be527d65ff43a5f4be3bbc6d1cc96de9c671915c29f3db221c6c5", SyncProtocol.signature(key, body))
        assertNotEquals(SyncProtocol.signature(key, body), SyncProtocol.signature(key, body + " ".toByteArray()))
    }
}
