package com.shohan.khatiyan.presentation.hisab

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.shohan.khatiyan.core.Money
import com.shohan.khatiyan.di.AppContainer
import com.shohan.khatiyan.domain.model.ObligationKind
import com.shohan.khatiyan.presentation.navigation.Routes
import com.shohan.khatiyan.ui.components.KhatiyanScaffold
import com.shohan.khatiyan.ui.theme.KhatiyanBrand
import com.shohan.khatiyan.utilities.DataBus
import com.shohan.khatiyan.utilities.containerFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class HisabSummaryViewModel(private val container: AppContainer) : ViewModel() {
    private val _totals = MutableStateFlow<Map<ObligationKind, Long>>(emptyMap())
    val totals = _totals

    init {
        refresh()
        DataBus.changes.onEach { refresh() }.launchIn(viewModelScope)
    }

    fun refresh() {
        viewModelScope.launch {
            val snap = runCatching { container.dashboardRepo.snapshot() }.getOrNull()
            _totals.value = snap?.outstandingByKind ?: emptyMap()
        }
    }
}

@Composable
fun HisabScreen(navController: NavController, container: AppContainer) {
    var tab by remember { mutableIntStateOf(0) }
    val vm: HisabSummaryViewModel = viewModel(factory = containerFactory { HisabSummaryViewModel(it) })
    val totals by vm.totals.collectAsStateWithLifecycle()

    KhatiyanScaffold(
        title = "হিসাব",
        onBack = { navController.popBackStack() },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (tab) {
                        0 -> navController.navigate(Routes.shopEdit())
                        1 -> navController.navigate(Routes.loanEdit())
                        2 -> navController.navigate(Routes.emiEdit())
                        else -> navController.navigate(Routes.personEdit())
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Outlined.Add, "নতুন হিসাব") }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1.1f)) {
                    Text("মোট বাকি", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        Money.format(totals.values.sumOf { it.coerceAtLeast(0L) }),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (totals.values.any { it > 0L }) KhatiyanBrand.Danger else KhatiyanBrand.Success,
                        maxLines = 1,
                    )
                }
                ObligationKind.entries.forEach { kind ->
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (kind) {
                                ObligationKind.SHOP -> "দোকান"
                                ObligationKind.LOAN -> "লোন"
                                ObligationKind.EMI -> "EMI"
                                ObligationKind.PERSONAL -> "ধার"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            Money.format((totals[kind] ?: 0L).coerceAtLeast(0)),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if ((totals[kind] ?: 0L) > 0) KhatiyanBrand.Danger else KhatiyanBrand.Success,
                            maxLines = 1,
                        )
                    }
                }
            }
            TabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("দোকান") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("লোন") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("EMI") })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("ধার") })
            }
            when (tab) {
                0 -> com.shohan.khatiyan.presentation.shop.ShopListContent(navController, container, withBack = false)
                1 -> com.shohan.khatiyan.presentation.loan.LoanListContent(navController, container)
                2 -> com.shohan.khatiyan.presentation.emi.EmiListContent(navController, container)
                else -> com.shohan.khatiyan.presentation.personal.PeopleListContent(navController, container)
            }
        }
    }
}
