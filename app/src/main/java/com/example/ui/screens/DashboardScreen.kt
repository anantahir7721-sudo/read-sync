package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AuthorizedReaders
import com.example.data.ComputedReaderStats
import com.example.data.ReadingSession
import com.example.data.StreakCalculator
import com.example.ui.theme.MonoMetricsFamily
import com.example.viewmodel.ReadingUiState
import com.example.viewmodel.TimerState
import java.util.Calendar
import java.util.Locale

@Composable
fun DashboardScreen(
    state: ReadingUiState.Ready,
    currentUserId: String,
    timerState: TimerState,
    onQuickLog: (Int) -> Unit,
    onOpenLogSheet: () -> Unit,
    onDeleteSession: (ReadingSession) -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onSaveTimerSession: () -> Unit,
    onViewPartnerWeekly: () -> Unit
) {
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 4..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

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
            // 1. Editorial Greeting & Reader Pill
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$greeting,",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = state.activeReader.displayName,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    StreakFlamePill(
                        streakDays = state.myStats.currentStreak,
                        goalMetToday = state.myStats.goalMetToday
                    )
                }
            }

            // 2. Hero Daily Progress Ring Card
            item(key = "hero_ring") {
                HeroDailyGoalCard(
                    stats = state.myStats,
                    onQuickLog = onQuickLog,
                    onOpenLogSheet = onOpenLogSheet,
                    isSaving = state.isSavingSession
                )
            }

            // 3. Built-in Live Reading Timer Card
            item(key = "reading_timer") {
                LiveReadingTimerCard(
                    timerState = timerState,
                    isSaving = state.isSavingSession,
                    onToggle = onToggleTimer,
                    onReset = onResetTimer,
                    onSave = onSaveTimerSession
                )
            }

            // 4. Partner Live Streak Card
            item(key = "partner_card") {
                PartnerLiveCard(
                    partnerStats = state.partnerStats,
                    partnerTodayCount = state.partnerTodaySessions.size,
                    onViewWeekly = onViewPartnerWeekly
                )
            }

            // 5. Today's Sessions Header
            item(key = "sessions_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Today's Reading Log",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${state.myTodaySessions.size} session${if (state.myTodaySessions.size == 1) "" else "s"} • ${state.myStats.todayMinutes} min total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = onOpenLogSheet,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("today_add_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.cd_add_session),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }

            if (state.myTodaySessions.isEmpty()) {
                item(key = "empty_today") {
                    EmptyTodaySessionsCard(onOpenLogSheet = onOpenLogSheet)
                }
            } else {
                items(
                    items = state.myTodaySessions,
                    key = { it.id.ifBlank { "${it.loggedTimeLabel}_${it.minutes}" } }
                ) { session ->
                    TodaySessionRowCard(
                        session = session,
                        canDelete = session.authorUid == currentUserId && session.id.isNotBlank(),
                        onDelete = { onDeleteSession(session) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StreakFlamePill(
    streakDays: Long,
    goalMetToday: Boolean
) {
    val container = if (goalMetToday) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val content = if (goalMetToday) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = stringResource(R.string.cd_streak_flame),
                tint = content,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$streakDays d",
                fontFamily = MonoMetricsFamily,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                color = content
            )
        }
    }
}

@Composable
private fun HeroDailyGoalCard(
    stats: ComputedReaderStats,
    onQuickLog: (Int) -> Unit,
    onOpenLogSheet: () -> Unit,
    isSaving: Boolean
) {
    val goal = AuthorizedReaders.DAILY_GOAL_MINUTES.toFloat()
    val rawProgress = (stats.todayMinutes.toFloat() / goal).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 220f),
        label = "goal_progress_arc"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val activeArcColor = if (stats.goalMetToday) secondaryColor else primaryColor

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            activeArcColor.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                )
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status badge
            val statusText = when {
                stats.goalMetToday -> "Daily 30m Goal Met • Streak Safe"
                stats.streakAtRisk -> "Streak at Risk • ${stats.remainingMinutesToday}m left before midnight"
                stats.todayMinutes > 0L -> "${stats.remainingMinutesToday} min left to hit today's 30m goal"
                else -> "Log 30 min today to ignite your streak"
            }
            val badgeContainer = when {
                stats.goalMetToday -> MaterialTheme.colorScheme.secondaryContainer
                stats.streakAtRisk -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            }
            val badgeContent = when {
                stats.goalMetToday -> MaterialTheme.colorScheme.onSecondaryContainer
                stats.streakAtRisk -> MaterialTheme.colorScheme.onTertiaryContainer
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            }
            Surface(
                color = badgeContainer,
                shape = RoundedCornerShape(999.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            stats.goalMetToday -> Icons.Filled.CheckCircle
                            stats.streakAtRisk -> Icons.Filled.WarningAmber
                            else -> Icons.Filled.LocalFireDepartment
                        },
                        contentDescription = null,
                        tint = badgeContent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeContent
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            // Circular Ring Progress Gauge
            Box(
                modifier = Modifier.size(192.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 16.dp.toPx()
                    val inset = strokeWidth / 2f
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(inset, inset)

                    drawArc(
                        color = trackColor,
                        startAngle = 140f,
                        sweepAngle = 260f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    if (animatedProgress > 0.005f) {
                        drawArc(
                            color = activeArcColor,
                            startAngle = 140f,
                            sweepAngle = 260f * animatedProgress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${stats.todayMinutes}",
                        fontFamily = MonoMetricsFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 46.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "of ${AuthorizedReaders.DAILY_GOAL_MINUTES} min today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 3 compact stat tiles inside hero
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroStatChip(
                    label = "Streak",
                    value = "${stats.currentStreak}d",
                    modifier = Modifier.weight(1f)
                )
                HeroStatChip(
                    label = "Best",
                    value = "${stats.longestStreak}d",
                    modifier = Modifier.weight(1f)
                )
                HeroStatChip(
                    label = "7-Day",
                    value = StreakCalculator.formatDuration(stats.weeklyMinutes),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(18.dp))

            // One-tap quick add row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(10, 15, 30).forEach { mins ->
                    FilledTonalButton(
                        onClick = { onQuickLog(mins) },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_add_${mins}_button")
                    ) {
                        Text(
                            text = "+${mins}m",
                            fontFamily = MonoMetricsFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                Button(
                    onClick = onOpenLogSheet,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1.25f)
                        .testTag("hero_custom_log_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Custom", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun HeroStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontFamily = MonoMetricsFamily,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LiveReadingTimerCard(
    timerState: TimerState,
    isSaving: Boolean,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit
) {
    val minutes = timerState.elapsedSeconds / 60
    val seconds = timerState.elapsedSeconds % 60
    val formatted = String.format(Locale.US, "%02d:%02d", minutes, seconds)

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (timerState.running) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = null,
                        tint = if (timerState.running) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (timerState.running) "Reading in progress" else "Live Reading Timer",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatted,
                        fontFamily = MonoMetricsFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (timerState.elapsedSeconds > 0L) {
                    IconButton(
                        onClick = onReset,
                        modifier = Modifier.testTag("timer_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reset timer"
                        )
                    }
                    FilledTonalButton(
                        onClick = onSave,
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("timer_save_button")
                    ) {
                        Text("Log", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Button(
                    onClick = onToggle,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (timerState.running) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    ),
                    modifier = Modifier.testTag("timer_toggle_button")
                ) {
                    Icon(
                        imageVector = if (timerState.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (timerState.running) "Pause timer" else "Start timer",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (timerState.running) "Pause" else "Start")
                }
            }
        }
    }
}

@Composable
private fun PartnerLiveCard(
    partnerStats: ComputedReaderStats,
    partnerTodayCount: Int,
    onViewWeekly: () -> Unit
) {
    val progress = (partnerStats.todayMinutes.toFloat() / AuthorizedReaders.DAILY_GOAL_MINUTES.toFloat())
        .coerceIn(0f, 1f)

    Card(
        onClick = onViewWeekly,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("partner_status_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = partnerStats.reader.initials,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "READING PARTNER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = partnerStats.reader.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                StreakFlamePill(
                    streakDays = partnerStats.currentStreak,
                    goalMetToday = partnerStats.goalMetToday
                )
            }

            // Partner daily progress bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Today: ${partnerStats.todayMinutes}/${AuthorizedReaders.DAILY_GOAL_MINUTES} min",
                        fontFamily = MonoMetricsFamily,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            partnerStats.goalMetToday -> "Goal complete!"
                            partnerStats.todayMinutes > 0L -> "${partnerStats.remainingMinutesToday}m left"
                            else -> "Not logged yet"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (partnerStats.goalMetToday) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = if (partnerStats.goalMetToday) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "This week: ${StreakCalculator.formatDuration(partnerStats.weeklyMinutes)} (${partnerStats.weeklyGoalDays}/7 days)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (partnerStats.lastBookTitle.isNotBlank()) {
                    Text(
                        text = partnerStats.lastBookTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 150.dp)
                    )
                } else if (partnerTodayCount > 0) {
                    Text(
                        text = "$partnerTodayCount session${if (partnerTodayCount == 1) "" else "s"} today",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTodaySessionsCard(
    onOpenLogSheet: () -> Unit
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "No sessions logged today yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Read 15 min now and 15 min later — PageStreak sums every session automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = onOpenLogSheet,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("empty_log_first_session_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Log First Session")
            }
        }
    }
}

@Composable
private fun TodaySessionRowCard(
    session: ReadingSession,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "+${session.minutes}m",
                    fontFamily = MonoMetricsFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.bookTitle?.takeIf { it.isNotBlank() } ?: "Reading Session",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = buildString {
                    append(session.loggedTimeLabel.ifBlank { "Today" })
                    if (!session.note.isNullOrBlank()) {
                        append(" • ")
                        append(session.note)
                    }
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (canDelete) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_session_${session.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_delete_session),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
