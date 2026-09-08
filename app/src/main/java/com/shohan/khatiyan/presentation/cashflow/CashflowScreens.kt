package com.shohan.khatiyan.presentation.cashflow

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.shohan.khatiyan.core.BnDates
import com.shohan.khatiyan.core.Money
import com.shohan.khatiyan.data.local.entity.ExpenseEntity
import com.shohan.khatiyan.data.local.entity.IncomeEntity
import com.shohan.khatiyan.data.repository.CashflowRepository
import com.shohan.khatiyan.data.repository.LedgerMapper
import com.shohan.khatiyan.di.AppContainer
import com.shohan.khatiyan.domain.model.LedgerEntry
import com.shohan.khatiyan.domain.model.TxnType
import com.shohan.khatiyan.presentation.navigation.Routes
import com.shohan.khatiyan.ui.components.AmountInput
import com.shohan.khatiyan.ui.components.AppCard
import com.shohan.khatiyan.ui.components.DateField
import com.shohan.khatiyan.ui.components.ChoiceChipsRow
import com.shohan.khatiyan.ui.components.EmptyState
import com.shohan.khatiyan.ui.components.KhatiyanScaffold
import com.shohan.khatiyan.ui.components.SectionTitle
import com.shohan.khatiyan.ui.components.StatusPill
import com.shohan.khatiyan.ui.components.PillTone
import com.shohan.khatiyan.ui.theme.KhatiyanBrand
import com.shohan.khatiyan.utilities.BnText
import com.shohan.khatiyan.utilities.DataBus
import com.shohan.khatiyan.utilities.containerFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Range + sort + filter options shared by the three segments. */
object CashflowFilters {
    val RANGES = listOf("আজ", "এই সপ্তাহ", "এই মাস", "গত মাস", "এই বছর", "সব")

    fun rangeIso(label: String, today: java.time.LocalDate = BnDates.today()): Pair<String, String> {
        fun iso(d: java.time.LocalDate) = d.toString()
        return when (label) {
            "আজ" -> iso(today) to iso(today)
            "এই সপ্তাহ" -> {
                val start = today.minusDays((today.dayOfWeek.value - 1).toLong())
                iso(start) to iso(start.plusDays(6))
            }
            "গত মাস" -> {
                val first = today.withDayOfMonth(1).minusMonths(1)
                iso(first) to iso(first.withDayOfMonth(first.lengthOfMonth()))
            }
            "এই বছর" -> iso(today.withDayOfMonth(1)) to iso(today.withMonth(12).withDayOfMonth(31))
            "সব" -> "0000-01-01" to "9999-12-31"
            else -> {
                val first = today.withDayOfMonth(1)
                iso(first) to iso(first.withDayOfMonth(first.lengthOfMonth()))
            }
        }
    }
}

class CashflowTabViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val tab: Int = 0, // 0 income, 1 expense, 2 all
        val query: String = "",
        val category: String = "",
        val rangeLabel: String = "এই মাস",
        val sort: CashflowRepository.Sort = CashflowRepository.Sort.NEWEST,
        val typeFilter: String = "",
        val incomes: List<IncomeEntity> = emptyList(),
        val expenses: List<ExpenseEntity> = emptyList(),
        val entries: List<LedgerEntry> = emptyList(),
        val categories: List<String> = CashflowRepository.INCOME_CATEGORIES,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state

    init {
        rebuild()
        DataBus.changes.onEach { rebuild() }.launchIn(viewModelScope)
    }

    fun update(transform: (UiState) -> UiState) {
        _state.value = transform(_state.value)
        rebuild()
    }

    private fun rebuild() {
        val s = _state.value
        val (from, to) = CashflowFilters.rangeIso(s.rangeLabel)
        when (s.tab) {
            0 -> container.cashflowRepo.observeIncomes(s.query.trim(), s.category, from, to, s.sort)
                .onEach { rows -> _state.value = _state.value.copy(incomes = rows) }
                .launchIn(viewModelScope)
            1 -> container.cashflowRepo.observeExpenses(s.query.trim(), s.category, from, to, s.sort)
                .onEach { rows -> _state.value = _state.value.copy(expenses = rows) }
                .launchIn(viewModelScope)
            else -> viewModelScope.launch {
                val rows = container.db.ledgerDao().entriesBetween(from, to, s.typeFilter, 250, 0)
                _state.value = _state.value.copy(entries = LedgerMapper.toEntries(rows))
            }
        }
        viewModelScope.launch {
            val kind = if (s.tab == 1) "expense" else "income"
            val cats = container.cashflowRepo.categoriesFor(kind)
            _state.value = _state.value.copy(categories = cats)
        }
    }
}

@Composable
fun CashflowScreen(navController: NavController, container: AppContainer) {
    val vm: CashflowTabViewModel = viewModel(factory = containerFactory { CashflowTabViewModel(it) })
    val state by vm.state.collectAsStateWithLifecycle()

    KhatiyanScaffold(
        title = "লেনদেন",
        actions = { com.shohan.khatiyan.ui.components.SearchIconButton { navController.navigate(Routes.SEARCH) } },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (state.tab) {
                        0 -> navController.navigate(Routes.incomeEdit())
                        1 -> navController.navigate(Routes.expenseEdit())
                        else -> navController.navigate(Routes.incomeEdit())
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    if (state.tab == 1) Icons.Outlined.TrendingDown else Icons.Outlined.Add,
                    if (state.tab == 1) "নতুন ব্যয়" else "নতুন আয়",
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxWidth()) {
            TabRow(
                selectedTabIndex = state.tab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Tab(selected = state.tab == 0, onClick = { vm.update { it.copy(tab = 0, category = "") } }, text = { Text("আয়") })
                Tab(selected = state.tab == 1, onClick = { vm.update { it.copy(tab = 1, category = "") } }, text = { Text("ব্যয়") })
                Tab(selected = state.tab == 2, onClick = { vm.update { it.copy(tab = 2) } }, text = { Text("সব লেনদেন") })
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when (state.tab) { 0 -> "মোট আয়: " else -> "মোট ব্যয়: " } +
                        Money.format(
                            if (state.tab == 0) state.incomes.fold(0L) { a, i -> a + i.amountPaisa }
                            else state.expenses.fold(0L) { a, e -> a + e.amountPaisa },
                        ),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.tab == 0) KhatiyanBrand.Success else KhatiyanBrand.Danger,
                    modifier = Modifier.weight(1f),
                )
            }

            TextField(
                value = state.query,
                onValueChange = { v -> vm.update { it.copy(query = v) } },
                placeholder = { Text(if (state.tab == 2) "সব লেনদেনে খুঁজুন…" else "উৎস বা নোট খুঁজুন…") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                leadingIcon = { Icon(com.shohan.khatiyan.ui.icons.KhatiyanIcons.Search, null) },
            )

            Spacer(Modifier.height(4.dp))
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("সময়কাল", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChoiceChipsRow(
                    options = CashflowFilters.RANGES,
                    selected = state.rangeLabel,
                    onSelect = { label -> vm.update { it.copy(rangeLabel = label) } },
                )
                if (state.tab < 2) {
                    Text("ক্যাটাগরি", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChoiceChipsRow(
                        options = listOf("সব") + state.categories,
                        selected = state.category.ifBlank { "সব" },
                        onSelect = { label -> vm.update { it.copy(category = if (label == "সব") "" else label) } },
                    )
                } else {
                    Text("ধরন", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChoiceChipsRow(
                        options = listOf("সব") + TxnType.entries.map { it.labelBn },
                        selected = state.typeFilter.ifBlank { "সব" },
                        onSelect = { label ->
                            val key = if (label == "সব") "" else TxnType.entries.first { it.labelBn == label }.key
                            vm.update { it.copy(typeFilter = key) }
                        },
                    )
                }
                if (state.tab < 2) {
                    Text("সাজান", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChoiceChipsRow(
                        options = listOf("নতুন আগে", "পুরোনো আগে", "বেশি টাকা", "কম টাকা"),
                        selected = when (state.sort) {
                            CashflowRepository.Sort.NEWEST -> "নতুন আগে"
                            CashflowRepository.Sort.OLDEST -> "পুরোনো আগে"
                            CashflowRepository.Sort.HIGHEST -> "বেশি টাকা"
                            CashflowRepository.Sort.LOWEST -> "কম টাকা"
                        },
                        onSelect = { label ->
                            val sort = when (label) {
                                "পুরোনো আগে" -> CashflowRepository.Sort.OLDEST
                                "বেশি টাকা" -> CashflowRepository.Sort.HIGHEST
                                "কম টাকা" -> CashflowRepository.Sort.LOWEST
                                else -> CashflowRepository.Sort.NEWEST
                            }
                            vm.update { it.copy(sort = sort) }
                        },
                    )
                }
            }

            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (state.tab) {
                    0 -> {
                        if (state.incomes.isEmpty()) {
                            item {
                                EmptyState(
                                    "এই সময়ের মধ্যে কোনো আয় নেই",
                                    "বেতন, ব্যবসা বা যেকোনো আয় নিচের + থেকে যোগ করুন।",
                                )
                            }
                        }
                        items(state.incomes, key = { "in" + it.id }) { row ->
                            EntryCard(
                                title = row.source.ifBlank { row.category },
                                subtitle = row.category + if (row.note.isNotBlank()) " · ${row.note}" else "",
                                dateIso = row.dateIso,
                                amountPaisa = row.amountPaisa,
                                income = true,
                                onClick = { navController.navigate(Routes.incomeEdit(row.id)) },
                            )
                        }
                    }
                    1 -> {
                        if (state.expenses.isEmpty()) {
                            item {
                                EmptyState(
                                    "এই সময়ের মধ্যে কোনো ব্যয় নেই",
                                    "বাজার, যাতায়াত, বিল — নিচের + থেকে নতুন ব্যয় যোগ করুন।",
                                )
                            }
                        }
                        items(state.expenses, key = { "ex" + it.id }) { row ->
                            EntryCard(
                                title = row.category,
                                subtitle = (if (row.place.isNotBlank()) row.place else "") + if (row.note.isNotBlank()) " · ${row.note}" else "",
                                dateIso = row.dateIso,
                                amountPaisa = row.amountPaisa,
                                income = false,
                                onClick = { navController.navigate(Routes.expenseEdit(row.id)) },
                            )
                        }
                    }
                    else -> {
                        if (state.entries.isEmpty()) {
                            item { EmptyState("কোনো লেনদেন নেই", "এই সময়ের মধ্যে এই ধরনের কোনো লেনদেন নেই।") }
                        }
                        items(state.entries, key = { it.key }) { entry ->
                            AllLedgerCard(entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryCard(
    title: String,
    subtitle: String,
    dateIso: String,
    amountPaisa: Long,
    income: Boolean,
    onClick: () -> Unit,
) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        if (income) KhatiyanBrand.SuccessBg else KhatiyanBrand.DangerBg,
                        MaterialTheme.shapes.medium,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (income) Icons.Outlined.TrendingUp else Icons.Outlined.TrendingDown,
                    null,
                    tint = if (income) KhatiyanBrand.Success else KhatiyanBrand.Danger,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle.ifBlank { BnDates.formatShort(BnDates.fromIso(dateIso) ?: BnDates.today()) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (income) "+ " else "− ") + Money.format(amountPaisa),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (income) KhatiyanBrand.Success else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    BnDates.relativeCompact(BnDates.fromIso(dateIso) ?: BnDates.today()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AllLedgerCard(entry: LedgerEntry) {
    val type = TxnType.entries.firstOrNull { it.key == entry.typeKey }
    val incomeSide = entry.typeKey == "income" || entry.typeKey == "personal_borrow" || entry.typeKey == "shop_credit"
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${type?.labelBn ?: ""} · ${BnDates.formatShort(BnDates.fromIso(entry.dateIso) ?: BnDates.today())}" +
                        if (entry.subtitle.isNotBlank()) " · ${entry.subtitle}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.note.isNotBlank()) {
                    Text(
                        entry.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Money.format(entry.amountPaisa),
                    style = MaterialTheme.typography.titleMedium,
                    color = when (entry.typeKey) {
                        "shop_payment", "loan_payment", "emi_payment", "personal_repay" -> KhatiyanBrand.Success
                        "expense" -> KhatiyanBrand.Danger
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                StatusPill(
                    type?.labelBn ?: "",
                    when (entry.typeKey) {
                        "shop_credit", "personal_borrow", "expense" -> PillTone.WARNING
                        "income" -> PillTone.SUCCESS
                        else -> PillTone.NEUTRAL
                    },
                )
            }
        }
    }
}

// ---------- editors ----------------------------------------------------------------------------

class CashEntryEditViewModel(
    private val container: AppContainer,
    private val income: Boolean,
    private val entryId: Long?,
) : ViewModel() {

    data class Form(
        val amount: String = "",
        val dateIso: String = BnDates.toIso(BnDates.today()),
        val source: String = "",
        val place: String = "",
        val category: String = "",
        val note: String = "",
        val error: String? = null,
        val loaded: Boolean = false,
        val categories: List<String> = emptyList(),
    )

    private val _state = MutableStateFlow(Form())
    val state = _state

    init {
        viewModelScope.launch {
            val cats = container.cashflowRepo.categoriesFor(if (income) "income" else "expense")
            var f = _state.value.copy(categories = cats, loaded = true)
            if (entryId != null) {
                if (income) {
                    container.cashflowRepo.getIncome(entryId)?.let {
                        f = f.copy(
                            amount = Money.formatPlain(it.amountPaisa),
                            dateIso = it.dateIso,
                            source = it.source,
                            category = it.category,
                            note = it.note,
                        )
                    }
                } else {
                    container.cashflowRepo.getExpense(entryId)?.let {
                        f = f.copy(
                            amount = Money.formatPlain(it.amountPaisa),
                            dateIso = it.dateIso,
                            place = it.place,
                            category = it.category,
                            note = it.note,
                        )
                    }
                }
            } else {
                f = f.copy(category = cats.firstOrNull().orEmpty())
            }
            _state.value = f
        }
    }

    fun update(transform: (Form) -> Form) { _state.value = transform(_state.value) }

    fun addCategory(name: String) {
        viewModelScope.launch {
            runCatching { container.cashflowRepo.addCategory(if (income) "income" else "expense", name) }
            val cats = container.cashflowRepo.categoriesFor(if (income) "income" else "expense")
            _state.value = _state.value.copy(categories = cats)
        }
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val f = _state.value
            val amount = Money.parse(f.amount)
            if (amount == null) {
                _state.value = f.copy(error = "টাকার সঠিক পরিমাণ লিখুন।")
                return@launch
            }
            val category = f.category.ifBlank { f.categories.firstOrNull() ?: if (income) "অন্যান্য" else "অন্যান্য" }
            val result = runCatching {
                if (income) {
                    container.cashflowRepo.saveIncome(
                        IncomeEntity(id = entryId ?: 0L, dateIso = f.dateIso, amountPaisa = amount, source = f.source.trim(), category = category, note = f.note.trim()),
                    )
                } else {
                    container.cashflowRepo.saveExpense(
                        ExpenseEntity(id = entryId ?: 0L, dateIso = f.dateIso, amountPaisa = amount, category = category, place = f.place.trim(), note = f.note.trim()),
                    )
                }
            }
            if (result.isFailure) {
                _state.value = _state.value.copy(error = "সংরক্ষণ করা যায়নি। আবার চেষ্টা করুন।")
                return@launch
            }
            onSaved()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = entryId ?: return
        viewModelScope.launch {
            if (income) container.cashflowRepo.deleteIncome(id) else container.cashflowRepo.deleteExpense(id)
            onDone()
        }
    }
}

@Composable
fun IncomeEditScreen(navController: NavController, container: AppContainer, entryId: Long?) {
    CashEntryEditor(navController, container, entryId, income = true)
}

@Composable
fun ExpenseEditScreen(navController: NavController, container: AppContainer, entryId: Long?) {
    CashEntryEditor(navController, container, entryId, income = false)
}

@Composable
private fun CashEntryEditor(
    navController: NavController,
    container: AppContainer,
    entryId: Long?,
    income: Boolean,
) {
    val vm: CashEntryEditViewModel = viewModel(factory = containerFactory { CashEntryEditViewModel(it, income, entryId) })
    val f by vm.state.collectAsStateWithLifecycle()
    var showAddCat by remember { mutableStateOf(false) }
    var catName by remember { mutableStateOf("") }
    var showDelete by remember { mutableStateOf(false) }

    KhatiyanScaffold(
        title = (if (income) "আয়" else "ব্যয়") + if (entryId == null) " — নতুন" else " — সম্পাদনা",
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
                AmountInput(
                    f.amount,
                    { v -> vm.update { it.copy(amount = v, error = null) } },
                    "টাকার পরিমাণ *",
                    "৳",
                    error = f.error,
                )
                Spacer(Modifier.height(8.dp))
                DateField("তারিখ", f.dateIso, { v -> vm.update { it.copy(dateIso = v) } })
                Spacer(Modifier.height(8.dp))
                if (income) {
                    com.shohan.khatiyan.ui.components.KhatiyanTextField(f.source, { v -> vm.update { it.copy(source = v) } }, "আয়ের উৎস (যেমন: অফিস / ফ্রিল্যান্স)")
                } else {
                    com.shohan.khatiyan.ui.components.KhatiyanTextField(f.place, { v -> vm.update { it.copy(place = v) } }, "কোথায় খরচ (ঐচ্ছিক)")
                }
            }
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ব্যয়ের খাত", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = { showAddCat = true }) { Text("+ নতুন") }
                }
                Spacer(Modifier.height(4.dp))
                ChoiceChipsRow(
                    options = f.categories,
                    selected = f.category.ifBlank { f.categories.firstOrNull().orEmpty() },
                    onSelect = { c -> vm.update { it.copy(category = c) } },
                )
            }
            AppCard {
                com.shohan.khatiyan.ui.components.KhatiyanTextField(
                    f.note,
                    { v -> vm.update { it.copy(note = v) } },
                    "নোট (ঐচ্ছিক)",
                    singleLine = false,
                    minLines = 2,
                )
            }
            com.shohan.khatiyan.ui.components.PrimaryButton(
                "সংরক্ষণ",
                onClick = { vm.save { navController.popBackStack() } },
                modifier = Modifier.fillMaxWidth(),
                enabled = f.loaded,
            )
            if (entryId != null) {
                androidx.compose.material3.TextButton(
                    onClick = { showDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("এই হিসাবটি মুছে ফেলুন", color = MaterialTheme.colorScheme.error) }
                if (showDelete) {
                    ConfirmDialogLocal(
                        title = "মুছে ফেলবেন?",
                        message = "${BnDates.formatShort(BnDates.fromIso(f.dateIso) ?: BnDates.today())} — ${Money.format(Money.parse(f.amount) ?: 0)} ${"হিসাবটি মুছে যাবে।"}",
                        onConfirm = { showDelete = false; vm.delete { navController.popBackStack() } },
                        onDismiss = { showDelete = false },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAddCat) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddCat = false },
            title = { Text("নতুন ক্যাটাগরি", style = MaterialTheme.typography.titleLarge) },
            text = {
                com.shohan.khatiyan.ui.components.KhatiyanTextField(catName, { catName = it }, "নাম")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (catName.isNotBlank()) {
                            vm.addCategory(catName.trim())
                            catName = ""
                            showAddCat = false
                        }
                    },
                ) { Text("যোগ করুন", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showAddCat = false }) { Text("বাতিল") } },
            shape = MaterialTheme.shapes.extraLarge,
        )
    }
}

@Composable
private fun ConfirmDialogLocal(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    com.shohan.khatiyan.ui.components.ConfirmDialog(
        title = title,
        message = message,
        confirmLabel = "মুছে দিন",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        danger = true,
    )
}
