package com.floatingmuseum.android.test.helper

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WirelessAdbDialogTest {
    @Test
    fun acceptsValidIpv4Addresses() {
        assertTrue(isValidWirelessIpAddress("192.168.1.23"))
        assertTrue(isValidWirelessIpAddress("0.0.0.0"))
        assertTrue(isValidWirelessIpAddress("255.255.255.255"))
    }

    @Test
    fun rejectsMalformedIpv4Addresses() {
        assertFalse(isValidWirelessIpAddress(""))
        assertFalse(isValidWirelessIpAddress("192.168.1"))
        assertFalse(isValidWirelessIpAddress("192.168.1.23.4"))
        assertFalse(isValidWirelessIpAddress("192.168..23"))
        assertFalse(isValidWirelessIpAddress("192.168.1.256"))
        assertFalse(isValidWirelessIpAddress("192.168.1.a"))
    }
}
