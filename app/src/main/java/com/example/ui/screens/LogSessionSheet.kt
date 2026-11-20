package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.AuthorizedReaders
import com.example.ui.theme.MonoMetricsFamily

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogSessionSheet(
    readerShortName: String,
    currentTodayMinutes: Long,
    defaultBookTitle: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirmLog: (minutes: Int, bookTitle: String?, note: String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val presetMinutes = listOf(10, 15, 20, 30, 45, 60)

    var minutesText by remember { mutableStateOf("15") }
    var bookTitle by remember { mutableStateOf(defaultBookTitle) }
    var note by remember { mutableStateOf("") }

    val parsedMinutes = minutesText.toIntOrNull() ?: 0
    val isMinutesValid = parsedMinutes in 1..720
    val projectedTotal = currentTodayMinutes + parsedMinutes
    val goal = AuthorizedReaders.DAILY_GOAL_MINUTES.toLong()
    val reachesGoal = projectedTotal >= goal

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = "Log Reading Session",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Sessions stack throughout the day toward $readerShortName's 30-min streak.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Quick preset chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetMinutes.forEach { preset ->
                    val selected = parsedMinutes == preset
                    FilterChip(
                        selected = selected,
                        onClick = { minutesText = preset.toString() },
                        label = {
                            Text(
                                text = "+$preset min",
                                fontFamily = MonoMetricsFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("preset_${preset}_min_chip")
                    )
                }
            }

            OutlinedTextField(
                value = minutesText,
                onValueChange = { next ->
                    minutesText = next.filter { it.isDigit() }.take(3)
                },
                label = { Text("Minutes read") },
                suffix = { Text("min") },
                leadingIcon = {
                    Icon(Icons.Filled.Schedule, contentDescription = null)
                },
                singleLine = true,
                isError = minutesText.isNotEmpty() && !isMinutesValid,
                supportingText = {
                    if (minutesText.isNotEmpty() && !isMinutesValid) {
                        Text("Enter a duration between 1 and 720 minutes.")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("session_minutes_input")
            )

            OutlinedTextField(
                value = bookTitle,
                onValueChange = { bookTitle = it.take(100) },
                label = { Text("Book or article (optional)") },
                placeholder = { Text("e.g. Atomic Habits") },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("session_book_input")
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(200) },
                label = { Text("Quick note (optional)") },
                placeholder = { Text("e.g. Finished Chapter 4") },
                leadingIcon = {
                    Icon(Icons.Filled.Notes, contentDescription = null)
                },
                maxLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("session_note_input")
            )

            // Live cumulative daily preview pill
            if (isMinutesValid) {
                Surface(
                    color = if (reachesGoal) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (reachesGoal) Icons.Filled.CheckCircle else Icons.Filled.LocalFireDepartment,
                            contentDescription = null,
                            tint = if (reachesGoal) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        val previewText = if (reachesGoal) {
                            "Today: ${currentTodayMinutes}m + ${parsedMinutes}m = ${projectedTotal}m • Daily 30m goal met!"
                        } else {
                            val left = (goal - projectedTotal).coerceAtLeast(0L)
                            "Today: ${currentTodayMinutes}m + ${parsedMinutes}m = ${projectedTotal}m • ${left}m left for streak"
                        }
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (reachesGoal) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (isMinutesValid) {
                        onConfirmLog(parsedMinutes, bookTitle, note)
                        onDismiss()
                    }
                },
                enabled = isMinutesValid && !isSaving,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("confirm_log_session_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = if (isMinutesValid) "Add $parsedMinutes min to Today" else "Enter valid minutes",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
