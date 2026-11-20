package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.pageStreakDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pagestreak_local_prefs"
)

data class ReminderLocalSnapshot(
    val readerName: String,
    val todayDateKey: String,
    val todayMinutes: Long,
    val currentStreak: Long,
    val reminderEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int
)

class LocalPreferencesStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACTIVE_READER_NAME = stringPreferencesKey("active_reader_name")
        val TODAY_DATE_KEY = stringPreferencesKey("today_date_key")
        val TODAY_MINUTES = longPreferencesKey("today_minutes")
        val CURRENT_STREAK = longPreferencesKey("current_streak")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    }

    val themeModeFlow: Flow<AppThemeMode> = context.pageStreakDataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE] ?: AppThemeMode.SYSTEM.name
        AppThemeMode.entries.firstOrNull { it.name == raw } ?: AppThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.pageStreakDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun syncReminderSnapshot(
        readerName: String,
        todayDateKey: String,
        todayMinutes: Long,
        currentStreak: Long,
        reminderEnabled: Boolean,
        reminderHour: Int,
        reminderMinute: Int
    ) {
        context.pageStreakDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_READER_NAME] = readerName
            prefs[Keys.TODAY_DATE_KEY] = todayDateKey
            prefs[Keys.TODAY_MINUTES] = todayMinutes
            prefs[Keys.CURRENT_STREAK] = currentStreak
            prefs[Keys.REMINDER_ENABLED] = reminderEnabled
            prefs[Keys.REMINDER_HOUR] = reminderHour
            prefs[Keys.REMINDER_MINUTE] = reminderMinute
        }
    }

    suspend fun readReminderSnapshot(): ReminderLocalSnapshot {
        val prefs = context.pageStreakDataStore.data.first()
        return ReminderLocalSnapshot(
            readerName = prefs[Keys.ACTIVE_READER_NAME] ?: "Reader",
            todayDateKey = prefs[Keys.TODAY_DATE_KEY] ?: "",
            todayMinutes = prefs[Keys.TODAY_MINUTES] ?: 0L,
            currentStreak = prefs[Keys.CURRENT_STREAK] ?: 0L,
            reminderEnabled = prefs[Keys.REMINDER_ENABLED] ?: true,
            reminderHour = prefs[Keys.REMINDER_HOUR] ?: 20,
            reminderMinute = prefs[Keys.REMINDER_MINUTE] ?: 0
        )
    }
}
