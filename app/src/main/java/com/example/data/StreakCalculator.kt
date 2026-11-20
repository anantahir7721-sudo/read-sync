package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DayReadingSummary(
    val dateKey: String,
    val shortDayLabel: String,
    val dayOfMonthLabel: String,
    val minutes: Long,
    val metGoal: Boolean,
    val isToday: Boolean
)

data class ComputedReaderStats(
    val reader: AuthorizedReader,
    val todayDateKey: String,
    val todayMinutes: Long,
    val remainingMinutesToday: Long,
    val goalMetToday: Boolean,
    val streakAtRisk: Boolean,
    val currentStreak: Long,
    val longestStreak: Long,
    val weeklyMinutes: Long,
    val weeklyGoalDays: Int,
    val totalMinutes: Long,
    val lastBookTitle: String,
    val weeklyDays: List<DayReadingSummary>
)

object StreakCalculator {
    private const val DATE_PATTERN = "yyyy-MM-dd"
    private const val TIME_PATTERN = "h:mm a"
    private const val SHORT_DAY_PATTERN = "EEE"
    private const val DAY_NUM_PATTERN = "d"

    fun todayDateKey(nowMillis: Long = System.currentTimeMillis()): String {
        return SimpleDateFormat(DATE_PATTERN, Locale.US).format(Date(nowMillis))
    }

    fun currentTimeLabel(nowMillis: Long = System.currentTimeMillis()): String {
        return SimpleDateFormat(TIME_PATTERN, Locale.US).format(Date(nowMillis))
    }

    fun dateKeyOffsetFromToday(daysOffset: Int, nowMillis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, daysOffset)
        }
        return SimpleDateFormat(DATE_PATTERN, Locale.US).format(cal.time)
    }

    fun pruneDailyTotals(
        dailyTotals: Map<String, Long>,
        maxEntries: Int = 85
    ): Map<String, Long> {
        val cleaned = dailyTotals.filterValues { it > 0L }
        if (cleaned.size <= maxEntries) return cleaned
        return cleaned.entries
            .sortedByDescending { it.key }
            .take(maxEntries)
            .associate { it.key to it.value }
    }

    fun computeStats(
        reader: AuthorizedReader,
        profile: ReaderProfile?,
        goalMinutes: Int = AuthorizedReaders.DAILY_GOAL_MINUTES,
        nowMillis: Long = System.currentTimeMillis()
    ): ComputedReaderStats {
        val dailyTotals = profile?.dailyTotals ?: emptyMap()
        val todayKey = todayDateKey(nowMillis)
        val yesterdayKey = dateKeyOffsetFromToday(-1, nowMillis)

        val todayMinutes = (dailyTotals[todayKey] ?: 0L).coerceAtLeast(0L)
        val yesterdayMinutes = (dailyTotals[yesterdayKey] ?: 0L).coerceAtLeast(0L)
        val goalLong = goalMinutes.toLong()
        val goalMetToday = todayMinutes >= goalLong
        val remainingToday = (goalLong - todayMinutes).coerceAtLeast(0L)

        // Streak rule:
        // - Must read >= 30 min per day.
        // - If today >= 30 min, count consecutive qualifying days ending today.
        // - If today < 30 min, the day is still in progress: if yesterday >= 30 min,
        //   the streak is alive ("At Risk") counting backwards from yesterday;
        //   otherwise yesterday was missed so the streak is broken (0).
        val currentStreak = when {
            goalMetToday -> countConsecutiveDaysEndingAt(dailyTotals, 0, goalLong, nowMillis)
            yesterdayMinutes >= goalLong -> countConsecutiveDaysEndingAt(dailyTotals, -1, goalLong, nowMillis)
            else -> 0L
        }

        val streakAtRisk = !goalMetToday && yesterdayMinutes >= goalLong && currentStreak > 0L

        val computedLongest = computeLongestRun(dailyTotals, goalLong)
        val storedLongest = profile?.longestStreak ?: 0L
        val longestStreak = maxOf(storedLongest, computedLongest, currentStreak)

        // Rolling 7-day breakdown (oldest -> today)
        val dayFormat = SimpleDateFormat(SHORT_DAY_PATTERN, Locale.US)
        val dayNumFormat = SimpleDateFormat(DAY_NUM_PATTERN, Locale.US)
        val dateFormat = SimpleDateFormat(DATE_PATTERN, Locale.US)

        val weeklyDays = (-6..0).map { offset ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val key = dateFormat.format(cal.time)
            val mins = (dailyTotals[key] ?: 0L).coerceAtLeast(0L)
            DayReadingSummary(
                dateKey = key,
                shortDayLabel = dayFormat.format(cal.time),
                dayOfMonthLabel = dayNumFormat.format(cal.time),
                minutes = mins,
                metGoal = mins >= goalLong,
                isToday = offset == 0
            )
        }

        val weeklyMinutes = weeklyDays.sumOf { it.minutes }
        val weeklyGoalDays = weeklyDays.count { it.metGoal }
        val mapSum = dailyTotals.values.sum().coerceAtLeast(0L)
        val totalMinutes = maxOf(profile?.totalMinutes ?: 0L, mapSum)

        return ComputedReaderStats(
            reader = reader,
            todayDateKey = todayKey,
            todayMinutes = todayMinutes,
            remainingMinutesToday = remainingToday,
            goalMetToday = goalMetToday,
            streakAtRisk = streakAtRisk,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            weeklyMinutes = weeklyMinutes,
            weeklyGoalDays = weeklyGoalDays,
            totalMinutes = totalMinutes,
            lastBookTitle = profile?.lastBookTitle.orEmpty(),
            weeklyDays = weeklyDays
        )
    }

    private fun countConsecutiveDaysEndingAt(
        dailyTotals: Map<String, Long>,
        startOffset: Int,
        goalMinutes: Long,
        nowMillis: Long
    ): Long {
        var streak = 0L
        var offset = startOffset
        // Walk backwards up to 400 days
        while (offset >= startOffset - 400) {
            val key = dateKeyOffsetFromToday(offset, nowMillis)
            val mins = dailyTotals[key] ?: 0L
            if (mins >= goalMinutes) {
                streak++
                offset--
            } else {
                break
            }
        }
        return streak
    }

    private fun computeLongestRun(
        dailyTotals: Map<String, Long>,
        goalMinutes: Long
    ): Long {
        val qualifyingKeys = dailyTotals
            .filterValues { it >= goalMinutes }
            .keys
            .sorted()
        if (qualifyingKeys.isEmpty()) return 0L

        val parser = SimpleDateFormat(DATE_PATTERN, Locale.US)
        var best = 1L
        var current = 1L
        var prevDate = parser.parse(qualifyingKeys.first()) ?: return 1L

        for (i in 1 until qualifyingKeys.size) {
            val nextDate = parser.parse(qualifyingKeys[i]) ?: continue
            val prevCal = Calendar.getInstance().apply {
                time = prevDate
                add(Calendar.DAY_OF_YEAR, 1)
            }
            val expectedNextKey = parser.format(prevCal.time)
            if (qualifyingKeys[i] == expectedNextKey) {
                current++
                if (current > best) best = current
            } else {
                current = 1L
            }
            prevDate = nextDate
        }
        return best
    }

    fun formatDuration(minutes: Long): String {
        val safe = minutes.coerceAtLeast(0L)
        val hours = safe / 60
        val mins = safe % 60
        return when {
            hours == 0L -> "${mins}m"
            mins == 0L -> "${hours}h"
            else -> "${hours}h ${mins}m"
        }
    }

    fun formatClockTime(hour: Int, minute: Int): String {
        val amPm = if (hour >= 12) "PM" else "AM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format(Locale.US, "%d:%02d %s", displayHour, minute, amPm)
    }
}
