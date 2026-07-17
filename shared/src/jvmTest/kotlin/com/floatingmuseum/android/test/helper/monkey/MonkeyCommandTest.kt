package com.floatingmuseum.android.test.helper.monkey

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonkeyCommandTest {
    @Test
    fun buildsWholeDeviceCommandWithoutPackageConstraint() {
        val command = buildMonkeyAdbCommand(
            deviceSerial = "device-1",
            form = MonkeyTestForm(
                targetScope = MonkeyTargetScope.WholeDevice,
                packages = listOf("com.example.ignored"),
                seed = "42",
            ),
        )

        assertEquals(
            listOf(
                "-s", "device-1", "shell", "monkey",
                "-s", "42", "--throttle", "100", "-v",
                "--monitor-native-crashes", "1000",
            ),
            command.args,
        )
        assertFalse(command.args.contains("-p"))
    }

    @Test
    fun buildsMultiPackageCommandWithCategoriesPercentagesAndPolicies() {
        val command = buildMonkeyAdbCommand(
            deviceSerial = "R58M123",
            form = MonkeyTestForm(
                packages = listOf("com.example.one", "com.example.two"),
                categories = listOf("android.intent.category.LAUNCHER", "android.intent.category.MONKEY"),
                eventCount = "5000",
                throttleMs = "250",
                seed = "99",
                verbosity = 2,
                eventPercentages = MonkeyEventPercentages(touch = "45", motion = "20", appSwitch = "10"),
                ignoreCrashes = true,
                ignoreTimeouts = true,
                ignoreSecurityExceptions = true,
                killProcessAfterError = true,
                monitorNativeCrashes = true,
            ),
        )

        assertEquals(
            listOf(
                "-s", "R58M123", "shell", "monkey",
                "-p", "com.example.one", "-p", "com.example.two",
                "-c", "android.intent.category.LAUNCHER",
                "-c", "android.intent.category.MONKEY",
                "-s", "99", "--throttle", "250", "-v", "-v",
                "--pct-touch", "45", "--pct-motion", "20", "--pct-appswitch", "10",
                "--ignore-crashes", "--ignore-timeouts", "--ignore-security-exceptions",
                "--kill-process-after-error", "--monitor-native-crashes", "5000",
            ),
            command.args,
        )
    }

    @Test
    fun validatesTargetNumbersDuplicatesAndPercentages() {
        val result = validateMonkeyForm(
            MonkeyTestForm(
                packages = listOf("bad-package!", "com.example.app", "COM.EXAMPLE.APP"),
                categories = listOf("android.intent.category.LAUNCHER", "android.intent.category.LAUNCHER"),
                eventCount = "0",
                throttleMs = "-1",
                seed = "not-a-number",
                eventPercentages = MonkeyEventPercentages(touch = "70", motion = "40", trackball = "101"),
            ),
        )

        val keys = result.errors.map { it.key }
        assertFalse(result.isValid)
        assertTrue("monkey.validation.invalid_package_arg0" in keys)
        assertTrue("monkey.validation.duplicate_package_arg0" in keys)
        assertTrue("monkey.validation.duplicate_category_arg0" in keys)
        assertTrue("monkey.validation.event_count" in keys)
        assertTrue("monkey.validation.throttle" in keys)
        assertTrue("monkey.validation.seed" in keys)
        assertTrue("monkey.validation.percentage_arg0" in keys)
        assertTrue("monkey.validation.percentage_total" in keys)
    }

    @Test
    fun parsesCompletionIncidentsAndDroppedEvents() {
        val parser = MonkeyReportSummaryBuilder(plannedEvents = 500)
        listOf(
            "// CRASH: com.example.app (pid 10)",
            "// NOT RESPONDING: com.example.app (pid 10)",
            "** New native crash detected.",
            ":Dropped: keys=1 pointers=2 trackballs=3 flips=4 rotations=5",
            "Events injected: 500",
            "// Monkey finished",
        ).forEach(parser::accept)

        val summary = parser.build()
        assertEquals(500, summary.injectedEvents)
        assertEquals(1, summary.crashCount)
        assertEquals(1, summary.anrCount)
        assertEquals(1, summary.nativeCrashCount)
        assertEquals(MonkeyDroppedEvents(1, 2, 3, 4, 5), summary.droppedEvents)
        assertEquals(
            MonkeyRunEndState.CompletedWithIncidents,
            resolveMonkeyRunEndState(0, stopRequested = false, cleanupFailed = false, summary),
        )
    }

    @Test
    fun resolvesAbortStopAndCleanupFailure() {
        val abortedParser = MonkeyReportSummaryBuilder(plannedEvents = 1000)
        abortedParser.accept("// CRASH: com.example.app (pid 10)")
        abortedParser.accept("** Monkey aborted due to error.")
        abortedParser.accept("Events injected: 22")
        val summary = abortedParser.build()

        assertEquals(
            MonkeyRunEndState.AbortedByIncident,
            resolveMonkeyRunEndState(22, stopRequested = false, cleanupFailed = false, summary),
        )
        assertEquals(
            MonkeyRunEndState.Stopped,
            resolveMonkeyRunEndState(null, stopRequested = true, cleanupFailed = false, summary),
        )
        assertEquals(
            MonkeyRunEndState.StopCleanupFailed,
            resolveMonkeyRunEndState(null, stopRequested = true, cleanupFailed = true, summary),
        )
    }

    @Test
    fun sanitizesReportFileName() {
        val fileName = buildMonkeyReportFileName(
            deviceModel = "Test/Tablet 12",
            form = MonkeyTestForm(packages = listOf("com.example:app"), seed = "42"),
            timestampToken = "20260717_120000",
        )

        assertEquals("monkey_Test_Tablet_12_com.example_app_20260717_120000.log", fileName)
    }
}
