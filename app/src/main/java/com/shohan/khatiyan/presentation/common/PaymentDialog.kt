package com.shohan.khatiyan.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shohan.khatiyan.core.BnDates
import com.shohan.khatiyan.core.Money
import com.shohan.khatiyan.di.AppContainer
import com.shohan.khatiyan.domain.finance.OverpaymentException
import com.shohan.khatiyan.domain.model.PaymentMethod
import com.shohan.khatiyan.ui.components.AmountInput
import com.shohan.khatiyan.ui.components.ChoiceChipsRow
import com.shohan.khatiyan.ui.components.DateField
import com.shohan.khatiyan.ui.components.KhatiyanTextField
import com.shohan.khatiyan.ui.components.WarningBanner
import kotlinx.coroutines.launch

/**
 * Shared "record a payment" dialog for shop/loan/EMI/personal (Phases 10–13,
 * 18). The [submit] callback performs the real repository write and returns
 * the [OverpaymentException] if the policy blocked the amount — the dialog then
 * shows an explicit Bangla confirmation and re-submits with permission.
 */
@Composable
fun PaymentEntryDialog(
    visible: Boolean,
    title: String,
    symbol: String,
    onDismiss: () -> Unit,
    submit: suspend (amountPaisa: Long, methodName: String, dateIso: String, note: String, allowOver: Boolean) -> OverpaymentException?,
    initialNote: String = "",
) {
    if (!visible) return
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var dateIso by remember { mutableStateOf(BnDates.toIso(BnDates.today())) }
    var note by remember { mutableStateOf(initialNote) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingExcess by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun run(allow: Boolean) {
        val paisa = Money.parse(amount)
        if (paisa == null) {
            error = "টাকার সঠিক পরিমাণ লিখুন।"
            return
        }
        error = null
        busy = true
        scope.launch {
            val outcome = submit(paisa, method.name, dateIso, note, allow)
            busy = false
            if (outcome != null) {
                pendingExcess = outcome.excessPaisa
            } else {
                onDismiss()
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                AmountInput(
                    value = amount,
                    onValueChange = { amount = it; error = null; pendingExcess = null },
                    label = "টাকার পরিমাণ",
                    symbol = symbol,
                    error = error,
                )
                Spacer(Modifier.height(10.dp))
                ChoiceChipsRow(
                    options = PaymentMethod.entries.map { it.labelBn },
                    selected = method.labelBn,
                    onSelect = { label -> method = PaymentMethod.entries.first { it.labelBn == label } },
                )
                Spacer(Modifier.height(6.dp))
                DateField(label = "তারিখ", iso = dateIso, onSelected = { dateIso = it })
                Spacer(Modifier.height(6.dp))
                KhatiyanTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "নোট (ঐচ্ছিক)",
                    singleLine = false,
                    minLines = 1,
                )
                val excess = pendingExcess
                if (excess != null) {
                    Spacer(Modifier.height(10.dp))
                    WarningBanner(
                        "বাকির চেয়ে ${Money.format(excess, symbol)} বেশি দিচ্ছেন। রাজি থাকলে বাড়তি অংশ “অগ্রিম জমা” থাকবে — বকেয়া ০ দেখাবে।",
                    )
                }
            }
        },
        confirmButton = {
            val excess = pendingExcess
            if (excess != null) {
                Column(Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { run(true) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("হ্যাঁ, পরিশোধ করুন", color = MaterialTheme.colorScheme.primary) }
                    TextButton(
                        onClick = { pendingExcess = null },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("না — পরিমাণ ঠিক করব", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            } else {
                TextButton(onClick = { run(false) }, enabled = !busy) {
                    Text(if (busy) "প্রসেস হচ্ছে…" else "পরিশোধ করুন", color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("বাতিল") }
        },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

/** Small helper for the four detail screens: standard payment action row. */
@Composable
fun PaymentActionButtons(
    onAddPayment: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.material3.Button(
            onClick = onAddPayment,
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
        ) { Text("পরিশোধ যোগ", style = MaterialTheme.typography.titleMedium) }
        if (extra != null) extra()
    }
}
