package com.shohan.khatiyan.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.shohan.khatiyan.core.BnDates

/**
 * Read-only date field backed by the Material date picker; the value is stored
 * app-wide as ISO "yyyy-MM-dd". Display label is Bangla (Phase 6).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    iso: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    allowClear: Boolean = false,
    onClear: (() -> Unit)? = null,
) {
    var showPicker by remember { mutableStateOf(false) }
    val date = remember(iso) { BnDates.fromIso(iso) }

    OutlinedTextField(
        value = date?.let { BnDates.formatLong(it) } ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text("তারিখ নির্বাচন করুন", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
        trailingIcon = {
            androidx.compose.foundation.layout.Row {
                if (allowClear && iso.isNotEmpty() && onClear != null) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Outlined.Close, contentDescription = "তারিখ সরান")
                    }
                }
                IconButton(onClick = { showPicker = true }) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = "তারিখ নির্বাচন")
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures { showPicker = true } },
        shape = MaterialTheme.shapes.medium,
    )

    if (showPicker) {
        val initialMillis = date?.toEpochDay()?.let { it * 86_400_000L }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            onSelected(java.time.LocalDate.ofEpochDay(millis / 86_400_000L).toString())
                        }
                        showPicker = false
                    },
                ) { Text("নিশ্চিত করুন") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("বাতিল") }
            },
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }
}
