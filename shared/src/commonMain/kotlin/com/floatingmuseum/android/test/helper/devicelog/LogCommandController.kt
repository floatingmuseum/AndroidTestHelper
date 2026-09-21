package com.floatingmuseum.android.test.helper.devicelog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.floatingmuseum.android.test.helper.localization.commandError
import com.floatingmuseum.android.test.helper.localization.localized

internal class LogCommandController(
    private val repository: LogCommandRepository,
    private val isCapturing: () -> Boolean,
    private val appendCommand: (String) -> Unit,
) {
    var configuration by mutableStateOf(repository.load().normalized())
        private set
    var managerOpen by mutableStateOf(false)
        private set
    var editingName by mutableStateOf<String?>(null)
        private set
    var editorName by mutableStateOf("")
        private set
    var editorCommand by mutableStateOf(NEW_LOGCAT_COMMAND)
        private set
    var nameError by mutableStateOf<String?>(null)
        private set
    var commandError by mutableStateOf<String?>(null)
        private set
    var persistenceError by mutableStateOf<String?>(null)
        private set

    fun openManager() {
        if (isCapturing()) return
        newCommand()
        managerOpen = true
    }

    fun closeManager() { managerOpen = false }

    fun newCommand() {
        if (isCapturing()) return
        editingName = null
        editorName = ""
        editorCommand = NEW_LOGCAT_COMMAND
        clearErrors()
    }

    fun edit(command: CustomLogCommand) {
        if (isCapturing() || command !in configuration.commands) return
        editingName = command.name
        editorName = command.name
        editorCommand = command.command
        clearErrors()
    }

    fun updateName(value: String) {
        if (isCapturing()) return
        editorName = value
        nameError = null
    }

    fun updateCommand(value: String) {
        if (isCapturing()) return
        editorCommand = value
        commandError = null
    }

    fun select(name: String?) {
        if (isCapturing() || (name != null && configuration.commands.none { it.name == name })) return
        persist(configuration.copy(selectedName = name))
    }

    fun saveEditor() {
        if (isCapturing()) return
        clearErrors()
        val name = editorName.trim()
        nameError = when {
            name.isEmpty() -> localized("log.command.name_required")
            configuration.commands.any { it.name != editingName && it.name.equals(name, ignoreCase = true) } ->
                localized("log.command.name_duplicate")
            else -> null
        }
        val text = editorCommand.trim()
        commandError = runCatching { parseCustomLogcatArguments(text) }.exceptionOrNull()?.message
        if (nameError != null || commandError != null) return
        val saved = CustomLogCommand(name, text)
        val commands = if (editingName == null) configuration.commands + saved else
            configuration.commands.map { if (it.name == editingName) saved else it }
        if (persist(LogCommandConfiguration(commands, name))) {
            newCommand()
            closeManager()
        }
    }

    fun delete(name: String) {
        if (isCapturing()) return
        val updated = configuration.copy(
            commands = configuration.commands.filterNot { it.name == name },
            selectedName = configuration.selectedName.takeUnless { it == name },
        )
        if (persist(updated) && editingName == name) newCommand()
    }

    private fun persist(configuration: LogCommandConfiguration): Boolean = try {
        repository.save(configuration)
        this.configuration = configuration
        persistenceError = null
        true
    } catch (error: Exception) {
        persistenceError = localized("log.command.save_failed") + ": " + error.message
        appendCommand(commandError(persistenceError.orEmpty()))
        false
    }

    private fun clearErrors() {
        nameError = null
        commandError = null
        persistenceError = null
    }
}
