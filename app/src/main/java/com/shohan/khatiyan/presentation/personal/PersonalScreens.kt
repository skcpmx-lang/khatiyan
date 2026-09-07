package com.shohan.khatiyan.presentation.personal

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.shohan.khatiyan.data.local.entity.PersonEntity
import com.shohan.khatiyan.data.local.entity.PersonalDebtEntity
import com.shohan.khatiyan.data.local.query.DebtRow
import com.shohan.khatiyan.data.local.query.PersonRow
import com.shohan.khatiyan.di.AppContainer
import com.shohan.khatiyan.domain.finance.OverpaymentException
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

class PeopleListViewModel(private val container: AppContainer) : ViewModel() {
    data class UiState(
        val query: String = "",
        val hideArchived: Boolean = true,
        val rows: List<PersonRow> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state

    init {
        rebuild()
        DataBus.changes.onEach { rebuild() }.launchIn(viewModelScope)
    }

    fun setQuery(q: String) { _state.value = _state.value.copy(query = q); rebuild() }
    fun toggleArchived() { _state.value = _state.value.copy(hideArchived = !_state.value.hideArchived); rebuild() }

    private fun rebuild() {
        container.personalRepo.observePeople(_state.value.query.trim(), _state.value.hideArchived)
            .onEach { rows -> _state.value = _state.value.copy(rows = rows) }
            .launchIn(viewModelScope)
    }
}

@Composable
fun PeopleTabScaffold(navController: NavController, container: AppContainer) {
    KhatiyanScaffold(
        title = "ব্যক্তিগত ধার",
        onBack = { navController.popBackStack() },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Routes.personEdit()) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Outlined.Add, "নতুন মানুষ") }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            PeopleListContent(navController, container)
        }
    }
}

@Composable
fun PeopleListContent(navController: NavController, container: AppContainer, modifier: Modifier = Modifier) {
    val vm: PeopleListViewModel = viewModel(factory = containerFactory { PeopleListViewModel(it) })
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = state.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("নাম খুঁজুন…") },
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
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Login, null) },
        )
        if (state.rows.isEmpty()) {
            EmptyState(
                title = if (state.query.isNotBlank()) "এই নামে কাউকে পাওয়া যায়নি" else "তালিকা এখন খালি",
                subtitle = "বন্ধু, আত্মীয় বা পরিচয়ের কারো কাছ থেকে নেওয়া টাকা এখানে লিপিবদ্ধ রাখুন।",
                actionLabel = if (state.query.isBlank()) "+ নতুন মানুষ" else null,
                onAction = { navController.navigate(Routes.personEdit()) },
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.rows.forEach { row ->
                    PersonRowCard(row) { navController.navigate(Routes.personDetail(row.id)) }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun PersonRowCard(row: PersonRow, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    row.name.take(1),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    row.relationship + if (row.openDebtCount > 0) " · ${BnText.toBnDigits(row.openDebtCount.toString())}টি খোলা ধার" else " · সব শোধ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (row.remainingPaisa > 0) "নেওয়া বাকি" else "ফেরত দেওয়া বাকি",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val amt = if (row.remainingPaisa >= 0) row.remainingPaisa else -row.remainingPaisa
                Text(
                    Money.format(amt),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (row.remainingPaisa > 0) KhatiyanBrand.Danger else KhatiyanBrand.Success,
                    maxLines = 1,
                )
                if (row.overdueCount > 0) {
                    StatusPill("${BnText.toBnDigits(row.overdueCount.toString())}টি ওভারডিউ", PillTone.DANGER)
                }
            }
        }
    }
}

// ---------- person editor -------------------------------------------------------------------

class PersonEditViewModel(private val container: AppContainer, private val personId: Long?) : ViewModel() {
    data class Form(
        val name: String = "",
        val relationship: String = "বন্ধু",
        val phone: String = "",
        val note: String = "",
        val archived: Boolean = false,
        val error: String? = null,
        val loaded: Boolean = false,
    )

    private val _state = MutableStateFlow(Form())
    val state = _state

    init {
        viewModelScope.launch {
            if (personId != null) {
                container.db.personalDao().getPerson(personId)?.let { p ->
                    _state.value = _state.value.copy(
                        name = p.name,
                        relationship = p.relationship,
                        phone = p.phone,
                        note = p.note,
                        archived = p.archived,
                    )
                }
            }
            _state.value = _state.value.copy(loaded = true)
        }
    }

    fun update(transform: (Form) -> Form) { _state.value = transform(_state.value) }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val f = _state.value
            if (f.name.isBlank()) {
                _state.value = f.copy(error = "নাম লিখুন।")
                return@launch
            }
            val result = runCatching {
                container.personalRepo.savePerson(
                    PersonEntity(
                        id = personId ?: 0L,
                        name = f.name.trim(),
                        relationship = f.relationship,
                        phone = f.phone.trim(),
                        note = f.note.trim(),
                        archived = f.archived,
                    ),
                )
            }
            if (result.isFailure) {
                _state.value = _state.value.copy(error = "সংরক্ষণ করা যায়নি — আবার চেষ্টা করুন।")
                return@launch
            }
            onSaved()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = personId ?: return
        viewModelScope.launch {
            container.personalRepo.deletePerson(id)
            onDone()
        }
    }

    fun toggleArchive(onDone: () -> Unit) {
        val id = personId ?: return
        viewModelScope.launch {
            container.personalRepo.setArchived(id, !state.value.archived)
            onDone()
        }
    }
}

@Composable
fun PersonEditScreen(navController: NavController, container: AppContainer, personId: Long?) {
    val vm: PersonEditViewModel = viewModel(factory = containerFactory { PersonEditViewModel(it, personId) })
    val f by vm.state.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }

    KhatiyanScaffold(
        title = if (personId == null) "নতুন মানুষ" else "সম্পাদনা",
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
                Text("পরিচয়", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                KhatiyanTextField(f.name, { v -> vm.update { it.copy(name = v, error = null) } }, "নাম *", error = f.error)
                Spacer(Modifier.height(10.dp))
                Text("সম্পর্ক", style = MaterialTheme.typography.titleSmall)
                ChoiceChipsRow(
                    options = com.shohan.khatiyan.data.repository.PersonalRepository.RELATIONSHIPS,
                    selected = f.relationship,
                    onSelect = { label -> vm.update { it.copy(relationship = label) } },
                )
                Spacer(Modifier.height(8.dp))
                KhatiyanTextField(
                    f.phone,
                    { v -> vm.update { it.copy(phone = v.filter { c -> c.isDigit() || c == '+' || c == '-' || c == ' ' }.take(18)) } },
                    "ফোন (ঐচ্ছিক)",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
                )
                Spacer(Modifier.height(8.dp))
                KhatiyanTextField(f.note, { v -> vm.update { it.copy(note = v) } }, "নোট (ঐচ্ছিক)", singleLine = false, minLines = 2)
            }
            PrimaryButton(
                "সংরক্ষণ করুন",
                onClick = { vm.save { navController.popBackStack() } },
                modifier = Modifier.fillMaxWidth(),
                enabled = f.loaded,
            )
            if (personId != null) {
                SecondaryButton(
                    text = if (f.archived) "আর্কাইভ থেকে ফিরিয়ে আনুন" else "আর্কাইভ করুন",
                    onClick = { vm.toggleArchive { navController.popBackStack() } },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { showDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("এই মানুষ ও তার সব ধারের রেকর্ড মুছুন", color = MaterialTheme.colorScheme.error)
                }
                if (showDelete) {
                    ConfirmDialog(
                        title = "সব মুছে ফেলবেন?",
                        message = "“${f.name}”-এর সব ধার ও পরিশোধের রেকর্ড মুছে যাবে। এটি ফেরানো যাবে না (ব্যাকআপ থাকলে বাদে)।",
                        confirmLabel = "হ্যাঁ, মুছে দিন",
                        danger = true,
                        onConfirm = { showDelete = false; vm.delete { navController.popBackStack() } },
                        onDismiss = { showDelete = false },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------- person detail ---------------------------------------------------------------------

class PersonDetailViewModel(private val container: AppContainer, private val personId: Long) : ViewModel() {
    private val _state = MutableStateFlow<com.shohan.khatiyan.data.repository.PersonalRepository.PersonDetail?>(null)
    val state = _state
    val events = MutableSharedFlow<String>(extraBufferCapacity = 4)

    init {
        refresh()
        DataBus.changes.onEach { refresh() }.launchIn(viewModelScope)
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = runCatching { container.personalRepo.loadDetail(personId) }.getOrNull()
        }
    }

    suspend fun saveDebt(debt: PersonalDebtEntity) {
        container.personalRepo.saveDebt(debt)
        events.emit(if (debt.id == 0L) "নতুন ধার যোগ হয়েছে" else "ধারের রেকর্ড হালনাগাদ হয়েছে")
    }

    fun deleteDebt(debtId: Long) {
        viewModelScope.launch {
            container.personalRepo.deleteDebt(debtId)
            events.emit("ধারের রেকর্ড মুছে ফেলা হয়েছে")
        }
    }

    suspend fun recordRepayment(debt: DebtRow, amount: Long, method: String, date: String, note: String, allow: Boolean): OverpaymentException? =
        try {
            container.personalRepo.recordRepayment(debt.id, date, amount, method, note, allow)
            events.emit("${Money.format(amount)} ফেরত দেওয়া হিসেবে যোগ হয়েছে")
            null
        } catch (e: OverpaymentException) {
            if (allow) null else e
        }
}

@Composable
fun PersonDetailScreen(navController: NavController, container: AppContainer, personId: Long) {
    val vm: PersonDetailViewModel = viewModel(factory = containerFactory { PersonDetailViewModel(it, personId) })
    val detail by vm.state.collectAsStateWithLifecycle()
    var debtDialog by remember { mutableStateOf<PersonalDebtEntity?>(null) }
    var newDebt by remember { mutableStateOf(false) }
    var repayFor by remember { mutableStateOf<DebtRow?>(null) }
    var deleteDebt by remember { mutableStateOf<DebtRow?>(null) }
    val snackbar = androidx.compose.material3.SnackbarHostState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    val csvLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = runCatching { CsvExporter.writeTo(context, uri, CsvExporter.personalLedgerCsv(container.db, personId)) }.isSuccess
                snackbar.showSnackbar(if (ok) "CSV সেভ হয়েছে" else "CSV সেভ করা যায়নি")
            }
        }
    }

    KhatiyanScaffold(
        title = detail?.person?.name ?: "ধারের হিসাব",
        onBack = { navController.popBackStack() },
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = { navController.navigate(Routes.personEdit(personId)) }) { Icon(Icons.Outlined.Edit, "সম্পাদনা") }
            IconButton(onClick = { csvLauncher.launch("khatiyan-person-$personId.csv") }) { Icon(Icons.Outlined.Download, "CSV নামান") }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { newDebt = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Outlined.Add, "নতুন ধার") }
        },
    ) { padding ->
        val d = detail
        if (d == null) {
            Box(Modifier.padding(padding))
            return@KhatiyanScaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard {
                Text("${d.person.relationship} · ${BnDates.formatLong(BnDates.today())}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(d.person.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (d.person.phone.isNotBlank()) {
                    Text(d.person.phone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (d.person.note.isNotBlank()) {
                    Text(d.person.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    PSummary("মোট নেওয়া", Money.format(d.borrowedPaisa), MaterialTheme.colorScheme.onSurface)
                    PSummary("মোট ফেরত", Money.format(d.repaidPaisa), KhatiyanBrand.Success)
                    PSummary(
                        if (d.remainingPaisa >= 0) "ফেরত দেন" else "ফেরত পান",
                        Money.format(kotlin.math.abs(d.remainingPaisa)),
                        if (d.remainingPaisa > 0) KhatiyanBrand.Danger else KhatiyanBrand.Info,
                    )
                }
            }

            SectionTitle("ধারের খাতা")
            if (d.debts.isEmpty()) {
                AppCard {
                    Text("এখনও কোনো ধারের এন্ট্রি নেই। নিচের + থেকে যোগ করুন।", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                d.debts.forEach { debt ->
                    DebtCard(
                        debt = debt,
                        onEdit = { debtDialog = PersonalDebtEntity(id = debt.id, personId = debt.personId, borrowedIso = debt.borrowedIso, amountPaisa = debt.amountPaisa, expectedReturnIso = debt.expectedReturnIso, note = debt.note) },
                        onDelete = { deleteDebt = debt },
                        onRepay = { repayFor = debt },
                    )
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }

    if (newDebt) {
        DebtEditDialog(
            title = "নতুন ধার",
            personId = personId,
            initial = null,
            onDismiss = { newDebt = false },
            onSave = { entity -> newDebt = false; scope.launch { vm.saveDebt(entity) } },
        )
    }
    debtDialog?.let { existing ->
        DebtEditDialog(
            title = "ধারের এন্ট্রি সম্পাদনা",
            personId = personId,
            initial = existing,
            onDismiss = { debtDialog = null },
            onSave = { entity -> debtDialog = null; scope.launch { vm.saveDebt(entity) } },
        )
    }
    repayFor?.let { debt ->
        PaymentEntryDialog(
            visible = true,
            title = "ফেরত দিন — ${debt.personName}",
            symbol = "৳",
            onDismiss = { repayFor = null },
            submit = { amount, method, date, note, allow -> vm.recordRepayment(debt, amount, method, date, note, allow) },
        )
    }
    deleteDebt?.let { debt ->
        ConfirmDialog(
            title = "ধারের এন্ট্রি মুছে ফেলবেন?",
            message = "${BnDates.formatShort(BnDates.fromIso(debt.borrowedIso) ?: BnDates.today())} এর ${Money.format(debt.amountPaisa)} টাকার এন্ট্রি ও তার পরিশোধের রেকর্ড মুছে যাবে।",
            confirmLabel = "মুছে দিন",
            danger = true,
            onConfirm = { vm.deleteDebt(debt.id); deleteDebt = null },
            onDismiss = { deleteDebt = null },
        )
    }
}

@Composable
private fun PSummary(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
    }
}

@Composable
private fun DebtCard(
    debt: DebtRow,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRepay: () -> Unit,
) {
    val today = remember { BnDates.today() }
    val expected = BnDates.fromIso(debt.expectedReturnIso)
    val over = expected != null && debt.remainingPaisa > 0 && expected.isBefore(today)
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("নেওয়া ${Money.format(debt.amountPaisa)}", style = MaterialTheme.typography.titleMedium)
                Text(
                    BnDates.formatShort(BnDates.fromIso(debt.borrowedIso) ?: today) + if (debt.note.isNotBlank()) " · ${debt.note}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (debt.remainingPaisa > 0) {
                    Text("বাকি ${Money.format(debt.remainingPaisa)}", style = MaterialTheme.typography.titleMedium, color = KhatiyanBrand.Danger)
                    Text("ফেরত ${Money.format(debt.repaidPaisa)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("শোধ হয়েছে ✓", style = MaterialTheme.typography.titleMedium, color = KhatiyanBrand.Success)
                }
            }
        }
        if (expected != null) {
            Spacer(Modifier.height(6.dp))
            StatusPill(
                if (debt.remainingPaisa > 0) "ফেরতের তারিখ: ${BnDates.relativeCompact(expected, today)}" else "নির্ধারিত সময়েই শোধ ✓",
                when {
                    over -> PillTone.DANGER
                    debt.remainingPaisa > 0 && expected == today -> PillTone.WARNING
                    else -> PillTone.INFO
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                TextButton(onClick = onEdit) { Text("সম্পাদনা") }
                TextButton(onClick = onDelete) { Text("মুছুন", color = MaterialTheme.colorScheme.error) }
            }
            if (debt.remainingPaisa > 0) {
                androidx.compose.material3.Button(onClick = onRepay, shape = MaterialTheme.shapes.medium) { Text("ফেরত দিন") }
            }
        }
    }
}

@Composable
private fun DebtEditDialog(
    title: String,
    personId: Long,
    initial: PersonalDebtEntity?,
    onDismiss: () -> Unit,
    onSave: (PersonalDebtEntity) -> Unit,
) {
    var amount by remember { mutableStateOf(initial?.let { Money.formatPlain(it.amountPaisa) } ?: "") }
    var borrowed by remember { mutableStateOf(initial?.borrowedIso ?: BnDates.toIso(BnDates.today())) }
    var expected by remember { mutableStateOf(initial?.expectedReturnIso ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                AmountInput(amount, { v -> amount = v; error = null }, "টাকার পরিমাণ *", "৳", error = error)
                Spacer(Modifier.height(8.dp))
                DateField("নেওয়ার তারিখ", borrowed, { borrowed = it })
                Spacer(Modifier.height(4.dp))
                DateField(
                    "ফেরত দেওয়ার তারিখ (ঐচ্ছিক)",
                    expected,
                    { expected = it },
                    allowClear = true,
                    onClear = { expected = "" },
                )
                Spacer(Modifier.height(4.dp))
                KhatiyanTextField(note, { note = it }, "নোট (ঐচ্ছিক)")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val paisa = Money.parse(amount)
                    if (paisa == null) {
                        error = "টাকার সঠিক পরিমাণ লিখুন।"
                        return@TextButton
                    }
                    onSave(
                        PersonalDebtEntity(
                            id = initial?.id ?: 0L,
                            personId = initial?.personId ?: personId,
                            borrowedIso = borrowed,
                            amountPaisa = paisa,
                            expectedReturnIso = expected.ifBlank { null },
                            note = note.trim(),
                        ),
                    )
                },
            ) { Text("সংরক্ষণ", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } },
        shape = MaterialTheme.shapes.extraLarge,
    )
}
