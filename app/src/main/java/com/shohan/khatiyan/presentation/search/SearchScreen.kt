package com.shohan.khatiyan.presentation.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.shohan.khatiyan.di.AppContainer
import com.shohan.khatiyan.domain.model.LedgerEntry
import com.shohan.khatiyan.domain.model.SearchResults
import com.shohan.khatiyan.presentation.navigation.Routes
import com.shohan.khatiyan.ui.components.AppCard
import com.shohan.khatiyan.ui.components.EmptyState
import com.shohan.khatiyan.ui.components.KhatiyanScaffold
import com.shohan.khatiyan.ui.components.PillTone
import com.shohan.khatiyan.ui.components.SectionTitle
import com.shohan.khatiyan.ui.components.StatusPill
import com.shohan.khatiyan.utilities.BnText
import com.shohan.khatiyan.ui.theme.KhatiyanBrand
import com.shohan.khatiyan.utilities.containerFactory
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SearchViewModel(private val container: AppContainer) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query = _query

    private val _hits = MutableStateFlow<SearchResults?>(null)
    val hits = _hits

    init {
        viewModelScope.launch {
            _query.debounce(220).collect { q ->
                if (q.isBlank()) _hits.value = null
                else _hits.value = runCatching { container.searchRepo.search(q.trim()) }.getOrNull()
            }
        }
    }

    fun setQuery(q: String) { _query.value = q }
}

@Composable
fun SearchScreen(navController: NavController, container: AppContainer) {
    val vm: SearchViewModel = viewModel(factory = containerFactory { SearchViewModel(it) })
    val hits by vm.hits.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()

    KhatiyanScaffold(title = "খুঁজুন", onBack = { navController.popBackStack() }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxWidth()) {
            TextField(
                value = query,
                onValueChange = vm::setQuery,
                placeholder = { Text("দোকান, লোন, ধার, পণ্য, নোট খুঁজুন…") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                leadingIcon = { Icon(com.shohan.khatiyan.ui.icons.KhatiyanIcons.Search, null) },
            )
            val res = hits
            if (query.isBlank() || res == null) {
                EmptyState("খুঁজুন", "দোকানের নাম, লোন, ধার, পণ্যের নোট — যা লিখেছেন সব এখানে খুঁজে পাবেন।")
            } else if (res.isEmpty) {
                EmptyState("কিছু পাওয়া যায়নি", "“$query” নামে কিছু পাওয়া যায়নি।")
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (res.shops.isNotEmpty()) {
                        SectionTitle("দোকান (" + BnText.toBnDigits(res.shops.size.toString()) + ")")
                        res.shops.forEach { shop ->
                            HitRow(
                                title = shop.name,
                                subtitle = shop.ownerName.ifBlank { shop.category.ifBlank { "দোকান" } } + " · বাকি ${Money.format(shop.balancePaisa.coerceAtLeast(0))}",
                                toneLabel = "বাকি",
                                onClick = { navController.navigate(Routes.shopDetail(shop.id)) },
                            )
                        }
                    }
                    if (res.people.isNotEmpty()) {
                        SectionTitle("ব্যক্তিগত ধার (" + BnText.toBnDigits(res.people.size.toString()) + ")")
                        res.people.forEach { person ->
                            HitRow(
                                title = person.name,
                                subtitle = person.relationship + " · " + if (person.remainingPaisa >= 0) "নেওয়া বাকি ${Money.format(person.remainingPaisa)}" else "ফেরত বাকি ${Money.format(-person.remainingPaisa)}",
                                toneLabel = "ধার",
                                onClick = { navController.navigate(Routes.personDetail(person.id)) },
                            )
                        }
                    }
                    if (res.loans.isNotEmpty()) {
                        SectionTitle("লোন (" + BnText.toBnDigits(res.loans.size.toString()) + ")")
                        res.loans.forEach { loan ->
                            HitRow(
                                title = loan.loanName.ifBlank { loan.institution },
                                subtitle = "${loan.institution} · বাকি ${Money.format(loan.remainingPaisa.coerceAtLeast(0))}",
                                toneLabel = "লোন",
                                onClick = { navController.navigate(Routes.loanDetail(loan.id)) },
                            )
                        }
                    }
                    if (res.emis.isNotEmpty()) {
                        SectionTitle("EMI (" + BnText.toBnDigits(res.emis.size.toString()) + ")")
                        res.emis.forEach { emi ->
                            HitRow(
                                title = emi.productName,
                                subtitle = emi.seller.ifBlank { "কিস্তি" } + " · বাকি ${Money.format(emi.remainingPaisa.coerceAtLeast(0))}",
                                toneLabel = "EMI",
                                onClick = { navController.navigate(Routes.emiDetail(emi.id)) },
                            )
                        }
                    }
                    if (res.debts.isNotEmpty()) {
                        SectionTitle("ধারের হিসাব (" + BnText.toBnDigits(res.debts.size.toString()) + ")")
                        res.debts.forEach { debt ->
                            HitRow(
                                title = "${debt.personName} — ${Money.format(debt.amountPaisa)}",
                                subtitle = "নেওয়া ${BnDates.formatShort(BnDates.fromIso(debt.borrowedIso) ?: BnDates.today())}" +
                                    if (debt.note.isNotBlank()) " · ${debt.note}" else "",
                                toneLabel = "ধার",
                                onClick = { navController.navigate(Routes.personDetail(debt.personId)) },
                            )
                        }
                    }
                    if (res.entries.isNotEmpty()) {
                        SectionTitle("লেনদেন (" + BnText.toBnDigits(res.entries.size.toString()) + ")")
                        res.entries.forEach { entry ->
                            LedgerHitRow(entry)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun HitRow(title: String, subtitle: String, toneLabel: String, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusPill(toneLabel, PillTone.INFO)
        }
    }
}

@Composable
private fun LedgerHitRow(entry: LedgerEntry) {
    val type = remember(entry) { com.shohan.khatiyan.domain.model.TxnType.entries.firstOrNull { it.key == entry.typeKey } }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(KhatiyanBrand.Gold, MaterialTheme.shapes.small),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${type?.labelBn ?: ""} · ${BnDates.formatShort(BnDates.fromIso(entry.dateIso) ?: BnDates.today())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Money.format(entry.amountPaisa), style = MaterialTheme.typography.titleSmall, color = KhatiyanBrand.Primary)
        }
    }
}
