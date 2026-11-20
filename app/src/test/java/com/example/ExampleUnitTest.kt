package com.example

import com.example.data.AuthorizedReaders
import com.example.data.ReaderProfile
import com.example.data.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `only the two registered mobile numbers are accepted`() {
        val narsinh = AuthorizedReaders.findByMobileNumber("7203070830")
        assertNotNull(narsinh)
        assertEquals("Narsinh Chad", narsinh?.displayName)

        val vishwaraj = AuthorizedReaders.findByMobileNumber("+91 7096295484")
        assertNotNull(vishwaraj)
        assertEquals("Vishwaraj Jadeja", vishwaraj?.displayName)

        assertNull(AuthorizedReaders.findByMobileNumber("9999999999"))
        assertNull(AuthorizedReaders.findByMobileNumber("1234567890"))
    }

    @Test
    fun `streak counts consecutive 30-min days and breaks when a day is missed`() {
        val now = System.currentTimeMillis()
        val today = StreakCalculator.todayDateKey(now)
        val yesterday = StreakCalculator.dateKeyOffsetFromToday(-1, now)
        val twoDaysAgo = StreakCalculator.dateKeyOffsetFromToday(-2, now)

        // Cumulative 15m + 15m = 30m today plus yesterday & two days ago => 3-day streak
        val profile3Days = ReaderProfile(
            readerId = AuthorizedReaders.NARSINH.readerId,
            displayName = AuthorizedReaders.NARSINH.displayName,
            dailyTotals = mapOf(
                twoDaysAgo to 35L,
                yesterday to 30L,
                today to 30L
            )
        )
        val stats3 = StreakCalculator.computeStats(AuthorizedReaders.NARSINH, profile3Days, nowMillis = now)
        assertEquals(3L, stats3.currentStreak)
        assertTrue(stats3.goalMetToday)
        assertFalse(stats3.streakAtRisk)
        assertEquals(95L, stats3.weeklyMinutes)

        // Today only 15m so far, yesterday 30m => streak still alive (1 day) and marked At Risk
        val profileAtRisk = ReaderProfile(
            readerId = AuthorizedReaders.VISHWARAJ.readerId,
            displayName = AuthorizedReaders.VISHWARAJ.displayName,
            dailyTotals = mapOf(
                yesterday to 40L,
                today to 15L
            )
        )
        val statsAtRisk = StreakCalculator.computeStats(AuthorizedReaders.VISHWARAJ, profileAtRisk, nowMillis = now)
        assertEquals(1L, statsAtRisk.currentStreak)
        assertFalse(statsAtRisk.goalMetToday)
        assertTrue(statsAtRisk.streakAtRisk)
        assertEquals(15L, statsAtRisk.remainingMinutesToday)

        // Yesterday missed (<30m), today <30m => streak breaks (0)
        val profileBroken = ReaderProfile(
            readerId = AuthorizedReaders.NARSINH.readerId,
            displayName = AuthorizedReaders.NARSINH.displayName,
            dailyTotals = mapOf(
                twoDaysAgo to 45L,
                yesterday to 20L,
                today to 10L
            )
        )
        val statsBroken = StreakCalculator.computeStats(AuthorizedReaders.NARSINH, profileBroken, nowMillis = now)
        assertEquals(0L, statsBroken.currentStreak)
        assertFalse(statsBroken.streakAtRisk)
    }
}
