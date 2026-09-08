package com.shohan.khatiyan.presentation.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.shohan.khatiyan.core.Money
import com.shohan.khatiyan.data.local.query.ShopRow
import com.shohan.khatiyan.di.AppContainer
import com.shohan.khatiyan.presentation.navigation.Routes
import com.shohan.khatiyan.ui.components.AppCard
import com.shohan.khatiyan.ui.components.EmptyState
import com.shohan.khatiyan.ui.components.KhatiyanScaffold
import com.shohan.khatiyan.ui.components.PillTone
import com.shohan.khatiyan.ui.components.SearchIconButton
import com.shohan.khatiyan.ui.components.StatusPill
import com.shohan.khatiyan.ui.theme.KhatiyanBrand
import com.shohan.khatiyan.utilities.DataBus
import com.shohan.khatiyan.utilities.containerFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class ShopListViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val query: String = "",
        val showArchived: Boolean = false,
        val rows: List<ShopRow> = emptyList(),
    )

    private val state = MutableStateFlow(UiState())
    val uiState = state

    init {
        rebuild()
        DataBus.changes.onEach { rebuild() }.launchIn(viewModelScope)
    }

    fun setQuery(q: String) {
        state.value = state.value.copy(query = q)
        rebuild()
    }

    fun toggleArchived() {
        state.value = state.value.copy(showArchived = !state.value.showArchived)
        rebuild()
    }

    private fun rebuild() {
        container.db.shopDao()
            .observeShopRows(state.value.query.trim(), !state.value.showArchived)
            .onEach { rows -> state.value = state.value.copy(rows = rows) }
            .launchIn(viewModelScope)
    }
}

@Composable
fun ShopsScreen(navController: NavController, container: AppContainer) {
    ShopListContent(
        navController = navController,
        container = container,
        withBack = true,
    )
}

@Composable
fun ShopListContent(
    navController: NavController,
    container: AppContainer,
    withBack: Boolean,
    modifier: Modifier = Modifier,
) {
    val vm: ShopListViewModel = viewModel(factory = containerFactory { ShopListViewModel(it) })
    val state by vm.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = state.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("দোকানের নাম খুঁজুন…") },
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
                title = if (state.query.isNotBlank()) "এই নামে কোনো দোকান নেই" else "এখনো কোনো দোকান যোগ করা হয়নি",
                subtitle = "প্রতিটি দোকানের নিজস্ব খাতা থাকবে — বাকি, পণ্যের বিবরণ আর পরিশোধ, সব এক জায়গায়।",
                actionLabel = if (state.query.isBlank()) "+ নতুন দোকান" else null,
                onAction = { navController.navigate(Routes.shopEdit()) },
            )
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.rows, key = { it.id }) { row ->
                    ShopRowCard(row) { navController.navigate(Routes.shopDetail(row.id)) }
                }
            }
        }
    }

    // FAB lives in the parent scaffold when embedded; standalone gets its own.
    if (withBack) {
        // no-op: parent provides FAB
    }
}

@Composable
private fun ShopRowCard(row: ShopRow, onClick: () -> Unit) {
    val out = row.balancePaisa
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                val sub = listOfNotNull(
                    row.ownerName.takeIf { it.isNotBlank() }?.let { "মালিক: $it" },
                    row.category.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column {
                        Text("মোট বাকি", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.format(row.totalCreditPaisa), style = MaterialTheme.typography.titleSmall)
                    }
                    Column {
                        Text("পরিশোধ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.format(row.totalPaidPaisa), style = MaterialTheme.typography.titleSmall, color = KhatiyanBrand.Success)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (out >= 0) "বাকি" else "অগ্রিম",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    Money.format(if (out >= 0) out else -out),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (out > 0) KhatiyanBrand.Danger else if (out < 0) KhatiyanBrand.Success else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (row.archived) {
                    Spacer(Modifier.height(4.dp))
                    StatusPill("আর্কাইভড", PillTone.NEUTRAL)
                }
            }
        }
    }
}

@Composable
fun ShopsTabScaffold(navController: NavController, container: AppContainer) {
    KhatiyanScaffold(
        title = "দোকানের বাকী",
        onBack = { navController.popBackStack() },
        actions = { SearchIconButton { navController.navigate(Routes.SEARCH) } },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Routes.shopEdit()) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Filled.Add, "নতুন দোকান") }
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            ShopListContent(navController, container, withBack = false)
        }
    }
}
