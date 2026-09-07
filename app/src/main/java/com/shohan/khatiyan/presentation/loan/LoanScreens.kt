package com.shohan.khatiyan.presentation.loan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
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
import com.shohan.khatiyan.data.local.entity.LoanPaymentEntity
import com.shohan.khatiyan.data.local.query.LoanRow
import com.shohan.khatiyan.data.repository.LoanRepository
import com.shohan.khatiyan.di.AppContainer
import com.shohan.khatiyan.domain.finance.LoanMath
import com.shohan.khatiyan.domain.model.Frequency
import com.shohan.khatiyan.presentation.common.PaymentEntryDialog
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

class LoanListViewModel(private val container: AppContainer) : ViewModel() {
    data class UiState(
        val query: String = "",
        val showArchived: Boolean = false,
        val rows: List<LoanRow> = emptyList(),
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
        container.loanRepo.observeLoans(_state.value.query.trim(), _state.value.showArchived)
            .onEach { rows -> _state.value = _state.value.copy(rows = rows) }
            .launchIn(viewModelScope)
    }
}

@Composable
fun LoansTabScaffold(navController: NavController, container: AppContainer) {
    KhatiyanScaffold(
        title = "লোন",
        onBack = { navController.popBackStack() },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Routes.loanEdit()) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Outlined.Add, "নতুন লোন") }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            LoanListContent(navController, container)
        }
    }
}

@Composable
fun LoanListContent(
    navController: NavController,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val vm: LoanListViewModel = viewModel(factory = containerFactory { LoanListViewModel(it) })
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = state.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("লোন বা প্রতিষ্ঠানের নাম খুঁজুন…") },
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
                .padding(horizontal = 16.dp, vertical = 6.dp),
            leadingIcon = { Icon(Icons.Outlined.AccountBalance, null) },
            trailingIcon = {
                androidx.compose.material3.TextButton(onClick = vm::toggleArchived) {
                    Text(
                        if (state.showArchived) "আর্কাইভ দেখানো হচ্ছে" else "আর্কাইভ",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.showArchived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        if (state.rows.isEmpty()) {
            EmptyState(
                title = if (state.query.isNotBlank()) "এই নামে কোনো লোন নেই" else "কোনো লোন যোগ করা হয়নি",
                subtitle = "ব্যাংক বা এনজিওর লোন — কিস্তির সময়, বকেয়া আর বাকিটা খতিয়ান সামলে নেবে।",
                actionLabel = if (state.query.isBlank()) "+ নতুন লোন" else null,
                onAction = { navController.navigate(Routes.loanEdit()) },
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.rows.forEach { row ->
                    LoanRowCard(row) { navController.navigate(Routes.loanDetail(row.id)) }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun LoanRowCard(row: LoanRow, onClick: () -> Unit) {
    val progress = if (row.totalPayablePaisa > 0) {
        (row.totalPaidPaisa.toFloat() / row.totalPayablePaisa.toFloat()).coerceIn(0f, 1f)
    } else 0f
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.loanName.ifBlank { row.institution }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    row.institution + " · " + Frequency.fromName(row.frequency).shortBn + " কিস্তি",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(7.dp),
            color = KhatiyanBrand.Primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            gapSize = 0.dp,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (row.overdueCount > 0) {
                StatusPill("${BnText.toBnDigits(row.overdueCount.toString())}টি ওভারডিউ", PillTone.DANGER)
            }
            val next = BnDates.fromIso(row.nextDueIso)
            if (next != null) {
                StatusPill("পরের কিস্তি: ${BnDates.relativeCompact(next)} · ${Money.format(row.installmentPaisa)}", PillTone.INFO)
            }
            if (row.openCount == 0) {
                StatusPill("পরিশোধ সম্পন্ন ✓", PillTone.SUCCESS)
            }
        }
    }
}

// ---------- Edit screen --------------------------------------------------------------------

class LoanEditViewModel(private val container: AppContainer, private val loanId: Long?) : ViewModel() {

    data class Form(
        val institution: String = "",
        val loanName: String = "",
        val principal: String = "",
        val startIso: String = BnDates.toIso(BnDates.today()),
        val rate: String = "",
        val fee: String = "",
        val totalPayable: String = "",
        val installment: String = "",
        val count: String = "",
        val frequency: Frequency = Frequency.MONTHLY,
        val firstDueIso: String = BnDates.toIso(BnDates.today().plusMonths(1)),
        val maturityIso: String = "",
        val note: String = "",
        val error: String? = null,
        val loaded: Boolean = false,
    )

    private val _state = MutableStateFlow(Form())
    val state = _state

    init {
        viewModelScope.launch {
            if (loanId != null) {
                container.db.loanDao().getLoan(loanId)?.let { l ->
                    _state.value = _state.value.copy(
                        institution = l.institution,
                        loanName = l.loanName,
                        principal = Money.formatPlain(l.principalPaisa),
                        startIso = l.startIso,
                        rate = if (l.annualRateBps > 0) LoanMath.ratePercentLabel(l.annualRateBps) else "",
                        fee = if (l.processingFeePaisa > 0) Money.formatPlain(l.processingFeePaisa) else "",
                        totalPayable = Money.formatPlain(l.totalPayablePaisa),
                        installment = if (l.installmentPaisa > 0) Money.formatPlain(l.installmentPaisa) else "",
                        frequency = Frequency.fromName(l.frequency),
                        firstDueIso = l.firstDueIso,
                        maturityIso = l.maturityIso,
                        note = l.note,
                        loaded = true,
                    )
                }
            }
            _state.value = _state.value.copy(loaded = true)
        }
    }

    fun update(transform: (Form) -> Form) { _state.value = transform(_state.value) }

    /** Auto-suggest total payable + schedule preview from current form inputs. */
    fun preview(): Triple<Long, Long, Int>? {
        val f = _state.value
        val principal = Money.parse(f.principal) ?: return null
        val from = BnDates.fromIso(f.startIso) ?: return null
        val to = BnDates.fromIso(f.maturityIso) ?: from.plusYears(1)
        val bps = f.rate.trim().let { if (it.isEmpty()) 0 else LoanMath.parseRateBps(it) ?: return null }
        val fee = Money.parseAllowZero(f.fee) ?: return null
        val autoTotal = LoanMath.totalPayablePaisa(principal, bps, from, to, fee)
        val total = Money.parse(f.totalPayable) ?: autoTotal
        val firstDue = BnDates.fromIso(f.firstDueIso) ?: return null
        val count = if (BnDates.fromIso(f.maturityIso) != null) {
            com.shohan.khatiyan.domain.finance.ScheduleGenerator.countBetween(firstDue, to, f.frequency)
        } else {
            f.count.toIntOrNull()?.coerceIn(1, 600) ?: 12
        }
        val inst = Money.parse(f.installment) ?: 0L
        val schedule = com.shohan.khatiyan.domain.finance.ScheduleGenerator.generate(
            firstDue, f.frequency, count, total, inst.takeIf { it > 0 },
        )
        return Triple(total, schedule.firstOrNull()?.amountPaisa ?: 0L, schedule.size)
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val f = _state.value
            val principal = Money.parse(f.principal)
            if (principal == null) {
                _state.value = f.copy(error = "মূল টাকার অঙ্ক সঠিকভাবে লিখুন।")
                return@launch
            }
            if (f.institution.isBlank() && f.loanName.isBlank()) {
                _state.value = f.copy(error = "প্রতিষ্ঠানের নাম বা লোনের নাম লিখুন।")
                return@launch
            }
            val firstDue = BnDates.fromIso(f.firstDueIso)
            if (firstDue == null) {
                _state.value = f.copy(error = "প্রথম কিস্তির তারিখ ঠিক দিন।")
                return@launch
            }
            val total = Money.parse(f.totalPayable) ?: run {
                val from = BnDates.fromIso(f.startIso) ?: firstDue
                val to = BnDates.fromIso(f.maturityIso) ?: from.plusYears(1)
                val bps = if (f.rate.isBlank()) 0 else LoanMath.parseRateBps(f.rate) ?: return@launch run {
                    _state.value = f.copy(error = "সুদের হার সঠিক নয় (যেমন 12.5)।")
                    return@launch
                }
                val fee = Money.parseAllowZero(f.fee) ?: 0L
                LoanMath.totalPayablePaisa(principal, bps, from, to, fee)
            }
            val bpsSafe = if (f.rate.isBlank()) 0 else LoanMath.parseRateBps(f.rate) ?: 0
            val draft = LoanRepository.LoanDraft(
                id = loanId ?: 0L,
                institution = f.institution.trim(),
                loanName = f.loanName.trim(),
                principalPaisa = principal,
                startIso = f.startIso,
                annualRateBps = bpsSafe,
                processingFeePaisa = Money.parseAllowZero(f.fee) ?: 0L,
                totalPayablePaisa = total,
                installmentPaisa = Money.parse(f.installment) ?: 0L,
                frequency = f.frequency,
                firstDueIso = f.firstDueIso,
                maturityIso = f.maturityIso,
                installmentCount = f.count.toIntOrNull()?.coerceIn(1, 600) ?: 0,
                note = f.note,
            )
            val result = runCatching { container.loanRepo.save(draft) }
            val failure = result.exceptionOrNull()
            if (failure != null) {
                val msg = if (failure is com.shohan.khatiyan.utilities.FinanceValidationException) failure.messageBn
                else "সংরক্ষণ করা যায়নি — ইনপুট দেখে নিন।"
                _state.value = _state.value.copy(error = msg)
                return@launch
            }
            onSaved()
        }
    }
}

@Composable
fun LoanEditScreen(navController: NavController, container: AppContainer, loanId: Long?) {
    val vm: LoanEditViewModel = viewModel(factory = containerFactory { LoanEditViewModel(it, loanId) })
    val f by vm.state.collectAsStateWithLifecycle()
    val preview = remember(f) { vm.preview() }

    KhatiyanScaffold(
        title = if (loanId == null) "নতুন লোন" else "লোন সম্পাদনা",
        onBack = { navController.popBackStack() },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard {
                Text("লোনের তথ্য", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                KhatiyanTextField(f.institution, { v -> vm.update { it.copy(institution = v, error = null) } }, "প্রতিষ্ঠানের নাম *")
                Spacer(Modifier.height(8.dp))
                KhatiyanTextField(f.loanName, { v -> vm.update { it.copy(loanName = v) } }, "লোনের নাম (ঐচ্ছিক)")
                Spacer(Modifier.height(8.dp))
                AmountInput(f.principal, { v -> vm.update { it.copy(principal = v, error = null) } }, "মূল টাকার অঙ্ক *", "৳")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField("তোলার তারিখ", f.startIso, { v -> vm.update { it.copy(startIso = v) } }, Modifier.weight(1f))
                    KhatiyanTextField(
                        f.rate,
                        { v -> vm.update { it.copy(rate = v.filter { c -> c.isDigit() || c == '.' }.take(7)) } },
                        "বার্ষিক সুদ %",
                        modifier = Modifier.weight(1f),
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                    )
                }
                Spacer(Modifier.height(8.dp))
                AmountInput(f.fee, { v -> vm.update { it.copy(fee = v) } }, "প্রসেসিং ফি (ঐচ্ছিক)", "৳")
            }

            AppCard {
                Text("কিস্তির হিসাব", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                AmountInput(
                    f.totalPayable,
                    { v -> vm.update { it.copy(totalPayable = v) } },
                    "মোট পরিশোধযোগ্য",
                    "৳",
                    helper = preview?.let { "স্বয়ংক্রিয় হিসাব: ${Money.format(it.first)} — ফাঁকা রাখলে এটাই হবে" },
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmountInput(f.installment, { v -> vm.update { it.copy(installment = v) } }, "প্রতি কিস্তি", "৳", modifier = Modifier.weight(1f))
                    KhatiyanTextField(
                        f.count,
                        { v -> vm.update { it.copy(count = v.filter { c -> c.isDigit() }.take(3)) } },
                        "কিস্তির সংখ্যা",
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField("প্রথম কিস্তির তারিখ *", f.firstDueIso, { v -> vm.update { it.copy(firstDueIso = v) } }, Modifier.weight(1f))
                    DateField("মেয়াদ শেষ (ঐচ্ছিক)", f.maturityIso, { v -> vm.update { it.copy(maturityIso = v) } }, Modifier.weight(1f), allowClear = true, onClear = { vm.update { it.copy(maturityIso = "") } })
                }
                preview?.let { (total, per, count) ->
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), MaterialTheme.shapes.medium)
                            .padding(12.dp),
                    ) {
                        Text(
                            "সময়সূচি — মোট ${Money.format(total)}, ${BnText.toBnDigits(count.toString())}টি কিস্তি (প্রতিটি প্রায় ${Money.format(per)})",
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
                "সংরক্ষণ করুন",
                onClick = { vm.save { navController.popBackStack() } },
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                enabled = f.loaded,
            )
        }
    }
}

// ---------- Detail screen -------------------------------------------------------------------

class LoanDetailViewModel(private val container: AppContainer, private val loanId: Long) : ViewModel() {
    private val _state = MutableStateFlow<LoanRepository.LoanDetail?>(null)
    val state = _state
    val events = MutableSharedFlow<String>(extraBufferCapacity = 4)

    init {
        refresh()
        DataBus.changes.onEach { refresh() }.launchIn(viewModelScope)
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = runCatching { container.loanRepo.loadDetail(loanId) }.getOrNull()
        }
    }

    suspend fun recordPayment(amount: Long, method: String, date: String, note: String, allow: Boolean): com.shohan.khatiyan.domain.finance.OverpaymentException? =
        try {
            container.loanRepo.recordPayment(loanId, date, amount, method, note, allow)
            events.emit("${Money.format(amount)} কিস্তি পরিশোধ যোগ হয়েছে")
            null
        } catch (e: com.shohan.khatiyan.domain.finance.OverpaymentException) {
            if (allow) null else e
        }

    suspend fun updatePayment(paymentId: Long, amount: Long, method: String, date: String, note: String, allow: Boolean): com.shohan.khatiyan.domain.finance.OverpaymentException? =
        try {
            container.loanRepo.updatePayment(loanId, paymentId, date, amount, method, note, allow)
            events.emit("পরিশোধ হালনাগাদ হয়েছে")
            null
        } catch (e: com.shohan.khatiyan.domain.finance.OverpaymentException) {
            if (allow) null else e
        }

    fun deletePayment(paymentId: Long) {
        viewModelScope.launch {
            container.loanRepo.deletePayment(loanId, paymentId)
            events.emit("পরিশোধের এন্ট্রি মুছে ফেলা হয়েছে")
        }
    }

    fun setArchived(archived: Boolean) {
        viewModelScope.launch { container.loanRepo.setArchived(loanId, archived) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            container.loanRepo.deleteLoan(loanId)
            events.emit("লোন ও তার সব কিস্তির রেকর্ড মুছে ফেলা হয়েছে")
            onDone()
        }
    }
}

@Composable
fun LoanDetailScreen(navController: NavController, container: AppContainer, loanId: Long) {
    val vm: LoanDetailViewModel = viewModel(factory = containerFactory { LoanDetailViewModel(it, loanId) })
    val detail by vm.state.collectAsStateWithLifecycle()
    var showPayment by remember { mutableStateOf(false) }
    var editPayment by remember { mutableStateOf<LoanPaymentEntity?>(null) }
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
                val ok = runCatching {
                    CsvExporter.writeTo(context, uri, CsvExporter.loanLedgerCsv(container.db, loanId))
                }.isSuccess
                snackbar.showSnackbar(if (ok) "CSV সেভ হয়েছে" else "CSV সেভ করা যায়নি")
            }
        }
    }

    KhatiyanScaffold(
        title = detail?.loan?.loanName?.ifBlank { detail?.loan?.institution } ?: "লোন",
        onBack = { navController.popBackStack() },
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = { navController.navigate(Routes.loanEdit(loanId)) }) {
                Icon(Icons.Outlined.Edit, "সম্পাদনা")
            }
            IconButton(onClick = { csvLauncher.launch("khatiyan-loan-$loanId.csv") }) {
                Icon(Icons.Outlined.Download, "CSV নামান")
            }
        },
    ) { padding ->
        val d = detail
        if (d == null) {
            Box(Modifier.padding(padding))
            return@KhatiyanScaffold
        }
        val loan = d.loan
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard {
                Text(loan.institution, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(loan.loanName.ifBlank { loan.institution }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "মূল ${Money.format(loan.principalPaisa)} · সুদ ${if (loan.annualRateBps > 0) LoanMath.ratePercentLabel(loan.annualRateBps) + "%" else "নেই"} · ${Frequency.fromName(loan.frequency).labelBn}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (loan.note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("নোট: ${loan.note}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val total = loan.totalPayablePaisa
            val paid = d.totalPaidPaisa
            val remaining = d.remainingPaisa
            val progress = if (total > 0) (paid.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("বর্তমান বাকি", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    }
                    com.shohan.khatiyan.ui.charts.ProgressRing(
                        fraction = progress,
                        color = KhatiyanBrand.Primary,
                        modifier = Modifier.size(72.dp),
                        centerText = "${BnText.toBnDigits((progress * 100).toInt().toString())}%",
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    LSummary("মোট পরিশোধযোগ্য", Money.format(total))
                    LSummary("পরিশোধিত", Money.format(paid), KhatiyanBrand.Success)
                }
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.Button(
                    onClick = { showPayment = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) { Text("কিস্তি পরিশোধ করুন") }
            }

            SectionTitle("কিস্তির সময়সূচি")
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
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
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
                            Text(
                                i.dueDate?.let { BnDates.formatShort(it) } ?: "তারিহবিহীন",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                if (i.paidPaisa > 0 && i.paidPaisa < i.amountPaisa) "আংশিক: ${Money.format(i.paidPaisa)} দেওয়া হয়েছে" else "কিস্তি ${Money.format(i.amountPaisa)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusPill(label, tone)
                        Spacer(Modifier.width(8.dp))
                        Text(Money.format(i.amountPaisa), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    }
                }
            }

            SectionTitle("পরিশোধের ইতিহাস")
            if (d.payments.isEmpty()) {
                AppCard { Text("এখনও কোনো পরিশোধ হয়নি।", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                AppCard(contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp)) {
                    d.payments.forEach { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 6.dp),
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
                                Icon(Icons.Outlined.DeleteOutline, "মুছুন", tint = MaterialTheme.colorScheme.error, Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            SecondaryButton(
                text = if (loan.archived) "আর্কাইভ থেকে ফিরিয়ে আনুন" else "লোন আর্কাইভ করুন",
                onClick = { vm.setArchived(!loan.archived) },
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.material3.TextButton(
                onClick = { showDelete = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("লোন ও সব রেকর্ড মুছে ফেলুন", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
        }
    }

    PaymentEntryDialog(
        visible = showPayment,
        title = "লোনের কিস্তি পরিশোধ",
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
            title = "লোন মুছে ফেলবেন?",
            message = "লোনটি, তার কিস্তির সময়সূচি ও সব পরিশোধের রেকর্ড মুছে যাবে। মুছে ফেলার আগে ব্যাকআপ রাখা বুদ্ধিমানের কাজ।",
            confirmLabel = "হ্যাঁ, মুছে দিন",
            danger = true,
            onConfirm = { showDelete = false; vm.delete { navController.popBackStack() } },
            onDismiss = { showDelete = false },
        )
    }
}

@Composable
private fun LSummary(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
    }
}

/** Payment dialog variant with pre-filled values for edits. */
@Composable
fun PaymentEntryEditDialog(
    visible: Boolean,
    title: String,
    symbol: String,
    initialAmount: String,
    initialNote: String,
    initialDate: String,
    onDismiss: () -> Unit,
    submit: suspend (Long, String, String, String, Boolean) -> com.shohan.khatiyan.domain.finance.OverpaymentException?,
) {
    if (!visible) return
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf(initialAmount) }
    var note by remember { mutableStateOf(initialNote) }
    var date by remember { mutableStateOf(initialDate) }
    var method by remember { mutableStateOf(com.shohan.khatiyan.domain.model.PaymentMethod.CASH) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingExcess by remember { mutableStateOf<Long?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                AmountInput(amount, { amount = it; error = null; pendingExcess = null }, "টাকার পরিমাণ", symbol, error = error)
                Spacer(Modifier.height(8.dp))
                ChoiceChipsRow(
                    options = com.shohan.khatiyan.domain.model.PaymentMethod.entries.map { it.labelBn },
                    selected = method.labelBn,
                    onSelect = { label -> method = com.shohan.khatiyan.domain.model.PaymentMethod.entries.first { it.labelBn == label } },
                )
                Spacer(Modifier.height(4.dp))
                DateField("তারিখ", date, { date = it })
                KhatiyanTextField(note, { note = it }, "নোট (ঐচ্ছিক)")
                pendingExcess?.let {
                    Spacer(Modifier.height(8.dp))
                    com.shohan.khatiyan.ui.components.WarningBanner(
                        "বাকির তুলনায় ${Money.format(it, symbol)} বেশি — নিশ্চিত করলে অতিরিক্ত অংশ অগ্রিম জমা থাকবে।",
                    )
                }
            }
        },
        confirmButton = {
            if (pendingExcess != null) {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val paisa = Money.parse(amount) ?: return@TextButton
                        scope.launch {
                            val out = submit(paisa, method.name, date, note, true)
                            if (out == null) onDismiss() else error = "সম্পাদনা করা যায়নি।"
                        }
                    },
                ) { Text("হ্যাঁ, সংরক্ষণ করুন", color = MaterialTheme.colorScheme.primary) }
            } else {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val paisa = Money.parse(amount)
                        if (paisa == null) { error = "টাকার সঠিক পরিমাণ লিখুন।"; return@TextButton }
                        scope.launch {
                            val out = submit(paisa, method.name, date, note, false)
                            if (out != null) pendingExcess = out.excessPaisa else onDismiss()
                        }
                    },
                ) { Text("সংরক্ষণ করুন", color = MaterialTheme.colorScheme.primary) }
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("বাতিল") } },
        shape = MaterialTheme.shapes.extraLarge,
    )
}
