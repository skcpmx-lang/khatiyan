package com.shohan.khatiyan.presentation.reports

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
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.shohan.khatiyan.data.repository.ReportRepository
import com.shohan.khatiyan.di.AppContainer
import com.shohan.khatiyan.domain.model.ReportSnapshot
import com.shohan.khatiyan.presentation.cashflow.CashflowFilters
import com.shohan.khatiyan.ui.charts.ChartLegend
import com.shohan.khatiyan.ui.charts.ChartSlice
import com.shohan.khatiyan.ui.charts.CategoryBars
import com.shohan.khatiyan.ui.charts.DonutChart
import com.shohan.khatiyan.ui.charts.GroupedBarChart
import com.shohan.khatiyan.ui.components.AppCard
import com.shohan.khatiyan.ui.charts.BarGroup
import com.shohan.khatiyan.ui.components.ChoiceChipsRow
import com.shohan.khatiyan.ui.components.KhatiyanScaffold
import com.shohan.khatiyan.ui.components.PillTone
import com.shohan.khatiyan.ui.components.SectionTitle
import com.shohan.khatiyan.ui.components.StatusPill
import com.shohan.khatiyan.ui.theme.ChartPalette
import com.shohan.khatiyan.ui.theme.KhatiyanBrand
import com.shohan.khatiyan.utilities.containerFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ReportsViewModel(
    private val container: AppContainer,
) : ViewModel() {

    data class UiState(
        val rangeLabel: String = "এই মাস",
        val snapshot: ReportSnapshot? = null,
        val loading: Boolean = true,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state

    init {
        load()
    }

    fun setRange(label: String) {
        _state.value = _state.value.copy(rangeLabel = label)
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val label = _state.value.rangeLabel
            val (fromIso, toIso) = CashflowFilters.rangeIso(label)
            val from = com.shohan.khatiyan.core.BnDates.fromIso(fromIso)!!
            val to = com.shohan.khatiyan.core.BnDates.fromIso(toIso)!!
            val snap = runCatching { container.reportRepo.report(from, to) }.getOrNull()
            _state.value = _state.value.copy(snapshot = snap, loading = false)
        }
    }
}

@Composable
fun ReportsScreen(navController: NavController, container: AppContainer) {
    val vm: ReportsViewModel = viewModel(factory = containerFactory { ReportsViewModel(it) })
    val state by vm.state.collectAsStateWithLifecycle()
    val snap = state.snapshot
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = androidx.compose.material3.SnackbarHostState()

    val csvLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null && snap != null) {
            scope.launch {
                val ok = runCatching {
                    CsvExporter.writeTo(context, uri, CsvExporter.transactionsCsv(container.db, snap.fromIso, snap.toIso))
                }.isSuccess
                snackbar.showSnackbar(if (ok) "CSV রপ্তানি সম্পন্ন" else "CSV রপ্তানি ব্যর্থ")
            }
        }
    }
    val pdfLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri != null && snap != null) {
            scope.launch {
                val name = container.db.profileDao().getProfile()?.name ?: ""
                val ok = runCatching { container.pdfExporter.exportReport(uri, snap, name) }.isSuccess
                snackbar.showSnackbar(if (ok) "PDF তৈরি হয়েছে" else "PDF তৈরি করা যায়নি")
            }
        }
    }

    KhatiyanScaffold(
        title = "রিপোর্ট",
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = {
                if (snap == null) { scope.launch { snackbar.showSnackbar("রিপোর্ট লোড হচ্ছে…") } } else {
                    csvLauncher.launch("khatiyan-report-${snap.fromIso}_${snap.toIso}.csv")
                }
            }) {
                Icon(Icons.Outlined.TableChart, "CSV রপ্তানি")
            }
            IconButton(onClick = {
                if (snap == null) { scope.launch { snackbar.showSnackbar("রিপোর্ট লোড হচ্ছে…") } } else {
                    pdfLauncher.launch("khatiyan-report-${snap.fromIso}_${snap.toIso}.pdf")
                }
            }) {
                Icon(Icons.Outlined.PictureAsPdf, "PDF রপ্তানি", tint = MaterialTheme.colorScheme.error)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChoiceChipsRow(
                options = CashflowFilters.RANGES,
                selected = state.rangeLabel,
                onSelect = vm::setRange,
            )

            if (snap == null) {
                AppCard { Text("রিপোর্ট তৈরি হচ্ছে…", style = MaterialTheme.typography.bodyMedium) }
            } else {
                val from = BnDates.fromIso(snap.fromIso) ?: BnDates.today()
                val to = BnDates.fromIso(snap.toIso) ?: BnDates.today()
                Text(
                    BnDates.formatShort(from) + " থেকে " + BnDates.formatShort(to),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("আয়", Money.format(snap.incomePaisa), KhatiyanBrand.Success, Modifier.weight(1f))
                    MetricCard("ব্যয়", Money.format(snap.expensePaisa), KhatiyanBrand.Danger, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(
                        "নেট ক্যাশ ফ্লো",
                        Money.format(snap.netPaisa),
                        if (snap.netPaisa >= 0) KhatiyanBrand.Primary else KhatiyanBrand.Danger,
                        Modifier.weight(1f),
                    )
                    MetricCard("পরিশোধ", Money.format(snap.repaidPaisa), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                }

                Column {
                    SectionTitle("মাসিক ধারা")
                    AppCard {
                        GroupedBarChart(
                            groups = snap.monthly.map {
                                BarGroup(
                                    BnDates.MONTHS_SHORT[it.yearMonth.monthValue - 1],
                                    listOf(it.incomePaisa, it.expensePaisa, it.repaidPaisa),
                                )
                            },
                            seriesColors = listOf(KhatiyanBrand.Primary, Color(0xFFC05746), KhatiyanBrand.GoldBright),
                            height = 160.dp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Legend("আয়", KhatiyanBrand.Primary)
                            Legend("ব্যয়", Color(0xFFC05746))
                            Legend("পরিশোধ", KhatiyanBrand.GoldBright)
                        }
                    }
                }

                if (snap.expenseByCategory.isNotEmpty()) {
                    Column {
                        SectionTitle("খরচের ক্যাটাগরি")
                        AppCard {
                            CategoryBars(
                                items = snap.expenseByCategory.map { it.first to it.second },
                                color = Color(0xFFC05746),
                                formatter = { Money.format(it) },
                            )
                        }
                    }
                }

                if (snap.debts.isNotEmpty()) {
                    Column {
                        SectionTitle("বকেয়ার বণ্টন")
                        AppCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DonutChart(
                                    slices = snap.debts.take(8).mapIndexed { i, d -> ChartSlice(d.name, d.outstandingPaisa, ChartPalette[i % ChartPalette.size]) },
                                    modifier = Modifier.size(130.dp),
                                    centerTitle = Money.format(snap.totalDebtPaisa),
                                    centerSubtitle = "মোট বকেয়া",
                                )
                                Spacer(Modifier.width(12.dp))
                                ChartLegend(
                                    rows = snap.debts.take(6).map { it.name to Money.format(it.outstandingPaisa) },
                                    colors = ChartPalette,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                if (snap.upcoming.isNotEmpty()) {
                    Column {
                        SectionTitle("আসন্ন / বকেয়া পেমেন্ট")
                        AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
                            snap.upcoming.take(10).forEach { item ->
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.entityLabel, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${item.detailLabel} · ${BnDates.relativeCompact(item.dueDate)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    StatusPill(
                                        when (item.status) {
                                            com.shohan.khatiyan.domain.model.DueStatus.OVERDUE -> "ওভারডিউ"
                                            com.shohan.khatiyan.domain.model.DueStatus.DUE_TODAY -> "আজ"
                                            com.shohan.khatiyan.domain.model.DueStatus.UPCOMING -> BnDates.formatShort(item.dueDate)
                                        },
                                        when (item.status) {
                                            com.shohan.khatiyan.domain.model.DueStatus.OVERDUE -> PillTone.DANGER
                                            com.shohan.khatiyan.domain.model.DueStatus.DUE_TODAY -> PillTone.WARNING
                                            com.shohan.khatiyan.domain.model.DueStatus.UPCOMING -> PillTone.INFO
                                        },
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(Money.format(item.amountPaisa), style = MaterialTheme.typography.titleMedium, color = KhatiyanBrand.Danger)
                                }
                            }
                        }
                    }
                }

                Column {
                    SectionTitle("লেনদেন (${com.shohan.khatiyan.utilities.BnText.toBnDigits(snap.entries.size.toString())}টি)")
                    if (snap.entries.isEmpty()) {
                        AppCard { Text("এই সময়ে কোনো লেনদেন নেই।", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
                            snap.entries.take(50).forEach { entry ->
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        BnDates.formatShort(BnDates.fromIso(entry.dateIso) ?: BnDates.today()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(74.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            entry.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        Money.format(entry.amountPaisa),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (entry.type.isRepayment || entry.typeKey == "income") KhatiyanBrand.Success else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    "PDF/CSV রপ্তানি উপরের বোতাম দুটো থেকে — সবকিছু আপনার ডিভাইসেই তৈরি হয়।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Legend(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(color, MaterialTheme.shapes.small))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
