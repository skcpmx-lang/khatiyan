package com.shohan.khatiyan.presentation.emi

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.shohan.khatiyan.data.local.entity.EmiPaymentEntity
import com.shohan.khatiyan.data.local.query.EmiRow
import com.shohan.khatiyan.data.repository.EmiRepository
import com.shohan.khatiyan.data.repository.LoanRepository
import com.shohan.khatiyan.di.AppContainer
import com.shohan.khatiyan.domain.model.Frequency
import com.shohan.khatiyan.presentation.common.PaymentEntryDialog
import com.shohan.khatiyan.presentation.loan.PaymentEntryEditDialog
import com.shohan.khatiyan.presentation.navigation.Routes
import com.shohan.khatiyan.ui.components.AppCard
import com.shohan.khatiyan.ui.components.AmountInput
import com.shohan.khatiyan.ui.components.ChoiceChipsRow
import com.shohan.khatiyan.ui.components.ConfirmDialog
import com.shohan.khatiyan.ui.components.DateField
import com.shohan.khatiyan.ui.components.EmptyState
import com.shohan.khatiyan.ui.components.KhatiyanScaffold
import com.shohan.khatiyan.ui.components.KhatiyanTextField
import com.shohan.khatiyan.ui.components.PillTone
import com.shohan.khatiyan.ui.components.PrimaryButton
import com.shohan.khatiyan.ui.components.SectionTitle
import com.shohan.khatiyan.ui.components.SecondaryButton
import com.shohan.khatiyan.ui.components.StatusPill
import com.shohan.khatiyan.ui.theme.KhatiyanBrand
import com.shohan.khatiyan.utilities.BnText
import com.shohan.khatiyan.utilities.DataBus
import com.shohan.khatiyan.utilities.containerFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class EmiListViewModel(private val container: AppContainer) : ViewModel() {
    data class UiState(
        val query: String = "",
        val showArchived: Boolean = false,
        val rows: List<EmiRow> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state

    init {
        rebuild()
        DataBus.changes.onEach { rebuild() }.launchIn(viewModelScope)
    }

    fun setQuery(q: String) { _state.value = _state.value.copy(query = q); rebuild() }
    fun toggleArchived() { _state.value = _state.value.copy(showArchived = !_state.value.showArchived); rebuild() }

    private fun rebuild() {
        container.emiRepo.observeEmis(_state.value.query.trim(), _state.value.showArchived)
            .onEach { rows -> _state.value = _state.value.copy(rows = rows) }
            .launchIn(viewModelScope)
    }
}

@Composable
fun EmisTabScaffold(navController: NavController, container: AppContainer) {
    KhatiyanScaffold(
        title = "কিস্তি (EMI)",
        onBack = { navController.popBackStack() },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Routes.emiEdit()) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Outlined.Add, "নতুন EMI") }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            EmiListContent(navController, container)
        }
    }
}

@Composable
fun EmiListContent(navController: NavController, container: AppContainer, modifier: Modifier = Modifier) {
    val vm: EmiListViewModel = viewModel(factory = containerFactory { EmiListViewModel(it) })
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = state.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("পণ্য বা দোকানের নাম খুঁজুন…") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            leadingIcon = { Icon(com.shohan.khatiyan.ui.icons.KhatiyanIcons.Search, null) },
        )
        if (state.rows.isEmpty()) {
            EmptyState(
                title = if (state.query.isNotBlank()) "এই নামে কিছু পাওয়া যায়নি" else "এখনো কোনো EMI যোগ করা হয়নি",
                subtitle = "মোবাইল, ল্যাপটপ, ফ্রিজ — কিস্তির পণ্য যোগ করুন, খতিয়ান সময়মতো মনে করিয়ে দেবে।",
                actionLabel = if (state.query.isBlank()) "+ নতুন EMI" else null,
                onAction = { navController.navigate(Routes.emiEdit()) },
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.rows.forEach { row ->
                    EmiRowCard(row) { navController.navigate(Routes.emiDetail(row.id)) }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun EmiRowCard(row: EmiRow, onClick: () -> Unit) {
    val financedTotal = row.financedPaisa
    val progress = if (financedTotal > 0) (row.totalPaidPaisa.toFloat() / financedTotal.toFloat()).coerceIn(0f, 1f) else 0f
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.productName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (row.seller.isNotBlank()) {
                    Text(row.seller, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (row.remainingPaisa > 0) "বাকি" else "শেষ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    Money.format(row.remainingPaisa.coerceAtLeast(0)),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (row.remainingPaisa > 0) KhatiyanBrand.Danger else KhatiyanBrand.Success,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "কিস্তি ${BnText.toBnDigits("${row.paidCount}/${row.paidCount + row.openCount}")} · ${Frequency.fromName(row.frequency).shortBn} ${Money.format(row.installmentPaisa)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(7.dp),
            color = KhatiyanBrand.Gold,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (row.overdueCount > 0) {
                StatusPill("${BnText.toBnDigits(row.overdueCount.toString())}টি ওভারডিউ", PillTone.DANGER)
            }
            val next = BnDates.fromIso(row.nextDueIso)
            if (next != null) {
                StatusPill("পরবর্তী কিস্তি ${BnDates.relativeCompact(next)}", PillTone.INFO)
            } else if (row.openCount == 0) {
                StatusPill("পরিশোধ সম্পন্ন ✓", PillTone.SUCCESS)
            }
        }
    }
}

// ---------- Edit -------------------------------------------------------------------------------

class EmiEditViewModel(private val container: AppContainer, private val emiId: Long?) : ViewModel() {

    data class Form(
        val productName: String = "",
        val seller: String = "",
        val purchaseIso: String = BnDates.toIso(BnDates.today()),
        val totalPrice: String = "",
        val downPayment: String = "",
        val totalPayable: String = "",
        val installment: String = "",
        val count: String = "12",
        val frequency: Frequency = Frequency.MONTHLY,
        val firstDueIso: String = BnDates.toIso(BnDates.today().plusMonths(1)),
        val note: String = "",
        val error: String? = null,
        val loaded: Boolean = false,
    )

    private val _state = MutableStateFlow(Form())
    val state = _state

    init {
        viewModelScope.launch {
            if (emiId != null) {
                container.db.emiDao().getEmi(emiId)?.let { e ->
                    _state.value = _state.value.copy(
                        productName = e.productName,
                        seller = e.seller,
                        purchaseIso = e.purchaseIso,
                        totalPrice = Money.formatPlain(e.totalPricePaisa),
                        downPayment = if (e.downPaymentPaisa > 0) Money.formatPlain(e.downPaymentPaisa) else "",
                        totalPayable = if (e.totalPayablePaisa != e.totalPricePaisa) Money.formatPlain(e.totalPayablePaisa) else "",
                        installment = if (e.installmentPaisa > 0) Money.formatPlain(e.installmentPaisa) else "",
                        count = e.installmentCount.toString(),
                        frequency = Frequency.fromName(e.frequency),
                        firstDueIso = e.firstDueIso,
                        note = e.note,
                    )
                }
            }
            _state.value = _state.value.copy(loaded = true)
        }
    }

    fun update(transform: (Form) -> Form) { _state.value = transform(_state.value) }

    fun preview(): Pair<Long, Int>? {
        val f = _state.value
        val price = Money.parse(f.totalPrice) ?: return null
        val down = Money.parseAllowZero(f.downPayment) ?: 0L
        val payable = Money.parse(f.totalPayable) ?: price
        val financed = (payable - down).coerceAtLeast(0)
        val count = f.count.toIntOrNull()?.coerceIn(1, 600) ?: 12
        return financed to count
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val f = _state.value
            val price = Money.parse(f.totalPrice)
            if (f.productName.isBlank()) {
                _state.value = f.copy(error = "পণ্যের নাম লিখুন।")
                return@launch
            }
            if (price == null) {
                _state.value = f.copy(error = "পণ্যের মোট দাম লিখুন।")
                return@launch
            }
            val down = Money.parseAllowZero(f.downPayment) ?: 0L
            if (down > price) {
                _state.value = f.copy(error = "অগ্রিম পরিশোধ মোট দামের চেয়ে বেশি হতে পারবে না।")
                return@launch
            }
            val draft = EmiRepository.EmiDraft(
                id = emiId ?: 0L,
                productName = f.productName.trim(),
                seller = f.seller.trim(),
                purchaseIso = f.purchaseIso,
                totalPricePaisa = price,
                downPaymentPaisa = down,
                totalPayablePaisa = Money.parse(f.totalPayable) ?: 0L,
                installmentPaisa = Money.parse(f.installment) ?: 0L,
                installmentCount = f.count.toIntOrNull()?.coerceIn(1, 600) ?: 12,
                frequency = f.frequency,
                firstDueIso = f.firstDueIso,
                note = f.note,
            )
            val result = runCatching { container.emiRepo.save(draft) }
            val failure = result.exceptionOrNull()
            if (failure != null) {
                val msg = if (failure is com.shohan.khatiyan.utilities.FinanceValidationException) failure.messageBn
                else "সংরক্ষণ করা যায়নি। লেখাগুলো মিলিয়ে আবার চেষ্টা করুন।"
                _state.value = _state.value.copy(error = msg)
                return@launch
            }
            onSaved()
        }
    }
}

@Composable
fun EmiEditScreen(navController: NavController, container: AppContainer, emiId: Long?) {
    val vm: EmiEditViewModel = viewModel(factory = containerFactory { EmiEditViewModel(it, emiId) })
    val f by vm.state.collectAsStateWithLifecycle()
    val preview = remember(f) { vm.preview() }

    KhatiyanScaffold(
        title = if (emiId == null) "নতুন EMI" else "EMI সম্পাদনা",
        onBack = { navController.popBackStack() },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard {
                Text("পণ্যের তথ্য", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                KhatiyanTextField(f.productName, { v -> vm.update { it.copy(productName = v, error = null) } }, "পণ্যের নাম *")
                Spacer(Modifier.height(8.dp))
                KhatiyanTextField(f.seller, { v -> vm.update { it.copy(seller = v) } }, "কোথা থেকে কিনেছেন")
                Spacer(Modifier.height(8.dp))
                DateField("কেনার তারিখ", f.purchaseIso, { v -> vm.update { it.copy(purchaseIso = v) } })
            }
            AppCard {
                Text("মূল্য ও কিস্তি", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                AmountInput(f.totalPrice, { v -> vm.update { it.copy(totalPrice = v, error = null) } }, "মোট দাম *", "৳")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmountInput(f.downPayment, { v -> vm.update { it.copy(downPayment = v) } }, "অগ্রিম (ডাউন পেমেন্ট)", "৳", Modifier.weight(1f))
                    AmountInput(f.totalPayable, { v -> vm.update { it.copy(totalPayable = v) } }, "সর্বমোট (ঐচ্ছিক)", "৳", Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "সর্বমোট খালি রাখলে মোট মূল্যই ধরা হবে; সুদ/ফি যোগ হলে এখানে লিখুন।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmountInput(f.installment, { v -> vm.update { it.copy(installment = v) } }, "মাসিক কিস্তি (ঐচ্ছিক)", "৳", Modifier.weight(1f))
                    KhatiyanTextField(
                        f.count,
                        { v -> vm.update { it.copy(count = v.filter { c -> c.isDigit() }.take(3)) } },
                        "মোট কিস্তি",
                        modifier = Modifier.weight(1f),
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    )
                }
                Spacer(Modifier.height(8.dp))
                ChoiceChipsRow(
                    options = Frequency.entries.map { it.labelBn },
                    selected = f.frequency.labelBn,
                    onSelect = { label -> vm.update { it.copy(frequency = Frequency.entries.first { e -> e.labelBn == label }) } },
                )
                Spacer(Modifier.height(6.dp))
                DateField("প্রথম কিস্তির তারিখ *", f.firstDueIso, { v -> vm.update { it.copy(firstDueIso = v) } })
                preview?.let { (financed, count) ->
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), MaterialTheme.shapes.medium)
                            .padding(12.dp),
                    ) {
                        Text(
                            "কিস্তিতে থাকবে ${Money.format(financed)} — ${BnText.toBnDigits(count.toString())}টি কিস্তিতে ভাগ হবে (শেষ কিস্তি সামান্য কম-বেশি হতে পারে)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                KhatiyanTextField(f.note, { v -> vm.update { it.copy(note = v) } }, "নোট (ঐচ্ছিক)", singleLine = false, minLines = 2)
            }
            if (f.error != null) {
                Text(f.error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            PrimaryButton(
                "সংরক্ষণ",
                onClick = { vm.save { navController.popBackStack() } },
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                enabled = f.loaded,
            )
        }
    }
}

// ---------- Detail -------------------------------------------------------------------------------

class EmiDetailViewModel(private val container: AppContainer, private val emiId: Long) : ViewModel() {
    private val _state = MutableStateFlow<EmiRepository.EmiDetail?>(null)
    val state = _state
    val events = MutableSharedFlow<String>(extraBufferCapacity = 4)

    init {
        refresh()
        DataBus.changes.onEach { refresh() }.launchIn(viewModelScope)
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = runCatching { container.emiRepo.loadDetail(emiId) }.getOrNull()
        }
    }

    suspend fun recordPayment(amount: Long, method: String, date: String, note: String, allow: Boolean): com.shohan.khatiyan.domain.finance.OverpaymentException? =
        try {
            container.emiRepo.recordPayment(emiId, date, amount, method, note, allow)
            events.emit("${Money.format(amount)} কিস্তি পরিশোধ যোগ হয়েছে")
            null
        } catch (e: com.shohan.khatiyan.domain.finance.OverpaymentException) {
            if (allow) null else e
        }

    suspend fun updatePayment(paymentId: Long, amount: Long, method: String, date: String, note: String, allow: Boolean): com.shohan.khatiyan.domain.finance.OverpaymentException? =
        try {
            container.emiRepo.updatePayment(emiId, paymentId, date, amount, method, note, allow)
            events.emit("পরিশোধ হালনাগাদ হয়েছে")
            null
        } catch (e: com.shohan.khatiyan.domain.finance.OverpaymentException) {
            if (allow) null else e
        }

    fun deletePayment(paymentId: Long) {
        viewModelScope.launch {
            container.emiRepo.deletePayment(emiId, paymentId)
            events.emit("পরিশোধটি মুছে দেওয়া হয়েছে")
        }
    }

    fun setArchived(archived: Boolean) {
        viewModelScope.launch { container.emiRepo.setArchived(emiId, archived) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            container.emiRepo.deleteEmi(emiId)
            events.emit("EMI আর তার সব কিস্তির হিসাব মুছে গেছে")
            onDone()
        }
    }
}

@Composable
fun EmiDetailScreen(navController: NavController, container: AppContainer, emiId: Long) {
    val vm: EmiDetailViewModel = viewModel(factory = containerFactory { EmiDetailViewModel(it, emiId) })
    val detail by vm.state.collectAsStateWithLifecycle()
    var showPayment by remember { mutableStateOf(false) }
    var editPayment by remember { mutableStateOf<EmiPaymentEntity?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    val snackbar = androidx.compose.material3.SnackbarHostState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    val csvLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = runCatching { CsvExporter.writeTo(context, uri, CsvExporter.emiLedgerCsv(container.db, emiId)) }.isSuccess
                snackbar.showSnackbar(if (ok) "CSV সেভ হয়েছে" else "CSV সেভ করা যায়নি")
            }
        }
    }

    KhatiyanScaffold(
        title = detail?.emi?.productName ?: "EMI",
        onBack = { navController.popBackStack() },
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = { navController.navigate(Routes.emiEdit(emiId)) }) { Icon(Icons.Outlined.Edit, "সম্পাদনা") }
            IconButton(onClick = { csvLauncher.launch("khatiyan-emi-$emiId.csv") }) { Icon(Icons.Outlined.Download, "CSV নামান") }
        },
    ) { padding ->
        val d = detail
        if (d == null) {
            Box(Modifier.padding(padding))
            return@KhatiyanScaffold
        }
        val emi = d.emi
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard {
                Text(emi.productName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (emi.seller.isNotBlank()) {
                    Text("বিক্রেতা: ${emi.seller}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "মূল্য ${Money.format(emi.totalPricePaisa)} · অগ্রিম ${Money.format(emi.downPaymentPaisa)} · সর্বমোট ${Money.format(emi.totalPayablePaisa)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (emi.note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("নোট: ${emi.note}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val financed = emi.financedPaisa
            val paid = d.totalPaidPaisa
            val remaining = d.remainingPaisa
            val progress = if (financed > 0) (paid.toFloat() / financed.toFloat()).coerceIn(0f, 1f) else 0f
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("কিস্তিতে বাকি", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            Money.format(remaining.coerceAtLeast(0)),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (remaining > 0) KhatiyanBrand.Danger else KhatiyanBrand.Success,
                            maxLines = 1,
                        )
                        if (remaining < 0) {
                            Text("অগ্রিম জমা ${Money.format(-remaining)}", style = MaterialTheme.typography.labelSmall, color = KhatiyanBrand.Success)
                        }
                        Text(
                            "পরিশোধিত (অগ্রিমসহ) ${Money.format(paid + emi.downPaymentPaisa)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    com.shohan.khatiyan.ui.charts.ProgressRing(
                        fraction = progress,
                        color = KhatiyanBrand.Gold,
                        modifier = Modifier.size(72.dp),
                        centerText = "${BnText.toBnDigits((progress * 100).toInt().toString())}%",
                    )
                }
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.Button(
                    onClick = { showPayment = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) { Text("কিস্তি পরিশোধ করুন") }
            }

            SectionTitle("কিস্তির তালিকা")
            AppCard(contentPadding = PaddingValues(12.dp)) {
                d.installments.forEach { i ->
                    val tone = when (i.status) {
                        LoanRepository.InstallmentStatus.PAID -> PillTone.SUCCESS
                        LoanRepository.InstallmentStatus.OVERDUE -> PillTone.DANGER
                        LoanRepository.InstallmentStatus.DUE_TODAY -> PillTone.WARNING
                        LoanRepository.InstallmentStatus.PENDING -> PillTone.NEUTRAL
                    }
                    val label = when (i.status) {
                        LoanRepository.InstallmentStatus.PAID -> "পরিশোধিত ✓"
                        LoanRepository.InstallmentStatus.OVERDUE -> "ওভারডিউ"
                        LoanRepository.InstallmentStatus.DUE_TODAY -> "আজ"
                        LoanRepository.InstallmentStatus.PENDING -> i.dueDate?.let { BnDates.relativeCompact(it) } ?: ""
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(
                                    if (i.status == LoanRepository.InstallmentStatus.PAID) KhatiyanBrand.SuccessBg else MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.shapes.small,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                BnText.toBnDigits(i.number.toString()),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (i.status == LoanRepository.InstallmentStatus.PAID) KhatiyanBrand.Success else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(i.dueDate?.let { BnDates.formatShort(it) } ?: "তারিহবিহীন", style = MaterialTheme.typography.titleSmall)
                            if (i.paidPaisa > 0 && i.paidPaisa < i.amountPaisa) {
                                Text("আংশিক ${Money.format(i.paidPaisa)} দেওয়া হয়েছে", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        StatusPill(label, tone)
                        Spacer(Modifier.width(8.dp))
                        Text(Money.format(i.amountPaisa), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    }
                }
            }

            SectionTitle("পরিশোধের ইতিহাস")
            if (d.payments.isEmpty()) {
                AppCard { Text("এখনো কোনো কিস্তি পরিশোধ করা হয়নি।", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                AppCard(contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp)) {
                    d.payments.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(BnDates.formatShort(BnDates.fromIso(p.dateIso) ?: BnDates.today()), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    com.shohan.khatiyan.domain.model.PaymentMethod.fromName(p.method).labelBn + if (p.note.isNotBlank()) " · ${p.note}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(Money.format(p.amountPaisa), style = MaterialTheme.typography.titleMedium, color = KhatiyanBrand.Success)
                            IconButton(onClick = { editPayment = p }) { Icon(Icons.Outlined.Edit, "সম্পাদনা", Modifier.size(18.dp)) }
                            IconButton(onClick = { vm.deletePayment(p.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, "মুছুন", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            SecondaryButton(
                text = if (emi.archived) "আর্কাইভ থেকে ফিরিয়ে আনুন" else "EMI আর্কাইভ করুন",
                onClick = { vm.setArchived(!emi.archived) },
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.material3.TextButton(onClick = { showDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("EMI আর সব কিস্তি মুছে ফেলুন", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    PaymentEntryDialog(
        visible = showPayment,
        title = "EMI কিস্তি পরিশোধ",
        symbol = "৳",
        onDismiss = { showPayment = false },
        submit = { amount, method, date, note, allow -> vm.recordPayment(amount, method, date, note, allow) },
    )

    val editing = editPayment
    if (editing != null) {
        PaymentEntryEditDialog(
            visible = true,
            title = "পরিশোধ সম্পাদনা",
            symbol = "৳",
            initialAmount = Money.formatPlain(editing.amountPaisa),
            initialNote = editing.note,
            initialDate = editing.dateIso,
            onDismiss = { editPayment = null },
            submit = { amount, method, date, note, allow -> vm.updatePayment(editing.id, amount, method, date, note, allow) },
        )
    }

    if (showDelete) {
        ConfirmDialog(
            title = "EMI মুছে ফেলবেন?",
            message = "পণ্যের হিসাব, কিস্তির সময়সূচি আর সব পরিশোধ মুছে যাবে। আগে একটা ব্যাকআপ রেখে দিন।",
            confirmLabel = "হ্যাঁ, মুছে দিন",
            danger = true,
            onConfirm = { showDelete = false; vm.delete { navController.popBackStack() } },
            onDismiss = { showDelete = false },
        )
    }
}
