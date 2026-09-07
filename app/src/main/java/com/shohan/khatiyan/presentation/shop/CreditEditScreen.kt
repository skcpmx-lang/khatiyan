package com.shohan.khatiyan.presentation.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.shohan.khatiyan.core.BnDates
import com.shohan.khatiyan.core.Money
import com.shohan.khatiyan.data.repository.ShopRepository
import com.shohan.khatiyan.di.AppContainer
import com.shohan.khatiyan.ui.components.AmountInput
import com.shohan.khatiyan.ui.components.AppCard
import com.shohan.khatiyan.ui.components.ChoiceChipsRow
import com.shohan.khatiyan.ui.components.DateField
import com.shohan.khatiyan.ui.components.KhatiyanScaffold
import com.shohan.khatiyan.ui.components.KhatiyanTextField
import com.shohan.khatiyan.ui.components.PrimaryButton
import com.shohan.khatiyan.utilities.containerFactory
import java.math.BigDecimal
import java.math.RoundingMode

class CreditEditViewModel(
    private val container: AppContainer,
    private val shopId: Long,
    private val creditId: Long?,
) : ViewModel() {

    data class ItemDraft(
        val name: String = "",
        val quantity: String = "1",
        val unit: String = "",
        val unitPrice: String = "",
        val manualTotal: String = "",
        val note: String = "",
    )

    data class UiState(
        val dateIso: String = BnDates.toIso(BnDates.today()),
        val dueIso: String = "",
        val note: String = "",
        val items: List<ItemDraft> = listOf(ItemDraft()),
        val error: String? = null,
        val loaded: Boolean = false,
        val shopName: String = "",
    )

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(UiState())
    val state = _state

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val shop = container.db.shopDao().getShop(shopId)
        var ui = _state.value.copy(shopName = shop?.name ?: "দোকান", loaded = true)
        if (creditId != null) {
            val credit = container.db.shopDao().getCredit(creditId)
            if (credit != null) {
                val items = container.db.shopDao().getItems(creditId).map {
                    ItemDraft(
                        name = it.name,
                        quantity = if (it.quantity % 1.0 == 0.0) it.quantity.toLong().toString() else it.quantity.toString(),
                        unit = it.unit,
                        unitPrice = if (it.unitPricePaisa > 0) Money.formatPlain(it.unitPricePaisa) else "",
                        manualTotal = Money.formatPlain(it.totalPaisa),
                        note = it.note,
                    )
                }
                ui = ui.copy(
                    dateIso = credit.dateIso,
                    dueIso = credit.dueDateIso ?: "",
                    note = credit.note,
                    items = items.ifEmpty { listOf(ItemDraft()) },
                )
            }
        }
        _state.value = ui
    }

    fun update(transform: (UiState) -> UiState) {
        _state.value = transform(_state.value)
    }

    /** Line total: manual value wins, else quantity × unit price (HALF_UP paisa). */
    fun lineTotal(item: ItemDraft): Long? {
        Money.parse(item.manualTotal)?.let { return it }
        val price = Money.parse(item.unitPrice) ?: return null
        val qty = parseQty(item.quantity) ?: return null
        return BigDecimal(com.shohan.khatiyan.utilities.BnText.fromBnDigits(item.quantity.trim()).ifEmpty { "1" })
            .multiply(BigDecimal(price))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }

    private fun parseQty(text: String): Double? {
        val t = com.shohan.khatiyan.utilities.BnText.fromBnDigits(text.trim())
        if (t.isEmpty()) return 1.0
        val v = t.toDoubleOrNull() ?: return null
        return if (v > 0 && v <= 1_000_000) v else null
    }

    fun computedTotal(): Long {
        var sum = 0L
        for (item in _state.value.items) {
            val t = lineTotal(item) ?: continue
            sum = Money.addClamped(sum, t)
        }
        return sum
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val cur = _state.value
            val items = cur.items.map { line ->
                val total = lineTotal(line)
                if (total == null || total <= 0) {
                    _state.value = cur.copy(error = "প্রতিটি পণ্যের দাম দিন (একক মূল্য বা মোট) — সঠিক সংখ্যা।")
                    return@launch
                }
                ShopRepository.CreditItemInput(
                    name = line.name.trim(),
                    quantity = parseQty(line.quantity) ?: 1.0,
                    unit = line.unit.trim(),
                    unitPricePaisa = Money.parse(line.unitPrice) ?: 0L,
                    lineTotalPaisa = total,
                    note = line.note.trim(),
                )
            }
            val draft = ShopRepository.CreditDraft(
                id = creditId ?: 0L,
                shopId = shopId,
                dateIso = cur.dateIso,
                dueDateIso = cur.dueIso.ifBlank { null },
                note = cur.note,
                items = items,
            )
            val result = runCatching { container.shopRepo.saveCredit(draft) }
            val failure = result.exceptionOrNull()
            if (failure != null) {
                val msg = if (failure is com.shohan.khatiyan.utilities.FinanceValidationException) failure.messageBn
                else "সংরক্ষণ করা যায়নি — ইনপুট দেখে আবার চেষ্টা করুন।"
                _state.value = _state.value.copy(error = msg)
                return@launch
            }
            onSaved()
        }
    }
}

@Composable
fun CreditEditScreen(navController: NavController, container: AppContainer, shopId: Long, creditId: Long?) {
    val vm: CreditEditViewModel = viewModel(factory = containerFactory { CreditEditViewModel(it, shopId, creditId) })
    val state by vm.state.collectAsStateWithLifecycle()
    val units = remember { listOf("", "কেজি", "গ্রাম", "লিটার", "পিস", "ডজন", "বোঝা", "বান্ডল") }

    KhatiyanScaffold(
        title = if (creditId == null) "নতুন বাকি" else "বাকির এন্ট্রি সম্পাদনা",
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
                Text("খরচের এন্ট্রি — ${state.shopName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DateField(
                        label = "তারিখ",
                        iso = state.dateIso,
                        onSelected = { v -> vm.update { it.copy(dateIso = v) } },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                DateField(
                    label = "পরিশোধের তারিখ (ঐচ্ছিক)",
                    iso = state.dueIso,
                    onSelected = { v -> vm.update { it.copy(dueIso = v) } },
                    allowClear = true,
                    onClear = { vm.update { it.copy(dueIso = "") } },
                )
                Spacer(Modifier.height(6.dp))
                KhatiyanTextField(
                    value = state.note,
                    onValueChange = { v -> vm.update { it.copy(note = v, error = null) } },
                    label = "নোট (ঐচ্ছিক)",
                )
            }

            SectionTitleRow("পণ্যসমূহ")
            state.items.forEachIndexed { index, item ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "পণ্য ${com.shohan.khatiyan.utilities.BnText.toBnDigits((index + 1).toString())}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.items.size > 1) {
                            IconButton(onClick = {
                                vm.update { it.copy(items = it.items.toMutableList().also { l -> l.removeAt(index) }) }
                            }) {
                                Icon(Icons.Outlined.DeleteOutline, "পণ্যটি মুছুন", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    KhatiyanTextField(
                        value = item.name,
                        onValueChange = { v -> vm.update { st -> st.copy(items = st.items.toMutableList().also { l -> l[index] = l[index].copy(name = v) }) } },
                        label = "পণ্যের নাম *",
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KhatiyanTextField(
                            value = item.quantity,
                            onValueChange = { v ->
                                val filtered = com.shohan.khatiyan.utilities.BnText.fromBnDigits(v).filter { it.isDigit() || it == '.' }
                                vm.update { st -> st.copy(items = st.items.toMutableList().also { l -> l[index] = l[index].copy(quantity = filtered.take(7).ifEmpty { "1" }) }) }
                            },
                            label = "পরিমাণ",
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                            modifier = Modifier.weight(1f),
                        )
                        ChoiceChipsRowCompact(
                            options = units,
                            selected = item.unit,
                            onSelect = { u -> vm.update { st -> st.copy(items = st.items.toMutableList().also { l -> l[index] = l[index].copy(unit = u) }) } },
                            modifier = Modifier.weight(1.4f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AmountInput(
                            value = item.unitPrice,
                            onValueChange = { v -> vm.update { st -> st.copy(items = st.items.toMutableList().also { l -> l[index] = l[index].copy(unitPrice = v, manualTotal = "") }) } },
                            label = "একক মূল্য",
                            symbol = "৳",
                            modifier = Modifier.weight(1f),
                        )
                        AmountInput(
                            value = item.manualTotal,
                            onValueChange = { v -> vm.update { st -> st.copy(items = st.items.toMutableList().also { l -> l[index] = l[index].copy(manualTotal = v) }) } },
                            label = "মোট (ঐচ্ছিক)",
                            symbol = "৳",
                            modifier = Modifier.weight(1f),
                            helper = vm.lineTotal(item)?.let { "= ${Money.format(it)}" },
                        )
                    }
                }
            }

            androidx.compose.material3.OutlinedButton(
                onClick = { vm.update { it.copy(items = it.items + CreditEditViewModel.ItemDraft()) } },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.size(8.dp))
                Text("আরেকটি পণ্য যোগ করুন")
            }

            AppCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("এই এন্ট্রির মোট", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        Money.format(vm.computedTotal()),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (state.error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(state.error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            PrimaryButton(
                text = "সংরক্ষণ করুন",
                onClick = { vm.save { navController.popBackStack() } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                enabled = state.loaded,
            )
        }
    }
}

@Composable
private fun SectionTitleRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ChoiceChipsRowCompact(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        ChoiceChipsRow(options = options, selected = selected.ifEmpty { options.first() }, onSelect = onSelect)
    }
}
import kotlinx.coroutines.launch
