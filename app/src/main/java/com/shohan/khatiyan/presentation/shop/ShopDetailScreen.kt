package com.shohan.khatiyan.presentation.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.shohan.khatiyan.core.BnDates
import com.shohan.khatiyan.core.Money
import com.shohan.khatiyan.data.backup.CsvExporter
import com.shohan.khatiyan.data.local.entity.ShopEntity
import com.shohan.khatiyan.data.local.entity.ShopPaymentEntity
import com.shohan.khatiyan.data.repository.ShopRepository
import com.shohan.khatiyan.di.AppContainer
import com.shohan.khatiyan.domain.finance.OverpaymentException
import com.shohan.khatiyan.domain.model.PaymentMethod
import com.shohan.khatiyan.presentation.common.PaymentEntryDialog
import com.shohan.khatiyan.presentation.navigation.Routes
import com.shohan.khatiyan.ui.components.AppCard
import com.shohan.khatiyan.ui.components.ConfirmDialog
import com.shohan.khatiyan.ui.components.KhatiyanScaffold
import com.shohan.khatiyan.ui.components.PillTone
import com.shohan.khatiyan.ui.components.SectionTitle
import com.shohan.khatiyan.ui.components.StatusPill
import com.shohan.khatiyan.ui.theme.KhatiyanBrand
import com.shohan.khatiyan.utilities.DataBus
import com.shohan.khatiyan.utilities.containerFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ShopDetailViewModel(private val container: AppContainer, private val shopId: Long) : ViewModel() {

    data class UiState(
        val detail: ShopRepository.ShopDetail? = null,
        val loading: Boolean = true,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state

    val events = MutableSharedFlow<String>(extraBufferCapacity = 4)

    init {
        refresh()
        DataBus.changes.onEach { refresh() }.launchIn(viewModelScope)
    }

    fun refresh() {
        viewModelScope.launch {
            val detail = runCatching { container.shopRepo.loadDetail(shopId) }.getOrNull()
            _state.value = _state.value.copy(detail = detail, loading = false)
        }
    }

    suspend fun recordPayment(
        amount: Long,
        method: String,
        dateIso: String,
        note: String,
        allow: Boolean,
    ): OverpaymentException? =
        try {
            container.shopRepo.addPayment(shopId, dateIso, amount, method, note, allow)
            events.emit("${Money.format(amount)} পরিশোধ যোগ হয়েছে")
            null
        } catch (e: OverpaymentException) {
            if (allow) null else e
        }

    suspend fun updatePayment(
        payment: com.shohan.khatiyan.data.local.entity.ShopPaymentEntity,
        amount: Long,
        method: String,
        dateIso: String,
        note: String,
        allow: Boolean,
    ): OverpaymentException? =
        try {
            container.shopRepo.updatePayment(payment, amount, dateIso, method, note, allow)
            events.emit("পরিশোধের হিসাব আপডেট হয়েছে")
            null
        } catch (e: OverpaymentException) {
            if (allow) null else e
        }

    fun deletePayment(paymentId: Long) {
        viewModelScope.launch {
            container.shopRepo.deletePayment(paymentId)
            events.emit("পরিশোধটি মুছে গেছে")
        }
    }

    fun deleteCredit(creditId: Long) {
        viewModelScope.launch {
            container.shopRepo.deleteCredit(creditId)
            events.emit("বাকির হিসাবটি মুছে গেছে")
        }
    }
}

@Composable
fun ShopDetailScreen(navController: NavController, container: AppContainer, shopId: Long) {
    val vm: ShopDetailViewModel = viewModel(factory = containerFactory { ShopDetailViewModel(it, shopId) })
    val state by vm.state.collectAsStateWithLifecycle()
    val detail = state.detail
    var showPayment by remember { mutableStateOf(false) }
    var editPayment by remember { mutableStateOf<ShopPaymentEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<Triple<String, Long, String>?>(null) }
    val snackbar = androidx.compose.material3.SnackbarHostState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    val csvLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = runCatching {
                    val csv = CsvExporter.shopLedgerCsv(container.db, shopId)
                    CsvExporter.writeTo(context, uri, csv)
                }.isSuccess
                snackbar.showSnackbar(if (ok) "CSV ফাইলটি সেভ হয়েছে" else "CSV সেভ করা যায়নি")
            }
        }
    }

    KhatiyanScaffold(
        title = detail?.shop?.name ?: "দোকান",
        onBack = { navController.popBackStack() },
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = { navController.navigate(Routes.shopEdit(shopId)) }) {
                Icon(Icons.Outlined.Edit, contentDescription = "দোকানের তথ্য সম্পাদনা")
            }
            IconButton(onClick = {
                csvLauncher.launch("khatiyan-${detail?.shop?.name?.replace(' ', '-') ?: "shop"}-$shopId.csv")
            }) {
                Icon(Icons.Filled.Download, contentDescription = "CSV হিসাব নামান")
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Routes.creditEdit(shopId)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Outlined.Storefront, "নতুন বাকি") }
        },
    ) { padding ->
        if (detail == null) {
            Box(Modifier.padding(padding))
            return@KhatiyanScaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(detail.shop.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        val sub = listOfNotNull(
                            detail.shop.ownerName.takeIf { it.isNotBlank() }?.let { "মালিক: $it" },
                            detail.shop.phone.takeIf { it.isNotBlank() },
                        ).joinToString(" · ")
                        if (sub.isNotBlank()) {
                            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (detail.shop.address.isNotBlank()) {
                            Text(
                                detail.shop.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (detail.shop.archived) StatusPill("আর্কাইভড", PillTone.NEUTRAL)
                }
                if (detail.shop.note.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("নোট: ${detail.shop.note}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val balance = detail.balancePaisa
            AppCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (balance >= 0) "বর্তমান বাকি" else "অগ্রিম জমা",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    Money.format(if (balance >= 0) balance else -balance),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (balance > 0) KhatiyanBrand.Danger else if (balance < 0) KhatiyanBrand.Success else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SummaryCol("মোট বাকী হয়েছে", Money.format(detail.totalCreditPaisa), MaterialTheme.colorScheme.onSurface)
                    SummaryCol("মোট পরিশোধ", Money.format(detail.totalPaidPaisa), KhatiyanBrand.Success)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.Button(
                        onClick = { showPayment = true },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                    ) { Text("পেমেন্ট যোগ করুন") }
                    androidx.compose.material3.OutlinedButton(
                        onClick = { navController.navigate(Routes.creditEdit(shopId)) },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                    ) { Text("বাকী যোগ করুন") }
                }
            }

            SectionTitle("বাকির হিসাব (${com.shohan.khatiyan.utilities.BnText.toBnDigits(detail.credits.size.toString())}টি)")
            if (detail.credits.isEmpty()) {
                AppCard {
                    Text(
                        "এখনো কোনো বাকি যোগ করা হয়নি।\nনিচের + থেকে প্রথম বাকি লিখুন — পণ্য, পরিমাণ, দামসহ।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                detail.credits.forEach { entry ->
                    CreditCard(
                        entry = entry,
                        onEdit = { navController.navigate(Routes.creditEdit(shopId, entry.credit.id)) },
                        onDelete = { deleteTarget = Triple("বাকির হিসাবটি", entry.credit.id, "মুছে দিলে এই হিসাবের পণ্যের তালিকাও মুছে যাবে। বাকিটুকু নতুন করে হিসাব হবে।") },
                    )
                }
            }

            SectionTitle("পরিশোধের তালিকা")
            if (detail.payments.isEmpty()) {
                AppCard {
                    Text(
                        "এখনো কোনো পরিশোধ যোগ করা হয়নি।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                AppCard(contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp)) {
                    detail.payments.forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    BnDates.formatShort(BnDates.fromIso(p.dateIso) ?: BnDates.today()),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    PaymentMethod.fromName(p.method).labelBn + (if (p.note.isNotBlank()) " · ${p.note}" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(Money.format(p.amountPaisa), style = MaterialTheme.typography.titleMedium, color = KhatiyanBrand.Success)
                            IconButton(onClick = { editPayment = p }) {
                                Icon(Icons.Outlined.Edit, "সম্পাদনা", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { deleteTarget = Triple("পরিশোধ", p.id, "মুছে ফেললে বকেয়া আবার হিসাব হবে।") }) {
                                Icon(Icons.Outlined.DeleteOutline, "মুছুন", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                        if (p != detail.payments.last()) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        }
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    PaymentEntryDialog(
        visible = showPayment,
        title = "দোকানে পরিশোধ",
        symbol = "৳",
        onDismiss = { showPayment = false },
        submit = { amount, method, date, note, allow -> vm.recordPayment(amount, method, date, note, allow) },
    )

    val editing = editPayment
    if (editing != null) {
        com.shohan.khatiyan.presentation.loan.PaymentEntryEditDialog(
            visible = true,
            title = "পরিশোধ সম্পাদনা",
            symbol = "৳",
            initialAmount = Money.formatPlain(editing.amountPaisa),
            initialNote = editing.note,
            initialDate = editing.dateIso,
            onDismiss = { editPayment = null },
            submit = { amount, method, date, note, allow ->
                vm.updatePayment(editing, amount, method, date, note, allow)
            },
        )
    }

    val toDelete = deleteTarget
    if (toDelete != null) {
        ConfirmDialog(
            title = "${toDelete.first} মুছে ফেলবেন?",
            message = toDelete.third,
            confirmLabel = "মুছে দিন",
            danger = true,
            onConfirm = {
                if (toDelete.first.startsWith("পরিশোধ")) vm.deletePayment(toDelete.second) else vm.deleteCredit(toDelete.second)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun SummaryCol(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CreditCard(
    entry: ShopRepository.CreditWithItems,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val today = remember { BnDates.today() }
    val due = BnDates.fromIso(entry.credit.dueDateIso)
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    BnDates.formatLong(BnDates.fromIso(entry.credit.dateIso) ?: today),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (entry.credit.note.isNotBlank()) {
                    Text(entry.credit.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("মোট ${Money.format(entry.credit.totalPaisa)}", style = MaterialTheme.typography.titleMedium)
                if (entry.outstandingPaisa > 0) {
                    Text(
                        "বাকি ${Money.format(entry.outstandingPaisa)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = KhatiyanBrand.Warning,
                    )
                } else {
                    Text("পরিশোধিত", style = MaterialTheme.typography.labelMedium, color = KhatiyanBrand.Success)
                }
            }
        }
        if (due != null) {
            Spacer(Modifier.height(6.dp))
            val tone = when {
                entry.outstandingPaisa > 0 && due.isBefore(today) -> PillTone.DANGER
                entry.outstandingPaisa > 0 && due == today -> PillTone.WARNING
                else -> PillTone.INFO
            }
            StatusPill(
                if (entry.outstandingPaisa > 0) "পরিশোধের তারিখ: ${BnDates.relativeCompact(due, today)}"
                else "নির্ধারিত তারিখে পরিশোধিত ✓",
                tone,
            )
        }
        if (entry.items.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Spacer(Modifier.height(8.dp))
            entry.items.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(item.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val qtyBase = if (item.quantity % 1.0 == 0.0) item.quantity.toLong().toString() else item.quantity.toString()
                    val qty = com.shohan.khatiyan.utilities.BnText.toBnDigits(qtyBase) + item.unit
                    Text(qty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp))
                    Text(Money.format(item.totalPaisa), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButtonRow("সম্পাদনা", onEdit)
            TextButtonRow("মুছুন", onDelete, danger = true)
        }
    }
}

@Composable
private fun TextButtonRow(text: String, onClick: () -> Unit, danger: Boolean = false) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(text, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}
