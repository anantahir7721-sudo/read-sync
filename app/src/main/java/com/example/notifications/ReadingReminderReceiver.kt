package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AuthorizedReaders
import com.example.data.LocalPreferencesStore
import com.example.data.StreakCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReadingReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = LocalPreferencesStore(appContext)
                val snapshot = store.readReminderSnapshot()
                val todayKey = StreakCalculator.todayDateKey()
                val loggedToday = if (snapshot.todayDateKey == todayKey) snapshot.todayMinutes else 0L

                if (snapshot.reminderEnabled && loggedToday < AuthorizedReaders.DAILY_GOAL_MINUTES) {
                    ReadingReminderScheduler.postBehindOnGoalNotification(
                        context = appContext,
                        readerName = snapshot.readerName,
                        todayMinutes = loggedToday,
                        currentStreak = snapshot.currentStreak,
                        isTestPreview = false
                    )
                }

                // Re-arm alarm for tomorrow if reminders remain enabled
                if (snapshot.reminderEnabled) {
                    ReadingReminderScheduler.scheduleDailyReminder(
                        context = appContext,
                        enabled = true,
                        hour = snapshot.reminderHour,
                        minute = snapshot.reminderMinute
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
