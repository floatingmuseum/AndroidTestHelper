package com.floatingmuseum.android.test.helper.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

interface SettingsRepository {
    fun loadSettings(): AppSettings
    fun saveSettings(settings: AppSettings)
}

expect fun createSettingsRepository(): SettingsRepository

object AppSettingsShared {
    private val repository: SettingsRepository by lazy { createSettingsRepository() }
    
    var currentSettings by mutableStateOf(AppSettings())
        private set
        
    fun init() {
        currentSettings = repository.loadSettings().normalized()
    }
    
    fun updateSettings(newSettings: AppSettings) {
        val normalizedSettings = newSettings.normalized()
        currentSettings = normalizedSettings
        repository.saveSettings(normalizedSettings)
    }
}
