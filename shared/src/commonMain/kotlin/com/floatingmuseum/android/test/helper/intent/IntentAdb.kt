package com.floatingmuseum.android.test.helper.intent

interface IntentAdb {
    suspend fun executeIntentCommand(
        deviceSerial: String,
        form: IntentTestForm,
        logCommand: (String) -> Unit,
    ): IntentExecutionResult
}

interface IntentTemplateRepository {
    fun loadTemplates(): List<IntentTemplate>

    fun saveTemplates(templates: List<IntentTemplate>)
}

expect fun createIntentAdb(): IntentAdb

expect fun createIntentTemplateRepository(): IntentTemplateRepository
