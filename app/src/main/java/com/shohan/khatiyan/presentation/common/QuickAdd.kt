package com.shohan.khatiyan.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.shohan.khatiyan.core.BnDates
import com.shohan.khatiyan.core.Money
import com.shohan.khatiyan.di.AppContainer
import com.shohan.khatiyan.domain.finance.OverpaymentException
import com.shohan.khatiyan.domain.model.PaymentMethod
import com.shohan.khatiyan.ui.components.AmountInput
import com.shohan.khatiyan.ui.components.ChoiceChipsRow
import com.shohan.khatiyan.ui.components.DateField
import com.shohan.khatiyan.ui.components.PillTone
import com.shohan.khatiyan.ui.components.PrimaryButton
import com.shohan.khatiyan.ui.components.WarningBanner
import com.shohan.khatiyan.ui.theme.KhatiyanBrand
import kotlinx.coroutines.launch

enum class QuickAction(val labelBn: String, val icon: ImageVector) {
    SHOP_CREDIT("দোকানের বাকী যোগ করুন", Icons.Outlined.Storefront),
    PAYMENT("কোনো বকেয়ায় পরিশোধ", Icons.Outlined.Payments),
    LOAN("নতুন লোন", Icons.Outlined.AccountBalance),
    EMI("নতুন EMI / কিস্তির পণ্য", Icons.Outlined.PhoneAndroid),
    PERSONAL_DEBT("ব্যক্তিগত ধার", Icons.AutoMirrored.Outlined.Login),
    INCOME("আয় যোগ করুন", Icons.Outlined.TrendingUp),
    EXPENSE("ব্যয় যোগ করুন", Icons.Outlined.TrendingDown),
}

/** Quick-Add sheet (Phase 8). Every row performs real navigation — no dead buttons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onAction: (QuickAction) -> Unit,
) {
    if (!visible) return
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.padding(bottom = 20.dp)) {
            Text(
                "কী যোগ করতে চান?",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            QuickAction.entries.forEach { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDismiss(); onAction(action) }
                        .padding(horizontal = 20.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(KhatiyanBrand.Primary.copy(alpha = 0.10f), MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(action.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(action.labelBn, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/**
 * Real "pay something" flow: pick book → pick entry → amount. Executes through
 * the same repositories as the detail screens, including the overpayment
 * policy (Phase 18): the first over-limit attempt surfaces an explicit
 * confirmation instead of silently accepting or rejecting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPaymentSheet(
    container: AppContainer,
    visible: Boolean,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        QuickPaymentFlow(container, onDone)
    }
}

private enum class PayKind(val labelBn: String) {
    SHOP("দোকান"), LOAN("লোন"), EMI("EMI"), PERSONAL("ব্যক্তিগত")
}

private suspend fun attemptPayment(
    container: AppContainer,
    kind: PayKind,
    entityId: Long,
    amountPaisa: Long,
    methodName: String,
    dateIso: String,
    allowOver: Boolean,
): OverpaymentException? = try {
    when (kind) {
        PayKind.SHOP -> container.shopRepo.addPayment(entityId, dateIso, amountPaisa, methodName, "কুইক পরিশোধ", allowOver)
        PayKind.LOAN -> container.loanRepo.recordPayment(entityId, dateIso, amountPaisa, methodName, "কুইক পরিশোধ", allowOver)
        PayKind.EMI -> container.emiRepo.recordPayment(entityId, dateIso, amountPaisa, methodName, "কুইক পরিশোধ", allowOver)
        PayKind.PERSONAL -> container.personalRepo.recordRepayment(entityId, dateIso, amountPaisa, methodName, "কুইক পরিশোধ", allowOver)
    }
    null
} catch (e: OverpaymentException) {
    if (allowOver) null else e
}

@Composable
private fun QuickPaymentFlow(container: AppContainer, onDone: (String) -> Unit) {
    var step by remember { mutableStateOf(0) }
    var kind by remember { mutableStateOf(PayKind.SHOP) }
    var entityOptions by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    var selectedEntity by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var amountText by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(PaymentMethod.CASH.labelBn) }
    var dateIso by remember { mutableStateOf(BnDates.toIso(BnDates.today())) }
    var overExcess by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(kind) {
        selectedEntity = null
        val today = BnDates.toIso(BnDates.today())
        val symbol = com.shohan.khatiyan.core.Money.DEFAULT_SYMBOL
        entityOptions = when (kind) {
            PayKind.SHOP -> container.db.shopDao().getShopRows()
                .filter { it.balancePaisa > 0 }
                .map { it.id to "${it.name} — বাকি ${Money.format(it.balancePaisa, symbol)}" }
            PayKind.LOAN -> container.db.loanDao().getLoanRows(today)
                .filter { it.remainingPaisa > 0 }
                .map { it.id to "${it.loanName.ifBlank { it.institution }} — বাকি ${Money.format(it.remainingPaisa, symbol)}" }
            PayKind.EMI -> container.db.emiDao().getEmiRows(today)
                .filter { it.remainingPaisa > 0 }
                .map { it.id to "${it.productName} — বাকি ${Money.format(it.remainingPaisa, symbol)}" }
            PayKind.PERSONAL -> container.db.personalDao().getAllDebtsWithRemaining()
                .filter { it.remainingPaisa > 0 }
                .map { it.id to "${it.personName} — বাকি ${Money.format(it.remainingPaisa, symbol)}" }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Text(
            when (step) {
                0 -> "কোন খাতায় পরিশোধ?"
                1 -> "কোন এন্ট্রিতে?"
                else -> "পরিশোধের পরিমাণ"
            },
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(12.dp))
        when (step) {
            0 -> ChoiceChipsRow(
                options = PayKind.entries.map { it.labelBn },
                selected = kind.labelBn,
                onSelect = { label -> kind = PayKind.entries.first { it.labelBn == label } },
            )
            1 -> {
                if (entityOptions.isEmpty()) {
                    WarningBanner("এই মুহূর্তে বকেয়া কোনো এন্ট্রি নেই।", tone = PillTone.INFO)
                } else {
                    entityOptions.forEach { opt ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selectedEntity = opt; step = 2 }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selectedEntity == opt, onClick = { selectedEntity = opt; step = 2 })
                            Spacer(Modifier.width(6.dp))
                            Text(opt.second, style = MaterialTheme.typography.bodyLarge)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            else -> {
                val entity = selectedEntity
                if (entity == null) {
                    Text("এন্ট্রি নির্বাচন হয়নি।", style = MaterialTheme.typography.bodyMedium)
                } else {
                    AmountInput(
                        value = amountText,
                        onValueChange = { amountText = it; error = null },
                        label = "টাকার পরিমাণ",
                        symbol = "৳",
                        error = error,
                    )
                    Spacer(Modifier.height(10.dp))
                    ChoiceChipsRow(
                        options = PaymentMethod.entries.map { it.labelBn },
                        selected = method,
                        onSelect = { method = it },
                    )
                    Spacer(Modifier.height(6.dp))
                    DateField(label = "তারিখ", iso = dateIso, onSelected = { dateIso = it })
                    val excess = overExcess
                    if (excess != null) {
                        Spacer(Modifier.height(10.dp))
                        WarningBanner(
                            "বাকির তুলনায় ${Money.format(excess)} বেশি দিচ্ছেন। নিশ্চিত করলে অতিরিক্ত অংশ “অগ্রিম জমা” হিসেবে থাকবে — বকেয়া ০ দেখাবে, ঋণ নেতিবাচক হবে না।",
                        )
                        Spacer(Modifier.height(8.dp))
                        PrimaryButton(
                            text = "হ্যাঁ, এইভাবেই পরিশোধ করুন",
                            enabled = !busy,
                            onClick = {
                                val amount = Money.parse(amountText) ?: return@PrimaryButton
                                busy = true
                                scope.launch {
                                    val again = attemptPayment(container, kind, entity.first, amount, PaymentMethod.entries.first { it.labelBn == method }.name, dateIso, true)
                                    busy = false
                                    if (again == null) {
                                        overExcess = null
                                        onDone("${Money.format(amount)} পরিশোধ হয়েছে — ${entity.second}")
                                    } else {
                                        error = "পরিশোধ করা যায়নি — আবার চেষ্টা করুন।"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = { overExcess = null }) {
                            Text("না — পরিমাণ ঠিক করি", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        PrimaryButton(
                            text = if (busy) "প্রসেস হচ্ছে…" else "পরিশোধ করুন",
                            enabled = !busy,
                            onClick = {
                                val amount = Money.parse(amountText)
                                if (amount == null) {
                                    error = "সঠিক টাকার অঙ্ক লিখুন।"
                                    return@PrimaryButton
                                }
                                busy = true
                                scope.launch {
                                    val fail = attemptPayment(container, kind, entity.first, amount, PaymentMethod.entries.first { it.labelBn == method }.name, dateIso, false)
                                    busy = false
                                    if (fail != null) {
                                        overExcess = fail.excessPaisa
                                    } else {
                                        onDone("${Money.format(amount)} পরিশোধ হয়েছে — ${entity.second}")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (step > 0) {
            TextButton(onClick = { step -= 1; error = null; overExcess = null }) {
                Text("পিছনে যান", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
