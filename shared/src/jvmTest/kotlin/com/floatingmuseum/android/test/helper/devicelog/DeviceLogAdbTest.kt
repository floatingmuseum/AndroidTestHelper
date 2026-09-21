package com.floatingmuseum.android.test.helper.devicelog

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceLogAdbTest {
    @Test
    fun continuousCommandUsesStudioTimePrecisionAndAllPriorities() {
        val command = buildLogcatAdbCommand("HA22CER8")
        assertEquals(
            listOf("-s", "HA22CER8", "shell", "logcat", "-b", "all", "-v", "threadtime", "-v", "year", "*:V"),
            command.args,
        )
        assertEquals("adb -s HA22CER8 shell logcat -b all -v threadtime -v year *:V", command.displayCommand)
    }

    @Test
    fun keywordsAreLiteralOrTermsAndIgnoreCaseByDefault() {
        val filter = LogKeywordFilter(" JPush | system.err | | JPush | CSDK ")
        assertEquals("JPush | system.err | CSDK", filter.normalized().query)
        val matcher = LogKeywordMatcher(filter)
        assertTrue(matcher.matches("tag=jpush message=hello"))
        assertTrue(matcher.matches("system.err: failure"))
        assertTrue(matcher.matches("CSDK: stop"))
        assertFalse(matcher.matches("systemXerr"))
        assertFalse(matcher.matches("unrelated"))
        assertTrue(LogKeywordMatcher(LogKeywordFilter(" | ")).matches("anything"))
    }

    @Test
    fun caseSensitiveKeywordsAndEmbeddedSpacesArePreserved() {
        val matcher = LogKeywordMatcher(LogKeywordFilter("ANR in | [error] | a.*", matchCase = true))
        assertTrue(matcher.matches("ANR in com.example"))
        assertFalse(matcher.matches("anr in com.example"))
        assertTrue(matcher.matches("[error]"))
        assertFalse(matcher.matches("error"))
        assertFalse(matcher.matches("abc"))
    }

    @Test
    fun studioFormattingRetainsMessageWhitespaceAndLongTags() {
        val raw = "2026-09-21 05:49:07.121  1105  1131 E UsbDeviceManager:   csdk updateState(): true"
        val entry = parseThreadtimeLogLine(raw)!!
        val formatted = entry.format("system_server")
        assertEquals("2026-09-21 05:49:07.121", entry.timestamp)
        assertEquals("  csdk updateState(): true", entry.message)
        assertTrue(formatted.contains("1105-1131"))
        assertTrue(formatted.indexOf("UsbDeviceManager") < formatted.indexOf("system_server"))
        assertTrue(formatted.endsWith("E    csdk updateState(): true"))
        val longTag = "vendor.lenovo.hardware.battery-service"
        assertTrue(parseThreadtimeLogLine(raw.replace("UsbDeviceManager", longTag))!!.format(null).contains(longTag))
        assertNull(parseThreadtimeLogLine("--------- beginning of main"))
        assertNull(parseThreadtimeLogLine("adb: device offline"))
    }

    @Test
    fun processNamesCanBeUsedAsKeywordsAndMissingNamesStayExplicit() {
        val entry = parseThreadtimeLogLine("2026-09-21 05:49:07.121 1105 1131 I Tag     : hello")!!
        assertTrue(LogKeywordMatcher(LogKeywordFilter("system_server")).matches(entry.format("system_server")))
        assertEquals("Tag", entry.tag)
        assertTrue(entry.format(null).contains("-                                I"))
        assertEquals(
            mapOf("1" to "init", "1105" to "system_server"),
            parseLogProcessNames(" PID NAME\n 1 init\n1105 system_server\nps: bad -o\n"),
        )
    }

    @Test
    fun historyMovesReusedQueriesToFrontAndCapsSize() {
        val old = (1..60).map { LogKeywordFilter("key$it") }
        val history = rememberLogFilter(old, LogKeywordFilter(" key4 "))
        assertEquals(LOG_FILTER_HISTORY_LIMIT, history.size)
        assertEquals("key4", history.first().query)
        assertEquals(1, history.count { it.query == "key4" })
        assertEquals(
            listOf(LogKeywordFilter("JPush"), LogKeywordFilter("jpush", true)),
            normalizeLogFilterHistory(listOf(LogKeywordFilter(" JPush "), LogKeywordFilter("jpush"), LogKeywordFilter("jpush", true), LogKeywordFilter(" | "))),
        )
    }

    @Test
    fun fileNamesAreSafeAndHaveMillisecondPrecision() {
        assertEquals(
            "LENOVO_TB373FU_HA22_CER8_20260921_054907_121.log",
            buildDeviceLogFileName("LENOVO TB373FU", "HA22:CER8", LocalDateTime.of(2026, 9, 21, 5, 49, 7, 121_000_000)),
        )
    }
}
