package com.floatingmuseum.android.test.helper.intent

import com.floatingmuseum.android.test.helper.AppRuntimePaths
import com.floatingmuseum.android.test.helper.adb.AdbCommandException
import com.floatingmuseum.android.test.helper.adb.AdbShell
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class JvmIntentAdb : IntentAdb {
    override suspend fun executeIntentCommand(
        deviceSerial: String,
        form: IntentTestForm,
        logCommand: (String) -> Unit,
    ): IntentExecutionResult {
        val command = buildIntentAdbCommand(deviceSerial, form)
        return try {
            val output = AdbShell.executeAdb(
                args = command.args,
                displayCommand = command.displayCommand,
                logCommand = logCommand,
            )
            IntentExecutionResult(
                command = command,
                output = output,
                isSuccess = true,
            )
        } catch (error: AdbCommandException) {
            IntentExecutionResult(
                command = command,
                output = error.message.orEmpty(),
                isSuccess = false,
            )
        }
    }
}

class JvmIntentTemplateRepository : IntentTemplateRepository {
    private val templatesFile: File
        get() = AppRuntimePaths.cacheDirectory().resolve("intent_templates.json")

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    override fun loadTemplates(): List<IntentTemplate> {
        if (!templatesFile.exists()) return emptyList()
        return try {
            json.decodeFromString<IntentTemplateFile>(templatesFile.readText()).templates
        } catch (error: Exception) {
            emptyList()
        }
    }

    override fun saveTemplates(templates: List<IntentTemplate>) {
        templatesFile.parentFile?.mkdirs()
        templatesFile.writeText(json.encodeToString(IntentTemplateFile(templates)))
    }
}

@Serializable
private data class IntentTemplateFile(
    val templates: List<IntentTemplate> = emptyList(),
)

actual fun createIntentAdb(): IntentAdb = JvmIntentAdb()

actual fun createIntentTemplateRepository(): IntentTemplateRepository = JvmIntentTemplateRepository()
