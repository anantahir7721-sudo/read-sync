package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AuthorizedReaders
import com.example.data.ComputedReaderStats
import com.example.data.ReadingSession
import com.example.data.StreakCalculator
import com.example.ui.theme.MonoMetricsFamily
import com.example.viewmodel.ReadingUiState

@Composable
fun WeeklyCompareScreen(
    state: ReadingUiState.Ready
) {
    val narsinhStats = if (state.activeReader.readerId == AuthorizedReaders.NARSINH.readerId) {
        state.myStats
    } else {
        state.partnerStats
    }
    val vishwarajStats = if (state.activeReader.readerId == AuthorizedReaders.VISHWARAJ.readerId) {
        state.myStats
    } else {
        state.partnerStats
    }

    val narsinhColor = MaterialTheme.colorScheme.primary
    val vishwarajColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = 104.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "weekly_title") {
                Column {
                    Text(
                        text = "SHARED READING CIRCLE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Weekly Comparison",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Side-by-side reader stat cards
            item(key = "duo_cards") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DuoScoreCard(
                        stats = narsinhStats,
                        accentColor = narsinhColor,
                        isLeading = narsinhStats.weeklyMinutes > vishwarajStats.weeklyMinutes,
                        isCurrentUser = state.activeReader.readerId == narsinhStats.reader.readerId,
                        modifier = Modifier.weight(1f)
                    )
                    DuoScoreCard(
                        stats = vishwarajStats,
                        accentColor = vishwarajColor,
                        isLeading = vishwarajStats.weeklyMinutes > narsinhStats.weeklyMinutes,
                        isCurrentUser = state.activeReader.readerId == vishwarajStats.reader.readerId,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 7-Day Grouped Bar Chart with 30-min Goal Line
            item(key = "weekly_chart") {
                SevenDayGroupedChartCard(
                    narsinhStats = narsinhStats,
                    vishwarajStats = vishwarajStats,
                    narsinhColor = narsinhColor,
                    vishwarajColor = vishwarajColor
                )
            }

            // 7-Day Daily Breakdown Table
            item(key = "daily_table") {
                DailyBreakdownCard(
                    narsinhStats = narsinhStats,
                    vishwarajStats = vishwarajStats,
                    narsinhColor = narsinhColor,
                    vishwarajColor = vishwarajColor
                )
            }

            // Recent Circle Sessions Feed
            item(key = "recent_feed_header") {
                Text(
                    text = "Recent Circle Activity",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            val recentSessions = state.allSessions.take(20)
            if (recentSessions.isEmpty()) {
                item(key = "empty_activity") {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No circle sessions logged yet. Start reading to populate your shared timeline!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                items(
                    items = recentSessions,
                    key = { it.id.ifBlank { "${it.readerId}_${it.dateKey}_${it.loggedTimeLabel}_${it.minutes}" } }
                ) { session ->
                    CircleActivityRow(
                        session = session,
                        accentColor = if (session.readerId == AuthorizedReaders.NARSINH.readerId) {
                            narsinhColor
                        } else {
                            vishwarajColor
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DuoScoreCard(
    stats: ComputedReaderStats,
    accentColor: Color,
    isLeading: Boolean,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = if (isLeading) 1.5.dp else 1.dp,
            color = if (isLeading) accentColor else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stats.reader.initials,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
                if (isLeading) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = "Weekly leader",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column {
                Text(
                    text = stats.reader.shortName + if (isCurrentUser) " (You)" else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${stats.weeklyGoalDays}/7 days hit 30m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${stats.currentStreak} day streak",
                    fontFamily = MonoMetricsFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = StreakCalculator.formatDuration(stats.weeklyMinutes),
                        fontFamily = MonoMetricsFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = accentColor
                    )
                    Text(
                        text = "Last 7 days • Best ${stats.longestStreak}d",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SevenDayGroupedChartCard(
    narsinhStats: ComputedReaderStats,
    vishwarajStats: ComputedReaderStats,
    narsinhColor: Color,
    vishwarajColor: Color
) {
    val maxDayMinutes = maxOf(
        AuthorizedReaders.DAILY_GOAL_MINUTES.toLong(),
        narsinhStats.weeklyDays.maxOfOrNull { it.minutes } ?: 0L,
        vishwarajStats.weeklyDays.maxOfOrNull { it.minutes } ?: 0L
    ).coerceAtLeast(45L)

    val gridLineColor = MaterialTheme.colorScheme.outlineVariant
    val goalLineColor = MaterialTheme.colorScheme.tertiary

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Last 7 Days",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Dashed line marks 30-min streak minimum",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Legend
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChartLegendItem(label = "Narsinh", color = narsinhColor)
                ChartLegendItem(label = "Vishwaraj", color = vishwarajColor)
                ChartLegendItem(label = "30m Goal", color = goalLineColor)
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
            ) {
                val daysCount = narsinhStats.weeklyDays.size.coerceAtLeast(1)
                val chartHeight = size.height - 8.dp.toPx()
                val slotWidth = size.width / daysCount
                val barGap = 4.dp.toPx()
                val barWidth = ((slotWidth - 18.dp.toPx()) / 2f).coerceIn(6.dp.toPx(), 18.dp.toPx())

                // Baseline
                drawLine(
                    color = gridLineColor,
                    start = Offset(0f, chartHeight),
                    end = Offset(size.width, chartHeight),
                    strokeWidth = 1.dp.toPx()
                )

                // 30-minute threshold dashed line
                val goalRatio = AuthorizedReaders.DAILY_GOAL_MINUTES.toFloat() / maxDayMinutes.toFloat()
                val goalY = chartHeight * (1f - goalRatio)
                drawLine(
                    color = goalLineColor,
                    start = Offset(0f, goalY),
                    end = Offset(size.width, goalY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )

                // Grouped bars per day
                for (i in 0 until daysCount) {
                    val centerX = slotWidth * i + slotWidth / 2f
                    val nMins = narsinhStats.weeklyDays.getOrNull(i)?.minutes ?: 0L
                    val vMins = vishwarajStats.weeklyDays.getOrNull(i)?.minutes ?: 0L

                    val nHeight = ((nMins.toFloat() / maxDayMinutes.toFloat()) * chartHeight)
                        .coerceAtLeast(if (nMins > 0L) 4.dp.toPx() else 2.dp.toPx())
                    val vHeight = ((vMins.toFloat() / maxDayMinutes.toFloat()) * chartHeight)
                        .coerceAtLeast(if (vMins > 0L) 4.dp.toPx() else 2.dp.toPx())

                    drawRoundRect(
                        color = if (nMins > 0L) narsinhColor else gridLineColor.copy(alpha = 0.5f),
                        topLeft = Offset(centerX - barWidth - barGap / 2f, chartHeight - nHeight),
                        size = Size(barWidth, nHeight),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    drawRoundRect(
                        color = if (vMins > 0L) vishwarajColor else gridLineColor.copy(alpha = 0.5f),
                        topLeft = Offset(centerX + barGap / 2f, chartHeight - vHeight),
                        size = Size(barWidth, vHeight),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
            }

            // Weekday labels under bars
            Row(modifier = Modifier.fillMaxWidth()) {
                narsinhStats.weeklyDays.forEach { day ->
                    Text(
                        text = if (day.isToday) "Today" else day.shortDayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (day.isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartLegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DailyBreakdownCard(
    narsinhStats: ComputedReaderStats,
    vishwarajStats: ComputedReaderStats,
    narsinhColor: Color,
    vishwarajColor: Color
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Day",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.1f)
                )
                Text(
                    text = "Narsinh",
                    style = MaterialTheme.typography.labelSmall,
                    color = narsinhColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Vishwaraj",
                    style = MaterialTheme.typography.labelSmall,
                    color = vishwarajColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }

            val reversedIndices = narsinhStats.weeklyDays.indices.reversed()
            reversedIndices.forEach { idx ->
                val nDay = narsinhStats.weeklyDays[idx]
                val vDay = vishwarajStats.weeklyDays[idx]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (nDay.isToday) "Today (${nDay.shortDayLabel})" else "${nDay.shortDayLabel} ${nDay.dayOfMonthLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (nDay.isToday) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1.1f)
                    )
                    Text(
                        text = "${nDay.minutes}m" + if (nDay.metGoal) " ✓" else "",
                        fontFamily = MonoMetricsFamily,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (nDay.metGoal) FontWeight.Bold else FontWeight.Normal,
                        color = if (nDay.metGoal) narsinhColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${vDay.minutes}m" + if (vDay.metGoal) " ✓" else "",
                        fontFamily = MonoMetricsFamily,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (vDay.metGoal) FontWeight.Bold else FontWeight.Normal,
                        color = if (vDay.metGoal) vishwarajColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CircleActivityRow(
    session: ReadingSession,
    accentColor: Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.readerName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val details = buildString {
                    append(session.dateKey)
                    if (session.loggedTimeLabel.isNotBlank()) {
                        append(" • ")
                        append(session.loggedTimeLabel)
                    }
                    if (!session.bookTitle.isNullOrBlank()) {
                        append(" • ")
                        append(session.bookTitle)
                    }
                }
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "+${session.minutes}m",
                fontFamily = MonoMetricsFamily,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                color = accentColor
            )
        }
    }
}
