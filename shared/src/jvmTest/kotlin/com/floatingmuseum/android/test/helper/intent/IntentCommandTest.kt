package com.floatingmuseum.android.test.helper.intent

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntentCommandTest {
    @Test
    fun buildsStartCommandWithDeepLinkExtrasAndFlags() {
        val command = buildIntentAdbCommand(
            deviceSerial = "R58M123ABC",
            form = IntentTestForm(
                mode = IntentCommandMode.Start,
                packageName = "com.example.app",
                className = ".DeepLinkActivity",
                action = "android.intent.action.VIEW",
                dataUri = "demo://detail/42",
                categories = listOf("android.intent.category.BROWSABLE"),
                extras = listOf(
                    IntentExtra("source", IntentExtraType.StringValue, "qa"),
                    IntentExtra("count", IntentExtraType.IntValue, "3"),
                    IntentExtra("enabled", IntentExtraType.BooleanValue, "TRUE"),
                ),
                startFlags = setOf(IntentStartFlag.NewTask, IntentStartFlag.ClearTop),
            ),
        )

        assertEquals(
            listOf(
                "-s",
                "R58M123ABC",
                "shell",
                "am",
                "start",
                "-n",
                "com.example.app/.DeepLinkActivity",
                "-a",
                "android.intent.action.VIEW",
                "-d",
                "demo://detail/42",
                "-c",
                "android.intent.category.BROWSABLE",
                "--activity-new-task",
                "--activity-clear-top",
                "--es",
                "source",
                "qa",
                "--ei",
                "count",
                "3",
                "--ez",
                "enabled",
                "true",
            ),
            command.args,
        )
        assertEquals(
            "adb -s R58M123ABC shell am start -n com.example.app/.DeepLinkActivity -a android.intent.action.VIEW -d demo://detail/42 -c android.intent.category.BROWSABLE --activity-new-task --activity-clear-top --es source qa --ei count 3 --ez enabled true",
            command.displayCommand,
        )
    }

    @Test
    fun buildsExplicitBroadcastCommandWithStringArray() {
        val command = buildIntentAdbCommand(
            deviceSerial = "R58M123ABC",
            form = IntentTestForm(
                mode = IntentCommandMode.Broadcast,
                packageName = "com.example.app",
                className = "com.example.app.SyncReceiver",
                action = "com.example.SYNC",
                extras = listOf(
                    IntentExtra("ids", IntentExtraType.StringArray, "a, b, c"),
                    IntentExtra("timestamp", IntentExtraType.LongValue, "123456789"),
                ),
                broadcastFlags = setOf(IntentBroadcastFlag.ReceiverForeground),
            ),
        )

        assertEquals(
            listOf(
                "-s",
                "R58M123ABC",
                "shell",
                "am",
                "broadcast",
                "-n",
                "com.example.app/com.example.app.SyncReceiver",
                "-a",
                "com.example.SYNC",
                "--receiver-foreground",
                "--esa",
                "ids",
                "a,b,c",
                "--el",
                "timestamp",
                "123456789",
            ),
            command.args,
        )
    }

    @Test
    fun validationRejectsMissingTargetAndInvalidExtraValues() {
        val result = validateIntentForm(
            IntentTestForm(
                action = "",
                extras = listOf(
                    IntentExtra("", IntentExtraType.StringValue, "value"),
                    IntentExtra("count", IntentExtraType.IntValue, "abc"),
                    IntentExtra("enabled", IntentExtraType.BooleanValue, "yes"),
                ),
            ),
        )

        assertFalse(result.isValid)
        assertTrue("intent.validation.target_required" in result.errors)
        assertTrue("intent.validation.extra_key_required:1" in result.errors)
        assertTrue("intent.validation.extra_int_required:2" in result.errors)
        assertTrue("intent.validation.extra_boolean_required:3" in result.errors)
    }

    @Test
    fun templateSerializationDoesNotRequireDeviceSerial() {
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
        val template = IntentTemplate(
            id = "intent-1",
            name = "Open detail",
            form = IntentTestForm(
                dataUri = "demo://detail/42",
                extras = listOf(IntentExtra("source", IntentExtraType.StringValue, "qa")),
            ),
        )

        val encoded = json.encodeToString(template)
        val decoded = json.decodeFromString<IntentTemplate>(encoded)

        assertEquals(template, decoded)
        assertFalse(encoded.contains("serial", ignoreCase = true))
    }
}
